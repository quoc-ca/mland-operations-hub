package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dto.PromotionReadDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyErrorCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyException;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TargetDimension;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TargetFact;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TargetRef;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TargetScope;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Warns the Manager on a product programme's detail when products or sizes in its scope can no longer be bought
 * (spec clarification 2026-10-26, edge case "ẩn, ngừng bán hoặc xóa mềm sau khi tạo"). The programme is not paused or
 * changed; the Manager decides. Facts come from the owning module through {@link PromotionTargetValidationService}.
 *
 * <p>Runs outside any loyalty transaction and only for {@code getPromotionProgram}: when the product source cannot be
 * read, the detail still answers with {@code targetCheck = UNAVAILABLE} instead of failing, and write responses never
 * depend on it. {@code targetCheck} is CHECKED (warnings listed, possibly none) or UNAVAILABLE; absent for workshop.
 */
@Service
public class PromotionTargetWarningService {

    public static final String CHECKED = "CHECKED";
    public static final String UNAVAILABLE = "UNAVAILABLE";

    private final PromotionTargetValidationService targetFacts;

    public PromotionTargetWarningService(PromotionTargetValidationService targetFacts) {
        this.targetFacts = targetFacts;
    }

    public PromotionReadDtos.Program withWarnings(PromotionReadDtos.Program program) {
        if (!TargetScope.PRODUCT.name().equals(program.scope())) {
            return program;
        }
        List<TargetRef> refs = new ArrayList<>();
        PromotionReadDtos.Targets targets = program.targets();
        if (targets != null && targets.productIds() != null) {
            targets.productIds().forEach(key -> refs.add(new TargetRef(TargetDimension.PRODUCT, key)));
        }
        if (targets != null && targets.variantIds() != null) {
            targets.variantIds().forEach(key -> refs.add(new TargetRef(TargetDimension.VARIANT, key)));
        }
        if (targets != null && targets.branchIds() != null) {
            targets.branchIds().forEach(key -> refs.add(new TargetRef(TargetDimension.BRANCH, key)));
        }
        List<TargetFact> facts;
        try {
            facts = targetFacts.facts(TargetScope.PRODUCT, refs);
        } catch (LoyaltyException e) {
            if (e.code() != LoyaltyErrorCode.DEPENDENCY_UNAVAILABLE) {
                throw e;
            }
            return program.withTargetCheck(UNAVAILABLE, null);
        }
        List<PromotionReadDtos.TargetWarning> warnings = new ArrayList<>();
        for (TargetFact fact : facts) {
            // A branch of a product voucher (Session 2026-11-09) only warns when it no longer sells.
            String state = fact.dimension() == TargetDimension.BRANCH
                    ? (!fact.exists() || !fact.purchasable() ? "BRANCH_INACTIVE" : null)
                    : !fact.exists() || fact.softDeleted() ? "DELETED"
                    : !fact.publicVisible() ? "NOT_PUBLIC"
                    : !fact.purchasable() ? "NOT_ON_SALE" : null;
            if (state != null) {
                warnings.add(new PromotionReadDtos.TargetWarning(fact.dimension().name(), fact.targetKey(), state));
            }
        }
        return program.withTargetCheck(CHECKED, warnings);
    }
}
