package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.AcceptedBenefitRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.PointLedgerRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.AcceptedBenefit;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.LedgerEntryKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.LoyaltyWallet;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.MemberProgram;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.PointLedger;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.PromotionProgram;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.AuthoritativeOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.BenefitResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.ComponentState;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.EarnState;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyErrorCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyException;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.OutcomeKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.SourceKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TargetScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T021: shared release/restore engine (FR-033-FR-038, US7.1-US7.7). Expected values come from the spec: release
 * gives held points and uses back without extra credit, a valid cancellation returns exactly the 200 points debited
 * and one use on both counters, nothing happens twice and nothing is invented for an unused component. Locks are
 * mocked here; the real MySQL locking and rollback are covered by the database tests.
 */
class LoyaltyLifecycleTest {

    private static final Instant ACCEPTED = Instant.parse("2026-10-27T01:00:00Z");
    private static final Instant DECIDED = Instant.parse("2026-10-27T02:00:00Z");
    private static final long MEMBER = 9_000_001L;
    private static final long PROGRAM = 5L;
    private static final long BENEFIT = 1L;
    private static final long DEBIT = 99L;

    private final AcceptedBenefitRepository benefits = mock(AcceptedBenefitRepository.class);
    private final PointLedgerRepository ledger = mock(PointLedgerRepository.class);
    private final LoyaltyLockCoordinator locks = mock(LoyaltyLockCoordinator.class);
    private final LoyaltyAuditService audit = mock(LoyaltyAuditService.class);
    private final List<PointLedger> posted = new ArrayList<>();

    private LoyaltyReleaseService release;
    private LoyaltyRestorationService restore;
    private PromotionProgram program;
    private MemberProgram memberProgram;
    private LoyaltyWallet wallet;

    @BeforeEach
    void setUp() {
        BenefitEffectSupport support = new BenefitEffectSupport(benefits, ledger, locks);
        release = new LoyaltyReleaseService(support, audit);
        restore = new LoyaltyRestorationService(support, audit);
        when(ledger.save(any(PointLedger.class))).thenAnswer(call -> {
            posted.add(call.getArgument(0));
            return call.getArgument(0);
        });
        PointLedger debit = PointLedger.entry(LedgerEntryKind.CONSUME, MEMBER, BENEFIT, BigInteger.valueOf(200),
                BigInteger.valueOf(300), BigInteger.ZERO, ACCEPTED, null);
        ReflectionTestUtils.setField(debit, "id", DEBIT);
        when(ledger.findByAcceptedBenefitIdAndEntryKind(BENEFIT, LedgerEntryKind.CONSUME)).thenReturn(Optional.of(debit));
    }

    private static BigInteger n(long value) {
        return BigInteger.valueOf(value);
    }

    /** Accepted benefit with points and/or a voucher held, as the accept transaction leaves it. */
    private AcceptedBenefit accepted(SourceKind kind, long points, boolean voucher) {
        AcceptedBenefit benefit = AcceptedBenefit.accept(new AcceptedBenefit.Snapshot(kind, "src-1", MEMBER, ACCEPTED,
                ACCEPTED.plusSeconds(1800), 1L, "digest", 1L, voucher ? PROGRAM : null, voucher ? 6L : null,
                n(1_000_000), n(voucher ? 100_000 : 0), n(points), n(points * 100), n(900_000 - points * 100),
                kind == SourceKind.WORKSHOP ? n(900_000 - points * 100) : null, voucher), true);
        ReflectionTestUtils.setField(benefit, "id", BENEFIT);

        program = PromotionProgram.voucher(TargetScope.WORKSHOP, "WS10", "WS10", 10, 2, ACCEPTED, 1L);
        ReflectionTestUtils.setField(program, "id", PROGRAM);
        memberProgram = MemberProgram.create(PROGRAM, MEMBER);
        wallet = LoyaltyWallet.open(MEMBER);
        wallet.earn(n(500));
        if (voucher) {
            program.hold();
            memberProgram.hold(2);
        }
        if (points > 0) {
            wallet.hold(n(points));
        }

        AcceptedBenefitRepository.References references = new AcceptedBenefitRepository.References() {
            @Override
            public Long getMemberId() {
                return benefit.getMemberId();
            }

            @Override
            public Long getProgramId() {
                return benefit.getProgramId();
            }

            @Override
            public BigInteger getRequestedPoints() {
                return benefit.getRequestedPoints();
            }
        };
        when(benefits.findReferences(kind, "src-1")).thenReturn(Optional.of(references));
        when(locks.lockBenefit(kind, "src-1")).thenReturn(Optional.of(benefit));
        when(locks.lockPrograms(anyCollection())).thenReturn(Map.of(PROGRAM, program));
        when(locks.lockWallet(MEMBER)).thenReturn(Optional.of(wallet));
        when(locks.lockOrCreateMemberProgram(PROGRAM, MEMBER)).thenReturn(memberProgram);
        return benefit;
    }

