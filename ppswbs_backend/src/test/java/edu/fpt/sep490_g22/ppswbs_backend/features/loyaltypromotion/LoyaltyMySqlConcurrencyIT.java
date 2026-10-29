package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.AcceptedBenefitRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.LoyaltyPolicyRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.LoyaltyRegistryRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.LoyaltyWalletRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.PointLedgerRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.PromotionProgramRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.PromotionRevisionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.PromotionTargetRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dto.PromotionWriteDtos.UpdateProgram;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.AcceptedBenefit;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.LedgerEntryKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.LoyaltyPolicy;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.LoyaltyRegistry;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.LoyaltyWallet;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.PointLedger;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.ProgramKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.PromotionProgram;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.PromotionRevision;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.PromotionTarget;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.AcceptedBenefitRef;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.AcceptedSourceContext;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.AuthoritativeOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.BenefitSelection;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.ComponentState;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyErrorCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyException;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.OutcomeKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.SelectionChoice;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.SourceKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.SourceLine;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TargetDimension;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TargetScope;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TrustedSourceContext;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.WorkshopFact;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.WorkshopSessionFact;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.LoyaltyCalculator.DiscountTerms;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.LoyaltyCalculator.PolicyTerms;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.LoyaltyPolicyService;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.LoyaltyPolicyService.PolicyUpdate;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.PromotionMaintenanceService;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.support.LoyaltyTestSupport;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.support.LoyaltyTestSupport.Outcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T063 (loyalty side only): real MySQL 8.4 transactions started together on a barrier, with this test acting as the
 * source owners. It covers the races that loyalty itself must serialize: retail and workshop on the same wallet,
 * Manager quota edit, pause and policy update against an accept, the first wallet insert, a duplicated accept or
 * outcome, and a paid outcome against an expiry. FE-03 and FE-08 are not on this branch, so the source-side
 * arbitration of paid-vs-expiry/cancel/completion is NOT covered here (G-03 to G-05 stay open). FR-009, FR-010,
 * FR-027, FR-032, FR-038.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles(LoyaltyTestSupport.MYSQL_PROFILE)
class LoyaltyMySqlConcurrencyIT {

    private static final long IN_SCOPE_PRODUCT = 8_001L;
    private static final String MANAGER_REF = Long.toString(LoyaltyTestSupport.MANAGER_ACCOUNT_ID);
    private static final LocalDate DAY = LocalDate.parse("2026-12-05");

    @Autowired private LoyaltyFacade loyalty;
    @Autowired private TransactionTemplate transaction;
    @Autowired private DataSource dataSource;
    @Autowired private PromotionProgramRepository programs;
    @Autowired private PromotionRevisionRepository revisions;
    @Autowired private PromotionTargetRepository targets;
    @Autowired private LoyaltyWalletRepository wallets;
    @Autowired private AcceptedBenefitRepository benefits;
    @Autowired private PointLedgerRepository ledger;
    @Autowired private LoyaltyPolicyRepository policies;
    @Autowired private LoyaltyRegistryRepository registry;
    @Autowired private LoyaltyPolicyService policyService;
    @Autowired private PromotionMaintenanceService maintenance;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void requireMySql() {
        LoyaltyTestSupport.requireMySql84(dataSource);
    }

    private static BigInteger n(long value) {
        return BigInteger.valueOf(value);
    }

    private static long newMember() {
        return ThreadLocalRandom.current().nextLong(100_000_000L, 900_000_000L);
    }

