package edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorContext;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.dto.PolicyVersionDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.dto.PreviewRequestDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.dto.PreviewResultDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.dto.PublishRequestDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.service.AdminRingDifficultyService;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.service.RingDesignException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/workshop-ring-difficulty")
@RequiredArgsConstructor
public class AdminRingDifficultyController {

    private final AdminRingDifficultyService adminRingDifficultyService;

    @GetMapping("/active")
    public ResponseEntity<PolicyVersionDto> getActiveRingDifficultyPolicy() {
        requireActiveAdmin();
        return ResponseEntity.ok(adminRingDifficultyService.getActivePolicy());
    }

    @GetMapping("/versions")
    public ResponseEntity<List<PolicyVersionDto>> listRingDifficultyPolicyVersions() {
        requireActiveAdmin();
        return ResponseEntity.ok(adminRingDifficultyService.listPolicyVersions());
    }

    @PostMapping("/preview")
    public ResponseEntity<PreviewResultDto> previewRingDifficultyPolicy(
            @Valid @RequestBody PreviewRequestDto request
    ) {
        CurrentAccountActor admin = requireActiveAdmin();
        return ResponseEntity.ok(adminRingDifficultyService.previewPolicy(request, admin.accountId()));
    }

    @PostMapping("/versions")
    public ResponseEntity<PolicyVersionDto> publishRingDifficultyPolicy(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationHeader,
            @Valid @RequestBody PublishRequestDto request,
            HttpServletRequest httpRequest
    ) {
        CurrentAccountActor admin = requireActiveAdmin();
        String correlationId = (correlationHeader != null && !correlationHeader.isBlank())
                ? correlationHeader.trim()
                : UUID.randomUUID().toString();
        PolicyVersionDto published = adminRingDifficultyService.publishPolicy(
                request, admin.accountId(), idempotencyKey, correlationId);
        return ResponseEntity.status(HttpStatus.CREATED).body(published);
    }

    private CurrentAccountActor requireActiveAdmin() {
        CurrentAccountActor actor = ActorContext.getCurrentActor()
                .orElseThrow(() -> RingDesignException.unauthorized(
                        "ADMIN_AUTH_REQUIRED",
                        "Yêu cầu đăng nhập tài khoản quản trị viên."));
        if (actor.role() != ActorRole.ADMIN || actor.status() != ActorStatus.ACTIVE) {
            throw RingDesignException.forbidden(
                    "ADMIN_ROLE_REQUIRED",
                    "Chỉ tài khoản Admin đang hoạt động mới có quyền cấu hình chính sách độ khó.");
        }
        return actor;
    }
}
