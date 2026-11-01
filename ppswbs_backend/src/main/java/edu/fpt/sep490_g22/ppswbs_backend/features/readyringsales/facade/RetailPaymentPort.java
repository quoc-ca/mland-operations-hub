package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.facade;

import java.math.BigDecimal;

public interface RetailPaymentPort {
    RetailCheckoutPorts.PaymentInitiation initiate(String checkoutId, BigDecimal amount);
}
