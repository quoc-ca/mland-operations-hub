package edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.facade;

import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.entity.PaymentOwnerKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.entity.PaymentOwnerRegistration;
import java.util.Optional;

public interface PaymentOwnerRegistry {
    PaymentOwnerRegistration register(String merchantReference, String bookingReference, PaymentOwnerKind kind,
                                      long amountVnd, String currencyCode);
    Optional<PaymentOwnerRegistration> resolve(String merchantReference);
}
