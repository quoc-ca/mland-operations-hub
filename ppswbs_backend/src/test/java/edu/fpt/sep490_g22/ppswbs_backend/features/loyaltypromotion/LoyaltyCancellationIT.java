package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.AcceptedBenefitRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.LoyaltyAuditRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.LoyaltyRegistryRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.LoyaltyWalletRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.MemberProgramRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.PointLedgerRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.PromotionProgramRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.PromotionRevisionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.AcceptedBenefit;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.LedgerEntryKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.LoyaltyRegistry;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.LoyaltyWallet;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.MemberProgram;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.PointLedger;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.ProgramKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.PromotionProgram;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.PromotionRevision;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.AuthoritativeOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.ComponentState;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.EarnState;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyException;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.OutcomeKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.SourceKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TargetScope;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.LoyaltyCalculator.DiscountTerms;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.LoyaltyReleaseService;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.LoyaltyRestorationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.support.LoyaltyTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigInteger;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T021 on the external MySQL 8.4 test database: release and restore run with the real lock order, commit
 * state, counters, ledger and audit together, never apply twice, and leave nothing behind when the source owner's
 * transaction rolls back. Each test uses its own member, code and source reference so reruns never collide.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles(LoyaltyTestSupport.MYSQL_PROFILE)
class LoyaltyCancellationIT {

    private static final Instant ACCEPTED = Instant.parse("2026-10-27T01:00:00.123456Z");
    private static final Instant DECIDED = Instant.parse("2026-10-27T02:00:00.654321Z");

    @Autowired private TransactionTemplate transaction;
    @Autowired private LoyaltyReleaseService release;
    @Autowired private LoyaltyRestorationService restore;
    @Autowired private PromotionProgramRepository programs;
    @Autowired private PromotionRevisionRepository revisions;
    @Autowired private MemberProgramRepository memberPrograms;
    @Autowired private LoyaltyWalletRepository wallets;
    @Autowired private AcceptedBenefitRepository benefits;
    @Autowired private PointLedgerRepository ledger;
    @Autowired private LoyaltyRegistryRepository registry;
    @Autowired private LoyaltyAuditRepository audits;

    private record Fixture(long memberId, long programId, String sourceRef) {
    }

    private static BigInteger n(long value) {
        return BigInteger.valueOf(value);
    }

    /**
     * A committed workshop booking with 200 points and one voucher use, as accept and (optionally) the verified
     * deposit leave it. The wallet starts at 500 points.
     */
    private Fixture booking(boolean depositSettled) {
        long memberId = ThreadLocalRandom.current().nextLong(100_000_000L, 900_000_000L);
        String code = "IT" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        String sourceRef = "bk-" + UUID.randomUUID();
        long programId = transaction.execute(status -> {
            long policyId = registry.findById(LoyaltyRegistry.SINGLETON_ID).orElseThrow().getCurrentPolicyId();
            PromotionProgram program = programs.saveAndFlush(
                    PromotionProgram.voucher(TargetScope.WORKSHOP, code, code, 10, 2, ACCEPTED, 1L));
            PromotionRevision revision = revisions.saveAndFlush(PromotionRevision.create(program.getId(), 1,
                    ProgramKind.VOUCHER, "Workshop 100k", ACCEPTED.minusSeconds(3600), ACCEPTED.plusSeconds(86_400),
                    DiscountTerms.fixed(n(100_000), n(100_000), BigInteger.ZERO), true, ACCEPTED, 1L));
            program.pointToRevision(revision.getId());
            program.hold();
            MemberProgram counters = MemberProgram.create(program.getId(), memberId);
            counters.hold(2);
            memberPrograms.saveAndFlush(counters);
            LoyaltyWallet wallet = LoyaltyWallet.open(memberId);
            wallet.earn(n(500));
            wallet.hold(n(200));
            wallet = wallets.saveAndFlush(wallet); // assigned id: save merges and returns the managed copy
            AcceptedBenefit benefit = benefits.saveAndFlush(AcceptedBenefit.accept(new AcceptedBenefit.Snapshot(
                    SourceKind.WORKSHOP, sourceRef, memberId, ACCEPTED, ACCEPTED.plusSeconds(1800), 1L, "digest",
                    policyId, program.getId(), revision.getId(), n(1_000_000), n(100_000), n(200), n(20_000),
                    n(880_000), n(880_000), true), true));
            ledger.save(PointLedger.entry(LedgerEntryKind.HOLD, memberId, benefit.getId(), n(200), n(500), n(200),
                    ACCEPTED, null));
            if (depositSettled) {
                benefit.settle("deposit-" + sourceRef, 1, ACCEPTED);
                program.consume();
                counters.consume();
                wallet.consume(n(200));
                ledger.save(PointLedger.entry(LedgerEntryKind.CONSUME, memberId, benefit.getId(), n(200), n(300),
                        BigInteger.ZERO, ACCEPTED, null));
            }
            return program.getId();
        });
        return new Fixture(memberId, programId, sourceRef);
    }

