package edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorContext;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.controller.dto.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.service.NotificationManagementException;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.service.TemplateManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/notification-templates")
@RequiredArgsConstructor
public class NotificationTemplateController {

    private final TemplateManagementService templateManagementService;

    @GetMapping
    public ResponseEntity<List<TemplateResponseDto>> listTemplates() {
        requireAdminOrManager();
        return ResponseEntity.ok(templateManagementService.listTemplates());
    }

    @GetMapping("/{code}")
    public ResponseEntity<TemplateDetailResponseDto> getTemplate(@PathVariable String code) {
        requireAdminOrManager();
        return ResponseEntity.ok(templateManagementService.getTemplateDetails(code));
    }

    @PostMapping("/{code}/versions")
    public ResponseEntity<TemplateDetailResponseDto.TemplateVersionDto> createDraft(
            @PathVariable String code,
            @Valid @RequestBody CreateTemplateVersionRequest request) {
        CurrentAccountActor actor = requireAdminOrManager();
        String actorId = actor.accountId() != null ? String.valueOf(actor.accountId()) : "system";
        TemplateDetailResponseDto.TemplateVersionDto dto = templateManagementService.createDraft(code, request, actorId);
        return new ResponseEntity<>(dto, HttpStatus.CREATED);
    }

    @PutMapping("/{code}/versions/{versionNumber}")
    public ResponseEntity<TemplateDetailResponseDto.TemplateVersionDto> updateDraft(
            @PathVariable String code,
            @PathVariable int versionNumber,
            @Valid @RequestBody CreateTemplateVersionRequest request) {
        requireAdminOrManager();
        TemplateDetailResponseDto.TemplateVersionDto dto = templateManagementService.updateDraft(code, versionNumber, request);
        return ResponseEntity.ok(dto);
    }

    @PostMapping("/{code}/versions/{versionNumber}/activate")
    public ResponseEntity<ActivateVersionResponse> activateVersion(
            @PathVariable String code,
            @PathVariable int versionNumber) {
        CurrentAccountActor actor = requireAdminOrManager();
        String actorId = actor.accountId() != null ? String.valueOf(actor.accountId()) : "system";
        ActivateVersionResponse response = templateManagementService.activateVersion(code, versionNumber, actorId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{code}/preview")
    public ResponseEntity<TemplatePreviewResponse> renderPreview(
            @PathVariable String code,
            @RequestBody(required = false) TemplatePreviewRequest request) {
        requireAdminOrManager();
        return ResponseEntity.ok(templateManagementService.renderPreview(code, request));
    }

    private CurrentAccountActor requireAdminOrManager() {
        CurrentAccountActor actor = ActorContext.requireCurrentActor();
        String role = actor.role() != null ? actor.role().name() : "";
        if (!"ADMIN".equalsIgnoreCase(role) && !"MANAGER".equalsIgnoreCase(role)) {
            throw NotificationManagementException.forbidden("NOTIFICATION_NOT_AUTHORIZED",
                    "Chỉ Quản trị viên (ADMIN) hoặc Quản lý (MANAGER) mới có quyền truy cập quản lý thông báo.");
        }
        return actor;
    }
}

