package edu.fpt.sep490_g22.ppswbs_backend.workshopsettlement;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.BookingTimePayment;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.BookingTimePaymentStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementEvidenceConclusionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementFinalBillRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementFolioCollectionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementRefundRequestRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.ReconciliationDtos.Method;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.ReconciliationDtos.ReconciliationCollectionDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.ReconciliationDtos.State;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.FolioCollectionMethod;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.FolioCollectionStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.RefundReason;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementFinalBill;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementFolioCollection;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementRefundRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopBookingSettlementAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.ReconciliationCollectionService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.ReconciliationSourceUnavailableException;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.WorkshopSettlementAuthorizationException;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.WorkshopSettlementAuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** T133 (FR-056): authorization, mapping and the 003.1 source rules of the reconciliation list, without a database. */
class ReconciliationCollectionServiceTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 16);
    private static final Instant NOON = DAY.atTime(12, 0).atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
    private static final CurrentAccountActor MANAGER =
            new CurrentAccountActor(10L, ActorRole.MANAGER, ActorStatus.ACTIVE, true, List.of());
    private static final CurrentAccountActor STAFF =
            new CurrentAccountActor(20L, ActorRole.STAFF, ActorStatus.ACTIVE, true, List.of(7L));

    private final WorkshopSettlementAuthorizationService authorization = mock(WorkshopSettlementAuthorizationService.class);
    private final SettlementFolioCollectionRepository collections = mock(SettlementFolioCollectionRepository.class);
    private final SettlementFinalBillRepository bills = mock(SettlementFinalBillRepository.class);
    private final SettlementRefundRequestRepository refunds = mock(SettlementRefundRequestRepository.class);
    private final SettlementEvidenceConclusionRepository evidence = mock(SettlementEvidenceConclusionRepository.class);
    private final WorkshopBookingSettlementAdapter booking = mock(WorkshopBookingSettlementAdapter.class);
    private ReconciliationCollectionService service;

    @BeforeEach
    void setUp() {
        service = new ReconciliationCollectionService(authorization, collections, bills, refunds, evidence, booking);
        when(authorization.requireManager(MANAGER)).thenReturn(MANAGER);
        when(authorization.requireManager(STAFF)).thenThrow(new WorkshopSettlementAuthorizationException("MANAGER_REQUIRED"));
        when(bills.findByBookingReference(any())).thenReturn(Optional.of(
                SettlementFinalBill.builder().groupReference("GRP-1").build()));
        when(collections.findForReconciliation(anyLong(), any(), any(), any(), any(), any())).thenReturn(List.of());
        when(evidence.findByConclusionAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAscIdAsc(
                any(), any(), any())).thenReturn(List.of());
        when(booking.listBookingTimePayments(anyLong(), any())).thenReturn(List.of());
    }

    private static BookingTimePayment payment(String group, List<String> tickets, long amount,
                                              BookingTimePaymentStatus status, List<Long> locations) {
        return new BookingTimePayment(group, tickets, amount, BookingTimePayment.METHOD_VNPAY_ONLINE, status,
                "MREF-" + group, "ATT-" + group, NOON, status == BookingTimePaymentStatus.CONFIRMED ? NOON : null,
                locations);
    }

    @Test
    void staffIsRefusedAndNothingIsRead() {
        assertThatThrownBy(() -> service.list(7L, DAY, null, null, STAFF))
                .isInstanceOf(WorkshopSettlementAuthorizationException.class);
        verifyNoInteractions(collections, booking, evidence);
    }

    @Test
    void theThreeBookingTimeStatusesMapToTwoStatesAndTheLateNote() {
        when(booking.listBookingTimePayments(7L, DAY)).thenReturn(List.of(
                payment("G-OK", List.of("T-1"), 6_000_000, BookingTimePaymentStatus.CONFIRMED, List.of(7L)),
                payment("G-UNV", List.of("T-2"), 500_000, BookingTimePaymentStatus.UNVERIFIED, List.of(7L)),
                payment("G-LATE", List.of("T-3"), 400_000, BookingTimePaymentStatus.LATE_PENDING_REVIEW, List.of(7L))));

        List<ReconciliationCollectionDto> rows = service.list(7L, DAY, Method.BOOKING_TIME, null, MANAGER);

        assertThat(rows).extracting(ReconciliationCollectionDto::state).containsExactly(State.CONFIRMED,
                State.PENDING_VERIFICATION, State.PENDING_VERIFICATION);
        assertThat(rows).extracting(ReconciliationCollectionDto::note).containsExactly(null, null,
                "late payment under review");
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.method()).isEqualTo(Method.BOOKING_TIME);
            assertThat(row.confirmedBy()).isNull();
        });
        assertThat(rows.get(1).confirmedAt()).isNull();
        // only the confirmed row counts in the confirmed total
        assertThat(service.list(7L, DAY, Method.BOOKING_TIME, State.CONFIRMED, MANAGER)).hasSize(1)
                .first().extracting(ReconciliationCollectionDto::amount).isEqualTo("6000000");
    }

    @Test
    void oneOnlinePaymentOfSeveralTicketsIsOneRowWithItsLocations() {
        when(booking.listBookingTimePayments(7L, DAY)).thenReturn(List.of(
                payment("G-3", List.of("T-1", "T-2", "T-3"), 6_000_000, BookingTimePaymentStatus.CONFIRMED,
                        List.of(7L, 9L))));

        List<ReconciliationCollectionDto> rows = service.list(7L, DAY, null, null, MANAGER);

        assertThat(rows).hasSize(1);
        ReconciliationCollectionDto row = rows.get(0);
        assertThat(row.ticketCode()).isNull();
        assertThat(row.ticketCodes()).containsExactly("T-1", "T-2", "T-3");
        assertThat(row.groupCode()).isEqualTo("G-3");
        assertThat(row.locationIds()).containsExactly("7", "9");
        assertThat(row.collectionId()).isNull();
    }

    @Test
    void anUnavailable003_1SourceFailsTheWholeListWhenBookingTimeIsIncluded() {
        SettlementFolioCollection transfer = SettlementFolioCollection.builder().collectionReference("C-1")
                .bookingReference("T-9").method(FolioCollectionMethod.BANK_TRANSFER)
                .status(FolioCollectionStatus.CONFIRMED).amountVnd(1_200_000).externalReference("BANK-1")
                .confirmedByAccountId(10L).confirmedAt(NOON).createdAt(NOON).build();
        when(collections.findForReconciliation(eq(7L), any(), any(), any(), any(), any())).thenReturn(List.of(transfer));
        when(booking.listBookingTimePayments(7L, DAY)).thenThrow(new IllegalStateException("003.1 down"));

        assertThatThrownBy(() -> service.list(7L, DAY, null, null, MANAGER))
                .isInstanceOf(ReconciliationSourceUnavailableException.class);
        assertThatThrownBy(() -> service.list(7L, DAY, Method.BOOKING_TIME, null, MANAGER))
                .isInstanceOf(ReconciliationSourceUnavailableException.class);
        // without BOOKING_TIME the failing source is never asked
        assertThat(service.list(7L, DAY, Method.BANK_TRANSFER, null, MANAGER)).hasSize(1);
        verify(booking, times(2)).listBookingTimePayments(7L, DAY);
    }

    @Test
    void aTransferBeyondTheBalanceCarriesItsExcessAndRefundRequest() {
        SettlementFolioCollection transfer = SettlementFolioCollection.builder().collectionReference("C-2")
                .bookingReference("T-9").method(FolioCollectionMethod.BANK_TRANSFER)
                .status(FolioCollectionStatus.CONFIRMED).amountVnd(1_500_000).excessVnd(300_000)
                .externalReference("BANK-2").confirmedByAccountId(10L).confirmedAt(NOON).createdAt(NOON).build();
        when(collections.findForReconciliation(eq(7L), any(), any(), any(), any(), any())).thenReturn(List.of(transfer));
        SettlementRefundRequest refund = new SettlementRefundRequest();
        refund.setRequestReference("REFUND-9");
        when(refunds.findByReasonAndSourceReference(RefundReason.EXCESS_OR_DUPLICATE_MONEY, "C-2"))
                .thenReturn(Optional.of(refund));

        ReconciliationCollectionDto row = service.list(7L, DAY, Method.BANK_TRANSFER, null, MANAGER).get(0);

        assertThat(row.amount()).isEqualTo("1500000");
        assertThat(row.excessAmount()).isEqualTo("300000");
        assertThat(row.refundRequestReference()).isEqualTo("REFUND-9");
        assertThat(row.confirmedBy()).isEqualTo("10");
        assertThat(row.groupCode()).isEqualTo("GRP-1");
        assertThat(row.locationIds()).containsExactly("7");
    }

    @Test
    void excessRowsComeFromEvidenceOfTheTicketsBranchOnly() {
        var mine = new edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementEvidenceConclusion();
        mine.setBookingReference("T-MINE");
        mine.setAmountVnd(70_000L);
        mine.setCreatedAt(NOON);
        mine.setProviderTransactionReference("VNP-1");
        var other = new edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementEvidenceConclusion();
        other.setBookingReference("T-OTHER");
        other.setAmountVnd(80_000L);
        other.setCreatedAt(NOON);
        when(evidence.findByConclusionAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAscIdAsc(
                eq("FOLIO_VNPAY_EXCESS"), any(), any())).thenReturn(List.of(mine, other));
        when(booking.getTicketFolioContext("T-MINE")).thenReturn(Optional.of(context("T-MINE", "G-1", 7L)));
        when(booking.getTicketFolioContext("T-OTHER")).thenReturn(Optional.of(context("T-OTHER", "G-2", 9L)));

        List<ReconciliationCollectionDto> rows = service.list(7L, DAY, null, State.EXCESS, MANAGER);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).state()).isEqualTo(State.EXCESS);
        assertThat(rows.get(0).method()).isEqualTo(Method.VNPAY);
        assertThat(rows.get(0).collectionId()).isNull();
        assertThat(rows.get(0).ticketCode()).isEqualTo("T-MINE");
        assertThat(rows.get(0).amount()).isEqualTo("70000");
        assertThat(rows.get(0).transactionReference()).isEqualTo("VNP-1");
        verifyNoInteractions(collections);
        verify(booking, never()).listBookingTimePayments(anyLong(), any());
    }

    private static edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.TicketFolioContext context(
            String ticket, String group, Long location) {
        return new edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.TicketFolioContext(ticket, group,
                group + "-L1", "7", location, List.of(), null, null, null, 0L, "L1-" + ticket, 0L);
    }

    @Test
    void excessRowKeepsTheBranchOfItsEvidenceAfterTheTicketMoves() {
        var moved = new edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementEvidenceConclusion();
        moved.setBookingReference("T-MOVED");
        moved.setAmountVnd(70_000L);
        moved.setCreatedAt(NOON);
        moved.setBranchId(7L);
        when(evidence.findByConclusionAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAscIdAsc(
                eq("FOLIO_VNPAY_EXCESS"), any(), any())).thenReturn(List.of(moved));
        // T186: the ticket is now at branch 9, but the money was recorded at branch 7.
        when(booking.getTicketFolioContext("T-MOVED")).thenReturn(Optional.of(context("T-MOVED", "G-1", 9L)));

        assertThat(service.list(7L, DAY, null, State.EXCESS, MANAGER)).hasSize(1);
        assertThat(service.list(9L, DAY, null, State.EXCESS, MANAGER)).isEmpty();
    }
}
