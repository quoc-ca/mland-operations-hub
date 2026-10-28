package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.AcceptedBenefitRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.LoyaltyWalletRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.PromotionProgramRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.PromotionRevisionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.PromotionTargetRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.AcceptedBenefit;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.LoyaltyWallet;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.ProgramKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.PromotionProgram;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.PromotionRevision;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.PromotionTarget;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.AcceptedBenefitRef;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.AcceptedSourceContext;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.AuthoritativeOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.BenefitPreview;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.BenefitSelection;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.EarnState;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyErrorCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyException;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.OutcomeKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.SelectionChoice;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.SourceKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TargetDimension;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TargetScope;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TrustedSourceContext;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.WorkshopFact;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.LoyaltyCalculator.DiscountTerms;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.support.LoyaltyTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Workshop journey through {@link LoyaltyFacade} on MySQL 8.4 with this test acting as the booking owner (US5.2-US5.5,
 * US5.7). FE-03 is not on this branch, so this is NOT the T057 / G-04 / G-05 evidence: it only shows that loyalty
 * computes the post-promotion amount, holds at accept, consumes on the deposit without earning, and earns once on
 * completed-and-fully-paid. The deposit itself is FE-03's.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles(LoyaltyTestSupport.MYSQL_PROFILE)
class LoyaltyWorkshopFacadeIT {

    private static final LocalDate DAY = LocalDate.parse("2026-12-05");

    @Autowired private LoyaltyFacade loyalty;
    @Autowired private TransactionTemplate transaction;
    @Autowired private PromotionProgramRepository programs;
    @Autowired private PromotionRevisionRepository revisions;
    @Autowired private PromotionTargetRepository targets;
    @Autowired private LoyaltyWalletRepository wallets;
    @Autowired private AcceptedBenefitRepository benefits;

    private static BigInteger n(long value) {
        return BigInteger.valueOf(value);
    }

    private String workshopVoucher(long packageId) {
        String code = "WK" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        transaction.executeWithoutResult(status -> {
            PromotionProgram program = programs.saveAndFlush(
                    PromotionProgram.voucher(TargetScope.WORKSHOP, code, code, 10, 1, now, 1L));
            PromotionRevision revision = revisions.saveAndFlush(PromotionRevision.create(program.getId(), 1,
                    ProgramKind.VOUCHER, "Workshop IT", now.minus(1, ChronoUnit.DAYS), now.plus(1, ChronoUnit.DAYS),
                    DiscountTerms.fixed(n(100_000), n(100_000), BigInteger.ZERO), true, now, 1L));
            targets.save(PromotionTarget.create(revision.getId(), TargetScope.WORKSHOP, TargetDimension.PACKAGE,
                    Long.toString(packageId)));
            targets.save(PromotionTarget.create(revision.getId(), TargetScope.WORKSHOP, TargetDimension.WORKSHOP_DATE,
                    DAY.toString()));
            program.pointToRevision(revision.getId());
        });
        return code;
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

    private static TrustedSourceContext booking(long memberId, long packageId, LocalDate day) {
        return new TrustedSourceContext(SourceKind.WORKSHOP, memberId, Instant.now(), List.of(),
                new WorkshopFact(packageId, 2L, day, 3L, 2, n(1_000_000)), BigInteger.ZERO);
    }

    private static AcceptedSourceContext accept(TrustedSourceContext booking, String ref, String digest) {
        Instant now = Instant.now();
        return new AcceptedSourceContext(booking, ref, 1L, true, now, now.plusSeconds(900), "quote-" + ref, digest);
    }

    private static AuthoritativeOutcome outcome(String ref, OutcomeKind kind, String key, long version) {
        Instant now = Instant.now();
        return new AuthoritativeOutcome(SourceKind.WORKSHOP, ref, "workshopbooking", key, version, kind, now,
                now.plusSeconds(900), "snap", true, "proof");
    }

    @Test
    void depositConsumesWithoutEarningAndCompletionEarnsOnce() {
        long packageId = ThreadLocalRandom.current().nextLong(1_000_000L, 9_000_000L);
        String code = workshopVoucher(packageId);
        long memberId = member(1_000);
        BenefitSelection selection = new BenefitSelection(SelectionChoice.VOUCHER, code, n(200));
        BenefitPreview preview = loyalty.previewBenefits(booking(memberId, packageId, DAY), selection);
        assertThat(preview.postPromotionAmount()).isEqualTo(n(880_000));
        assertThat(preview.maxPoints()).isEqualTo(n(900));

        String ref = "booking-" + UUID.randomUUID();
        AcceptedBenefitRef accepted = transaction.execute(status -> loyalty.reserveAcceptedTransaction(
                accept(booking(memberId, packageId, DAY), ref, preview.termsDigest()), selection, "cmd"));
        assertThat(accepted.postPromotionAmount()).isEqualTo(n(880_000));

        AuthoritativeOutcome deposit = outcome(ref, OutcomeKind.WORKSHOP_DEPOSIT_ACCEPTED, "dep-" + ref, 2);
        transaction.executeWithoutResult(status -> loyalty.settleAcceptedBenefits(deposit));
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> loyalty.grantEarnedPoints(deposit)))
                .isInstanceOf(LoyaltyException.class);
        assertThat(wallets.findById(memberId).orElseThrow().getBalancePoints()).isEqualTo(n(800));

        AuthoritativeOutcome completed = outcome(ref, OutcomeKind.WORKSHOP_COMPLETED_AND_FULLY_PAID, "done-" + ref, 3);
        transaction.executeWithoutResult(status -> loyalty.grantEarnedPoints(completed));
        transaction.executeWithoutResult(status -> loyalty.grantEarnedPoints(completed));
        assertThat(wallets.findById(memberId).orElseThrow().getBalancePoints()).isEqualTo(n(888));
        AcceptedBenefit benefit = benefits.findBySourceKindAndSourceRef(SourceKind.WORKSHOP, ref).orElseThrow();
        assertThat(benefit.getEarnState()).isEqualTo(EarnState.GRANTED);
        assertThat(benefit.getPostPromotionAmount()).isEqualTo(n(880_000));
    }

    @Test
    void aVoucherForAnotherDateOrAProductVoucherIsRefused() {
        long packageId = ThreadLocalRandom.current().nextLong(1_000_000L, 9_000_000L);
        String code = workshopVoucher(packageId);
        long memberId = member(1);
        BenefitSelection selection = new BenefitSelection(SelectionChoice.VOUCHER, code, BigInteger.ZERO);
        BenefitPreview otherDay = loyalty.previewBenefits(booking(memberId, packageId, DAY.plusDays(1)), selection);
        assertThat(otherDay.programId()).isNull();
        assertThat(otherDay.reasons()).isNotEmpty();
        assertThatThrownBy(() -> transaction.execute(status -> loyalty.reserveAcceptedTransaction(
                accept(booking(memberId, packageId, DAY.plusDays(1)), "booking-" + UUID.randomUUID(),
                        otherDay.termsDigest()), selection, "cmd")))
                .isInstanceOfSatisfying(LoyaltyException.class,
                        e -> assertThat(e.code()).isEqualTo(LoyaltyErrorCode.PROMOTION_INELIGIBLE));
    }

    @Test
    void anAutomaticSelectionIsRefusedForWorkshops() {
        long memberId = member(1);
        BenefitPreview preview = loyalty.previewBenefits(booking(memberId, 1L, DAY),
                new BenefitSelection(SelectionChoice.AUTO, null, BigInteger.ZERO));
        assertThat(preview.programId()).isNull();
        assertThat(preview.reasons()).isNotEmpty();
    }
}
