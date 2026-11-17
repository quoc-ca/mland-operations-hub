package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.WorkshopCartQuoteRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.WorkshopCartQuoteResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.WorkshopCartQuoteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Quotes a multi-ticket cart for a Guest or a Member (003.1 T092, FR-030). Holds no capacity. */
@RestController
@RequestMapping("/api/v1/workshop-cart-quotes")
@RequiredArgsConstructor
public class WorkshopCartQuoteController {

    private final WorkshopCartQuoteService cartQuoteService;

    @PostMapping
    public ResponseEntity<WorkshopCartQuoteResponse> quote(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody WorkshopCartQuoteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cartQuoteService.quote(authorization, request));
    }
}
