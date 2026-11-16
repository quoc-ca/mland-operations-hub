package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

/**
 * A business rule refused valid input and the contract names the problem code (422), unlike the generic
 * {@link WorkshopSettlementValidationException}; the message is the code, for example
 * {@code UNDERCHARGE_EXCEEDS_FOLIO_TOTAL}.
 */
public class SettlementUnprocessableException extends WorkshopSettlementValidationException {
    public SettlementUnprocessableException(String code) {
        super(code);
    }
}
