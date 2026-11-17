package edu.fpt.sep490_g22.ppswbs_backend.common.payment;

/** Minimal receipt persisted only after checksum and payment-owner checks. */
public record VerifiedTerminalPaymentOutcome(String merchantReference, long amountVnd, String currencyCode,
                                             String responseCode, String transactionStatus,
                                             String evidenceReference) {}
