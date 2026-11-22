package edu.fpt.sep490_g22.ppswbs_backend.features.custommanufacturing.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.custommanufacturing.facade.CustomOrderPaymentFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CustomOrderPaymentFacadeImpl implements CustomOrderPaymentFacade {

    private final CustomOrderService customOrderService;

    @Override
    public void applyVerifiedFullPayment(
            String customOrderReference,
            String merchantReference,
            String providerTransactionNumber,
            long amountVnd,
            String currencyCode
    ) {
        customOrderService.applyVerifiedFullPayment(
                customOrderReference,
                merchantReference,
                providerTransactionNumber,
                amountVnd,
                currencyCode
        );
    }
}
