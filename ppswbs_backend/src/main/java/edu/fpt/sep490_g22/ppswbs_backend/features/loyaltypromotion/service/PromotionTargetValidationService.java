package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyErrorCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyException;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.PromotionTargetPort;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TargetFact;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TargetRef;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TargetScope;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Checks programme targets through the owning module's {@link PromotionTargetPort} (T025, FR-007, interfaces
 * "Target port"). It runs before the registry lock.
 *
 * <ul>
 *   <li>Exactly one provider must serve the scope. Products are served by productmanagement (T091 of 002); the
 *       workshop provider belongs to FE-03 and does not exist yet, so a workshop programme is refused with
 *       {@code DEPENDENCY_UNAVAILABLE}. Nothing is ever accepted unchecked.</li>
 *   <li>A target is rejected only when it does not exist, does not belong to its parent product or is
 *       soft-deleted. Unpublished, hidden or stopped products stay valid (spec clarification 2026-10-26).</li>
 *   <li>Loyalty never reads another module's repository; it only sees the immutable facts the port returns.</li>
 * </ul>
 */
@Service
public class PromotionTargetValidationService {

    private final ObjectProvider<PromotionTargetPort> providers;

    public PromotionTargetValidationService(ObjectProvider<PromotionTargetPort> providers) {
        this.providers = providers;
    }

    /**
     * The owning module's facts for the targets, one per target in the same order, without judging them (used to
     * show only public products on public cards). Fails with DEPENDENCY_UNAVAILABLE when no provider serves the scope.
     */
    public List<TargetFact> facts(TargetScope scope, List<TargetRef> targets) {
        if (targets.isEmpty()) {
            return List.of();
        }
        List<PromotionTargetPort> serving = providers.orderedStream().filter(port -> port.supports(scope)).toList();
        if (serving.size() != 1) {
            throw new LoyaltyException(LoyaltyErrorCode.DEPENDENCY_UNAVAILABLE,
                    "The " + scope.name().toLowerCase() + " information needed to check this programme is unavailable.");
        }
        return serving.get(0).validateAndGuard(scope, targets);
    }

    public List<TargetFact> validate(TargetScope scope, List<TargetRef> targets) {
        List<TargetFact> facts = facts(scope, targets);
        if (facts == null || facts.size() != targets.size()) {
            throw new LoyaltyException(LoyaltyErrorCode.DEPENDENCY_UNAVAILABLE,
                    "The target information returned is incomplete.");
        }
        List<LoyaltyException.FieldIssue> issues = new ArrayList<>();
        for (int i = 0; i < targets.size(); i++) {
            TargetRef target = targets.get(i);
            TargetFact fact = facts.get(i);
            if (fact.dimension() != target.dimension() || !fact.targetKey().equals(target.targetKey())) {
                throw new LoyaltyException(LoyaltyErrorCode.DEPENDENCY_UNAVAILABLE,
                        "The target information returned does not match the request.");
            }
            String field = "targets." + fieldName(target) + "[" + target.targetKey() + "]";
            if (!fact.exists()) {
                issues.add(new LoyaltyException.FieldIssue(field, "This target does not exist."));
            } else if (fact.softDeleted()) {
                issues.add(new LoyaltyException.FieldIssue(field, "This target has been deleted."));
            } else if (!fact.belongsToParent()) {
                issues.add(new LoyaltyException.FieldIssue(field, "This size does not belong to the selected products."));
            }
        }
        if (!issues.isEmpty()) {
            throw new LoyaltyException(LoyaltyErrorCode.INVALID_INPUT, "The request contains an invalid value.", issues);
        }
        return facts;
    }

    private static String fieldName(TargetRef target) {
        return switch (target.dimension()) {
            case PRODUCT -> "productIds";
            case VARIANT -> "variantIds";
            case PACKAGE -> "packageIds";
            case BRANCH -> "branchIds";
            case WORKSHOP_DATE -> "workshopDates";
            case SESSION -> "sessionIds";
        };
    }
}
