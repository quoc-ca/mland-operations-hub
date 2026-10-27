package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.AcceptedBenefitRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.LoyaltyPolicyRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.PointLedgerRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.PromotionProgramRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.AcceptedBenefit;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.LedgerEntryKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.LoyaltyPolicy;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.LoyaltyWallet;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.MemberProgram;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.PointLedger;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.ProgramKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.PromotionProgram;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.AcceptedBenefitRef;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.AcceptedSourceContext;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.BenefitSelection;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyErrorCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyException;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.SelectionChoice;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.SourceKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TrustedSourceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

/**
 * reserveAcceptedTransaction (T051, FR-017, FR-027, FR-032, US4.7, US4.8). Runs inside the source owner's accept
 * transaction (MANDATORY), so the owner's order rows and the loyalty holds commit or roll back together.
 *
 * <ol>
 *   <li>Only a Member's direct website transaction qualifies; a guest-import origin is refused.</li>
 *   <li>The same source reference with the same acknowledged terms returns the existing snapshot and holds nothing
 *       more; different terms are a conflict.</li>
 *   <li>Locks in order: registry READ, the selected voucher programme, the wallet, the Member counters. The quote is
 *       then computed again in strict mode from the locked rows and must reproduce the digest the customer
 *       acknowledged; otherwise QUOTE_CHANGED and nothing is held (never a silently smaller benefit or another code).</li>
 *   <li>Holds one voucher use (total and Member) and the requested points, writes the immutable snapshot with the
 *       policy of acceptance (also when no voucher or points are used), the HOLD ledger row and the audit row.</li>
 * </ol>
 * No remote call is made while the locks are held.
 */
@Service
public class LoyaltyReservationService {

    private final LoyaltyQuoteService quotes;
    private final LoyaltyLockCoordinator locks;
    private final AcceptedBenefitRepository benefits;
    private final PromotionProgramRepository programs;
    private final PointLedgerRepository ledger;
    private final LoyaltyAuditService audit;
    private final LoyaltyPolicyRepository policies;

    public LoyaltyReservationService(LoyaltyQuoteService quotes, LoyaltyLockCoordinator locks,
                                     AcceptedBenefitRepository benefits, PromotionProgramRepository programs,
                                     PointLedgerRepository ledger, LoyaltyAuditService audit,
                                     LoyaltyPolicyRepository policies) {
        this.policies = policies;
        this.quotes = quotes;
        this.locks = locks;
        this.benefits = benefits;
        this.programs = programs;
        this.ledger = ledger;
        this.audit = audit;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public AcceptedBenefitRef reserve(AcceptedSourceContext context, BenefitSelection selection, String sourceCommandKey) {
        if (!context.memberOrigin()) {
            throw new LoyaltyException(LoyaltyErrorCode.PROMOTION_INELIGIBLE,
                    "Only a Member's direct website transaction can use these benefits.");
        }
        TrustedSourceContext trusted = context.trusted();
        Optional<AcceptedBenefit> existing = benefits.findBySourceKindAndSourceRef(trusted.sourceKind(), context.sourceRef());
        if (existing.isPresent()) {
            return replay(existing.get(), context);
        }

        Long voucherProgramId = selection.choice() == SelectionChoice.VOUCHER
                ? programs.findIdByCodeKey(LoyaltyValues.normalizeCode("voucherCode", selection.voucherCode()).key())
                        .orElse(null)
                : null;
        long memberId = trusted.memberId();
        locks.readRegistry();
        PromotionProgram voucher = voucherProgramId == null ? null
                : locks.lockPrograms(Set.of(voucherProgramId)).get(voucherProgramId);
        LoyaltyWallet wallet = selection.requestedPoints().signum() > 0
                ? locks.lockOrOpenWallet(memberId) : locks.lockWallet(memberId).orElse(null);
        MemberProgram counters = voucher == null || voucher.getKind() != ProgramKind.VOUCHER ? null
                : locks.lockOrCreateMemberProgram(voucher.getId(), memberId);
        // A concurrent accept of the same source may have committed while this one waited for the locks above.
        Optional<AcceptedBenefit> concurrent = locks.lockBenefit(trusted.sourceKind(), context.sourceRef());
        if (concurrent.isPresent()) {
            return replay(concurrent.get(), context);
        }

        LoyaltyQuoteService.Evaluation evaluation = quotes.evaluate(trusted, selection, true);
        if (!evaluation.termsDigest().equals(context.termsDigest())) {
            throw new LoyaltyException(LoyaltyErrorCode.QUOTE_CHANGED,
                    "The price or conditions changed. Review the new total and confirm again.");
        }
        RetailQuote.Result result = evaluation.result();
        RetailQuote.Candidate chosen = result.candidate();
        boolean voucherHeld = chosen != null && chosen.kind() == ProgramKind.VOUCHER;
        if (voucherHeld) {
            voucher.hold();
            counters.hold(voucher.getMemberLimit());
        }
        BigInteger points = result.pointsUsed();
        if (points.signum() > 0) {
            wallet.hold(points);
        }
        Instant acceptedAt = LoyaltyValues.truncateToMicros(context.acceptedAt());
        AcceptedBenefit benefit = benefits.saveAndFlush(AcceptedBenefit.accept(new AcceptedBenefit.Snapshot(
                trusted.sourceKind(), context.sourceRef(), memberId, acceptedAt,
                LoyaltyValues.truncateToMicros(context.originalDeadline()), context.sourceRevision(),
                context.termsDigest(), evaluation.policy().getId(), chosen == null ? null : chosen.programId(),
                chosen == null ? null : chosen.revisionId(), result.grossAmount(), result.programDiscount(), points,
                result.pointDiscount(), result.earnBase(),
                trusted.sourceKind() == SourceKind.WORKSHOP ? result.earnBase() : null, voucherHeld), true));
        if (points.signum() > 0) {
            ledger.save(PointLedger.entry(LedgerEntryKind.HOLD, memberId, benefit.getId(), points,
                    wallet.getBalancePoints(), wallet.getHeldPoints(), acceptedAt, null));
        }
        audit.record("source:" + trusted.sourceKind(), "benefit.reserve", "benefit:" + benefit.getId(),
                "{\"program\":" + (chosen == null ? "null" : chosen.programId()) + ",\"points\":\"" + points
                        + "\",\"discount\":\"" + result.programDiscount() + "\"}");
        return ref(benefit);
    }

    /** The same source accepted again: the existing snapshot when the terms match, otherwise a conflict. */
    private AcceptedBenefitRef replay(AcceptedBenefit existing, AcceptedSourceContext context) {
        if (!existing.getAcceptedTermsDigest().equals(context.termsDigest())) {
            throw new LoyaltyException(LoyaltyErrorCode.IDEMPOTENCY_CONFLICT,
                    "This transaction was already accepted with different terms.");
        }
        return ref(existing);
    }

    AcceptedBenefitRef ref(AcceptedBenefit benefit) {
        long policyRevision = policies.findById(benefit.getPolicyId()).map(LoyaltyPolicy::getRevisionNo)
                .orElseThrow(() -> new IllegalStateException("The policy of an accepted benefit is missing"));
        return new AcceptedBenefitRef(benefit.getId(), benefit.getSourceKind(), benefit.getSourceRef(),
                benefit.getGrossAmount(), benefit.getProgramDiscount(), benefit.getRequestedPoints(),
                benefit.getPointDiscount(), benefit.getPostPromotionAmount(), benefit.getEarnBase(),
                policyRevision, benefit.getPointsState(), benefit.getVoucherState(), benefit.getEarnState());
    }
}
