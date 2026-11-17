package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service;

public class RetailConflictException extends RuntimeException {
    private final String code;

    public RetailConflictException(String message) {
        this("RETAIL_CONFLICT", message);
    }

    /** A more specific 409 code the checkout screen can act on, e.g. RETAIL_VOUCHER_REFUSED. */
    public RetailConflictException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