    /** Deposit or payment verified by the owner: HELD components become CONSUMED (what settle will do). */
    private void settled(AcceptedBenefit benefit) {
        AcceptedBenefit.Changes changes = benefit.settle("paid-1", 1, ACCEPTED);
        if (changes.points()) {
            wallet.consume(benefit.getRequestedPoints());
        }
        if (changes.voucher()) {
            program.consume();
            memberProgram.consume();
        }
    }

    private static AuthoritativeOutcome outcome(SourceKind kind, OutcomeKind outcome, String key, long version) {
        return new AuthoritativeOutcome(kind, "src-1", kind == SourceKind.WORKSHOP ? "workshopbooking" : "readyringsales",
                key, version, outcome, DECIDED, ACCEPTED.plusSeconds(1800), "snap-1", true, "proof-1");
    }

    private static void assertConflict(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(LoyaltyException.class,
                e -> assertThat(e.code()).isEqualTo(LoyaltyErrorCode.BENEFIT_STATE_CONFLICT));
    }

    // ----- release on a confirmed unpaid expiry -----

    @Test
    void unpaidExpiryReleasesHeldPointsAndUsesWithoutExtraCredit() {
        accepted(SourceKind.RETAIL, 200, true);
        BenefitResult result = release.releaseHeldBenefits(outcome(SourceKind.RETAIL, OutcomeKind.UNPAID_EXPIRY_CONFIRMED, "exp-1", 2));

        assertThat(result.pointsState()).isEqualTo(ComponentState.RELEASED);
        assertThat(result.voucherState()).isEqualTo(ComponentState.RELEASED);
        assertThat(result.earnState()).isEqualTo(EarnState.INELIGIBLE);
        assertThat(wallet.getBalancePoints()).as("no extra credit").isEqualTo(n(500));
        assertThat(wallet.getHeldPoints()).isZero();
        assertThat(program.getHeldCount()).isZero();
        assertThat(program.getConsumedCount()).as("never counted as used").isZero();
        assertThat(memberProgram.getHeldCount()).isZero();
        assertThat(posted).singleElement().satisfies(entry -> {
            assertThat(entry.getEntryKind()).isEqualTo(LedgerEntryKind.RELEASE);
            assertThat(entry.getDeltaBalance()).isZero();
            assertThat(entry.getDeltaHeld()).isEqualTo(n(-200));
        });
        verify(audit).record(eq("readyringsales"), eq("benefit.release"), eq("benefit:1"), anyString());
    }

    @Test
    void repeatedExpiryReleasesOnlyOnce() {
        accepted(SourceKind.RETAIL, 200, true);
        AuthoritativeOutcome expiry = outcome(SourceKind.RETAIL, OutcomeKind.UNPAID_EXPIRY_CONFIRMED, "exp-1", 2);
        release.releaseHeldBenefits(expiry);
        release.releaseHeldBenefits(expiry);

        assertThat(posted).hasSize(1);
        assertThat(wallet.getHeldPoints()).isZero();
        assertThat(wallet.getBalancePoints()).isEqualTo(n(500));
    }

    @Test
    void aSettledTransactionIsNeverReleased() {
        AcceptedBenefit benefit = accepted(SourceKind.RETAIL, 200, true);
        settled(benefit);
        assertConflict(() -> release.releaseHeldBenefits(
                outcome(SourceKind.RETAIL, OutcomeKind.UNPAID_EXPIRY_CONFIRMED, "exp-1", 2)));
        assertThat(wallet.getBalancePoints()).isEqualTo(n(300));
        assertThat(program.getConsumedCount()).isEqualTo(1);
        assertThat(posted).isEmpty();
    }

