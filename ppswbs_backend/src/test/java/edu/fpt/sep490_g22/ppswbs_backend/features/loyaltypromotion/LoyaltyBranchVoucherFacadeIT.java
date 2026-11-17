package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.PromotionProgramRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.PromotionRevisionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.PromotionTargetRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.ProgramKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.PromotionProgram;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.PromotionRevision;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.PromotionTarget;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.AcceptedBenefitRef;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.AcceptedSourceContext;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.BenefitPreview;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.BenefitReason;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.BenefitReasonCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.BenefitSelection;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.ComponentState;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyErrorCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyException;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.SelectionChoice;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T085/T088 (Session 2026-11-09, D-009) on MySQL 8.4 through the public {@link LoyaltyFacade}: the contract FE-08
 * uses for branch-limited product vouchers. This test plays the retail owner (FE-08) the way D-009 asks it to: each
 * {@link SourceLine} carries the {@code locationId} of the cart line, the preview tells which lines the voucher
 * discounts ({@link BenefitPreview#eligibleLineIndexes()}), and accept re-checks the same lines. A line without
 * {@code locationId} (FE-08 before the switch) keeps the old behaviour for every other benefit.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles(LoyaltyTestSupport.MYSQL_PROFILE)
class LoyaltyBranchVoucherFacadeIT {

    private static final long PRODUCT = 8_201L;
    private static final long SIZE = 82_011L;
    private static final long OTHER_PRODUCT = 8_202L;
    private static final long OTHER_SIZE = 82_021L;
    private static final long BRANCH_A = 1L;
    private static final long BRANCH_B = 2L;

    @Autowired private LoyaltyFacade loyalty;
    @Autowired private TransactionTemplate transaction;
    @Autowired private DataSource dataSource;
    @Autowired private PromotionProgramRepository programs;
    @Autowired private PromotionRevisionRepository revisions;
    @Autowired private PromotionTargetRepository targets;

    @BeforeEach
    void requireMySql() {
        LoyaltyTestSupport.requireMySql84(dataSource);
    }

    /** US4.11: the same ring added at A and at B; a 10% voucher limited to A discounts only the A line. */
    @Test
    void previewDiscountsOnlyTheLinesAddedAtTheVoucherBranchesAndNamesThem() {
        String code = voucher(List.of(BRANCH_A));
        long member = newMember();
        TrustedSourceContext cart = cart(member, line(PRODUCT, SIZE, 1_000_000, BRANCH_B),
                line(OTHER_PRODUCT, OTHER_SIZE, 300_000, BRANCH_A), line(PRODUCT, SIZE, 1_000_000, BRANCH_A));

        BenefitPreview preview = loyalty.previewBenefits(cart, voucherSelection(code));

        assertThat(preview.grossAmount()).isEqualTo(n(2_300_000));
        assertThat(preview.eligibleSubtotal()).isEqualTo(n(1_000_000));
        assertThat(preview.programDiscount()).isEqualTo(n(100_000));
        assertThat(preview.normalizedCode()).isEqualTo(code);
        assertThat(preview.eligibleLineIndexes()).as("only the ring added at A, in the order of the lines sent")
                .containsExactly(2);
        assertThat(preview.reasonDetails()).isEmpty();
    }

    @Test
    void aBranchLimitedVoucherIsRefusedWithBranchNotCoveredWhenNoLineIsAtItsBranchesOrHasNoBranch() {
        String code = voucher(List.of(BRANCH_A));
        long member = newMember();

        BenefitPreview atB = loyalty.previewBenefits(cart(member, line(PRODUCT, SIZE, 1_000_000, BRANCH_B)),
                voucherSelection(code));
        assertThat(atB.programId()).isNull();
        assertThat(atB.programDiscount()).isZero();
        assertThat(atB.eligibleLineIndexes()).isEmpty();
        assertThat(atB.reasonDetails()).extracting(BenefitReason::code)
                .containsExactly(BenefitReasonCode.BRANCH_NOT_COVERED);

        // FE-08 before the switch: the old 4-argument SourceLine has no branch.
        BenefitPreview noBranch = loyalty.previewBenefits(
                cart(member, new SourceLine(PRODUCT, SIZE, 1, n(1_000_000))), voucherSelection(code));
        assertThat(noBranch.programDiscount()).isZero();
        assertThat(noBranch.reasonDetails()).extracting(BenefitReason::code)
                .containsExactly(BenefitReasonCode.BRANCH_NOT_COVERED);
    }

    @Test
    void aVoucherWithoutBranchesIgnoresLocationIdAndKeepsItsDigest() {
        String code = voucher(List.of());
        long member = newMember();

        BenefitPreview withBranch = loyalty.previewBenefits(cart(member, line(PRODUCT, SIZE, 1_000_000, BRANCH_B)),
                voucherSelection(code));
        BenefitPreview withoutBranch = loyalty.previewBenefits(
                cart(member, new SourceLine(PRODUCT, SIZE, 1, n(1_000_000))), voucherSelection(code));

        assertThat(withBranch.programDiscount()).isEqualTo(n(100_000));
        assertThat(withBranch.eligibleLineIndexes()).containsExactly(0);
        assertThat(withoutBranch.programDiscount()).isEqualTo(n(100_000));
        assertThat(withoutBranch.eligibleLineIndexes()).containsExactly(0);
        assertThat(withBranch.termsDigest()).as("an owner that adds locationId does not invalidate stored quotes")
                .isEqualTo(withoutBranch.termsDigest());
    }

    @Test
    void acceptHoldsTheBranchVoucherWhenTheLinesAreTheOnesPreviewed() {
        String code = voucher(List.of(BRANCH_A));
        long member = newMember();
        TrustedSourceContext cart = cart(member, line(PRODUCT, SIZE, 1_000_000, BRANCH_A),
                line(PRODUCT, SIZE, 1_000_000, BRANCH_B));
        BenefitPreview preview = loyalty.previewBenefits(cart, voucherSelection(code));
        assertThat(preview.eligibleLineIndexes()).containsExactly(0);

        AcceptedBenefitRef held = reserve(accept(cart, preview.termsDigest()), voucherSelection(code));

        assertThat(held.programDiscount()).isEqualTo(n(100_000));
        assertThat(held.voucherState()).isEqualTo(ComponentState.HELD);
        assertThat(program(code).getHeldCount()).isEqualTo(1);
    }

    @Test
    void acceptIsQuoteChangedWhenALineMovedToAnotherBranchAfterThePreview() {
        String code = voucher(List.of(BRANCH_A));
        long member = newMember();
        TrustedSourceContext previewed = cart(member, line(PRODUCT, SIZE, 1_000_000, BRANCH_A),
                line(PRODUCT, SIZE, 1_000_000, BRANCH_B));
        String digest = loyalty.previewBenefits(previewed, voucherSelection(code)).termsDigest();
        // Same amounts and the same discount, but now the B line is the discounted one.
        TrustedSourceContext moved = cart(member, line(PRODUCT, SIZE, 1_000_000, BRANCH_B),
                line(PRODUCT, SIZE, 1_000_000, BRANCH_A));

        assertThatThrownBy(() -> reserve(accept(moved, digest), voucherSelection(code)))
                .isInstanceOfSatisfying(LoyaltyException.class,
                        e -> assertThat(e.code()).isEqualTo(LoyaltyErrorCode.QUOTE_CHANGED));
        assertThat(program(code).getHeldCount()).as("nothing is held").isZero();
    }

    @Test
    void acceptOfABranchVoucherWithoutAnyLineAtItsBranchesIsIneligible() {
        String code = voucher(List.of(BRANCH_A));
        long member = newMember();
        TrustedSourceContext atB = cart(member, line(PRODUCT, SIZE, 1_000_000, BRANCH_B));
        String digest = loyalty.previewBenefits(atB, voucherSelection(code)).termsDigest();

        assertThatThrownBy(() -> reserve(accept(atB, digest), voucherSelection(code)))
                .isInstanceOfSatisfying(LoyaltyException.class,
                        e -> assertThat(e.code()).isIn(LoyaltyErrorCode.PROMOTION_INELIGIBLE,
                                LoyaltyErrorCode.QUOTE_CHANGED));
        assertThat(program(code).getHeldCount()).isZero();
    }

    // ---- helpers ----

    private static BigInteger n(long value) {
        return BigInteger.valueOf(value);
    }

    private static long newMember() {
        return ThreadLocalRandom.current().nextLong(100_000_000L, 900_000_000L);
    }

    private static SourceLine line(long product, long size, long unit, Long branch) {
        return new SourceLine(product, size, 1, n(unit), branch);
    }

    private static TrustedSourceContext cart(long member, SourceLine... lines) {
        return new TrustedSourceContext(SourceKind.RETAIL, member, Instant.now(), List.of(lines), null,
                BigInteger.ZERO);
    }

    private static BenefitSelection voucherSelection(String code) {
        return new BenefitSelection(SelectionChoice.VOUCHER, code, BigInteger.ZERO);
    }

    private static AcceptedSourceContext accept(TrustedSourceContext context, String digest) {
        Instant now = Instant.now();
        String ref = "branch-it-" + UUID.randomUUID();
        return new AcceptedSourceContext(context, ref, 1L, true, now, now.plusSeconds(900), "quote-" + ref, digest);
    }

    private AcceptedBenefitRef reserve(AcceptedSourceContext context, BenefitSelection selection) {
        return transaction.execute(status ->
                loyalty.reserveAcceptedTransaction(context, selection, "cmd-" + context.sourceRef()));
    }

    /** An active product voucher on {@link #PRODUCT}: 10%, cap 1.000.000, no minimum, limited to {@code branches}. */
    private String voucher(List<Long> branches) {
        String code = "BR" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        transaction.executeWithoutResult(status -> {
            PromotionProgram program = programs.saveAndFlush(
                    PromotionProgram.voucher(TargetScope.PRODUCT, code, code, 10, 5, now, 1L));
            PromotionRevision revision = revisions.saveAndFlush(PromotionRevision.create(program.getId(), 1,
                    ProgramKind.VOUCHER, "Branch voucher IT", now.minus(1, ChronoUnit.DAYS),
                    now.plus(1, ChronoUnit.DAYS),
                    DiscountTerms.percent(new BigDecimal("10"), n(1_000_000), BigInteger.ZERO), true, now, 1L));
            targets.save(PromotionTarget.create(revision.getId(), TargetScope.PRODUCT, TargetDimension.PRODUCT,
                    Long.toString(PRODUCT)));
            for (Long branch : branches) {
                targets.save(PromotionTarget.create(revision.getId(), TargetScope.PRODUCT, TargetDimension.BRANCH,
                        Long.toString(branch)));
            }
            program.pointToRevision(revision.getId());
        });
        return code;
    }

    private PromotionProgram program(String code) {
        return transaction.execute(status -> programs.findByCodeKey(code).orElseThrow());
    }
}
