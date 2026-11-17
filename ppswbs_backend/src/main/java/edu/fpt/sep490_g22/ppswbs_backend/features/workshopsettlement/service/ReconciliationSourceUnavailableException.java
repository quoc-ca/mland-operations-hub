package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

/** The 003.1 source of the reconciliation list failed; the whole list is refused, never a partial one (R02). */
public class ReconciliationSourceUnavailableException extends RuntimeException {
    public ReconciliationSourceUnavailableException(Throwable cause) {
        super("RECONCILIATION_SOURCE_UNAVAILABLE", cause);
    }
}
