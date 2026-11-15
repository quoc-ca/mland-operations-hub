package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.GroupPaymentAttemptResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.GroupRecoveryDtos.ContactProof;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.BookingGroupRecoveryService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.GroupPaymentService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.GroupRecoveryRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Starts the single gateway payment of a booking group (003.1 FR-038, FR-048). */
@RestController
@RequestMapping("/api/v1/workshop-booking-groups/{groupCode}/payment-attempts")
@RequiredArgsConstructor
public class GroupPaymentController {

    private final GroupPaymentService paymentService;
    private final BookingGroupRecoveryService recovery;
    private final GroupRecoveryRateLimiter rateLimiter;

    /** A Guest sends exactly one contact value; an authenticated Member sends none (FR-048, CHK023). */
    @PostMapping
    public ResponseEntity<GroupPaymentAttemptResponse> initiate(
            @PathVariable String groupCode,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody(required = false) ContactProof proof,
            HttpServletRequest request) {
        BookingGroupRecoveryService.Actor actor = recovery.resolveActor(authorization);
        if (actor.anonymous()) rateLimiter.check("PAYMENT", groupCode, request.getRemoteAddr());
        String email = proof == null ? null : proof.contactEmail();
        String phone = proof == null ? null : proof.contactPhone();
        return ResponseEntity.ok(paymentService.initiate(groupCode, idempotencyKey, request.getRemoteAddr(),
                actor, email, phone));
    }
}
