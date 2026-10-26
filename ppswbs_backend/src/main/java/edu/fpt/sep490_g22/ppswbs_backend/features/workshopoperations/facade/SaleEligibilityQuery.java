package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade;

import java.util.Objects;
import java.util.UUID;

public record SaleEligibilityQuery(UUID sessionReference) {
    public SaleEligibilityQuery {
        Objects.requireNonNull(sessionReference, "sessionReference must not be null");
    }
}
