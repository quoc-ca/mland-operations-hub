package edu.fpt.sep490_g22.ppswbs_backend.workshopsettlement;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementBusinessAuditRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementCashReversalRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementFinalBillRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementFolioCollectionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementRefundRequestRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.FolioCollectionMethod;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.FolioCollectionStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementCashReversal;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementFinalBill;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementFolioCollection;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** T144 (FR-036, FR-053): the refusals and the entry a cash reversal writes, without a database. */
class CashReversalServiceTest {

    private static final String KEY = "REVERSAL-KEY-1234567";
    private static final LocalDate DAY = LocalDate.of(2026, 11, 16);
    private static final CurrentAccountActor MANAGER =
            new CurrentAccountActor(10L, ActorRole.MANAGER, ActorStatus.ACTIVE, true, List.of());
    private static final CurrentAccountActor STAFF =
            new CurrentAccountActor(20L, ActorRole.STAFF, ActorStatus.ACTIVE, true, List.of(7L));

    private final WorkshopSettlementAuthorizationService authorization = mock(WorkshopSettlementAuthorizationService.class);
    private final SettlementFinalBillRepository bills = mock(SettlementFinalBillRepository.class);
    private final SettlementFolioCollectionRepository collections = mock(SettlementFolioCollectionRepository.class);
    private final SettlementCashReversalRepository reversals = mock(SettlementCashReversalRepository.class);
    private final SettlementRefundRequestRepository refunds = mock(SettlementRefundRequestRepository.class);
    private final CashDayCloseService cashCloses = mock(CashDayCloseService.class);
    private final SettlementIdempotencyService idempotency = mock(SettlementIdempotencyService.class);
    private CashReversalService service;
    private SettlementFinalBill folio;
    private SettlementFolioCollection collection;

    @BeforeEach
    void setUp() {
        service = new CashReversalService(authorization, bills, collections, reversals, refunds, cashCloses,
                idempotency, mock(SettlementBusinessAuditRepository.class));
        when(authorization.requireManager(MANAGER)).thenReturn(MANAGER);
        when(authorization.requireManager(STAFF)).thenThrow(new WorkshopSettlementAuthorizationException("MANAGER_REQUIRED"));
        folio = SettlementFinalBill.builder().billReference("BILL-1").bookingReference("T-1")
                .lifecycleStatus("OPEN").build();
        collection = SettlementFolioCollection.builder().id(5L).collectionReference("COLL-1").billReference("BILL-1")
                .bookingReference("T-1").method(FolioCollectionMethod.CASH).status(FolioCollectionStatus.CONFIRMED)
                .amountVnd(120_000).branchId(7L).cashBusinessDay(DAY).build();
        when(bills.findByBookingReferenceForUpdate("T-1")).thenReturn(Optional.of(folio));
        when(collections.findByCollectionReference("COLL-1")).thenReturn(Optional.of(collection));
        when(collections.findById(5L)).thenReturn(Optional.of(collection));
        when(cashCloses.isClosed(7L, DAY)).thenReturn(true);
        when(cashCloses.currentCashDay(7L)).thenReturn(DAY.plusDays(1));
        when(idempotency.execute(anyString(), anyString(), any())).thenAnswer(invocation -> {
            Supplier<String> action = invocation.getArgument(2);
            return new SettlementIdempotencyResult(false, action.get());
        });
        when(reversals.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(reversals.findByReversalReference(anyString())).thenAnswer(invocation -> {
            SettlementCashReversal saved = org.mockito.Mockito.mockingDetails(reversals).getInvocations().stream()
                    .filter(i -> i.getMethod().getName().equals("saveAndFlush"))
                    .map(i -> (SettlementCashReversal) i.getArgument(0)).findFirst().orElseThrow();
            return Optional.of(saved);
        });
    }

    private void refused(String code) {
        assertThatThrownBy(() -> service.reverse("T-1", "COLL-1", KEY, "c", "reason", MANAGER))
                .isInstanceOf(WorkshopSettlementConflictException.class).hasMessage(code);
        verify(reversals, never()).saveAndFlush(any());
    }

    @Test
    void staffIsRefusedBeforeAnythingIsRead() {
        assertThatThrownBy(() -> service.reverse("T-1", "COLL-1", KEY, "c", "reason", STAFF))
                .isInstanceOf(WorkshopSettlementAuthorizationException.class).hasMessage("MANAGER_REQUIRED");
        verifyNoInteractions(bills, reversals, idempotency);
    }

    @Test
    void aReversalIsMinusTheAppliedAmountOnTheCurrentWorkingDay() {
        var dto = service.reverse("T-1", "COLL-1", KEY, "c", "Counted twice", MANAGER);

        assertThat(dto.amountVnd()).isEqualTo("-120000");
        assertThat(dto.workingDate()).isEqualTo(DAY.plusDays(1));
        assertThat(dto.originalWorkingDate()).isEqualTo(DAY);
        assertThat(dto.reason()).isEqualTo("Counted twice");
        assertThat(dto.recordedBy()).isEqualTo("10");
        verify(reversals).saveAndFlush(any());
    }

    @Test
    void aVoidedCollectionIsRefused() {
        collection.setStatus(FolioCollectionStatus.VOIDED);
        refused("COLLECTION_ALREADY_VOIDED");
    }

    @Test
    void aNonCashCollectionIsRefused() {
        collection.setMethod(FolioCollectionMethod.BANK_TRANSFER);
        refused("COLLECTION_NOT_CASH");
    }

    @Test
    void anAlreadyReversedCollectionIsRefused() {
        when(reversals.existsByCollectionId(5L)).thenReturn(true);
        refused("COLLECTION_ALREADY_REVERSED");
    }

    @Test
    void moneyUnderARefundRequestIsRefused() {
        when(refunds.existsBySourceReference("COLL-1")).thenReturn(true);
        refused("COLLECTION_UNDER_REFUND");
    }

    @Test
    void aDayThatIsNotClosedIsCorrectedByTheVoidInstead() {
        when(cashCloses.isClosed(7L, DAY)).thenReturn(false);
        refused("CASH_DAY_NOT_CLOSED");
    }

    @Test
    void aFinalizedOrClosedFolioIsRefused() {
        folio.setLifecycleStatus("FINALIZED");
        refused("FINAL_BILL_NOT_OPEN");
        folio.setLifecycleStatus("CLOSED");
        refused("FINAL_BILL_NOT_OPEN");
    }

    @Test
    void aCollectionOfAnotherFolioIsNotFound() {
        collection.setBillReference("BILL-OTHER");
        assertThatThrownBy(() -> service.reverse("T-1", "COLL-1", KEY, "c", "reason", MANAGER))
                .isInstanceOf(WorkshopSettlementNotFoundException.class);
    }
}
