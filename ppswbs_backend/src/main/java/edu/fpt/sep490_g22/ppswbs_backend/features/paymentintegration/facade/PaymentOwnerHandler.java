package edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.facade;

public interface PaymentOwnerHandler {
    PaymentOwnerKind supportedKind();
    void onPaymentSuccess(VerifiedPaymentSuccess payment);
}
