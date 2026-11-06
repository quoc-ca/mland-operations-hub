package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade;

/** Redacted verified-payment facts for a deposit that cannot confirm the booking. */
public record RecordLateDepositEvidenceCommand(
        String bookingReference,
        String merchantReference,
        String evidenceReference,
        String providerTransactionReference,
        long amountVnd,
        String currencyCode) {
}
