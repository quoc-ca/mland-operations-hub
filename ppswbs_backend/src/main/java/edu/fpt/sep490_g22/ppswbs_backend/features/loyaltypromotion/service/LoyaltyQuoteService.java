package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.LoyaltyWalletRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.MemberProgramRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.PromotionEligibilityRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.PromotionProgramRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.PromotionRevisionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.PromotionTargetRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.LoyaltyPolicy;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.LoyaltyWallet;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.MemberProgram;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.ProgramKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.ProgramStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.PromotionProgram;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.PromotionRevision;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.PromotionTarget;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.BenefitPreview;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.BenefitSelection;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyErrorCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyException;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.SelectionChoice;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.SourceKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TargetDimension;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TargetScope;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TrustedSourceContext;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.WorkshopFact;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * previewBenefits (T050, FR-013-FR-016, FR-025, US4.1-US4.8). Loads the current policy, the Member's available points
 * and the candidate programmes, then applies {@link RetailQuote}. The preview is read-only: it holds no quota or
 * points and creates no accepted benefit. The same evaluation runs again at accept, under the locks, in strict mode.
 * Workshop bookings use {@link WorkshopBenefitEvaluator} (US5).
 */
@Service
public class LoyaltyQuoteService {

    /** A quote together with the policy it used and the digest the customer acknowledges. */
    public record Evaluation(RetailQuote.Result result, LoyaltyPolicy policy, String termsDigest) {
    }

    private final LoyaltyPolicyService policies;
    private final LoyaltyWalletRepository wallets;
    private final PromotionProgramRepository programs;
    private final PromotionRevisionRepository revisions;
    private final PromotionTargetRepository targets;
    private final PromotionEligibilityRepository eligibility;
    private final MemberProgramRepository memberPrograms;

    public LoyaltyQuoteService(LoyaltyPolicyService policies, LoyaltyWalletRepository wallets,
                               PromotionProgramRepository programs, PromotionRevisionRepository revisions,
                               PromotionTargetRepository targets, PromotionEligibilityRepository eligibility,
                               MemberProgramRepository memberPrograms) {
        this.policies = policies;
        this.wallets = wallets;
        this.programs = programs;
        this.revisions = revisions;
        this.targets = targets;
        this.eligibility = eligibility;
        this.memberPrograms = memberPrograms;
    }

    @Transactional(readOnly = true)
    public BenefitPreview preview(TrustedSourceContext context, BenefitSelection selection) {
        Evaluation evaluation = evaluate(context, selection, false);
        RetailQuote.Result result = evaluation.result();
        RetailQuote.Candidate chosen = result.candidate();
        LoyaltyPolicy policy = evaluation.policy();
        return new BenefitPreview(result.grossAmount(), result.eligibleSubtotal(), result.programDiscount(),
                result.netBeforePoints(), result.maxPoints(), policy.getMinRedeemPoints(), policy.getRedeemPointValue(),
                result.pointsUsed(), result.pointDiscount(), result.earnBase(), result.earnBase(),
                chosen == null ? null : chosen.programId(), chosen == null ? null : chosen.revisionNo(),
                chosen == null ? null : chosen.normalizedCode(), policy.getRevisionNo(), result.pointsAllowed(),
                result.reasons(), evaluation.termsDigest());
    }

    /**
     * Evaluates a retail selection with what the persistence context sees now. At accept the caller has already
     * locked the registry, the voucher programme, the wallet and the Member counters, so those reads are the locked
     * rows.
     */
    public Evaluation evaluate(TrustedSourceContext context, BenefitSelection selection, boolean strict) {
        LoyaltyPolicy policy = policies.current();
        BigInteger available = wallets.findById(context.memberId()).map(LoyaltyWallet::availablePoints)
                .orElse(BigInteger.ZERO);
        if (context.sourceKind() == SourceKind.WORKSHOP) {
            return evaluateWorkshop(context, selection, strict, policy, available);
        }
        RetailQuote.Candidate voucher = null;
        if (selection.choice() == SelectionChoice.VOUCHER) {
            String key = LoyaltyValues.normalizeCode("voucherCode", selection.voucherCode()).key();
            voucher = programs.findByCodeKey(key).map(program -> candidate(program, context, TargetScope.PRODUCT)).orElse(null);
        }
        List<RetailQuote.Candidate> autos = selection.choice() == SelectionChoice.AUTO
                ? eligibility.findActiveAutoDiscounts(context.at()).stream().map(p -> candidate(p, context, TargetScope.PRODUCT)).toList()
                : List.of();
        RetailQuote.Result result = RetailQuote.compute(context.lines(), selection, voucher, autos, policy.terms(),
                available, strict);
        return new Evaluation(result, policy, RetailQuote.digest(result, policy.getId(), context.shippingAmount()));
    }

