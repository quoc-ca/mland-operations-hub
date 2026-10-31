package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dto.CheckoutDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.LocalCheckoutCancellationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.RetailActorResolver;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("local")
@RequiredArgsConstructor
public class LocalCheckoutCancellationController {
    private final RetailActorResolver actors;
    private final LocalCheckoutCancellationService cancellation;

    @PostMapping("/api/v1/ready-ring-checkouts/{checkoutId}/cancellations")
    public CheckoutDtos.GroupedCheckoutView cancel(HttpServletRequest request, @PathVariable String checkoutId) {
        return cancellation.cancelAsDemoExpiry(actors.requireActiveMember(request).accountId(), checkoutId);
    }
}
