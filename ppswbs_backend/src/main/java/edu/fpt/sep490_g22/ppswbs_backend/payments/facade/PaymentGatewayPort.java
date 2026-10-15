package edu.fpt.sep490_g22.ppswbs_backend.payments.facade;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Port for the payment gateway adapter.
 * VNPay SDK details are hidden behind this interface.
 * No OTP, signed payloads or provider secrets cross this boundary.
 */
public interface PaymentGatewayPort {

    /**
     * Initiate a payment for the given amount/currency and return a gateway URL.
     * Link is valid for at most 10 minutes.
     *
     * @param request initiation request with booking reference, amount and currency.
     * @return signed gateway URL and expiry.
     */
    PaymentInitiationResult initiate(PaymentInitiationRequest request);

    /**
     * Verify a provider callback result: signature, amount, currency, and reference.
     *
     * @param callback raw callback data to verify.
     * @return verified result; outcome is FAILED if signature/amount/currency mismatches.
     */
    VerifiedCallbackResult verifyCallback(RawProviderCallback callback);

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class PaymentInitiationRequest {
        private String bookingCode;
        private String providerAttemptReference;
        private long amount;
        private String currency;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class PaymentInitiationResult {
        private String paymentUrl;
        private Instant expiresAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class RawProviderCallback {
        /** Provider-assigned event identifier for deduplication. */
        private String providerEventId;
        private String providerReference;
        private String bookingCode;
        /** Raw amount from provider. */
        private long amount;
        private String currency;
        /** SUCCESS, FAILED, CANCELLED, PENDING, UNKNOWN */
        private String rawOutcome;
        /** Provider signature for verification; never logged. */
        private String signature;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class VerifiedCallbackResult {
        private String providerEventId;
        private String providerReference;
        private String bookingCode;
        private long amount;
        private String currency;
        /** Normalized outcome: SUCCESS, FAILED, CANCELLED */
        private String outcome;
        private boolean signatureValid;
    }
}
