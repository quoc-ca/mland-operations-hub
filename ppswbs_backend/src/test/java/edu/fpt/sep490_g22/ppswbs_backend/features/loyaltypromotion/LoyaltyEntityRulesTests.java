package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.AcceptedBenefit;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.AcceptedBenefit.Changes;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.AcceptedBenefit.Snapshot;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.LedgerEntryKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.LoyaltyWallet;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.MemberProgram;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.PointLedger;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.PromotionProgram;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.PromotionTarget;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.ComponentState;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.EarnState;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyErrorCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyException;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.SourceKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TargetDimension;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TargetScope;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** State and invariant rules of the entities (data-model sections 2, 4 and 5), without a database. */
class LoyaltyEntityRulesTests {

    private static final Instant NOW = Instant.parse("2026-10-26T00:00:00Z");

    private static BigInteger n(long value) {
        return BigInteger.valueOf(value);
    }

    private static void assertCode(Runnable action, LoyaltyErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(LoyaltyException.class,
                e -> assertThat(e.code()).isEqualTo(code));
    }

    private static PromotionProgram voucher(long total, long perMember) {
        return PromotionProgram.voucher(TargetScope.PRODUCT, "TET10", "TET10", total, perMember, NOW, 1L);
    }

    private static AcceptedBenefit accepted(SourceKind kind, long points, boolean voucherHeld) {
        Snapshot snapshot = new Snapshot(kind, "ref-1", 7L, NOW, NOW.plusSeconds(3600), 1L, "digest", 1L,
                voucherHeld ? 5L : null, voucherHeld ? 6L : null, n(1_000_000), n(100_000), n(points),
                n(points * 100), n(900_000 - points * 100),
                kind == SourceKind.WORKSHOP ? n(900_000 - points * 100) : null, voucherHeld);
        return AcceptedBenefit.accept(snapshot, true);
    }

    // ----- programme quota -----

    @Test
    void lastSlotCanOnlyBeHeldOnce() {
        PromotionProgram program = voucher(1, 1);
        program.hold();
        assertCode(program::hold, LoyaltyErrorCode.QUOTA_LIMIT_CONFLICT);
        assertThat(program.availableTotal()).isZero();
    }

    @Test
    void usedHeldAndRestoredCountersFollowTheLifecycle() {
        PromotionProgram program = voucher(10, 3);
        program.hold();
        program.hold();
        program.consume();
        program.release();
        assertThat(program.getHeldCount()).isZero();
        assertThat(program.getConsumedCount()).isEqualTo(1);
        program.restore();
        assertThat(program.getConsumedCount()).isZero();
        assertCode(program::restore, LoyaltyErrorCode.BENEFIT_STATE_CONFLICT);
        assertCode(program::release, LoyaltyErrorCode.BENEFIT_STATE_CONFLICT);
    }

    @Test
    void totalLimitCannotDropBelowUsedPlusHeld() {
        // US2.3: 6 used + 2 held, lowering to 7 is rejected, 8 is accepted.
        PromotionProgram program = voucher(20, 20);
        for (int i = 0; i < 8; i++) {
            program.hold();
        }
        for (int i = 0; i < 6; i++) {
            program.consume();
        }
        assertCode(() -> program.changeLimits(7, 20), LoyaltyErrorCode.QUOTA_LIMIT_CONFLICT);
        program.changeLimits(8, 20);
        assertThat(program.getTotalLimit()).isEqualTo(8);
    }

    @Test
    void pauseAndResumeKeepQuotaAndRejectWrongState() {
        PromotionProgram program = voucher(5, 2);
        program.hold();
        program.consume();
        program.pause();
        assertCode(program::pause, LoyaltyErrorCode.BENEFIT_STATE_CONFLICT);
        program.resume();
        assertThat(program.getConsumedCount()).isEqualTo(1);
        assertCode(program::resume, LoyaltyErrorCode.BENEFIT_STATE_CONFLICT);
    }

    @Test
    void automaticDiscountIsProductOnlyWithoutCodeOrQuota() {
        PromotionProgram auto = PromotionProgram.autoDiscount(NOW, 1L);
        assertThat(auto.getScope()).isEqualTo(TargetScope.PRODUCT);
        assertThat(auto.getCodeKey()).isNull();
        assertCode(auto::hold, LoyaltyErrorCode.BENEFIT_STATE_CONFLICT);
    }

