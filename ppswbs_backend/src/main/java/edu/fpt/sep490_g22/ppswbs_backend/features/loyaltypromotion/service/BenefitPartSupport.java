package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.AcceptedBenefitPartRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.AcceptedBenefit;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.AcceptedBenefitPart;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.BenefitResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyErrorCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Settlement parts seen from the whole-transaction effects (Session 2026-11-12, D-011). A checkout accepted with parts
 * can still be settled, earned or released as a whole (for example VNPay pays every branch order at once): each part
 * then records the same result for its own share. A whole outcome is refused once any part has its own outcome, so a
 * checkout is driven either as a whole or part by part, never both. Package-private helper.
 */
@Component
class BenefitPartSupport {

    private final AcceptedBenefitPartRepository parts;

    BenefitPartSupport(AcceptedBenefitPartRepository parts) {
        this.parts = parts;
    }

    List<AcceptedBenefitPart> partsOf(AcceptedBenefit benefit) {
        return parts.findByAcceptedBenefitIdOrderByPartSeqAsc(benefit.getId());
    }

    /** Refuses a whole-transaction outcome on a checkout whose branch orders already have their own outcomes. */
    List<AcceptedBenefitPart> requireWholeAllowed(AcceptedBenefit benefit) {
        List<AcceptedBenefitPart> found = partsOf(benefit);
        if (found.stream().anyMatch(AcceptedBenefitPart::hasOutcome)) {
            throw new LoyaltyException(LoyaltyErrorCode.BENEFIT_STATE_CONFLICT,
                    "This checkout is settled per branch order; send the outcome of each branch order.");
        }
        return found;
    }

    void mirrorSettle(List<AcceptedBenefitPart> found, Instant at) {
        found.forEach(part -> part.mirrorSettle(at));
    }

    void mirrorRelease(List<AcceptedBenefitPart> found, Instant at) {
        found.forEach(part -> part.mirrorRelease(at));
    }

    void mirrorGrant(List<AcceptedBenefitPart> found) {
        found.forEach(AcceptedBenefitPart::mirrorGrant);
    }

    void mirrorIneligible(List<AcceptedBenefitPart> found) {
        found.forEach(AcceptedBenefitPart::mirrorIneligible);
    }

    /** The result with the current share and state of every part (empty list without parts). */
    BenefitResult result(AcceptedBenefit benefit) {
        return result(benefit, partsOf(benefit));
    }

    static BenefitResult result(AcceptedBenefit benefit, List<AcceptedBenefitPart> found) {
        return new BenefitResult(benefit.getSourceKind(), benefit.getSourceRef(), benefit.getPointsState(),
                benefit.getVoucherState(), benefit.getEarnState(), benefit.getSettledAt(),
                found.stream().map(AcceptedBenefitPart::ref).toList());
    }
}
