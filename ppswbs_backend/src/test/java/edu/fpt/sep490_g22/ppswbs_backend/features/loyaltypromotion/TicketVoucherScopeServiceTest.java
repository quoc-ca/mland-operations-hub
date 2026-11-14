package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.AcceptedBenefitPartRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.AcceptedBenefitRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.PromotionTargetRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.AcceptedBenefit;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.AcceptedBenefitPart;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.PromotionTarget;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.ComponentState;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyException;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.SourceKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TargetDimension;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TargetScope;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TicketVoucherScope;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TicketVoucherScopeQuery;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.WorkshopSessionFact;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.TicketVoucherScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 003.3 FR-045 / ERROR-LOG BE-DESIGN-0007: per-ticket voucher scope of a proposed session set, judged on the ACCEPTED
 * revision's targets. Cases 1-9 of {@code ticket-change-voucher-scope-request.md}.
 */
class TicketVoucherScopeServiceTest {

    private static final String GROUP = "GRP-20261114-001";
    private static final String TICKET = "L0-T1";
    private static final long PACKAGE = 7L;
    private static final long ACCEPTED_REVISION = 91L;
    private static final LocalDate DAY = LocalDate.parse("2026-11-20");
    private static final String S1 = "00000000-0000-4000-8000-000000000011";
    private static final String S2 = "00000000-0000-4000-8000-000000000012";
    private static final String S3 = "00000000-0000-4000-8000-000000000013";

    private AcceptedBenefitRepository benefits;
    private AcceptedBenefitPartRepository parts;
    private PromotionTargetRepository targets;
    private TicketVoucherScopeService service;
    private AcceptedBenefit benefit;
    private final List<PromotionTarget> rows = new ArrayList<>();

    @BeforeEach
    void setUp() {
        benefits = mock(AcceptedBenefitRepository.class);
        parts = mock(AcceptedBenefitPartRepository.class);
        targets = mock(PromotionTargetRepository.class);
        service = new TicketVoucherScopeService(benefits, parts, targets);

        benefit = mock(AcceptedBenefit.class);
        when(benefit.getId()).thenReturn(5L);
        when(benefit.getRevisionId()).thenReturn(ACCEPTED_REVISION);
        when(benefit.getVoucherState()).thenReturn(ComponentState.CONSUMED);
        when(benefits.findBySourceKindAndSourceRef(SourceKind.WORKSHOP, GROUP)).thenReturn(Optional.of(benefit));
        AcceptedBenefitPart part = mock(AcceptedBenefitPart.class);
        when(part.getPartRef()).thenReturn(TICKET);
        when(part.getProgramDiscount()).thenReturn(BigInteger.valueOf(27_273));
        AcceptedBenefitPart other = mock(AcceptedBenefitPart.class);
        when(other.getPartRef()).thenReturn("L0-T2");
        when(parts.findByAcceptedBenefitIdOrderByPartSeqAsc(5L)).thenReturn(List.of(other, part));
        when(targets.findByRevisionIdOrderByDimensionAscTargetKeyAsc(ACCEPTED_REVISION)).thenReturn(rows);
        target(TargetDimension.PACKAGE, String.valueOf(PACKAGE));
    }

    private void target(TargetDimension dimension, String key) {
        rows.add(PromotionTarget.create(ACCEPTED_REVISION, TargetScope.WORKSHOP, dimension, key));
    }

    private TicketVoucherScope ask(WorkshopSessionFact... sessions) {
        return service.check(new TicketVoucherScopeQuery(SourceKind.WORKSHOP, GROUP, TICKET, PACKAGE, List.of(sessions)));
    }

    private static void assertKeeps(TicketVoucherScope scope) {
        assertThat(scope.voucherApplied()).isTrue();
        assertThat(scope.inScope()).isTrue();
        assertThat(scope.lostDiscount()).isZero();
    }

    private static void assertLoses(TicketVoucherScope scope) {
        assertThat(scope.voucherApplied()).isTrue();
        assertThat(scope.inScope()).isFalse();
        assertThat(scope.lostDiscount()).isEqualTo(BigInteger.valueOf(27_273));
    }

    @Test
    void case1_packageOnlyVoucher_anotherSessionOfThePackage_staysInScope() {
        assertKeeps(ask(WorkshopSessionFact.of(S2, 4L, DAY.plusDays(3))));
    }

