package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * T135: partial update of a workshop location ({@code PATCH /workshop-locations/{locationId}}).
 * At least one of {@code name}, {@code address}, {@code active} is required. {@code code} is immutable:
 * it is accepted only so a different value can be refused with 400 VALIDATION_ERROR.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocationUpdateRequest {

    private String code;
    private String name;
    private String address;
    private Boolean active;
}
