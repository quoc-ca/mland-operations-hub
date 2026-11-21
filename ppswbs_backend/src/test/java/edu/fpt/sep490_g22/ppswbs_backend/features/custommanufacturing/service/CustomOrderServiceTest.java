package edu.fpt.sep490_g22.ppswbs_backend.features.custommanufacturing.service;

import edu.fpt.sep490_g22.ppswbs_backend.designreview.application.DesignReviewException;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.domain.DesignRequest;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.domain.ReviewStatus;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.infrastructure.CustomDesignReferenceSnapshotRepository;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.infrastructure.DesignRequestRepository;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.web.DesignReviewDtos.PaymentAttemptProjectionDto;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.web.DesignReviewDtos.QuoteProjectionDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.custommanufacturing.dao.CustomOrderIdempotencyRecordRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.custommanufacturing.dao.CustomOrderPaymentContextRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.custommanufacturing.dao.CustomOrderQuoteRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.custommanufacturing.dao.CustomOrderRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.custommanufacturing.domain.CustomOrder;
import edu.fpt.sep490_g22.ppswbs_backend.features.custommanufacturing.domain.CustomOrderIdempotencyRecord;
import edu.fpt.sep490_g22.ppswbs_backend.features.custommanufacturing.domain.CustomOrderQuote;
import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.config.VnpayProperties;
import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.entity.PaymentOwnerKind;
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
    private CustomDesignReferenceSnapshotRepository snapshotRepository;

    @Mock
    private PaymentOwnerRegistry paymentOwnerRegistry;

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
                snapshotRepository,
                paymentOwnerRegistry,
                vnpayAdapter
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

        customOrderService.applyVerifiedFullPayment(
                orderRef, "MERCH-1", "TXN-VNPAY-999", 4_500_000L, "VND"
        );

        assertThat(testQuote.getPaymentStatus()).isEqualTo("PAID");
        assertThat(testQuote.getStatus()).isEqualTo("PAID");
        assertThat(testOrder.getStatus()).isEqualTo("PAID");
        assertThat(testOrder.getProductionStatus()).isEqualTo("PENDING");

        verify(quoteRepository).save(testQuote);
        verify(customOrderRepository).save(testOrder);
    }
}