    @Test
    void memberLimitIsChecked() {
        MemberProgram row = MemberProgram.create(5L, 7L);
        row.hold(1);
        assertCode(() -> row.hold(1), LoyaltyErrorCode.QUOTA_LIMIT_CONFLICT);
        row.consume();
        assertCode(() -> row.hold(1), LoyaltyErrorCode.QUOTA_LIMIT_CONFLICT);
        row.restore();
        row.hold(1);
    }

    @Test
    void savingIsIdempotentAndTouchesNoCounter() {
        MemberProgram row = MemberProgram.create(5L, 7L);
        row.save(NOW);
        row.save(NOW.plusSeconds(60));
        assertThat(row.getSavedAt()).isEqualTo(NOW);
        assertThat(row.getHeldCount()).isZero();
        assertThat(row.getConsumedCount()).isZero();
    }

    // ----- wallet and ledger -----

    @Test
    void walletTracksHeldAvailableAndNeverSpendsHeldPointsTwice() {
        LoyaltyWallet wallet = LoyaltyWallet.open(7L);
        wallet.earn(n(200));
        wallet.hold(n(50));
        assertThat(wallet.availablePoints()).isEqualTo(n(150));
        assertCode(() -> wallet.hold(n(151)), LoyaltyErrorCode.POINTS_LIMIT_CHANGED);
        wallet.consume(n(50));
        assertThat(wallet.getBalancePoints()).isEqualTo(n(150));
        assertThat(wallet.getHeldPoints()).isZero();
        assertCode(() -> wallet.consume(n(1)), LoyaltyErrorCode.BENEFIT_STATE_CONFLICT);
    }

    @Test
    void releaseGivesNoExtraCreditAndRestoreReturnsTheOriginalAmount() {
        LoyaltyWallet wallet = LoyaltyWallet.open(7L);
        wallet.earn(n(200));
        wallet.hold(n(200));
        wallet.release(n(200));
        assertThat(wallet.getBalancePoints()).isEqualTo(n(200));
        wallet.hold(n(200));
        wallet.consume(n(200));
        wallet.restore(n(200));
        assertThat(wallet.getBalancePoints()).isEqualTo(n(200));
        assertThat(wallet.getHeldPoints()).isZero();
    }

