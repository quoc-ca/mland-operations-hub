package edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.InternalAccountInvitationRequested;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.InvitationDeliveryFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.InvitationDeliveryReport;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.dao.NotificationRequestRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.dao.OperationalFailureRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.dao.TemplateVersionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.entity.NotificationRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.entity.OperationalFailure;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.entity.TemplateVersion;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class InternalAccountInvitationRequestedConsumer {

    private final NotificationRequestRepository notificationRequestRepository;
    private final TemplateVersionRepository templateVersionRepository;
    private final OperationalFailureRepository operationalFailureRepository;
    private final RenditionService renditionService;
    private final InvitationDeliveryFacade invitationDeliveryFacade;
    private final ObjectMapper objectMapper;

    @EventListener
    @Transactional
    public void onInternalAccountInvitationRequested(InternalAccountInvitationRequested event) {
        log.info("Consuming InternalAccountInvitationRequested: invitationId={}, version={}, eventRef={}",
                event.invitationId(), event.version(), event.eventReference());

        // Idempotency check: eventReference + templateCode
        Optional<NotificationRequest> existingOpt = notificationRequestRepository
                .findTopByBusinessEventReferenceAndTemplateCodeOrderByCreatedAtDesc(event.eventReference(), "INTERNAL_ACCOUNT_INVITATION");
        if (existingOpt.isPresent()) {
            log.info("Duplicate invitation request ignored for eventRef: {}", event.eventReference());
            invitationDeliveryFacade.reportDeliveryState(new InvitationDeliveryReport(
                    event.invitationId(),
                    event.version(),
                    existingOpt.get().getId(),
                    existingOpt.get().getDeliveryState(),
                    Instant.now()
            ));
            return;
        }

        // Check active template version
        Optional<TemplateVersion> versionOpt = templateVersionRepository
                .findByTemplateDefinitionCodeAndStatus("INTERNAL_ACCOUNT_INVITATION", "ACTIVE");

        if (versionOpt.isEmpty()) {
            log.warn("Active template version not found for INTERNAL_ACCOUNT_INVITATION");
            OperationalFailure failure = new OperationalFailure();
            failure.setBusinessEventReference(event.eventReference());
            failure.setTemplateCode("INTERNAL_ACCOUNT_INVITATION");
            failure.setRecipientFingerprint(sha256(event.recipientEmail()));
            failure.setFailureCode("TEMPLATE_NOT_FOUND");
            failure.setSafeReason("Mẫu thông báo thiết lập tài khoản nội bộ chưa được kích hoạt");
            failure.setCreatedAt(Instant.now());
            operationalFailureRepository.save(failure);
            return;
        }

        TemplateVersion activeVersion = versionOpt.get();

        String recipientFingerprint = sha256(event.recipientEmail());
        String recipientMasked = maskEmail(event.recipientEmail());

        String recipientSnapshotJson;
        String businessDataSnapshotJson;
        try {
            recipientSnapshotJson = objectMapper.writeValueAsString(Map.of(
                    "email", event.recipientEmail(),
                    "name", event.recipientName() != null ? event.recipientName() : "Nhân viên Mland"
            ));
            businessDataSnapshotJson = objectMapper.writeValueAsString(Map.of(
                    "recipient_name", event.recipientName() != null ? event.recipientName() : "Nhân viên Mland",
                    "setup_url", "{{setup_url}}",
                    "expiry_time", event.expiresAt() != null ? event.expiresAt().toString() : ""
            ));
        } catch (Exception e) {
            log.error("Failed to serialize snapshots", e);
            return;
        }

        NotificationRequest request = new NotificationRequest();
        request.setBusinessEventReference(event.eventReference());
        request.setTemplateCode("INTERNAL_ACCOUNT_INVITATION");
        request.setTemplateVersion(activeVersion);
        request.setRecipientRole("STAFF_OR_MANAGER");
        request.setRecipientAddressFingerprint(recipientFingerprint);
        request.setRecipientAddressMasked(recipientMasked);
        request.setRecipientSnapshot(recipientSnapshotJson);
        request.setBusinessDataSnapshot(businessDataSnapshotJson);
        request.setArtifactReference(null);
        request.setCredentialReference(event.invitationId() + ":" + event.version());
        request.setCredentialExpiresAt(event.expiresAt());
        request.setDeliveryState("PENDING");
        request.setAttemptCount(0);
        request.setNextAttemptAt(Instant.now());

        request = notificationRequestRepository.save(request);

        // Freeze rendition with sentinel placeholder {{setup_url}}
        renditionService.getOrCreateRendition(request);

        // Report enqueued state to FE-001
        invitationDeliveryFacade.reportDeliveryState(new InvitationDeliveryReport(
                event.invitationId(),
                event.version(),
                request.getId(),
                "PENDING",
                Instant.now()
        ));

        log.info("Successfully enqueued invitation NotificationRequest id={}", request.getId());
    }

    private String sha256(String input) {
        if (input == null) return "";
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.toLowerCase().trim().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            return "";
        }
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "***";
        int at = email.indexOf('@');
        String local = email.substring(0, at);
        String domain = email.substring(at);
        if (local.length() <= 2) return local + domain;
        return local.charAt(0) + "***" + local.charAt(local.length() - 1) + domain;
    }
}
