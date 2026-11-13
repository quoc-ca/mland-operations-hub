package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.GroupPaymentAttemptResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.GroupPaymentService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Starts the single gateway payment of a booking group (003.1 FR-038). */
@RestController
@RequestMapping("/api/v1/workshop-booking-groups/{groupCode}/payment-attempts")
@RequiredArgsConstructor
public class GroupPaymentController {

    private final GroupPaymentService paymentService;

    @PostMapping
    public ResponseEntity<GroupPaymentAttemptResponse> initiate(
            @PathVariable String groupCode,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            HttpServletRequest request) {
        return ResponseEntity.ok(paymentService.initiate(groupCode, idempotencyKey, request.getRemoteAddr()));
    }
}
