package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade;

import java.util.List;

/**
 * Implemented by the owning module (products in productmanagement, packages/dates/sessions in
 * workshopbooking) as an independent adapter component. It returns immutable facts and guards only;
 * a missing provider must surface as {@code DEPENDENCY_UNAVAILABLE}, never as an allow-all fallback.
 */
public interface PromotionTargetPort {

    /** Whether this provider serves the given scope. */
    boolean supports(TargetScope scope);

    /** Validates the targets before the registry lock and returns one fact per requested target. */
    List<TargetFact> validateAndGuard(TargetScope scope, List<TargetRef> targets);
}
