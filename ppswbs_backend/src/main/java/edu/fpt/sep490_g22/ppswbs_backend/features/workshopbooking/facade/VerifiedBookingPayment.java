package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade;

/** Redacted evidence for one verified deposit payment. */
public record VerifiedBookingPayment(String reference, String providerTransactionNumber,
        String originalProviderCreateDate, long amountVnd, String currencyCode, boolean verified) { }