    /** An active product voucher without a minimum: fixed 50.000 on product 8001. Returns the code. */
    private String voucher(long totalLimit, long memberLimit) {
        String code = "CC" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        transaction.executeWithoutResult(status -> {
            PromotionProgram program = programs.saveAndFlush(
                    PromotionProgram.voucher(TargetScope.PRODUCT, code, code, totalLimit, memberLimit, now, 1L));
            PromotionRevision revision = revisions.saveAndFlush(PromotionRevision.create(program.getId(), 1,
                    ProgramKind.VOUCHER, "Concurrency IT", now.minus(1, ChronoUnit.DAYS), now.plus(1, ChronoUnit.DAYS),
                    DiscountTerms.fixed(n(50_000), n(50_000), BigInteger.ZERO), true, now, 1L));
            targets.save(PromotionTarget.create(revision.getId(), TargetScope.PRODUCT, TargetDimension.PRODUCT,
                    Long.toString(IN_SCOPE_PRODUCT)));
            program.pointToRevision(revision.getId());
        });
        return code;
    }

    private void wallet(long memberId, long balance) {
        transaction.executeWithoutResult(status -> {
            LoyaltyWallet wallet = LoyaltyWallet.open(memberId);
            if (balance > 0) {
                wallet.earn(n(balance));
            }
            wallets.saveAndFlush(wallet);
        });
    }

    /** 800.000 in scope, no shipping. */
    private static TrustedSourceContext cart(long memberId) {
        return new TrustedSourceContext(SourceKind.RETAIL, memberId, Instant.now(),
                List.of(new SourceLine(IN_SCOPE_PRODUCT, 80_011L, 1, n(800_000))), null, BigInteger.ZERO);
    }

    private static TrustedSourceContext booking(long memberId) {
        return new TrustedSourceContext(SourceKind.WORKSHOP, memberId, Instant.now(), List.of(),
                WorkshopFact.singleSession(8_100L, 1, n(1_000_000),
                        WorkshopSessionFact.of("00000000-0000-4000-8000-000000000003", 2L, DAY)), BigInteger.ZERO);
    }

    private static AcceptedSourceContext accept(TrustedSourceContext context, String sourceRef, String digest) {
        Instant now = Instant.now();
        return new AcceptedSourceContext(context, sourceRef, 1L, true, now, now.plusSeconds(900),
                "quote-" + sourceRef, digest);
    }

    private static AuthoritativeOutcome outcome(SourceKind kind, String sourceRef, OutcomeKind outcomeKind,
                                                String key, long version) {
        Instant now = Instant.now();
        return new AuthoritativeOutcome(kind, sourceRef, kind == SourceKind.RETAIL ? "readyringsales" : "workshopbooking",
                key, version, outcomeKind, now, now.plusSeconds(900), "snap", true, "proof");
    }

    private String digest(TrustedSourceContext context, BenefitSelection selection) {
        return loyalty.previewBenefits(context, selection).termsDigest();
    }

    private AcceptedBenefitRef reserve(AcceptedSourceContext context, BenefitSelection selection) {
        return transaction.execute(status ->
                loyalty.reserveAcceptedTransaction(context, selection, "cmd-" + context.sourceRef()));
    }

    private PromotionProgram program(String code) {
        return programs.findByCodeKey(code).orElseThrow();
    }

    private static <T> long successes(List<Outcome<T>> results) {
        return results.stream().filter(Outcome::succeeded).count();
    }

    /** Every failure of a race must be a typed loyalty error, never a raw database or framework exception. */
    private static <T> void failuresAreTyped(List<Outcome<T>> results) {
        assertThat(results.stream().filter(result -> !result.succeeded()))
                .allSatisfy(result -> assertThat(result.failure()).isInstanceOf(LoyaltyException.class));
    }

    @Test
    void retailAndWorkshopCannotSpendTheSamePointsTwice() {
        long member = newMember();
        wallet(member, 200);
        BenefitSelection points = new BenefitSelection(SelectionChoice.NONE, null, n(200));
        String retailDigest = digest(cart(member), points);
        String workshopDigest = digest(booking(member), points);

        List<Outcome<AcceptedBenefitRef>> results = LoyaltyTestSupport.concurrently(
                () -> reserve(accept(cart(member), "order-" + UUID.randomUUID(), retailDigest), points),
                () -> reserve(accept(booking(member), "booking-" + UUID.randomUUID(), workshopDigest), points));

        assertThat(successes(results)).isEqualTo(1);
        failuresAreTyped(results);
        LoyaltyWallet wallet = wallets.findById(member).orElseThrow();
        assertThat(wallet.getHeldPoints()).isEqualTo(n(200));
        assertThat(wallet.getBalancePoints()).isEqualTo(n(200));
    }

