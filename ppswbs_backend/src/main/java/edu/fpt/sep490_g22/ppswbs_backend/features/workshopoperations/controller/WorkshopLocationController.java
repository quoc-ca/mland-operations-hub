package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.LocationRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.LocationResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.SlotRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.SlotResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopLocationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopSlotService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * T018: REST controller for Workshop Location and Location Slot endpoints.
 * Corresponds to OpenAPI paths:
 * - GET  /api/v1/workshop-locations
 * - POST /api/v1/workshop-locations
 * - GET  /api/v1/workshop-locations/{locationId}/slots
 * - POST /api/v1/workshop-locations/{locationId}/slots
 */
@RestController
@RequestMapping("/api/v1/workshop-locations")
@RequiredArgsConstructor
public class WorkshopLocationController {

    private final WorkshopLocationService locationService;
    private final WorkshopSlotService slotService;

    @GetMapping
    public ResponseEntity<List<LocationResponse>> listLocations(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        List<LocationResponse> response = locationService.listLocations(authHeader);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<LocationResponse> createLocation(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @Valid @RequestBody LocationRequest request) {
        LocationResponse response = locationService.createLocation(authHeader, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{locationId}/slots")
    public ResponseEntity<List<SlotResponse>> listLocationSlots(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable("locationId") Long locationId) {
        List<SlotResponse> response = slotService.listSlots(authHeader, locationId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{locationId}/slots")
    public ResponseEntity<SlotResponse> createSlot(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable("locationId") Long locationId,
            @Valid @RequestBody SlotRequest request) {
        SlotResponse response = slotService.createSlot(authHeader, locationId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
