package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

import edu.fpt.sep490_g22.ppswbs_backend.common.payment.PaymentGatewayCheckoutPort;
import edu.fpt.sep490_g22.ppswbs_backend.common.payment.PaymentOwnerRegistrationPort;
import edu.fpt.sep490_g22.ppswbs_backend.common.payment.VnpayMerchantOperationResult;
import edu.fpt.sep490_g22.ppswbs_backend.common.payment.VnpayMerchantOperationsPort;
import edu.fpt.sep490_g22.ppswbs_backend.common.payment.VnpayQueryDrCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.ConfirmedBookingFinancialProjection;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementCharge;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementEvidenceConclusion;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementFinalBill;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementFinalPaymentAttempt;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopBookingSettlementAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FinalPaymentCheckoutServiceTest {
    private final SettlementFinalBillRepository bills = mock(SettlementFinalBillRepository.class);
    private final SettlementChargeRepository charges = mock(SettlementChargeRepository.class);
    private final SettlementEvidenceConclusionRepository evidence = mock(SettlementEvidenceConclusionRepository.class);
    private final SettlementFinalPaymentAttemptRepository attempts = mock(SettlementFinalPaymentAttemptRepository.class);
    private final SettlementBusinessAuditRepository audits = mock(SettlementBusinessAuditRepository.class);
    private final WorkshopBookingSettlementAdapter booking = mock(WorkshopBookingSettlementAdapter.class);
    private final WorkshopSettlementAuthorizationService authorization = mock(WorkshopSettlementAuthorizationService.class);
    private final SettlementIdempotencyService idempotency = mock(SettlementIdempotencyService.class);
    private final PaymentGatewayCheckoutPort checkout = mock(PaymentGatewayCheckoutPort.class);
    private final PaymentOwnerRegistrationPort owners = mock(PaymentOwnerRegistrationPort.class);
    private final VnpayMerchantOperationsPort merchantOperations = mock(VnpayMerchantOperationsPort.class);
    private final FinalPaymentService finalPaymentService = mock(FinalPaymentService.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final AtomicReference<SettlementFinalPaymentAttempt> savedAttempt = new AtomicReference<>();
    private FinalPaymentCheckoutService service;

    private final CurrentAccountActor manager = new CurrentAccountActor(7L, ActorRole.MANAGER,
            ActorStatus.ACTIVE, true, List.of());

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(new SimpleTransactionStatus());
        doNothing().when(transactionManager).commit(any(TransactionStatus.class));
        doNothing().when(transactionManager).rollback(any(TransactionStatus.class));
        when(booking.confirmedProjection("BK-1")).thenReturn(new ConfirmedBookingFinancialProjection(
                "PKG-1", 150_000L, 150_000L, 50_000L, 100_000L, 1, List.of("SESSION-1"), 77L));
        when(bills.findByBookingReferenceForUpdate("BK-1")).thenReturn(Optional.of(openBill()));
        when(charges.findByBillReferenceAndLifecycleStatusOrderByIdAsc("BILL-1", "CONFIRMED"))
                .thenReturn(List.of(charge(12_000L)));
        when(evidence.findByBookingReferenceAndConclusion("BK-1", "FINAL_PAYMENT_VERIFIED"))
                .thenReturn(List.of(priorPayment(20_000L)));
        when(idempotency.findOutcomeReference(anyString(), anyString())).thenReturn(Optional.empty());
        when(idempotency.execute(anyString(), anyString(), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked") Supplier<String> supplier = invocation.getArgument(2);
            return new SettlementIdempotencyResult(false, supplier.get());
        });
        when(attempts.saveAndFlush(any(SettlementFinalPaymentAttempt.class))).thenAnswer(invocation -> {
            SettlementFinalPaymentAttempt attempt = invocation.getArgument(0);
            attempt.setId(1L);
            savedAttempt.set(attempt);
            return attempt;
        });
        when(attempts.findByAttemptReference(anyString())).thenAnswer(invocation ->
                Optional.ofNullable(savedAttempt.get()));
        when(checkout.createFinalPaymentUrl(anyString(), eq("BK-1"), anyLong(), any(), any(), anyString()))
                .thenReturn("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?opaque=1");
        when(authorization.requireManagerOrStaff(manager, 77L)).thenReturn(manager);
        service = new FinalPaymentCheckoutService(bills, charges, evidence, attempts, audits, booking,
                authorization, idempotency, checkout, owners, merchantOperations, finalPaymentService,
                transactionManager, "203.0.113.10");
    }

    @Test
    void createsOwnerBoundCheckoutForExactServerDerivedOutstandingAmountAndTenMinuteExpiry() {
        when(attempts.findFirstByBookingReferenceOrderByCreatedAtDescIdDesc("BK-1"))
                .thenReturn(Optional.empty());

        var response = service.start("BK-1", "idempotency-key-final-payment-1", "corr-1", "127.0.0.1", manager);

        assertThat(response.amountVnd()).isEqualTo(92_000L);
        assertThat(response.currency()).isEqualTo("VND");
        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.paymentUrl()).startsWith("https://sandbox.vnpayment.vn/");
        assertThat(Duration.between(Instant.now(), response.expiresAt()))
                .isBetween(Duration.ofMinutes(9), Duration.ofMinutes(10));
        verify(owners).registerOwner(anyString(), eq("BK-1"), eq("FINAL_PAYMENT"), eq(92_000L),
                eq("VND"), argThat(date -> date.matches("[0-9]{14}")));
        verify(checkout).createFinalPaymentUrl(anyString(), eq("BK-1"), eq(92_000L), any(),
                eq(response.expiresAt()), eq("127.0.0.1"));
    }

    @Test
    void expiredAttemptRequiresSignedQueryDrNotFoundBeforeCreatingNextCheckout() {
        SettlementFinalPaymentAttempt expired = expiredPendingAttempt();
        when(attempts.findFirstByBookingReferenceOrderByCreatedAtDescIdDesc("BK-1"))
                .thenReturn(Optional.of(expired));
        when(attempts.findByAttemptReferenceForUpdate(expired.getAttemptReference())).thenReturn(Optional.of(expired));
        when(merchantOperations.queryTransaction(any(VnpayQueryDrCommand.class))).thenReturn(
                new VnpayMerchantOperationResult("QDR-1", "QUERYDR", "RECONCILED", "91", null,
                        null, null, expired.getMerchantReference(), null, true));

        var response = service.start("BK-1", "idempotency-key-final-payment-2", "corr-2", "127.0.0.1", manager);

        assertThat(expired.getLifecycleStatus()).isEqualTo("VERIFIED_UNPAID");
        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.amountVnd()).isEqualTo(92_000L);
        verify(merchantOperations).queryTransaction(argThat(command ->
                command.merchantReference().equals(expired.getMerchantReference())
                        && command.originalTransactionDate().equals(expired.getProviderCreateDate())));
        verify(owners).registerOwner(anyString(), eq("BK-1"), eq("FINAL_PAYMENT"), eq(92_000L),
                eq("VND"), anyString());
    }

    private static SettlementFinalBill openBill() {
        return SettlementFinalBill.builder().id(1L).billReference("BILL-1").bookingReference("BK-1")
                .postPromotionAmountVnd(150_000L).acceptedDepositAmountVnd(50_000L)
                .remainingAmountVnd(100_000L).currencyCode("VND").lifecycleStatus("OPEN")
                .frozenAt(Instant.now()).createdAt(Instant.now()).build();
    }

    private static SettlementCharge charge(long amount) {
        return SettlementCharge.builder().chargeReference("CHARGE-1").billReference("BILL-1")
                .sourceCategory("MANUAL").description("Materials").amountVnd(amount).currencyCode("VND")
                .lifecycleStatus("CONFIRMED").createdAt(Instant.now()).build();
    }

    private static SettlementEvidenceConclusion priorPayment(long amount) {
        return SettlementEvidenceConclusion.builder().evidenceReference("PAYMENT-PRIOR")
                .bookingReference("BK-1").providerTransactionReference("MERCHANT-PRIOR")
                .conclusion("FINAL_PAYMENT_VERIFIED").amountVnd(amount).currencyCode("VND")
                .operationReference("PAYMENT-PRIOR").createdAt(Instant.now()).build();
    }

    private static SettlementFinalPaymentAttempt expiredPendingAttempt() {
        Instant createdAt = Instant.now().minus(Duration.ofMinutes(12));
        return SettlementFinalPaymentAttempt.builder().id(4L).attemptReference("FPA-EXPIRED")
                .billReference("BILL-1").bookingReference("BK-1").operationReference("OP-OLD")
                .merchantReference("FIN-OLD").amountVnd(92_000L).currencyCode("VND")
                .providerCreateDate("20261031100000").paymentUrlSnapshot("https://expired.example.test")
                .lifecycleStatus("PENDING").createdAt(createdAt).expiresAt(createdAt.plus(Duration.ofMinutes(10)))
                .build();
    }
}
