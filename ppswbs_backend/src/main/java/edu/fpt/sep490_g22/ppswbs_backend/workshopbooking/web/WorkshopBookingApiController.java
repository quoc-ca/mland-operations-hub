package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.web;

import edu.fpt.sep490_g22.ppswbs_backend.configuration.FirebaseAuthenticationToken;
import edu.fpt.sep490_g22.ppswbs_backend.common.InvalidRequestException;
import edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.application.BookingLookupResult;
import edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.application.CreateBookingCommand;
import edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.application.WorkshopBookingService;
import edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.domain.WorkshopBooking;
import jakarta.validation.Valid;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/workshop-bookings")
@RequiredArgsConstructor
public class WorkshopBookingApiController {

    private final WorkshopBookingService bookingService;
    private final edu.fpt.sep490_g22.ppswbs_backend.members.facade.MemberFacade memberFacade;

    @PostMapping
    public ResponseEntity<BookingSummaryResponse> createBooking(
            @Valid @RequestBody CreateBookingCommand command,
            Authentication authentication) {

        WorkshopBooking booking;
        if (authentication instanceof FirebaseAuthenticationToken token) {
            booking = bookingService.createMemberBooking(command, token.getUid());
        } else {
            booking = bookingService.createGuestBooking(command);
        }

        // Newly created booking has hold expiring in 15 mins
        Instant expiresAt = Instant.now().plusSeconds(15 * 60);

        BookingSummaryResponse response = BookingSummaryResponse.builder()
                .bookingCode(booking.getBookingCode())
                .bookingStatus(booking.getBookingStatus().name())
                .paymentStatus(booking.getPaymentStatus().name())
                .holdExpiresAt(expiresAt)
                .paymentResumeAllowed(true)
                .build();

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{bookingCode}")
    public ResponseEntity<BookingSummaryResponse> lookupBooking(
            @PathVariable("bookingCode") String bookingCode,
            @RequestParam(value = "contactPhone", required = false) String contactPhone,
            @RequestParam(value = "contactEmail", required = false) String contactEmail,
            Authentication authentication) {

        Long memberId = null;
        if (authentication instanceof FirebaseAuthenticationToken token) {
            memberId = memberFacade.getMemberByUid(token.getUid())
                    .map(edu.fpt.sep490_g22.ppswbs_backend.members.facade.MemberDto::getId)
                    .orElse(null);
        }

        // For simplicity in this controller, Guest validation:
        if (authentication == null) {
            if ((contactPhone == null || contactPhone.isBlank()) && (contactEmail == null || contactEmail.isBlank())) {
                throw new InvalidRequestException("Either contactPhone or contactEmail must be provided for Guest lookup");
            }
            if (contactPhone != null && !contactPhone.isBlank() && contactEmail != null && !contactEmail.isBlank()) {
                throw new InvalidRequestException("Provide exactly one of contactPhone or contactEmail for Guest lookup");
            }
        }

        BookingLookupResult result = bookingService.lookupBooking(bookingCode, memberId, contactPhone, contactEmail);

        BookingSummaryResponse response = BookingSummaryResponse.builder()
                .bookingCode(result.getBooking().getBookingCode())
                .bookingStatus(result.getBooking().getBookingStatus().name())
                .paymentStatus(result.getBooking().getPaymentStatus().name())
                .holdExpiresAt(result.getHoldExpiresAt())
                .qrTicket(result.getTicket() != null ? result.getTicket().getQrValue() : null)
                .paymentResumeAllowed(result.isPaymentResumeAllowed())
                .build();

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{bookingCode}/release")
    public ResponseEntity<Map<String, String>> releaseHold(
            @PathVariable("bookingCode") String bookingCode,
            @RequestParam(value = "contactPhone", required = false) String contactPhone,
            @RequestParam(value = "contactEmail", required = false) String contactEmail,
            Authentication authentication) {

        Long memberId = null;
        if (authentication instanceof FirebaseAuthenticationToken token) {
            memberId = memberFacade.getMemberByUid(token.getUid())
                    .map(edu.fpt.sep490_g22.ppswbs_backend.members.facade.MemberDto::getId)
                    .orElse(null);
        }

        bookingService.releaseHold(bookingCode, memberId, contactPhone, contactEmail);
        return ResponseEntity.ok(Map.of("message", "Booking hold released successfully"));
    }

    @Data
    @Builder
    public static class BookingSummaryResponse {
        private String bookingCode;
        private String bookingStatus;
        private String paymentStatus;
        private Instant holdExpiresAt;
        private String qrTicket;
        private boolean paymentResumeAllowed;
    }
}
