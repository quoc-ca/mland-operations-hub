package edu.fpt.sep490_g22.ppswbs_backend.workshopsettlement;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementBusinessAuditRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementCashDayCloseRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementFolioCollectionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.CashDayCloseStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementCashDayClose;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.CashDayCloseService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.WorkshopSettlementConflictException;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.WorkshopSettlementNotFoundException;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.WorkshopSettlementValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 003.3 T096, FR-036: one cash close per branch per Vietnam working day, differences gated by a Manager. */
class CashDayCloseServiceTest {

    private static final Long BRANCH = 5L;
    private static final Long STAFF = 11L;
    private static final Long MANAGER = 22L;
    private static final LocalDate DAY = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));

    private SettlementCashDayCloseRepository closes;
    private SettlementFolioCollectionRepository collections;
    private CashDayCloseService service;

    @BeforeEach
    void setUp() {
        closes = mock(SettlementCashDayCloseRepository.class);
        collections = mock(SettlementFolioCollectionRepository.class);
        SettlementBusinessAuditRepository audits = mock(SettlementBusinessAuditRepository.class);
        when(closes.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        service = new CashDayCloseService(closes, collections, audits);
    }

    @Test
    void totalsTheBranchesConfirmedCashForTheDay() {
        when(collections.sumConfirmedCash(BRANCH, DAY)).thenReturn(1_250_000L);

        assertThat(service.expectedCash(BRANCH, DAY)).isEqualTo(1_250_000);
        assertThat(service.expectedCash(BRANCH, null)).isEqualTo(1_250_000);
    }

    @Test
    void aDayThatBalancesIsClosedStraightAway() {
        when(collections.sumConfirmedCash(BRANCH, DAY)).thenReturn(1_000_000L);

        SettlementCashDayClose close = service.close(BRANCH, DAY, 1_000_000, null, STAFF, "corr");

        assertThat(close.getStatus()).isEqualTo(CashDayCloseStatus.CLOSED);
        assertThat(close.getDifferenceVnd()).isZero();
        assertThat(close.getDifferenceReason()).isNull();
        assertThat(close.getExpectedCashVnd()).isEqualTo(1_000_000);
        assertThat(close.getCloseReference()).startsWith("CASHCLOSE-");
    }

    @Test
    void aDifferenceNeedsAReasonAndWaitsForAManager() {
        when(collections.sumConfirmedCash(BRANCH, DAY)).thenReturn(1_000_000L);

        assertThatThrownBy(() -> service.close(BRANCH, DAY, 990_000, null, STAFF, "corr"))
                .isInstanceOf(WorkshopSettlementValidationException.class)
                .hasMessageContaining("CASH_DIFFERENCE_REASON_REQUIRED");

        SettlementCashDayClose close = service.close(BRANCH, DAY, 990_000, "Thieu tien le", STAFF, "corr");

        assertThat(close.getDifferenceVnd()).isEqualTo(-10_000);
        assertThat(close.getStatus()).isEqualTo(CashDayCloseStatus.PENDING_MANAGER_CONFIRMATION);
        assertThat(close.getDifferenceReason()).isEqualTo("Thieu tien le");
        assertThat(close.getConfirmedAt()).isNull();
    }

    @Test
    void aSurplusIsAlsoADifference() {
        when(collections.sumConfirmedCash(BRANCH, DAY)).thenReturn(1_000_000L);

        SettlementCashDayClose close = service.close(BRANCH, DAY, 1_020_000, "Thu them chua ghi", STAFF, "corr");

        assertThat(close.getDifferenceVnd()).isEqualTo(20_000);
        assertThat(close.getStatus()).isEqualTo(CashDayCloseStatus.PENDING_MANAGER_CONFIRMATION);
    }

    @Test
    void theManagerConfirmationIsWhatClosesADayThatDidNotBalance() {
        SettlementCashDayClose pending = SettlementCashDayClose.builder().branchId(BRANCH).businessDay(DAY)
                .closeReference("CASHCLOSE-1").expectedCashVnd(1_000_000).countedCashVnd(990_000)
                .differenceVnd(-10_000).differenceReason("Thieu tien le")
                .status(CashDayCloseStatus.PENDING_MANAGER_CONFIRMATION).closedByAccountId(STAFF).build();
        when(closes.findForUpdate(BRANCH, DAY)).thenReturn(Optional.of(pending));

        SettlementCashDayClose confirmed = service.confirmDifference(BRANCH, DAY, MANAGER, "corr");

        assertThat(confirmed.getStatus()).isEqualTo(CashDayCloseStatus.CLOSED);
        assertThat(confirmed.getConfirmedByAccountId()).isEqualTo(MANAGER);
        assertThat(confirmed.getConfirmedAt()).isNotNull();
    }

    @Test
    void confirmingAnAlreadyClosedDayChangesNothing() {
        SettlementCashDayClose closed = SettlementCashDayClose.builder().branchId(BRANCH).businessDay(DAY)
                .closeReference("CASHCLOSE-2").status(CashDayCloseStatus.CLOSED).closedByAccountId(STAFF).build();
        when(closes.findForUpdate(BRANCH, DAY)).thenReturn(Optional.of(closed));

        SettlementCashDayClose result = service.confirmDifference(BRANCH, DAY, MANAGER, "corr");

        assertThat(result.getConfirmedByAccountId()).isNull();
        verify(closes, never()).saveAndFlush(any());
    }

    @Test
    void aBranchCannotCloseTheSameDayTwice() {
        when(closes.findForUpdate(BRANCH, DAY)).thenReturn(Optional.of(
                SettlementCashDayClose.builder().branchId(BRANCH).businessDay(DAY).build()));

        assertThatThrownBy(() -> service.close(BRANCH, DAY, 1_000_000, null, STAFF, "corr"))
                .isInstanceOf(WorkshopSettlementConflictException.class)
                .hasMessageContaining("CASH_DAY_ALREADY_CLOSED");
    }

    @Test
    void invalidInputIsRefused() {
        assertThatThrownBy(() -> service.close(null, DAY, 1_000, null, STAFF, "corr"))
                .isInstanceOf(WorkshopSettlementValidationException.class);
        assertThatThrownBy(() -> service.close(BRANCH, DAY, -1, null, STAFF, "corr"))
                .isInstanceOf(WorkshopSettlementValidationException.class);
        assertThatThrownBy(() -> service.close(BRANCH, DAY.plusDays(1), 1_000, null, STAFF, "corr"))
                .isInstanceOf(WorkshopSettlementValidationException.class)
                .hasMessageContaining("BUSINESS_DAY_IN_THE_FUTURE");
    }

    @Test
    void readingACloseThatDoesNotExistIsANotFound() {
        assertThatThrownBy(() -> service.find(BRANCH, DAY)).isInstanceOf(WorkshopSettlementNotFoundException.class);
    }
}
