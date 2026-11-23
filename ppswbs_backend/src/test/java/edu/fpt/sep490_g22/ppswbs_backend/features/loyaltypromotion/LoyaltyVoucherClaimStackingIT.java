package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.AcceptedBenefitRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.AcceptedBenefitPartRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.MemberProgramRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.MemberProgram;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.PromotionProgramRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.PromotionRevisionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.PromotionTargetRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dto.PromotionUsageDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.AcceptedBenefit;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.ProgramKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.PromotionProgram;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.PromotionRevision;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.PromotionTarget;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.VoucherDistribution;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.AcceptedBenefitRef;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.AcceptedSourceContext;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.AuthoritativeOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.BenefitPartRef;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.BenefitPreview;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.BenefitReason;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.BenefitReasonCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.BenefitSelection;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LineDiscount;
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
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.WorkshopFact;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.WorkshopSessionFact;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.WorkshopTicketFact;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.LoyaltyCalculator.DiscountTerms;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.PromotionUsageQueryService;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.SavedVoucherService;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.support.LoyaltyTestSupport;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.support.LoyaltyTestSupport.Outcome;
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
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Session 2026-11-22 (FR-015, FR-046, FR-047) on real MySQL 8.4 with V195: a voucher that must be saved (saving holds
 * one use; the last use goes to one Member only; holding, releasing and restoring move that use between the Member's
 * wallet and the order), and an automatic discount stacked with a voucher at accept, split per branch order and per
 * workshop ticket. Every programme targets random product/package IDs and its window ends soon, so the automatic
 * discounts created here never change the carts of other ITs sharing the database.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles(LoyaltyTestSupport.MYSQL_PROFILE)
class LoyaltyVoucherClaimStackingIT {

    private static final LocalDate DAY = LocalDate.parse("2026-12-05");
    private static final String SESSION = "00000000-0000-4000-8000-0000000000a1";

    @Autowired private LoyaltyFacade loyalty;
    @Autowired private SavedVoucherService savedVouchers;
    @Autowired private PromotionUsageQueryService usage;
    @Autowired private TransactionTemplate transaction;
    @Autowired private DataSource dataSource;
    @Autowired private PromotionProgramRepository programs;
    @Autowired private PromotionRevisionRepository revisions;
    @Autowired private PromotionTargetRepository targets;
    @Autowired private AcceptedBenefitRepository benefits;
    @Autowired private AcceptedBenefitPartRepository parts;
    @Autowired private MemberProgramRepository memberPrograms;

    @BeforeEach
    void requireMySql() {
        LoyaltyTestSupport.requireMySql84(dataSource);
    }

    private static BigInteger n(long value) {
        return BigInteger.valueOf(value);
    }

    private static long randomId() {
        return ThreadLocalRandom.current().nextLong(500_000_000L, 900_000_000L);
    }

    private static String code() {
        return "CS" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
    }

