package edu.fpt.sep490_g22.ppswbs_backend.workshopsettlement;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementBusinessAuditRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.FolioCollectionDtos.CashCloseRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.FolioCollectionDtos.ManagerReasonRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.CashDayCloseStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementCashDayClose;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopOperationsSettlementAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** T160 (FR-036, CHK024): who may close a branch's cash day and who may confirm its difference. */
class CashCloseCloserAuthorityTest {

    private static final long BRANCH = 7L;
    private static final String KEY = "CLOSE-KEY-1234567890";
    private static final LocalDate DAY = LocalDate.of(2026, 11, 16);

    private final CashDayCloseService cashCloses = mock(CashDayCloseService.class);
    private final SettlementIdempotencyService idempotency = mock(SettlementIdempotencyService.class);
    private FolioCollectionApiService api;

    @BeforeEach
    void setUp() {
        WorkshopSettlementAuthorizationService authorization = new WorkshopSettlementAuthorizationService(
                mock(AccountFacade.class), mock(SettlementBusinessAuditRepository.class),
                mock(WorkshopOperationsSettlementAdapter.class));
        api = new FolioCollectionApiService(authorization, mock(FolioCollectionService.class),
                mock(FolioFinalizationService.class), cashCloses, idempotency);
        when(idempotency.execute(anyString(), anyString(), any())).thenAnswer(invocation -> {
            Supplier<String> action = invocation.getArgument(2);
            return new SettlementIdempotencyResult(false, action.get());
        });
        when(cashCloses.close(any(), any(), anyLong(), any(), any(), any())).thenReturn(close());
        when(cashCloses.findByReference("CASHCLOSE-1")).thenReturn(close());
    }

    private static SettlementCashDayClose close() {
        return SettlementCashDayClose.builder().closeReference("CASHCLOSE-1").branchId(BRANCH).businessDay(DAY)
                .expectedCashVnd(100_000L).countedCashVnd(100_000L).status(CashDayCloseStatus.CLOSED)
                .closedByAccountId(20L).build();
    }

    private static CashCloseRequest request() {
        return new CashCloseRequest(BRANCH, DAY, "100000", null);
    }

    private static CurrentAccountActor staff(ActorStatus status, Long... locations) {
        return new CurrentAccountActor(20L, ActorRole.STAFF, status, true, List.of(locations));
    }

    @Test
    void anActiveStaffMemberOfTheBranchMayClose() {
        var response = api.closeCashDay(request(), KEY, "corr", staff(ActorStatus.ACTIVE, BRANCH, 9L));

        assertThat(response.closeReference()).isEqualTo("CASHCLOSE-1");
        // the closer account is what the close record stores
        verify(cashCloses).close(eq(BRANCH), eq(DAY), eq(100_000L), any(), eq(20L), eq("corr"));
    }

    @Test
    void aManagerMayClose() {
        var manager = new CurrentAccountActor(10L, ActorRole.MANAGER, ActorStatus.ACTIVE, true, List.of());

        api.closeCashDay(request(), KEY, "corr", manager);

        verify(cashCloses).close(eq(BRANCH), eq(DAY), eq(100_000L), any(), eq(10L), eq("corr"));
    }

    @Test
    void staffOutsideTheBranchIsRefused() {
        assertThatThrownBy(() -> api.closeCashDay(request(), KEY, "corr", staff(ActorStatus.ACTIVE, 9L)))
                .isInstanceOf(WorkshopSettlementAuthorizationException.class)
                .hasMessage("LOCATION_SCOPE_DENIED");
        verifyNoInteractions(cashCloses);
    }

    @Test
    void suspendedStaffOfTheBranchIsRefused() {
        assertThatThrownBy(() -> api.closeCashDay(request(), KEY, "corr", staff(ActorStatus.SUSPENDED, BRANCH)))
                .isInstanceOf(WorkshopSettlementAuthorizationException.class)
                .hasMessage("ACCOUNT_NOT_ACTIVE");
        verifyNoInteractions(cashCloses);
    }

    @Test
    void staffCannotConfirmADifference() {
        assertThatThrownBy(() -> api.confirmDifferenceById("CASHCLOSE-1", "Da kiem", KEY, "corr",
                staff(ActorStatus.ACTIVE, BRANCH)))
                .isInstanceOf(WorkshopSettlementAuthorizationException.class)
                .hasMessage("MANAGER_REQUIRED");
        verify(cashCloses, never()).confirmDifferenceById(any(), any(), any(), any());
        assertThat(new ManagerReasonRequest("x").reason()).isEqualTo("x");
    }
}
