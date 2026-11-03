package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.AddOnLifecycleTransitionRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.AddOnResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.AddOnWrite;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopAddOnService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopPackageAuthorizationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/workshop-packages/{packageId}/locations/{locationId}/add-ons")
@RequiredArgsConstructor
public class WorkshopAddOnController {
    private final WorkshopAddOnService service;
    private final WorkshopPackageAuthorizationService authorization;

    @GetMapping
    public List<AddOnResponse> list(@PathVariable Long packageId, @PathVariable Long locationId) {
        return service.listCustomerSelectable(packageId, locationId);
    }

    @PostMapping
    public ResponseEntity<AddOnResponse> create(@PathVariable Long packageId, @PathVariable Long locationId,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @Valid @RequestBody AddOnWrite request) {
        Long actorId = authorization.requireActiveManager(authorizationHeader);
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createDraft(packageId, locationId, request, actorId, correlationId));
    }

    @PatchMapping("/{addOnReference}")
    public AddOnResponse update(@PathVariable Long packageId, @PathVariable Long locationId,
            @PathVariable String addOnReference,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @Valid @RequestBody AddOnWrite request) {
        Long actorId = authorization.requireActiveManager(authorizationHeader);
        return service.updateProspectively(packageId, locationId, addOnReference, request, actorId, correlationId);
    }

    @PostMapping("/{addOnReference}/lifecycle-transitions")
    public AddOnResponse transition(@PathVariable Long packageId, @PathVariable Long locationId,
            @PathVariable String addOnReference,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @Valid @RequestBody AddOnLifecycleTransitionRequest request) {
        Long actorId = authorization.requireActiveManager(authorizationHeader);
        return service.transition(packageId, locationId, addOnReference, request, actorId, correlationId);
    }
}
