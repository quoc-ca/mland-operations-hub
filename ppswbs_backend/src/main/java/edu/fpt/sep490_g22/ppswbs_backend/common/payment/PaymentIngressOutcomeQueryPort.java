package edu.fpt.sep490_g22.ppswbs_backend.common.payment;

/** Read-only technical boundary for checking whether a verified terminal IPN was recorded. */
public interface PaymentIngressOutcomeQueryPort {
    boolean hasVerifiedTerminalOutcome(String merchantReference);
}