    /** The programme as a candidate for this context, with the reason it cannot be used if any. */
    private Evaluation evaluateWorkshop(TrustedSourceContext context, BenefitSelection selection, boolean strict,
                                        LoyaltyPolicy policy, BigInteger available) {
        WorkshopBenefitEvaluator.WorkshopCandidate voucher = null;
        if (selection.choice() == SelectionChoice.VOUCHER) {
            String key = LoyaltyValues.normalizeCode("voucherCode", selection.voucherCode()).key();
            voucher = programs.findByCodeKey(key).map(program -> {
                RetailQuote.Candidate base = candidate(program, context, TargetScope.WORKSHOP);
                List<PromotionTarget> rows = targets.findByRevisionIdOrderByDimensionAscTargetKeyAsc(base.revisionId());
                return new WorkshopBenefitEvaluator.WorkshopCandidate(base, ids(rows, TargetDimension.PACKAGE),
                        ids(rows, TargetDimension.BRANCH), dates(rows), keys(rows, TargetDimension.SESSION));
            }).orElse(null);
        }
        RetailQuote.Result result = WorkshopBenefitEvaluator.compute(context.workshop(), selection, voucher,
                policy.terms(), available, strict);
        WorkshopFact booking = context.workshop();
        String digest = RetailQuote.digest(result, policy.getId(), BigInteger.ZERO);
        // The digest binds the package, every session (in the fact's stable order) and the participant count, so an
        // accept for other sessions than the ones the Member saw is QUOTE_CHANGED.
        StringBuilder bound = new StringBuilder(digest).append("|package=").append(booking.packageId());
        booking.sessions().forEach(session -> bound.append("|session=").append(session.sessionRef())
                .append('@').append(session.branchId()).append('@').append(session.workshopDate()));
        bound.append("|people=").append(booking.participantCount());
        return new Evaluation(result, policy, LoyaltyCommandService.digest(bound.toString()));
    }

    private static Set<LocalDate> dates(List<PromotionTarget> rows) {
        return rows.stream().filter(row -> row.getDimension() == TargetDimension.WORKSHOP_DATE)
                .map(row -> LocalDate.parse(row.getTargetKey())).collect(Collectors.toUnmodifiableSet());
    }

    private RetailQuote.Candidate candidate(PromotionProgram program, TrustedSourceContext context, TargetScope expected) {
        PromotionRevision revision = revisions.findById(program.getCurrentRevisionId())
                .orElseThrow(() -> new IllegalStateException("Programme " + program.getId() + " has no revision"));
        List<PromotionTarget> rows = targets.findByRevisionIdOrderByDimensionAscTargetKeyAsc(revision.getId());
        return new RetailQuote.Candidate(program.getId(), revision.getId(), revision.getRevisionNo(), program.getKind(),
                program.getCreatedAt(), revision.terms(), revision.isAllowPoints(),
                ids(rows, TargetDimension.PRODUCT), ids(rows, TargetDimension.VARIANT), program.getCodeOriginal(),
                unavailableReason(program, revision, context.memberId(), context.at(), expected));
    }

    private String unavailableReason(PromotionProgram program, PromotionRevision revision, long memberId, Instant at,
                                     TargetScope expected) {
        if (program.getScope() != expected) {
            return expected == TargetScope.PRODUCT ? "This voucher is for workshop bookings, not for products."
                    : "This voucher is for products, not for workshop bookings.";
        }
        if (program.getStatus() == ProgramStatus.PAUSED) {
            return "This promotion is paused.";
        }
        if (at.isBefore(revision.getStartAt())) {
            return "This promotion has not started yet.";
        }
        if (!at.isBefore(revision.getEndAt())) {
            return "This promotion has ended.";
        }
        if (program.getKind() == ProgramKind.VOUCHER) {
            if (program.availableTotal() < 1) {
                return "This voucher has no uses left.";
            }
            Optional<MemberProgram> counters = memberPrograms.findByProgramIdAndMemberId(program.getId(), memberId);
            long used = counters.map(c -> c.getHeldCount() + c.getConsumedCount()).orElse(0L);
            if (used >= program.getMemberLimit()) {
                return "You have already used this voucher the maximum number of times.";
            }
        }
        return null;
    }

    private static Set<String> keys(List<PromotionTarget> rows, TargetDimension dimension) {
        return rows.stream().filter(row -> row.getDimension() == dimension)
                .map(PromotionTarget::getTargetKey).collect(Collectors.toUnmodifiableSet());
    }

    private static Set<Long> ids(List<PromotionTarget> rows, TargetDimension dimension) {
        return rows.stream().filter(row -> row.getDimension() == dimension)
                .map(row -> Long.parseLong(row.getTargetKey())).collect(Collectors.toUnmodifiableSet());
    }
}
