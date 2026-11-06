package edu.fpt.sep490_g22.ppswbs_backend.workshopsettlement;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.FolioCorrectionDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopBookingSettlementAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FolioCorrectionServiceTest {

    @Mock private SettlementFolioCorrectionRepository corrections;
    @Mock private SettlementCaseRepository cases;
    @Mock private SettlementRefundAllocationRepository allocations;
    @Mock private SettlementFinalBillRepository bills;
    @Mock private SettlementEvidenceConclusionRepository evidence;
    @Mock private SettlementBusinessAuditRepository audits;
    @Mock private SettlementIdempotencyService idempotency;
    @Mock private WorkshopSettlementAuthorizationService authorization;
    @Mock private WorkshopBookingSettlementAdapter booking;
    @Mock private WorkshopRefundService refunds;

    private FolioCorrectionService service;

    private final CurrentAccountActor manager = new CurrentAccountActor(10L, ActorRole.MANAGER, ActorStatus.ACTIVE, true, List.of());
    private final CurrentAccountActor staff = new CurrentAccountActor(20L, ActorRole.STAFF, ActorStatus.ACTIVE, true, List.of(1L));
    private final CurrentAccountActor member = new CurrentAccountActor(30L, ActorRole.MEMBER, ActorStatus.ACTIVE, true, List.of());

    @BeforeEach
    void setUp() {
        service = new FolioCorrectionService(
                corrections, cases, allocations, bills, evidence, audits,
                idempotency, authorization, booking, refunds, mock(org.springframework.transaction.PlatformTransactionManager.class));

        when(authorization.requireManager(manager)).thenReturn(manager);
        when(authorization.requireManager(staff))
                .thenThrow(new WorkshopSettlementAuthorizationException("MANAGER_AUTHORIZATION_REQUIRED"));
        when(authorization.requireManager(member))
                .thenThrow(new WorkshopSettlementAuthorizationException("MANAGER_AUTHORIZATION_REQUIRED"));
        when(authorization.requireManager((CurrentAccountActor) isNull()))
                .thenThrow(new WorkshopSettlementAuthorizationException("MANAGER_AUTHORIZATION_REQUIRED"));

        when(idempotency.execute(any(), any(), any())).thenAnswer(invocation -> {
            java.util.function.Supplier<String> supplier = invocation.getArgument(2);
            String ref = supplier.get();
            return new SettlementIdempotencyResult(false, ref);
        });
    }

    @Test
    void managerCreatesFolioCorrectionSuccessfully() {
        String bookingRef = "BK-100";
        SettlementFinalBill bill = SettlementFinalBill.builder()
                .billReference("BILL-100")
                .bookingReference(bookingRef)
                .lifecycleStatus("FINALIZED")
                .remainingAmountVnd(0L)
                .build();
        when(bills.findByBookingReferenceForUpdate(bookingRef)).thenReturn(Optional.of(bill));
        when(booking.confirmedProjection(bookingRef)).thenReturn(new ConfirmedBookingFinancialProjection(
                "PKG-1", 200000L, 200000L, 100000L, 100000L, 1, List.of("SES-1"), 1L));

        when(booking.verifiedDepositPayments(bookingRef)).thenReturn(new VerifiedBookingDepositPaymentsProjection(
                List.of(new VerifiedBookingPayment("DEP-1", "9901", "20261101120000", 100000L, "VND", true))));
        when(evidence.findByBookingReferenceAndConclusion(bookingRef, "FINAL_PAYMENT_VERIFIED")).thenReturn(
                List.of(SettlementEvidenceConclusion.builder()
                        .providerTransactionReference("FIN-1").providerTransactionNumber("9902")
                        .originalProviderCreateDate("20261102120000").amountVnd(100000L).currencyCode("VND")
                        .build()));

        when(allocations.findAllByOriginalPaymentReference(any())).thenReturn(List.of());

        when(corrections.saveAndFlush(any(SettlementFolioCorrection.class))).thenAnswer(i -> {
            SettlementFolioCorrection c = i.getArgument(0);
            c.setId(1L);
            return c;
        });
        when(cases.saveAndFlush(any(SettlementCase.class))).thenAnswer(i -> {
            SettlementCase c = i.getArgument(0);
            c.setId(1L);
            return c;
        });

        when(corrections.findByCorrectionReference(any())).thenAnswer(i -> Optional.of(SettlementFolioCorrection.builder()
                .correctionReference(i.getArgument(0)).caseReference("SETTLE-CORR-1").bookingReference(bookingRef)
                .billReference("BILL-100").amountVnd(50000L).currencyCode("VND").reason("Overcharged")
                .createdAt(Instant.now()).updatedAt(Instant.now()).build()));
        when(cases.findByCaseReference(any())).thenAnswer(i -> Optional.of(SettlementCase.builder()
                .caseReference(i.getArgument(0)).bookingReference(bookingRef).caseType("FOLIO_CORRECTION")
                .lifecycleStatus("PENDING_PROVIDER").build()));

        FolioCorrectionDtos.Response response = service.create(
                bookingRef, "key-1", "corr-1",
                new FolioCorrectionDtos.CreateRequest(50000L, "Customer overcharged for materials"),
                manager);

        assertThat(response).isNotNull();
        assertThat(response.amountVnd()).isEqualTo(50000L);
        assertThat(response.lifecycleStatus()).isEqualTo("PENDING_PROVIDER");

        ArgumentCaptor<SettlementRefundAllocation> allocCaptor = ArgumentCaptor.forClass(SettlementRefundAllocation.class);
        verify(allocations).save(allocCaptor.capture());
        assertThat(allocCaptor.getValue().getAllocatedAmountVnd()).isEqualTo(50000L);
        assertThat(allocCaptor.getValue().getOriginalPaymentReference()).isEqualTo("DEP-1");

        verify(audits).save(argThat(a -> "FOLIO_CORRECTION_OPENED".equals(a.getActionCode())));
    }

    @Test
    void nonManagerActorsAreForbidden() {
        assertThatThrownBy(() -> service.create("BK-1", "key-1", null,
                new FolioCorrectionDtos.CreateRequest(50000L, "reason"), staff))
                .isInstanceOf(WorkshopSettlementAuthorizationException.class);

        assertThatThrownBy(() -> service.create("BK-1", "key-1", null,
                new FolioCorrectionDtos.CreateRequest(50000L, "reason"), member))
                .isInstanceOf(WorkshopSettlementAuthorizationException.class);

        assertThatThrownBy(() -> service.create("BK-1", "key-1", null,
                new FolioCorrectionDtos.CreateRequest(50000L, "reason"), null))
                .isInstanceOf(WorkshopSettlementAuthorizationException.class);
    }

    @Test
    void missingOrBlankReasonIsRejected() {
        assertThatThrownBy(() -> service.create("BK-1", "key-1", null,
                new FolioCorrectionDtos.CreateRequest(50000L, null), manager))
                .isInstanceOf(WorkshopSettlementValidationException.class);

        assertThatThrownBy(() -> service.create("BK-1", "key-1", null,
                new FolioCorrectionDtos.CreateRequest(50000L, "   "), manager))
                .isInstanceOf(WorkshopSettlementValidationException.class);
    }

    @Test
    void nonPositiveAmountIsRejected() {
        assertThatThrownBy(() -> service.create("BK-1", "key-1", null,
                new FolioCorrectionDtos.CreateRequest(0L, "valid reason"), manager))
                .isInstanceOf(WorkshopSettlementValidationException.class);

        assertThatThrownBy(() -> service.create("BK-1", "key-1", null,
                new FolioCorrectionDtos.CreateRequest(-1000L, "valid reason"), manager))
                .isInstanceOf(WorkshopSettlementValidationException.class);
    }

    @Test
    void overchargeExceedingUnrefundedCollectionsIsRejectedWith422() {
        String bookingRef = "BK-200";
        SettlementFinalBill bill = SettlementFinalBill.builder()
                .billReference("BILL-200").bookingReference(bookingRef).lifecycleStatus("FINALIZED").build();
        when(bills.findByBookingReferenceForUpdate(bookingRef)).thenReturn(Optional.of(bill));
        when(booking.confirmedProjection(bookingRef)).thenReturn(new ConfirmedBookingFinancialProjection(
                "PKG-1", 100000L, 100000L, 50000L, 50000L, 1, List.of("SES-1"), 1L));

        when(booking.verifiedDepositPayments(bookingRef)).thenReturn(new VerifiedBookingDepositPaymentsProjection(
                List.of(new VerifiedBookingPayment("DEP-1", "9901", "20261101120000", 50000L, "VND", true))));
        when(evidence.findByBookingReferenceAndConclusion(bookingRef, "FINAL_PAYMENT_VERIFIED")).thenReturn(List.of());
        when(allocations.findAllByOriginalPaymentReference(any())).thenReturn(List.of());

        // Attempting to refund 60,000 VND when total collected is only 50,000 VND
        assertThatThrownBy(() -> service.create(bookingRef, "key-1", null,
                new FolioCorrectionDtos.CreateRequest(60000L, "Overcharge"), manager))
                .isInstanceOf(WorkshopSettlementValidationException.class)
                .hasMessageContaining("OVERCHARGE_EXCEEDS_UNREFUNDED_COLLECTIONS");
    }

    @Test
    void folioOpenOrClosedIsRejectedWith409() {
        String bookingRef = "BK-300";
        SettlementFinalBill openBill = SettlementFinalBill.builder()
                .billReference("BILL-300").bookingReference(bookingRef).lifecycleStatus("OPEN").build();
        when(bills.findByBookingReferenceForUpdate(bookingRef)).thenReturn(Optional.of(openBill));
        when(booking.confirmedProjection(bookingRef)).thenReturn(new ConfirmedBookingFinancialProjection(
                "PKG-1", 100000L, 100000L, 50000L, 50000L, 1, List.of("SES-1"), 1L));

        assertThatThrownBy(() -> service.create(bookingRef, "key-1", null,
                new FolioCorrectionDtos.CreateRequest(10000L, "reason"), manager))
                .isInstanceOf(WorkshopSettlementConflictException.class)
                .hasMessageContaining("FOLIO_NOT_FINALIZED");

        SettlementFinalBill closedBill = SettlementFinalBill.builder()
                .billReference("BILL-300").bookingReference(bookingRef).lifecycleStatus("CLOSED").build();
        when(bills.findByBookingReferenceForUpdate(bookingRef)).thenReturn(Optional.of(closedBill));

        assertThatThrownBy(() -> service.create(bookingRef, "key-1", null,
                new FolioCorrectionDtos.CreateRequest(10000L, "reason"), manager))
                .isInstanceOf(WorkshopSettlementConflictException.class)
                .hasMessageContaining("FOLIO_CLOSED");
    }

    @Test
    void otaBookingIsRejected() {
        assertThatThrownBy(() -> service.create("OTA-BK-999", "key-1", null,
                new FolioCorrectionDtos.CreateRequest(10000L, "reason"), manager))
                .isInstanceOf(WorkshopSettlementConflictException.class)
                .hasMessageContaining("OTA_BOOKING_NOT_SUPPORTED");
    }

    @Test
    void oldestPaymentFirstAllocationAcrossMultiplePayments() {
        String bookingRef = "BK-400";
        SettlementFinalBill bill = SettlementFinalBill.builder()
                .billReference("BILL-400").bookingReference(bookingRef).lifecycleStatus("FINALIZED").build();
        when(bills.findByBookingReferenceForUpdate(bookingRef)).thenReturn(Optional.of(bill));
        when(booking.confirmedProjection(bookingRef)).thenReturn(new ConfirmedBookingFinancialProjection(
                "PKG-1", 300000L, 300000L, 100000L, 200000L, 1, List.of("SES-1"), 1L));

        // Deposit: 100,000 VND at 2026-11-01
        when(booking.verifiedDepositPayments(bookingRef)).thenReturn(new VerifiedBookingDepositPaymentsProjection(
                List.of(new VerifiedBookingPayment("DEP-1", "9901", "20261101100000", 100000L, "VND", true))));
        // Final payment: 200,000 VND at 2026-11-02
        when(evidence.findByBookingReferenceAndConclusion(bookingRef, "FINAL_PAYMENT_VERIFIED")).thenReturn(
                List.of(SettlementEvidenceConclusion.builder()
                        .providerTransactionReference("FIN-1").providerTransactionNumber("9902")
                        .originalProviderCreateDate("20261102100000").amountVnd(200000L).currencyCode("VND")
                        .build()));

        when(allocations.findAllByOriginalPaymentReference(any())).thenReturn(List.of());

        when(corrections.findByCorrectionReference(any())).thenAnswer(i -> Optional.of(SettlementFolioCorrection.builder()
                .correctionReference(i.getArgument(0)).caseReference("SETTLE-CORR-1").bookingReference(bookingRef)
                .billReference("BILL-400").amountVnd(150000L).currencyCode("VND").reason("Overcharge")
                .createdAt(Instant.now()).updatedAt(Instant.now()).build()));
        when(cases.findByCaseReference(any())).thenAnswer(i -> Optional.of(SettlementCase.builder()
                .caseReference(i.getArgument(0)).bookingReference(bookingRef).caseType("FOLIO_CORRECTION")
                .lifecycleStatus("PENDING_PROVIDER").build()));

        // Request 150,000 VND refund: should allocate 100,000 to DEP-1, 50,000 to FIN-1
        service.create(bookingRef, "key-1", null,
                new FolioCorrectionDtos.CreateRequest(150000L, "Partial overcharge refund"), manager);

        ArgumentCaptor<SettlementRefundAllocation> allocCaptor = ArgumentCaptor.forClass(SettlementRefundAllocation.class);
        verify(allocations, times(2)).save(allocCaptor.capture());
        List<SettlementRefundAllocation> captured = allocCaptor.getAllValues();

        assertThat(captured.get(0).getOriginalPaymentReference()).isEqualTo("DEP-1");
        assertThat(captured.get(0).getAllocatedAmountVnd()).isEqualTo(100000L);

        assertThat(captured.get(1).getOriginalPaymentReference()).isEqualTo("FIN-1");
        assertThat(captured.get(1).getAllocatedAmountVnd()).isEqualTo(50000L);
    }

    @Test
    void capsPaymentByUnrefundedAmountWithPriorRefund() {
        String bookingRef = "BK-500";
        SettlementFinalBill bill = SettlementFinalBill.builder()
                .billReference("BILL-500").bookingReference(bookingRef).lifecycleStatus("FINALIZED").build();
        when(bills.findByBookingReferenceForUpdate(bookingRef)).thenReturn(Optional.of(bill));
        when(booking.confirmedProjection(bookingRef)).thenReturn(new ConfirmedBookingFinancialProjection(
                "PKG-1", 300000L, 300000L, 100000L, 200000L, 1, List.of("SES-1"), 1L));

        when(booking.verifiedDepositPayments(bookingRef)).thenReturn(new VerifiedBookingDepositPaymentsProjection(
                List.of(new VerifiedBookingPayment("DEP-1", "9901", "20261101100000", 100000L, "VND", true))));
        when(evidence.findByBookingReferenceAndConclusion(bookingRef, "FINAL_PAYMENT_VERIFIED")).thenReturn(
                List.of(SettlementEvidenceConclusion.builder()
                        .providerTransactionReference("FIN-1").providerTransactionNumber("9902")
                        .originalProviderCreateDate("20261102100000").amountVnd(200000L).currencyCode("VND")
                        .build()));

        // DEP-1 already has 40,000 VND allocated in a previous VERIFIED refund
        when(allocations.findAllByOriginalPaymentReference("DEP-1")).thenReturn(List.of(
                SettlementRefundAllocation.builder().allocatedAmountVnd(40000L).lifecycleStatus("VERIFIED").build()));
        when(allocations.findAllByOriginalPaymentReference("FIN-1")).thenReturn(List.of());

        when(corrections.findByCorrectionReference(any())).thenAnswer(i -> Optional.of(SettlementFolioCorrection.builder()
                .correctionReference(i.getArgument(0)).caseReference("SETTLE-CORR-1").bookingReference(bookingRef)
                .billReference("BILL-500").amountVnd(80000L).currencyCode("VND").reason("Overcharge")
                .createdAt(Instant.now()).updatedAt(Instant.now()).build()));
        when(cases.findByCaseReference(any())).thenAnswer(i -> Optional.of(SettlementCase.builder()
                .caseReference(i.getArgument(0)).bookingReference(bookingRef).caseType("FOLIO_CORRECTION")
                .lifecycleStatus("PENDING_PROVIDER").build()));

        // Request 80,000 VND: DEP-1 only has 60,000 unrefunded, so 60,000 from DEP-1, 20,000 from FIN-1
        service.create(bookingRef, "key-1", null,
                new FolioCorrectionDtos.CreateRequest(80000L, "Reason"), manager);

        ArgumentCaptor<SettlementRefundAllocation> allocCaptor = ArgumentCaptor.forClass(SettlementRefundAllocation.class);
        verify(allocations, times(2)).save(allocCaptor.capture());
        List<SettlementRefundAllocation> captured = allocCaptor.getAllValues();

        assertThat(captured.get(0).getOriginalPaymentReference()).isEqualTo("DEP-1");
        assertThat(captured.get(0).getAllocatedAmountVnd()).isEqualTo(60000L);

        assertThat(captured.get(1).getOriginalPaymentReference()).isEqualTo("FIN-1");
        assertThat(captured.get(1).getAllocatedAmountVnd()).isEqualTo(20000L);
    }

    @Test
    void concurrentPendingCorrectionIsRejected() {
        String bookingRef = "BK-600";
        SettlementFinalBill bill = SettlementFinalBill.builder()
                .billReference("BILL-600").bookingReference(bookingRef).lifecycleStatus("FINALIZED").build();
        when(bills.findByBookingReferenceForUpdate(bookingRef)).thenReturn(Optional.of(bill));
        when(booking.confirmedProjection(bookingRef)).thenReturn(new ConfirmedBookingFinancialProjection(
                "PKG-1", 100000L, 100000L, 50000L, 50000L, 1, List.of("SES-1"), 1L));

        when(cases.findByBookingReferenceAndCaseTypeInAndLifecycleStatusOrderByCreatedAtAsc(
                eq(bookingRef), eq(List.of("FOLIO_CORRECTION")), eq("PENDING_PROVIDER")))
                .thenReturn(List.of(SettlementCase.builder().caseReference("EXISTING-PENDING").build()));

        assertThatThrownBy(() -> service.create(bookingRef, "key-1", null,
                new FolioCorrectionDtos.CreateRequest(10000L, "Reason"), manager))
                .isInstanceOf(WorkshopSettlementConflictException.class)
                .hasMessageContaining("FOLIO_CORRECTION_ALREADY_IN_PROGRESS");
    }

    @Test
    void customerSafeReadAuthorizesManagerStaffMemberGuest() {
        String bookingRef = "BK-700";
        String corrRef = "CORR-700";
        SettlementFolioCorrection correction = SettlementFolioCorrection.builder()
                .correctionReference(corrRef).caseReference("CASE-700").bookingReference(bookingRef)
                .billReference("BILL-700").amountVnd(20000L).currencyCode("VND").reason("Reason")
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();
        SettlementCase settlementCase = SettlementCase.builder()
                .caseReference("CASE-700").bookingReference(bookingRef).caseType("FOLIO_CORRECTION")
                .lifecycleStatus("RESOLVED").build();

        when(corrections.findByBookingReferenceAndCorrectionReference(bookingRef, corrRef))
                .thenReturn(Optional.of(correction));
        when(cases.findByCaseReference("CASE-700")).thenReturn(Optional.of(settlementCase));
        when(booking.confirmedProjection(bookingRef)).thenReturn(new ConfirmedBookingFinancialProjection(
                "PKG-1", 100000L, 100000L, 50000L, 50000L, 1, List.of("SES-1"), 1L));

        // 1. Manager reads
        FolioCorrectionDtos.Response managerRes = service.read(bookingRef, corrRef, null, null, manager);
        assertThat(managerRes.correctionReference()).isEqualTo(corrRef);
        assertThat(managerRes.lifecycleStatus()).isEqualTo("RESOLVED");

        // 2. Staff in location reads
        when(authorization.requireManagerOrStaff(staff, 1L)).thenReturn(staff);
        FolioCorrectionDtos.Response staffRes = service.read(bookingRef, corrRef, null, null, staff);
        assertThat(staffRes.correctionReference()).isEqualTo(corrRef);

        // 3. Member owner reads
        when(booking.verifyCustomerAccess(bookingRef, 30L, null, null))
                .thenReturn(new CustomerBookingAccessDecision(true));
        FolioCorrectionDtos.Response memberRes = service.read(bookingRef, corrRef, null, null, member);
        assertThat(memberRes.correctionReference()).isEqualTo(corrRef);

        // 4. Guest with valid contact reads
        when(booking.verifyCustomerAccess(bookingRef, null, "guest@test.test", null))
                .thenReturn(new CustomerBookingAccessDecision(true));
        FolioCorrectionDtos.Response guestRes = service.read(bookingRef, corrRef, "guest@test.test", null, null);
        assertThat(guestRes.correctionReference()).isEqualTo(corrRef);

        // 5. Guest with invalid contact rejected
        when(booking.verifyCustomerAccess(bookingRef, null, "wrong@test.test", null))
                .thenReturn(new CustomerBookingAccessDecision(false));
        assertThatThrownBy(() -> service.read(bookingRef, corrRef, "wrong@test.test", null, null))
                .isInstanceOf(WorkshopSettlementNotFoundException.class);
    }

    /** An UNKNOWN refund request may already have moved money, so its amount stays reserved (no double refund). */
    @Test
    void anUnknownRefundAllocationKeepsItsAmountReservedAgainstANewCorrection() {
        singlePaymentFolio("BK-UNK", 100_000L);
        when(allocations.findAllByOriginalPaymentReference("DEP-1")).thenReturn(List.of(
                SettlementRefundAllocation.builder().allocatedAmountVnd(100_000L).lifecycleStatus("UNKNOWN").build()));

        assertThatThrownBy(() -> service.create("BK-UNK", "key-unknown", null,
                new FolioCorrectionDtos.CreateRequest(1L, "Overcharge"), manager))
                .isInstanceOf(WorkshopSettlementValidationException.class)
                .hasMessageContaining("OVERCHARGE_EXCEEDS_UNREFUNDED_COLLECTIONS");
        verify(allocations, never()).save(any());
    }

    @Test
    void preparedAndAcceptedAllocationsAlsoStayReservedButARejectedOneDoesNot() {
        singlePaymentFolio("BK-RES", 100_000L);
        when(allocations.findAllByOriginalPaymentReference("DEP-1")).thenReturn(List.of(
                SettlementRefundAllocation.builder().allocatedAmountVnd(30_000L).lifecycleStatus("PREPARED").build(),
                SettlementRefundAllocation.builder().allocatedAmountVnd(30_000L).lifecycleStatus("ACCEPTED").build(),
                SettlementRefundAllocation.builder().allocatedAmountVnd(40_000L).lifecycleStatus("REJECTED").build()));

        // 100,000 - 30,000 - 30,000 = 40,000 still refundable (the REJECTED 40,000 never left Mland).
        assertThatThrownBy(() -> service.create("BK-RES", "key-over", null,
                new FolioCorrectionDtos.CreateRequest(40_001L, "Overcharge"), manager))
                .isInstanceOf(WorkshopSettlementValidationException.class);
        stubCorrectionPersistence("BK-RES");
        service.create("BK-RES", "key-ok", null, new FolioCorrectionDtos.CreateRequest(40_000L, "Overcharge"), manager);

        ArgumentCaptor<SettlementRefundAllocation> captured = ArgumentCaptor.forClass(SettlementRefundAllocation.class);
        verify(allocations).save(captured.capture());
        assertThat(captured.getValue().getAllocatedAmountVnd()).isEqualTo(40_000L);
    }

    /** Concurrent corrections must serialize on the folio row, never read it without the lock. */
    @Test
    void createAlwaysLocksTheFolioRowSoConcurrentCorrectionsSerialize() {
        singlePaymentFolio("BK-LOCK", 100_000L);
        when(allocations.findAllByOriginalPaymentReference("DEP-1")).thenReturn(List.of());
        stubCorrectionPersistence("BK-LOCK");

        service.create("BK-LOCK", "key-lock", null, new FolioCorrectionDtos.CreateRequest(10_000L, "Overcharge"), manager);

        verify(bills).findByBookingReferenceForUpdate("BK-LOCK");
        verify(bills, never()).findByBookingReference(any());
    }

    private void singlePaymentFolio(String bookingRef, long depositVnd) {
        SettlementFinalBill bill = SettlementFinalBill.builder().billReference("BILL-" + bookingRef)
                .bookingReference(bookingRef).lifecycleStatus("FINALIZED").remainingAmountVnd(0L).build();
        when(bills.findByBookingReferenceForUpdate(bookingRef)).thenReturn(Optional.of(bill));
        when(booking.confirmedProjection(bookingRef)).thenReturn(new ConfirmedBookingFinancialProjection(
                "PKG-1", depositVnd, depositVnd, depositVnd, 0L, 1, List.of("SES-1"), 1L));
        when(booking.verifiedDepositPayments(bookingRef)).thenReturn(new VerifiedBookingDepositPaymentsProjection(
                List.of(new VerifiedBookingPayment("DEP-1", "9901", "20261101120000", depositVnd, "VND", true))));
        when(evidence.findByBookingReferenceAndConclusion(bookingRef, "FINAL_PAYMENT_VERIFIED")).thenReturn(List.of());
    }

    private void stubCorrectionPersistence(String bookingRef) {
        when(corrections.saveAndFlush(any(SettlementFolioCorrection.class))).thenAnswer(i -> i.getArgument(0));
        when(cases.saveAndFlush(any(SettlementCase.class))).thenAnswer(i -> i.getArgument(0));
        when(corrections.findByCorrectionReference(any())).thenAnswer(i -> Optional.of(SettlementFolioCorrection.builder()
                .correctionReference(i.getArgument(0)).caseReference("SETTLE-CORR-X").bookingReference(bookingRef)
                .billReference("BILL-" + bookingRef).amountVnd(10_000L).currencyCode("VND").reason("Overcharge")
                .createdAt(Instant.now()).updatedAt(Instant.now()).build()));
        when(cases.findByCaseReference(any())).thenAnswer(i -> Optional.of(SettlementCase.builder()
                .caseReference(i.getArgument(0)).bookingReference(bookingRef).caseType("FOLIO_CORRECTION")
                .lifecycleStatus("PENDING_PROVIDER").build()));
    }
}

