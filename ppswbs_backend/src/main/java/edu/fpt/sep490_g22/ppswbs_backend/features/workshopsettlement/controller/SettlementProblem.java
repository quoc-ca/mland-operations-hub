package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.controller;

import java.util.List;

public record SettlementProblem(String code, String correlationReference, List<FieldError> fieldErrors) {
    public SettlementProblem {
        fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
    }
    public record FieldError(String field, String message) { }
}
