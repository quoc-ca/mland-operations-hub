package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.LoyaltyWalletRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.PointLedgerRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.PromotionProgramRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.PromotionRevisionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.PromotionTargetRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.LedgerEntryKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.LoyaltyWallet;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.PointLedger;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.ProgramKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.PromotionProgram;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.PromotionRevision;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.PromotionTarget;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.AcceptedBenefitRef;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.AcceptedSourceContext;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.AuthoritativeOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.BenefitPartRef;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.BenefitPreview;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.BenefitResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.BenefitSelection;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.ComponentState;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.EarnState;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyErrorCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyException;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.OutcomeKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.SelectionChoice;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.SettlementPart;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.SourceKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.SourceLine;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TargetDimension;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TargetScope;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TrustedSourceContext;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.LoyaltyCalculator.DiscountTerms;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.support.LoyaltyTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Session 2026-11-12 (D-011) on MySQL 8.4 through the public {@link LoyaltyFacade}: a retail checkout split into
 * branch orders that FE-08 settles one by one (cash at the counter, 72-hour hold). The test plays FE-08: it accepts
 * the checkout with {@link SettlementPart}s and sends {@code RETAIL_BRANCH_*} outcomes with the branch order as
 * {@code partRef}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles(LoyaltyTestSupport.MYSQL_PROFILE)
class LoyaltyBranchSettlementFacadeIT {

    private static final long PRODUCT = 8_301L;
    private static final long SIZE = 83_011L;
    private static final long OTHER_PRODUCT = 8_302L;
    private static final long OTHER_SIZE = 83_021L;

    @Autowired private LoyaltyFacade loyalty;
    @Autowired private TransactionTemplate transaction;
    @Autowired private DataSource dataSource;
    @Autowired private PromotionProgramRepository programs;
    @Autowired private PromotionRevisionRepository revisions;
    @Autowired private PromotionTargetRepository targets;
    @Autowired private LoyaltyWalletRepository wallets;
    @Autowired private PointLedgerRepository ledger;

    @BeforeEach
    void requireMySql() {
        LoyaltyTestSupport.requireMySql84(dataSource);
    }

    /** Branch A holds the voucher product (1.000.000), branch B another product (600.000); 10% voucher, 100 points. */
    private record Accepted(long member, String code, String ref, AcceptedBenefitRef held) {
        BenefitPartRef part(String partRef) {
            return held.parts().stream().filter(p -> p.partRef().equals(partRef)).findFirst().orElseThrow();
        }
    }

    private Accepted acceptTwoBranches() {
        String code = voucher();
        long member = member(1_000);
        TrustedSourceContext cart = cart(member, line(PRODUCT, SIZE, 1_000_000, 1L),
                line(OTHER_PRODUCT, OTHER_SIZE, 600_000, 2L));
        BenefitSelection selection = new BenefitSelection(SelectionChoice.VOUCHER, code, n(100));
        BenefitPreview preview = loyalty.previewBenefits(cart, selection);
        assertThat(preview.programDiscount()).isEqualTo(n(100_000));
        assertThat(preview.eligibleLineIndexes()).containsExactly(0);
        String ref = "cash-it-" + UUID.randomUUID();
        Instant now = Instant.now();
        AcceptedSourceContext context = new AcceptedSourceContext(cart, ref, 1L, true, now,
                now.plus(72, ChronoUnit.HOURS), "quote-" + ref, preview.termsDigest(),
                List.of(new SettlementPart("A", List.of(0)), new SettlementPart("B", List.of(1))));
        AcceptedBenefitRef held = transaction.execute(status ->
                loyalty.reserveAcceptedTransaction(context, selection, "cmd-" + ref));
        return new Accepted(member, code, ref, held);
    }

