package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.BookingSummaryResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.MagicLinkExchangeRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.MagicLinkService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public bearer exchange; the token is intentionally never logged or returned. */
@RestController
@RequestMapping("/api/v1/booking-magic-links")
@RequiredArgsConstructor
public class BookingMagicLinkExchangeController {

    private final MagicLinkService magicLinks;

    @PostMapping("/exchanges")
    public BookingSummaryResponse exchange(@Valid @RequestBody MagicLinkExchangeRequest request) {
        return magicLinks.exchange(request.getToken());
    }
}
