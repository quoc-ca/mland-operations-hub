package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.AcceptedBenefitRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.PointLedgerRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.AcceptedBenefit;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.LedgerEntryKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.LoyaltyWallet;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.MemberProgram;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.PointLedger;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.PromotionProgram;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.AuthoritativeOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.BenefitResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyErrorCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Set;

/**
 * Shared steps of the lifecycle effects (release, restore; later settle and earn): read the immutable references
 * of the accepted benefit, take the locks in the agreed order, and post wallet, quota and ledger changes for the
 * components that actually changed. Package-private helper; the public entry points are the effect services.
 */
@Component
class BenefitEffectSupport {

    /** Rows locked for one effect. {@code program}, {@code memberProgram} and {@code wallet} are null when unused. */
    record Locked(AcceptedBenefit benefit, PromotionProgram program, MemberProgram memberProgram, LoyaltyWallet wallet) {
    }

    private final AcceptedBenefitRepository benefits;
    private final PointLedgerRepository ledger;
    private final LoyaltyLockCoordinator locks;

    BenefitEffectSupport(AcceptedBenefitRepository benefits, PointLedgerRepository ledger, LoyaltyLockCoordinator locks) {
        this.benefits = benefits;
        this.ledger = ledger;
        this.locks = locks;
    }

    /**
     * Locks registry (shared), the programme, the wallet, the Member counters and finally the benefit. The first read
     * is unlocked and only used for the immutable references (member, programme, points requested); every state
     * decision is taken on the locked benefit.
     */
    Locked lockFor(AuthoritativeOutcome outcome) {
        return lockFor(outcome, false);
    }

    /**
     * As {@link #lockFor(AuthoritativeOutcome)}; with {@code openWallet} the wallet is locked (and created empty) even
     * when no points were used, so an earn in the same transaction never has to lock it after the benefit.
     */
    Locked lockFor(AuthoritativeOutcome outcome, boolean openWallet) {
        AcceptedBenefitRepository.References references = benefits
                .findReferences(outcome.sourceKind(), outcome.sourceRef())
                .orElseThrow(() -> new LoyaltyException(LoyaltyErrorCode.BENEFIT_STATE_CONFLICT,
                        "No accepted benefit exists for this source transaction."));
        locks.readRegistry();
        PromotionProgram program = references.getProgramId() == null ? null
                : locks.lockPrograms(Set.of(references.getProgramId())).get(references.getProgramId());
        LoyaltyWallet wallet;
        if (openWallet) {
            wallet = locks.lockOrOpenWallet(references.getMemberId());
        } else {
            wallet = references.getRequestedPoints().signum() == 0 ? null
                    : locks.lockWallet(references.getMemberId()).orElseThrow(() -> new LoyaltyException(
                            LoyaltyErrorCode.BENEFIT_STATE_CONFLICT, "The wallet of this benefit is missing."));
        }
        MemberProgram memberProgram = program == null || !program.isVoucher() ? null
                : locks.lockOrCreateMemberProgram(program.getId(), references.getMemberId());
        AcceptedBenefit benefit = locks.lockBenefit(outcome.sourceKind(), outcome.sourceRef())
                .orElseThrow(() -> new LoyaltyException(LoyaltyErrorCode.BENEFIT_STATE_CONFLICT,
                        "No accepted benefit exists for this source transaction."));
        return new Locked(benefit, program, memberProgram, wallet);
    }

    /** HELD points debited on a verified payment, deposit or zero order: balance -= P, held -= P, ledger CONSUME. */
    void consumePoints(Locked locked, Instant at) {
        locked.wallet().consume(locked.benefit().getRequestedPoints());
        post(LedgerEntryKind.CONSUME, locked, at, null);
    }

    /** HELD voucher use becomes used on both the programme total and the Member counter. */
    void consumeVoucher(Locked locked) {
        locked.program().consume();
        locked.memberProgram().consume();
    }

    /** HELD points back to available: held -= P, balance unchanged, ledger RELEASE. */
    void releasePoints(Locked locked, Instant at) {
        AcceptedBenefit benefit = locked.benefit();
        locked.wallet().release(benefit.getRequestedPoints());
        post(LedgerEntryKind.RELEASE, locked, at, null);
    }

    /** CONSUMED points returned in the exact original amount: balance += P, ledger RESTORE linked to the debit. */
    void restorePoints(Locked locked, Instant at) {
        AcceptedBenefit benefit = locked.benefit();
        PointLedger debit = ledger.findByAcceptedBenefitIdAndEntryKind(benefit.getId(), LedgerEntryKind.CONSUME)
                .orElseThrow(() -> new LoyaltyException(LoyaltyErrorCode.BENEFIT_STATE_CONFLICT,
                        "The original point debit is missing."));
        locked.wallet().restore(benefit.getRequestedPoints());
        post(LedgerEntryKind.RESTORE, locked, at, debit.getId());
    }

    /** HELD voucher use back to available on both the programme total and the Member counter. */
    void releaseVoucher(Locked locked) {
        locked.program().release();
        locked.memberProgram().release();
    }

    /** CONSUMED voucher use returned on both counters; an expired or paused programme is not reactivated. */
    void restoreVoucher(Locked locked) {
        locked.program().restore();
        locked.memberProgram().restore();
    }

    static BenefitResult result(AcceptedBenefit benefit) {
        return new BenefitResult(benefit.getSourceKind(), benefit.getSourceRef(), benefit.getPointsState(),
                benefit.getVoucherState(), benefit.getEarnState(), benefit.getSettledAt());
    }

    /** Safe audit detail: states and amounts only, no member data or raw proof. */
    static String detail(AcceptedBenefit benefit, AuthoritativeOutcome outcome) {
        return "{\"outcome\":\"" + outcome.outcome() + "\",\"outcomeVersion\":" + outcome.sourceOutcomeVersion()
                + ",\"points\":\"" + benefit.getPointsState() + "\",\"voucher\":\"" + benefit.getVoucherState()
                + "\",\"earn\":\"" + benefit.getEarnState() + "\"}";
    }

    private void post(LedgerEntryKind kind, Locked locked, Instant at, Long originalDebitId) {
        AcceptedBenefit benefit = locked.benefit();
        LoyaltyWallet wallet = locked.wallet();
        ledger.save(PointLedger.entry(kind, benefit.getMemberId(), benefit.getId(), benefit.getRequestedPoints(),
                wallet.getBalancePoints(), wallet.getHeldPoints(), at, originalDebitId));
    }
}
