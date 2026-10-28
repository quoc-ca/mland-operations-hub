package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.AcceptedBenefit;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.AuthoritativeOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.BenefitResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyErrorCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyException;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.OutcomeKind;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * settleAcceptedBenefits (T052, FR-018, FR-021, FR-032, US4.9, US5.4).
 *
 * <p>Only an outcome the owner verified settles: a retail order fully paid, a valid zero order (no gateway), or a
 * workshop deposit. HELD points become CONSUMED ({@code balance -= P, held -= P}) and a HELD voucher use becomes used
 * on both counters, exactly once. The settlement fact is recorded once even when nothing was held (automatic
 * discounts are reported from it). The same outcome replays as a no-op; an expired, released or conflicting state is
 * a conflict and nothing is consumed again. It does not need the Member to still be active: it completes a commitment
 * already accepted.
 */
@Service
public class LoyaltySettlementService {

    private static final Set<OutcomeKind> SETTLING = Set.of(OutcomeKind.RETAIL_FULLY_PAID,
            OutcomeKind.RETAIL_ZERO_ACCEPTED, OutcomeKind.WORKSHOP_DEPOSIT_ACCEPTED);

    private final BenefitEffectSupport effects;
    private final LoyaltyAuditService audit;

    public LoyaltySettlementService(BenefitEffectSupport effects, LoyaltyAuditService audit) {
        this.effects = effects;
        this.audit = audit;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public BenefitResult settleAcceptedBenefits(AuthoritativeOutcome outcome) {
        if (!SETTLING.contains(outcome.outcome())) {
            throw new LoyaltyException(LoyaltyErrorCode.BENEFIT_STATE_CONFLICT, "This outcome does not settle benefits.");
        }
        // A retail settlement is followed by the earn in the same transaction: lock the wallet now, in order.
        BenefitEffectSupport.Locked locked = effects.lockFor(outcome,
                outcome.outcome() != OutcomeKind.WORKSHOP_DEPOSIT_ACCEPTED);
        AcceptedBenefit benefit = locked.benefit();
        boolean firstSettlement = benefit.getSettlementOutcomeKey() == null;
        AcceptedBenefit.Changes changes = benefit.settle(outcome.sourceOutcomeKey(), outcome.sourceOutcomeVersion(),
                outcome.decisionAt());
        if (changes.points()) {
            effects.consumePoints(locked, outcome.decisionAt());
        }
        if (changes.voucher()) {
            effects.consumeVoucher(locked);
        }
        if (firstSettlement) {
            audit.record(outcome.ownerModule(), "benefit.settle", "benefit:" + benefit.getId(),
                    BenefitEffectSupport.detail(benefit, outcome));
        }
        return BenefitEffectSupport.result(benefit);
    }
}
