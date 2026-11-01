package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade;

/**
 * One active branch where a Member can pick up or order from (the {@code locationId} of FE-08's cart line). Field names
 * follow the canonical branch list of 003.2 ({@code workshop_locations.display_name / address_snapshot}).
 */
public record SellingLocation(long id, String displayName, String addressSnapshot) {

    public SellingLocation {
        if (id <= 0 || displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("A selling location needs a positive id and a name");
        }
    }
}
