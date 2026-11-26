package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.PackageAvailabilityDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.OperationsDeniedAttemptRecorder;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.OperationsRestIdempotencyService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopPackageAuthorizationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopPackageAvailabilityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 003.2 FR-038: the Package x location matrix. Active Manager only; Staff, Admin and every other role get 403 with
 * a denied-attempt audit event (T131), a missing or invalid token 401, an unavailable Account facade 503.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class WorkshopPackageAvailabilityController {

    private static final String CORRELATION_HEADER = "X-Correlation-Id";

    private final WorkshopPackageAvailabilityService availabilityService;
    private final WorkshopPackageAuthorizationService packageAuthorization;
    private final OperationsDeniedAttemptRecorder deniedAttemptRecorder;
    private final OperationsRestIdempotencyService restIdempotency;

    @GetMapping("/workshop-package-availability")
    public PackageAvailabilityDtos.Matrix getMatrix(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = CORRELATION_HEADER, required = false) String correlationReference) {
        Long actorId = deniedAttemptRecorder.guard("PACKAGE_AVAILABILITY_MATRIX_READ", null, authorization,
                correlationReference, () -> packageAuthorization.requireActiveManager(authorization));
        return availabilityService.readMatrix(actorId);
    }

    @PutMapping("/workshop-packages/{packageId}/locations/{locationId}/availability")
    public PackageAvailabilityDtos.Cell putAvailability(
            @PathVariable Long packageId,
            @PathVariable Long locationId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = CORRELATION_HEADER, required = false) String correlationReference,
            @RequestHeader(value = "Idempotency-Key", required = true) String idempotencyKey,
            @Valid @RequestBody PackageAvailabilityDtos.Write request) {
        Long actorId = deniedAttemptRecorder.guard(AUDIT_DENIED_ACTION, null, authorization, correlationReference,
                () -> packageAuthorization.requireActiveManager(authorization));
        return restIdempotency.execute(OperationsRestIdempotencyService.AUTHORIZED_BY_CALLER, idempotencyKey,
                "REST:PUT:/api/v1/workshop-packages/{packageId}/locations/{locationId}/availability",
                "package-availability:" + packageId + ":" + locationId, request, PackageAvailabilityDtos.Cell.class,
                () -> availabilityService.setAvailability(actorId, packageId, locationId, request.state(),
                        request.version(), correlationReference));
    }

    private static final String AUDIT_DENIED_ACTION = WorkshopPackageAvailabilityService.AUDIT_ACTION;
}