    @Test
    void case2_branchRestricted_movedToAnotherBranch_losesItsShare() {
        target(TargetDimension.BRANCH, "2");
        assertKeeps(ask(WorkshopSessionFact.of(S1, 2L, DAY)));
        assertLoses(ask(WorkshopSessionFact.of(S2, 3L, DAY)));
    }

    @Test
    void case3_dateRestricted_movedToAnotherDate_losesItsShare() {
        target(TargetDimension.WORKSHOP_DATE, DAY.toString());
        assertLoses(ask(WorkshopSessionFact.of(S1, 2L, DAY.plusDays(1))));
    }

    @Test
    void case4_sessionRestricted_movedToAnotherSession_losesItsShare() {
        target(TargetDimension.SESSION, S1);
        assertLoses(ask(WorkshopSessionFact.of(S2, 2L, DAY)));
    }

    @Test
    void case5_noBranchRestriction_movedToAnotherBranch_staysInScope() {
        target(TargetDimension.WORKSHOP_DATE, DAY.toString());
        assertKeeps(ask(WorkshopSessionFact.of(S2, 9L, DAY)));
    }

    @Test
    void case6_twoSessionTicket_onlyOneOutsideTheRestriction_isOutOfScope() {
        target(TargetDimension.BRANCH, "2");
        assertLoses(ask(WorkshopSessionFact.of(S1, 2L, DAY), WorkshopSessionFact.of(S3, 3L, DAY.plusDays(1))));
    }

    @Test
    void cases7and8_pointsOnlyOrAutomaticDiscount_noVoucherToLose() {
        target(TargetDimension.BRANCH, "2");
        when(benefit.getVoucherState()).thenReturn(ComponentState.NONE);
        TicketVoucherScope scope = ask(WorkshopSessionFact.of(S2, 3L, DAY));
        assertThat(scope.voucherApplied()).isFalse();
        assertThat(scope.lostDiscount()).isZero();
        verifyNoInteractions(parts, targets);
    }

    @Test
    void case9_askingTwiceAnswersTheSameAndWritesNothing() {
        target(TargetDimension.BRANCH, "2");
        TicketVoucherScope first = ask(WorkshopSessionFact.of(S2, 3L, DAY));
        TicketVoucherScope second = ask(WorkshopSessionFact.of(S2, 3L, DAY));
        assertThat(second).isEqualTo(first);
        verify(benefits, never()).save(any());
        verify(benefits, never()).lockBySource(any(), any());
        verify(parts, never()).save(any());
    }

    @Test
    void theAcceptedRevisionIsJudgedNotTheCurrentOne() {
        target(TargetDimension.BRANCH, "2");
        ask(WorkshopSessionFact.of(S1, 2L, DAY));
        verify(targets).findByRevisionIdOrderByDimensionAscTargetKeyAsc(ACCEPTED_REVISION);
    }

    @Test
    void aGivenBackVoucherOrNoAcceptedBenefitHasNothingToLose() {
        when(benefit.getVoucherState()).thenReturn(ComponentState.RESTORED);
        assertThat(ask(WorkshopSessionFact.of(S2, 3L, DAY)).voucherApplied()).isFalse();
        when(benefits.findBySourceKindAndSourceRef(SourceKind.WORKSHOP, GROUP)).thenReturn(Optional.empty());
        assertThat(ask(WorkshopSessionFact.of(S2, 3L, DAY))).isEqualTo(TicketVoucherScope.noVoucher());
    }

    @Test
    void aTicketOutsideTheTransactionIsRefused() {
        assertThatThrownBy(() -> service.check(new TicketVoucherScopeQuery(SourceKind.WORKSHOP, GROUP, "L9-T9", PACKAGE,
                List.of(WorkshopSessionFact.of(S1, 2L, DAY))))).isInstanceOf(LoyaltyException.class);
    }

    @Test
    void theQueryIsOnlyForAWorkshopTicketWithSessions() {
        assertThatThrownBy(() -> new TicketVoucherScopeQuery(SourceKind.RETAIL, GROUP, TICKET, PACKAGE,
                List.of(WorkshopSessionFact.of(S1, 2L, DAY)))).isInstanceOf(LoyaltyException.class);
        assertThatThrownBy(() -> new TicketVoucherScopeQuery(SourceKind.WORKSHOP, GROUP, TICKET, PACKAGE, List.of()))
                .isInstanceOf(LoyaltyException.class);
    }
}