    @Test
    void aQuotaEditRacingAnAcceptNeverLeavesMoreUsesThanTheLimit() {
        String code = voucher(2, 2);
        BenefitSelection selection = new BenefitSelection(SelectionChoice.VOUCHER, code, BigInteger.ZERO);
        long first = newMember();
        reserve(accept(cart(first), "order-" + UUID.randomUUID(), digest(cart(first), selection)), selection);
        long second = newMember();
        String secondDigest = digest(cart(second), selection);
        PromotionProgram before = program(code);

        List<Outcome<Object>> results = LoyaltyTestSupport.concurrently(
                () -> {
                    maintenance.update(MANAGER_REF, UUID.randomUUID(), before.getId(), new UpdateProgram(
                            before.getConfigVersion(), null, null, null, null, null, null, 1L, 1L));
                    return "edited";
                },
                () -> reserve(accept(cart(second), "order-" + UUID.randomUUID(), secondDigest), selection));

        assertThat(successes(results)).as("either the edit or the second hold wins, never both").isEqualTo(1);
        failuresAreTyped(results);
        PromotionProgram after = program(code);
        assertThat(after.getHeldCount() + after.getConsumedCount()).isLessThanOrEqualTo(after.getTotalLimit());
        if (results.get(0).succeeded()) {
            assertThat(after.getTotalLimit()).isEqualTo(1);
            assertThat(after.getHeldCount()).isEqualTo(1);
        } else {
            assertThat(((LoyaltyException) results.get(0).failure()).code())
                    .isEqualTo(LoyaltyErrorCode.QUOTA_LIMIT_CONFLICT);
            assertThat(after.getTotalLimit()).isEqualTo(2);
            assertThat(after.getHeldCount()).isEqualTo(2);
        }
    }

    @Test
    void aPauseRacingAnAcceptKeepsWhateverWasAccepted() {
        String code = voucher(5, 1);
        BenefitSelection selection = new BenefitSelection(SelectionChoice.VOUCHER, code, BigInteger.ZERO);
        long member = newMember();
        String memberDigest = digest(cart(member), selection);
        String sourceRef = "order-" + UUID.randomUUID();
        PromotionProgram before = program(code);

        List<Outcome<Object>> results = LoyaltyTestSupport.concurrently(
                () -> {
                    maintenance.pause(MANAGER_REF, UUID.randomUUID(), before.getId(), before.getConfigVersion());
                    return "paused";
                },
                () -> reserve(accept(cart(member), sourceRef, memberDigest), selection));

        assertThat(results.get(0).succeeded()).as("a hold never blocks a pause").isTrue();
        failuresAreTyped(results);
        PromotionProgram after = program(code);
        boolean held = results.get(1).succeeded();
        assertThat(after.getHeldCount()).isEqualTo(held ? 1 : 0);
        assertThat(benefits.findBySourceKindAndSourceRef(SourceKind.RETAIL, sourceRef).isPresent()).isEqualTo(held);
        if (held) {
            assertThat(benefits.findBySourceKindAndSourceRef(SourceKind.RETAIL, sourceRef).orElseThrow()
                    .getVoucherState()).as("a pause does not cancel an accepted hold").isEqualTo(ComponentState.HELD);
        }
    }

