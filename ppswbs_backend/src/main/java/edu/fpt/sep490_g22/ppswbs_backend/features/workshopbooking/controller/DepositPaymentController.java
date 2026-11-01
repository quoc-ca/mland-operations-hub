package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.DepositPaymentAttemptResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.DepositPaymentService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class DepositPaymentController {
    private final DepositPaymentService payments;

    @PostMapping("/api/v1/workshop-bookings/{bookingCode}/payment-attempts")
    public ResponseEntity<DepositPaymentAttemptResponse> initiate(
            @PathVariable String bookingCode,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            HttpServletRequest request) {
        return ResponseEntity.ok(payments.initiate(bookingCode, idempotencyKey, request.getRemoteAddr()));
    }
}
