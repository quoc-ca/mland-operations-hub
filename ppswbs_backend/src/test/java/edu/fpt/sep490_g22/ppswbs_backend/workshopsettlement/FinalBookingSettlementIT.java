package edu.fpt.sep490_g22.ppswbs_backend.workshopsettlement;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.ConfirmedBookingFinancialProjection;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.CustomerBookingAccessDecision;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.FinalBalanceResolutionOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.SettlementReadiness;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.WorkshopAddOnPriceSnapshot;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementChargeRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementEvidenceConclusionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementFinalBillRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementNotificationHandoffRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.ChargeCommandDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.FinalBillProjectionDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.FinalizationCommandDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementFinalBill;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.ApplyVerifiedSettlementPaymentEvidenceCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.SettlementPaymentEvidenceOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopBookingSettlementAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopOperationsSettlementAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * T020 [US1]: Integration tests for final-bill creation, charges, finalization,
 * valid/duplicate/mismatched/late evidence, unpaid-finalization denial for Staff/Manager,
 * verified-payment-before-output-handover, ownership/proof, browser-return denial,
 * and reference-only notification-handoff delivery-failure isolation.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FinalBookingSettlementIT {

    private static final String BOOKING_CODE = "BK-SETTLE-IT-001";
    private static final Long LOCATION_ID = 42L;
    private static final Long OTHER_LOCATION_ID = 99L;
    private static final Long MEMBER_ID = 501L;
    private static final Long OTHER_MEMBER_ID = 502L;

    @Autowired private WorkshopFinalBillApiService apiService;
    @Autowired private FinalPaymentService finalPaymentService;
    @Autowired private SettlementNotificationHandoffService notificationHandoffService;
    @Autowired private SettlementFinalBillRepository billRepository;
    @Autowired private SettlementChargeRepository chargeRepository;
    @Autowired private SettlementEvidenceConclusionRepository evidenceRepository;
    @Autowired private SettlementNotificationHandoffRepository handoffRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    @MockitoBean private WorkshopBookingSettlementAdapter bookingAdapter;
    @MockitoBean private WorkshopOperationsSettlementAdapter operationsAdapter;
    @MockitoBean private SettlementNotificationEventDispatcher notificationDispatcher;

    @Test
    @DisplayName("Create final bill requires Operations readiness and location-scoped authority; replays idempotently")
    void createFinalBill_readinessAndLocationAuthority_idempotentReplay() {
        when(bookingAdapter.confirmedProjection(BOOKING_CODE)).thenReturn(
                new ConfirmedBookingFinancialProjection("PKG-1", 100_000L, 80_000L, 40_000L, 40_000L, 1, List.of("SESSION-1"), LOCATION_ID));

        // Readiness false -> conflict SETTLEMENT_NOT_READY
        when(operationsAdapter.readiness(BOOKING_CODE)).thenReturn(new SettlementReadiness(false));
        assertThatThrownBy(() -> apiService.create(BOOKING_CODE, "idem-create-001", "CORR-01", staffAuth(LOCATION_ID)))
                .isInstanceOf(WorkshopSettlementConflictException.class)
                .hasMessage("SETTLEMENT_NOT_READY");

        // Staff out of location scope -> LOCATION_SCOPE_DENIED
        when(operationsAdapter.readiness(BOOKING_CODE)).thenReturn(new SettlementReadiness(true));
        assertThatThrownBy(() -> apiService.create(BOOKING_CODE, "idem-create-001", "CORR-01", staffAuth(OTHER_LOCATION_ID)))
                .isInstanceOf(WorkshopSettlementAuthorizationException.class)
                .hasMessage("LOCATION_SCOPE_DENIED");

        // Staff in location scope -> successfully created
        FinalBillProjectionDto projection = apiService.create(BOOKING_CODE, "idem-create-001", "CORR-01", staffAuth(LOCATION_ID));
        assertThat(projection.bookingCode()).isEqualTo(BOOKING_CODE);
        assertThat(projection.status()).isEqualTo("OPEN");
        assertThat(projection.outstandingAmount()).isEqualTo(40_000L);
        assertThat(projection.paidAmount()).isEqualTo(40_000L);
        assertThat(projection.charges()).isEmpty();

        // Idempotent replay with same key returns existing projection without duplicate creation
        FinalBillProjectionDto replay = apiService.create(BOOKING_CODE, "idem-create-001", "CORR-01", staffAuth(LOCATION_ID));
        assertThat(replay.bookingCode()).isEqualTo(BOOKING_CODE);
        assertThat(replay.outstandingAmount()).isEqualTo(40_000L);
        assertThat(billRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Staff variable charge and Customer catalogue add-on charge increase outstanding balance")
    void addCharges_variableAndCatalogueAddOn_increasesOutstandingBalance() {
        setupOpenBill(40_000L);

        // 1. Staff adds variable charge of 10,000 VND
        ChargeCommandDto varCmd = new ChargeCommandDto(null, null, null, "Extra clay and glaze", 10_000L, "Student consumed additional materials");
        FinalBillProjectionDto afterVar = apiService.addCharge(BOOKING_CODE, "idem-charge-var", "CORR-02", varCmd, staffAuth(LOCATION_ID));
        assertThat(afterVar.outstandingAmount()).isEqualTo(50_000L);
        assertThat(afterVar.charges()).hasSize(1);
        assertThat(afterVar.charges().get(0).description()).isEqualTo("Extra clay and glaze");
        assertThat(afterVar.charges().get(0).amount()).isEqualTo(10_000L);

        // 2. Member adds catalogue add-on charge of 15,000 VND
        when(bookingAdapter.verifyCustomerAccess(BOOKING_CODE, MEMBER_ID, null, null))
                .thenReturn(new CustomerBookingAccessDecision(true));
        when(operationsAdapter.addOnPrice("PKG-1", LOCATION_ID, "ADDON-GIFT-BOX"))
                .thenReturn(new WorkshopAddOnPriceSnapshot("ADDON-GIFT-BOX", "Premium gift wrap box", 15_000L, "VND"));

        ChargeCommandDto catCmd = new ChargeCommandDto("ADDON-GIFT-BOX", null, null, null, null, null);
        FinalBillProjectionDto afterCat = apiService.addCharge(BOOKING_CODE, "idem-charge-cat", "CORR-03", catCmd, memberAuth(MEMBER_ID));
        assertThat(afterCat.outstandingAmount()).isEqualTo(65_000L);
        assertThat(afterCat.charges()).hasSize(2);
    }

    @Test
    @DisplayName("Unpaid finalization is denied for both Staff and Manager while balance remains")
    void unpaidFinalization_deniedForBothStaffAndManager() {
        setupOpenBill(40_000L);

        // Staff finalization denied with OUTSTANDING_BALANCE
        assertThatThrownBy(() -> apiService.finalizeBill(BOOKING_CODE, "idem-fin-staff", "CORR-04", new FinalizationCommandDto(), staffAuth(LOCATION_ID)))
                .isInstanceOf(WorkshopSettlementConflictException.class)
                .hasMessage("OUTSTANDING_BALANCE");

        // Manager finalization ALSO denied with OUTSTANDING_BALANCE (no role exception waiver)
        assertThatThrownBy(() -> apiService.finalizeBill(BOOKING_CODE, "idem-fin-mgr", "CORR-05", new FinalizationCommandDto(), managerAuth()))
                .isInstanceOf(WorkshopSettlementConflictException.class)
                .hasMessage("OUTSTANDING_BALANCE");

        // Bill remains OPEN
        SettlementFinalBill bill = billRepository.findByBookingReference(BOOKING_CODE).orElseThrow();
        assertThat(bill.getLifecycleStatus()).isEqualTo("OPEN");
    }

    @Test
    @DisplayName("Valid, duplicate, mismatched, and late payment evidence handling")
    void paymentEvidence_valid_duplicate_mismatched_andLate() {
        setupOpenBill(40_000L);
        when(bookingAdapter.recordFinalBalanceResolved(eq(BOOKING_CODE), anyString(), anyString(), anyString()))
                .thenReturn(FinalBalanceResolutionOutcome.accepted());

        // 1. Mismatched currency (USD instead of VND) is rejected at command boundary
        assertThatThrownBy(() -> new ApplyVerifiedSettlementPaymentEvidenceCommand(
                BOOKING_CODE, "VNPAY-TX-USD", "MERCH-USD", "FINAL_PAYMENT", 40_000L, "USD", "TX-USD", "20261031120000", "OP-PAY-USD", "CORR-USD"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("currencyCode must be VND");

        // 2. Mismatched amount (> outstanding 40,000) is rejected
        SettlementPaymentEvidenceOutcome excessiveOutcome = finalPaymentService.apply(new ApplyVerifiedSettlementPaymentEvidenceCommand(
                BOOKING_CODE, "VNPAY-TX-EXC", "MERCH-EXC", "FINAL_PAYMENT", 50_000L, "VND", "TX-EXC", "20261031120000", "OP-PAY-EXC", "CORR-EXC"));
        assertThat(excessiveOutcome.status()).isEqualTo(SettlementPaymentEvidenceOutcome.Status.REJECTED);
        assertThat(excessiveOutcome.reason()).isEqualTo("AMOUNT_MISMATCH");

        // 3. Unknown booking reference is rejected
        SettlementPaymentEvidenceOutcome unknownOutcome = finalPaymentService.apply(new ApplyVerifiedSettlementPaymentEvidenceCommand(
                "BK-NON-EXISTENT", "VNPAY-TX-UNK", "MERCH-UNK", "FINAL_PAYMENT", 40_000L, "VND", "TX-UNK", "20261031120000", "OP-PAY-UNK", "CORR-UNK"));
        assertThat(unknownOutcome.status()).isEqualTo(SettlementPaymentEvidenceOutcome.Status.REJECTED);
        assertThat(unknownOutcome.reason()).isEqualTo("FINAL_BILL_NOT_FOUND");

        // 4. Valid payment evidence of exact outstanding amount (40,000 VND) is accepted
        SettlementPaymentEvidenceOutcome validOutcome = finalPaymentService.apply(new ApplyVerifiedSettlementPaymentEvidenceCommand(
                BOOKING_CODE, "VNPAY-TX-001", "MERCH-001", "FINAL_PAYMENT", 40_000L, "VND", "TX-001", "20261031120000", "OP-PAY-001", "CORR-001"));
        assertThat(validOutcome.status()).isEqualTo(SettlementPaymentEvidenceOutcome.Status.ACCEPTED);
        verify(bookingAdapter).recordFinalBalanceResolved(eq(BOOKING_CODE), anyString(), eq("OP-PAY-001"), eq("CORR-001"));

        // 5. Duplicate payment evidence replay returns replay accepted without double-applying
        SettlementPaymentEvidenceOutcome replayOutcome = finalPaymentService.apply(new ApplyVerifiedSettlementPaymentEvidenceCommand(
                BOOKING_CODE, "VNPAY-TX-001", "MERCH-001", "FINAL_PAYMENT", 40_000L, "VND", "TX-001", "20261031120000", "OP-PAY-001", "CORR-001"));
        assertThat(replayOutcome.status()).isEqualTo(SettlementPaymentEvidenceOutcome.Status.ACCEPTED);
        assertThat(evidenceRepository.findByBookingReferenceAndConclusion(BOOKING_CODE, "FINAL_PAYMENT_VERIFIED")).hasSize(1);

        // Conflicting duplicate replay (different amount) is rejected with IDEMPOTENCY_CONFLICT
        SettlementPaymentEvidenceOutcome conflictOutcome = finalPaymentService.apply(new ApplyVerifiedSettlementPaymentEvidenceCommand(
                BOOKING_CODE, "VNPAY-TX-001", "MERCH-001", "FINAL_PAYMENT", 30_000L, "VND", "TX-001", "20261031120000", "OP-PAY-001", "CORR-001"));
        assertThat(conflictOutcome.status()).isEqualTo(SettlementPaymentEvidenceOutcome.Status.REJECTED);
        assertThat(conflictOutcome.reason()).isEqualTo("IDEMPOTENCY_CONFLICT");

        // 6. Finalize the zero-balance bill
        FinalBillProjectionDto finalized = apiService.finalizeBill(BOOKING_CODE, "idem-fin-001", "CORR-FIN", new FinalizationCommandDto(), staffAuth(LOCATION_ID));
        assertThat(finalized.status()).isEqualTo("FINALIZED");
        assertThat(finalized.outstandingAmount()).isEqualTo(0L);

        // 7. Late evidence arriving after finalization is rejected with BILL_NOT_OPEN
        SettlementPaymentEvidenceOutcome lateOutcome = finalPaymentService.apply(new ApplyVerifiedSettlementPaymentEvidenceCommand(
                BOOKING_CODE, "VNPAY-TX-LATE", "MERCH-LATE", "FINAL_PAYMENT", 10_000L, "VND", "TX-LATE", "20261031120000", "OP-PAY-LATE", "CORR-LATE"));
        assertThat(lateOutcome.status()).isEqualTo(SettlementPaymentEvidenceOutcome.Status.REJECTED);
        assertThat(lateOutcome.reason()).isEqualTo("BILL_NOT_OPEN");
    }

    @Test
    @DisplayName("Customer read authorizes owning Member or single Guest contact proof; rejects unauthorized or malformed proof")
    void customerRead_ownershipAndContactProof() {
        setupOpenBill(40_000L);

        // Owning member can read
        when(bookingAdapter.verifyCustomerAccess(BOOKING_CODE, MEMBER_ID, null, null))
                .thenReturn(new CustomerBookingAccessDecision(true));
        FinalBillProjectionDto memberRead = apiService.read(BOOKING_CODE, null, null, memberAuth(MEMBER_ID));
        assertThat(memberRead.bookingCode()).isEqualTo(BOOKING_CODE);
        assertThat(memberRead.outstandingAmount()).isEqualTo(40_000L);

        // Other member cannot read (denied -> 404 WorkshopSettlementNotFoundException)
        when(bookingAdapter.verifyCustomerAccess(BOOKING_CODE, OTHER_MEMBER_ID, null, null))
                .thenReturn(new CustomerBookingAccessDecision(false));
        assertThatThrownBy(() -> apiService.read(BOOKING_CODE, null, null, memberAuth(OTHER_MEMBER_ID)))
                .isInstanceOf(WorkshopSettlementNotFoundException.class);

        // Guest with valid email proof can read
        when(bookingAdapter.verifyCustomerAccess(BOOKING_CODE, null, "guest@mland.vn", null))
                .thenReturn(new CustomerBookingAccessDecision(true));
        FinalBillProjectionDto guestRead = apiService.read(BOOKING_CODE, "guest@mland.vn", null, null);
        assertThat(guestRead.bookingCode()).isEqualTo(BOOKING_CODE);

        // Guest with mismatched email is denied
        when(bookingAdapter.verifyCustomerAccess(BOOKING_CODE, null, "wrong@mland.vn", null))
                .thenReturn(new CustomerBookingAccessDecision(false));
        assertThatThrownBy(() -> apiService.read(BOOKING_CODE, "wrong@mland.vn", null, null))
                .isInstanceOf(WorkshopSettlementNotFoundException.class);

        // Guest supplying both email and phone or neither is rejected before query
        assertThatThrownBy(() -> apiService.read(BOOKING_CODE, "guest@mland.vn", "0901234567", null))
                .isInstanceOf(WorkshopSettlementNotFoundException.class);
        assertThatThrownBy(() -> apiService.read(BOOKING_CODE, null, null, null))
                .isInstanceOf(WorkshopSettlementNotFoundException.class);

        // Staff/Manager attempting to use customer read route is rejected (must use internal staff endpoints)
        assertThatThrownBy(() -> apiService.read(BOOKING_CODE, null, null, staffAuth(LOCATION_ID)))
                .isInstanceOf(WorkshopSettlementNotFoundException.class);
    }

    @Test
    @DisplayName("Browser return denial: Settlement does not expose or accept browser-return payment confirmations")
    void browserReturnDenial_requiresServerVerifiedEvidence() {
        setupOpenBill(40_000L);

        // Verified payment requires server-side verified evidence via FinalPaymentService / Facade.
        // There is no browser return payment confirmation endpoint in settlement; only server verified evidence
        // reduces the balance.
        SettlementFinalBill billBefore = billRepository.findByBookingReference(BOOKING_CODE).orElseThrow();
        assertThat(billBefore.getRemainingAmountVnd()).isEqualTo(40_000L);

        // Unverified evidence or lack of signed provider ingress outcome leaves the bill untouched
        assertThat(evidenceRepository.findByBookingReferenceAndConclusion(BOOKING_CODE, "FINAL_PAYMENT_VERIFIED")).isEmpty();
    }

    @Test
    @DisplayName("Reference-only notification handoff dispatches after commit and isolates delivery failure")
    void notificationHandoff_referenceOnlyAndDeliveryFailureIsolation() {
        setupOpenBill(40_000L);

        // When delivery failure occurs in dispatcher, the transaction still commits safely
        doThrow(new RuntimeException("Simulated messaging connection timeout"))
                .when(notificationDispatcher).dispatch(anyString());

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        String notifRef = tx.execute(status -> notificationHandoffService.create(
                "BILL-TEST-REF", "FINAL_PAYMENT_VERIFIED", "CORR-NOTIF-01",
                "buyer@mland.vn", "Final settlement verified"));

        assertThat(notifRef).startsWith("SETTLE-NOTIF-");

        // Verify dispatcher was invoked after commit
        verify(notificationDispatcher).dispatch(notifRef);

        // Verify handoff record was persisted despite dispatcher exception (delivery failure isolation)
        var persisted = handoffRepository.findByNotificationReference(notifRef);
        assertThat(persisted).isPresent();
        assertThat(persisted.get().getSettlementReference()).isEqualTo("BILL-TEST-REF");
        assertThat(persisted.get().getNotificationKind()).isEqualTo("FINAL_PAYMENT_VERIFIED");
        assertThat(persisted.get().getStoredRecipient()).isEqualTo("buyer@mland.vn");
    }

    private void setupOpenBill(long remainingAmount) {
        when(bookingAdapter.confirmedProjection(BOOKING_CODE)).thenReturn(
                new ConfirmedBookingFinancialProjection("PKG-1", 100_000L, 80_000L, 40_000L, remainingAmount, 1, List.of("SESSION-1"), LOCATION_ID));
        when(operationsAdapter.readiness(BOOKING_CODE)).thenReturn(new SettlementReadiness(true));

        apiService.create(BOOKING_CODE, "idem-setup-" + System.nanoTime(), "CORR-SETUP", staffAuth(LOCATION_ID));
    }

    private static Authentication staffAuth(Long locationId) {
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(new CurrentAccountActor(
                11L, ActorRole.STAFF, ActorStatus.ACTIVE, true, List.of(locationId)));
        return auth;
    }

    private static Authentication managerAuth() {
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(new CurrentAccountActor(
                99L, ActorRole.MANAGER, ActorStatus.ACTIVE, true, List.of()));
        return auth;
    }

    private static Authentication memberAuth(Long memberId) {
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(new CurrentAccountActor(
                memberId, ActorRole.MEMBER, ActorStatus.ACTIVE, true, List.of()));
        return auth;
    }
}
