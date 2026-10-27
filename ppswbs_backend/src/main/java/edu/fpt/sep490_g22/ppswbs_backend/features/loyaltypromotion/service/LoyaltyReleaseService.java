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
 * releaseHeldBenefits (T022, FR-033, FR-036, FR-038, US7.1-US7.3, US7.6).
 *
 * <p>Only an outcome the source owner already verified and serialized can release: a confirmed unpaid expiry, or a
 * valid workshop cancellation while everything is still held. Loyalty never infers an expiry from the clock or
 * the original deadline. HELD components become RELEASED exactly once: {@code held -= P} with no extra credit and
 * one held use given back on both the programme total and the Member counter. A component that is already
 * CONSUMED is never released (a settled-versus-expired conflict is reported, not compensated). A repeat of the same
 * outcome is a no-op; a different outcome key is a conflict. State, counters, ledger and audit commit in the source
 * owner's transaction.
 */
@Service
public class LoyaltyReleaseService {

    private static final Set<OutcomeKind> ALLOWED = Set.of(OutcomeKind.UNPAID_EXPIRY_CONFIRMED,
            OutcomeKind.VALID_WORKSHOP_CANCELLATION);

    private final BenefitEffectSupport effects;
    private final LoyaltyAuditService audit;

    public LoyaltyReleaseService(BenefitEffectSupport effects, LoyaltyAuditService audit) {
        this.effects = effects;
        this.audit = audit;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public BenefitResult releaseHeldBenefits(AuthoritativeOutcome outcome) {
        if (!ALLOWED.contains(outcome.outcome())) {
            throw new LoyaltyException(LoyaltyErrorCode.BENEFIT_STATE_CONFLICT,
                    "This outcome does not release held benefits.");
        }
        BenefitEffectSupport.Locked locked = effects.lockFor(outcome);
        AcceptedBenefit benefit = locked.benefit();
        AcceptedBenefit.Changes changes = benefit.release(outcome.sourceOutcomeKey(), outcome.sourceOutcomeVersion(),
                outcome.decisionAt());
        if (changes.points()) {
            effects.releasePoints(locked, outcome.decisionAt());
        }
        if (changes.voucher()) {
            effects.releaseVoucher(locked);
        }
        if (changes.any()) {
            audit.record(outcome.ownerModule(), "benefit.release", "benefit:" + benefit.getId(),
                    BenefitEffectSupport.detail(benefit, outcome));
        }
        return BenefitEffectSupport.result(benefit);
    }
}
