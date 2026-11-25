package edu.fpt.sep490_g22.ppswbs_backend.features.custommanufacturing.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.facade.PaymentOwnerHandler;
import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.facade.PaymentOwnerKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.facade.VerifiedPaymentSuccess;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CustomOrderPaymentHandler implements PaymentOwnerHandler {

    @Lazy
    private final ObjectProvider<CustomOrderService> customOrderServiceProvider;

    @Override
    public PaymentOwnerKind supportedKind() {
        return PaymentOwnerKind.CUSTOM_ORDER_FULL_PAYMENT;
    }

    @Override
    public void onPaymentSuccess(VerifiedPaymentSuccess payment) {
        CustomOrderService service = customOrderServiceProvider.getIfAvailable();
        if (service != null) {
            service.applyVerifiedFullPayment(
                    payment.ownerReference(),
                    payment.paymentReference(),
                    payment.gatewayTransactionReference(),
                    payment.amountVnd(),
                    payment.currencyCode()
            );
        }
    }
}
