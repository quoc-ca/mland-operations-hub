package edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorContext;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.dto.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.service.WorkshopRingDesignApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/workshop-designs")
@RequiredArgsConstructor
public class WorkshopRingDesignController {

    private final WorkshopRingDesignApplicationService applicationService;

    @PostMapping
    public ResponseEntity<DesignResponseDto> createWorkshopDesign(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Guest-Design-Session", required = false) String guestSessionHeader,
            @Valid @RequestBody CreateDesignRequest request
    ) {
        Long memberAccountId = resolveMemberAccountId();
        DesignResponseDto dto = applicationService.createDraft(request, memberAccountId, guestSessionHeader, idempotencyKey);
        var responseBuilder = ResponseEntity.status(HttpStatus.CREATED);
        if (dto.getGuestSession() != null) {
            responseBuilder.header("X-Guest-Design-Session", dto.getGuestSession());
        }
        return responseBuilder.body(dto);
    }

    @GetMapping
    public ResponseEntity<java.util.List<DesignResponseDto>> listWorkshopDesigns(
            @RequestHeader(value = "X-Guest-Design-Session", required = false) String guestSessionHeader
    ) {
        Long memberAccountId = resolveMemberAccountId();
        java.util.List<DesignResponseDto> list = applicationService.listMyDesigns(memberAccountId, guestSessionHeader);
        return ResponseEntity.ok(list);
    }

    @GetMapping("/{designId}")
    public ResponseEntity<DesignResponseDto> getWorkshopDesign(
            @PathVariable String designId,
            @RequestHeader(value = "X-Guest-Design-Session", required = false) String guestSessionHeader
    ) {
        Long memberAccountId = resolveMemberAccountId();
        DesignResponseDto dto = applicationService.getDesign(designId, memberAccountId, guestSessionHeader);
        return ResponseEntity.ok(dto);
    }

    @PostMapping("/{designId}/versions")
    public ResponseEntity<DesignResponseDto> saveWorkshopDesignVersion(
            @PathVariable String designId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("If-Match-Version") Long ifMatchVersion,
            @RequestHeader(value = "X-Guest-Design-Session", required = false) String guestSessionHeader,
            @Valid @RequestBody SaveDesignVersionRequest request
    ) {
        Long memberAccountId = resolveMemberAccountId();
        DesignResponseDto dto = applicationService.saveVersion(designId, request, memberAccountId, guestSessionHeader, ifMatchVersion, idempotencyKey);
        return ResponseEntity.ok(dto);
    }

    @PostMapping("/{designId}/evaluate")
    public ResponseEntity<DesignResponseDto> evaluateWorkshopDesign(
            @PathVariable String designId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("If-Match-Version") Long ifMatchVersion,
            @RequestHeader(value = "X-Guest-Design-Session", required = false) String guestSessionHeader,
            @Valid @RequestBody SaveDesignVersionRequest request
    ) {
        Long memberAccountId = resolveMemberAccountId();
        DesignResponseDto dto = applicationService.evaluateDesign(designId, request, memberAccountId, guestSessionHeader, ifMatchVersion, idempotencyKey);
        return ResponseEntity.ok(dto);
    }

    @PostMapping("/{designId}/archive")
    public ResponseEntity<DesignResponseDto> archiveWorkshopDesign(
            @PathVariable String designId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("If-Match-Version") Long ifMatchVersion,
            @RequestHeader(value = "X-Guest-Design-Session", required = false) String guestSessionHeader
    ) {
        Long memberAccountId = resolveMemberAccountId();
        DesignResponseDto dto = applicationService.archive(designId, memberAccountId, guestSessionHeader, ifMatchVersion, idempotencyKey);
        return ResponseEntity.ok(dto);
    }

    @PostMapping("/{designId}/attach-to-booking")
    public ResponseEntity<BookingDesignSnapshotDto> attachWorkshopDesignToBooking(
            @PathVariable String designId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("If-Match-Version") Long ifMatchVersion,
            @RequestHeader(value = "X-Guest-Design-Session", required = false) String guestSessionHeader,
            @Valid @RequestBody AttachDesignRequestDto request
    ) {
        Long memberAccountId = resolveMemberAccountId();
        BookingDesignSnapshotDto dto = applicationService.attachToBooking(designId, request, memberAccountId, guestSessionHeader, ifMatchVersion, idempotencyKey);
        return ResponseEntity.ok(dto);
    }

    private Long resolveMemberAccountId() {
        var actor = ActorContext.getCurrentActor().orElse(null);
        if (actor != null && actor.role() == ActorRole.MEMBER) {
            return actor.accountId();
        }
        return null;
    }
}