    @Test
    void acceptSplitsTheBenefitPerBranchOrderAndAcceptsA72HourDeadline() {
        Accepted accepted = acceptTwoBranches();
        AcceptedBenefitRef held = accepted.held();
        assertThat(held.parts()).extracting(BenefitPartRef::partRef).containsExactly("A", "B");
        BenefitPartRef a = accepted.part("A");
        BenefitPartRef b = accepted.part("B");
        assertThat(a.programDiscount()).as("only A has voucher lines").isEqualTo(n(100_000));
        assertThat(b.programDiscount()).isZero();
        assertThat(sum(held.parts(), BenefitPartRef::grossAmount)).isEqualTo(held.grossAmount());
        assertThat(sum(held.parts(), BenefitPartRef::pointsHeld)).isEqualTo(n(100));
        assertThat(sum(held.parts(), BenefitPartRef::pointDiscount)).isEqualTo(held.pointDiscount());
        assertThat(sum(held.parts(), BenefitPartRef::earnBase)).isEqualTo(held.earnBase());
        // Points split by the amount after the voucher: A 900.000, B 600.000 -> 60 / 40.
        assertThat(a.pointsHeld()).isEqualTo(n(60));
        assertThat(b.pointsHeld()).isEqualTo(n(40));
        assertThat(held.parts()).allMatch(p -> p.pointsState() == ComponentState.HELD
                && p.earnState() == EarnState.PENDING);
        assertThat(held.voucherState()).isEqualTo(ComponentState.HELD);
    }

    @Test
    void aBranchWithoutVoucherShareIsPaidThenTheVoucherBranchCancelledGivesTheVoucherBack() {
        Accepted accepted = acceptTwoBranches();
        BigInteger earnB = accepted.part("B").earnPoints();

        BenefitResult paidB = transaction.execute(status -> {
            loyalty.settleAcceptedBenefits(outcome(accepted, OutcomeKind.RETAIL_BRANCH_PAID, "B", "paid-B"));
            return loyalty.grantEarnedPoints(outcome(accepted, OutcomeKind.RETAIL_BRANCH_PAID, "B", "paid-B"));
        });
        assertThat(paidB.voucherState()).as("B carries no voucher share: the use stays held")
                .isEqualTo(ComponentState.HELD);
        assertThat(part(paidB, "B").pointsState()).isEqualTo(ComponentState.CONSUMED);
        assertThat(part(paidB, "B").earnState()).isEqualTo(EarnState.GRANTED);
        assertThat(part(paidB, "A").pointsState()).isEqualTo(ComponentState.HELD);
        assertThat(wallet(accepted.member()).getBalancePoints()).isEqualTo(n(1_000 - 40).add(earnB));
        assertThat(wallet(accepted.member()).getHeldPoints()).isEqualTo(n(60));

        BenefitResult cancelledA = transaction.execute(status -> loyalty.releaseHeldBenefits(
                outcome(accepted, OutcomeKind.RETAIL_BRANCH_CANCELLED_BEFORE_PAYMENT, "A", "cancel-A")));
        assertThat(cancelledA.voucherState()).as("every voucher branch ended unpaid")
                .isEqualTo(ComponentState.RELEASED);
        assertThat(part(cancelledA, "A").pointsState()).isEqualTo(ComponentState.RELEASED);
        assertThat(part(cancelledA, "A").earnState()).isEqualTo(EarnState.INELIGIBLE);
        assertThat(cancelledA.pointsState()).isEqualTo(ComponentState.CONSUMED);
        assertThat(cancelledA.earnState()).isEqualTo(EarnState.GRANTED);
        assertThat(wallet(accepted.member()).getHeldPoints()).isZero();
        assertThat(wallet(accepted.member()).getBalancePoints()).isEqualTo(n(960).add(earnB));
        PromotionProgram program = program(accepted.code());
        assertThat(program.getHeldCount()).isZero();
        assertThat(program.getConsumedCount()).isZero();
    }

