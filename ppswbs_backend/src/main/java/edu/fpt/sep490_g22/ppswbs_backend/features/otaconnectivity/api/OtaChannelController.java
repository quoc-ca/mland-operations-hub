package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.api;

import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaReadinessCheck;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaReadinessCheckOutcome;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class OtaChannelController {
    private final OtaAuthorizationService authorization;
    private final OtaChannelManagementService management;
    private final OtaSalesControlService salesControl;

    @GetMapping("/ota-channels")
    public OtaChannelViews.Channels list(@RequestHeader(value = "Authorization", required = false) String token) {
        return management.list(authorization.requireChannelView(token));
    }

    @PostMapping(value = "/ota-channels/{channelId}/mappings", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public OtaChannelViews.Mapping create(@PathVariable String channelId,
            @RequestHeader(value = "Authorization", required = false) String token,
            @Valid @RequestBody OtaChannelRequests.MappingUpsert body) {
        var actor = authorization.requireMappingAdministration(token);
        return management.create(channelId, body.providerProductReference(), body.providerOptionReference(),
                body.workshopOfferReference(), body.sessionReference(), actor);
    }

    @PutMapping(value = "/ota-channel-mappings/{mappingId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public OtaChannelViews.Mapping update(@PathVariable String mappingId,
            @RequestHeader(value = "Authorization", required = false) String token,
            @Valid @RequestBody OtaChannelRequests.MappingUpdate body) {
        var actor = authorization.requireManager(token);
        return management.update(mappingId, body.providerProductReference(), body.providerOptionReference(),
                body.workshopOfferReference(), body.sessionReference(), body.version(), actor);
    }

    @PostMapping(value = "/ota-channel-mappings/{mappingId}/price-terms", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public OtaChannelViews.PriceTerms price(@PathVariable String mappingId,
            @RequestHeader(value = "Authorization", required = false) String token,
            @Valid @RequestBody OtaChannelRequests.PriceTerms body) {
        var actor = authorization.requireManager(token);
        return management.recordPrice(mappingId, body.retailAmount().amount(), body.retailAmount().currency(),
                body.targetNetAmount().amount(), body.targetNetAmount().currency(),
                body.providerConfirmationEvidenceReference(), body.effectiveFrom(), body.effectiveTo(), body.reason(), actor);
    }

    @PostMapping(value = "/ota-channel-mappings/{mappingId}/sync", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public OtaOperationAccepted sync(@PathVariable String mappingId,
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody OtaChannelRequests.Sync body) {
        var actor = authorization.requireManagerOrAdmin(token);
        return management.sync(mappingId, body.mode(), body.reason(), key, actor);
    }

    @PostMapping(value = "/ota-channel-mappings/{mappingId}/sales-close", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public OtaOperationAccepted closeSales(@PathVariable String mappingId,
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody OtaChannelRequests.Reason body) {
        var actor = authorization.requireManager(token);
        return salesControl.requestSalesClose(OtaChannelManagementService.publicReference(mappingId), body.reason(), key, actor);
    }

    @PostMapping(value = "/ota-channel-mappings/{mappingId}/booking-enable", consumes = MediaType.APPLICATION_JSON_VALUE)
    public OtaBookingEnableDecision enable(@PathVariable String mappingId,
            @RequestHeader(value = "Authorization", required = false) String token,
            @Valid @RequestBody OtaChannelRequests.BookingEnable body) {
        var actor = authorization.requireAdmin(token);
        var checks = body.checks().stream().map(check -> new OtaReadinessCheckSubmission(
                OtaReadinessCheck.valueOf(check.check()), OtaReadinessCheckOutcome.valueOf(check.outcome()), check.evidenceReference())).toList();
        return management.enable(mappingId, body.readinessRecordReference(), body.reason(), checks, actor);
    }
}
