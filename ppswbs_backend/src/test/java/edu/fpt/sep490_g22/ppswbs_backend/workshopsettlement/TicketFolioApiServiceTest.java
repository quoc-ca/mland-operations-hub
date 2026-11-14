package edu.fpt.sep490_g22.ppswbs_backend.workshopsettlement;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.CustomerBookingAccessDecision;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.TicketFolioContext;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.FolioCollectionDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketFolioDtos.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopBookingSettlementAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopOperationsSettlementAdapter;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TicketFolioApiServiceTest {

    @Mock private WorkshopSettlementAuthorizationService authorization;
    @Mock private WorkshopBookingSettlementAdapter booking;
    @Mock private WorkshopOperationsSettlementAdapter operations;
    @Mock private SettlementFinalBillRepository bills;
    @Mock private SettlementChargeRepository charges;
    @Mock private SettlementFolioCollectionRepository collections;
    @Mock private SettlementBusinessAuditRepository audits;
    @Mock private FolioBalanceService folioBalances;
    @Mock private FolioCollectionService folioCollectionService;
    @Mock private FolioFinalizationService folioFinalizationService;
    @Mock private CashDayCloseService cashCloses;
    @Mock private SettlementIdempotencyService idempotency;

    private TicketFolioApiService service;

    private final CurrentAccountActor manager = new CurrentAccountActor(10L, ActorRole.MANAGER, ActorStatus.ACTIVE, true, List.of());
    private final CurrentAccountActor staff = new CurrentAccountActor(20L, ActorRole.STAFF, ActorStatus.ACTIVE, true, List.of(1L));
    private final CurrentAccountActor memberBuyer = new CurrentAccountActor(30L, ActorRole.MEMBER, ActorStatus.ACTIVE, true, List.of());
    private final CurrentAccountActor memberOther = new CurrentAccountActor(40L, ActorRole.MEMBER, ActorStatus.ACTIVE, true, List.of());

    @BeforeEach
    void setUp() {
        service = new TicketFolioApiService(
                authorization, booking, operations, bills, charges, collections, audits,
                folioBalances, folioCollectionService, folioFinalizationService, cashCloses, idempotency
        );
        when(authorization.requireManager(manager)).thenReturn(manager);
        when(authorization.requireManager(staff)).thenThrow(new WorkshopSettlementAuthorizationException("MANAGER_REQUIRED"));
        when(idempotency.execute(any(), any(), any())).thenAnswer(invocation -> {
            java.util.function.Supplier<String> action = invocation.getArgument(2);
            String result = action.get();
            return new SettlementIdempotencyResult(false, result != null ? result : "RES");
        });
    }

    @Test
    void readFolio_asOwningMember_succeeds() {
        TicketFolioContext ctx = new TicketFolioContext(
                "TK-1", "GRP-1", "LINE-1", "PKG-1", 1L, List.of("SES-1"), 30L
        );
        SettlementFinalBill folio = SettlementFinalBill.builder()
                .billReference("BILL-1")
                .bookingReference("TK-1")
                .lifecycleStatus("OPEN")
                .postPromotionAmountVnd(100_000L)
                .acceptedDepositAmountVnd(20_000L)
                .remainingAmountVnd(80_000L)
                .build();

        when(booking.getTicketFolioContext("TK-1")).thenReturn(Optional.of(ctx));
        when(bills.findByBookingReference("TK-1")).thenReturn(Optional.of(folio));
        when(folioBalances.balanceOf(folio)).thenReturn(new FolioBalanceService.FolioBalance(
                80_000L, 0L, 80_000L
        ));

        TicketFolioDto dto = service.readFolio("TK-1", memberBuyer);

        assertThat(dto.ticketCode()).isEqualTo("TK-1");
        assertThat(dto.status()).isEqualTo("OPEN");
        assertThat(dto.balanceDue()).isEqualTo("80000");
    }

    @Test
    void readFolio_asNonOwningMember_throwsException() {
        TicketFolioContext ctx = new TicketFolioContext(
                "TK-1", "GRP-1", "LINE-1", "PKG-1", 1L, List.of("SES-1"), 30L
        );
        SettlementFinalBill folio = SettlementFinalBill.builder()
                .billReference("BILL-1")
                .bookingReference("TK-1")
                .lifecycleStatus("OPEN")
                .build();

        when(booking.getTicketFolioContext("TK-1")).thenReturn(Optional.of(ctx));
        when(bills.findByBookingReference("TK-1")).thenReturn(Optional.of(folio));

        assertThatThrownBy(() -> service.readFolio("TK-1", memberOther))
                .isInstanceOf(WorkshopSettlementAuthorizationException.class)
                .hasMessageContaining("MEMBER_OWNERSHIP_REQUIRED");
    }

    @Test
    void lookupFolio_validGuestProof_succeeds() {
        TicketFolioContext ctx = new TicketFolioContext(
                "TK-1", "GRP-1", "LINE-1", "PKG-1", 1L, List.of("SES-1"), null
        );
        SettlementFinalBill folio = SettlementFinalBill.builder()
                .billReference("BILL-1")
                .bookingReference("TK-1")
                .lifecycleStatus("OPEN")
                .postPromotionAmountVnd(100_000L)
                .acceptedDepositAmountVnd(20_000L)
                .remainingAmountVnd(80_000L)
                .build();

        when(booking.getTicketFolioContext("TK-1")).thenReturn(Optional.of(ctx));
        when(bills.findByBookingReference("TK-1")).thenReturn(Optional.of(folio));
        when(booking.verifyCustomerAccess("TK-1", null, "guest@example.test", null))
                .thenReturn(new CustomerBookingAccessDecision(true));
        when(folioBalances.balanceOf(folio)).thenReturn(new FolioBalanceService.FolioBalance(
                80_000L, 0L, 80_000L
        ));

        GuestFolioLookupRequest req = new GuestFolioLookupRequest("guest@example.test", null);
        TicketFolioDto dto = service.lookupFolio("TK-1", req);

        assertThat(dto.ticketCode()).isEqualTo("TK-1");
    }

    @Test
    void lookupFolio_invalidGuestProof_throwsNotFound() {
        TicketFolioContext ctx = new TicketFolioContext(
                "TK-1", "GRP-1", "LINE-1", "PKG-1", 1L, List.of("SES-1"), null
        );
        SettlementFinalBill folio = SettlementFinalBill.builder()
                .billReference("BILL-1")
                .bookingReference("TK-1")
                .lifecycleStatus("OPEN")
                .build();

        when(booking.getTicketFolioContext("TK-1")).thenReturn(Optional.of(ctx));
        when(bills.findByBookingReference("TK-1")).thenReturn(Optional.of(folio));
        when(booking.verifyCustomerAccess("TK-1", null, "wrong@example.test", null))
                .thenReturn(new CustomerBookingAccessDecision(false));

        GuestFolioLookupRequest req = new GuestFolioLookupRequest("wrong@example.test", null);
        assertThatThrownBy(() -> service.lookupFolio("TK-1", req))
                .isInstanceOf(WorkshopSettlementNotFoundException.class);
    }

    @Test
    void finalizeFolio_outstandingBalance_throwsConflict() {
        TicketFolioContext ctx = new TicketFolioContext(
                "TK-1", "GRP-1", "LINE-1", "PKG-1", 1L, List.of("SES-1"), null
        );
        SettlementFinalBill folio = SettlementFinalBill.builder()
                .billReference("BILL-1")
                .bookingReference("TK-1")
                .lifecycleStatus("OPEN")
                .build();

        when(booking.getTicketFolioContext("TK-1")).thenReturn(Optional.of(ctx));
        when(bills.findByBookingReferenceForUpdate("TK-1")).thenReturn(Optional.of(folio));
        when(folioBalances.balanceOf(folio)).thenReturn(new FolioBalanceService.FolioBalance(
                80_000L, 0L, 80_000L
        ));

        assertThatThrownBy(() -> service.finalizeFolio("TK-1", "key-1234567890123456", null, manager))
                .isInstanceOf(WorkshopSettlementConflictException.class)
                .hasMessageContaining("OUTSTANDING_BALANCE");
        verify(folioFinalizationService, never()).concludeTicketSettled(any(), any());
    }

    @Test
    void finalizeFolio_settled_finalizesAndTellsBookingTheTicketIsSettled() {
        TicketFolioContext ctx = new TicketFolioContext(
                "TK-1", "GRP-1", "LINE-1", "PKG-1", 1L, List.of("SES-1"), null
        );
        SettlementFinalBill folio = SettlementFinalBill.builder()
                .billReference("BILL-1")
                .bookingReference("TK-1")
                .lifecycleStatus("OPEN")
                .postPromotionAmountVnd(100_000L)
                .acceptedDepositAmountVnd(100_000L)
                .remainingAmountVnd(0L)
                .build();

        when(booking.getTicketFolioContext("TK-1")).thenReturn(Optional.of(ctx));
        when(bills.findByBookingReferenceForUpdate("TK-1")).thenReturn(Optional.of(folio));
        when(folioBalances.balanceOf(folio)).thenReturn(new FolioBalanceService.FolioBalance(
                0L, 0L, 0L
        ));

        service.finalizeFolio("TK-1", "key-1234567890123456", "CORR-F", manager);

        assertThat(folio.getLifecycleStatus()).isEqualTo("FINALIZED");
        verify(folioFinalizationService).concludeTicketSettled(folio, "CORR-F");
    }

    @Test
    void voidCollection_fromClosedCashDay_isRefusedAndWritesNothing() {
        SettlementFinalBill folio = SettlementFinalBill.builder()
                .billReference("BILL-1")
                .bookingReference("TK-1")
                .lifecycleStatus("OPEN")
                .postPromotionAmountVnd(100_000L)
                .acceptedDepositAmountVnd(0L)
                .remainingAmountVnd(100_000L)
                .build();
        LocalDate yesterday = LocalDate.now().minusDays(1);
        SettlementFolioCollection col = SettlementFolioCollection.builder()
                .collectionReference("COL-1")
                .billReference("BILL-1")
                .bookingReference("TK-1")
                .branchId(1L)
                .method(FolioCollectionMethod.CASH)
                .status(FolioCollectionStatus.CONFIRMED)
                .amountVnd(50_000L)
                .cashBusinessDay(yesterday)
                .build();
        when(bills.findByBookingReferenceForUpdate("TK-1")).thenReturn(Optional.of(folio));
        when(collections.findByCollectionReference("COL-1")).thenReturn(Optional.of(col));
        when(cashCloses.isClosed(1L, yesterday)).thenReturn(true);

        FolioCollectionDtos.ManagerReasonRequest req = new FolioCollectionDtos.ManagerReasonRequest("Refund cash mistake");

        assertThatThrownBy(() -> service.voidCollection("TK-1", "COL-1", "key-1234567890123456", "CORR-1", req, manager))
                .isInstanceOf(WorkshopSettlementConflictException.class)
                .hasMessageContaining("CASH_DAY_ALREADY_CLOSED");

        // FR-050: the closed day stays locked and nothing is written for it.
        verify(audits, never()).save(any());
        verify(collections, never()).saveAndFlush(any());
        assertThat(col.getStatus()).isEqualTo(FolioCollectionStatus.CONFIRMED);
    }

    @Test
    void voidCollection_openCashDay_succeeds() {
        SettlementFinalBill folio = SettlementFinalBill.builder()
                .billReference("BILL-1")
                .bookingReference("TK-1")
                .lifecycleStatus("OPEN")
                .postPromotionAmountVnd(100_000L)
                .acceptedDepositAmountVnd(0L)
                .remainingAmountVnd(100_000L)
                .build();
        LocalDate today = LocalDate.now();
        SettlementFolioCollection col = SettlementFolioCollection.builder()
                .collectionReference("COL-2")
                .billReference("BILL-1")
                .bookingReference("TK-1")
                .branchId(1L)
                .method(FolioCollectionMethod.CASH)
                .status(FolioCollectionStatus.CONFIRMED)
                .amountVnd(50_000L)
                .cashBusinessDay(today)
                .build();

        when(bills.findByBookingReferenceForUpdate("TK-1")).thenReturn(Optional.of(folio));
        when(collections.findByCollectionReference("COL-2")).thenReturn(Optional.of(col));
        when(cashCloses.isClosed(1L, today)).thenReturn(false);
        when(folioBalances.balanceOf(folio)).thenReturn(new FolioBalanceService.FolioBalance(
                100_000L, 0L, 100_000L
        ));

        FolioCollectionDtos.ManagerReasonRequest req = new FolioCollectionDtos.ManagerReasonRequest("Wrong cash entered");

        TicketFolioDto result = service.voidCollection("TK-1", "COL-2", "key-1234567890123456", "CORR-2", req, manager);

        assertThat(col.getStatus()).isEqualTo(FolioCollectionStatus.VOIDED);
        verify(collections).saveAndFlush(col);

        ArgumentCaptor<SettlementBusinessAudit> auditCaptor = ArgumentCaptor.forClass(SettlementBusinessAudit.class);
        verify(audits).save(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getActionCode()).isEqualTo("COLLECTION_VOIDED");
    }

    @Test
    void voidLine_succeeds() {
        SettlementFinalBill folio = SettlementFinalBill.builder()
                .billReference("BILL-1")
                .bookingReference("TK-1")
                .lifecycleStatus("OPEN")
                .postPromotionAmountVnd(100_000L)
                .acceptedDepositAmountVnd(0L)
                .remainingAmountVnd(100_000L)
                .build();
        SettlementCharge charge = SettlementCharge.builder()
                .chargeReference("CHG-1")
                .billReference("BILL-1")
                .amountVnd(20_000L)
                .lifecycleStatus("CONFIRMED")
                .build();

        when(bills.findByBookingReferenceForUpdate("TK-1")).thenReturn(Optional.of(folio));
        when(charges.findByChargeReference("CHG-1")).thenReturn(Optional.of(charge));
        when(folioBalances.balanceOf(folio)).thenReturn(new FolioBalanceService.FolioBalance(
                100_000L, 0L, 100_000L
        ));

        FolioCollectionDtos.ManagerReasonRequest req = new FolioCollectionDtos.ManagerReasonRequest("Removed extra material");

        TicketFolioDto result = service.voidLine("TK-1", "CHG-1", "key-1234567890123456", "CORR-3", req, manager);

        assertThat(charge.getLifecycleStatus()).isEqualTo("VOIDED");
        verify(charges).saveAndFlush(charge);

        ArgumentCaptor<SettlementBusinessAudit> auditCaptor = ArgumentCaptor.forClass(SettlementBusinessAudit.class);
        verify(audits).save(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getActionCode()).isEqualTo("CHARGE_VOIDED");
    }
}
