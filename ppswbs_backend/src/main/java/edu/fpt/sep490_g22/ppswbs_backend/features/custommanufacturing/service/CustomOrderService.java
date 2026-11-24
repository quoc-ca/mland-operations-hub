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
import edu.fpt.sep490_g22.ppswbs_backend.common.payment.PaymentGatewayCheckoutPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public QuoteProjectionDto prepareOrUpdateQuote(
            String designRequestId,
            Long staffAccountId,
            boolean isManager,
            Long totalAmount,
            String currency,
            Instant expiresAt,
            String managerReason
    ) {
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
            String managerReason
    ) {
        log.info("Manager approving quote: order={}, quote={}, manager={}", customOrderIdOrRef, quoteIdOrRef, managerAccountId);

        CustomOrder order = customOrderRepository.findById(customOrderIdOrRef)
                .or(() -> customOrderRepository.findByCustomOrderReference(customOrderIdOrRef))
                .orElseThrow(() -> DesignReviewException.notFound("NOT_FOUND", "Không tìm thấy đơn hàng tùy chỉnh."));

        CustomOrderQuote quote = (quoteIdOrRef != null && !quoteIdOrRef.isBlank())
                ? quoteRepository.findById(quoteIdOrRef)
                        .or(() -> quoteRepository.findByQuoteReference(quoteIdOrRef))
                        .orElseThrow(() -> DesignReviewException.notFound("NOT_FOUND", "Không tìm thấy báo giá."))
                : quoteRepository.findTopByCustomOrderIdOrderByQuoteGenerationDesc(order.getId())
                        .orElseThrow(() -> DesignReviewException.notFound("NOT_FOUND", "Không tìm thấy báo giá nào cho đơn hàng này."));

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
            String managerReason
    ) {
        CustomOrder order = customOrderRepository.findByDesignRequestId(designRequestId)
                .orElseThrow(() -> DesignReviewException.notFound("NOT_FOUND", "Không tìm thấy đơn hàng tùy chỉnh cho yêu cầu này."));
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
                .orElseThrow(() -> DesignReviewException.notFound("NOT_FOUND", "Không tìm thấy đơn hàng gia công tùy chỉnh."));
        return mapToOrderDto(order);
    }

    @Transactional(readOnly = true)
    public CustomOrderProjectionDto getMyCustomOrder(Long memberAccountId, String customOrderIdOrRef) {
        CustomOrder order = customOrderRepository.findById(customOrderIdOrRef)
                .or(() -> customOrderRepository.findByCustomOrderReference(customOrderIdOrRef))
                .orElseThrow(() -> DesignReviewException.notFound("NOT_FOUND", "Không tìm thấy đơn hàng gia công tùy chỉnh."));

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
            String clientIp
    ) {
        log.info("Starting full payment for custom order: ref={}, member={}", customOrderIdOrRef, memberAccountId);

        CustomOrder order = customOrderRepository.findById(customOrderIdOrRef)
                .or(() -> customOrderRepository.findByCustomOrderReference(customOrderIdOrRef))
                .orElseThrow(() -> DesignReviewException.notFound("NOT_FOUND", "Không tìm thấy đơn hàng tùy chỉnh."));

        if (!order.getMemberAccountId().equals(memberAccountId)) {
            throw DesignReviewException.forbidden("ACCESS_DENIED", "Bạn không có quyền thanh toán cho đơn hàng này.");
        }

        CustomOrderQuote quote = quoteRepository.findTopByCustomOrderIdOrderByQuoteGenerationDesc(order.getId())
                .orElseThrow(() -> DesignReviewException.unprocessable("NO_QUOTE_FOUND", "Đơn hàng chưa có báo giá để thanh toán."));

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
            throw DesignReviewException.conflict("ORDER_ALREADY_PAID", "Đơn hàng này đã được thanh toán toàn bộ trước đó.");
        }

        if (!"QUOTE_READY".equalsIgnoreCase(quote.getStatus()) && !"FULL_PAYMENT_PENDING".equalsIgnoreCase(order.getStatus())) {
            throw DesignReviewException.conflict("QUOTE_NOT_PAYABLE",
                    "Báo giá chưa sẵn sàng để thanh toán (cần sự phê duyệt của Manager).");
        }

        String merchantRef = "CUST-" + order.getCustomOrderReference().replace("CO-", "") + "-" + System.currentTimeMillis();

        paymentOwnerRegistry.register(
                merchantRef,
                order.getCustomOrderReference(),
                PaymentOwnerKind.CUSTOM_ORDER_FULL_PAYMENT,
                quote.getTotalAmount(),
                quote.getCurrency()
        );

        String paymentUrl = vnpayAdapter.createRetailPaymentUrl(
                merchantRef,
                order.getCustomOrderReference(),
                quote.getTotalAmount(),
                Instant.now(),
                quote.getExpiresAt(),
                clientIp
        );

        CustomOrderPaymentContext paymentContext = CustomOrderPaymentContext.builder()
                .customOrderId(order.getId())
                .quoteId(quote.getId())
                .merchantReference(merchantRef)
                .paymentAttemptReference(merchantRef)
                .amountVnd(quote.getTotalAmount())
                .currency(quote.getCurrency())
                .status("INITIATED")
                .paymentUrl(paymentUrl)
                .expiresAt(quote.getExpiresAt())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
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
            String idempotencyKey
    ) {
        log.info("Member requesting new quote for custom order: order={}, member={}", customOrderIdOrRef, memberAccountId);

        CustomOrder order = customOrderRepository.findById(customOrderIdOrRef)
                .or(() -> customOrderRepository.findByCustomOrderReference(customOrderIdOrRef))
                .orElseThrow(() -> DesignReviewException.notFound("NOT_FOUND", "Không tìm thấy đơn hàng tùy chỉnh."));

        if (!order.getMemberAccountId().equals(memberAccountId)) {
            throw DesignReviewException.forbidden("ACCESS_DENIED", "Bạn không có quyền yêu cầu báo giá cho đơn hàng này.");
        }

        Optional<CustomOrderQuote> latestQuoteOpt = quoteRepository.findTopByCustomOrderIdOrderByQuoteGenerationDesc(order.getId());
        if (latestQuoteOpt.isPresent()) {
            CustomOrderQuote latestQuote = latestQuoteOpt.get();
            boolean isExpired = Instant.now().isAfter(latestQuote.getExpiresAt()) || "QUOTE_EXPIRED".equalsIgnoreCase(latestQuote.getStatus());
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

    @Transactional
    public void applyVerifiedFullPayment(
            String customOrderReference,
            String merchantReference,
            String providerTransactionNumber,
            long amountVnd,
            String currencyCode
    ) {
        log.info("Applying verified full payment: orderRef={}, merchantRef={}, amount={}",
                customOrderReference, merchantReference, amountVnd);

        CustomOrder order = customOrderRepository.findByCustomOrderReference(customOrderReference)
                .orElseThrow(() -> new IllegalStateException("Custom order not found: " + customOrderReference));

        CustomOrderQuote quote = quoteRepository.findTopByCustomOrderIdOrderByQuoteGenerationDesc(order.getId())
                .orElseThrow(() -> new IllegalStateException("No quote found for custom order: " + customOrderReference));

        if (amountVnd != quote.getTotalAmount()) {
            throw new IllegalStateException("Payment amount mismatch: expected " + quote.getTotalAmount() + ", got " + amountVnd);
        }

        quote.setStatus("PAID");
        quote.setPaymentStatus("PAID");
        quote.setUpdatedAt(Instant.now());
        quoteRepository.save(quote);

        order.setStatus("PAID");
        order.setProductionStatus("PENDING");
        order.setUpdatedAt(Instant.now());
        customOrderRepository.save(order);

        paymentContextRepository.findByMerchantReference(merchantReference).ifPresent(ctx -> {
            ctx.setStatus("PAID");
            ctx.setVnpayTransactionNo(providerTransactionNumber);
            ctx.setUpdatedAt(Instant.now());
            paymentContextRepository.save(ctx);
        });

        log.info("Custom order {} successfully marked PAID and queued for production", customOrderReference);
    }

    @Transactional
    public CustomOrderProjectionDto updateProductionOrFulfilmentStatus(
            String customOrderIdOrRef,
            String productionStatus,
            String fulfilmentStatus,
            String overallStatus
    ) {
        CustomOrder order = customOrderRepository.findById(customOrderIdOrRef)
                .or(() -> customOrderRepository.findByCustomOrderReference(customOrderIdOrRef))
                .orElseThrow(() -> DesignReviewException.notFound("NOT_FOUND", "Không tìm thấy đơn hàng tùy chỉnh."));

        if (productionStatus != null && !productionStatus.isBlank()) {
            order.setProductionStatus(productionStatus.trim());
        }
        if (fulfilmentStatus != null && !fulfilmentStatus.isBlank()) {
            order.setFulfilmentStatus(fulfilmentStatus.trim());
        }
        if (overallStatus != null && !overallStatus.isBlank()) {
            order.setStatus(overallStatus.trim());
        }
        order.setUpdatedAt(Instant.now());
        customOrderRepository.save(order);

        return mapToOrderDto(order);
    }

    public QuoteProjectionDto mapToQuoteDto(CustomOrderQuote quote) {
        if (quote == null) return null;
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
        if (order == null) return null;

        Optional<CustomOrderQuote> quoteOpt = quoteRepository.findTopByCustomOrderIdOrderByQuoteGenerationDesc(order.getId());
        QuoteProjectionDto quoteDto = quoteOpt.map(this::mapToQuoteDto).orElse(null);

        Object snapshot = null;
        Optional<CustomDesignReferenceSnapshot> snapshotOpt = snapshotRepository.findTopByRequestIdOrderByCreatedAtDesc(order.getDesignRequestId());
        if (snapshotOpt.isPresent()) {
            try {
                snapshot = objectMapper.readValue(snapshotOpt.get().getSnapshotJson(), new TypeReference<Map<String, Object>>() {});
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
                log.debug("Could not resolve account info for memberAccountId {}: {}", order.getMemberAccountId(), e.getMessage());
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

            List<DesignRequestImage> images = designRequestImageRepository.findByDesignRequestId(order.getDesignRequestId());
            if (images != null && !images.isEmpty()) {
                List<ImageProjectionDto> imageDtos = images.stream().map(img -> ImageProjectionDto.builder()
                        .imageReference(img.getImageReference())
                        .qualityStatus(img.getQualityStatus())
                        .qualityReason(img.getQualityReason())
                        .originalFilename(img.getOriginalFilename())
                        .fileSizeBytes(img.getFileSizeBytes())
                        .build()
                ).collect(Collectors.toList());
                builder.designImages(imageDtos);
            }
        }

        return builder.build();
    }
}
