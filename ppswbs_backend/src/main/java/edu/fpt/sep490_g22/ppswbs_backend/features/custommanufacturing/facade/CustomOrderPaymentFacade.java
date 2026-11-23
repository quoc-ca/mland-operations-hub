package edu.fpt.sep490_g22.ppswbs_backend.features.custommanufacturing.facade;

public interface CustomOrderPaymentFacade {
    void applyVerifiedFullPayment(String customOrderReference, String merchantReference,
                                  String providerTransactionNumber, long amountVnd, String currencyCode);
}
