package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.AcceptedBenefitRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.LoyaltyPolicyRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.PointLedgerRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.AcceptedBenefit;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.LedgerEntryKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.LoyaltyPolicy;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.LoyaltyWallet;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.PointLedger;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.AuthoritativeOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.BenefitResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyErrorCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyException;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.OutcomeKind;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.util.Set;

/**
 * grantEarnedPoints and finishIneligibleEarn (T037, FR-028-FR-030, US6.1-US6.3, US6.6, US6.7).
 *
 * <p>Points are earned only from an outcome the source owner verified: a retail order fully paid (or a valid zero
 * order), or a workshop both completed and fully paid; a deposit, a check-in or a booking alone never earns. The
 * amount is {@code floor(earnBase / earnUnit)} with the policy recorded at acceptance, never the current one; the
 * remainder is not carried over. Every used component must already be CONSUMED (the owner settles first in the same
 * transaction), so nothing is credited while a debit is still held. One grant per transaction: the same outcome is a
 * no-op, another one a conflict. A zero result is recorded on the benefit without a ledger row. Earned points never
 * expire and are never withdrawn in V1. A no-show, a valid cancellation or an unpaid expiry closes a pending earn
 * without refunding consumed benefits (that is the restore effect's job, on valid cancellations only).
 */
@Service
public class LoyaltyEarnService {

    private static final Set<OutcomeKind> GRANTING = Set.of(OutcomeKind.RETAIL_FULLY_PAID,
            OutcomeKind.RETAIL_ZERO_ACCEPTED, OutcomeKind.WORKSHOP_COMPLETED_AND_FULLY_PAID);
    private static final Set<OutcomeKind> CLOSING = Set.of(OutcomeKind.UNPAID_EXPIRY_CONFIRMED,
            OutcomeKind.VALID_WORKSHOP_CANCELLATION, OutcomeKind.WORKSHOP_NO_SHOW_FINAL);

    private final AcceptedBenefitRepository benefits;
    private final LoyaltyPolicyRepository policies;
    private final PointLedgerRepository ledger;
    private final LoyaltyLockCoordinator locks;
    private final LoyaltyAuditService audit;

    public LoyaltyEarnService(AcceptedBenefitRepository benefits, LoyaltyPolicyRepository policies,
                              PointLedgerRepository ledger, LoyaltyLockCoordinator locks, LoyaltyAuditService audit) {
        this.benefits = benefits;
        this.policies = policies;
        this.ledger = ledger;
        this.locks = locks;
        this.audit = audit;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public BenefitResult grantEarnedPoints(AuthoritativeOutcome outcome) {
        if (!GRANTING.contains(outcome.outcome())) {
            throw new LoyaltyException(LoyaltyErrorCode.BENEFIT_STATE_CONFLICT, "This outcome does not earn points.");
        }
        AcceptedBenefitRepository.References references = references(outcome);
        locks.readRegistry();
        LoyaltyWallet wallet = locks.lockOrOpenWallet(references.getMemberId());
        AcceptedBenefit benefit = lockBenefit(outcome);
        LoyaltyPolicy policy = policies.findById(benefit.getPolicyId())
                .orElseThrow(() -> new LoyaltyException(LoyaltyErrorCode.BENEFIT_STATE_CONFLICT,
                        "The policy recorded for this transaction is missing."));
        BigInteger earned = LoyaltyCalculator.earnedPoints(policy.terms(), benefit.getEarnBase());
        boolean granted = benefit.grantEarn(outcome.sourceOutcomeKey(), outcome.sourceOutcomeVersion(), earned);
        if (granted) {
            if (earned.signum() > 0) {
                wallet.earn(earned);
                ledger.save(PointLedger.entry(LedgerEntryKind.EARN, benefit.getMemberId(), benefit.getId(), earned,
                        wallet.getBalancePoints(), wallet.getHeldPoints(), outcome.decisionAt(), null));
            }
            audit.record(outcome.ownerModule(), "benefit.earn", "benefit:" + benefit.getId(),
                    "{\"earnedPoints\":\"" + earned + "\",\"policyRevision\":" + policy.getRevisionNo() + "}");
        }
        return BenefitEffectSupport.result(benefit);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public BenefitResult finishIneligibleEarn(AuthoritativeOutcome outcome) {
        if (!CLOSING.contains(outcome.outcome())) {
            throw new LoyaltyException(LoyaltyErrorCode.BENEFIT_STATE_CONFLICT,
                    "This outcome does not close the earn.");
        }
        references(outcome);
        locks.readRegistry();
        AcceptedBenefit benefit = lockBenefit(outcome);
        if (benefit.finishIneligibleEarn(outcome.sourceOutcomeKey(), outcome.sourceOutcomeVersion(),
                outcome.decisionAt())) {
            audit.record(outcome.ownerModule(), "benefit.earn-ineligible", "benefit:" + benefit.getId(),
                    "{\"outcome\":\"" + outcome.outcome() + "\"}");
        }
        return BenefitEffectSupport.result(benefit);
    }

    /** Immutable references only; the entity is loaded later by the lock, in lock order. */
    private AcceptedBenefitRepository.References references(AuthoritativeOutcome outcome) {
        return benefits.findReferences(outcome.sourceKind(), outcome.sourceRef())
                .orElseThrow(LoyaltyEarnService::noBenefit);
    }

    private AcceptedBenefit lockBenefit(AuthoritativeOutcome outcome) {
        return locks.lockBenefit(outcome.sourceKind(), outcome.sourceRef()).orElseThrow(LoyaltyEarnService::noBenefit);
    }

    private static LoyaltyException noBenefit() {
        return new LoyaltyException(LoyaltyErrorCode.BENEFIT_STATE_CONFLICT,
                "No accepted benefit exists for this source transaction.");
    }
}
