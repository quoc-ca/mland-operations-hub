package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.CustomerSessionAvailabilityResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.PackageLifecycleTransitionRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.PackageRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.PackageResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopPackageAuthorizationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopPackageDiscoveryService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopPackageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class WorkshopPackageController {

    private static final String CORRELATION_HEADER = "X-Correlation-Id";

    private final WorkshopPackageService packageService;
    private final WorkshopPackageDiscoveryService discoveryService;
    private final WorkshopPackageAuthorizationService packageAuthorization;

    @GetMapping("/workshop-packages")
    public List<PackageResponse> listPublishedPackages() {
        return packageService.listPublished();
    }

    @PostMapping("/workshop-packages")
    public ResponseEntity<PackageResponse> createDraft(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = CORRELATION_HEADER, required = false) String correlationReference,
            @Valid @RequestBody PackageRequest request) {
        Long actorId = packageAuthorization.requireCentralManager(authorization);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(packageService.createDraft(request, actorId, correlationReference));
    }

    @GetMapping("/workshop-packages/{packageId}")
    public PackageResponse getPublishedPackage(@PathVariable Long packageId) {
        return packageService.getPublished(packageId);
    }

    @PatchMapping("/workshop-packages/{packageId}")
    public PackageResponse updatePackageProspectively(
            @PathVariable Long packageId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = CORRELATION_HEADER, required = false) String correlationReference,
            @Valid @RequestBody PackageRequest request) {
        Long actorId = packageAuthorization.requireCentralManager(authorization);
        return packageService.updateProspectively(packageId, request, actorId, correlationReference);
    }

    @PostMapping("/workshop-packages/{packageId}/lifecycle-transitions")
    public PackageResponse transitionPackage(
            @PathVariable Long packageId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = CORRELATION_HEADER, required = false) String correlationReference,
            @Valid @RequestBody PackageLifecycleTransitionRequest request) {
        Long actorId = packageAuthorization.requireCentralManager(authorization);
        return packageService.transition(packageId, request.getTargetState(), actorId, correlationReference);
    }

    @GetMapping("/workshop-packages/{packageId}/sessions")
    public List<CustomerSessionAvailabilityResponse> discoverSessions(
            @PathVariable Long packageId,
            @RequestParam Long locationId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return discoveryService.discoverSessions(packageId, locationId, from, to);
    }
}
