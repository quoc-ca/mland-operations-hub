package edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.dao.NotificationRequestRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.dao.OperationalFailureRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.dao.TemplateDefinitionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.dao.TemplateVersionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.entity.NotificationRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.entity.OperationalFailure;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.entity.TemplateDefinition;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.entity.TemplateVersion;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.facade.NotificationIntentResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.facade.NotificationIntentResult.Status;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.facade.NotificationSummaryDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.facade.SubmitNotificationIntentCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationRequestService {

    private final TemplateDefinitionRepository templateDefinitionRepository;
    private final TemplateVersionRepository templateVersionRepository;
    private final NotificationRequestRepository notificationRequestRepository;
    private final OperationalFailureRepository operationalFailureRepository;
    private final RenditionService renditionService;
    private final ObjectMapper objectMapper;

    /**
     * Enqueues a notification request in the caller's transaction.
     * Guarantees that failure will NOT roll back caller transaction and will record OperationalFailure instead.
     */
    @Transactional
    public NotificationIntentResult submitNotificationIntent(SubmitNotificationIntentCommand command) {
        try {
            if (command == null || command.getBusinessEventReference() == null || command.getTemplateCode() == null) {
                return recordFailure(
                        command != null ? command.getBusinessEventReference() : "UNKNOWN",
                        command != null ? command.getTemplateCode() : "UNKNOWN",
                        null,
                        "INVALID_INPUT",
                        "Business event reference and template code are mandatory"
                );
            }

            String email = command.getRecipientEmail() != null ? command.getRecipientEmail().trim() : null;
            if (email == null || email.isBlank() || !email.contains("@") || email.startsWith("@") || email.endsWith("@")) {
                return recordFailure(
                        command.getBusinessEventReference(),
                        command.getTemplateCode(),
                        null,
                        "INVALID_RECIPIENT",
                        "Recipient email address is missing or invalid"
                );
            }

            String fingerprint = sha256Hex(email.toLowerCase(Locale.ROOT));
            String lockKey = (command.getBusinessEventReference() + ":" + command.getTemplateCode() + ":" + fingerprint).intern();

            synchronized (lockKey) {
                Optional<TemplateDefinition> defOpt = templateDefinitionRepository.findByCode(command.getTemplateCode());
                if (defOpt.isEmpty()) {
                    return recordFailure(
                            command.getBusinessEventReference(),
                            command.getTemplateCode(),
                            fingerprint,
                            "TEMPLATE_NOT_FOUND",
                            "Template definition not found for code: " + command.getTemplateCode()
                    );
                }

                TemplateDefinition definition = defOpt.get();
                if (!"ACTIVE".equalsIgnoreCase(definition.getLifecycleStatus())) {
                    return recordFailure(
                            command.getBusinessEventReference(),
                            command.getTemplateCode(),
                            fingerprint,
                            "TEMPLATE_INACTIVE",
                            "Template definition is not in ACTIVE status: " + definition.getLifecycleStatus()
                    );
                }

                Optional<TemplateVersion> activeVersionOpt = templateVersionRepository
                        .findByTemplateDefinitionIdAndStatus(definition.getId(), "ACTIVE");
                if (activeVersionOpt.isEmpty()) {
                    return recordFailure(
                            command.getBusinessEventReference(),
                            command.getTemplateCode(),
                            fingerprint,
                            "TEMPLATE_INACTIVE",
                            "No active template version found for template code: " + command.getTemplateCode()
                    );
                }

                TemplateVersion activeVersion = activeVersionOpt.get();

                // Validate required placeholders in active version against provided snapshot
                Set<String> requiredPlaceholders = new HashSet<>();
                requiredPlaceholders.addAll(renditionService.extractPlaceholders(activeVersion.getSubjectTemplate()));
                requiredPlaceholders.addAll(renditionService.extractPlaceholders(activeVersion.getHtmlTemplate()));
                requiredPlaceholders.addAll(renditionService.extractPlaceholders(activeVersion.getPlainTextTemplate()));

                Map<String, Object> snapshot = command.getBusinessDataSnapshot() != null ? command.getBusinessDataSnapshot() : Collections.emptyMap();
                List<String> missing = new ArrayList<>();
                for (String req : requiredPlaceholders) {
                    if (!snapshot.containsKey(req) || snapshot.get(req) == null) {
                        missing.add(req);
                    }
                }

                if (!missing.isEmpty()) {
                    return recordFailure(
                            command.getBusinessEventReference(),
                            command.getTemplateCode(),
                            fingerprint,
                            "REQUIRED_PLACEHOLDER_MISSING",
                            "Missing required placeholder(s): " + String.join(", ", missing)
                    );
                }

                // Idempotency check: unique original request for same (businessEventReference, templateCode, recipientRole, fingerprint)
                Optional<NotificationRequest> existingOpt = notificationRequestRepository
                        .findByBusinessEventReferenceAndTemplateCodeAndRecipientRoleAndRecipientAddressFingerprintAndOriginalRequestIsNull(
                                command.getBusinessEventReference(),
                                command.getTemplateCode(),
                                "CUSTOMER",
                                fingerprint
                        );

                if (existingOpt.isPresent()) {
                    NotificationRequest existing = existingOpt.get();
                    log.info("Duplicate notification intent detected for event {} and recipient. Returning DUPLICATE_IGNORED.", command.getBusinessEventReference());
                    return NotificationIntentResult.builder()
                            .status(Status.DUPLICATE_IGNORED)
                            .notificationRequestId(existing.getId())
                            .message("Duplicate intent ignored")
                            .build();
                }

                // Create NotificationRequest
                NotificationRequest request = new NotificationRequest();
                request.setBusinessEventReference(command.getBusinessEventReference());
                request.setTemplateCode(command.getTemplateCode());
                request.setTemplateVersion(activeVersion);
                request.setRecipientRole("CUSTOMER");
                request.setRecipientAddressFingerprint(fingerprint);
                request.setRecipientAddressMasked(maskEmail(email));

                Map<String, String> recipientMap = new HashMap<>();
                recipientMap.put("email", email);
                if (command.getRecipientName() != null) {
                    recipientMap.put("name", command.getRecipientName());
                }
                request.setRecipientSnapshot(objectMapper.writeValueAsString(recipientMap));
                request.setBusinessDataSnapshot(objectMapper.writeValueAsString(snapshot));

                request.setArtifactReference(command.getArtifactReference());
                request.setCredentialReference(command.getCredentialReference());
                request.setCredentialExpiresAt(command.getCredentialExpiresAt());
                request.setDeliveryState("PENDING");
                request.setAttemptCount(0);
                request.setNextAttemptAt(Instant.now());
                request.setOriginalRequest(null);

                try {
                    NotificationRequest saved = notificationRequestRepository.saveAndFlush(request);
                    log.info("Notification request {} successfully enqueued for event {}", saved.getId(), command.getBusinessEventReference());

                    return NotificationIntentResult.builder()
                            .status(Status.ENQUEUED)
                            .notificationRequestId(saved.getId())
                            .message("Notification intent enqueued")
                            .build();
                } catch (DataIntegrityViolationException dive) {
                    log.info("Concurrent insert collision for event {}. Returning DUPLICATE_IGNORED.", command.getBusinessEventReference());
                    Optional<NotificationRequest> dupOpt = notificationRequestRepository
                            .findByBusinessEventReferenceAndTemplateCodeAndRecipientRoleAndRecipientAddressFingerprintAndOriginalRequestIsNull(
                                    command.getBusinessEventReference(),
                                    command.getTemplateCode(),
                                    "CUSTOMER",
                                    fingerprint
                            );
                    Long dupId = dupOpt.map(NotificationRequest::getId).orElse(null);
                    return NotificationIntentResult.builder()
                            .status(Status.DUPLICATE_IGNORED)
                            .notificationRequestId(dupId)
                            .message("Duplicate intent ignored")
                            .build();
                }
            }

        } catch (Exception e) {
            log.error("Unexpected error submitting notification intent for event {}", command != null ? command.getBusinessEventReference() : "null", e);
            return recordFailure(
                    command != null ? command.getBusinessEventReference() : "UNKNOWN",
                    command != null ? command.getTemplateCode() : "UNKNOWN",
                    null,
                    "INTERNAL_ERROR",
                    "Unexpected error: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName())
            );
        }
    }

    @Transactional(readOnly = true)
    public Optional<NotificationSummaryDto> findNotificationSummary(String businessEventReference, String templateCode) {
        return notificationRequestRepository
                .findTopByBusinessEventReferenceAndTemplateCodeOrderByCreatedAtDesc(businessEventReference, templateCode)
                .map(req -> NotificationSummaryDto.builder()
                        .requestId(req.getId())
                        .templateCode(req.getTemplateCode())
                        .deliveryState(req.getDeliveryState())
                        .attemptCount(req.getAttemptCount())
                        .createdAt(req.getCreatedAt())
                        .terminalAt(req.getTerminalAt())
                        .build());
    }

    private NotificationIntentResult recordFailure(String businessEventRef, String templateCode, String recipientFingerprint, String failureCode, String safeReason) {
        try {
            OperationalFailure failure = new OperationalFailure();
            failure.setBusinessEventReference(businessEventRef != null ? businessEventRef : "UNKNOWN");
            failure.setTemplateCode(templateCode != null ? templateCode : "UNKNOWN");
            failure.setRecipientFingerprint(recipientFingerprint);
            failure.setFailureCode(failureCode);
            failure.setSafeReason(safeReason);
            failure.setCreatedAt(Instant.now());

            OperationalFailure saved = operationalFailureRepository.save(failure);
            return NotificationIntentResult.builder()
                    .status(Status.OPERATIONAL_FAILURE)
                    .operationalFailureId(saved.getId())
                    .failureCode(failureCode)
                    .message(safeReason)
                    .build();
        } catch (Exception ex) {
            log.error("Failed to persist operational failure", ex);
            return NotificationIntentResult.builder()
                    .status(Status.OPERATIONAL_FAILURE)
                    .failureCode(failureCode)
                    .message(safeReason)
                    .build();
        }
    }

    public static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    public static String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        String[] parts = email.split("@", 2);
        String local = parts[0];
        String domain = parts[1];
        if (local.length() <= 1) {
            return local + "***@" + domain;
        }
        return local.charAt(0) + "***" + local.charAt(local.length() - 1) + "@" + domain;
    }
}