    @Test
    void ledgerDeltasAreFixedPerEntryKind() {
        PointLedger hold = PointLedger.entry(LedgerEntryKind.HOLD, 7L, 1L, n(10), n(200), n(10), NOW, null);
        assertThat(hold.getDeltaBalance()).isZero();
        assertThat(hold.getDeltaHeld()).isEqualTo(n(10));
        PointLedger consume = PointLedger.entry(LedgerEntryKind.CONSUME, 7L, 1L, n(10), n(190), n(0), NOW, null);
        assertThat(consume.getDeltaBalance()).isEqualTo(n(-10));
        assertThat(consume.getDeltaHeld()).isEqualTo(n(-10));
        PointLedger restore = PointLedger.entry(LedgerEntryKind.RESTORE, 7L, 1L, n(10), n(200), n(0), NOW, 99L);
        assertThat(restore.getDeltaBalance()).isEqualTo(n(10));
        assertThat(restore.getOriginalDebitId()).isEqualTo(99L);
        PointLedger earn = PointLedger.entry(LedgerEntryKind.EARN, 7L, 2L, n(73), n(273), n(0), NOW, null);
        assertThat(earn.getDeltaBalance()).isEqualTo(n(73));
        assertThatThrownBy(() -> PointLedger.entry(LedgerEntryKind.RESTORE, 7L, 1L, n(10), n(1), n(0), NOW, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PointLedger.entry(LedgerEntryKind.EARN, 7L, 1L, BigInteger.ZERO, n(1), n(0), NOW, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ----- accepted benefit lifecycle -----

    @Test
    void retailSettleConsumesOnceAndReplaysAsNoOp() {
        AcceptedBenefit benefit = accepted(SourceKind.RETAIL, 200, true);
        assertThat(benefit.getPointsState()).isEqualTo(ComponentState.HELD);
        Changes first = benefit.settle("paid-1", 1, NOW);
        assertThat(first.points()).isTrue();
        assertThat(first.voucher()).isTrue();
        assertThat(benefit.getVoucherState()).isEqualTo(ComponentState.CONSUMED);
        assertThat(benefit.countsAsVoucherUse()).isTrue();
        assertThat(benefit.settle("paid-1", 1, NOW).any()).isFalse();
        assertCode(() -> benefit.settle("paid-2", 2, NOW), LoyaltyErrorCode.BENEFIT_STATE_CONFLICT);
    }

    @Test
    void settlementIsRecordedEvenWhenNothingWasHeld() {
        AcceptedBenefit benefit = accepted(SourceKind.RETAIL, 0, false);
        assertThat(benefit.settle("paid-1", 1, NOW).any()).isFalse();
        assertThat(benefit.getSettledAt()).isEqualTo(NOW);
        assertThat(benefit.getSettlementOutcomeKey()).isEqualTo("paid-1");
    }

    @Test
    void expiryReleasesHeldOnlyAndCannotReleaseConsumed() {
        AcceptedBenefit expired = accepted(SourceKind.RETAIL, 200, true);
        Changes released = expired.release("expiry-1", 1, NOW);
        assertThat(released.points()).isTrue();
        assertThat(expired.getPointsState()).isEqualTo(ComponentState.RELEASED);
        assertThat(expired.getEarnState()).isEqualTo(EarnState.INELIGIBLE);
        assertThat(expired.release("expiry-1", 1, NOW).any()).isFalse();
        assertCode(() -> expired.settle("paid-1", 2, NOW), LoyaltyErrorCode.BENEFIT_STATE_CONFLICT);

        AcceptedBenefit paid = accepted(SourceKind.RETAIL, 200, true);
        paid.settle("paid-1", 1, NOW);
        assertCode(() -> paid.release("expiry-1", 2, NOW), LoyaltyErrorCode.BENEFIT_STATE_CONFLICT);
    }

    @Test
    void validCancellationRestoresConsumedAndReleasesHeld() {
        AcceptedBenefit deposited = accepted(SourceKind.WORKSHOP, 200, true);
        deposited.settle("deposit-1", 1, NOW);
        Changes restored = deposited.restore("cancel-1", 2, NOW);
        assertThat(restored.points()).isTrue();
        assertThat(restored.voucher()).isTrue();
        assertThat(deposited.getPointsState()).isEqualTo(ComponentState.RESTORED);
        assertThat(deposited.getVoucherState()).isEqualTo(ComponentState.RESTORED);
        assertThat(deposited.countsAsVoucherUse()).isFalse();
        assertThat(deposited.getSettledAt()).as("history is kept").isEqualTo(NOW);
        assertThat(deposited.restore("cancel-1", 2, NOW).any()).as("repeat does not restore twice").isFalse();

        AcceptedBenefit heldOnly = accepted(SourceKind.WORKSHOP, 200, true);
        Changes released = heldOnly.restore("cancel-2", 1, NOW);
        assertThat(released.points()).isTrue();
        assertThat(heldOnly.getPointsState()).isEqualTo(ComponentState.RELEASED);
    }

    @Test
    void pointsOnlyAndVoucherOnlyRestoreOnlyWhatWasUsed() {
        AcceptedBenefit pointsOnly = accepted(SourceKind.WORKSHOP, 200, false);
        pointsOnly.settle("deposit-1", 1, NOW);
        Changes a = pointsOnly.restore("cancel-1", 2, NOW);
        assertThat(a.points()).isTrue();
        assertThat(a.voucher()).isFalse();

        AcceptedBenefit voucherOnly = accepted(SourceKind.WORKSHOP, 0, true);
        voucherOnly.settle("deposit-1", 1, NOW);
        Changes b = voucherOnly.restore("cancel-1", 2, NOW);
        assertThat(b.points()).isFalse();
        assertThat(b.voucher()).isTrue();
    }

    @Test
    void olderOutcomeVersionIsRejected() {
        AcceptedBenefit benefit = accepted(SourceKind.RETAIL, 200, true);
        benefit.settle("paid-1", 5, NOW);
        assertCode(() -> benefit.release("late", 4, NOW), LoyaltyErrorCode.BENEFIT_STATE_CONFLICT);
    }

    @Test
    void earnRequiresConsumedOrUnusedComponentsAndHappensOnce() {
        AcceptedBenefit benefit = accepted(SourceKind.RETAIL, 200, false);
        assertCode(() -> benefit.grantEarn("paid-1", 1, n(73)), LoyaltyErrorCode.BENEFIT_STATE_CONFLICT);
        benefit.settle("paid-1", 1, NOW);
        assertThat(benefit.grantEarn("paid-1", 1, n(73))).isTrue();
        assertThat(benefit.getEarnedPoints()).isEqualTo(n(73));
        assertThat(benefit.grantEarn("paid-1", 1, n(73))).isFalse();
        assertCode(() -> benefit.grantEarn("other", 2, n(73)), LoyaltyErrorCode.BENEFIT_STATE_CONFLICT);
        assertCode(() -> benefit.finishIneligibleEarn("late", 3, NOW), LoyaltyErrorCode.BENEFIT_STATE_CONFLICT);
    }

    @Test
    void zeroEarnIsStillAGrantedResult() {
        AcceptedBenefit benefit = accepted(SourceKind.RETAIL, 0, false);
        benefit.settle("paid-1", 1, NOW);
        assertThat(benefit.grantEarn("paid-1", 1, BigInteger.ZERO)).isTrue();
        assertThat(benefit.getEarnState()).isEqualTo(EarnState.GRANTED);
    }

    @Test
    void noShowMakesEarnIneligibleWithoutRefundingConsumedComponents() {
        AcceptedBenefit benefit = accepted(SourceKind.WORKSHOP, 200, true);
        benefit.settle("deposit-1", 1, NOW);
        assertThat(benefit.finishIneligibleEarn("noshow-1", 2, NOW)).isTrue();
        assertThat(benefit.getPointsState()).isEqualTo(ComponentState.CONSUMED);
        assertThat(benefit.getVoucherState()).isEqualTo(ComponentState.CONSUMED);
        assertThat(benefit.finishIneligibleEarn("noshow-1", 2, NOW)).isFalse();
    }

    @Test
    void guestOriginNeverQualifies() {
        Snapshot snapshot = new Snapshot(SourceKind.RETAIL, "ref-2", 7L, NOW, NOW.plusSeconds(60), 1L, "d", 1L,
                null, null, n(10), n(0), n(0), n(0), n(10), null, false);
        assertCode(() -> AcceptedBenefit.accept(snapshot, false), LoyaltyErrorCode.PROMOTION_INELIGIBLE);
    }

    @Test
    void workshopSnapshotNeedsThePostPromotionAmountAndRetailHasNone() {
        AcceptedBenefit workshop = accepted(SourceKind.WORKSHOP, 200, false);
        assertThat(workshop.getPostPromotionAmount()).isEqualTo(n(880_000));
        Snapshot noPost = new Snapshot(SourceKind.WORKSHOP, "ref-3", 7L, NOW, NOW.plusSeconds(60), 1L, "d", 1L,
                null, null, n(10), n(0), n(0), n(0), n(10), null, false);
        assertCode(() -> AcceptedBenefit.accept(noPost, true), LoyaltyErrorCode.INVALID_INPUT);
        Snapshot retailWithPost = new Snapshot(SourceKind.RETAIL, "ref-4", 7L, NOW, NOW.plusSeconds(60), 1L, "d", 1L,
                null, null, n(10), n(0), n(0), n(0), n(10), n(10), false);
        assertCode(() -> AcceptedBenefit.accept(retailWithPost, true), LoyaltyErrorCode.INVALID_INPUT);
    }

    // ----- targets -----

    @Test
    void targetDimensionsMustMatchTheScope() {
        PromotionTarget.create(1L, TargetScope.PRODUCT, TargetDimension.VARIANT, "10");
        PromotionTarget.create(1L, TargetScope.WORKSHOP, TargetDimension.BRANCH, "3");
        assertThatThrownBy(() -> PromotionTarget.create(1L, TargetScope.PRODUCT, TargetDimension.BRANCH, "3"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PromotionTarget.create(1L, TargetScope.WORKSHOP, TargetDimension.PRODUCT, "3"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
