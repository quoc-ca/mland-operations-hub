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
import static org.assertj.core.api.Assertions.tuple;
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
    @Mock private SettlementRefundRequestRepository refundRequests;
    @Mock private SettlementFolioCollectionRepository collections;

    private FolioCorrectionService service;

    private final CurrentAccountActor manager = new CurrentAccountActor(10L, ActorRole.MANAGER, ActorStatus.ACTIVE, true, List.of());
    private final CurrentAccountActor staff = new CurrentAccountActor(20L, ActorRole.STAFF, ActorStatus.ACTIVE, true, List.of(1L));
    private final CurrentAccountActor member = new CurrentAccountActor(30L, ActorRole.MEMBER, ActorStatus.ACTIVE, true, List.of());

    @BeforeEach
    void setUp() {
        service = new FolioCorrectionService(
                corrections, cases, allocations, bills, evidence, audits,
                idempotency, authorization, booking, refunds,
                mock(org.springframework.transaction.PlatformTransactionManager.class),
                refundRequests, collections);

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
    void folioOpenOrClosedIsRejectedWith409() {
        String bookingRef = "BK-300";
        SettlementFinalBill openBill = SettlementFinalBill.builder()
                .billReference("BILL-300").bookingReference(bookingRef).lifecycleStatus("OPEN").build();
        when(bills.findByBookingReferenceForUpdate(bookingRef)).thenReturn(Optional.of(openBill));
        ticketFolio(bookingRef);

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
    void concurrentPendingCorrectionIsRejected() {
        String bookingRef = "BK-600";
        SettlementFinalBill bill = SettlementFinalBill.builder()
                .billReference("BILL-600").bookingReference(bookingRef).lifecycleStatus("FINALIZED").build();
        when(bills.findByBookingReferenceForUpdate(bookingRef)).thenReturn(Optional.of(bill));
        ticketFolio(bookingRef);

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
        ticketFolio(bookingRef);

        // 1. Manager reads
        FolioCorrectionDtos.Response managerRes = service.read(bookingRef, corrRef, manager);
        assertThat(managerRes.correctionReference()).isEqualTo(corrRef);
        assertThat(managerRes.lifecycleStatus()).isEqualTo("RESOLVED");

        // 2. Staff in location reads
        when(authorization.requireManagerOrStaff(staff, 1L)).thenReturn(staff);
        FolioCorrectionDtos.Response staffRes = service.read(bookingRef, corrRef, staff);
        assertThat(staffRes.correctionReference()).isEqualTo(corrRef);

        // 3. Member owner reads
        FolioCorrectionDtos.Response memberRes = service.read(bookingRef, corrRef, member);
        assertThat(memberRes.correctionReference()).isEqualTo(corrRef);

        // 4. T114: no anonymous read (contact proof never travels in a URL); a Guest uses POST folio-lookups.
        assertThatThrownBy(() -> service.read(bookingRef, corrRef, null))
                .isInstanceOf(WorkshopSettlementNotFoundException.class);
    }

    // ---- T114 / T115: ticket folio ----

    private static final String TICKET = "GRP-9-L1-T1";

    private TicketFolioContext ticketContext() {
        return new TicketFolioContext(TICKET, "GRP-9", "LINE-9", "PKG-9", 1L, List.of("SES-9"), 30L);
    }

    @Test
    void ticketCorrectionReadAllowsOwnerAndAssignedStaffOnly() {
        when(booking.getTicketFolioContext(TICKET)).thenReturn(Optional.of(ticketContext()));
        when(corrections.findByBookingReferenceAndCorrectionReference(TICKET, "CORR-T")).thenReturn(Optional.of(
                SettlementFolioCorrection.builder().correctionReference("CORR-T").caseReference("CASE-T")
                        .bookingReference(TICKET).billReference("BILL-T").amountVnd(10_000L).currencyCode("VND")
                        .reason("r").createdAt(Instant.now()).updatedAt(Instant.now()).build()));
        when(cases.findByCaseReference("CASE-T")).thenReturn(Optional.of(SettlementCase.builder()
                .caseReference("CASE-T").bookingReference(TICKET).caseType("FOLIO_CORRECTION")
                .lifecycleStatus("PENDING_PROVIDER").build()));

        assertThat(service.read(TICKET, "CORR-T", member).correctionReference()).isEqualTo("CORR-T");
        CurrentAccountActor otherMember = new CurrentAccountActor(31L, ActorRole.MEMBER, ActorStatus.ACTIVE, true, List.of());
        assertThatThrownBy(() -> service.read(TICKET, "CORR-T", otherMember))
                .isInstanceOf(WorkshopSettlementNotFoundException.class);

        when(authorization.requireManagerOrAssignedStaff(staff, 1L, "LINE-9")).thenReturn(staff);
        assertThat(service.read(TICKET, "CORR-T", staff).correctionReference()).isEqualTo("CORR-T");
        verify(authorization).requireManagerOrAssignedStaff(staff, 1L, "LINE-9");
        verify(booking, never()).verifyCustomerAccess(any(), any(), any(), any());
    }

    @Test
    void ticketCorrectionIsCappedByRealCollectedMoneyAndLabelledAsBankRefund() {
        when(booking.getTicketFolioContext(TICKET)).thenReturn(Optional.of(ticketContext()));
        SettlementFinalBill bill = SettlementFinalBill.builder().billReference("BILL-T").bookingReference(TICKET)
                .lifecycleStatus("FINALIZED").acceptedDepositAmountVnd(30_000L).remainingAmountVnd(0L).build();
        when(bills.findByBookingReferenceForUpdate(TICKET)).thenReturn(Optional.of(bill));
        when(collections.findByBillReferenceOrderByIdAsc("BILL-T")).thenReturn(List.of(
                SettlementFolioCollection.builder().collectionReference("C-CASH").method(FolioCollectionMethod.CASH)
                        .amountVnd(20_000L).status(FolioCollectionStatus.CONFIRMED).build(),
                SettlementFolioCollection.builder().collectionReference("C-FAIL").method(FolioCollectionMethod.BANK_TRANSFER)
                        .amountVnd(10_000L).status(FolioCollectionStatus.FAILED).build(),
                SettlementFolioCollection.builder().collectionReference("C-VNP").method(FolioCollectionMethod.VNPAY)
                        .amountVnd(25_000L).status(FolioCollectionStatus.CONFIRMED).build()));
        when(allocations.findAllByOriginalPaymentReference(any())).thenReturn(List.of());
        stubCorrectionPersistence(TICKET);

        // 30,000 + 20,000 + 25,000 = 75,000 collected; the failed transfer is not money.
        assertThatThrownBy(() -> service.create(TICKET, "key-over", null,
                new FolioCorrectionDtos.CreateRequest(75_001L, "Overcharged"), manager))
                .isInstanceOf(WorkshopSettlementValidationException.class)
                .hasMessageContaining("OVERCHARGE_EXCEEDS_UNREFUNDED_COLLECTIONS");

        service.create(TICKET, "key-ok", null, new FolioCorrectionDtos.CreateRequest(60_000L, "Overcharged"), manager);

        ArgumentCaptor<SettlementRefundAllocation> captor = ArgumentCaptor.forClass(SettlementRefundAllocation.class);
        verify(allocations, times(3)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(SettlementRefundAllocation::getOriginalPaymentReference,
                        SettlementRefundAllocation::getAllocatedAmountVnd)
                .containsExactly(tuple("BOOKING_TIME:" + TICKET, 30_000L), tuple("COLLECTION:C-CASH", 20_000L),
                        tuple("COLLECTION:C-VNP", 10_000L));
        assertThat(captor.getAllValues()).allSatisfy(a -> {
            assertThat(a.getOriginalProviderTransactionNumber()).isNull();
            assertThat(a.getOriginalProviderCreateDate()).isNull();
            assertThat(a.getOperationReference()).startsWith("BANK-REFUND-");
        });
        // The ticket path never reads gateway evidence, so a VNPay part is not counted twice.
        verify(evidence, never()).findByBookingReferenceAndConclusion(any(), any());
    }

    /** An UNKNOWN refund request may already have moved money, so its amount stays reserved (no double refund). */
    @Test
    void anUnknownRefundAllocationKeepsItsAmountReservedAgainstANewCorrection() {
        singlePaymentFolio("BK-UNK", 100_000L);
        when(allocations.findAllByOriginalPaymentReference("BOOKING_TIME:BK-UNK")).thenReturn(List.of(
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
        when(allocations.findAllByOriginalPaymentReference("BOOKING_TIME:BK-RES")).thenReturn(List.of(
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
        when(allocations.findAllByOriginalPaymentReference("BOOKING_TIME:BK-LOCK")).thenReturn(List.of());
        stubCorrectionPersistence("BK-LOCK");

        service.create("BK-LOCK", "key-lock", null, new FolioCorrectionDtos.CreateRequest(10_000L, "Overcharge"), manager);

        verify(bills).findByBookingReferenceForUpdate("BK-LOCK");
        verify(bills, never()).findByBookingReference(any());
    }

    // FR-050 (closed cash day stays locked) is NOT asserted here: this service never reads or writes a folio
    // collection, so it cannot alter a closed day's cash. The protection belongs to the operations that do touch
    // collections — the Manager voids of spec FR-053 — and is verified in 003.3 T106.

    /** A ticket folio whose only collected money is its booking-time payment of {@code depositVnd}. */
    private void singlePaymentFolio(String bookingRef, long depositVnd) {
        SettlementFinalBill bill = SettlementFinalBill.builder().billReference("BILL-" + bookingRef)
                .bookingReference(bookingRef).lifecycleStatus("FINALIZED").acceptedDepositAmountVnd(depositVnd)
                .remainingAmountVnd(0L).build();
        when(bills.findByBookingReferenceForUpdate(bookingRef)).thenReturn(Optional.of(bill));
        ticketFolio(bookingRef);
    }

    private void ticketFolio(String ticketCode) {
        when(booking.getTicketFolioContext(ticketCode)).thenReturn(Optional.of(
                new TicketFolioContext(ticketCode, "GRP-9", "LINE-9", "PKG-9", 1L, List.of("SES-9"), 30L)));
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

