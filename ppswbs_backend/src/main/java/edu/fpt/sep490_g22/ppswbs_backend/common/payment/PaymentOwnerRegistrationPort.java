package edu.fpt.sep490_g22.ppswbs_backend.common.payment;

/** Stable inbound port for registering an immutable payment owner. */
public interface PaymentOwnerRegistrationPort {
    record LockedRetailPayment(String checkoutReference, long amountVnd, String currencyCode, String providerCreateDate) { }

    /** Locks the same payment owner used by callback ingress for the caller's transaction. */
    java.util.Optional<LockedRetailPayment> lockRetailPayment(String merchantReference);
    void registerOwner(String merchantReference, String bookingReference, String paymentKind,
                       long amountVnd, String currencyCode);

    /** Registers the immutable VNPay PAY URL creation time required by QueryDR/refund. */
    default void registerOwner(String merchantReference, String bookingReference, String paymentKind,
                               long amountVnd, String currencyCode, String providerCreateDate) {
        registerOwner(merchantReference, bookingReference, paymentKind, amountVnd, currencyCode);
    }
}
