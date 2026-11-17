package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record OtaApiError(String code, String message, String correlationId, List<FieldError> fieldErrors) {
    public OtaApiError(String code, String message, String correlationId) {
        this(code, message, correlationId, List.of());
    }

    public record FieldError(String field, String code) {
    }
}
