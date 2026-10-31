package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.BookingSummaryResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.GuestLookupRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.BookingLookupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Customer booking projections after Guest contact proof or Member ownership is verified. */
@RestController
@RequiredArgsConstructor
public class BookingLookupController {

    private final BookingLookupService lookupService;

    @PostMapping("/api/v1/booking-lookups")
    public BookingSummaryResponse lookupGuest(@Valid @RequestBody GuestLookupRequest request) {
        return lookupService.lookupGuest(request);
    }

    @GetMapping("/api/v1/members/me/workshop-bookings/{bookingCode}")
    public BookingSummaryResponse lookupMember(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String bookingCode) {
        return lookupService.lookupMember(authorization, bookingCode);
    }
}
