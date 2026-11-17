package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dto.CheckoutDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.RetailActorResolver;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.RetailCheckoutService;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.RetailMemberActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.RetailPaymentService;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.RetailCheckoutQuotePreviewService;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.RetailCheckoutAcceptanceService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class CheckoutController {
    private final RetailActorResolver actorResolver;
    private final RetailCheckoutService checkoutService;
    private final RetailPaymentService paymentService;
    private final ObjectProvider<RetailCheckoutQuotePreviewService> quotePreview;
    private final ObjectProvider<RetailCheckoutAcceptanceService> acceptance;
    @Value("${retail.checkout.test-acceptance-enabled:false}")
    private boolean testAcceptanceEnabled;

    @PostMapping("/api/v1/ready-ring-benefit-previews")
    public CheckoutDtos.BenefitPreviewView previewBenefits(HttpServletRequest request, @Valid @RequestBody CheckoutDtos.BenefitPreviewRequest body) {
        RetailMemberActor actor = actorResolver.requireActiveMember(request);
        var realPreview = quotePreview.getIfAvailable();
        return realPreview == null ? checkoutService.previewBenefits(actor.accountId(), body)
            : realPreview.preview(actor.accountId(), body);
    }

    @PostMapping("/api/v1/ready-ring-shipping-quotes")
    public CheckoutDtos.ShippingQuoteView quoteShipping(HttpServletRequest request, @Valid @RequestBody CheckoutDtos.ShippingQuoteRequest body) {
        RetailMemberActor actor = actorResolver.requireActiveMember(request);
        var realPreview = quotePreview.getIfAvailable();
        return realPreview == null ? checkoutService.quoteShipping(actor.accountId(), body)
            : realPreview.quoteShipping(actor.accountId(), body);
    }

    @PostMapping("/api/v1/ready-ring-orders")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.CREATED)
    public CheckoutDtos.GroupedCheckoutView createCheckout(HttpServletRequest request,
                                                            @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                            @Valid @RequestBody CheckoutDtos.CreateGroupedCheckoutRequest body) {
        RetailMemberActor actor = actorResolver.requireActiveMember(request);
        // Real acceptance (FE-06 reserve, FE-02 holds) when enabled; the local profile has no such bean and keeps its
        // simple path, so the same .env works in both profiles.
        var realAcceptance = testAcceptanceEnabled ? acceptance.getIfAvailable() : null;
        if (realAcceptance != null) return realAcceptance.accept(actor.accountId(), idempotencyKey, body);
        return checkoutService.createCheckout(actor.accountId(), idempotencyKey, body);
    }

    @PostMapping("/api/v1/ready-ring-checkouts/{checkoutId}/payment")
    public CheckoutDtos.PaymentRedirectView initiatePayment(HttpServletRequest request, @PathVariable String checkoutId) {
        RetailMemberActor actor = actorResolver.requireActiveMember(request);
        return paymentService.initiate(actor.accountId(), checkoutId);
    }
}
