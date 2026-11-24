package edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorContext;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.dto.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.service.RingDesignException;
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
            @Valid @RequestBody CreateDesignRequest request) {
        Long memberAccountId = requireMemberAccountId();
        DesignResponseDto dto = applicationService.createDraft(request, memberAccountId, null, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @GetMapping
    public ResponseEntity<java.util.List<DesignResponseDto>> listWorkshopDesigns(
            @RequestParam(value = "includeArchived", required = false, defaultValue = "false") boolean includeArchived) {
        Long memberAccountId = requireMemberAccountId();
        java.util.List<DesignResponseDto> list = applicationService.listMyDesigns(memberAccountId, null,
                includeArchived);
        return ResponseEntity.ok(list);
    }

    @GetMapping("/{designId}")
    public ResponseEntity<DesignResponseDto> getWorkshopDesign(
            @PathVariable String designId) {
        Long memberAccountId = requireMemberAccountId();
        DesignResponseDto dto = applicationService.getDesign(designId, memberAccountId, null);
        return ResponseEntity.ok(dto);
    }

    @PostMapping("/{designId}/versions")
    public ResponseEntity<DesignResponseDto> saveWorkshopDesignVersion(
            @PathVariable String designId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("If-Match-Version") Long ifMatchVersion,
            @Valid @RequestBody SaveDesignVersionRequest request) {
        Long memberAccountId = requireMemberAccountId();
        DesignResponseDto dto = applicationService.saveVersion(designId, request, memberAccountId, null,
                ifMatchVersion, idempotencyKey);
        return ResponseEntity.ok(dto);
    }

    @PostMapping("/{designId}/evaluate")
    public ResponseEntity<DesignResponseDto> evaluateWorkshopDesign(
            @PathVariable String designId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("If-Match-Version") Long ifMatchVersion,
            @Valid @RequestBody SaveDesignVersionRequest request) {
        Long memberAccountId = requireMemberAccountId();
        DesignResponseDto dto = applicationService.evaluateDesign(designId, request, memberAccountId, null,
                ifMatchVersion, idempotencyKey);
        return ResponseEntity.ok(dto);
    }

    @PostMapping("/{designId}/archive")
    public ResponseEntity<DesignResponseDto> archiveWorkshopDesign(
            @PathVariable String designId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("If-Match-Version") Long ifMatchVersion) {
        Long memberAccountId = requireMemberAccountId();
        DesignResponseDto dto = applicationService.archive(designId, memberAccountId, null, ifMatchVersion,
                idempotencyKey);
        return ResponseEntity.ok(dto);
    }

    @PostMapping("/{designId}/restore")
    public ResponseEntity<DesignResponseDto> restoreWorkshopDesign(
            @PathVariable String designId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "If-Match-Version", required = false) Long ifMatchVersion) {
        Long memberAccountId = requireMemberAccountId();
        String key = idempotencyKey != null && !idempotencyKey.isBlank() ? idempotencyKey : java.util.UUID.randomUUID().toString();
        DesignResponseDto dto = applicationService.restore(designId, memberAccountId, null, ifMatchVersion, key);
        return ResponseEntity.ok(dto);
    }

    @PostMapping("/{designId}/attach-to-booking")
    public ResponseEntity<BookingDesignSnapshotDto> attachWorkshopDesignToBooking(
            @PathVariable String designId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("If-Match-Version") Long ifMatchVersion,
            @Valid @RequestBody AttachDesignRequestDto request) {
        Long memberAccountId = requireMemberAccountId();
        BookingDesignSnapshotDto dto = applicationService.attachToBooking(designId, request, memberAccountId, null,
                ifMatchVersion, idempotencyKey);
        return ResponseEntity.ok(dto);
    }

    private Long requireMemberAccountId() {
        var actor = ActorContext.getCurrentActor()
                .orElseThrow(() -> RingDesignException
                        .unauthorized("DESIGN_MEMBER_AUTH_REQUIRED",
                                "Yêu cầu đăng nhập tài khoản hội viên để thực hiện thao tác thiết kế nhẫn."));
        if (actor.role() != ActorRole.MEMBER) {
            throw RingDesignException
                    .forbidden("DESIGN_MEMBER_ACCESS_DENIED",
                            "Chỉ tài khoản hội viên hoạt động mới được thao tác thiết kế nhẫn.");
        }
        return actor.accountId();
    }
}
