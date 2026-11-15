package edu.fpt.sep490_g22.ppswbs_backend.workshopsettlement;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.FolioCollectionDtos.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.CashDayCloseStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementCashDayClose;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** T120 (FR-036, FR-052, FR-022d): the cash-close resource requires and stores the Idempotency-Key. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FolioCollectionApiServiceTest {

    @Mock private WorkshopSettlementAuthorizationService authorization;
    @Mock private FolioCollectionService collections;
    @Mock private FolioFinalizationService finalization;
    @Mock private CashDayCloseService cashCloses;
    @Mock private SettlementIdempotencyService idempotency;

    private FolioCollectionApiService service;
    private final Map<String, String> stored = new HashMap<>();

    private final CurrentAccountActor staff = new CurrentAccountActor(20L, ActorRole.STAFF, ActorStatus.ACTIVE, true, List.of(1L));
    private final CurrentAccountActor manager = new CurrentAccountActor(10L, ActorRole.MANAGER, ActorStatus.ACTIVE, true, List.of());
    private static final String KEY = "CLOSE-KEY-1234567890";

    @BeforeEach
    void setUp() {
        service = new FolioCollectionApiService(authorization, collections, finalization, cashCloses, idempotency);
        when(authorization.requireManagerOrStaff(staff, 1L)).thenReturn(staff);
        when(authorization.requireManager(manager)).thenReturn(manager);
        // A stand-in for the stored outcome: the same operation returns what it returned the first time.
        when(idempotency.execute(anyString(), anyString(), any())).thenAnswer(inv -> {
            String op = inv.getArgument(0);
            if (stored.containsKey(op)) return new SettlementIdempotencyResult(true, stored.get(op));
            Supplier<String> action = inv.getArgument(2);
            String ref = action.get();
            stored.put(op, ref);
            return new SettlementIdempotencyResult(false, ref);
        });
    }

    private static SettlementCashDayClose close(CashDayCloseStatus status) {
        return SettlementCashDayClose.builder().closeReference("CASHCLOSE-1").branchId(1L)
                .businessDay(LocalDate.of(2026, 11, 14)).expectedCashVnd(100_000L).countedCashVnd(90_000L)
                .differenceVnd(-10_000L).differenceReason("Thieu").status(status).build();
    }

    @Test
    void closeRequiresTheIdempotencyKey() {
        CashCloseRequest request = new CashCloseRequest(1L, LocalDate.of(2026, 11, 14), 90_000L, "Thieu");
        assertThatThrownBy(() -> service.closeCashDay(request, null, "corr", staff))
                .isInstanceOf(WorkshopSettlementValidationException.class);
        assertThatThrownBy(() -> service.closeCashDay(request, "short", "corr", staff))
                .isInstanceOf(WorkshopSettlementValidationException.class);
        verifyNoInteractions(cashCloses);
    }

    @Test
    void closeReplayReturnsTheStoredCloseWithoutClosingTwice() {
        CashCloseRequest request = new CashCloseRequest(1L, LocalDate.of(2026, 11, 14), 90_000L, "Thieu");
        when(cashCloses.close(eq(1L), eq(LocalDate.of(2026, 11, 14)), eq(90_000L), eq("Thieu"), eq(20L), eq("corr")))
                .thenReturn(close(CashDayCloseStatus.PENDING_MANAGER_CONFIRMATION));
        when(cashCloses.findByReference("CASHCLOSE-1")).thenReturn(close(CashDayCloseStatus.PENDING_MANAGER_CONFIRMATION));

        CashCloseResponse first = service.closeCashDay(request, KEY, "corr", staff);
        CashCloseResponse replay = service.closeCashDay(request, KEY, "corr", staff);

        assertThat(first.closeReference()).isEqualTo("CASHCLOSE-1");
        assertThat(replay).isEqualTo(first);
        verify(cashCloses, times(1)).close(any(), any(), anyLong(), any(), any(), any());
        verify(idempotency, times(2)).execute(eq("CASH_CLOSE:1:" + KEY),
                eq("CASH_CLOSE:1:2026-11-14:90000:Thieu"), any());
    }

    @Test
    void closeByStaffOfAnotherBranchIsRefused() {
        CurrentAccountActor otherBranch = new CurrentAccountActor(21L, ActorRole.STAFF, ActorStatus.ACTIVE, true, List.of(2L));
        when(authorization.requireManagerOrStaff(otherBranch, 1L))
                .thenThrow(new WorkshopSettlementAuthorizationException("LOCATION_SCOPE_DENIED"));
        assertThatThrownBy(() -> service.closeCashDay(new CashCloseRequest(1L, null, 0L, null), KEY, "corr", otherBranch))
                .isInstanceOf(WorkshopSettlementAuthorizationException.class);
        verifyNoInteractions(cashCloses, idempotency);
    }

    @Test
    void differenceConfirmationIsManagerOnlyIdempotentAndNeedsAKey() {
        when(cashCloses.confirmDifferenceById("CASHCLOSE-1", "Da kiem", 10L, "corr"))
                .thenReturn(close(CashDayCloseStatus.CLOSED));
        when(cashCloses.findByReference("CASHCLOSE-1")).thenReturn(close(CashDayCloseStatus.CLOSED));

        CashCloseResponse confirmed = service.confirmDifferenceById("CASHCLOSE-1", "Da kiem", KEY, "corr", manager);
        service.confirmDifferenceById("CASHCLOSE-1", "Da kiem", KEY, "corr", manager);

        assertThat(confirmed.status()).isEqualTo("CLOSED");
        verify(cashCloses, times(1)).confirmDifferenceById(any(), any(), any(), any());
        assertThatThrownBy(() -> service.confirmDifferenceById("CASHCLOSE-1", "Da kiem", null, "corr", manager))
                .isInstanceOf(WorkshopSettlementValidationException.class);
    }

    @Test
    void expectedCashIsReadUnderTheCashCloseResourceForTheBranch() {
        when(cashCloses.expectedCash(1L, LocalDate.of(2026, 11, 14))).thenReturn(150_000L);

        ExpectedCashResponse expected = service.expectedCash(1L, LocalDate.of(2026, 11, 14), staff);

        assertThat(expected).isEqualTo(new ExpectedCashResponse(1L, LocalDate.of(2026, 11, 14), 150_000L));
        verify(authorization).requireManagerOrStaff(staff, 1L);
    }
}
