package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.AcceptedBenefitPartRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.AcceptedBenefitRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.PromotionTargetRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.AcceptedBenefit;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.AcceptedBenefitPart;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.PromotionTarget;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.ComponentState;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyException;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TargetDimension;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TicketVoucherScope;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TicketVoucherScopeQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Per-ticket voucher scope for a proposed session set (003.3 FR-045, BE-DESIGN-0007). It re-applies the cart's own rule
 * ({@link WorkshopBenefitEvaluator#matches}) to the targets of the revision that was ACCEPTED, never the programme's
 * current terms, and reports the ticket's already allocated share as the amount at stake. It reads only.
 */
@Service
public class TicketVoucherScopeService {

    private final AcceptedBenefitRepository benefits;
    private final AcceptedBenefitPartRepository parts;
    private final PromotionTargetRepository targets;

    public TicketVoucherScopeService(AcceptedBenefitRepository benefits, AcceptedBenefitPartRepository parts,
                                     PromotionTargetRepository targets) {
        this.benefits = benefits;
        this.parts = parts;
        this.targets = targets;
    }

    @Transactional(readOnly = true)
    public TicketVoucherScope check(TicketVoucherScopeQuery query) {
        Optional<AcceptedBenefit> accepted = benefits.findBySourceKindAndSourceRef(query.sourceKind(), query.sourceRef());
        // No accepted benefit (a cart nothing discounted), no programme, or a programme that is not a held or consumed
        // voucher (an automatic discount, or a voucher already given back): nothing can be lost by moving sessions.
        if (accepted.isEmpty() || accepted.get().getRevisionId() == null || !voucherInForce(accepted.get())) {
            return TicketVoucherScope.noVoucher();
        }
        AcceptedBenefit benefit = accepted.get();
        AcceptedBenefitPart part = parts.findByAcceptedBenefitIdOrderByPartSeqAsc(benefit.getId()).stream()
                .filter(candidate -> query.ticketRef().equals(candidate.getPartRef()))
                .findFirst()
                .orElseThrow(() -> LoyaltyException.invalidField("ticketRef",
                        "The ticket is not part of this accepted transaction."));

        List<PromotionTarget> rows = targets.findByRevisionIdOrderByDimensionAscTargetKeyAsc(benefit.getRevisionId());
        WorkshopBenefitEvaluator.WorkshopCandidate scope = new WorkshopBenefitEvaluator.WorkshopCandidate(null,
                ids(rows, TargetDimension.PACKAGE), ids(rows, TargetDimension.BRANCH), dates(rows),
                keys(rows, TargetDimension.SESSION));
        boolean inScope = WorkshopBenefitEvaluator.matches(scope, query.asTicketFact());

        BigInteger share = part.getProgramDiscount() == null ? BigInteger.ZERO : part.getProgramDiscount();
        // A ticket that left scope loses exactly its allocated share; one that had none (it was outside scope at
        // booking) loses nothing and, moved into scope, gains nothing (FR-045).
        return new TicketVoucherScope(true, inScope, inScope ? BigInteger.ZERO : share);
    }

    private static boolean voucherInForce(AcceptedBenefit benefit) {
        return benefit.getVoucherState() == ComponentState.HELD || benefit.getVoucherState() == ComponentState.CONSUMED;
    }

    private static Set<Long> ids(List<PromotionTarget> rows, TargetDimension dimension) {
        return rows.stream().filter(row -> row.getDimension() == dimension)
                .map(row -> Long.parseLong(row.getTargetKey())).collect(Collectors.toUnmodifiableSet());
    }

    private static Set<LocalDate> dates(List<PromotionTarget> rows) {
        return rows.stream().filter(row -> row.getDimension() == TargetDimension.WORKSHOP_DATE)
                .map(row -> LocalDate.parse(row.getTargetKey())).collect(Collectors.toUnmodifiableSet());
    }

    private static Set<String> keys(List<PromotionTarget> rows, TargetDimension dimension) {
        return rows.stream().filter(row -> row.getDimension() == dimension)
                .map(PromotionTarget::getTargetKey).collect(Collectors.toUnmodifiableSet());
    }
}