    /** A programme active for the next hour, with targets of one dimension. Returns its ID. */
    private long programme(ProgramKind kind, TargetScope scope, VoucherDistribution distribution, long totalLimit,
                           DiscountTerms terms, TargetDimension dimension, Set<Long> keys) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        String code = kind == ProgramKind.VOUCHER ? code() : null;
        return transaction.execute(status -> {
            PromotionProgram program = programs.saveAndFlush(kind == ProgramKind.VOUCHER
                    ? PromotionProgram.voucher(scope, code, code, totalLimit, 1, distribution, now, 1L)
                    : PromotionProgram.autoDiscount(scope, now, 1L));
            PromotionRevision revision = revisions.saveAndFlush(PromotionRevision.create(program.getId(), 1, kind,
                    "Claim/stacking IT", now.minus(1, ChronoUnit.DAYS), now.plus(1, ChronoUnit.HOURS), terms, true,
                    now, 1L));
            keys.forEach(key -> targets.save(PromotionTarget.create(revision.getId(), scope, dimension,
                    Long.toString(key))));
            program.pointToRevision(revision.getId());
            return program.getId();
        });
    }

    private long claimWorkshopVoucher(long packageId, long totalLimit) {
        return programme(ProgramKind.VOUCHER, TargetScope.WORKSHOP, VoucherDistribution.CLAIM_REQUIRED, totalLimit,
                DiscountTerms.fixed(n(100_000), n(100_000), BigInteger.ZERO), TargetDimension.PACKAGE,
                Set.of(packageId));
    }

    private String codeOf(long programId) {
        return programs.findById(programId).orElseThrow().getCodeOriginal();
    }

    private PromotionProgram program(long programId) {
        return programs.findById(programId).orElseThrow();
    }

    private void save(long memberId, long programId) {
        savedVouchers.save(memberId, UUID.randomUUID(), programId);
    }

    private static TrustedSourceContext booking(long memberId, long packageId) {
        return new TrustedSourceContext(SourceKind.WORKSHOP, memberId, Instant.now(), List.of(),
                WorkshopFact.singleSession(packageId, 1, n(1_000_000), WorkshopSessionFact.of(SESSION, 2L, DAY)),
                BigInteger.ZERO);
    }

    private static AcceptedSourceContext accept(TrustedSourceContext context, String sourceRef, String digest,
                                                List<SettlementPart> settlementParts) {
        Instant now = Instant.now();
        return new AcceptedSourceContext(context, sourceRef, 1L, true, now, now.plusSeconds(900),
                "quote-" + sourceRef, digest, settlementParts);
    }

    private AcceptedBenefitRef reserve(TrustedSourceContext context, BenefitSelection selection, String sourceRef,
                                       List<SettlementPart> settlementParts) {
        String digest = loyalty.previewBenefits(context, selection).termsDigest();
        return transaction.execute(status -> loyalty.reserveAcceptedTransaction(
                accept(context, sourceRef, digest, settlementParts), selection, "cmd-" + sourceRef));
    }

    private static AuthoritativeOutcome outcome(SourceKind kind, String sourceRef, OutcomeKind outcomeKind,
                                                long version) {
        Instant now = Instant.now();
        return new AuthoritativeOutcome(kind, sourceRef, kind == SourceKind.RETAIL ? "readyringsales" : "workshopbooking",
                outcomeKind + "-" + sourceRef, version, outcomeKind, now, now.plusSeconds(900), "snap", true, "proof");
    }

    /** As {@link #outcome} but for one part (branch order or ticket) and an explicit decision time. */
    private static AuthoritativeOutcome partOutcome(SourceKind kind, String sourceRef, OutcomeKind outcomeKind,
                                                    long version, String partRef, Instant decisionAt) {
        return new AuthoritativeOutcome(kind, sourceRef, kind == SourceKind.RETAIL ? "readyringsales" : "workshopbooking",
                outcomeKind + "-" + sourceRef + "-" + partRef, version, outcomeKind, decisionAt,
                decisionAt.plusSeconds(900), "snap", true, "proof", partRef);
    }

    /**
     * The Member's saved use of a CLAIM_REQUIRED product voucher, written as the save would (product targets here are
     * random IDs with no public product, so the HTTP save would answer 404).
     */
    private void claimFor(long memberId, long programId) {
        transaction.executeWithoutResult(status -> {
            programs.findById(programId).orElseThrow().claim();
            memberPrograms.saveAndFlush(MemberProgram.create(programId, memberId))
                    .save(Instant.now().truncatedTo(ChronoUnit.MICROS));
        });
    }

    private TrustedSourceContext twoTicketCart(long memberId, long packageId) {
        WorkshopSessionFact session = WorkshopSessionFact.of(SESSION, 2L, DAY);
        return new TrustedSourceContext(SourceKind.WORKSHOP, memberId, Instant.now(), List.of(),
                WorkshopFact.cart(List.of(new WorkshopTicketFact("t-1", packageId, n(500_000), List.of(session)),
                        new WorkshopTicketFact("t-2", packageId, n(500_000), List.of(session)))), BigInteger.ZERO);
    }

    private static BenefitSelection voucher(String code) {
        return new BenefitSelection(SelectionChoice.VOUCHER, code, BigInteger.ZERO);
    }

    // ---------------------------------------------------------------- FR-046: saving holds one use

    @Test
    void twoMembersRacingForTheLastSavedUseGiveItToExactlyOne() {
        long programId = claimWorkshopVoucher(randomId(), 1);
        long first = randomId();
        long second = randomId();

        List<Outcome<Object>> results = LoyaltyTestSupport.concurrently(
                () -> { save(first, programId); return "first"; },
                () -> { save(second, programId); return "second"; });

        assertThat(results.stream().filter(Outcome::succeeded).count()).isEqualTo(1);
        Throwable loser = results.stream().filter(result -> !result.succeeded()).findFirst().orElseThrow().failure();
        if (!(loser instanceof LoyaltyException)) {
            throw new AssertionError("The loser must get a typed loyalty error", loser);
        }
        assertThat(((LoyaltyException) loser).code()).isEqualTo(LoyaltyErrorCode.PROMOTION_INELIGIBLE);
        PromotionProgram after = program(programId);
        assertThat(after.getClaimedCount()).isEqualTo(1L);
        assertThat(after.availableTotal()).isZero();

        // The winner saving again holds nothing more; the loser still cannot save.
        long winner = results.get(0).succeeded() ? first : second;
        save(winner, programId);
        assertThat(program(programId).getClaimedCount()).isEqualTo(1L);
        assertThat(savedVouchers.saved(winner, programId).availability()).isEqualTo("ACTIVE");
    }

    @Test
    void anUnsavedMemberIsRefusedAndTheSavedUseMovesBetweenWalletAndOrder() {
        long packageId = randomId();
        long programId = claimWorkshopVoucher(packageId, 1);
        String code = codeOf(programId);
        long saver = randomId();
        long other = randomId();
        save(saver, programId);

        // Not saved: the preview says why, the accept is refused and holds nothing.
        BenefitPreview refused = loyalty.previewBenefits(booking(other, packageId), voucher(code));
        assertThat(refused.reasonDetails()).extracting(BenefitReason::code)
                .contains(BenefitReasonCode.VOUCHER_NOT_SAVED);
        Throwable strict = catchThrowable(() -> transaction.execute(status -> loyalty.reserveAcceptedTransaction(
                accept(booking(other, packageId), "booking-" + UUID.randomUUID(), refused.termsDigest(), List.of()),
                voucher(code), "cmd")));
        assertThat(strict).isInstanceOf(LoyaltyException.class);

        // The saver uses it although the voucher has no use left for anyone else.
        String first = "booking-" + UUID.randomUUID();
        AcceptedBenefitRef held = reserve(booking(saver, packageId), voucher(code), first, List.of());
        assertThat(held.voucherDiscount()).isEqualTo(n(100_000));
        assertThat(program(programId)).extracting(PromotionProgram::getClaimedCount, PromotionProgram::getHeldCount)
                .containsExactly(0L, 1L);

        // Unpaid expiry: the use goes back to the saver's wallet.
        transaction.executeWithoutResult(status -> loyalty.releaseHeldBenefits(
                outcome(SourceKind.WORKSHOP, first, OutcomeKind.UNPAID_EXPIRY_CONFIRMED, 1)));
        assertThat(program(programId)).extracting(PromotionProgram::getClaimedCount, PromotionProgram::getHeldCount)
                .containsExactly(1L, 0L);
        assertThat(savedVouchers.saved(saver, programId).availability()).isEqualTo("ACTIVE");

        // Used, then validly cancelled while the code is still valid: back in the wallet again.
        String second = "booking-" + UUID.randomUUID();
        reserve(booking(saver, packageId), voucher(code), second, List.of());
        transaction.executeWithoutResult(status -> loyalty.settleAcceptedBenefits(
                outcome(SourceKind.WORKSHOP, second, OutcomeKind.WORKSHOP_DEPOSIT_ACCEPTED, 1)));
        assertThat(program(programId).getConsumedCount()).isEqualTo(1L);
        assertThat(savedVouchers.saved(saver, programId).availability()).isEqualTo("MEMBER_LIMIT_REACHED");
        transaction.executeWithoutResult(status -> loyalty.restoreCancelledWorkshopBenefits(
                outcome(SourceKind.WORKSHOP, second, OutcomeKind.VALID_WORKSHOP_CANCELLATION, 2)));
        assertThat(program(programId)).extracting(PromotionProgram::getClaimedCount, PromotionProgram::getConsumedCount)
                .containsExactly(1L, 0L);
        assertThat(savedVouchers.saved(saver, programId).availability()).isEqualTo("ACTIVE");
    }

    // ---------------------------------------------------------------- T136: the saved use through part and ticket paths

    @Test
    void everyVoucherBranchOrderCancelledBeforePaymentGivesTheSavedUseBack() {
        long product = randomId();
        long programId = programme(ProgramKind.VOUCHER, TargetScope.PRODUCT, VoucherDistribution.CLAIM_REQUIRED, 1,
                DiscountTerms.fixed(n(50_000), n(50_000), BigInteger.ZERO), TargetDimension.PRODUCT, Set.of(product));
        long member = randomId();
        claimFor(member, programId);
        TrustedSourceContext cart = new TrustedSourceContext(SourceKind.RETAIL, member, Instant.now(),
                List.of(new SourceLine(product, product * 10, 1, n(300_000), 1L),
                        new SourceLine(product, product * 10, 1, n(200_000), 2L)), null, BigInteger.ZERO);
        String ref = "order-" + UUID.randomUUID();
        reserve(cart, voucher(codeOf(programId)), ref,
                List.of(new SettlementPart("branch-1", List.of(0)), new SettlementPart("branch-2", List.of(1))));
        assertThat(program(programId)).extracting(PromotionProgram::getClaimedCount, PromotionProgram::getHeldCount)
                .containsExactly(0L, 1L);

        transaction.executeWithoutResult(status -> loyalty.releaseHeldBenefits(partOutcome(SourceKind.RETAIL, ref,
                OutcomeKind.RETAIL_BRANCH_CANCELLED_BEFORE_PAYMENT, 1, "branch-1", Instant.now())));
        assertThat(program(programId).getHeldCount()).as("branch-2 still holds the voucher").isEqualTo(1L);
        transaction.executeWithoutResult(status -> loyalty.releaseHeldBenefits(partOutcome(SourceKind.RETAIL, ref,
                OutcomeKind.RETAIL_BRANCH_CANCELLED_BEFORE_PAYMENT, 2, "branch-2", Instant.now())));
        assertThat(program(programId)).extracting(PromotionProgram::getClaimedCount, PromotionProgram::getHeldCount)
                .containsExactly(1L, 0L);
        // The product of this test is not public, so the wallet reads NO_PUBLIC_TARGET; the Member's row is the proof.
        MemberProgram row = memberPrograms.findByProgramIdAndMemberId(programId, member).orElseThrow();
        assertThat(row.isSaved()).isTrue();
        assertThat(row.getHeldCount() + row.getConsumedCount()).isZero();
    }

    @Test
    void ticketsValidlyCancelledOrCancelledByMlandGiveTheSavedUseBack() {
        // Every ticket validly cancelled: the use comes back once all are cancelled.
        long packageId = randomId();
        long programId = claimWorkshopVoucher(packageId, 2);
        long member = randomId();
        save(member, programId);
        String ref = "group-" + UUID.randomUUID();
        reserve(twoTicketCart(member, packageId), voucher(codeOf(programId)), ref, List.of());
        transaction.executeWithoutResult(status -> loyalty.settleAcceptedBenefits(
                outcome(SourceKind.WORKSHOP, ref, OutcomeKind.WORKSHOP_DEPOSIT_ACCEPTED, 1)));
        transaction.executeWithoutResult(status -> loyalty.restoreCancelledWorkshopBenefits(partOutcome(
                SourceKind.WORKSHOP, ref, OutcomeKind.WORKSHOP_TICKET_VALID_CANCELLATION, 2, "t-1", Instant.now())));
        assertThat(program(programId).getConsumedCount()).as("t-2 still runs").isEqualTo(1L);
        transaction.executeWithoutResult(status -> loyalty.restoreCancelledWorkshopBenefits(partOutcome(
                SourceKind.WORKSHOP, ref, OutcomeKind.WORKSHOP_TICKET_VALID_CANCELLATION, 3, "t-2", Instant.now())));
        assertThat(program(programId)).extracting(PromotionProgram::getClaimedCount, PromotionProgram::getConsumedCount)
                .containsExactly(1L, 0L);

        // Mland cancels one ticket: the group's use comes back at once.
        String second = "group-" + UUID.randomUUID();
        reserve(twoTicketCart(member, packageId), voucher(codeOf(programId)), second, List.of());
        transaction.executeWithoutResult(status -> loyalty.settleAcceptedBenefits(
                outcome(SourceKind.WORKSHOP, second, OutcomeKind.WORKSHOP_DEPOSIT_ACCEPTED, 1)));
        transaction.executeWithoutResult(status -> loyalty.restoreCancelledWorkshopBenefits(partOutcome(
                SourceKind.WORKSHOP, second, OutcomeKind.WORKSHOP_TICKET_MLAND_CANCELLATION, 2, "t-1", Instant.now())));
        assertThat(program(programId)).extracting(PromotionProgram::getClaimedCount, PromotionProgram::getConsumedCount)
                .containsExactly(1L, 0L);
        assertThat(savedVouchers.saved(member, programId).availability()).isEqualTo("ACTIVE");
    }

    @Test
    void aSavedUseConsumedAndCancelledAfterTheVoucherEndedStaysUsed() {
        // FR-035: a code no longer valid when the cancellation is decided keeps its use as used.
        long packageId = randomId();
        long programId = claimWorkshopVoucher(packageId, 1);
        long member = randomId();
        save(member, programId);
        String ref = "booking-" + UUID.randomUUID();
        reserve(booking(member, packageId), voucher(codeOf(programId)), ref, List.of());
        transaction.executeWithoutResult(status -> loyalty.settleAcceptedBenefits(
                outcome(SourceKind.WORKSHOP, ref, OutcomeKind.WORKSHOP_DEPOSIT_ACCEPTED, 1)));
        Instant afterEnd = Instant.now().plus(2, ChronoUnit.HOURS);
        transaction.executeWithoutResult(status -> loyalty.restoreCancelledWorkshopBenefits(new AuthoritativeOutcome(
                SourceKind.WORKSHOP, ref, "workshopbooking", "late-cancel-" + ref, 2,
                OutcomeKind.VALID_WORKSHOP_CANCELLATION, afterEnd, afterEnd.plusSeconds(900), "snap", true, "proof")));
        assertThat(program(programId)).extracting(PromotionProgram::getClaimedCount, PromotionProgram::getConsumedCount)
                .containsExactly(0L, 1L);
        assertThat(savedVouchers.saved(member, programId).availability()).isNotEqualTo("ACTIVE");
    }

    // ---------------------------------------------------------------- T139: no voucher for 0 VND

    @Test
    void aVoucherLeftWithNothingAfterTheAutomaticDiscountIsRefused() {
        long product = randomId();
        programme(ProgramKind.AUTO_DISCOUNT, TargetScope.PRODUCT, null, 0,
                DiscountTerms.percent(new BigDecimal("100"), n(10_000_000), BigInteger.ZERO), TargetDimension.PRODUCT,
                Set.of(product));
        long voucherId = programme(ProgramKind.VOUCHER, TargetScope.PRODUCT, VoucherDistribution.PUBLIC, 10,
                DiscountTerms.percent(new BigDecimal("10"), n(100_000), BigInteger.ZERO), TargetDimension.PRODUCT,
                Set.of(product));
        TrustedSourceContext cart = new TrustedSourceContext(SourceKind.RETAIL, randomId(), Instant.now(),
                List.of(new SourceLine(product, product * 10, 1, n(300_000), 1L)), null, BigInteger.ZERO);
        BenefitPreview preview = loyalty.previewBenefits(cart, voucher(codeOf(voucherId)));
        assertThat(preview.reasonDetails()).extracting(BenefitReason::code)
                .contains(BenefitReasonCode.VOUCHER_NO_EXTRA_DISCOUNT);
        assertThat(preview.autoDiscount()).isEqualTo(n(300_000));
        assertThat(preview.voucherDiscount()).isEqualTo(n(0));
        Throwable refused = catchThrowable(() -> transaction.execute(status -> loyalty.reserveAcceptedTransaction(
                accept(cart, "order-" + UUID.randomUUID(), preview.termsDigest(), List.of()),
                voucher(codeOf(voucherId)), "cmd")));
        assertThat(refused).isInstanceOf(LoyaltyException.class);
        assertThat(program(voucherId).getHeldCount()).isZero();
    }

    // ---------------------------------------------------------------- FR-015/FR-047: stacking at accept

    @Test
    void aRetailCheckoutStacksBothDiscountsAndSplitsThemPerBranchOrder() {
        // US4 scenario 3a: A 600.000 at branch 1, B 400.000 at branch 2, 10% automatic on both, 20% voucher (cap
        // 100.000, minimum 300.000) on B only.
        long productA = randomId();
        long productB = randomId();
        long autoId = programme(ProgramKind.AUTO_DISCOUNT, TargetScope.PRODUCT, null, 0,
                DiscountTerms.percent(new BigDecimal("10"), n(1_000_000), BigInteger.ZERO), TargetDimension.PRODUCT,
                Set.of(productA, productB));
        long voucherId = programme(ProgramKind.VOUCHER, TargetScope.PRODUCT, VoucherDistribution.PUBLIC, 10,
                DiscountTerms.percent(new BigDecimal("20"), n(100_000), n(300_000)), TargetDimension.PRODUCT,
                Set.of(productB));
        long member = randomId();
        TrustedSourceContext cart = new TrustedSourceContext(SourceKind.RETAIL, member, Instant.now(),
                List.of(new SourceLine(productA, productA * 10, 1, n(600_000), 1L),
                        new SourceLine(productB, productB * 10, 1, n(400_000), 2L)), null, BigInteger.ZERO);

        BenefitPreview preview = loyalty.previewBenefits(cart, voucher(codeOf(voucherId)));
        assertThat(preview.autoProgramId()).isEqualTo(autoId);
        assertThat(preview.programId()).isEqualTo(voucherId);
        assertThat(preview.autoDiscount()).isEqualTo(n(100_000));
        assertThat(preview.voucherDiscount()).isEqualTo(n(72_000));
        assertThat(preview.programDiscount()).isEqualTo(n(172_000));
        assertThat(preview.lineDiscounts()).containsExactly(new LineDiscount(0, n(60_000), n(0)),
                new LineDiscount(1, n(40_000), n(72_000)));

        String ref = "order-" + UUID.randomUUID();
        AcceptedBenefitRef accepted = reserve(cart, voucher(codeOf(voucherId)), ref,
                List.of(new SettlementPart("branch-1", List.of(0)), new SettlementPart("branch-2", List.of(1))));
        assertThat(accepted.autoDiscount()).isEqualTo(n(100_000));
        assertThat(accepted.voucherDiscount()).isEqualTo(n(72_000));
        assertThat(accepted.parts()).extracting(BenefitPartRef::partRef, BenefitPartRef::programDiscount,
                        BenefitPartRef::autoDiscount, BenefitPartRef::voucherDiscount)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("branch-1", n(60_000), n(60_000), n(0)),
                        org.assertj.core.groups.Tuple.tuple("branch-2", n(112_000), n(40_000), n(72_000)));

        AcceptedBenefit row = benefits.findBySourceKindAndSourceRef(SourceKind.RETAIL, ref).orElseThrow();
        assertThat(row.getProgramId()).isEqualTo(voucherId);
        assertThat(row.getAutoProgramId()).isEqualTo(autoId);
        assertThat(row.getAutoDiscount()).isEqualTo(n(100_000));

        // Paid: each programme reports its own part.
        transaction.executeWithoutResult(status -> loyalty.settleAcceptedBenefits(
                outcome(SourceKind.RETAIL, ref, OutcomeKind.RETAIL_FULLY_PAID, 1)));
        PromotionUsageDtos.ProgramUsage voucherUsage = (PromotionUsageDtos.ProgramUsage) usage.usage(voucherId);
        assertThat(voucherUsage.consumedCount()).isEqualTo("1");
        assertThat(voucherUsage.committedDiscountAmount()).isEqualTo("72000");
        PromotionUsageDtos.AutoUsage autoUsage = (PromotionUsageDtos.AutoUsage) usage.usage(autoId);
        assertThat(autoUsage.acceptedCount()).isEqualTo("1");
        assertThat(autoUsage.committedDiscountAmount()).isEqualTo("100000");
    }

    @Test
    void aWorkshopCartStacksBothDiscountsAndKeepsEachTicketsShares() {
        long packageId = randomId();
        long autoId = programme(ProgramKind.AUTO_DISCOUNT, TargetScope.WORKSHOP, null, 0,
                DiscountTerms.fixed(n(100_000), n(100_000), BigInteger.ZERO), TargetDimension.PACKAGE,
                Set.of(packageId));
        long voucherId = programme(ProgramKind.VOUCHER, TargetScope.WORKSHOP, VoucherDistribution.PUBLIC, 10,
                DiscountTerms.percent(new BigDecimal("10"), n(1_000_000), BigInteger.ZERO), TargetDimension.PACKAGE,
                Set.of(packageId));
        long member = randomId();
        WorkshopSessionFact session = WorkshopSessionFact.of(SESSION, 2L, DAY);
        TrustedSourceContext cart = new TrustedSourceContext(SourceKind.WORKSHOP, member, Instant.now(), List.of(),
                WorkshopFact.cart(List.of(new WorkshopTicketFact("t-1", packageId, n(500_000), List.of(session)),
                        new WorkshopTicketFact("t-2", packageId, n(500_000), List.of(session)))), BigInteger.ZERO);

        // 100.000 automatic (50.000 a ticket), then 10% of the 900.000 left = 90.000 (45.000 a ticket).
        BenefitPreview preview = loyalty.previewBenefits(cart, voucher(codeOf(voucherId)));
        assertThat(preview.autoProgramId()).isEqualTo(autoId);
        assertThat(preview.programDiscount()).isEqualTo(n(190_000));
        assertThat(preview.postPromotionAmount()).isEqualTo(n(810_000));
        assertThat(preview.lineDiscounts()).containsExactly(new LineDiscount(0, n(50_000), n(45_000)),
                new LineDiscount(1, n(50_000), n(45_000)));

        AcceptedBenefitRef accepted = reserve(cart, voucher(codeOf(voucherId)), "group-" + UUID.randomUUID(),
                List.of());
        assertThat(accepted.parts()).extracting(BenefitPartRef::partRef, BenefitPartRef::autoDiscount,
                        BenefitPartRef::voucherDiscount)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("t-1", n(50_000), n(45_000)),
                        org.assertj.core.groups.Tuple.tuple("t-2", n(50_000), n(45_000)));
        assertThat(parts.findByAcceptedBenefitIdOrderByPartSeqAsc(accepted.acceptedBenefitId()))
                .allSatisfy(part -> assertThat(part.getVoucherDiscount()).isEqualTo(n(45_000)));
    }
}
