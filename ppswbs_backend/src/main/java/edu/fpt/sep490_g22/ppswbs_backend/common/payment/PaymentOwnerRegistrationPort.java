package edu.fpt.sep490_g22.ppswbs_backend.common.payment;

/** Stable inbound port for registering an immutable payment owner. */
public interface PaymentOwnerRegistrationPort {
    void registerOwner(String merchantReference, String bookingReference, String paymentKind,
                       long amountVnd, String currencyCode);

    /** Registers the immutable VNPay PAY URL creation time required by QueryDR/refund. */
    default void registerOwner(String merchantReference, String bookingReference, String paymentKind,
                               long amountVnd, String currencyCode, String providerCreateDate) {
        registerOwner(merchantReference, bookingReference, paymentKind, amountVnd, currencyCode);
    }
}
