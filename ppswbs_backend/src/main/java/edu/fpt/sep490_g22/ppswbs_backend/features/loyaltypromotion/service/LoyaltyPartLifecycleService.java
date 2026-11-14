package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.AcceptedBenefit;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.AcceptedBenefitPart;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.LedgerEntryKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.LoyaltyWallet;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.PointLedger;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.PointLedgerRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.AuthoritativeOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.BenefitResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.ComponentState;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyErrorCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyException;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.OutcomeKind;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.util.List;
import java.util.Set;

/**
 * Settle, earn and release one settlement part of a retail checkout (Session 2026-11-12, D-011): FE-08 branch orders
 * paid in cash at the counter, cancelled or expired one by one. Same lock order and MANDATORY transaction as the
 * whole-transaction effects; the parent benefit row lock serialises every part of a checkout.
 *
 * <ul>
 *   <li>Paid ({@code RETAIL_BRANCH_PAID}): the part's held points are debited; the voucher use is consumed when the
 *       first part that carries a share of the voucher discount is paid. Earn grants the part's own earn points.</li>
 *   <li>Cancelled or expired unpaid: the part's held points go back and its earn closes. The voucher use goes back
 *       once every part carrying a voucher share ended unpaid (even if a part without a share was paid). A voucher
 *       already consumed is never given back here.</li>
 * </ul>
 * When no part carries a programme discount, every part counts as carrying the voucher. The same outcome key on a part
 * replays as a no-op; another outcome on a decided part is a conflict.
 */
@Service
public class LoyaltyPartLifecycleService {

    private static final Set<OutcomeKind> RELEASING = Set.of(OutcomeKind.RETAIL_BRANCH_CANCELLED_BEFORE_PAYMENT,
            OutcomeKind.RETAIL_BRANCH_UNPAID_EXPIRY);

    private final BenefitEffectSupport effects;
    private final BenefitPartSupport partSupport;
    private final PointLedgerRepository ledger;
    private final LoyaltyLockCoordinator locks;
    private final LoyaltyAuditService audit;

