package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dto.response;

public record AdministrativeDivisionResponse(
    String id,
    String name,
    String code
) {
    public AdministrativeDivisionResponse(String id, String name) {
        this(id, name, null);
    }
}

