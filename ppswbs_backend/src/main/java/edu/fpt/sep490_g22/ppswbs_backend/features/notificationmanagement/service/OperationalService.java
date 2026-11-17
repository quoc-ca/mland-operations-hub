package edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.controller.dto.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.dao.DeliveryAttemptRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.dao.NotificationRequestRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.dao.OperationalAuditRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.dao.RenditionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.entity.DeliveryAttempt;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.entity.NotificationRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.entity.OperationalAuditRecord;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.entity.Rendition;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OperationalService {

    private final NotificationRequestRepository notificationRequestRepository;
    private final DeliveryAttemptRepository deliveryAttemptRepository;
    private final OperationalAuditRepository operationalAuditRepository;
    private final RenditionRepository renditionRepository;

    @Transactional(readOnly = true)
    public Page<NotificationRequestSummaryDto> listRequests(
            String templateCode,
            String deliveryState,
            String businessEventReference,
            Instant from,
            Instant to,
            Pageable pageable) {

        Specification<NotificationRequest> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (templateCode != null && !templateCode.isBlank()) {
                predicates.add(cb.equal(root.get("templateCode"), templateCode));
            }
            if (deliveryState != null && !deliveryState.isBlank()) {
                predicates.add(cb.equal(root.get("deliveryState"), deliveryState));
            }
            if (businessEventReference != null && !businessEventReference.isBlank()) {
                predicates.add(cb.equal(root.get("businessEventReference"), businessEventReference));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<NotificationRequest> page = notificationRequestRepository.findAll(spec, pageable);
        return page.map(req -> NotificationRequestSummaryDto.builder()
                .id(req.getId())
                .businessEventReference(req.getBusinessEventReference())
                .templateCode(req.getTemplateCode())
                .recipientAddressMasked(req.getRecipientAddressMasked())
                .deliveryState(req.getDeliveryState())
                .attemptCount(req.getAttemptCount())
                .nextAttemptAt(req.getNextAttemptAt())
                .createdAt(req.getCreatedAt())
                .terminalAt(req.getTerminalAt())
                .build());
    }

    @Transactional(readOnly = true)
    public NotificationRequestDetailDto getRequestDetail(Long id) {
        NotificationRequest req = notificationRequestRepository.findById(id)
                .orElseThrow(() -> NotificationManagementException.notFound("REQUEST_NOT_FOUND",
                        "Notification request not found with ID: " + id));

        List<DeliveryAttempt> attempts = deliveryAttemptRepository.findByNotificationRequestIdOrderByAttemptNumberAsc(id);
        List<OperationalAuditRecord> audits = operationalAuditRepository.findByNotificationRequestIdOrderByCreatedAtAsc(id);

        List<NotificationRequestDetailDto.AttemptDto> attemptDtos = attempts.stream().map(a ->
                NotificationRequestDetailDto.AttemptDto.builder()
                        .attemptNumber(a.getAttemptNumber())
                        .startedAt(a.getStartedAt())
                        .completedAt(a.getCompletedAt())
                        .outcomeCategory(a.getOutcomeCategory())
                        .providerMessageId(a.getProviderMessageId())
                        .safeErrorSummary(a.getSafeErrorSummary())
                        .build()
        ).toList();

        List<NotificationRequestDetailDto.AuditDto> auditDtos = audits.stream().map(aud ->
                NotificationRequestDetailDto.AuditDto.builder()
                        .id(aud.getId())
                        .actorAccountId(aud.getActorAccountId())
                        .actorRole(aud.getActorRole())
                        .actionType(aud.getActionType())
                        .reason(aud.getReason())
                        .previousState(aud.getPreviousState())
                        .newState(aud.getNewState())
                        .duplicateRiskAccepted(aud.isDuplicateRiskAccepted())
                        .createdAt(aud.getCreatedAt())
                        .build()
        ).toList();

        return NotificationRequestDetailDto.builder()
                .id(req.getId())
                .businessEventReference(req.getBusinessEventReference())
                .templateCode(req.getTemplateCode())
                .templateVersionId(req.getTemplateVersion() != null ? req.getTemplateVersion().getId() : null)
                .recipientRole(req.getRecipientRole())
                .recipientAddressMasked(req.getRecipientAddressMasked())
                .deliveryState(req.getDeliveryState())
                .attemptCount(req.getAttemptCount())
                .nextAttemptAt(req.getNextAttemptAt())
                .processingStartedAt(req.getProcessingStartedAt())
                .smtpInvocationStartedAt(req.getSmtpInvocationStartedAt())
                .terminalAt(req.getTerminalAt())
                .originalRequestId(req.getOriginalRequest() != null ? req.getOriginalRequest().getId() : null)
                .createdAt(req.getCreatedAt())
                .updatedAt(req.getUpdatedAt())
                .attempts(attemptDtos)
                .audits(auditDtos)
                .build();
    }

    @Transactional
    public ResendResponse resendRequest(Long id, CurrentAccountActor actor, ResendRequestCommand command) {
        if (command == null || command.reason() == null || command.reason().isBlank()) {
            throw NotificationManagementException.badRequest("REASON_REQUIRED", "Lý do gửi lại là bắt buộc.");
        }

        NotificationRequest original = notificationRequestRepository.findById(id)
                .orElseThrow(() -> NotificationManagementException.notFound("REQUEST_NOT_FOUND",
                        "Notification request not found with ID: " + id));

        String state = original.getDeliveryState();
        String actorRole = actor.role() != null ? actor.role().name() : "";

        if ("UNKNOWN_OUTCOME".equalsIgnoreCase(state)) {
            if (!"ADMIN".equalsIgnoreCase(actorRole)) {
                throw NotificationManagementException.forbidden("MANAGER_CANNOT_RESEND_UNKNOWN_OUTCOME",
                        "Chỉ Quản trị viên (ADMIN) mới có quyền gửi lại yêu cầu có trạng thái UNKNOWN_OUTCOME.");
            }
            if (command.duplicateRiskAccepted() == null || !command.duplicateRiskAccepted()) {
                throw NotificationManagementException.badRequest("DUPLICATE_RISK_NOT_ACCEPTED",
                        "Cần xác nhận chấp nhận rủi ro trùng lặp email (duplicateRiskAccepted = true).");
            }
        } else if (!"SENT".equalsIgnoreCase(state) && !"FAILED".equalsIgnoreCase(state)) {
            throw NotificationManagementException.badRequest("INVALID_STATE_FOR_RESEND",
                    "Chỉ có thể gửi lại yêu cầu ở trạng thái SENT hoặc FAILED. Trạng thái hiện tại: " + state);
        }

        NotificationRequest rootRequest = resolveRootRequest(original);

        // Clone NotificationRequest
        NotificationRequest newRequest = new NotificationRequest();
        newRequest.setBusinessEventReference(original.getBusinessEventReference());
        newRequest.setTemplateCode(original.getTemplateCode());
        newRequest.setTemplateVersion(original.getTemplateVersion());
        newRequest.setRecipientRole(original.getRecipientRole());
        newRequest.setRecipientAddressFingerprint(original.getRecipientAddressFingerprint());
        newRequest.setRecipientAddressMasked(original.getRecipientAddressMasked());
        newRequest.setRecipientSnapshot(original.getRecipientSnapshot());
        newRequest.setBusinessDataSnapshot(original.getBusinessDataSnapshot());
        newRequest.setArtifactReference(original.getArtifactReference());
        if (original.getCredentialExpiresAt() == null || original.getCredentialExpiresAt().isAfter(Instant.now())) {
            newRequest.setCredentialReference(original.getCredentialReference());
            newRequest.setCredentialExpiresAt(original.getCredentialExpiresAt());
        } else {
            newRequest.setCredentialReference(null);
            newRequest.setCredentialExpiresAt(null);
        }
        newRequest.setDeliveryState("PENDING");
        newRequest.setAttemptCount(0);
        newRequest.setNextAttemptAt(Instant.now());
        newRequest.setOriginalRequest(rootRequest);

        NotificationRequest saved = notificationRequestRepository.save(newRequest);

        // Copy Rendition if exists
        Optional<Rendition> origRendition = renditionRepository.findByNotificationRequestId(original.getId());
        if (origRendition.isEmpty() && !original.getId().equals(rootRequest.getId())) {
            origRendition = renditionRepository.findByNotificationRequestId(rootRequest.getId());
        }
        origRendition.ifPresent(r -> {
            Rendition newR = new Rendition();
            newR.setNotificationRequest(saved);
            newR.setRenderedSubject(r.getRenderedSubject());
            newR.setRenderedHtml(r.getRenderedHtml());
            newR.setRenderedPlainText(r.getRenderedPlainText());
            newR.setRenderedAt(Instant.now());
            renditionRepository.save(newR);
        });

        // Record Operational Audit
        OperationalAuditRecord audit = new OperationalAuditRecord();
        audit.setNotificationRequest(original);
        audit.setActorAccountId(actor.accountId() != null ? String.valueOf(actor.accountId()) : "system");
        audit.setActorRole(actorRole);
        audit.setActionType("RESEND");
        audit.setReason(command.reason());
        audit.setPreviousState(state);
        audit.setNewState(state);
        audit.setDuplicateRiskAccepted(Boolean.TRUE.equals(command.duplicateRiskAccepted()));
        operationalAuditRepository.save(audit);

        log.info("Operational resend created new request {} (root {}) from selected request {}",
                saved.getId(), rootRequest.getId(), original.getId());

        return ResendResponse.builder()
                .newRequestId(saved.getId())
                .originalRequestId(rootRequest.getId())
                .deliveryState("PENDING")
                .createdAt(saved.getCreatedAt())
                .message("Resend request enqueued successfully.")
                .build();
    }

    @Transactional
    public CancelResponse cancelRequest(Long id, CurrentAccountActor actor, CancelRequestCommand command) {
        if (command == null || command.reason() == null || command.reason().isBlank()) {
            throw NotificationManagementException.badRequest("REASON_REQUIRED", "Lý do hủy là bắt buộc.");
        }

        NotificationRequest request = notificationRequestRepository.findById(id)
                .orElseThrow(() -> NotificationManagementException.notFound("REQUEST_NOT_FOUND",
                        "Notification request not found with ID: " + id));

        String state = request.getDeliveryState();
        if ("PROCESSING".equalsIgnoreCase(state)) {
            throw NotificationManagementException.conflict("CANNOT_CANCEL_PROCESSING",
                    "Không thể hủy yêu cầu đang trong tiến trình xử lý gửi.");
        }
        if (!"PENDING".equalsIgnoreCase(state) && !"RETRY_SCHEDULED".equalsIgnoreCase(state)) {
            throw NotificationManagementException.conflict("CANNOT_CANCEL_TERMINAL",
                    "Không thể hủy yêu cầu ở trạng thái: " + state);
        }

        Instant now = Instant.now();
        request.setDeliveryState("CANCELLED");
        request.setTerminalAt(now);
        request.setNextAttemptAt(null);
        notificationRequestRepository.save(request);

        String actorId = actor.accountId() != null ? String.valueOf(actor.accountId()) : "system";
        String actorRole = actor.role() != null ? actor.role().name() : "";

        OperationalAuditRecord audit = new OperationalAuditRecord();
        audit.setNotificationRequest(request);
        audit.setActorAccountId(actorId);
        audit.setActorRole(actorRole);
        audit.setActionType("CANCEL");
        audit.setReason(command.reason());
        audit.setPreviousState(state);
        audit.setNewState("CANCELLED");
        audit.setDuplicateRiskAccepted(false);
        operationalAuditRepository.save(audit);

        log.info("Notification request {} cancelled by actor {}", id, actorId);

        return CancelResponse.builder()
                .requestId(request.getId())
                .deliveryState("CANCELLED")
                .terminalAt(now)
                .cancelledBy(actorId)
                .reason(command.reason())
                .build();
    }

    @Transactional
    public RedeliverResponse redeliverWithRefreshedCredential(Long id, CurrentAccountActor actor, RedeliverRequestCommand command) {
        if (command == null || command.reason() == null || command.reason().isBlank()) {
            throw NotificationManagementException.badRequest("REASON_REQUIRED", "Lý do cấp phát và gửi lại là bắt buộc.");
        }

        NotificationRequest original = notificationRequestRepository.findById(id)
                .orElseThrow(() -> NotificationManagementException.notFound("REQUEST_NOT_FOUND",
                        "Notification request not found with ID: " + id));

        NotificationRequest rootRequest = resolveRootRequest(original);

        NotificationRequest newRequest = new NotificationRequest();
        newRequest.setBusinessEventReference(original.getBusinessEventReference());
        newRequest.setTemplateCode(original.getTemplateCode());
        newRequest.setTemplateVersion(original.getTemplateVersion());
        newRequest.setRecipientRole(original.getRecipientRole());
        newRequest.setRecipientAddressFingerprint(original.getRecipientAddressFingerprint());
        newRequest.setRecipientAddressMasked(original.getRecipientAddressMasked());
        newRequest.setRecipientSnapshot(original.getRecipientSnapshot());
        newRequest.setBusinessDataSnapshot(original.getBusinessDataSnapshot());
        newRequest.setArtifactReference(original.getArtifactReference());
        // FR-019: For expired or revoked credentials, MUST NOT copy the expired or revoked credential
        newRequest.setCredentialReference(null);
        newRequest.setCredentialExpiresAt(null);
        newRequest.setDeliveryState("PENDING");
        newRequest.setAttemptCount(0);
        newRequest.setNextAttemptAt(Instant.now());
        newRequest.setOriginalRequest(rootRequest);

        NotificationRequest saved = notificationRequestRepository.save(newRequest);

        // Copy Rendition if exists from selected or root request
        Optional<Rendition> origRendition = renditionRepository.findByNotificationRequestId(original.getId());
        if (origRendition.isEmpty() && !original.getId().equals(rootRequest.getId())) {
            origRendition = renditionRepository.findByNotificationRequestId(rootRequest.getId());
        }
        origRendition.ifPresent(r -> {
            Rendition newR = new Rendition();
            newR.setNotificationRequest(saved);
            newR.setRenderedSubject(r.getRenderedSubject());
            newR.setRenderedHtml(r.getRenderedHtml());
            newR.setRenderedPlainText(r.getRenderedPlainText());
            newR.setRenderedAt(Instant.now());
            renditionRepository.save(newR);
        });

        String actorId = actor.accountId() != null ? String.valueOf(actor.accountId()) : "system";
        String actorRole = actor.role() != null ? actor.role().name() : "";

        OperationalAuditRecord audit = new OperationalAuditRecord();
        audit.setNotificationRequest(original);
        audit.setActorAccountId(actorId);
        audit.setActorRole(actorRole);
        audit.setActionType("REDELIVER_REFRESHED_CREDENTIAL");
        audit.setReason(command.reason());
        audit.setPreviousState(original.getDeliveryState());
        audit.setNewState(original.getDeliveryState());
        audit.setDuplicateRiskAccepted(false);
        operationalAuditRepository.save(audit);

        log.info("Redeliver with refreshed credential created new request {} (root {}) from selected {}",
                saved.getId(), rootRequest.getId(), original.getId());

        return RedeliverResponse.builder()
                .newRequestId(saved.getId())
                .originalRequestId(rootRequest.getId())
                .deliveryState("PENDING")
                .createdAt(saved.getCreatedAt())
                .message("Redelivery request enqueued with replacement credential.")
                .build();
    }

    private NotificationRequest resolveRootRequest(NotificationRequest request) {
        NotificationRequest root = request;
        while (root.getOriginalRequest() != null) {
            root = root.getOriginalRequest();
        }
        return root;
    }
}