    public LoyaltyPartLifecycleService(BenefitEffectSupport effects, BenefitPartSupport partSupport,
                                       PointLedgerRepository ledger, LoyaltyLockCoordinator locks,
                                       LoyaltyAuditService audit) {
        this.effects = effects;
        this.partSupport = partSupport;
        this.ledger = ledger;
        this.locks = locks;
        this.audit = audit;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public BenefitResult settlePart(AuthoritativeOutcome outcome) {
        require(outcome, OutcomeKind.RETAIL_BRANCH_PAID == outcome.outcome(), "This outcome does not settle a branch order.");
        // The earn follows in the same transaction: lock the wallet now, in order.
        BenefitEffectSupport.Locked locked = effects.lockFor(outcome, true);
        AcceptedBenefit benefit = locked.benefit();
        List<AcceptedBenefitPart> parts = partsFor(benefit);
        AcceptedBenefitPart part = find(parts, outcome.partRef());
        boolean first = !part.isPaid();
        if (part.settle(outcome.sourceOutcomeKey(), outcome.decisionAt())) {
            LoyaltyWallet wallet = locked.wallet();
            wallet.consume(part.getPoints());
            post(LedgerEntryKind.CONSUME, benefit, part, wallet, outcome);
        }
        if (benefit.settlePart(isVoucherPart(part, parts), outcome.decisionAt())) {
            effects.consumeVoucher(locked);
        }
        benefit.refreshFromParts(parts, outcome.decisionAt());
        if (first) {
            audit(outcome, "benefit.part-settle", benefit, part);
        }
        return BenefitPartSupport.result(benefit, parts);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public BenefitResult grantPart(AuthoritativeOutcome outcome) {
        require(outcome, OutcomeKind.RETAIL_BRANCH_PAID == outcome.outcome(), "This outcome does not earn points.");
        BenefitEffectSupport.Locked locked = effects.lockFor(outcome, true);
        AcceptedBenefit benefit = locked.benefit();
        List<AcceptedBenefitPart> parts = partsFor(benefit);
        AcceptedBenefitPart part = find(parts, outcome.partRef());
        if (isVoucherPart(part, parts) && benefit.getVoucherState() == ComponentState.HELD) {
            throw new LoyaltyException(LoyaltyErrorCode.BENEFIT_STATE_CONFLICT,
                    "Points cannot be earned while the voucher of this branch order is still held.");
        }
        if (part.grantEarn(outcome.sourceOutcomeKey())) {
            BigInteger earned = part.getEarnPoints();
            if (earned.signum() > 0) {
                LoyaltyWallet wallet = locked.wallet();
                wallet.earn(earned);
                post(LedgerEntryKind.EARN, benefit, part, wallet, outcome);
            }
            audit(outcome, "benefit.part-earn", benefit, part);
        }
        benefit.refreshFromParts(parts, outcome.decisionAt());
        return BenefitPartSupport.result(benefit, parts);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public BenefitResult releasePart(AuthoritativeOutcome outcome) {
        require(outcome, RELEASING.contains(outcome.outcome()), "This outcome does not release a branch order.");
        BenefitEffectSupport.Locked locked = effects.lockFor(outcome);
        AcceptedBenefit benefit = locked.benefit();
        List<AcceptedBenefitPart> parts = partsFor(benefit);
        AcceptedBenefitPart part = find(parts, outcome.partRef());
        boolean first = !part.isReleased();
        if (part.release(outcome.sourceOutcomeKey(), outcome.decisionAt())) {
            LoyaltyWallet wallet = locked.wallet();
            wallet.release(part.getPoints());
            post(LedgerEntryKind.RELEASE, benefit, part, wallet, outcome);
        }
        boolean everyVoucherPartUnpaid = parts.stream().filter(p -> isVoucherPart(p, parts))
                .allMatch(AcceptedBenefitPart::isReleased);
        if (everyVoucherPartUnpaid && benefit.releaseVoucherAfterParts()) {
            effects.releaseVoucher(locked);
        }
        benefit.refreshFromParts(parts, outcome.decisionAt());
        if (first) {
            audit(outcome, "benefit.part-release", benefit, part);
        }
        return BenefitPartSupport.result(benefit, parts);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public BenefitResult finishIneligiblePart(AuthoritativeOutcome outcome) {
        require(outcome, RELEASING.contains(outcome.outcome()), "This outcome does not close the earn.");
        locks.readRegistry();
        AcceptedBenefit benefit = locks.lockBenefit(outcome.sourceKind(), outcome.sourceRef())
                .orElseThrow(() -> new LoyaltyException(LoyaltyErrorCode.BENEFIT_STATE_CONFLICT,
                        "No accepted benefit exists for this source transaction."));
        List<AcceptedBenefitPart> parts = partsFor(benefit);
        find(parts, outcome.partRef()).finishIneligibleEarn(outcome.sourceOutcomeKey(), outcome.decisionAt());
        benefit.refreshFromParts(parts, outcome.decisionAt());
        return BenefitPartSupport.result(benefit, parts);
    }

    // ---- workshop tickets (Session 2026-11-12, D-012, 003.3 FR-030/FR-042/FR-043) ----

    /** A ticket checked out and fully paid: earns the ticket's share once, after the group deposit settled. */
    @Transactional(propagation = Propagation.MANDATORY)
    public BenefitResult grantTicket(AuthoritativeOutcome outcome) {
        require(outcome, outcome.outcome() == OutcomeKind.WORKSHOP_TICKET_COMPLETED_AND_FULLY_PAID,
                "This outcome does not earn points for a ticket.");
        BenefitEffectSupport.Locked locked = effects.lockFor(outcome, true);
        AcceptedBenefit benefit = locked.benefit();
        List<AcceptedBenefitPart> parts = ticketPartsFor(benefit);
        AcceptedBenefitPart part = find(parts, outcome.partRef());
        boolean settled = benefit.isSettled() && benefit.getVoucherState() != ComponentState.HELD;
        if (part.grantTicketEarn(outcome.sourceOutcomeKey(), settled)) {
            if (part.getEarnPoints().signum() > 0) {
                LoyaltyWallet wallet = locked.wallet();
                wallet.earn(part.getEarnPoints());
                post(LedgerEntryKind.EARN, benefit, part, wallet, outcome);
            }
            audit(outcome, "benefit.ticket-earn", benefit, part);
        }
        benefit.refreshFromParts(parts, outcome.decisionAt());
        return BenefitPartSupport.result(benefit, parts);
    }

    /**
     * A ticket validly cancelled (>= 24 h before its first session) or cancelled by Mland: the ticket's used points
     * come back (held → released, consumed → restored in the original amount) and its earn closes. The group's voucher
     * use comes back once: on a Mland cancellation at once, on a valid cancellation when every ticket ended by a valid
     * or Mland cancellation.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public BenefitResult cancelTicket(AuthoritativeOutcome outcome) {
        boolean mland = outcome.outcome() == OutcomeKind.WORKSHOP_TICKET_MLAND_CANCELLATION;
        require(outcome, mland || outcome.outcome() == OutcomeKind.WORKSHOP_TICKET_VALID_CANCELLATION,
                "This outcome does not cancel a ticket.");
        BenefitEffectSupport.Locked locked = effects.lockFor(outcome);
        AcceptedBenefit benefit = locked.benefit();
        List<AcceptedBenefitPart> parts = ticketPartsFor(benefit);
        AcceptedBenefitPart part = find(parts, outcome.partRef());
        boolean first = part.getClosure() == null;
        AcceptedBenefitPart.TicketPoints points = part.cancelTicket(outcome.sourceOutcomeKey(),
                mland ? AcceptedBenefitPart.MLAND_CANCELLATION : AcceptedBenefitPart.VALID_CANCELLATION,
                outcome.decisionAt());
        if (points != AcceptedBenefitPart.TicketPoints.NONE && part.getPoints().signum() > 0) {
            LoyaltyWallet wallet = locked.wallet();
            if (points == AcceptedBenefitPart.TicketPoints.RELEASED) {
                wallet.release(part.getPoints());
                post(LedgerEntryKind.RELEASE, benefit, part, wallet, outcome);
            } else {
                PointLedger debit = ledger.findByAcceptedBenefitIdAndEntryKind(benefit.getId(), LedgerEntryKind.CONSUME)
                        .orElseThrow(() -> new LoyaltyException(LoyaltyErrorCode.BENEFIT_STATE_CONFLICT,
                                "The original point debit is missing."));
                wallet.restore(part.getPoints());
                ledger.save(PointLedger.partRestore(benefit.getMemberId(), benefit.getId(), part.getPartSeq(),
                        part.getPoints(), wallet.getBalancePoints(), wallet.getHeldPoints(), outcome.decisionAt(),
                        debit.getId()));
            }
        }
        boolean voucherBack = mland || parts.stream().allMatch(AcceptedBenefitPart::isCancelledForVoucher);
        if (voucherBack) {
            ComponentState voucher = benefit.giveBackVoucherForTickets(outcome.decisionAt());
            if (voucher == ComponentState.RESTORED) {
                effects.restoreVoucher(locked);
            } else if (voucher == ComponentState.RELEASED) {
                effects.releaseVoucher(locked);
            }
        }
        benefit.refreshFromParts(parts, outcome.decisionAt());
        if (first) {
            audit(outcome, "benefit.ticket-cancel", benefit, part);
        }
        return BenefitPartSupport.result(benefit, parts);
    }

    /** A ticket's final no-show or late cancellation: nothing comes back and the ticket earns nothing. */
    @Transactional(propagation = Propagation.MANDATORY)
    public BenefitResult noShowTicket(AuthoritativeOutcome outcome) {
        require(outcome, outcome.outcome() == OutcomeKind.WORKSHOP_TICKET_NO_SHOW_FINAL,
                "This outcome does not close a ticket.");
        locks.readRegistry();
        AcceptedBenefit benefit = locks.lockBenefit(outcome.sourceKind(), outcome.sourceRef())
                .orElseThrow(() -> new LoyaltyException(LoyaltyErrorCode.BENEFIT_STATE_CONFLICT,
                        "No accepted benefit exists for this source transaction."));
        List<AcceptedBenefitPart> parts = ticketPartsFor(benefit);
        AcceptedBenefitPart part = find(parts, outcome.partRef());
        if (part.noShowTicket(outcome.sourceOutcomeKey(), outcome.decisionAt())) {
            audit(outcome, "benefit.ticket-no-show", benefit, part);
        }
        benefit.refreshFromParts(parts, outcome.decisionAt());
        return BenefitPartSupport.result(benefit, parts);
    }

    /** Tickets of a workshop cart; the group deposit settlement may come first, terminal whole outcomes may not. */
    private List<AcceptedBenefitPart> ticketPartsFor(AcceptedBenefit benefit) {
        if (benefit.hasWholeTerminalOutcome()) {
            throw new LoyaltyException(LoyaltyErrorCode.BENEFIT_STATE_CONFLICT,
                    "This booking already has an outcome for the whole booking.");
        }
        List<AcceptedBenefitPart> parts = partSupport.partsOf(benefit);
        if (parts.isEmpty()) {
            throw new LoyaltyException(LoyaltyErrorCode.BENEFIT_STATE_CONFLICT,
                    "This booking was accepted without tickets.");
        }
        return parts;
    }

    /** A part carries the voucher when it has a share of the programme discount, or when no part has one. */
    static boolean isVoucherPart(AcceptedBenefitPart part, List<AcceptedBenefitPart> parts) {
        boolean anyShare = parts.stream().anyMatch(p -> p.getProgramDiscount().signum() > 0);
        return !anyShare || part.getProgramDiscount().signum() > 0;
    }

    private List<AcceptedBenefitPart> partsFor(AcceptedBenefit benefit) {
        if (benefit.hasWholeOutcome()) {
            throw new LoyaltyException(LoyaltyErrorCode.BENEFIT_STATE_CONFLICT,
                    "This checkout already has an outcome for the whole transaction.");
        }
        List<AcceptedBenefitPart> parts = partSupport.partsOf(benefit);
        if (parts.isEmpty()) {
            throw new LoyaltyException(LoyaltyErrorCode.BENEFIT_STATE_CONFLICT,
                    "This checkout was accepted without branch orders.");
        }
        return parts;
    }

    private static AcceptedBenefitPart find(List<AcceptedBenefitPart> parts, String partRef) {
        return parts.stream().filter(p -> p.getPartRef().equals(partRef)).findFirst()
                .orElseThrow(() -> new LoyaltyException(LoyaltyErrorCode.BENEFIT_STATE_CONFLICT,
                        "This branch order is not part of the accepted checkout."));
    }

    private static void require(AuthoritativeOutcome outcome, boolean allowed, String message) {
        if (!allowed || !outcome.outcome().isPartOutcome()) {
            throw new LoyaltyException(LoyaltyErrorCode.BENEFIT_STATE_CONFLICT, message);
        }
    }

    private void post(LedgerEntryKind kind, AcceptedBenefit benefit, AcceptedBenefitPart part, LoyaltyWallet wallet,
                      AuthoritativeOutcome outcome) {
        BigInteger amount = kind == LedgerEntryKind.EARN ? part.getEarnPoints() : part.getPoints();
        if (amount.signum() > 0) {
            ledger.save(PointLedger.partEntry(kind, benefit.getMemberId(), benefit.getId(), part.getPartSeq(), amount,
                    wallet.getBalancePoints(), wallet.getHeldPoints(), outcome.decisionAt()));
        }
    }

    private void audit(AuthoritativeOutcome outcome, String action, AcceptedBenefit benefit, AcceptedBenefitPart part) {
        audit.record(outcome.ownerModule(), action, "benefit:" + benefit.getId(),
                "{\"outcome\":\"" + outcome.outcome() + "\",\"part\":" + part.getPartSeq() + ",\"points\":\""
                        + part.getPointsState() + "\",\"voucher\":\"" + benefit.getVoucherState() + "\",\"earn\":\""
                        + part.getEarnState() + "\"}");
    }
}