    @Test
    void aPolicyUpdateRacingAnAcceptKeepsThePolicyTheTransactionWasAcceptedUnder() {
        LoyaltyPolicy old = policyService.current();
        long member = newMember();
        wallet(member, 0);
        BenefitSelection none = BenefitSelection.none();
        String memberDigest = digest(cart(member), none);
        String sourceRef = "order-" + UUID.randomUUID();
        try {
            List<Outcome<Object>> results = LoyaltyTestSupport.concurrently(
                    () -> policyService.update(MANAGER_REF, UUID.randomUUID(), new PolicyUpdate(old.getRevisionNo(),
                            new PolicyTerms(n(5_000), n(100), n(10), BigDecimal.TEN))),
                    () -> reserve(accept(cart(member), sourceRef, memberDigest), none));

            assertThat(results.get(0).succeeded()).isTrue();
            failuresAreTyped(results);
            if (results.get(1).succeeded()) {
                AcceptedBenefit accepted = benefits.findBySourceKindAndSourceRef(SourceKind.RETAIL, sourceRef)
                        .orElseThrow();
                LoyaltyPolicy recorded = policies.findById(accepted.getPolicyId()).orElseThrow();
                AuthoritativeOutcome paid = outcome(SourceKind.RETAIL, sourceRef, OutcomeKind.RETAIL_FULLY_PAID,
                        "paid-" + sourceRef, 2);
                transaction.executeWithoutResult(status -> {
                    loyalty.settleAcceptedBenefits(paid);
                    loyalty.grantEarnedPoints(paid);
                });
                long expected = 800_000 / recorded.getEarnUnitAmount().longValueExact();
                assertThat(wallets.findById(member).orElseThrow().getBalancePoints())
                        .as("earned under the recorded policy, not recomputed").isEqualTo(n(expected));
            } else {
                assertThat(((LoyaltyException) results.get(1).failure()).code())
                        .isEqualTo(LoyaltyErrorCode.QUOTE_CHANGED);
                assertThat(benefits.findBySourceKindAndSourceRef(SourceKind.RETAIL, sourceRef)).isEmpty();
            }
        } finally {
            // Test cleanup only: point the registry back at the policy that was current, so other tests see it.
            transaction.executeWithoutResult(status ->
                    registry.findById(LoyaltyRegistry.SINGLETON_ID).orElseThrow().pointTo(old.getId()));
        }
    }

    @Test
    void theFirstWalletInsertIsNotDuplicatedByConcurrentEarns() {
        long member = newMember();
        BenefitSelection none = BenefitSelection.none();
        String memberDigest = digest(cart(member), none);
        String first = "order-" + UUID.randomUUID();
        String second = "order-" + UUID.randomUUID();
        reserve(accept(cart(member), first, memberDigest), none);
        reserve(accept(cart(member), second, memberDigest), none);
        assertThat(wallets.findById(member)).as("no wallet before the first earn").isEmpty();

        List<Callable<Object>> earns = List.of(paidAndEarned(first), paidAndEarned(second));
        List<Outcome<Object>> results = LoyaltyTestSupport.concurrently(earns.get(0), earns.get(1));

        failuresAreTyped(results);
        assertThat(results.stream().filter(result -> !result.succeeded()))
                .allSatisfy(result -> assertThat(((LoyaltyException) result.failure()).code())
                        .isEqualTo(LoyaltyErrorCode.RETRYABLE_CONTENTION));
        for (int i = 0; i < results.size(); i++) {
            if (!results.get(i).succeeded()) {
                call(earns.get(i)); // the owner retries the same outcome
            }
        }
        assertThat(wallets.findById(member).orElseThrow().getBalancePoints()).as("80 + 80").isEqualTo(n(160));
    }

    private Callable<Object> paidAndEarned(String sourceRef) {
        AuthoritativeOutcome paid = outcome(SourceKind.RETAIL, sourceRef, OutcomeKind.RETAIL_FULLY_PAID,
                "paid-" + sourceRef, 2);
        return () -> transaction.execute(status -> {
            loyalty.settleAcceptedBenefits(paid);
            return loyalty.grantEarnedPoints(paid);
        });
    }

