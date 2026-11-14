package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.AcceptedBenefitRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.LoyaltyPolicyRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.LoyaltyRegistryRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.LoyaltyWalletRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.PointLedgerRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.AcceptedBenefit;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.LedgerEntryKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.LoyaltyPolicy;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.LoyaltyRegistry;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.LoyaltyWallet;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.PointLedger;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.AuthoritativeOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.BenefitResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.ComponentState;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.EarnState;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyErrorCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyException;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.OutcomeKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.SourceKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.LoyaltyCalculator.PolicyTerms;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.LoyaltyEarnService;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.support.LoyaltyTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T035 on the MySQL 8.4 test database (FR-028-FR-030, US4.4, US5.5, US6.1-US6.3, US6.6, US6.7): points are granted
 * once, only from a verified owner outcome, on the amount actually paid, with the policy of acceptance; a workshop
 * earns only when completed and fully paid; nothing is credited while a benefit is still held; a zero result is
 * recorded without a ledger row; no-show or cancellation closes the earn without refunding anything.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles(LoyaltyTestSupport.MYSQL_PROFILE)
class LoyaltyEarnIT {

    private static final Instant ACCEPTED = Instant.parse("2026-10-27T01:00:00.123456Z");
    private static final Instant DECIDED = Instant.parse("2026-10-27T03:00:00Z");

    @Autowired private TransactionTemplate transaction;
    @Autowired private LoyaltyEarnService earn;
    @Autowired private AcceptedBenefitRepository benefits;
    @Autowired private LoyaltyWalletRepository wallets;
    @Autowired private PointLedgerRepository ledger;
    @Autowired private LoyaltyRegistryRepository registry;
    @Autowired private LoyaltyPolicyRepository policies;

    private record Fixture(long memberId, String sourceRef, SourceKind kind) {
    }

    private static BigInteger n(long value) {
        return BigInteger.valueOf(value);
    }

    private long currentPolicyId() {
        return registry.findById(LoyaltyRegistry.SINGLETON_ID).orElseThrow().getCurrentPolicyId();
    }

    /**
     * An accepted Member transaction with {@code points} used (wallet starts at 1000) and the given earn base,
     * optionally settled by the owner's verified payment or deposit.
     */
    private Fixture accepted(SourceKind kind, long points, long earnBase, boolean settled, long policyId) {
        long memberId = ThreadLocalRandom.current().nextLong(100_000_000L, 900_000_000L);
        String sourceRef = kind.name().toLowerCase() + "-" + UUID.randomUUID();
        transaction.executeWithoutResult(status -> {
            LoyaltyWallet wallet = LoyaltyWallet.open(memberId);
            wallet.earn(n(1_000));
            if (points > 0) {
                wallet.hold(n(points));
            }
            wallet = wallets.saveAndFlush(wallet);
            AcceptedBenefit benefit = benefits.saveAndFlush(AcceptedBenefit.accept(new AcceptedBenefit.Snapshot(kind,
                    sourceRef, memberId, ACCEPTED, ACCEPTED.plusSeconds(1800), 1L, "digest", policyId, null, null,
                    n(earnBase + points * 100), BigInteger.ZERO, n(points), n(points * 100), n(earnBase),
                    kind == SourceKind.WORKSHOP ? n(earnBase) : null, false), true));
            if (settled) {
                benefit.settle("paid-" + sourceRef, 1, ACCEPTED);
                if (points > 0) {
                    wallet.consume(n(points));
                }
            }
        });
        return new Fixture(memberId, sourceRef, kind);
    }

    private static AuthoritativeOutcome outcome(Fixture fixture, OutcomeKind kind, String key, long version) {
        return new AuthoritativeOutcome(fixture.kind(), fixture.sourceRef(),
                fixture.kind() == SourceKind.WORKSHOP ? "workshopbooking" : "readyringsales", key, version, kind,
                DECIDED, ACCEPTED.plusSeconds(1800), "snap", true, "proof");
    }

    private BenefitResult grant(AuthoritativeOutcome outcome) {
        return transaction.execute(status -> earn.grantEarnedPoints(outcome));
    }

    private BigInteger balance(Fixture fixture) {
        return wallets.findById(fixture.memberId()).orElseThrow().getBalancePoints();
    }

