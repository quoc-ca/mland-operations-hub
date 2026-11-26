package edu.fpt.sep490_g22.ppswbs_backend.features.custommanufacturing.service;

import java.util.List;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.application.DesignReviewException;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.domain.DesignRequest;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.domain.ReviewStatus;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.infrastructure.CustomDesignReferenceSnapshotRepository;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.infrastructure.DesignRequestImageRepository;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.infrastructure.DesignRequestRepository;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.web.DesignReviewDtos.PaymentAttemptProjectionDto;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.web.DesignReviewDtos.QuoteProjectionDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.custommanufacturing.dao.CustomOrderIdempotencyRecordRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.custommanufacturing.dao.CustomOrderPaymentContextRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.custommanufacturing.dao.CustomOrderQuoteRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.custommanufacturing.dao.CustomOrderRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.custommanufacturing.domain.CustomOrder;
import edu.fpt.sep490_g22.ppswbs_backend.features.custommanufacturing.domain.CustomOrderIdempotencyRecord;
import edu.fpt.sep490_g22.ppswbs_backend.features.custommanufacturing.domain.CustomOrderPaymentContext;
import edu.fpt.sep490_g22.ppswbs_backend.features.custommanufacturing.domain.CustomOrderQuote;
import edu.fpt.sep490_g22.ppswbs_backend.features.custommanufacturing.domain.CustomOrderShipment;
import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.config.VnpayProperties;
import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.facade.PaymentOwnerKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.entity.PaymentOwnerRegistration;
import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.facade.PaymentOwnerRegistry;
import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.service.VnpayChecksumService;
import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.service.VnpayPaymentGatewayCheckoutAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomOrderServiceTest {

    @Mock
    private CustomOrderRepository customOrderRepository;

    @Mock
    private CustomOrderQuoteRepository quoteRepository;

    @Mock
    private CustomOrderPaymentContextRepository paymentContextRepository;

    @Mock
    private CustomOrderIdempotencyRecordRepository idempotencyRecordRepository;

    @Mock
    private DesignRequestRepository designRequestRepository;

    @Mock
    private DesignRequestImageRepository designRequestImageRepository;

    @Mock
    private CustomDesignReferenceSnapshotRepository snapshotRepository;

    @Mock
    private PaymentOwnerRegistry paymentOwnerRegistry;

    @Mock
    private AccountFacade accountFacade;

    @Mock
    private CustomOrderShipmentService customOrderShipmentService;

    private VnpayPaymentGatewayCheckoutAdapter vnpayAdapter;

    private CustomOrderService customOrderService;

    private CustomOrder testOrder;
    private CustomOrderQuote testQuote;
    private final Long memberAccountId = 100L;
    private final String orderId = "order-uuid-123";
    private final String orderRef = "CO-D8FD507B";

    @BeforeEach
    void setUp() {
        var properties = new VnpayProperties();
        properties.setPayUrl("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html");
        properties.setReturnUrl("https://example.test/payment/return");
        properties.setHashSecret("unit-test-only-secret");
        properties.setTmnCode("TESTCODE");
        vnpayAdapter = new VnpayPaymentGatewayCheckoutAdapter(properties, new VnpayChecksumService(properties));

        customOrderService = new CustomOrderService(
                customOrderRepository,
                quoteRepository,
                paymentContextRepository,
                idempotencyRecordRepository,
                designRequestRepository,
                designRequestImageRepository,
                snapshotRepository,
                paymentOwnerRegistry,
                vnpayAdapter,
                accountFacade,
                customOrderShipmentService
        );

        testOrder = CustomOrder.builder()
                .id(orderId)
                .customOrderReference(orderRef)
                .memberAccountId(memberAccountId)
                .designRequestId("dr-123")
                .status("FULL_PAYMENT_PENDING")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        testQuote = CustomOrderQuote.builder()
                .id("quote-uuid-123")
                .customOrderId(orderId)
                .quoteReference("QUO-034DCFBF")
                .quoteGeneration(1)
                .totalAmount(4_500_000L)
                .currency("VND")
                .expiresAt(Instant.now().plus(Duration.ofDays(7)))
                .status("QUOTE_READY")
                .approvedByManager(true)
                .paymentStatus("UNPAID")
                .paymentMode("FULL_CUSTOM_ORDER_PAYMENT")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("startFullPayment registers PaymentOwnerKind.CUSTOM_ORDER_FULL_PAYMENT with exact 25 characters and returns payment attempt")
    void startFullPayment_Success_RegistersCorrectOwnerKindAndReturnsUrl() {
        when(customOrderRepository.findById(orderId)).thenReturn(Optional.of(testOrder));
        when(quoteRepository.findTopByCustomOrderIdOrderByQuoteGenerationDesc(orderId)).thenReturn(Optional.of(testQuote));

        PaymentAttemptProjectionDto result = customOrderService.startFullPayment(
                memberAccountId, orderId, "idem-key-1", "127.0.0.1"
        );

        assertThat(result).isNotNull();
        assertThat(result.getPaymentAttemptReference()).isNotNull();
        assertThat(result.getStatus()).isEqualTo("INITIATED");
        assertThat(result.getPaymentUrl()).contains("sandbox.vnpayment.vn");

        ArgumentCaptor<PaymentOwnerKind> kindCaptor = ArgumentCaptor.forClass(PaymentOwnerKind.class);
        ArgumentCaptor<String> merchantRefCaptor = ArgumentCaptor.forClass(String.class);

        verify(paymentOwnerRegistry).register(
                merchantRefCaptor.capture(),
                eq(orderRef),
                kindCaptor.capture(),
                eq(4_500_000L),
                eq("VND")
        );

        assertThat(kindCaptor.getValue()).isEqualTo(PaymentOwnerKind.CUSTOM_ORDER_FULL_PAYMENT);
        assertThat(kindCaptor.getValue().name().length()).isEqualTo(25);
        assertThat(merchantRefCaptor.getValue()).startsWith("CUST-");
    }

    @Test
    @DisplayName("startFullPayment throws unprocessable error when quote has expired and does not register payment owner")
    void startFullPayment_QuoteExpired_ThrowsExceptionAndDoesNotRegister() {
        testQuote.setExpiresAt(Instant.now().minus(Duration.ofMinutes(5)));
        when(customOrderRepository.findById(orderId)).thenReturn(Optional.of(testOrder));
        when(quoteRepository.findTopByCustomOrderIdOrderByQuoteGenerationDesc(orderId)).thenReturn(Optional.of(testQuote));

        assertThatThrownBy(() -> customOrderService.startFullPayment(memberAccountId, orderId, null, "127.0.0.1"))
                .isInstanceOf(DesignReviewException.class)
                .hasMessageContaining("hết hạn thanh toán");

        assertThat(testQuote.getStatus()).isEqualTo("QUOTE_EXPIRED");
        assertThat(testOrder.getStatus()).isEqualTo("QUOTE_EXPIRED");
        verify(quoteRepository).save(testQuote);
        verify(customOrderRepository).save(testOrder);
        verifyNoInteractions(paymentOwnerRegistry);
    }

    @Test
    @DisplayName("startFullPayment throws conflict error when order is not in FULL_PAYMENT_PENDING or quote not QUOTE_READY")
    void startFullPayment_NotPayableState_ThrowsConflict() {
        testOrder.setStatus("QUOTE_PENDING");
        testQuote.setStatus("PENDING_APPROVAL");
        when(customOrderRepository.findById(orderId)).thenReturn(Optional.of(testOrder));
        when(quoteRepository.findTopByCustomOrderIdOrderByQuoteGenerationDesc(orderId)).thenReturn(Optional.of(testQuote));

        assertThatThrownBy(() -> customOrderService.startFullPayment(memberAccountId, orderId, null, "127.0.0.1"))
                .isInstanceOf(DesignReviewException.class)
                .hasMessageContaining("Báo giá chưa sẵn sàng để thanh toán");

        verifyNoInteractions(paymentOwnerRegistry);
    }

    @Test
    @DisplayName("startFullPayment throws conflict error when order or quote is already PAID")
    void startFullPayment_AlreadyPaid_ThrowsConflict() {
        testOrder.setStatus("PAID");
        testQuote.setStatus("PAID");
        when(customOrderRepository.findById(orderId)).thenReturn(Optional.of(testOrder));
        when(quoteRepository.findTopByCustomOrderIdOrderByQuoteGenerationDesc(orderId)).thenReturn(Optional.of(testQuote));

        assertThatThrownBy(() -> customOrderService.startFullPayment(memberAccountId, orderId, null, "127.0.0.1"))
                .isInstanceOf(DesignReviewException.class)
                .hasMessageContaining("đã được thanh toán toàn bộ");

        verifyNoInteractions(paymentOwnerRegistry);
    }

    @Test
    @DisplayName("startFullPayment throws forbidden error when called by non-owner member")
    void startFullPayment_NonOwner_ThrowsForbidden() {
        when(customOrderRepository.findById(orderId)).thenReturn(Optional.of(testOrder));

        assertThatThrownBy(() -> customOrderService.startFullPayment(999L, orderId, null, "127.0.0.1"))
                .isInstanceOf(DesignReviewException.class)
                .hasMessageContaining("Bạn không có quyền thanh toán cho đơn hàng này");

        verifyNoInteractions(paymentOwnerRegistry);
    }

    @Test
    @DisplayName("applyVerifiedFullPayment transitions custom order and quote to PAID and clears hold")
    void applyVerifiedFullPayment_Success_TransitionsToPaid() {
        when(customOrderRepository.findByCustomOrderReference(orderRef)).thenReturn(Optional.of(testOrder));
        when(quoteRepository.findTopByCustomOrderIdOrderByQuoteGenerationDesc(orderId)).thenReturn(Optional.of(testQuote));

        var context = paymentContext("quote-uuid-123");
        when(paymentContextRepository.findByMerchantReference("MERCH-1")).thenReturn(Optional.of(context));

        customOrderService.applyVerifiedFullPayment(
                orderRef, "MERCH-1", "TXN-VNPAY-999", 4_500_000L, "VND"
        );

        assertThat(context.getStatus()).isEqualTo("PAID");

        assertThat(testQuote.getPaymentStatus()).isEqualTo("PAID");
        assertThat(testQuote.getStatus()).isEqualTo("PAID");
        assertThat(testOrder.getStatus()).isEqualTo("PAID");
        assertThat(testOrder.getProductionStatus()).isEqualTo("PENDING");

        verify(quoteRepository).save(testQuote);
        verify(customOrderRepository).save(testOrder);
    }

    @Test
    @DisplayName("applyVerifiedFullPayment keeps late evidence for review and does not pay the order")
    void applyVerifiedFullPayment_ExpiredQuote_RequiresReview() {
        testQuote.setExpiresAt(Instant.now().minus(Duration.ofMinutes(1)));

        assertReviewRequired(paymentContext("quote-uuid-123"));
    }

    @Test
    @DisplayName("applyVerifiedFullPayment keeps evidence of a replaced quote for review")
    void applyVerifiedFullPayment_ReplacedQuote_RequiresReview() {
        assertReviewRequired(paymentContext("older-quote"));
    }

    @Test
    @DisplayName("applyVerifiedFullPayment keeps a second payment of a paid order for review")
    void applyVerifiedFullPayment_AlreadyPaidOrder_RequiresReview() {
        testOrder.setStatus("IN_PRODUCTION");
        testQuote.setStatus("PAID");
        var context = paymentContext("quote-uuid-123");
        when(customOrderRepository.findByCustomOrderReference(orderRef)).thenReturn(Optional.of(testOrder));
        when(quoteRepository.findTopByCustomOrderIdOrderByQuoteGenerationDesc(orderId)).thenReturn(Optional.of(testQuote));
        when(paymentContextRepository.findByMerchantReference("MERCH-1")).thenReturn(Optional.of(context));

        customOrderService.applyVerifiedFullPayment(orderRef, "MERCH-1", "TXN-2", 4_500_000L, "VND");

        assertThat(context.getStatus()).isEqualTo("REVIEW_REQUIRED");
        assertThat(testOrder.getStatus()).isEqualTo("IN_PRODUCTION");
        verify(customOrderRepository, never()).save(any());
    }

    @Test
    @DisplayName("status update moves a paid order one stage forward and derives the other statuses")
    void updateStatus_NextStage_Succeeds() {
        paidOrder("PAID");

        customOrderService.updateProductionOrFulfilmentStatus(orderId, null, null, "IN_PRODUCTION");

        assertThat(testOrder.getStatus()).isEqualTo("IN_PRODUCTION");
        assertThat(testOrder.getProductionStatus()).isEqualTo("IN_PRODUCTION");
        verify(customOrderRepository).save(testOrder);
    }

    @Test
    @DisplayName("startFullPayment limits payment gateway session to at most 10 minutes even when quote validity is 7 days")
    void startFullPayment_Uses10MinuteWindowDecoupledFrom72hQuote() {
        testQuote.setExpiresAt(Instant.now().plus(Duration.ofDays(7)));
        when(customOrderRepository.findById(orderId)).thenReturn(Optional.of(testOrder));
        when(quoteRepository.findTopByCustomOrderIdOrderByQuoteGenerationDesc(orderId)).thenReturn(Optional.of(testQuote));

        var attempt = customOrderService.startFullPayment(memberAccountId, orderId, "idem-10m", "127.0.0.1");

        assertThat(attempt).isNotNull();
        ArgumentCaptor<CustomOrderPaymentContext> contextCaptor = ArgumentCaptor.forClass(CustomOrderPaymentContext.class);
        verify(paymentContextRepository).save(contextCaptor.capture());

        CustomOrderPaymentContext savedContext = contextCaptor.getValue();
        assertThat(savedContext.getExpiresAt()).isNotNull();
        // Payment window must expire around 10 minutes from now (between 9 and 11 minutes), not 7 days
        Duration remaining = Duration.between(Instant.now(), savedContext.getExpiresAt());
        assertThat(remaining.toMinutes()).isLessThanOrEqualTo(10);
        assertThat(remaining.toMinutes()).isGreaterThanOrEqualTo(9);
    }

    @Test
    @DisplayName("status update to READY_FOR_FULFILMENT automatically delegates shipment creation to CustomOrderShipmentService")
    void updateStatus_ReadyForFulfilment_CreatesShipment() {
        paidOrder("IN_PRODUCTION");

        customOrderService.updateProductionOrFulfilmentStatus(orderId, null, null, "READY_FOR_FULFILMENT");

        assertThat(testOrder.getStatus()).isEqualTo("READY_FOR_FULFILMENT");
        assertThat(testOrder.getProductionStatus()).isEqualTo("COMPLETED");
        assertThat(testOrder.getFulfilmentStatus()).isEqualTo("READY_FOR_FULFILMENT");
        verify(customOrderShipmentService).createShipmentForOrder(testOrder);
        verify(customOrderRepository).save(testOrder);
    }

    @Test
    @DisplayName("status update to CLOSED directly is rejected because only courier delivery can close order")
    void updateStatus_DirectClosed_ThrowsConflict() {
        paidOrder("READY_FOR_FULFILMENT");
        when(customOrderShipmentService.findByCustomOrderReference(orderRef))
                .thenReturn(Optional.of(CustomOrderShipment.builder().status("PREPARING").build()));

        assertThatThrownBy(() -> customOrderService.updateProductionOrFulfilmentStatus(orderId, null, null, "CLOSED"))
                .isInstanceOf(DesignReviewException.class)
                .hasMessageContaining("Đơn hàng chỉ được đóng (CLOSED) khi bưu tá đã giao hàng thành công (DELIVERED).");

        verify(customOrderRepository, never()).save(testOrder);
    }

    @Test
    @DisplayName("staffAdvanceShipmentToReadyForCarrier delegates to CustomOrderShipmentService and returns updated order")
    void staffAdvanceShipmentToReadyForCarrier_Delegates() {
        when(customOrderRepository.findById(orderId)).thenReturn(Optional.of(testOrder));

        var dto = customOrderService.staffAdvanceShipmentToReadyForCarrier(orderId);

        assertThat(dto).isNotNull();
        verify(customOrderShipmentService).staffAdvanceToReadyForCarrier(orderRef);
    }

    @Test
    @DisplayName("status update refuses skipped stages, unknown statuses and unpaid orders")
    void updateStatus_Invalid_Rejected() {
        paidOrder("PAID");
        assertThatThrownBy(() -> customOrderService.updateProductionOrFulfilmentStatus(orderId, null, null, "CLOSED"))
                .isInstanceOf(DesignReviewException.class).hasMessageContaining("bước kế tiếp");
        assertThatThrownBy(() -> customOrderService.updateProductionOrFulfilmentStatus(orderId, null, null, "PAID"))
                .isInstanceOf(DesignReviewException.class).hasMessageContaining("không hợp lệ");
        testQuote.setPaymentStatus("UNPAID");
        assertThatThrownBy(() -> customOrderService.updateProductionOrFulfilmentStatus(orderId, null, null, "IN_PRODUCTION"))
                .isInstanceOf(DesignReviewException.class).hasMessageContaining("chưa được thanh toán");
        verify(customOrderRepository, never()).save(any());
    }

    private void paidOrder(String status) {
        testOrder.setStatus(status);
        testQuote.setStatus("PAID");
        testQuote.setPaymentStatus("PAID");
        when(customOrderRepository.findById(orderId)).thenReturn(Optional.of(testOrder));
        lenient().when(quoteRepository.findTopByCustomOrderIdOrderByQuoteGenerationDesc(orderId))
                .thenReturn(Optional.of(testQuote));
    }

    private CustomOrderPaymentContext paymentContext(String quoteId) {
        return CustomOrderPaymentContext.builder()
                .customOrderId(orderId)
                .quoteId(quoteId)
                .merchantReference("MERCH-1")
                .status("INITIATED")
                .build();
    }

    private void assertReviewRequired(CustomOrderPaymentContext context) {
        when(customOrderRepository.findByCustomOrderReference(orderRef)).thenReturn(Optional.of(testOrder));
        when(quoteRepository.findTopByCustomOrderIdOrderByQuoteGenerationDesc(orderId)).thenReturn(Optional.of(testQuote));
        when(paymentContextRepository.findByMerchantReference("MERCH-1")).thenReturn(Optional.of(context));

        customOrderService.applyVerifiedFullPayment(orderRef, "MERCH-1", "TXN-LATE", 4_500_000L, "VND");

        assertThat(context.getStatus()).isEqualTo("REVIEW_REQUIRED");
        assertThat(testOrder.getStatus()).isEqualTo("FULL_PAYMENT_PENDING");
        assertThat(testQuote.getPaymentStatus()).isEqualTo("UNPAID");
        verify(quoteRepository, never()).save(any());
    }

    @Test
    @DisplayName("listAllCustomOrdersForOperations filters by status when status parameter is supplied")
    void listAllCustomOrdersForOperations_WithStatus_QueriesFiltered() {
        when(customOrderRepository.findByStatusOrderByCreatedAtDesc("PAID")).thenReturn(List.of(testOrder));

        var result = customOrderService.listAllCustomOrdersForOperations("PAID");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCustomOrderReference()).isEqualTo(orderRef);
        verify(customOrderRepository).findByStatusOrderByCreatedAtDesc("PAID");
    }

    @Test
    @DisplayName("listAllCustomOrdersForOperations returns all orders when status is null or ALL")
    void listAllCustomOrdersForOperations_All_QueriesAll() {
        when(customOrderRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(testOrder));

        var result = customOrderService.listAllCustomOrdersForOperations("ALL");

        assertThat(result).hasSize(1);
        verify(customOrderRepository).findAllByOrderByCreatedAtDesc();
    }

    @Test
    @DisplayName("getCustomOrderForOperations resolves order by id or reference")
    void getCustomOrderForOperations_Success() {
        when(customOrderRepository.findById(orderId)).thenReturn(Optional.of(testOrder));

        var result = customOrderService.getCustomOrderForOperations(orderId);

        assertThat(result.getCustomOrderReference()).isEqualTo(orderRef);
    }
}
