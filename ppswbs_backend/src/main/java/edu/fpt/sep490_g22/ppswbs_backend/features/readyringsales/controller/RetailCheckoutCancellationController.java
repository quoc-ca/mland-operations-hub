package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dto.CheckoutDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.RetailActorResolver;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.RetailCheckoutCancellationService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("!local")
@RequiredArgsConstructor
public class RetailCheckoutCancellationController {
    private final RetailActorResolver actors;
    private final RetailCheckoutCancellationService cancellation;

    @PostMapping("/api/v1/ready-ring-checkouts/{checkoutId}/cancellations")
    public CheckoutDtos.CancellationView cancel(HttpServletRequest request, @PathVariable String checkoutId,
                                                @RequestHeader("Idempotency-Key") String key) {
        if (key.isBlank() || key.length() > 128) throw new edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.RetailConflictException("A valid Idempotency-Key is required");
        return cancellation.cancel(actors.requireActiveMember(request).accountId(), checkoutId);
    }
}