    private AcceptedBenefit benefit(Fixture fixture) {
        return benefits.findBySourceKindAndSourceRef(fixture.kind(), fixture.sourceRef()).orElseThrow();
    }

    private static void assertCode(Runnable action, LoyaltyErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(LoyaltyException.class,
                e -> assertThat(e.code()).isEqualTo(code));
    }

    @Test
    void aFullyPaidRetailOrderEarnsOnTheAmountPaidOnce() {
        // US4.4: 750.000 after voucher, 200 points used: earn base 730.000 -> 73 points (shipping excluded).
        Fixture fixture = accepted(SourceKind.RETAIL, 200, 730_000, true, currentPolicyId());
        AuthoritativeOutcome paid = outcome(fixture, OutcomeKind.RETAIL_FULLY_PAID, "paid-1", 2);

        BenefitResult result = grant(paid);
        grant(paid);

        assertThat(result.earnState()).isEqualTo(EarnState.GRANTED);
        assertThat(balance(fixture)).as("1000 - 200 used + 73 earned, once").isEqualTo(n(873));
        AcceptedBenefit benefit = benefit(fixture);
        assertThat(benefit.getEarnedPoints()).isEqualTo(n(73));
        PointLedger entry = ledger.findByAcceptedBenefitIdAndEntryKind(benefit.getId(), LedgerEntryKind.EARN).orElseThrow();
        assertThat(entry.getDeltaBalance()).isEqualTo(n(73));
        assertCode(() -> grant(outcome(fixture, OutcomeKind.RETAIL_FULLY_PAID, "paid-2", 3)),
                LoyaltyErrorCode.BENEFIT_STATE_CONFLICT);
        assertThat(balance(fixture)).isEqualTo(n(873));
    }

    @Test
    void aZeroEarnIsRecordedWithoutALedgerRow() {
        Fixture fixture = accepted(SourceKind.RETAIL, 0, 9_999, true, currentPolicyId());
        BenefitResult result = grant(outcome(fixture, OutcomeKind.RETAIL_ZERO_ACCEPTED, "zero-1", 2));
        assertThat(result.earnState()).isEqualTo(EarnState.GRANTED);
        AcceptedBenefit benefit = benefit(fixture);
        assertThat(benefit.getEarnedPoints()).isZero();
        assertThat(ledger.findByAcceptedBenefitIdAndEntryKind(benefit.getId(), LedgerEntryKind.EARN)).isEmpty();
        assertThat(balance(fixture)).isEqualTo(n(1_000));
    }

    @Test
    void aWorkshopEarnsOnlyWhenCompletedAndFullyPaid() {
        // US5.5: post-promotion fee 880.000 -> 88 points, once, not at deposit.
        Fixture fixture = accepted(SourceKind.WORKSHOP, 200, 880_000, true, currentPolicyId());
        assertCode(() -> grant(outcome(fixture, OutcomeKind.WORKSHOP_DEPOSIT_ACCEPTED, "deposit-1", 2)),
                LoyaltyErrorCode.BENEFIT_STATE_CONFLICT);
        assertThat(benefit(fixture).getEarnState()).isEqualTo(EarnState.PENDING);

        grant(outcome(fixture, OutcomeKind.WORKSHOP_COMPLETED_AND_FULLY_PAID, "done-1", 3));
        assertThat(balance(fixture)).isEqualTo(n(1_000 - 200 + 88));
        assertThat(benefit(fixture).getEarnedPoints()).isEqualTo(n(88));
    }

    @Test
    void nothingIsCreditedWhileAPointDebitIsStillHeld() {
        Fixture fixture = accepted(SourceKind.RETAIL, 200, 730_000, false, currentPolicyId());
        assertCode(() -> grant(outcome(fixture, OutcomeKind.RETAIL_FULLY_PAID, "paid-1", 2)),
                LoyaltyErrorCode.BENEFIT_STATE_CONFLICT);
        assertThat(balance(fixture)).isEqualTo(n(1_000));
        assertThat(benefit(fixture).getEarnState()).isEqualTo(EarnState.PENDING);
    }