    private static AuthoritativeOutcome outcome(Fixture fixture, OutcomeKind kind, String key, long version) {
        return new AuthoritativeOutcome(SourceKind.WORKSHOP, fixture.sourceRef(), "workshopbooking", key, version,
                kind, DECIDED, ACCEPTED.plusSeconds(1800), "snap", true, "proof");
    }

    private AcceptedBenefit benefitOf(Fixture fixture) {
        return benefits.findBySourceKindAndSourceRef(SourceKind.WORKSHOP, fixture.sourceRef()).orElseThrow();
    }

    @Test
    void validCancellationAfterDepositRestoresPointsAndBothCountersOnce() {
        Fixture fixture = booking(true);
        AuthoritativeOutcome cancel = outcome(fixture, OutcomeKind.VALID_WORKSHOP_CANCELLATION, "cancel-1", 2);

        transaction.executeWithoutResult(status -> restore.restoreCancelledWorkshopBenefits(cancel));
        transaction.executeWithoutResult(status -> restore.restoreCancelledWorkshopBenefits(cancel));

        LoyaltyWallet wallet = wallets.findById(fixture.memberId()).orElseThrow();
        assertThat(wallet.getBalancePoints()).isEqualTo(n(500));
        assertThat(wallet.getHeldPoints()).isZero();
        PromotionProgram program = programs.findById(fixture.programId()).orElseThrow();
        assertThat(program.getConsumedCount()).isZero();
        assertThat(program.getHeldCount()).isZero();
        MemberProgram counters = memberPrograms.findByProgramIdAndMemberId(fixture.programId(), fixture.memberId())
                .orElseThrow();
        assertThat(counters.getConsumedCount()).isZero();
        AcceptedBenefit benefit = benefitOf(fixture);
        assertThat(benefit.getPointsState()).isEqualTo(ComponentState.RESTORED);
        assertThat(benefit.getVoucherState()).isEqualTo(ComponentState.RESTORED);
        assertThat(benefit.getEarnState()).isEqualTo(EarnState.INELIGIBLE);
        assertThat(benefit.getVoucherRestoredAt()).isEqualTo(DECIDED);
        assertThat(benefit.getSettledAt()).as("history kept").isEqualTo(ACCEPTED);
        assertThat(ledger.findByAcceptedBenefitIdOrderByIdAsc(benefit.getId()))
                .extracting(PointLedger::getEntryKind)
                .containsExactly(LedgerEntryKind.HOLD, LedgerEntryKind.CONSUME, LedgerEntryKind.RESTORE);
        PointLedger restored = ledger.findByAcceptedBenefitIdAndEntryKind(benefit.getId(), LedgerEntryKind.RESTORE)
                .orElseThrow();
        PointLedger debit = ledger.findByAcceptedBenefitIdAndEntryKind(benefit.getId(), LedgerEntryKind.CONSUME)
                .orElseThrow();
        assertThat(restored.getOriginalDebitId()).isEqualTo(debit.getId());
        assertThat(audits.findByObjectRefOrderByIdAsc("benefit:" + benefit.getId()))
                .as("one audit row for the one real change").hasSize(1);
    }

