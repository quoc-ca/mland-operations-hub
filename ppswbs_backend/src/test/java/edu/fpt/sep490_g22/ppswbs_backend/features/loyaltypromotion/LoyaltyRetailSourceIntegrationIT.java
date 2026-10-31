package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.AcceptedBenefitRepository;
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
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.BenefitPreview;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.BenefitSelection;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.ComponentState;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.EarnState;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyErrorCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.BenefitResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyException;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.OutcomeKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.SelectionChoice;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.SourceKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.SourceLine;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TargetDimension;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TargetScope;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TrustedSourceContext;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.LoyaltyCalculator.DiscountTerms;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.support.LoyaltyTestSupport;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.support.LoyaltyTestSupport.Outcome;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T049: the retail journey through {@link LoyaltyFacade} on the MySQL 8.4 test database, with this test acting as the
 * source owner (FE-08 does not exist on this branch yet, so this is not G-03 integration evidence). Preview writes
 * nothing; reserve holds atomically with the owner's transaction and rolls back with it; changed terms are refused;
 * a repeated accept holds once; the last use and the same points cannot be taken twice by concurrent orders; settle
 * then grant credits the earned points once (US4.4, US4.7-US4.10).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles(LoyaltyTestSupport.MYSQL_PROFILE)
class LoyaltyRetailSourceIntegrationIT {

    private static final long IN_SCOPE_PRODUCT = 7_001L;
    private static final long OTHER_PRODUCT = 7_002L;

    @Autowired private LoyaltyFacade loyalty;
    @Autowired private TransactionTemplate transaction;
    @Autowired private PromotionProgramRepository programs;
    @Autowired private PromotionRevisionRepository revisions;
    @Autowired private PromotionTargetRepository targets;
    @Autowired private LoyaltyWalletRepository wallets;
    @Autowired private AcceptedBenefitRepository benefits;
    @Autowired private PointLedgerRepository ledger;

    private static BigInteger n(long value) {
        return BigInteger.valueOf(value);
    }

    private static long newMember() {
        return ThreadLocalRandom.current().nextLong(100_000_000L, 900_000_000L);
    }

