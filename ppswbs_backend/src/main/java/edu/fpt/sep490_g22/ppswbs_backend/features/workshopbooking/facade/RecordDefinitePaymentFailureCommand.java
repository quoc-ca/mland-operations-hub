package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade;

/**
 * A verified (signature, owner, exact amount and currency passed) VNPay IPN that reports a definite failure of a
 * booking-group payment attempt (003.1 FR-032 "Definite failure", decided 2026-11-19). Called only by
 * {@code paymentintegration}; never for {@code 01}, {@code 07}, {@code 75}, {@code 99}, unknown codes or the return
 * URL. {@code eventReference} is the shared ingress receipt's delivery reference. Carries no raw payload or checksum.
 */
public record RecordDefinitePaymentFailureCommand(
        String merchantReference,
        String providerResponseCode,
        String providerTransactionStatus,
        String eventReference
) { }