    @Test
    void confirmedUnpaidExpiryReleasesHeldBenefitsWithoutExtraCredit() {
        Fixture fixture = booking(false);
        transaction.executeWithoutResult(status -> release.releaseHeldBenefits(
                outcome(fixture, OutcomeKind.UNPAID_EXPIRY_CONFIRMED, "expiry-1", 2)));

        LoyaltyWallet wallet = wallets.findById(fixture.memberId()).orElseThrow();
        assertThat(wallet.getBalancePoints()).isEqualTo(n(500));
        assertThat(wallet.getHeldPoints()).isZero();
        PromotionProgram program = programs.findById(fixture.programId()).orElseThrow();
        assertThat(program.getHeldCount()).isZero();
        assertThat(program.getConsumedCount()).isZero();
        AcceptedBenefit benefit = benefitOf(fixture);
        assertThat(benefit.getPointsState()).isEqualTo(ComponentState.RELEASED);
        assertThat(benefit.getVoucherState()).isEqualTo(ComponentState.RELEASED);
        assertThat(ledger.findByAcceptedBenefitIdAndEntryKind(benefit.getId(), LedgerEntryKind.RELEASE)).isPresent();
    }

    @Test
    void noShowNeverRefundsAndLeavesEverythingUnchanged() {
        Fixture fixture = booking(true);
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> restore.restoreCancelledWorkshopBenefits(
                outcome(fixture, OutcomeKind.WORKSHOP_NO_SHOW_FINAL, "noshow-1", 2))))
                .isInstanceOf(LoyaltyException.class);
        assertThat(wallets.findById(fixture.memberId()).orElseThrow().getBalancePoints()).isEqualTo(n(300));
        assertThat(programs.findById(fixture.programId()).orElseThrow().getConsumedCount()).isEqualTo(1);
        assertThat(benefitOf(fixture).getPointsState()).isEqualTo(ComponentState.CONSUMED);
    }

    @Test
    void aRolledBackSourceTransactionLeavesNoPartialRestore() {
        Fixture fixture = booking(true);
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            restore.restoreCancelledWorkshopBenefits(outcome(fixture, OutcomeKind.VALID_WORKSHOP_CANCELLATION,
                    "cancel-rollback", 2));
            throw new IllegalStateException("source owner failed after calling loyalty");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(wallets.findById(fixture.memberId()).orElseThrow().getBalancePoints()).isEqualTo(n(300));
        assertThat(programs.findById(fixture.programId()).orElseThrow().getConsumedCount()).isEqualTo(1);
        AcceptedBenefit benefit = benefitOf(fixture);
        assertThat(benefit.getPointsState()).isEqualTo(ComponentState.CONSUMED);
        assertThat(ledger.findByAcceptedBenefitIdAndEntryKind(benefit.getId(), LedgerEntryKind.RESTORE)).isEmpty();

        // The retry after the rollback completes the restore exactly once.
        transaction.executeWithoutResult(status -> restore.restoreCancelledWorkshopBenefits(
                outcome(fixture, OutcomeKind.VALID_WORKSHOP_CANCELLATION, "cancel-rollback", 2)));
        assertThat(wallets.findById(fixture.memberId()).orElseThrow().getBalancePoints()).isEqualTo(n(500));
    }

    @Test
    void effectsRefuseToRunOutsideTheSourceOwnersTransaction() {
        Fixture fixture = booking(true);
        assertThatThrownBy(() -> restore.restoreCancelledWorkshopBenefits(
                outcome(fixture, OutcomeKind.VALID_WORKSHOP_CANCELLATION, "cancel-no-tx", 2)))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(benefitOf(fixture).getPointsState()).isEqualTo(ComponentState.CONSUMED);
    }

    @Test
    void theRestoredAmountIsTheOriginalDebitNotARecomputedValue() {
        // US7.4: 200 points come back as 200 points, linked to the debit, whatever the current conversion rate.
        Fixture fixture = booking(true);
        transaction.executeWithoutResult(status -> restore.restoreCancelledWorkshopBenefits(
                outcome(fixture, OutcomeKind.VALID_WORKSHOP_CANCELLATION, "cancel-rate", 2)));
        AcceptedBenefit benefit = benefitOf(fixture);
        assertThat(ledger.findByAcceptedBenefitIdAndEntryKind(benefit.getId(), LedgerEntryKind.RESTORE).orElseThrow()
                .getDeltaBalance()).isEqualTo(n(200));
    }
}