    @Test
    void noShowOrCompletionCannotRelease() {
        accepted(SourceKind.WORKSHOP, 200, true);
        assertConflict(() -> release.releaseHeldBenefits(
                outcome(SourceKind.WORKSHOP, OutcomeKind.WORKSHOP_NO_SHOW_FINAL, "ns-1", 2)));
        assertConflict(() -> release.releaseHeldBenefits(
                outcome(SourceKind.WORKSHOP, OutcomeKind.WORKSHOP_COMPLETED_AND_FULLY_PAID, "done-1", 2)));
        assertThat(wallet.getHeldPoints()).isEqualTo(n(200));
    }

    @Test
    void anUnknownSourceTransactionIsAConflict() {
        when(benefits.findReferences(any(), anyString())).thenReturn(Optional.empty());
        assertConflict(() -> release.releaseHeldBenefits(
                outcome(SourceKind.RETAIL, OutcomeKind.UNPAID_EXPIRY_CONFIRMED, "exp-1", 2)));
        verify(locks, never()).readRegistry();
    }

    // ----- restore on a valid workshop cancellation -----

    @Test
    void validCancellationAfterDepositReturnsTheOriginalPointsAndBothCounters() {
        AcceptedBenefit benefit = accepted(SourceKind.WORKSHOP, 200, true);
        settled(benefit);
        BenefitResult result = restore.restoreCancelledWorkshopBenefits(
                outcome(SourceKind.WORKSHOP, OutcomeKind.VALID_WORKSHOP_CANCELLATION, "cancel-1", 2));

        assertThat(result.pointsState()).isEqualTo(ComponentState.RESTORED);
        assertThat(result.voucherState()).isEqualTo(ComponentState.RESTORED);
        assertThat(wallet.getBalancePoints()).as("200 points back, not converted").isEqualTo(n(500));
        assertThat(program.getConsumedCount()).isZero();
        assertThat(memberProgram.getConsumedCount()).isZero();
        assertThat(posted).singleElement().satisfies(entry -> {
            assertThat(entry.getEntryKind()).isEqualTo(LedgerEntryKind.RESTORE);
            assertThat(entry.getDeltaBalance()).isEqualTo(n(200));
            assertThat(entry.getDeltaHeld()).isZero();
            assertThat(entry.getOriginalDebitId()).isEqualTo(DEBIT);
        });
        assertThat(program.getStatus()).as("not reactivated or changed").isEqualTo(
                edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.ProgramStatus.ENABLED);
    }

    @Test
    void validCancellationWhileStillHeldOnlyReleases() {
        accepted(SourceKind.WORKSHOP, 200, true);
        BenefitResult result = restore.restoreCancelledWorkshopBenefits(
                outcome(SourceKind.WORKSHOP, OutcomeKind.VALID_WORKSHOP_CANCELLATION, "cancel-1", 2));

        assertThat(result.pointsState()).isEqualTo(ComponentState.RELEASED);
        assertThat(result.voucherState()).isEqualTo(ComponentState.RELEASED);
        assertThat(wallet.getBalancePoints()).as("not released and restored at the same time").isEqualTo(n(500));
        assertThat(wallet.getHeldPoints()).isZero();
        assertThat(program.getHeldCount()).isZero();
        assertThat(posted).singleElement().extracting(PointLedger::getEntryKind).isEqualTo(LedgerEntryKind.RELEASE);
    }

    @Test
    void voucherOnlyCancellationReturnsTheUseAndPostsNoZeroLedgerEntry() {
        AcceptedBenefit benefit = accepted(SourceKind.WORKSHOP, 0, true);
        settled(benefit);
        BenefitResult result = restore.restoreCancelledWorkshopBenefits(
                outcome(SourceKind.WORKSHOP, OutcomeKind.VALID_WORKSHOP_CANCELLATION, "cancel-1", 2));

        assertThat(result.pointsState()).isEqualTo(ComponentState.NONE);
        assertThat(result.voucherState()).isEqualTo(ComponentState.RESTORED);
        assertThat(program.getConsumedCount()).isZero();
        assertThat(memberProgram.getConsumedCount()).isZero();
        assertThat(posted).isEmpty();
        verify(locks, never()).lockWallet(anyLong());
    }