    @Test
    void theVoucherBranchPaidConsumesTheVoucherAndAnExpiredBranchGivesOnlyItsPointsBack() {
        Accepted accepted = acceptTwoBranches();

        BenefitResult paidA = transaction.execute(status ->
                loyalty.settleAcceptedBenefits(outcome(accepted, OutcomeKind.RETAIL_BRANCH_PAID, "A", "paid-A")));
        assertThat(paidA.voucherState()).isEqualTo(ComponentState.CONSUMED);
        assertThat(program(accepted.code()).getConsumedCount()).isEqualTo(1);

        BenefitResult expiredB = transaction.execute(status -> loyalty.releaseHeldBenefits(
                outcome(accepted, OutcomeKind.RETAIL_BRANCH_UNPAID_EXPIRY, "B", "expire-B")));
        assertThat(expiredB.voucherState()).as("a consumed voucher is never given back here")
                .isEqualTo(ComponentState.CONSUMED);
        assertThat(part(expiredB, "B").pointsState()).isEqualTo(ComponentState.RELEASED);
        assertThat(wallet(accepted.member()).getBalancePoints()).isEqualTo(n(940));
        assertThat(wallet(accepted.member()).getHeldPoints()).isZero();
    }

    @Test
    void partOutcomesReplayAndConflictLikeWholeOutcomes() {
        Accepted accepted = acceptTwoBranches();
        AuthoritativeOutcome paidA = outcome(accepted, OutcomeKind.RETAIL_BRANCH_PAID, "A", "paid-A");
        transaction.executeWithoutResult(status -> loyalty.settleAcceptedBenefits(paidA));
        transaction.executeWithoutResult(status -> loyalty.settleAcceptedBenefits(paidA));
        assertThat(wallet(accepted.member()).getBalancePoints()).as("the replay debits nothing more")
                .isEqualTo(n(940));

        assertCode(() -> transaction.executeWithoutResult(status -> loyalty.releaseHeldBenefits(
                outcome(accepted, OutcomeKind.RETAIL_BRANCH_CANCELLED_BEFORE_PAYMENT, "A", "cancel-A"))),
                LoyaltyErrorCode.BENEFIT_STATE_CONFLICT);
        assertCode(() -> transaction.executeWithoutResult(status -> loyalty.settleAcceptedBenefits(
                outcome(accepted, OutcomeKind.RETAIL_BRANCH_PAID, "C", "paid-C"))),
                LoyaltyErrorCode.BENEFIT_STATE_CONFLICT);
        assertCode(() -> transaction.executeWithoutResult(status -> loyalty.settleAcceptedBenefits(
                wholeOutcome(accepted, OutcomeKind.RETAIL_FULLY_PAID, "paid-all"))),
                LoyaltyErrorCode.BENEFIT_STATE_CONFLICT);
        // Part ledger rows sit beside the whole-transaction HOLD.
        List<PointLedger> rows = ledger.findByAcceptedBenefitIdOrderByIdAsc(accepted.held().acceptedBenefitId());
        assertThat(rows).extracting(PointLedger::getEntryKind, PointLedger::getPartSeq)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(LedgerEntryKind.HOLD, 0),
                        org.assertj.core.groups.Tuple.tuple(LedgerEntryKind.CONSUME, 1));
    }

    @Test
    void aWholeOutcomeStillSettlesEveryBranchOrderAtOnce() {
        Accepted accepted = acceptTwoBranches();
        AuthoritativeOutcome paid = wholeOutcome(accepted, OutcomeKind.RETAIL_FULLY_PAID, "vnpay-paid");
        BenefitResult result = transaction.execute(status -> {
            loyalty.settleAcceptedBenefits(paid);
            return loyalty.grantEarnedPoints(paid);
        });
        assertThat(result.voucherState()).isEqualTo(ComponentState.CONSUMED);
        assertThat(result.parts()).allMatch(p -> p.pointsState() == ComponentState.CONSUMED
                && p.earnState() == EarnState.GRANTED);
        assertCode(() -> transaction.executeWithoutResult(status -> loyalty.releaseHeldBenefits(
                outcome(accepted, OutcomeKind.RETAIL_BRANCH_UNPAID_EXPIRY, "B", "expire-B"))),
                LoyaltyErrorCode.BENEFIT_STATE_CONFLICT);
        assertThat(loyalty.findBenefitResult(SourceKind.RETAIL, accepted.ref()).parts()).hasSize(2);
    }

    // ---- helpers ----

    private static BigInteger n(long value) {
        return BigInteger.valueOf(value);
    }

    private static BigInteger sum(List<BenefitPartRef> parts, Function<BenefitPartRef, BigInteger> field) {
        return parts.stream().map(field).reduce(BigInteger.ZERO, BigInteger::add);
    }

    private static BenefitPartRef part(BenefitResult result, String partRef) {
        return result.parts().stream().filter(p -> p.partRef().equals(partRef)).findFirst().orElseThrow();
    }

    private static void assertCode(Runnable action, LoyaltyErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(LoyaltyException.class,
                e -> assertThat(e.code()).isEqualTo(code));
    }

    private static SourceLine line(long product, long size, long unit, Long branch) {
        return new SourceLine(product, size, 1, n(unit), branch);
    }

    private static TrustedSourceContext cart(long member, SourceLine... lines) {
        return new TrustedSourceContext(SourceKind.RETAIL, member, Instant.now(), List.of(lines), null,
                BigInteger.ZERO);
    }

    private static AuthoritativeOutcome outcome(Accepted accepted, OutcomeKind kind, String partRef, String key) {
        Instant now = Instant.now();
        return new AuthoritativeOutcome(SourceKind.RETAIL, accepted.ref(), "retailorder", key + "-" + accepted.ref(),
                1, kind, now, now.plus(72, ChronoUnit.HOURS), "snap", true, "proof", partRef);
    }

    private static AuthoritativeOutcome wholeOutcome(Accepted accepted, OutcomeKind kind, String key) {
        Instant now = Instant.now();
        return new AuthoritativeOutcome(SourceKind.RETAIL, accepted.ref(), "retailorder", key + "-" + accepted.ref(),
                1, kind, now, now.plus(72, ChronoUnit.HOURS), "snap", true, "proof");
    }

    private long member(long points) {
        long memberId = ThreadLocalRandom.current().nextLong(100_000_000L, 900_000_000L);
        transaction.executeWithoutResult(status -> {
            LoyaltyWallet wallet = LoyaltyWallet.open(memberId);
            wallet.earn(n(points));
            wallets.saveAndFlush(wallet);
        });
        return memberId;
    }

    private LoyaltyWallet wallet(long member) {
        return transaction.execute(status -> wallets.findById(member).orElseThrow());
    }

    /** An active product voucher on {@link #PRODUCT}: 10%, cap 1.000.000, no minimum, every branch. */
    private String voucher() {
        String code = "BS" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        transaction.executeWithoutResult(status -> {
            PromotionProgram program = programs.saveAndFlush(
                    PromotionProgram.voucher(TargetScope.PRODUCT, code, code, 10, 5, now, 1L));
            PromotionRevision revision = revisions.saveAndFlush(PromotionRevision.create(program.getId(), 1,
                    ProgramKind.VOUCHER, "Branch settlement IT", now.minus(1, ChronoUnit.DAYS),
                    now.plus(1, ChronoUnit.DAYS),
                    DiscountTerms.percent(new BigDecimal("10"), n(1_000_000), BigInteger.ZERO), true, now, 1L));
            targets.save(PromotionTarget.create(revision.getId(), TargetScope.PRODUCT, TargetDimension.PRODUCT,
                    Long.toString(PRODUCT)));
            program.pointToRevision(revision.getId());
        });
        return code;
    }

    private PromotionProgram program(String code) {
        return transaction.execute(status -> programs.findByCodeKey(code).orElseThrow());
    }
}