    private static void call(Callable<Object> action) {
        try {
            action.call();
        } catch (Exception e) {
            throw new AssertionError("Retry failed", e);
        }
    }

    @Test
    void aDuplicatedAcceptAtTheSameTimeHoldsOnce() {
        long member = newMember();
        wallet(member, 500);
        BenefitSelection points = new BenefitSelection(SelectionChoice.NONE, null, n(200));
        String memberDigest = digest(cart(member), points);
        String sourceRef = "order-" + UUID.randomUUID();

        List<Outcome<AcceptedBenefitRef>> results = LoyaltyTestSupport.concurrently(
                () -> reserve(accept(cart(member), sourceRef, memberDigest), points),
                () -> reserve(accept(cart(member), sourceRef, memberDigest), points));

        assertThat(successes(results)).isEqualTo(2);
        assertThat(results.get(0).value().acceptedBenefitId()).isEqualTo(results.get(1).value().acceptedBenefitId());
        assertThat(wallets.findById(member).orElseThrow().getHeldPoints()).isEqualTo(n(200));
    }

    @Test
    void aDuplicatedAcceptWithoutPointsOrVoucherIsNotARawDatabaseError() {
        long member = newMember();
        BenefitSelection none = BenefitSelection.none();
        String memberDigest = digest(cart(member), none);
        String sourceRef = "order-" + UUID.randomUUID();

        List<Outcome<AcceptedBenefitRef>> results = LoyaltyTestSupport.concurrently(
                () -> reserve(accept(cart(member), sourceRef, memberDigest), none),
                () -> reserve(accept(cart(member), sourceRef, memberDigest), none));

        assertThat(successes(results)).isGreaterThanOrEqualTo(1);
        failuresAreTyped(results);
        assertThat(results.stream().filter(result -> !result.succeeded()))
                .allSatisfy(result -> assertThat(((LoyaltyException) result.failure()).code())
                        .isEqualTo(LoyaltyErrorCode.RETRYABLE_CONTENTION));
        assertThat(benefits.findBySourceKindAndSourceRef(SourceKind.RETAIL, sourceRef)).isPresent();
    }

    @Test
    void theSameOutcomeDeliveredTwiceAtOnceTakesEffectOnce() {
        long member = newMember();
        wallet(member, 500);
        BenefitSelection points = new BenefitSelection(SelectionChoice.NONE, null, n(200));
        String sourceRef = "order-" + UUID.randomUUID();
        AcceptedBenefitRef ref = reserve(accept(cart(member), sourceRef, digest(cart(member), points)), points);

        List<Outcome<Object>> results = LoyaltyTestSupport.concurrently(paidAndEarned(sourceRef),
                paidAndEarned(sourceRef));

        failuresAreTyped(results);
        assertThat(successes(results)).isGreaterThanOrEqualTo(1);
        LoyaltyWallet wallet = wallets.findById(member).orElseThrow();
        assertThat(wallet.getBalancePoints()).as("500 - 200 + 78").isEqualTo(n(378));
        assertThat(wallet.getHeldPoints()).isZero();
        assertThat(ledger.findByAcceptedBenefitIdOrderByIdAsc(ref.acceptedBenefitId()))
                .extracting(PointLedger::getEntryKind)
                .containsExactly(LedgerEntryKind.HOLD, LedgerEntryKind.CONSUME, LedgerEntryKind.EARN);
    }

