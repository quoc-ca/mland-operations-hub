package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.AddOnLifecycleTransitionRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.AddOnResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.AddOnWrite;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopAddOnService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopPackageAuthorizationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/workshop-packages/{packageId}/locations/{locationId}/add-ons")
public class WorkshopAddOnController {
    private final WorkshopAddOnService service;
    private final WorkshopPackageAuthorizationService authorization;
    private final edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.OperationsRestIdempotencyService restIdempotency;

    public WorkshopAddOnController(WorkshopAddOnService service,
            WorkshopPackageAuthorizationService authorization) {
        this(service, authorization, null);
    }

    @Autowired
    public WorkshopAddOnController(WorkshopAddOnService service,
            WorkshopPackageAuthorizationService authorization,
            edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.OperationsRestIdempotencyService restIdempotency) {
        this.service = service;
        this.authorization = authorization;
        this.restIdempotency = restIdempotency;
    }

    private <T> T execute(String key, String action, String scope, Object command,
            Class<T> responseType, java.util.function.Supplier<T> operation) {
        if (restIdempotency == null) {
            return operation.get();
        }
        return restIdempotency.execute(key, action, scope, command, responseType, operation);
    }

    @GetMapping
    public List<AddOnResponse> list(@PathVariable Long packageId, @PathVariable Long locationId) {
        return service.listCustomerSelectable(packageId, locationId);
    }

    @PostMapping
    public ResponseEntity<AddOnResponse> create(@PathVariable Long packageId, @PathVariable Long locationId,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @RequestHeader(value = "Idempotency-Key", required = true) String idempotencyKey,
            @Valid @RequestBody AddOnWrite request) {
        Long actorId = authorization.requireActiveManager(authorizationHeader);
        return ResponseEntity.status(HttpStatus.CREATED).body(execute(idempotencyKey,
                "REST:POST:/api/v1/workshop-packages/{packageId}/locations/{locationId}/add-ons",
                "addon:create:" + packageId + ":" + locationId, request, AddOnResponse.class,
                () -> service.createDraft(packageId, locationId, request, actorId, correlationId)));
    }

    @PatchMapping("/{addOnReference}")
    public AddOnResponse update(@PathVariable Long packageId, @PathVariable Long locationId,
            @PathVariable String addOnReference,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @RequestHeader(value = "Idempotency-Key", required = true) String idempotencyKey,
            @Valid @RequestBody AddOnWrite request) {
        Long actorId = authorization.requireActiveManager(authorizationHeader);
        return execute(idempotencyKey, "REST:PATCH:/api/v1/workshop-packages/{packageId}/locations/{locationId}/add-ons/{addOnReference}",
                "addon:update:" + addOnReference, request, AddOnResponse.class,
                () -> service.updateProspectively(packageId, locationId, addOnReference, request, actorId, correlationId));
    }

    @PostMapping("/{addOnReference}/lifecycle-transitions")
    public AddOnResponse transition(@PathVariable Long packageId, @PathVariable Long locationId,
            @PathVariable String addOnReference,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @RequestHeader(value = "Idempotency-Key", required = true) String idempotencyKey,
            @Valid @RequestBody AddOnLifecycleTransitionRequest request) {
        Long actorId = authorization.requireActiveManager(authorizationHeader);
        return execute(idempotencyKey, "REST:POST:/api/v1/workshop-packages/{packageId}/locations/{locationId}/add-ons/{addOnReference}/lifecycle-transitions",
                "addon:transition:" + addOnReference, request, AddOnResponse.class,
                () -> service.transition(packageId, locationId, addOnReference, request, actorId, correlationId));
    }
}
