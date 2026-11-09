package edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorContext;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.controller.dto.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.service.NotificationManagementException;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.service.OperationalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/notification-requests")
@RequiredArgsConstructor
public class NotificationRequestController {

    private final OperationalService operationalService;

    @GetMapping
    public ResponseEntity<Page<NotificationRequestSummaryDto>> listRequests(
            @RequestParam(required = false) String templateCode,
            @RequestParam(required = false) String deliveryState,
            @RequestParam(required = false) String businessEventReference,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        requireAdminOrManager();
        return ResponseEntity.ok(operationalService.listRequests(templateCode, deliveryState, businessEventReference, from, to, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<NotificationRequestDetailDto> getRequestDetail(@PathVariable Long id) {
        requireAdminOrManager();
        return ResponseEntity.ok(operationalService.getRequestDetail(id));
    }

    @PostMapping("/{id}/resend")
    public ResponseEntity<ResendResponse> resendRequest(
            @PathVariable Long id,
            @Valid @RequestBody ResendRequestCommand command) {
        CurrentAccountActor actor = requireAdminOrManager();
        ResendResponse response = operationalService.resendRequest(id, actor, command);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<CancelResponse> cancelRequest(
            @PathVariable Long id,
            @Valid @RequestBody CancelRequestCommand command) {
        CurrentAccountActor actor = requireAdminOrManager();
        CancelResponse response = operationalService.cancelRequest(id, actor, command);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/redeliver")
    public ResponseEntity<RedeliverResponse> redeliverRequest(
            @PathVariable Long id,
            @Valid @RequestBody RedeliverRequestCommand command) {
        CurrentAccountActor actor = requireAdminOrManager();
        RedeliverResponse response = operationalService.redeliverWithRefreshedCredential(id, actor, command);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    private CurrentAccountActor requireAdminOrManager() {
        CurrentAccountActor actor = ActorContext.requireCurrentActor();
        String role = actor.role() != null ? actor.role().name() : "";
        if (!"ADMIN".equalsIgnoreCase(role) && !"MANAGER".equalsIgnoreCase(role)) {
            throw NotificationManagementException.forbidden("NOTIFICATION_NOT_AUTHORIZED",
                    "Chỉ Quản trị viên (ADMIN) hoặc Quản lý (MANAGER) mới có quyền truy cập vận hành thông báo.");
        }
        return actor;
    }
}

