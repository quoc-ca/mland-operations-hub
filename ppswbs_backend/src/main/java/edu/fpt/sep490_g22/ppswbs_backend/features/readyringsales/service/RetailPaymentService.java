package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dto.CheckoutDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.GroupedCheckout;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.PaymentState;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.facade.RetailCheckoutPorts;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.facade.RetailPaymentPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RetailPaymentService {
    private final RetailCheckoutService checkoutService;
    private final RetailPaymentPort paymentPort;

    @Transactional
    public CheckoutDtos.PaymentRedirectView initiate(Long memberAccountId, String checkoutId) {
        GroupedCheckout checkout = checkoutService.requireOwnedCheckout(memberAccountId, checkoutId);
        if (checkout.getPaymentState() == PaymentState.PAID) {
            return new CheckoutDtos.PaymentRedirectView(checkoutId, null, null, PaymentState.PAID.name());
        }
        if (checkout.getPaymentState() != PaymentState.ACCEPTED_UNPAID) {
            throw new RetailConflictException("Payment cannot be initiated in the current checkout state");
        }
        checkout.beginPayment();
        RetailCheckoutPorts.PaymentInitiation initiation = paymentPort.initiate(checkout.getId(), checkout.getPayableAmount());
        if (initiation.locallyVerified()) checkout.markPaid();
        return new CheckoutDtos.PaymentRedirectView(checkout.getId(), initiation.paymentUrl(), initiation.expiresAt().toString(), checkout.getPaymentState().name());
    }
}
