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
            @RequestHeader(value = "X-Guest-Session", required = false) String legacyGuestSession,
            @Valid @RequestBody CreateDesignRequest request) {
        Long memberAccountId = resolveMemberAccountId();
        String session = resolveGuestSession(guestSessionHeader, legacyGuestSession);
        DesignResponseDto dto = applicationService.createDraft(request, memberAccountId, session, idempotencyKey);
        var responseBuilder = ResponseEntity.status(HttpStatus.CREATED);
        if (dto.getGuestSession() != null) {
            responseBuilder.header("X-Guest-Design-Session", dto.getGuestSession());
            responseBuilder.header("X-Guest-Session", dto.getGuestSession());
        }
        return responseBuilder.body(dto);
    }

    @GetMapping
    public ResponseEntity<java.util.List<DesignResponseDto>> listWorkshopDesigns(
            @RequestHeader(value = "X-Guest-Design-Session", required = false) String guestSessionHeader,
            @RequestHeader(value = "X-Guest-Session", required = false) String legacyGuestSession,
            @RequestParam(value = "includeArchived", required = false, defaultValue = "false") boolean includeArchived) {
        Long memberAccountId = resolveMemberAccountId();
        String session = resolveGuestSession(guestSessionHeader, legacyGuestSession);
        java.util.List<DesignResponseDto> list = applicationService.listMyDesigns(memberAccountId, session,
                includeArchived);
        return ResponseEntity.ok(list);
    }

    @GetMapping("/{designId}")
    public ResponseEntity<DesignResponseDto> getWorkshopDesign(
            @PathVariable String designId,
            @RequestHeader(value = "X-Guest-Design-Session", required = false) String guestSessionHeader,
            @RequestHeader(value = "X-Guest-Session", required = false) String legacyGuestSession) {
        Long memberAccountId = resolveMemberAccountId();
        String session = resolveGuestSession(guestSessionHeader, legacyGuestSession);
        DesignResponseDto dto = applicationService.getDesign(designId, memberAccountId, session);
        return ResponseEntity.ok(dto);
    }

    @PostMapping("/{designId}/versions")
    public ResponseEntity<DesignResponseDto> saveWorkshopDesignVersion(
            @PathVariable String designId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("If-Match-Version") Long ifMatchVersion,
            @RequestHeader(value = "X-Guest-Design-Session", required = false) String guestSessionHeader,
            @RequestHeader(value = "X-Guest-Session", required = false) String legacyGuestSession,
            @Valid @RequestBody SaveDesignVersionRequest request) {
        Long memberAccountId = resolveMemberAccountId();
        String session = resolveGuestSession(guestSessionHeader, legacyGuestSession);
        DesignResponseDto dto = applicationService.saveVersion(designId, request, memberAccountId, session,
                ifMatchVersion, idempotencyKey);
        return ResponseEntity.ok(dto);
    }

    @PostMapping("/{designId}/evaluate")
    public ResponseEntity<DesignResponseDto> evaluateWorkshopDesign(
            @PathVariable String designId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("If-Match-Version") Long ifMatchVersion,
            @RequestHeader(value = "X-Guest-Design-Session", required = false) String guestSessionHeader,
            @RequestHeader(value = "X-Guest-Session", required = false) String legacyGuestSession,
            @Valid @RequestBody SaveDesignVersionRequest request) {
        Long memberAccountId = resolveMemberAccountId();
        String session = resolveGuestSession(guestSessionHeader, legacyGuestSession);
        DesignResponseDto dto = applicationService.evaluateDesign(designId, request, memberAccountId, session,
                ifMatchVersion, idempotencyKey);
        return ResponseEntity.ok(dto);
    }

    @PostMapping("/{designId}/archive")
    public ResponseEntity<DesignResponseDto> archiveWorkshopDesign(
            @PathVariable String designId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("If-Match-Version") Long ifMatchVersion,
            @RequestHeader(value = "X-Guest-Design-Session", required = false) String guestSessionHeader,
            @RequestHeader(value = "X-Guest-Session", required = false) String legacyGuestSession) {
        Long memberAccountId = resolveMemberAccountId();
        String session = resolveGuestSession(guestSessionHeader, legacyGuestSession);
        DesignResponseDto dto = applicationService.archive(designId, memberAccountId, session, ifMatchVersion,
                idempotencyKey);
        return ResponseEntity.ok(dto);
    }

    @PostMapping("/{designId}/restore")
    public ResponseEntity<DesignResponseDto> restoreWorkshopDesign(
            @PathVariable String designId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "If-Match-Version", required = false) Long ifMatchVersion,
            @RequestHeader(value = "X-Guest-Design-Session", required = false) String guestSessionHeader,
            @RequestHeader(value = "X-Guest-Session", required = false) String legacyGuestSession) {
        Long memberAccountId = resolveMemberAccountId();
        String session = resolveGuestSession(guestSessionHeader, legacyGuestSession);
        String key = idempotencyKey != null && !idempotencyKey.isBlank() ? idempotencyKey : java.util.UUID.randomUUID().toString();
        DesignResponseDto dto = applicationService.restore(designId, memberAccountId, session, ifMatchVersion, key);
        return ResponseEntity.ok(dto);
    }

    @PostMapping("/{designId}/attach-to-booking")
    public ResponseEntity<BookingDesignSnapshotDto> attachWorkshopDesignToBooking(
            @PathVariable String designId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("If-Match-Version") Long ifMatchVersion,
            @RequestHeader(value = "X-Guest-Design-Session", required = false) String guestSessionHeader,
            @RequestHeader(value = "X-Guest-Session", required = false) String legacyGuestSession,
            @Valid @RequestBody AttachDesignRequestDto request) {
        Long memberAccountId = resolveMemberAccountId();
        String session = resolveGuestSession(guestSessionHeader, legacyGuestSession);
        BookingDesignSnapshotDto dto = applicationService.attachToBooking(designId, request, memberAccountId, session,
                ifMatchVersion, idempotencyKey);
        return ResponseEntity.ok(dto);
    }

    private String resolveGuestSession(String session1, String session2) {
        if (session1 != null && !session1.isBlank()) {
            return session1.trim();
        }
        if (session2 != null && !session2.isBlank()) {
            return session2.trim();
        }
        return null;
    }

    private Long resolveMemberAccountId() {
        var actor = ActorContext.getCurrentActor().orElse(null);
        if (actor != null && actor.accountId() != null) {
            return actor.accountId();
        }
        return null;
    }
}
