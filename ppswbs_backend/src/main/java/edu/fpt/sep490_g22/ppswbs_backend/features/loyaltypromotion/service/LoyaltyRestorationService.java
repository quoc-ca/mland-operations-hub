package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.AcceptedBenefit;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.AuthoritativeOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.BenefitResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.ComponentState;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyErrorCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyException;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.OutcomeKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.SourceKind;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * restoreCancelledWorkshopBenefits (T023, FR-034-FR-037, US7.3-US7.7, US2.9).
 *
 * <p>Only a valid workshop cancellation before completion, verified by FE-03, restores benefits; a cancel button,
 * a no-show, an invalid or unconfirmed cancellation never does. Each component is handled on its own:
 * HELD is released, CONSUMED is restored. Points come back in the exact originally debited amount, linked to the
 * original debit, whatever the current conversion rate; nothing is converted to money and no new points are earned.
 * A voucher use is returned on both the programme total and the Member counter even when no points were used;
 * an expired or paused programme is not reactivated or extended. A retry after a partial result completes only
 * what is missing, and never restores twice. Earned points from a completed workshop are never withdrawn.
 */
@Service
public class LoyaltyRestorationService {

    private final BenefitEffectSupport effects;
    private final LoyaltyAuditService audit;

    public LoyaltyRestorationService(BenefitEffectSupport effects, LoyaltyAuditService audit) {
        this.effects = effects;
        this.audit = audit;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public BenefitResult restoreCancelledWorkshopBenefits(AuthoritativeOutcome outcome) {
        if (outcome.outcome() != OutcomeKind.VALID_WORKSHOP_CANCELLATION || outcome.sourceKind() != SourceKind.WORKSHOP) {
            throw new LoyaltyException(LoyaltyErrorCode.BENEFIT_STATE_CONFLICT,
                    "Only a valid workshop cancellation restores benefits.");
        }
        BenefitEffectSupport.Locked locked = effects.lockFor(outcome);
        AcceptedBenefit benefit = locked.benefit();
        AcceptedBenefit.Changes changes = benefit.restore(outcome.sourceOutcomeKey(), outcome.sourceOutcomeVersion(),
                outcome.decisionAt());
        if (changes.points()) {
            if (benefit.getPointsState() == ComponentState.RESTORED) {
                effects.restorePoints(locked, outcome.decisionAt());
            } else {
                effects.releasePoints(locked, outcome.decisionAt());
            }
        }
        if (changes.voucher()) {
            if (benefit.getVoucherState() == ComponentState.RESTORED) {
                effects.restoreVoucher(locked);
            } else {
                effects.releaseVoucher(locked);
            }
        }
        if (changes.any()) {
            audit.record(outcome.ownerModule(), "benefit.restore", "benefit:" + benefit.getId(),
                    BenefitEffectSupport.detail(benefit, outcome));
        }
        return BenefitEffectSupport.result(benefit);
    }
}
