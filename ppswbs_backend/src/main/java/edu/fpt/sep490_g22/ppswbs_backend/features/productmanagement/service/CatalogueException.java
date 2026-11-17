package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service;

import edu.fpt.sep490_g22.ppswbs_backend.common.ErrorResponse;

import java.util.List;

/** Typed, safe business error of productmanagement; mapped to {@link ErrorResponse} by the controller advice. */
public class CatalogueException extends RuntimeException {

    private final CatalogueErrorCode code;
    private final List<ErrorResponse.FieldError> fieldErrors;

    public CatalogueException(CatalogueErrorCode code) {
        this(code, List.of());
    }

    public CatalogueException(CatalogueErrorCode code, List<ErrorResponse.FieldError> fieldErrors) {
        super(code.name(), null, false, false);
        this.code = code;
        this.fieldErrors = List.copyOf(fieldErrors);
    }

    public static CatalogueException invalidField(String field, String message) {
        return new CatalogueException(CatalogueErrorCode.VALIDATION_ERROR,
                List.of(new ErrorResponse.FieldError(field, message)));
    }

    public CatalogueErrorCode code() {
        return code;
    }

    public List<ErrorResponse.FieldError> fieldErrors() {
        return fieldErrors;
    }
}
