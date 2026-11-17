package edu.fpt.sep490_g22.ppswbs_backend.workshopsettlement;

import edu.fpt.sep490_g22.ppswbs_backend.common.payment.PaymentGatewayCheckoutPort;
import edu.fpt.sep490_g22.ppswbs_backend.common.payment.PaymentOwnerRegistrationPort;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.OtaAddonPaymentDtos.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OtaAddonPaymentServiceTest {

    @Mock private SettlementAddonBillRepository addonBills;
    @Mock private SettlementAddonPaymentAttemptRepository addonAttempts;
    @Mock private SettlementAddonRefundRepository addonRefunds;
    @Mock private SettlementCaseRepository cases;
    @Mock private SettlementRefundAllocationRepository allocations;
    @Mock private SettlementEvidenceConclusionRepository evidence;
    @Mock private SettlementBusinessAuditRepository audits;
    @Mock private SettlementIdempotencyService idempotency;
    @Mock private WorkshopSettlementAuthorizationService authorization;
    @Mock private PaymentGatewayCheckoutPort checkout;
    @Mock private PaymentOwnerRegistrationPort owners;
    @Mock private WorkshopRefundService refunds;
    @Mock private PlatformTransactionManager transactionManager;

    private OtaAddonPaymentService service;

    private final CurrentAccountActor manager = new CurrentAccountActor(10L, ActorRole.MANAGER, ActorStatus.ACTIVE, true, List.of());
    private final CurrentAccountActor staffInScope = new CurrentAccountActor(20L, ActorRole.STAFF, ActorStatus.ACTIVE, true, List.of(1L));
    private final CurrentAccountActor staffOutScope = new CurrentAccountActor(30L, ActorRole.STAFF, ActorStatus.ACTIVE, true, List.of(2L));

    private SettlementAddonBill openBill;
    private SettlementAddonBill paidBill;

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());

        service = new OtaAddonPaymentService(
                addonBills, addonAttempts, addonRefunds, cases, allocations,
                evidence, audits, idempotency, authorization, checkout, owners,
                refunds, transactionManager);

        when(authorization.requireManager(manager)).thenReturn(manager);
        when(authorization.requireManager(staffInScope))
                .thenThrow(new WorkshopSettlementAuthorizationException("MANAGER_REQUIRED"));
        when(authorization.requireManagerOrAssignedStaff(manager, 1L, null)).thenReturn(manager);
        when(authorization.requireManagerOrAssignedStaff(staffInScope, 1L, null)).thenReturn(staffInScope);
        when(authorization.requireManagerOrAssignedStaff(staffOutScope, 1L, null))
                .thenThrow(new WorkshopSettlementAuthorizationException("LOCATION_SCOPE_DENIED"));

        openBill = SettlementAddonBill.builder()
                .id(1L)
                .billReference("BILL-ADDON-1")
                .channelBookingReference("OTA-BK-1")
                .quoteReference("QUOTE-1")
                .itemCode("ADDON-ENGRAVE")
                .itemDescription("Engraving")
                .amountMinor(150000L)
                .currencyCode("VND")
                .customerConsentReference("CONSENT-1")
                .staffActorReference("STAFF-20")
                .locationId(1L)
                .lifecycleStatus("OPEN")
                .createdAt(Instant.now())
                .build();

        paidBill = SettlementAddonBill.builder()
                .id(2L)
                .billReference("BILL-ADDON-PAID")
                .channelBookingReference("OTA-BK-PAID")
                .quoteReference("QUOTE-2")
                .itemCode("ADDON-BOX")
                .itemDescription("Premium Box")
                .amountMinor(200000L)
                .currencyCode("VND")
                .customerConsentReference("CONSENT-2")
                .staffActorReference("STAFF-20")
                .locationId(1L)
                .lifecycleStatus("PAID")
                .createdAt(Instant.now())
                .build();

        when(idempotency.findOutcomeReference(any(), any())).thenReturn(Optional.empty());
        when(idempotency.execute(any(), any(), any(Supplier.class))).thenAnswer(invocation -> {
            Supplier<?> action = invocation.getArgument(2);
            Object out = action.get();
            return new SettlementIdempotencyResult(false, String.valueOf(out));
        });
    }

    @Test
    void staffInScopeCanStartPaymentAttempt() {
        when(addonBills.findByBillReferenceForUpdate("BILL-ADDON-1")).thenReturn(Optional.of(openBill));
        when(checkout.createShopAddonUrl(any(), eq("OTA-BK-1"), eq(150000L), any(), any(), any()))
                .thenReturn("https://vnpay.test/pay?addon=1");
        when(addonAttempts.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(addonAttempts.findByAttemptReference(any())).thenAnswer(inv -> Optional.of(
                SettlementAddonPaymentAttempt.builder()
                        .attemptReference(inv.getArgument(0))
                        .billReference("BILL-ADDON-1")
                        .merchantReference("OTA-REF-1")
                        .amountVnd(150000L)
                        .currencyCode("VND")
                        .lifecycleStatus("PENDING")
                        .paymentUrlSnapshot("https://vnpay.test/pay?addon=1")
                        .expiresAt(Instant.now().plus(10, ChronoUnit.MINUTES))
                        .build()
        ));

        OtaAddonPaymentAttemptResponse response = service.startPaymentAttempt(
                "BILL-ADDON-1", "IDEMP-KEY-1234567890", "CORR-1", "127.0.0.1", staffInScope);

        assertThat(response).isNotNull();
        assertThat(response.billReference()).isEqualTo("BILL-ADDON-1");
        assertThat(response.amountVnd()).isEqualTo(150000L);
        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.paymentUrl()).isEqualTo("https://vnpay.test/pay?addon=1");

        verify(owners).registerOwner(any(), eq("OTA-BK-1"), eq("SHOP_ADDON"), eq(150000L), eq("VND"), any());
    }

    @Test
    void staffOutOfScopeIsRejectedFromStartingPayment() {
        when(addonBills.findByBillReferenceForUpdate("BILL-ADDON-1")).thenReturn(Optional.of(openBill));

        assertThatThrownBy(() -> service.startPaymentAttempt(
                "BILL-ADDON-1", "IDEMP-KEY-1234567890", "CORR-1", "127.0.0.1", staffOutScope))
                .isInstanceOf(WorkshopSettlementAuthorizationException.class);
    }

    @Test
    void managerCanStartPaymentGlobally() {
        when(addonBills.findByBillReferenceForUpdate("BILL-ADDON-1")).thenReturn(Optional.of(openBill));
        when(checkout.createShopAddonUrl(any(), eq("OTA-BK-1"), eq(150000L), any(), any(), any()))
                .thenReturn("https://vnpay.test/pay?addon=1");
        when(addonAttempts.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(addonAttempts.findByAttemptReference(any())).thenAnswer(inv -> Optional.of(
                SettlementAddonPaymentAttempt.builder()
                        .attemptReference(inv.getArgument(0))
                        .billReference("BILL-ADDON-1")
                        .merchantReference("OTA-REF-1")
                        .amountVnd(150000L)
                        .currencyCode("VND")
                        .lifecycleStatus("PENDING")
                        .paymentUrlSnapshot("https://vnpay.test/pay?addon=1")
                        .expiresAt(Instant.now().plus(10, ChronoUnit.MINUTES))
                        .build()
        ));

        OtaAddonPaymentAttemptResponse response = service.startPaymentAttempt(
                "BILL-ADDON-1", "IDEMP-KEY-1234567890", "CORR-1", "127.0.0.1", manager);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo("PENDING");
    }

    @Test
    void paidBillCannotStartNewPaymentAttempt() {
        when(addonBills.findByBillReferenceForUpdate("BILL-ADDON-PAID")).thenReturn(Optional.of(paidBill));

        assertThatThrownBy(() -> service.startPaymentAttempt(
                "BILL-ADDON-PAID", "IDEMP-KEY-1234567890", "CORR-1", "127.0.0.1", manager))
                .isInstanceOf(WorkshopSettlementConflictException.class)
                .hasMessageContaining("BILL_NOT_OPEN");
    }

    @Test
    void activePendingAttemptReturnsExistingUrl() {
        SettlementAddonPaymentAttempt existingAttempt = SettlementAddonPaymentAttempt.builder()
                .attemptReference("OAA-ACTIVE")
                .billReference("BILL-ADDON-1")
                .merchantReference("OTA-MERCH-ACTIVE")
                .amountVnd(150000L)
                .currencyCode("VND")
                .lifecycleStatus("PENDING")
                .paymentUrlSnapshot("https://vnpay.test/existing")
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plus(5, ChronoUnit.MINUTES))
                .build();

        when(addonBills.findByBillReferenceForUpdate("BILL-ADDON-1")).thenReturn(Optional.of(openBill));
        when(addonAttempts.findFirstByBillReferenceOrderByCreatedAtDescIdDesc("BILL-ADDON-1"))
                .thenReturn(Optional.of(existingAttempt));

        OtaAddonPaymentAttemptResponse response = service.startPaymentAttempt(
                "BILL-ADDON-1", "IDEMP-KEY-1234567890", "CORR-1", "127.0.0.1", staffInScope);

        assertThat(response.attemptReference()).isEqualTo("OAA-ACTIVE");
        assertThat(response.paymentUrl()).isEqualTo("https://vnpay.test/existing");
        verifyNoInteractions(checkout);
    }

    @Test
    void managerCanCreateOverchargeRefundForPaidBill() {
        when(addonBills.findByBillReferenceForUpdate("BILL-ADDON-PAID")).thenReturn(Optional.of(paidBill));

        SettlementAddonPaymentAttempt paidAttempt = SettlementAddonPaymentAttempt.builder()
                .attemptReference("OAA-PAID")
                .billReference("BILL-ADDON-PAID")
                .merchantReference("MERCH-1")
                .amountVnd(200000L)
                .currencyCode("VND")
                .lifecycleStatus("VERIFIED_PAID")
                .build();
        when(addonAttempts.findAllByBillReferenceOrderByCreatedAtDescIdDesc("BILL-ADDON-PAID"))
                .thenReturn(List.of(paidAttempt));

        SettlementEvidenceConclusion ev = SettlementEvidenceConclusion.builder()
                .evidenceReference("VNPAY-111111")
                .bookingReference("OTA-BK-PAID")
                .providerTransactionReference("MERCH-1")
                .providerTransactionNumber("111111")
                .originalProviderCreateDate("20261106120000")
                .conclusion("SHOP_ADDON_VERIFIED")
                .amountVnd(200000L)
                .currencyCode("VND")
                .build();
        when(evidence.findByBookingReferenceAndConclusion("OTA-BK-PAID", "SHOP_ADDON_VERIFIED"))
                .thenReturn(List.of(ev));

        when(allocations.findAllByOriginalPaymentReference("MERCH-1")).thenReturn(List.of());

        when(cases.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(addonRefunds.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(allocations.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(addonRefunds.findByRefundReference(any())).thenAnswer(inv -> Optional.of(
                SettlementAddonRefund.builder()
                        .refundReference(inv.getArgument(0))
                        .caseReference("SETTLE-ADDON-CASE-1")
                        .billReference("BILL-ADDON-PAID")
                        .channelBookingReference("OTA-BK-PAID")
                        .amountVnd(50000L)
                        .currencyCode("VND")
                        .reason("Wrong add-on size requested")
                        .managerActorReference("10")
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build()
        ));
        when(cases.findByCaseReference("SETTLE-ADDON-CASE-1")).thenReturn(Optional.of(
                SettlementCase.builder()
                        .caseReference("SETTLE-ADDON-CASE-1")
                        .caseType("ADDON_REFUND")
                        .lifecycleStatus("PENDING_PROVIDER")
                        .build()
        ));

        OtaAddonRefundProjection refund = service.createRefund(
                "BILL-ADDON-PAID", "IDEMP-KEY-1234567890", "CORR-1",
                new OtaAddonRefundCommand(50000L, "Wrong add-on size requested"), manager);

        assertThat(refund).isNotNull();
        assertThat(refund.amountVnd()).isEqualTo(50000L);
        assertThat(refund.reason()).isEqualTo("Wrong add-on size requested");

        verify(refunds).dispatchCase("SETTLE-ADDON-CASE-1");
    }

    @Test
    void nonManagerCannotCreateRefund() {
        when(addonBills.findByBillReferenceForUpdate("BILL-ADDON-PAID")).thenReturn(Optional.of(paidBill));

        assertThatThrownBy(() -> service.createRefund(
                "BILL-ADDON-PAID", "IDEMP-KEY-1234567890", "CORR-1",
                new OtaAddonRefundCommand(50000L, "Wrong item"), staffInScope))
                .isInstanceOf(WorkshopSettlementAuthorizationException.class);
    }

    @Test
    void cannotRefundMoreThanUnrefundedCollections() {
        when(addonBills.findByBillReferenceForUpdate("BILL-ADDON-PAID")).thenReturn(Optional.of(paidBill));

        SettlementAddonPaymentAttempt paidAttempt = SettlementAddonPaymentAttempt.builder()
                .attemptReference("OAA-PAID")
                .billReference("BILL-ADDON-PAID")
                .merchantReference("MERCH-1")
                .amountVnd(200000L)
                .currencyCode("VND")
                .lifecycleStatus("VERIFIED_PAID")
                .build();
        when(addonAttempts.findAllByBillReferenceOrderByCreatedAtDescIdDesc("BILL-ADDON-PAID"))
                .thenReturn(List.of(paidAttempt));

        SettlementEvidenceConclusion ev = SettlementEvidenceConclusion.builder()
                .evidenceReference("VNPAY-111111")
                .bookingReference("OTA-BK-PAID")
                .providerTransactionReference("MERCH-1")
                .providerTransactionNumber("111111")
                .originalProviderCreateDate("20261106120000")
                .conclusion("SHOP_ADDON_VERIFIED")
                .amountVnd(200000L)
                .currencyCode("VND")
                .build();
        when(evidence.findByBookingReferenceAndConclusion("OTA-BK-PAID", "SHOP_ADDON_VERIFIED"))
                .thenReturn(List.of(ev));

        when(allocations.findAllByOriginalPaymentReference("MERCH-1")).thenReturn(List.of(
                SettlementRefundAllocation.builder()
                        .allocatedAmountVnd(150000L)
                        .lifecycleStatus("VERIFIED")
                        .build()
        ));

        // Unrefunded is 200,000 - 150,000 = 50,000. Requesting 60,000 must fail.
        assertThatThrownBy(() -> service.createRefund(
                "BILL-ADDON-PAID", "IDEMP-KEY-1234567890", "CORR-1",
                new OtaAddonRefundCommand(60000L, "Too much"), manager))
                .isInstanceOf(WorkshopSettlementValidationException.class)
                .hasMessageContaining("OVERCHARGE_EXCEEDS_UNREFUNDED_COLLECTIONS");
    }

    @Test
    void getBillStatusReturnsBillAttemptAndRefunds() {
        when(addonBills.findByBillReference("BILL-ADDON-PAID")).thenReturn(Optional.of(paidBill));
        when(addonAttempts.findFirstByBillReferenceOrderByCreatedAtDescIdDesc("BILL-ADDON-PAID")).thenReturn(Optional.of(
                SettlementAddonPaymentAttempt.builder()
                        .attemptReference("OAA-PAID")
                        .billReference("BILL-ADDON-PAID")
                        .merchantReference("MERCH-1")
                        .amountVnd(200000L)
                        .currencyCode("VND")
                        .lifecycleStatus("VERIFIED_PAID")
                        .build()
        ));
        when(addonRefunds.findAllByBillReferenceOrderByCreatedAtAsc("BILL-ADDON-PAID")).thenReturn(List.of(
                SettlementAddonRefund.builder()
                        .refundReference("REFUND-1")
                        .caseReference("CASE-1")
                        .billReference("BILL-ADDON-PAID")
                        .channelBookingReference("OTA-BK-PAID")
                        .amountVnd(50000L)
                        .currencyCode("VND")
                        .reason("Wrong color")
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build()
        ));
        when(cases.findByCaseReference("CASE-1")).thenReturn(Optional.of(
                SettlementCase.builder()
                        .caseReference("CASE-1")
                        .lifecycleStatus("RESOLVED")
                        .updatedAt(Instant.now())
                        .build()
        ));

        OtaAddonBillProjection projection = service.getBillStatus("BILL-ADDON-PAID", manager);

        assertThat(projection).isNotNull();
        assertThat(projection.billReference()).isEqualTo("BILL-ADDON-PAID");
        assertThat(projection.lifecycleStatus()).isEqualTo("PAID");
        assertThat(projection.latestPaymentAttempt()).isNotNull();
        assertThat(projection.latestPaymentAttempt().status()).isEqualTo("VERIFIED_PAID");
        assertThat(projection.refunds()).hasSize(1);
        assertThat(projection.refunds().get(0).lifecycleStatus()).isEqualTo("RESOLVED");
    }
}
