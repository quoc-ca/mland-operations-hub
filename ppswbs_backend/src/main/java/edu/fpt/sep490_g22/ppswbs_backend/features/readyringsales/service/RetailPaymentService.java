package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dto.CheckoutDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.GroupedCheckout;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.PaymentState;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.facade.RetailCheckoutPorts;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.facade.RetailPaymentPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.ObjectProvider;

@Service
@RequiredArgsConstructor
public class RetailPaymentService {
    private final RetailCheckoutService checkoutService;
    private final RetailPaymentPort paymentPort;
    private final ObjectProvider<RetailUnstartedExpiryService> expiryService;
    private final RetailShipmentCreationService shipmentCreation;

    @Transactional
    public CheckoutDtos.PaymentRedirectView initiate(Long memberAccountId, String checkoutId) {
        GroupedCheckout checkout = checkoutService.lockOwnedCheckout(memberAccountId, checkoutId);
        if (checkout.getPaymentState() == PaymentState.PAID) {
            return new CheckoutDtos.PaymentRedirectView(checkoutId, null, null, PaymentState.PAID.name());
        }
        // The Member left VNPay without paying: "Thanh toán" reopens the same link while the hold lasts
        // (Session 2026-11-18), so one checkout never has two VNPay transactions.
        String reusable = checkout.isCounterCash() ? null : checkout.reusablePaymentUrl(java.time.Instant.now());
        if (reusable != null) {
            return new CheckoutDtos.PaymentRedirectView(checkoutId, reusable, checkout.getPaymentUrlExpiresAt().toString(),
                checkout.getPaymentState().name());
        }
        if (checkout.getPaymentState() != PaymentState.ACCEPTED_UNPAID) {
            throw new RetailConflictException("Payment cannot be initiated in the current checkout state");
        }
        if (checkout.isCounterCash()) {
            throw new RetailConflictException("This checkout is paid in cash at the branch counter");
        }
        var expiry = expiryService.getIfAvailable();
        if (expiry != null) expiry.requirePaymentBeforeDeadline(checkoutId);
        checkout.beginPayment();
        RetailCheckoutPorts.PaymentInitiation initiation = paymentPort.initiate(checkout.getId(), checkout.getPayableAmount());
        if (initiation.paymentUrl() != null) checkout.rememberPaymentLink(initiation.paymentUrl(), initiation.expiresAt());
        if (initiation.locallyVerified()) {
            checkout.markPaid();
            shipmentCreation.createShipmentsForPaidCheckout(checkout);
        }
        return new CheckoutDtos.PaymentRedirectView(checkout.getId(), initiation.paymentUrl(), initiation.expiresAt().toString(), checkout.getPaymentState().name());
    }
}
