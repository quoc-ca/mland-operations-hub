package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.controller;

import com.fasterxml.jackson.databind.JsonNode;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dto.LoyaltyScalarCodec;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dto.PromotionReadDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dto.PromotionUsageDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dto.PromotionWriteDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.LoyaltyAuthorization;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.LoyaltyAuthorization.Capability;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.PromotionCreationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.PromotionMaintenanceService;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.PromotionManagementQueryService;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.PromotionUsageQueryService;
import org.springframework.http.CacheControl;
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

import java.util.UUID;

/**
 * Programme management API for Managers (T029): createPromotionProgram, listPromotionPrograms and
 * getPromotionProgram of the OpenAPI contract. Every call authorizes the current Manager first; creation needs an
 * {@code Idempotency-Key} and answers 201 only after commit, also on a same-key replay. Responses are never cached.
 * US2 adds edit (PATCH), pause, resume and usage (T046). No lifecycle effect is reachable over HTTP.
 */
@RestController
@RequestMapping("/api/v1/promotion-programs")
class PromotionManagementApiController {

    private final LoyaltyAuthorization authorization;
    private final PromotionCreationService creation;
    private final PromotionManagementQueryService query;
    private final PromotionMaintenanceService maintenance;
    private final PromotionUsageQueryService usageQuery;

    PromotionManagementApiController(LoyaltyAuthorization authorization, PromotionCreationService creation,
                                     PromotionManagementQueryService query, PromotionMaintenanceService maintenance,
                                     PromotionUsageQueryService usageQuery) {
        this.authorization = authorization;
        this.creation = creation;
        this.query = query;
        this.maintenance = maintenance;
        this.usageQuery = usageQuery;
    }

    @PostMapping
    ResponseEntity<PromotionReadDtos.Program> create(@RequestHeader("Idempotency-Key") UUID key,
                                                     @RequestBody JsonNode body) {
        String manager = authorization.requireManager(Capability.MANAGE_PROGRAMS);
        long programId = creation.create(manager, key, PromotionWriteDtos.parseCreate(body));
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(query.get(programId));
    }

    @GetMapping
    ResponseEntity<PromotionReadDtos.ProgramPage> list(@RequestParam(required = false) Integer limit,
                                                       @RequestParam(required = false) String cursor,
                                                       @RequestParam(required = false) String scope,
                                                       @RequestParam(required = false) String status) {
        authorization.requireManager(Capability.MANAGE_PROGRAMS);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(query.list(limit, cursor, scope, status));
    }

    @GetMapping("/{programId}")
    ResponseEntity<PromotionReadDtos.Program> get(@PathVariable String programId) {
        authorization.requireManager(Capability.MANAGE_PROGRAMS);
        long id = LoyaltyScalarCodec.parseId("programId", programId);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(query.get(id));
    }

    @PatchMapping("/{programId}")
    ResponseEntity<PromotionReadDtos.Program> update(@PathVariable String programId,
                                                     @RequestHeader("Idempotency-Key") UUID key,
                                                     @RequestBody JsonNode body) {
        String manager = authorization.requireManager(Capability.MANAGE_PROGRAMS);
        long id = LoyaltyScalarCodec.parseId("programId", programId);
        maintenance.update(manager, key, id, PromotionWriteDtos.parseUpdate(body));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(query.get(id));
    }

    @PostMapping("/{programId}/pauses")
    ResponseEntity<PromotionReadDtos.Program> pause(@PathVariable String programId,
                                                    @RequestHeader("Idempotency-Key") UUID key,
                                                    @RequestBody JsonNode body) {
        String manager = authorization.requireManager(Capability.MANAGE_PROGRAMS);
        long id = LoyaltyScalarCodec.parseId("programId", programId);
        maintenance.pause(manager, key, id, PromotionWriteDtos.parseExpectedVersion(body));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(query.get(id));
    }

    @PostMapping("/{programId}/resumptions")
    ResponseEntity<PromotionReadDtos.Program> resume(@PathVariable String programId,
                                                     @RequestHeader("Idempotency-Key") UUID key,
                                                     @RequestBody JsonNode body) {
        String manager = authorization.requireManager(Capability.MANAGE_PROGRAMS);
        long id = LoyaltyScalarCodec.parseId("programId", programId);
        maintenance.resume(manager, key, id, PromotionWriteDtos.parseExpectedVersion(body));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(query.get(id));
    }

    @GetMapping("/{programId}/usage")
    ResponseEntity<PromotionUsageDtos.Summary> usage(@PathVariable String programId) {
        authorization.requireManager(Capability.MANAGE_PROGRAMS);
        long id = LoyaltyScalarCodec.parseId("programId", programId);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(usageQuery.usage(id));
    }
}
