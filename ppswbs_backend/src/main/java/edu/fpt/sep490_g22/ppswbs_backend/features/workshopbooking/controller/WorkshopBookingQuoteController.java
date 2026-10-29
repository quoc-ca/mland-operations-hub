package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.WorkshopBookingQuoteRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.WorkshopBookingQuoteResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.WorkshopBookingQuoteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/workshop-booking-quotes")
@RequiredArgsConstructor
public class WorkshopBookingQuoteController {
    private final WorkshopBookingQuoteService quoteService;

    @PostMapping
    public ResponseEntity<WorkshopBookingQuoteResponse> preview(
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody WorkshopBookingQuoteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(quoteService.preview(authorization, request));
    }
}