    @Test
    void aPaidOutcomeRacingAnExpiryNeverBothSettlesAndReleases() {
        String code = voucher(5, 1);
        long member = newMember();
        wallet(member, 500);
        BenefitSelection selection = new BenefitSelection(SelectionChoice.VOUCHER, code, n(200));
        String sourceRef = "order-" + UUID.randomUUID();
        reserve(accept(cart(member), sourceRef, digest(cart(member), selection)), selection);
        AuthoritativeOutcome paid = outcome(SourceKind.RETAIL, sourceRef, OutcomeKind.RETAIL_FULLY_PAID,
                "paid-" + sourceRef, 2);
        AuthoritativeOutcome expired = outcome(SourceKind.RETAIL, sourceRef, OutcomeKind.UNPAID_EXPIRY_CONFIRMED,
                "expiry-" + sourceRef, 2);

        List<Outcome<Object>> results = LoyaltyTestSupport.concurrently(
                () -> transaction.execute(status -> loyalty.settleAcceptedBenefits(paid)),
                () -> transaction.execute(status -> loyalty.releaseHeldBenefits(expired)));

        assertThat(successes(results)).as("exactly one outcome takes effect").isEqualTo(1);
        failuresAreTyped(results);
        AcceptedBenefit benefit = benefits.findBySourceKindAndSourceRef(SourceKind.RETAIL, sourceRef).orElseThrow();
        LoyaltyWallet wallet = wallets.findById(member).orElseThrow();
        PromotionProgram after = program(code);
        assertThat(wallet.getHeldPoints()).isZero();
        assertThat(after.getHeldCount()).isZero();
        if (results.get(0).succeeded()) {
            assertThat(benefit.getPointsState()).isEqualTo(ComponentState.CONSUMED);
            assertThat(benefit.getVoucherState()).isEqualTo(ComponentState.CONSUMED);
            assertThat(wallet.getBalancePoints()).isEqualTo(n(300));
            assertThat(after.getConsumedCount()).isEqualTo(1);
        } else {
            assertThat(benefit.getPointsState()).isEqualTo(ComponentState.RELEASED);
            assertThat(benefit.getVoucherState()).isEqualTo(ComponentState.RELEASED);
            assertThat(wallet.getBalancePoints()).isEqualTo(n(500));
            assertThat(after.getConsumedCount()).isZero();
        }
    }

    // ----- forced lock timeout and deadlock, then retry -----