    /** An active product voucher: 10%, cap 50.000, minimum 500.000, on product 7001. Returns the code. */
    private String voucher(long totalLimit, boolean allowPoints) {
        String code = "RT" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        transaction.executeWithoutResult(status -> {
            PromotionProgram program = programs.saveAndFlush(
                    PromotionProgram.voucher(TargetScope.PRODUCT, code, code, totalLimit, 2, now, 1L));
            PromotionRevision revision = revisions.saveAndFlush(PromotionRevision.create(program.getId(), 1,
                    ProgramKind.VOUCHER, "Retail IT", now.minus(1, ChronoUnit.DAYS), now.plus(1, ChronoUnit.DAYS),
                    DiscountTerms.percent(new BigDecimal("10"), n(50_000), n(500_000)), allowPoints, now, 1L));
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

    /** US4.1/US4.4 cart: 600.000 in scope, 200.000 out of scope, shipping 30.000. */
    private static TrustedSourceContext cart(long memberId) {
        return new TrustedSourceContext(SourceKind.RETAIL, memberId, Instant.now(),
                List.of(new SourceLine(IN_SCOPE_PRODUCT, 70_011L, 2, n(300_000)),
                        new SourceLine(OTHER_PRODUCT, 70_021L, 1, n(200_000))), null, n(30_000));
    }

    private static AcceptedSourceContext accept(TrustedSourceContext cart, String sourceRef, String termsDigest,
                                                boolean memberOrigin) {
        Instant now = Instant.now();
        return new AcceptedSourceContext(cart, sourceRef, 1L, memberOrigin, now, now.plusSeconds(900),
                "quote-" + sourceRef, termsDigest);
    }

    private static AuthoritativeOutcome outcome(String sourceRef, OutcomeKind kind, String key, long version) {
        Instant now = Instant.now();
        return new AuthoritativeOutcome(SourceKind.RETAIL, sourceRef, "readyringsales", key, version, kind, now,
                now.plusSeconds(900), "snap", true, "proof");
    }

    private AcceptedBenefitRef reserve(AcceptedSourceContext context, BenefitSelection selection) {
        return transaction.execute(status -> loyalty.reserveAcceptedTransaction(context, selection, "cmd-" + context.sourceRef()));
    }

    private PromotionProgram program(String code) {
        return programs.findByCodeKey(code).orElseThrow();
    }

    private static void assertCode(Runnable action, LoyaltyErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(LoyaltyException.class,
                e -> assertThat(e.code()).isEqualTo(code));
    }

    @Test
    void previewShowsTheBreakdownAndWritesNothing() {
        String code = voucher(10, true);
        long member = newMember();
        wallet(member, 500);
        BenefitPreview preview = loyalty.previewBenefits(cart(member),
                new BenefitSelection(SelectionChoice.VOUCHER, " " + code.toLowerCase() + " ", n(200)));

        assertThat(preview.grossAmount()).isEqualTo(n(800_000));
        assertThat(preview.eligibleSubtotal()).isEqualTo(n(600_000));
        assertThat(preview.programDiscount()).isEqualTo(n(50_000));
        assertThat(preview.maxPoints()).isEqualTo(n(500));
        assertThat(preview.pointDiscount()).isEqualTo(n(20_000));
        assertThat(preview.earnBase()).isEqualTo(n(730_000));
        assertThat(preview.normalizedCode()).isEqualTo(code);
        assertThat(preview.termsDigest()).isNotBlank();
        assertThat(program(code).getHeldCount()).isZero();
        assertThat(wallets.findById(member).orElseThrow().getHeldPoints()).isZero();
    }

    @Test
    void theFullJourneyHoldsSettlesAndEarnsOnce() {
        String code = voucher(10, true);
        long member = newMember();
        wallet(member, 500);
        String sourceRef = "order-" + UUID.randomUUID();
        BenefitSelection selection = new BenefitSelection(SelectionChoice.VOUCHER, code, n(200));
        BenefitPreview preview = loyalty.previewBenefits(cart(member), selection);

        AcceptedBenefitRef ref = reserve(accept(cart(member), sourceRef, preview.termsDigest(), true), selection);
        assertThat(ref.pointsState()).isEqualTo(ComponentState.HELD);
        assertThat(ref.voucherState()).isEqualTo(ComponentState.HELD);
        assertThat(ref.earnBase()).isEqualTo(n(730_000));
        assertThat(wallets.findById(member).orElseThrow().getHeldPoints()).isEqualTo(n(200));
        assertThat(program(code).getHeldCount()).isEqualTo(1);

        AcceptedBenefitRef again = reserve(accept(cart(member), sourceRef, preview.termsDigest(), true), selection);
        assertThat(again.acceptedBenefitId()).as("same source, held once").isEqualTo(ref.acceptedBenefitId());
        assertThat(wallets.findById(member).orElseThrow().getHeldPoints()).isEqualTo(n(200));

        AuthoritativeOutcome paid = outcome(sourceRef, OutcomeKind.RETAIL_FULLY_PAID, "paid-" + sourceRef, 2);
        transaction.executeWithoutResult(status -> {
            loyalty.settleAcceptedBenefits(paid);
            loyalty.grantEarnedPoints(paid);
        });
        transaction.executeWithoutResult(status -> {
            loyalty.settleAcceptedBenefits(paid);
            loyalty.grantEarnedPoints(paid);
        });

        LoyaltyWallet wallet = wallets.findById(member).orElseThrow();
        assertThat(wallet.getBalancePoints()).as("500 - 200 + 73").isEqualTo(n(373));
        assertThat(wallet.getHeldPoints()).isZero();
        assertThat(program(code).getConsumedCount()).isEqualTo(1);
        assertThat(program(code).getHeldCount()).isZero();
        assertThat(ledger.findByAcceptedBenefitIdOrderByIdAsc(ref.acceptedBenefitId()))
                .extracting(PointLedger::getEntryKind)
                .containsExactly(LedgerEntryKind.HOLD, LedgerEntryKind.CONSUME, LedgerEntryKind.EARN);
        assertThat(loyalty.findBenefitResult(SourceKind.RETAIL, sourceRef).earnState()).isEqualTo(EarnState.GRANTED);
    }

    /** FE-08: the Member cancels the order before paying; voucher use and points go back, no earn (2026-10-30). */
    @Test
    void aCancellationBeforePaymentGivesBackTheVoucherAndPoints() {
        String code = voucher(10, true);
        long member = newMember();
        wallet(member, 500);
        String sourceRef = "order-" + UUID.randomUUID();
        BenefitSelection selection = new BenefitSelection(SelectionChoice.VOUCHER, code, n(200));
        BenefitPreview preview = loyalty.previewBenefits(cart(member), selection);
        AcceptedBenefitRef ref = reserve(accept(cart(member), sourceRef, preview.termsDigest(), true), selection);

        AuthoritativeOutcome cancelled = outcome(sourceRef, OutcomeKind.RETAIL_CANCELLED_BEFORE_PAYMENT,
                "cancel-" + sourceRef, 2);
        for (int attempt = 0; attempt < 2; attempt++) {
            transaction.executeWithoutResult(status -> {
                loyalty.releaseHeldBenefits(cancelled);
                loyalty.finishIneligibleEarn(cancelled);
            });
        }

        LoyaltyWallet wallet = wallets.findById(member).orElseThrow();
        assertThat(wallet.getBalancePoints()).isEqualTo(n(500));
        assertThat(wallet.getHeldPoints()).isZero();
        assertThat(program(code).getHeldCount()).isZero();
        assertThat(program(code).getConsumedCount()).isZero();
        assertThat(ledger.findByAcceptedBenefitIdOrderByIdAsc(ref.acceptedBenefitId()))
                .extracting(PointLedger::getEntryKind)
                .containsExactly(LedgerEntryKind.HOLD, LedgerEntryKind.RELEASE);
        BenefitResult result = loyalty.findBenefitResult(SourceKind.RETAIL, sourceRef);
        assertThat(result.pointsState()).isEqualTo(ComponentState.RELEASED);
        assertThat(result.voucherState()).isEqualTo(ComponentState.RELEASED);
        assertThat(result.earnState()).isEqualTo(EarnState.INELIGIBLE);

        // A late payment after the cancellation is a conflict for FE-08 to reconcile, never a silent consume.
        AuthoritativeOutcome latePaid = outcome(sourceRef, OutcomeKind.RETAIL_FULLY_PAID, "paid-" + sourceRef, 3);
        assertCode(() -> transaction.executeWithoutResult(status -> loyalty.settleAcceptedBenefits(latePaid)),
                LoyaltyErrorCode.BENEFIT_STATE_CONFLICT);
        // A paid order cannot be cancelled-before-payment: consumed components are never released.
        String paidRef = "order-" + UUID.randomUUID();
        reserve(accept(cart(member), paidRef, loyalty.previewBenefits(cart(member),
                new BenefitSelection(SelectionChoice.NONE, null, n(100))).termsDigest(), true),
                new BenefitSelection(SelectionChoice.NONE, null, n(100)));
        AuthoritativeOutcome paid = outcome(paidRef, OutcomeKind.RETAIL_FULLY_PAID, "paid-" + paidRef, 2);
        transaction.executeWithoutResult(status -> loyalty.settleAcceptedBenefits(paid));
        assertCode(() -> transaction.executeWithoutResult(status -> loyalty.releaseHeldBenefits(
                outcome(paidRef, OutcomeKind.RETAIL_CANCELLED_BEFORE_PAYMENT, "cancel-" + paidRef, 3))),
                LoyaltyErrorCode.BENEFIT_STATE_CONFLICT);
        // The outcome belongs to retail only.
        assertThatThrownBy(() -> new AuthoritativeOutcome(SourceKind.WORKSHOP, "b", "workshopbooking", "k", 1,
                OutcomeKind.RETAIL_CANCELLED_BEFORE_PAYMENT, Instant.now(), Instant.now().plusSeconds(60), "s", true, "p"))
                .isInstanceOf(LoyaltyException.class);
    }

    @Test
    void aFailedOwnerTransactionLeavesNothingHeld() {
        String code = voucher(10, true);
        long member = newMember();
        wallet(member, 500);
        String sourceRef = "order-" + UUID.randomUUID();
        BenefitSelection selection = new BenefitSelection(SelectionChoice.VOUCHER, code, n(200));
        String digest = loyalty.previewBenefits(cart(member), selection).termsDigest();
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            loyalty.reserveAcceptedTransaction(accept(cart(member), sourceRef, digest, true), selection, "cmd");
            throw new IllegalStateException("order insert failed after loyalty");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(benefits.findBySourceKindAndSourceRef(SourceKind.RETAIL, sourceRef)).isEmpty();
        assertThat(wallets.findById(member).orElseThrow().getHeldPoints()).isZero();
        assertThat(program(code).getHeldCount()).isZero();
    }

    @Test
    void changedTermsAreRefusedInsteadOfSilentlyRecomputed() {
        String code = voucher(10, true);
        long member = newMember();
        wallet(member, 500);
        String acknowledged = loyalty.previewBenefits(cart(member),
                new BenefitSelection(SelectionChoice.VOUCHER, code, n(100))).termsDigest();
        String sourceRef = "order-" + UUID.randomUUID();
        assertCode(() -> reserve(accept(cart(member), sourceRef, acknowledged, true),
                new BenefitSelection(SelectionChoice.VOUCHER, code, n(200))), LoyaltyErrorCode.QUOTE_CHANGED);
        assertThat(benefits.findBySourceKindAndSourceRef(SourceKind.RETAIL, sourceRef)).isEmpty();
        assertThat(wallets.findById(member).orElseThrow().getHeldPoints()).isZero();
    }

    @Test
    void aPausedVoucherOrAGuestOriginIsIneligible() {
        String code = voucher(10, true);
        long member = newMember();
        wallet(member, 500);
        BenefitSelection selection = new BenefitSelection(SelectionChoice.VOUCHER, code, BigInteger.ZERO);
        String digest = loyalty.previewBenefits(cart(member), selection).termsDigest();
        assertCode(() -> reserve(accept(cart(member), "guest-" + UUID.randomUUID(), digest, false), selection),
                LoyaltyErrorCode.PROMOTION_INELIGIBLE);

        transaction.executeWithoutResult(status -> program(code).pause());
        BenefitPreview paused = loyalty.previewBenefits(cart(member), selection);
        assertThat(paused.programId()).isNull();
        assertThat(paused.reasons()).isNotEmpty();
        assertCode(() -> reserve(accept(cart(member), "order-" + UUID.randomUUID(), digest, true), selection),
                LoyaltyErrorCode.PROMOTION_INELIGIBLE);
        assertThat(program(code).getHeldCount()).isZero();
    }

    @Test
    void aZeroOrderSettlesWithoutAGatewayAndEarnsNothing() {
        long member = newMember();
        wallet(member, 0);
        TrustedSourceContext free = new TrustedSourceContext(SourceKind.RETAIL, member, Instant.now(),
                List.of(new SourceLine(OTHER_PRODUCT, 70_021L, 1, BigInteger.ZERO)), null, BigInteger.ZERO);
        String sourceRef = "order-" + UUID.randomUUID();
        BenefitSelection none = BenefitSelection.none();
        String digest = loyalty.previewBenefits(free, none).termsDigest();
        reserve(accept(free, sourceRef, digest, true), none);
        AuthoritativeOutcome zero = outcome(sourceRef, OutcomeKind.RETAIL_ZERO_ACCEPTED, "zero-" + sourceRef, 2);
        transaction.executeWithoutResult(status -> {
            loyalty.settleAcceptedBenefits(zero);
            loyalty.grantEarnedPoints(zero);
        });
        assertThat(benefits.findBySourceKindAndSourceRef(SourceKind.RETAIL, sourceRef).orElseThrow().getEarnedPoints())
                .isZero();
        assertThat(wallets.findById(member).orElseThrow().getBalancePoints()).isZero();
    }

    @Test
    void theLastVoucherUseCannotBeHeldTwice() {
        String code = voucher(1, true);
        long first = newMember();
        long second = newMember();
        BenefitSelection selection = new BenefitSelection(SelectionChoice.VOUCHER, code, BigInteger.ZERO);
        String digestA = loyalty.previewBenefits(cart(first), selection).termsDigest();
        String digestB = loyalty.previewBenefits(cart(second), selection).termsDigest();
        List<Outcome<AcceptedBenefitRef>> results = LoyaltyTestSupport.concurrently(
                () -> reserve(accept(cart(first), "order-" + UUID.randomUUID(), digestA, true), selection),
                () -> reserve(accept(cart(second), "order-" + UUID.randomUUID(), digestB, true), selection));
        assertThat(results.stream().filter(Outcome::succeeded)).hasSize(1);
        assertThat(results.stream().filter(result -> !result.succeeded()))
                .allSatisfy(result -> assertThat(result.failure()).isInstanceOf(LoyaltyException.class));
        assertThat(program(code).getHeldCount()).isEqualTo(1);
    }

    @Test
    void theSamePointsCannotBeHeldByTwoOrders() {
        long member = newMember();
        wallet(member, 200);
        BenefitSelection selection = new BenefitSelection(SelectionChoice.NONE, null, n(200));
        String digest = loyalty.previewBenefits(cart(member), selection).termsDigest();
        List<Outcome<AcceptedBenefitRef>> results = LoyaltyTestSupport.concurrently(
                () -> reserve(accept(cart(member), "order-" + UUID.randomUUID(), digest, true), selection),
                () -> reserve(accept(cart(member), "order-" + UUID.randomUUID(), digest, true), selection));
        assertThat(results.stream().filter(Outcome::succeeded)).hasSize(1);
        assertThat(results.stream().filter(result -> !result.succeeded()))
                .allSatisfy(result -> assertThat(result.failure()).isInstanceOf(LoyaltyException.class));
        LoyaltyWallet wallet = wallets.findById(member).orElseThrow();
        assertThat(wallet.getHeldPoints()).isEqualTo(n(200));
        assertThat(wallet.getBalancePoints()).isEqualTo(n(200));
    }

    @Test
    void effectsRefuseToRunOutsideTheOwnersTransaction() {
        long member = newMember();
        BenefitSelection none = BenefitSelection.none();
        String digest = loyalty.previewBenefits(cart(member), none).termsDigest();
        String sourceRef = "order-" + UUID.randomUUID();
        assertThatThrownBy(() -> loyalty.reserveAcceptedTransaction(accept(cart(member), sourceRef, digest, true), none,
                "cmd")).isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
        assertThat(benefits.findBySourceKindAndSourceRef(SourceKind.RETAIL, sourceRef)).isEmpty();
    }
}