    @Test
    void pointsOnlyCancellationReturnsThePointsAndTouchesNoQuota() {
        AcceptedBenefit benefit = accepted(SourceKind.WORKSHOP, 200, false);
        settled(benefit);
        BenefitResult result = restore.restoreCancelledWorkshopBenefits(
                outcome(SourceKind.WORKSHOP, OutcomeKind.VALID_WORKSHOP_CANCELLATION, "cancel-1", 2));

        assertThat(result.pointsState()).isEqualTo(ComponentState.RESTORED);
        assertThat(result.voucherState()).isEqualTo(ComponentState.NONE);
        assertThat(wallet.getBalancePoints()).isEqualTo(n(500));
        verify(locks, never()).lockOrCreateMemberProgram(anyLong(), anyLong());
    }

    @Test
    void repeatedCancellationRestoresOnlyOnce() {
        AcceptedBenefit benefit = accepted(SourceKind.WORKSHOP, 200, true);
        settled(benefit);
        AuthoritativeOutcome cancel = outcome(SourceKind.WORKSHOP, OutcomeKind.VALID_WORKSHOP_CANCELLATION, "cancel-1", 2);
        restore.restoreCancelledWorkshopBenefits(cancel);
        restore.restoreCancelledWorkshopBenefits(cancel);

        assertThat(posted).hasSize(1);
        assertThat(wallet.getBalancePoints()).isEqualTo(n(500));
        assertThat(program.getConsumedCount()).isZero();
    }

    @Test
    void aDifferentCancellationOutcomeAfterRestoreIsAConflict() {
        AcceptedBenefit benefit = accepted(SourceKind.WORKSHOP, 200, true);
        settled(benefit);
        restore.restoreCancelledWorkshopBenefits(
                outcome(SourceKind.WORKSHOP, OutcomeKind.VALID_WORKSHOP_CANCELLATION, "cancel-1", 2));
        assertConflict(() -> restore.restoreCancelledWorkshopBenefits(
                outcome(SourceKind.WORKSHOP, OutcomeKind.VALID_WORKSHOP_CANCELLATION, "cancel-2", 3)));
        assertThat(wallet.getBalancePoints()).isEqualTo(n(500));
    }

    @Test
    void onlyAValidWorkshopCancellationRestores() {
        AcceptedBenefit benefit = accepted(SourceKind.WORKSHOP, 200, true);
        settled(benefit);
        for (OutcomeKind kind : new OutcomeKind[] {OutcomeKind.WORKSHOP_NO_SHOW_FINAL,
                OutcomeKind.WORKSHOP_COMPLETED_AND_FULLY_PAID, OutcomeKind.UNPAID_EXPIRY_CONFIRMED}) {
            assertConflict(() -> restore.restoreCancelledWorkshopBenefits(outcome(SourceKind.WORKSHOP, kind, "x", 2)));
        }
        assertThat(wallet.getBalancePoints()).as("a no-show never refunds").isEqualTo(n(300));
        assertThat(program.getConsumedCount()).isEqualTo(1);
    }

    @Test
    void aCompletedWorkshopThatEarnedPointsCannotBeRestored() {
        AcceptedBenefit benefit = accepted(SourceKind.WORKSHOP, 200, true);
        settled(benefit);
        benefit.grantEarn("done-1", 2, n(88));
        assertConflict(() -> restore.restoreCancelledWorkshopBenefits(
                outcome(SourceKind.WORKSHOP, OutcomeKind.VALID_WORKSHOP_CANCELLATION, "cancel-1", 3)));
        assertThat(posted).isEmpty();
    }

    @Test
    void aLateOlderOutcomeIsRejected() {
        AcceptedBenefit benefit = accepted(SourceKind.WORKSHOP, 200, true);
        benefit.settle("dep-1", 5, ACCEPTED);
        assertConflict(() -> restore.restoreCancelledWorkshopBenefits(
                outcome(SourceKind.WORKSHOP, OutcomeKind.VALID_WORKSHOP_CANCELLATION, "cancel-1", 4)));
    }
}
