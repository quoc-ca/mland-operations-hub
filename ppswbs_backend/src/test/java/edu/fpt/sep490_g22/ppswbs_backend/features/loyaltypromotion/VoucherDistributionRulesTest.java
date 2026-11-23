package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.PromotionProgram;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.VoucherDistribution;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyErrorCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyException;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TargetScope;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Session 2026-11-22, FR-046: counters of a voucher that must be saved, where saving holds one use. */
class VoucherDistributionRulesTest {

    private static final Instant AT = Instant.parse("2026-11-22T00:00:00Z");

    private static PromotionProgram claimVoucher(long total) {
        return PromotionProgram.voucher(TargetScope.PRODUCT, "LUU1", "LUU1", total, 1,
                VoucherDistribution.CLAIM_REQUIRED, AT, 1L);
    }

    @Test
    void savingHoldsOneUseUntilNoneIsLeft() {
        PromotionProgram program = claimVoucher(2);
        program.claim();
        program.claim();
        assertThat(program.availableTotal()).isZero();
        assertThatThrownBy(program::claim).isInstanceOf(LoyaltyException.class)
                .extracting(e -> ((LoyaltyException) e).code()).isEqualTo(LoyaltyErrorCode.PROMOTION_INELIGIBLE);
    }

    @Test
    void usingTheSavedUseMovesItAndCancellingGivesItBackToTheWallet() {
        PromotionProgram program = claimVoucher(1);
        program.claim();
        program.hold();
        assertThat(program.getClaimedCount()).isZero();
        assertThat(program.getHeldCount()).isEqualTo(1L);
        assertThat(program.availableTotal()).as("the use is still taken").isZero();

        program.release();
        assertThat(program.getClaimedCount()).as("back in the wallet").isEqualTo(1L);
        assertThat(program.getHeldCount()).isZero();

        program.hold();
        program.consume();
        assertThat(program.getConsumedCount()).isEqualTo(1L);
        program.restore();
        assertThat(program.getClaimedCount()).as("a valid cancellation gives the use back to the wallet").isEqualTo(1L);
        assertThat(program.getConsumedCount()).isZero();
    }

    @Test
    void holdingWithoutASavedUseIsRefused() {
        PromotionProgram program = claimVoucher(5);
        assertThatThrownBy(program::hold).isInstanceOf(LoyaltyException.class);
    }

    @Test
    void aVoucherThatMustBeSavedAllowsOneUsePerMember() {
        assertThatThrownBy(() -> PromotionProgram.voucher(TargetScope.PRODUCT, "LUU2", "LUU2", 10, 2,
                VoucherDistribution.CLAIM_REQUIRED, AT, 1L)).isInstanceOf(LoyaltyException.class);
        PromotionProgram program = claimVoucher(10);
        assertThatThrownBy(() -> program.changeLimits(10, 2)).isInstanceOf(LoyaltyException.class);
    }

    @Test
    void theTotalCannotDropBelowTheSavedUses() {
        PromotionProgram program = claimVoucher(3);
        program.claim();
        program.claim();
        assertThatThrownBy(() -> program.changeLimits(1, 1)).isInstanceOf(LoyaltyException.class)
                .extracting(e -> ((LoyaltyException) e).code()).isEqualTo(LoyaltyErrorCode.QUOTA_LIMIT_CONFLICT);
    }

    @Test
    void aPublicVoucherKeepsItsOldCounters() {
        PromotionProgram program = PromotionProgram.voucher(TargetScope.PRODUCT, "PUB1", "PUB1", 1, 1, AT, 1L);
        assertThat(program.getDistribution()).isEqualTo(VoucherDistribution.PUBLIC);
        assertThatThrownBy(program::claim).isInstanceOf(LoyaltyException.class);
        program.hold();
        program.release();
        assertThat(program.getClaimedCount()).isZero();
        assertThat(program.availableTotal()).isEqualTo(1L);
    }
}