    @Test
    void theEarnRateIsThePolicyOfAcceptanceNotTheCurrentOne() {
        // US8.4: a transaction accepted under 20.000đ per point keeps that rate after the current policy differs.
        long oldPolicyId = transaction.execute(status -> policies.saveAndFlush(LoyaltyPolicy.create(
                policies.findMaxRevisionNo() + 1, new PolicyTerms(n(20_000), n(100), n(10), BigDecimal.TEN),
                ACCEPTED, null)).getId());
        assertThat(oldPolicyId).isNotEqualTo(currentPolicyId());
        Fixture fixture = accepted(SourceKind.WORKSHOP, 0, 880_000, true, oldPolicyId);
        grant(outcome(fixture, OutcomeKind.WORKSHOP_COMPLETED_AND_FULLY_PAID, "done-1", 2));
        assertThat(benefit(fixture).getEarnedPoints()).isEqualTo(n(44));
        assertThat(balance(fixture)).isEqualTo(n(1_044));
    }

    @Test
    void noShowOrCancellationClosesTheEarnWithoutRefunding() {
        Fixture noShow = accepted(SourceKind.WORKSHOP, 200, 880_000, true, currentPolicyId());
        transaction.executeWithoutResult(status -> earn.finishIneligibleEarn(
                outcome(noShow, OutcomeKind.WORKSHOP_NO_SHOW_FINAL, "noshow-1", 2)));
        AcceptedBenefit closed = benefit(noShow);
        assertThat(closed.getEarnState()).isEqualTo(EarnState.INELIGIBLE);
        assertThat(closed.getPointsState()).as("no automatic refund").isEqualTo(ComponentState.CONSUMED);
        assertThat(balance(noShow)).isEqualTo(n(800));
        assertCode(() -> grant(outcome(noShow, OutcomeKind.WORKSHOP_COMPLETED_AND_FULLY_PAID, "done-1", 3)),
                LoyaltyErrorCode.BENEFIT_STATE_CONFLICT);

        Fixture expired = accepted(SourceKind.RETAIL, 0, 100_000, false, currentPolicyId());
        transaction.executeWithoutResult(status -> earn.finishIneligibleEarn(
                outcome(expired, OutcomeKind.UNPAID_EXPIRY_CONFIRMED, "exp-1", 2)));
        assertThat(benefit(expired).getEarnState()).isEqualTo(EarnState.INELIGIBLE);
    }

    @Test
    void aGrantOutcomeCannotCloseTheEarnAndAClosingOutcomeCannotGrant() {
        Fixture fixture = accepted(SourceKind.RETAIL, 0, 100_000, true, currentPolicyId());
        assertCode(() -> grant(outcome(fixture, OutcomeKind.UNPAID_EXPIRY_CONFIRMED, "exp-1", 2)),
                LoyaltyErrorCode.BENEFIT_STATE_CONFLICT);
        assertCode(() -> transaction.executeWithoutResult(status -> earn.finishIneligibleEarn(
                outcome(fixture, OutcomeKind.RETAIL_FULLY_PAID, "paid-1", 2))), LoyaltyErrorCode.BENEFIT_STATE_CONFLICT);
        assertThat(benefit(fixture).getEarnState()).isEqualTo(EarnState.PENDING);
    }

    @Test
    void earningRefusesToRunOutsideTheOwnersTransaction() {
        Fixture fixture = accepted(SourceKind.RETAIL, 0, 100_000, true, currentPolicyId());
        assertThatThrownBy(() -> earn.grantEarnedPoints(outcome(fixture, OutcomeKind.RETAIL_FULLY_PAID, "paid-1", 2)))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(benefit(fixture).getEarnState()).isEqualTo(EarnState.PENDING);
    }

    @Test
    void anUnknownSourceIsAConflictAndCreditsNothing() {
        Fixture unknown = new Fixture(123L, "retail-" + UUID.randomUUID(), SourceKind.RETAIL);
        assertCode(() -> grant(outcome(unknown, OutcomeKind.RETAIL_FULLY_PAID, "paid-1", 2)),
                LoyaltyErrorCode.BENEFIT_STATE_CONFLICT);
        assertThat(wallets.findById(123L)).isEmpty();
    }
}
