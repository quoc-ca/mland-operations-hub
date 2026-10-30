package edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.facade;

import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.entity.PaymentOwnerKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.entity.PaymentOwnerRegistration;
import java.util.Optional;
import edu.fpt.sep490_g22.ppswbs_backend.common.payment.PaymentOwnerRegistrationPort;

public interface PaymentOwnerRegistry extends PaymentOwnerRegistrationPort {
    PaymentOwnerRegistration register(String merchantReference, String bookingReference, PaymentOwnerKind kind,
                                      long amountVnd, String currencyCode);
    Optional<PaymentOwnerRegistration> resolve(String merchantReference);

    @Override
    default void registerOwner(String merchantReference, String bookingReference, String paymentKind,
                                long amountVnd, String currencyCode) {
        register(merchantReference, bookingReference, PaymentOwnerKind.valueOf(paymentKind), amountVnd, currencyCode);
    }
}
