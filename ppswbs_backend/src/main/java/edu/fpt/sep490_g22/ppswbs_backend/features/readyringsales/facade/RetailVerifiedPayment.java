package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.facade;

public record RetailVerifiedPayment(String checkoutId, String merchantReference,
                                    String providerTransaction, long amountVnd, String currency) { }
