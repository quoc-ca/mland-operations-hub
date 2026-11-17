package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyErrorCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyException;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.PromotionTargetPort;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TargetDimension;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TargetFact;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TargetRef;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TargetScope;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopLocationRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopPackageRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopSessionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopPackage;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopPackageState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.SessionState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopSession;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** WORKSHOP-scope target provider backed only by Workshop Operations-owned records. */
@Component
public class LoyaltyWorkshopTargetAdapter implements PromotionTargetPort {

    private final WorkshopPackageRepository packages;
    private final WorkshopLocationRepository locations;
    private final WorkshopSessionRepository sessions;

    public LoyaltyWorkshopTargetAdapter(WorkshopPackageRepository packages,
                                        WorkshopLocationRepository locations,
                                        WorkshopSessionRepository sessions) {
        this.packages = packages;
        this.locations = locations;
        this.sessions = sessions;
    }

    @Override
    public boolean supports(TargetScope scope) {
        return scope == TargetScope.WORKSHOP;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TargetFact> validateAndGuard(TargetScope scope, List<TargetRef> targets) {
        if (!supports(scope)) {
            throw LoyaltyException.invalidField("scope", "This provider serves workshop targets only.");
        }
        if (targets == null) {
            throw LoyaltyException.invalidField("targets", "Targets are required.");
        }

        try {
            List<TargetFact> facts = new ArrayList<>(targets.size());
            for (TargetRef target : targets) {
                if (target == null) {
                    throw LoyaltyException.invalidField("targets", "A target is required.");
                }
                facts.add(fact(target));
            }
            return List.copyOf(facts);
        } catch (DataAccessException e) {
            throw new LoyaltyException(LoyaltyErrorCode.DEPENDENCY_UNAVAILABLE,
                    "Workshop target information is temporarily unavailable.");
        }
    }

    private TargetFact fact(TargetRef target) {
        return switch (target.dimension()) {
            case PACKAGE -> packageFact(target);
            case BRANCH -> branchFact(target);
            case WORKSHOP_DATE -> dateFact(target);
            case SESSION -> sessionFact(target);
            default -> throw LoyaltyException.invalidField("targets",
                    "Workshop programmes target packages, branches, dates or sessions only.");
        };
    }

    private TargetFact packageFact(TargetRef target) {
        Optional<Long> id = numericId(target.targetKey());
        WorkshopPackage value = id.flatMap(packages::findById).orElse(null);
        if (value == null) {
            return missing(target);
        }
        boolean published = value.getState() == WorkshopPackageState.PUBLISHED;
        // Match the shared target contract: existence/deletion determine configuration validity;
        // current publication state is descriptive and must not invalidate a saved target.
        return new TargetFact(target.dimension(), target.targetKey(), true, true, false, published, published);
    }

    private TargetFact branchFact(TargetRef target) {
        Optional<Long> id = numericId(target.targetKey());
        var value = id.flatMap(locations::findById).orElse(null);
        if (value == null) {
            return missing(target);
        }
        return new TargetFact(target.dimension(), target.targetKey(), true, true, false,
                value.isActive(), value.isActive());
    }

    private static TargetFact dateFact(TargetRef target) {
        try {
            LocalDate date = LocalDate.parse(target.targetKey());
            boolean canonical = date.toString().equals(target.targetKey());
            return canonical
                    ? new TargetFact(target.dimension(), target.targetKey(), true, true, false, true, true)
                    : missing(target);
        } catch (DateTimeParseException e) {
            return missing(target);
        }
    }

    private TargetFact sessionFact(TargetRef target) {
        String reference = canonicalSessionReference(target.targetKey()).orElse(null);
        WorkshopSession value = reference == null ? null : sessions.findByReference(reference).orElse(null);
        if (value == null) {
            return missing(target);
        }
        boolean selectable = value.getState() == SessionState.PUBLISHED
                && value.getLocation() != null && value.getLocation().isActive();
        return new TargetFact(target.dimension(), target.targetKey(), true, true, false, selectable, selectable);
    }

    private static Optional<Long> numericId(String key) {
        if (key == null || !key.matches("[1-9][0-9]{0,18}")) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(key));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static Optional<String> canonicalSessionReference(String key) {
        try {
            UUID parsed = UUID.fromString(key);
            String canonical = parsed.toString();
            return canonical.equals(key) ? Optional.of(canonical) : Optional.empty();
        } catch (IllegalArgumentException | NullPointerException e) {
            return Optional.empty();
        }
    }

    private static TargetFact missing(TargetRef target) {
        return new TargetFact(target.dimension(), target.targetKey(), false, false, false, false, false);
    }
}
