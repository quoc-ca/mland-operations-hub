package edu.fpt.sep490_g22.ppswbs_backend.features.custommanufacturing.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.application.DesignReviewException;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.domain.CustomDesignReferenceSnapshot;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.domain.DesignRequest;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.domain.DesignRequestImage;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.domain.ReviewStatus;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.infrastructure.CustomDesignReferenceSnapshotRepository;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.infrastructure.DesignRequestImageRepository;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.infrastructure.DesignRequestRepository;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.web.DesignReviewDtos.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.custommanufacturing.dao.CustomOrderIdempotencyRecordRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.custommanufacturing.dao.CustomOrderPaymentContextRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.custommanufacturing.dao.CustomOrderQuoteRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.custommanufacturing.dao.CustomOrderRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.custommanufacturing.domain.CustomOrder;
import edu.fpt.sep490_g22.ppswbs_backend.features.custommanufacturing.domain.CustomOrderPaymentContext;
import edu.fpt.sep490_g22.ppswbs_backend.features.custommanufacturing.domain.CustomOrderQuote;
import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.facade.PaymentOwnerKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.facade.PaymentOwnerRegistry;
import edu.fpt.sep490_g22.ppswbs_backend.common.notification.NotificationIntentPort;
import edu.fpt.sep490_g22.ppswbs_backend.common.payment.PaymentGatewayCheckoutPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomOrderService {

    private static final String PAYMENT_PAID = "PAID";
    private static final String PAYMENT_REVIEW_REQUIRED = "REVIEW_REQUIRED";

    private record Stage(String previous, String productionStatus, String fulfilmentStatus) {
    }

    /**
     * Order statuses after full payment: no quote command may touch the order any
     * more (spec 004 2.1).
     */
    private static final Set<String> QUOTE_LOCKED_STATUSES = Set.of("PAID", "IN_PRODUCTION", "READY_FOR_FULFILMENT",
            "CLOSED");

    private static void requireQuoteUnlocked(CustomOrder order) {
        if (QUOTE_LOCKED_STATUSES.contains(order.getStatus())) {
            throw DesignReviewException.conflict("CUSTOM_ORDER_QUOTE_LOCKED",
                    "Đơn hàng đã thanh toán, không thể thay đổi báo giá.");
        }
    }

    private static final Map<String, Stage> STAGES = Map.of(
            "IN_PRODUCTION", new Stage("PAID", "IN_PRODUCTION", null),
            "READY_FOR_FULFILMENT", new Stage("IN_PRODUCTION", "COMPLETED", "READY_FOR_FULFILMENT"),
            "CLOSED", new Stage("READY_FOR_FULFILMENT", "COMPLETED", "DELIVERED"));

    private final CustomOrderRepository customOrderRepository;
    private final CustomOrderQuoteRepository quoteRepository;
    private final CustomOrderPaymentContextRepository paymentContextRepository;
    private final CustomOrderIdempotencyRecordRepository idempotencyRecordRepository;
    private final DesignRequestRepository designRequestRepository;
    private final DesignRequestImageRepository designRequestImageRepository;
    private final CustomDesignReferenceSnapshotRepository snapshotRepository;
    private final PaymentOwnerRegistry paymentOwnerRegistry;
    private final PaymentGatewayCheckoutPort vnpayAdapter;
    private final AccountFacade accountFacade;
    private final CustomOrderShipmentService customOrderShipmentService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired(required = false)
    private NotificationIntentPort notificationIntentPort;

    public void setNotificationIntentPort(NotificationIntentPort notificationIntentPort) {
        this.notificationIntentPort = notificationIntentPort;
    }

    @Transactional
    public QuoteProjectionDto prepareOrUpdateQuote(
            String designRequestId,
            Long staffAccountId,
            boolean isManager,
            Long totalAmount,
            String currency,
            Instant expiresAt,
            String managerReason) {
        log.info("Preparing quote for request: id={}, staff={}, isManager={}, amount={}",
                designRequestId, staffAccountId, isManager, totalAmount);

        DesignRequest designRequest = designRequestRepository.findById(designRequestId)
                .or(() -> designRequestRepository.findByRequestReference(designRequestId))
                .orElseThrow(() -> DesignReviewException.notFound("NOT_FOUND", "Không tìm thấy yêu cầu thiết kế."));

        if (designRequest.getReviewStatus() != ReviewStatus.ACCEPTED) {
            throw DesignReviewException.conflict("DESIGN_REQUEST_NOT_ACCEPTED",
                    "Yêu cầu thiết kế chưa được phê duyệt (ACCEPTED), không thể chuẩn bị báo giá.");
        }

        if (!"MEMBER".equalsIgnoreCase(designRequest.getOwnershipType()) || designRequest.getOwnerAccountId() == null) {
            throw DesignReviewException.forbidden("MEMBER_REQUIRED_FOR_CUSTOM_ORDER",
                    "Chỉ thành viên (Member) đã đăng nhập mới có thể tiến hành tạo đơn hàng gia công tùy chỉnh.");
        }

        if (totalAmount == null || totalAmount <= 0) {
            throw DesignReviewException.badRequest("QUOTE_AMOUNT_INVALID", "Tổng số tiền báo giá phải lớn hơn 0.");
        }

        CustomOrder customOrder = customOrderRepository.findByDesignRequestId(designRequest.getId())
                .orElseGet(() -> {
                    String orderId = UUID.randomUUID().toString();
                    String orderRef = "CO-" + orderId.substring(0, 8).toUpperCase();
                    CustomOrder newOrder = CustomOrder.builder()
                            .id(orderId)
                            .customOrderReference(orderRef)
                            .memberAccountId(designRequest.getOwnerAccountId())
                            .designRequestId(designRequest.getId())
                            .status(isManager ? "FULL_PAYMENT_PENDING" : "QUOTE_PENDING")
                            .createdAt(Instant.now())
                            .updatedAt(Instant.now())
                            .build();
                    return customOrderRepository.save(newOrder);
                });

        requireQuoteUnlocked(customOrder);

        int nextGen = (int) quoteRepository.countByCustomOrderId(customOrder.getId()) + 1;
        Instant expiry = expiresAt != null ? expiresAt : Instant.now().plus(Duration.ofDays(3));
        String quoteId = UUID.randomUUID().toString();
        String quoteRef = "QUO-" + quoteId.substring(0, 8).toUpperCase();

        CustomOrderQuote quote = CustomOrderQuote.builder()
                .id(quoteId)
                .customOrderId(customOrder.getId())
                .quoteReference(quoteRef)
                .quoteGeneration(nextGen)
                .totalAmount(totalAmount)
                .currency(currency != null ? currency : "VND")
                .expiresAt(expiry)
                .status(isManager ? "QUOTE_READY" : "QUOTE_PENDING")
                .approvedByManager(isManager)
                .managerAccountId(isManager ? staffAccountId : null)
                .managerReason(managerReason)
                .paymentStatus("UNPAID")
                .paymentMode("FULL_CUSTOM_ORDER_PAYMENT")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        quoteRepository.save(quote);

        customOrder.setStatus(isManager ? "FULL_PAYMENT_PENDING" : "QUOTE_PENDING");
        customOrder.setUpdatedAt(Instant.now());
        customOrderRepository.save(customOrder);

        designRequest.setQuoteReference(quoteRef);
        designRequest.setUpdatedAt(Instant.now());
        designRequestRepository.save(designRequest);

        return mapToQuoteDto(quote);
    }

    @Transactional
    public QuoteProjectionDto approveQuote(
            String customOrderIdOrRef,
            String quoteIdOrRef,
            Long managerAccountId,
            String managerReason) {
        log.info("Manager approving quote: order={}, quote={}, manager={}", customOrderIdOrRef, quoteIdOrRef,
                managerAccountId);

        CustomOrder order = customOrderRepository.findById(customOrderIdOrRef)
                .or(() -> customOrderRepository.findByCustomOrderReference(customOrderIdOrRef))
                .orElseThrow(() -> DesignReviewException.notFound("NOT_FOUND", "Không tìm thấy đơn hàng tùy chỉnh."));

        CustomOrderQuote quote = (quoteIdOrRef != null && !quoteIdOrRef.isBlank())
                ? quoteRepository.findById(quoteIdOrRef)
                        .or(() -> quoteRepository.findByQuoteReference(quoteIdOrRef))
                        .orElseThrow(() -> DesignReviewException.notFound("NOT_FOUND", "Không tìm thấy báo giá."))
                : quoteRepository.findTopByCustomOrderIdOrderByQuoteGenerationDesc(order.getId())
                        .orElseThrow(() -> DesignReviewException.notFound("NOT_FOUND",
                                "Không tìm thấy báo giá nào cho đơn hàng này."));

        requireQuoteUnlocked(order);
        boolean latest = quoteRepository.findTopByCustomOrderIdOrderByQuoteGenerationDesc(order.getId())
                .map(newest -> newest.getId().equals(quote.getId()))
                .orElse(false);
        if (!latest) {
            throw DesignReviewException.conflict("QUOTE_ALREADY_DECIDED",
                    "Chỉ có thể duyệt báo giá mới nhất của đơn hàng này.");
        }

        if (!"QUOTE_PENDING".equalsIgnoreCase(quote.getStatus())) {
            throw DesignReviewException.conflict("QUOTE_ALREADY_DECIDED",
                    "Báo giá không ở trạng thái chờ duyệt (QUOTE_PENDING). Trạng thái hiện tại: " + quote.getStatus());
        }

        quote.setApprovedByManager(true);
        quote.setManagerAccountId(managerAccountId);
        quote.setManagerReason(managerReason);
        quote.setStatus("QUOTE_READY");
        quote.setUpdatedAt(Instant.now());
        quoteRepository.save(quote);

        order.setStatus("FULL_PAYMENT_PENDING");
        order.setUpdatedAt(Instant.now());
        customOrderRepository.save(order);

        if (notificationIntentPort != null) {
            try {
                accountFacade.findAccountEmail(order.getMemberAccountId()).ifPresent(email -> {
                    notificationIntentPort.submitIntent(
                            "QUOTE_APPROVED_" + quote.getQuoteReference(),
                            "CUSTOM_ORDER_QUOTE_APPROVED",
                            email,
                            Map.of(
                                    "customOrderReference", order.getCustomOrderReference(),
                                    "quoteReference", quote.getQuoteReference(),
                                    "totalAmount", quote.getTotalAmount(),
                                    "expiresAt", quote.getExpiresAt().toString()
                            )
                    );
                });
            } catch (Exception ex) {
                log.warn("Failed to submit quote approval notification: {}", ex.getMessage());
            }
        }

        return mapToQuoteDto(quote);
    }

    @Transactional
    public QuoteProjectionDto rejectQuoteByDesignRequestId(
            String designRequestId,
            Long managerAccountId,
            String managerReason) {
        CustomOrder order = customOrderRepository.findByDesignRequestId(designRequestId)
                .orElseThrow(() -> DesignReviewException.notFound("NOT_FOUND",
                        "Không tìm thấy đơn hàng tùy chỉnh cho yêu cầu này."));
        return rejectQuote(order.getId(), null, managerAccountId, managerReason);
    }

    @Transactional
    public QuoteProjectionDto rejectQuote(
            String customOrderId,
            String quoteId,
            Long managerAccountId,
            String managerReason) {
        CustomOrder order = customOrderRepository.findById(customOrderId)
                .orElseThrow(() -> DesignReviewException.notFound("NOT_FOUND", "Không tìm thấy đơn hàng."));

        CustomOrderQuote quote;
        if (quoteId != null && !quoteId.isBlank()) {
            quote = quoteRepository.findById(quoteId)
                    .orElseThrow(() -> DesignReviewException.notFound("NOT_FOUND", "Không tìm thấy báo giá."));
        } else {
            quote = quoteRepository.findTopByCustomOrderIdOrderByQuoteGenerationDesc(order.getId())
                    .orElseThrow(() -> DesignReviewException.notFound("NOT_FOUND", "Không tìm thấy báo giá cho đơn hàng."));
        }

        requireQuoteUnlocked(order);
        if (!"QUOTE_PENDING".equalsIgnoreCase(quote.getStatus())) {
            throw DesignReviewException.conflict("QUOTE_ALREADY_DECIDED",
                    "Báo giá không ở trạng thái chờ duyệt (QUOTE_PENDING). Trạng thái hiện tại: " + quote.getStatus());
        }

        quote.setApprovedByManager(false);
        quote.setManagerAccountId(managerAccountId);
        quote.setManagerReason(managerReason);
        quote.setStatus("REJECTED");
        quote.setUpdatedAt(Instant.now());
        quoteRepository.save(quote);

        order.setStatus("CANCELLED");
        order.setUpdatedAt(Instant.now());
        customOrderRepository.save(order);

        if (order.getDesignRequestId() != null) {
            designRequestRepository.findById(order.getDesignRequestId()).ifPresent(req -> {
                req.setReviewStatus(ReviewStatus.REJECTED);
                req.setDecisionExplanation(managerReason != null ? managerReason : "Quản lý từ chối mẫu thiết kế.");
                req.setUpdatedAt(Instant.now());
                designRequestRepository.save(req);
            });
        }

        if (notificationIntentPort != null) {
            try {
                accountFacade.findAccountEmail(order.getMemberAccountId()).ifPresent(email -> {
                    notificationIntentPort.submitIntent(
                            "QUOTE_REJECTED_" + quote.getQuoteReference(),
                            "CUSTOM_ORDER_QUOTE_REJECTED",
                            email,
                            Map.of(
                                    "customOrderReference", order.getCustomOrderReference(),
                                    "quoteReference", quote.getQuoteReference(),
                                    "reason", managerReason != null ? managerReason : ""
                            )
                    );
                });
            } catch (Exception ex) {
                log.warn("Failed to submit quote rejection notification: {}", ex.getMessage());
            }
        }

        return mapToQuoteDto(quote);
    }

    @Transactional(readOnly = true)
    public Optional<QuoteProjectionDto> findLatestQuoteByDesignRequestId(String designRequestId) {
        return customOrderRepository.findByDesignRequestId(designRequestId)
                .flatMap(order -> quoteRepository.findTopByCustomOrderIdOrderByQuoteGenerationDesc(order.getId()))
                .map(this::mapToQuoteDto);
    }

    @Transactional
    public QuoteProjectionDto approveQuoteByDesignRequestId(
            String designRequestId,
            Long managerAccountId,
            String managerReason) {
        CustomOrder order = customOrderRepository.findByDesignRequestId(designRequestId)
                .orElseThrow(() -> DesignReviewException.notFound("NOT_FOUND",
                        "Không tìm thấy đơn hàng tùy chỉnh cho yêu cầu này."));
        return approveQuote(order.getId(), null, managerAccountId, managerReason);
    }

    @Transactional(readOnly = true)
    public List<CustomOrderProjectionDto> listMyCustomOrders(Long memberAccountId) {
        List<CustomOrder> orders = customOrderRepository.findByMemberAccountIdOrderByCreatedAtDesc(memberAccountId);
        return orders.stream()
                .map(this::mapToOrderDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<CustomOrderProjectionDto> listAllCustomOrdersForOperations(String status) {
        List<CustomOrder> orders;
        if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status.trim())) {
            String targetStatus = status.trim().toUpperCase();
            orders = customOrderRepository.findByStatusOrderByCreatedAtDesc(targetStatus);
        } else {
            orders = customOrderRepository.findAllByOrderByCreatedAtDesc();
        }
        return orders.stream()
                .map(this::mapToOrderDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public CustomOrderProjectionDto getCustomOrderForOperations(String customOrderIdOrRef) {
        CustomOrder order = customOrderRepository.findById(customOrderIdOrRef)
                .or(() -> customOrderRepository.findByCustomOrderReference(customOrderIdOrRef))
                .orElseThrow(() -> DesignReviewException.notFound("NOT_FOUND",
                        "Không tìm thấy đơn hàng gia công tùy chỉnh."));
        return mapToOrderDto(order);
    }

    @Transactional(readOnly = true)
    public CustomOrderProjectionDto getMyCustomOrder(Long memberAccountId, String customOrderIdOrRef) {
        CustomOrder order = customOrderRepository.findById(customOrderIdOrRef)
                .or(() -> customOrderRepository.findByCustomOrderReference(customOrderIdOrRef))
                .orElseThrow(() -> DesignReviewException.notFound("NOT_FOUND",
                        "Không tìm thấy đơn hàng gia công tùy chỉnh."));

        if (!order.getMemberAccountId().equals(memberAccountId)) {
            throw DesignReviewException.forbidden("ACCESS_DENIED", "Bạn không có quyền truy cập đơn hàng này.");
        }

        return mapToOrderDto(order);
    }

    @Transactional
    public PaymentAttemptProjectionDto startFullPayment(
            Long memberAccountId,
            String customOrderIdOrRef,
            String idempotencyKey,
            String clientIp) {
        log.info("Starting full payment for custom order: ref={}, member={}", customOrderIdOrRef, memberAccountId);

        CustomOrder order = customOrderRepository.findById(customOrderIdOrRef)
                .or(() -> customOrderRepository.findByCustomOrderReference(customOrderIdOrRef))
                .orElseThrow(() -> DesignReviewException.notFound("NOT_FOUND", "Không tìm thấy đơn hàng tùy chỉnh."));

        if (!order.getMemberAccountId().equals(memberAccountId)) {
            throw DesignReviewException.forbidden("ACCESS_DENIED", "Bạn không có quyền thanh toán cho đơn hàng này.");
        }

        CustomOrderQuote quote = quoteRepository.findTopByCustomOrderIdOrderByQuoteGenerationDesc(order.getId())
                .orElseThrow(() -> DesignReviewException.unprocessable("NO_QUOTE_FOUND",
                        "Đơn hàng chưa có báo giá để thanh toán."));

        // Expiry check
        if (Instant.now().isAfter(quote.getExpiresAt())) {
            quote.setStatus("QUOTE_EXPIRED");
            quoteRepository.save(quote);
            order.setStatus("QUOTE_EXPIRED");
            customOrderRepository.save(order);
            throw DesignReviewException.unprocessable("QUOTE_EXPIRED",
                    "Báo giá cho đơn hàng đã hết hạn thanh toán. Vui lòng gửi yêu cầu báo giá mới.");
        }

        if ("PAID".equalsIgnoreCase(quote.getStatus()) || "PAID".equalsIgnoreCase(order.getStatus())) {
            throw DesignReviewException.conflict("ORDER_ALREADY_PAID",
                    "Đơn hàng này đã được thanh toán toàn bộ trước đó.");
        }

        if (!"QUOTE_READY".equalsIgnoreCase(quote.getStatus())
                && !"FULL_PAYMENT_PENDING".equalsIgnoreCase(order.getStatus())) {
            throw DesignReviewException.conflict("QUOTE_NOT_PAYABLE",
                    "Báo giá chưa sẵn sàng để thanh toán (cần sự phê duyệt của Manager).");
        }

        String merchantRef = "CUST-" + order.getCustomOrderReference().replace("CO-", "") + "-"
                + System.currentTimeMillis();

        paymentOwnerRegistry.register(
                merchantRef,
                order.getCustomOrderReference(),
                PaymentOwnerKind.CUSTOM_ORDER_FULL_PAYMENT,
                quote.getTotalAmount(),
                quote.getCurrency());

        Instant now = Instant.now();
        Instant attemptExpiry = now.plus(Duration.ofMinutes(10));
        if (quote.getExpiresAt().isBefore(attemptExpiry)) {
            attemptExpiry = quote.getExpiresAt();
        }

        String paymentUrl = vnpayAdapter.createRetailPaymentUrl(
                merchantRef,
                order.getCustomOrderReference(),
                quote.getTotalAmount(),
                now,
                attemptExpiry,
                clientIp);

        CustomOrderPaymentContext paymentContext = CustomOrderPaymentContext.builder()
                .customOrderId(order.getId())
                .quoteId(quote.getId())
                .merchantReference(merchantRef)
                .paymentAttemptReference(merchantRef)
                .amountVnd(quote.getTotalAmount())
                .currency(quote.getCurrency())
                .status("INITIATED")
                .paymentUrl(paymentUrl)
                .expiresAt(attemptExpiry)
                .createdAt(now)
                .updatedAt(now)
                .build();
        paymentContextRepository.save(paymentContext);

        return PaymentAttemptProjectionDto.builder()
                .paymentAttemptReference(merchantRef)
                .status("INITIATED")
                .paymentUrl(paymentUrl)
                .build();
    }

    @Transactional
    public CustomOrderProjectionDto requestNewQuote(
            Long memberAccountId,
            String customOrderIdOrRef,
            String idempotencyKey) {
        log.info("Member requesting new quote for custom order: order={}, member={}", customOrderIdOrRef,
                memberAccountId);

        CustomOrder order = customOrderRepository.findById(customOrderIdOrRef)
                .or(() -> customOrderRepository.findByCustomOrderReference(customOrderIdOrRef))
                .orElseThrow(() -> DesignReviewException.notFound("NOT_FOUND", "Không tìm thấy đơn hàng tùy chỉnh."));

        if (!order.getMemberAccountId().equals(memberAccountId)) {
            throw DesignReviewException.forbidden("ACCESS_DENIED",
                    "Bạn không có quyền yêu cầu báo giá cho đơn hàng này.");
        }
        requireQuoteUnlocked(order);

        Optional<CustomOrderQuote> latestQuoteOpt = quoteRepository
                .findTopByCustomOrderIdOrderByQuoteGenerationDesc(order.getId());
        if (latestQuoteOpt.isPresent()) {
            CustomOrderQuote latestQuote = latestQuoteOpt.get();
            boolean isExpired = Instant.now().isAfter(latestQuote.getExpiresAt())
                    || "QUOTE_EXPIRED".equalsIgnoreCase(latestQuote.getStatus());
            boolean isCancelled = "CANCELLED".equalsIgnoreCase(latestQuote.getStatus());

            if (!isExpired && !isCancelled && !"QUOTE_EXPIRED".equalsIgnoreCase(order.getStatus())) {
                throw DesignReviewException.conflict("ACTIVE_QUOTE_EXISTS",
                        "Đơn hàng hiện đang có báo giá còn hiệu lực, không thể yêu cầu báo giá mới.");
            }

            if (!"QUOTE_EXPIRED".equalsIgnoreCase(latestQuote.getStatus())) {
                latestQuote.setStatus("QUOTE_EXPIRED");
                latestQuote.setUpdatedAt(Instant.now());
                quoteRepository.save(latestQuote);
            }
        }

        order.setStatus("QUOTE_PENDING");
        order.setUpdatedAt(Instant.now());
        customOrderRepository.save(order);

        return mapToOrderDto(order);
    }

    /**
     * Applies a verified VNPay success only to the quote the payment was started
     * for, while that quote is still the
     * latest one, payable and unexpired. Any other evidence (late, replaced quote,
     * already paid order, different
     * amount) is kept on the payment context as {@code REVIEW_REQUIRED} for manual
     * handling; the order is not paid.
     */
    @Transactional
    public void applyVerifiedFullPayment(
            String customOrderReference,
            String merchantReference,
            String providerTransactionNumber,
            long amountVnd,
            String currencyCode) {
        log.info("Applying verified full payment: orderRef={}, merchantRef={}, amount={}",
                customOrderReference, merchantReference, amountVnd);

        CustomOrder order = customOrderRepository.findByCustomOrderReference(customOrderReference)
                .orElseThrow(() -> new IllegalStateException("Custom order not found: " + customOrderReference));

        CustomOrderPaymentContext context = paymentContextRepository.findByMerchantReference(merchantReference)
                .filter(ctx -> ctx.getCustomOrderId().equals(order.getId()))
                .orElseThrow(() -> new IllegalStateException("Payment context not found: " + merchantReference));

        if (PAYMENT_PAID.equals(context.getStatus()) || PAYMENT_REVIEW_REQUIRED.equals(context.getStatus())) {
            return;
        }

        CustomOrderQuote quote = quoteRepository.findTopByCustomOrderIdOrderByQuoteGenerationDesc(order.getId())
                .orElseThrow(
                        () -> new IllegalStateException("No quote found for custom order: " + customOrderReference));

        boolean applicable = quote.getId().equals(context.getQuoteId())
                && "FULL_PAYMENT_PENDING".equals(order.getStatus())
                && "QUOTE_READY".equals(quote.getStatus())
                && Instant.now().isBefore(quote.getExpiresAt())
                && amountVnd == quote.getTotalAmount()
                && quote.getCurrency().equalsIgnoreCase(currencyCode);

        context.setStatus(applicable ? PAYMENT_PAID : PAYMENT_REVIEW_REQUIRED);
        context.setVnpayTransactionNo(providerTransactionNumber);
        context.setUpdatedAt(Instant.now());
        paymentContextRepository.save(context);

        if (!applicable) {
            log.warn("Custom order payment needs manual review: orderRef={}, merchantRef={}, orderStatus={}",
                    customOrderReference, merchantReference, order.getStatus());
            return;
        }

        quote.setStatus("PAID");
        quote.setPaymentStatus("PAID");
        quote.setUpdatedAt(Instant.now());
        quoteRepository.save(quote);

        order.setStatus("PAID");
        order.setProductionStatus("PENDING");
        order.setUpdatedAt(Instant.now());
        customOrderRepository.save(order);

        log.info("Custom order {} successfully marked PAID and queued for production", customOrderReference);

        if (notificationIntentPort != null) {
            try {
                // 1. Notify member
                accountFacade.findAccountEmail(order.getMemberAccountId()).ifPresent(email -> {
                    notificationIntentPort.submitIntent(
                            "ORDER_PAID_" + order.getCustomOrderReference(),
                            "CUSTOM_ORDER_IN_PRODUCTION",
                            email,
                            Map.of(
                                    "customOrderReference", order.getCustomOrderReference(),
                                    "status", "PAID"
                            )
                    );
                });

                // 2. Alert active Staff and Managers
                List<String> staffEmails = accountFacade.listActiveStaffEmails();
                if (staffEmails != null) {
                    for (String staffEmail : staffEmails) {
                        notificationIntentPort.submitIntent(
                                "ORDER_PAID_STAFF_" + order.getCustomOrderReference() + "_" + staffEmail,
                                "CUSTOM_ORDER_IN_PRODUCTION_ALERT",
                                staffEmail,
                                Map.of(
                                        "customOrderReference", order.getCustomOrderReference(),
                                        "status", "PAID",
                                        "recipientRole", "STAFF"
                                )
                        );
                    }
                }

                List<String> managerEmails = accountFacade.listActiveManagerEmails();
                if (managerEmails != null) {
                    for (String managerEmail : managerEmails) {
                        notificationIntentPort.submitIntent(
                                "ORDER_PAID_MGR_" + order.getCustomOrderReference() + "_" + managerEmail,
                                "CUSTOM_ORDER_IN_PRODUCTION_ALERT",
                                managerEmail,
                                Map.of(
                                        "customOrderReference", order.getCustomOrderReference(),
                                        "status", "PAID",
                                        "recipientRole", "MANAGER"
                                )
                        );
                    }
                }
            } catch (Exception ex) {
                log.warn("Failed to submit order paid notification: {}", ex.getMessage());
            }
        }
    }

    /**
     * Moves a fully paid order one stage forward: PAID, IN_PRODUCTION,
     * READY_FOR_FULFILMENT, CLOSED. Production and
     * fulfilment statuses follow from the stage. Repeating the current stage
     * changes nothing.
     */
    @Transactional
    public CustomOrderProjectionDto updateProductionOrFulfilmentStatus(
            String customOrderIdOrRef,
            String productionStatus,
            String fulfilmentStatus,
            String overallStatus) {
        CustomOrder order = customOrderRepository.findById(customOrderIdOrRef)
                .or(() -> customOrderRepository.findByCustomOrderReference(customOrderIdOrRef))
                .orElseThrow(() -> DesignReviewException.notFound("NOT_FOUND", "Không tìm thấy đơn hàng tùy chỉnh."));

        String target = overallStatus == null ? "" : overallStatus.trim();
        Stage stage = STAGES.get(target);
        if (stage == null
                || !matches(productionStatus, stage.productionStatus())
                || !matches(fulfilmentStatus, stage.fulfilmentStatus())) {
            throw DesignReviewException.badRequest("CUSTOM_ORDER_STATUS_INVALID",
                    "Trạng thái đơn gia công không hợp lệ.");
        }

        boolean paid = quoteRepository.findTopByCustomOrderIdOrderByQuoteGenerationDesc(order.getId())
                .map(quote -> "PAID".equals(quote.getPaymentStatus()))
                .orElse(false);
        if (!paid) {
            throw DesignReviewException.conflict("CUSTOM_ORDER_NOT_PAID",
                    "Đơn hàng chưa được thanh toán toàn bộ.");
        }
        if (target.equals(order.getStatus())) {
            return mapToOrderDto(order);
        }
        if (!stage.previous().equals(order.getStatus())) {
            throw DesignReviewException.conflict("CUSTOM_ORDER_STATUS_CONFLICT",
                    "Chỉ có thể chuyển đơn sang bước kế tiếp.");
        }

        if ("CLOSED".equals(target) && customOrderShipmentService != null) {
            var shipmentOpt = customOrderShipmentService.findByCustomOrderReference(order.getCustomOrderReference());
            if (shipmentOpt.isEmpty() || !"DELIVERED".equalsIgnoreCase(shipmentOpt.get().getStatus())) {
                throw DesignReviewException.conflict("CUSTOM_ORDER_STATUS_CONFLICT",
                        "Đơn hàng chỉ được đóng (CLOSED) khi bưu tá đã giao hàng thành công (DELIVERED).");
            }
        }

        order.setStatus(target);
        order.setProductionStatus(stage.productionStatus());
        order.setFulfilmentStatus(stage.fulfilmentStatus());
        order.setUpdatedAt(Instant.now());
        customOrderRepository.save(order);

        if ("IN_PRODUCTION".equals(target) && notificationIntentPort != null) {
            try {
                // 1. Notify member
                accountFacade.findAccountEmail(order.getMemberAccountId()).ifPresent(email -> {
                    notificationIntentPort.submitIntent(
                            "ORDER_IN_PRODUCTION_" + order.getCustomOrderReference(),
                            "CUSTOM_ORDER_IN_PRODUCTION",
                            email,
                            Map.of(
                                    "customOrderReference", order.getCustomOrderReference(),
                                    "status", "IN_PRODUCTION"
                            )
                    );
                });

                // 2. Alert active Staff and Managers
                List<String> staffEmails = accountFacade.listActiveStaffEmails();
                if (staffEmails != null) {
                    for (String staffEmail : staffEmails) {
                        notificationIntentPort.submitIntent(
                                "ORDER_IN_PROD_STAFF_" + order.getCustomOrderReference() + "_" + staffEmail,
                                "CUSTOM_ORDER_IN_PRODUCTION_ALERT",
                                staffEmail,
                                Map.of(
                                        "customOrderReference", order.getCustomOrderReference(),
                                        "status", "IN_PRODUCTION",
                                        "recipientRole", "STAFF"
                                )
                        );
                    }
                }

                List<String> managerEmails = accountFacade.listActiveManagerEmails();
                if (managerEmails != null) {
                    for (String managerEmail : managerEmails) {
                        notificationIntentPort.submitIntent(
                                "ORDER_IN_PROD_MGR_" + order.getCustomOrderReference() + "_" + managerEmail,
                                "CUSTOM_ORDER_IN_PRODUCTION_ALERT",
                                managerEmail,
                                Map.of(
                                        "customOrderReference", order.getCustomOrderReference(),
                                        "status", "IN_PRODUCTION",
                                        "recipientRole", "MANAGER"
                                )
                        );
                    }
                }
            } catch (Exception ex) {
                log.warn("Failed to submit in-production notification: {}", ex.getMessage());
            }
        }

        if ("READY_FOR_FULFILMENT".equals(target) && customOrderShipmentService != null) {
            customOrderShipmentService.createShipmentForOrder(order);
        }

        return mapToOrderDto(order);
    }

    private static boolean matches(String requested, String expected) {
        return requested == null || requested.isBlank() || requested.trim().equals(expected);
    }

    public QuoteProjectionDto mapToQuoteDto(CustomOrderQuote quote) {
        if (quote == null)
            return null;
        return QuoteProjectionDto.builder()
                .quoteReference(quote.getQuoteReference())
                .status(quote.getStatus())
                .expiresAt(quote.getExpiresAt().toString())
                .totalAmount(String.valueOf(quote.getTotalAmount()))
                .currency(quote.getCurrency())
                .paymentMode(quote.getPaymentMode())
                .quoteGeneration(quote.getQuoteGeneration())
                .approvedByManager(quote.getApprovedByManager())
                .managerReason(quote.getManagerReason())
                .build();
    }

    public CustomOrderProjectionDto mapToOrderDto(CustomOrder order) {
        if (order == null)
            return null;

        Optional<CustomOrderQuote> quoteOpt = quoteRepository
                .findTopByCustomOrderIdOrderByQuoteGenerationDesc(order.getId());
        QuoteProjectionDto quoteDto = quoteOpt.map(this::mapToQuoteDto).orElse(null);

        Object snapshot = null;
        Optional<CustomDesignReferenceSnapshot> snapshotOpt = snapshotRepository
                .findTopByRequestIdOrderByCreatedAtDesc(order.getDesignRequestId());
        if (snapshotOpt.isPresent()) {
            try {
                snapshot = objectMapper.readValue(snapshotOpt.get().getSnapshotJson(),
                        new TypeReference<Map<String, Object>>() {
                        });
            } catch (Exception e) {
                log.warn("Failed to parse design snapshot json: {}", e.getMessage());
            }
        }

        String nextAction = switch (order.getStatus()) {
            case "QUOTE_PENDING" -> "AWAITING_MANAGER_QUOTE_APPROVAL";
            case "FULL_PAYMENT_PENDING" -> "PAY_FULL_AMOUNT";
            case "PAID" -> "PREPARING_PRODUCTION";
            case "IN_PRODUCTION" -> "PRODUCTION_IN_PROGRESS";
            case "READY_FOR_FULFILMENT" -> "AWAITING_DELIVERY_OR_PICKUP";
            case "DELIVERING" -> "DELIVERY_IN_PROGRESS";
            case "CLOSED" -> "ORDER_COMPLETED";
            case "QUOTE_EXPIRED" -> "REQUEST_NEW_QUOTE_AVAILABLE";
            default -> null;
        };

        CustomOrderProjectionDto.CustomOrderProjectionDtoBuilder builder = CustomOrderProjectionDto.builder()
                .customOrderReference(order.getCustomOrderReference())
                .status(order.getStatus())
                .quote(quoteDto)
                .designSnapshot(snapshot)
                .nextAction(nextAction)
                .productionStatus(order.getProductionStatus())
                .fulfilmentStatus(order.getFulfilmentStatus())
                .createdAt(order.getCreatedAt().toString())
                .updatedAt(order.getUpdatedAt().toString())
                .memberAccountId(order.getMemberAccountId())
                .shippingAddress(order.getShippingAddress());

        if (order.getMemberAccountId() != null && accountFacade != null) {
            try {
                accountFacade.findAccountFullName(order.getMemberAccountId()).ifPresent(builder::customerName);
                accountFacade.findAccountEmail(order.getMemberAccountId()).ifPresent(builder::customerEmail);
            } catch (Exception e) {
                log.debug("Could not resolve account info for memberAccountId {}: {}", order.getMemberAccountId(),
                        e.getMessage());
            }
        }

        if (order.getDesignRequestId() != null) {
            builder.designRequestId(order.getDesignRequestId());
            designRequestRepository.findById(order.getDesignRequestId()).ifPresent(dr -> {
                builder.designRequestReference(dr.getRequestReference());
                builder.designOriginalDescription(dr.getDescription());
                builder.designRef(dr.getDesignReference());
                builder.staffExplanation(dr.getDecisionExplanation());
            });

            List<DesignRequestImage> images = designRequestImageRepository
                    .findByDesignRequestId(order.getDesignRequestId());
            if (images != null && !images.isEmpty()) {
                List<ImageProjectionDto> imageDtos = images.stream().map(img -> ImageProjectionDto.builder()
                        .imageReference(img.getImageReference())
                        .qualityStatus(img.getQualityStatus())
                        .qualityReason(img.getQualityReason())
                        .originalFilename(img.getOriginalFilename())
                        .fileSizeBytes(img.getFileSizeBytes())
                        .build()).collect(Collectors.toList());
                builder.designImages(imageDtos);
            }
        }

        if (customOrderShipmentService != null) {
            customOrderShipmentService.findByCustomOrderReference(order.getCustomOrderReference())
                    .ifPresent(shipment -> {
                        builder.trackingNumber(shipment.getTrackingNumber());
                        builder.shipmentStatus(shipment.getStatus());
                        builder.carrierService(shipment.getCarrierService());
                        if (shipment.getPickedUpAt() != null) {
                            builder.shipmentPickedUpAt(shipment.getPickedUpAt().toString());
                        }
                        if (shipment.getDeliveredAt() != null) {
                            builder.shipmentDeliveredAt(shipment.getDeliveredAt().toString());
                        }
                    });
        }

        return builder.build();
    }

    @Transactional
    public CustomOrderProjectionDto staffAdvanceShipmentToReadyForCarrier(String customOrderIdOrRef) {
        CustomOrder order = customOrderRepository.findById(customOrderIdOrRef)
                .or(() -> customOrderRepository.findByCustomOrderReference(customOrderIdOrRef))
                .orElseThrow(() -> DesignReviewException.notFound("NOT_FOUND", "Không tìm thấy đơn hàng tùy chỉnh."));

        if (customOrderShipmentService != null) {
            customOrderShipmentService.staffAdvanceToReadyForCarrier(order.getCustomOrderReference());
        }
        order.setFulfilmentStatus("READY_FOR_CARRIER");
        order.setUpdatedAt(Instant.now());
        CustomOrder saved = customOrderRepository.save(order);
        return mapToOrderDto(saved);
    }
}