    /** Runs {@code work} in its own transaction on another thread; returns a handle to wait for it. */
    private Future<Object> inBackground(Callable<Object> work) {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            return pool.submit(work);
        } finally {
            pool.shutdown();
        }
    }

    /**
     * Reserves with a 1-second InnoDB lock wait for this transaction only (test-only session setting, reset before the
     * connection returns to the pool), so a blocked lock surfaces quickly instead of after MySQL's 50-second default.
     */
    private AcceptedBenefitRef reserveWithShortLockWait(AcceptedSourceContext context, BenefitSelection selection) {
        return transaction.execute(status -> {
            jdbc.execute("SET SESSION innodb_lock_wait_timeout = 1");
            try {
                return loyalty.reserveAcceptedTransaction(context, selection, "cmd-" + context.sourceRef());
            } finally {
                jdbc.execute("SET SESSION innodb_lock_wait_timeout = DEFAULT");
            }
        });
    }

    @Test
    void aLockTimeoutIsATypedRetryableErrorThatHoldsNothingAndARetrySucceeds() throws Exception {
        long member = newMember();
        wallet(member, 500);
        BenefitSelection points = new BenefitSelection(SelectionChoice.NONE, null, n(200));
        String digest = digest(cart(member), points);
        AcceptedSourceContext context = accept(cart(member), "order-" + UUID.randomUUID(), digest);

        CountDownLatch walletLocked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Future<Object> blocker = inBackground(() -> transaction.execute(status -> {
            wallets.lockByMemberId(member).orElseThrow();
            walletLocked.countDown();
            try {
                release.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "released";
        }));
        assertThat(walletLocked.await(10, TimeUnit.SECONDS)).isTrue();

        Throwable failure = null;
        try {
            reserveWithShortLockWait(context, points);
        } catch (Throwable e) {
            failure = e;
        } finally {
            release.countDown();
            blocker.get(30, TimeUnit.SECONDS);
        }

        assertThat(failure).isInstanceOfSatisfying(LoyaltyException.class,
                error -> assertThat(error.code()).isEqualTo(LoyaltyErrorCode.RETRYABLE_CONTENTION));
        assertThat(benefits.findBySourceKindAndSourceRef(SourceKind.RETAIL, context.sourceRef())).isEmpty();
        assertThat(wallets.findById(member).orElseThrow().getHeldPoints()).as("nothing held after the timeout").isZero();

        AcceptedBenefitRef retried = reserve(context, points);
        assertThat(retried).isNotNull();
        assertThat(wallets.findById(member).orElseThrow().getHeldPoints()).isEqualTo(n(200));
        assertThat(ledger.findAll().stream().filter(entry -> entry.getMemberId() == member
                && entry.getEntryKind() == LedgerEntryKind.HOLD)).hasSize(1);
    }

    @Test
    void aDeadlockLeavesNoPartialHoldAndTheLoserCanRetry() throws Exception {
        String code = voucher(5, 5);
        long member = newMember();
        wallet(member, 500);
        BenefitSelection selection = new BenefitSelection(SelectionChoice.VOUCHER, code, n(100));
        String digest = digest(cart(member), selection);
        AcceptedSourceContext context = accept(cart(member), "order-" + UUID.randomUUID(), digest);
        long programId = program(code).getId();

        // The other transaction takes the wallet first, then the programme: the reverse of loyalty's lock order.
        CountDownLatch walletLocked = new CountDownLatch(1);
        CountDownLatch takeProgramme = new CountDownLatch(1);
        Future<Object> reverseOrder = inBackground(() -> transaction.execute(status -> {
            wallets.lockByMemberId(member).orElseThrow();
            walletLocked.countDown();
            try {
                takeProgramme.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            programs.lockAllByIdIn(List.of(programId));
            return "reverse-order";
        }));
        assertThat(walletLocked.await(10, TimeUnit.SECONDS)).isTrue();

        // Loyalty locks registry → programme, then waits on the wallet; the other side then asks for the programme.
        Future<Object> accept = inBackground(() -> reserve(context, selection));
        Thread.sleep(700);
        takeProgramme.countDown();

        Throwable acceptFailure = failureOf(accept);
        Throwable reverseFailure = failureOf(reverseOrder);
        assertThat(acceptFailure == null || reverseFailure == null).as("MySQL rolls back exactly one side").isTrue();
        assertThat(acceptFailure != null || reverseFailure != null).as("the reverse order really deadlocked").isTrue();

        PromotionProgram afterRace = program(code);
        if (acceptFailure != null) {
            assertThat(acceptFailure).isInstanceOfSatisfying(LoyaltyException.class,
                    error -> assertThat(error.code()).isEqualTo(LoyaltyErrorCode.RETRYABLE_CONTENTION));
            assertThat(benefits.findBySourceKindAndSourceRef(SourceKind.RETAIL, context.sourceRef())).isEmpty();
            assertThat(afterRace.getHeldCount()).as("no partial voucher hold").isZero();
            assertThat(wallets.findById(member).orElseThrow().getHeldPoints()).as("no partial points hold").isZero();
            reserve(context, selection);
        }
        PromotionProgram afterRetry = program(code);
        assertThat(afterRetry.getHeldCount()).isEqualTo(1);
        assertThat(wallets.findById(member).orElseThrow().getHeldPoints()).isEqualTo(n(100));
        assertThat(benefits.findBySourceKindAndSourceRef(SourceKind.RETAIL, context.sourceRef())).isPresent();
    }

    private static Throwable failureOf(Future<Object> future) throws Exception {
        try {
            future.get(60, TimeUnit.SECONDS);
            return null;
        } catch (ExecutionException e) {
            return e.getCause();
        }
    }
}
