package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.web;

import edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.application.WorkshopBookingService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/workshop-bookings")
@RequiredArgsConstructor
public class BookingConfirmationApiController {

    private final WorkshopBookingService bookingService;

    @Data
    public static class EmailConfirmationRequest {
        private String confirmationToken;
    }

    @PostMapping("/{bookingCode}/email-confirmations")
    public ResponseEntity<Map<String, String>> confirmEmail(
            @PathVariable("bookingCode") String bookingCode,
            @RequestBody EmailConfirmationRequest request) {
        bookingService.confirmBookingEmail(bookingCode, request.getConfirmationToken());
        return ResponseEntity.ok(Map.of("message", "Booking email confirmed successfully"));
    }
}
