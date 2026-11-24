package edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.InvitationDeliveryFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.InvitationDeliveryReport;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.MaterializationOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.MaterializationPurpose;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.MaterializationRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.dao.DeliveryAttemptRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.dao.NotificationRequestRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.entity.DeliveryAttempt;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.entity.NotificationRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.entity.Rendition;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class DeliveryWorkerService {

    private final NotificationRequestRepository notificationRequestRepository;
    private final DeliveryAttemptRepository deliveryAttemptRepository;
    private final RenditionService renditionService;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final ObjectProvider<InvitationDeliveryFacade> invitationDeliveryFacadeProvider;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final String fromAddress;

    public DeliveryWorkerService(
            NotificationRequestRepository notificationRequestRepository,
            DeliveryAttemptRepository deliveryAttemptRepository,
            RenditionService renditionService,
            ObjectProvider<JavaMailSender> mailSenderProvider,
            ObjectProvider<InvitationDeliveryFacade> invitationDeliveryFacadeProvider,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager,
            @Value("${spring.mail.username:noreply@mland.vn}") String fromAddress) {
        this.notificationRequestRepository = notificationRequestRepository;
        this.deliveryAttemptRepository = deliveryAttemptRepository;
        this.renditionService = renditionService;
        this.mailSenderProvider = mailSenderProvider;
        this.invitationDeliveryFacadeProvider = invitationDeliveryFacadeProvider;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.fromAddress = (fromAddress != null && !fromAddress.isBlank()) ? fromAddress : "noreply@mland.vn";
    }

    /**
     * Poll-based delivery worker polling processable requests every 30 seconds (FR-028).
     */
    @Scheduled(fixedDelay = 30000)
    public void pollAndDeliver() {
        try {
            Instant now = Instant.now();
            List<NotificationRequest> processable = notificationRequestRepository.findProcessableRequests(now, PageRequest.of(0, 10));
            if (processable.isEmpty()) {
                return;
            }

            log.info("DeliveryWorker: found {} processable request(s)", processable.size());
            for (NotificationRequest req : processable) {
                deliverSingleRequest(req.getId());
            }
        } catch (Exception e) {
            log.error("DeliveryWorker encountered unexpected error during polling loop", e);
        }
    }

    /**
     * Executes delivery for one notification request safely.
     */
    public void deliverSingleRequest(Long requestId) {
        // Step 1: Claim request and freeze rendition in dedicated transaction
        NotificationClaim claim = transactionTemplate.execute(status -> {
            NotificationRequest request = notificationRequestRepository.findById(requestId).orElse(null);
            if (request == null) {
                return null;
            }
            if (!"PENDING".equalsIgnoreCase(request.getDeliveryState()) &&
                !"RETRY_SCHEDULED".equalsIgnoreCase(request.getDeliveryState())) {
                return null; // Concurrently altered or cancelled
            }

            request.setDeliveryState("PROCESSING");
            request.setProcessingStartedAt(Instant.now());
            request.setSmtpInvocationStartedAt(Instant.now());

            Rendition rendition = renditionService.getOrCreateRendition(request);
            notificationRequestRepository.saveAndFlush(request);

            String recipientEmail = extractRecipientEmail(request.getRecipientSnapshot());
            return new NotificationClaim(request.getId(), recipientEmail, rendition.getRenderedSubject(),
                    rendition.getRenderedHtml(), rendition.getRenderedPlainText(), request.getAttemptCount(),
                    request.getTemplateCode(), request.getCredentialReference(), request.getCredentialExpiresAt(),
                    request.getBusinessEventReference());
        });

        if (claim == null) {
            return;
        }

        Instant attemptStart = Instant.now();
        boolean isInvitation = "INTERNAL_ACCOUNT_INVITATION".equalsIgnoreCase(claim.templateCode());
        String sendHtml = claim.html();
        String sendPlain = claim.plainText();

        if (isInvitation) {
            // Check creation-based expiry
            if (claim.credentialExpiresAt() != null && Instant.now().isAfter(claim.credentialExpiresAt())) {
                transactionTemplate.executeWithoutResult(status -> {
                    NotificationRequest req = notificationRequestRepository.findById(claim.requestId()).orElse(null);
                    if (req != null) {
                        req.setDeliveryState("CANCELLED");
                        req.setTerminalAt(Instant.now());
                        req.setNextAttemptAt(null);
                        notificationRequestRepository.save(req);
                    }
                });
                reportInvitationStatus(claim, "CANCELLED", Instant.now());
                log.info("Invitation request {} cancelled due to expiration", claim.requestId());
                return;
            }

            // Materialize URL transiently through FE-001 facade
            InvitationDeliveryFacade facade = invitationDeliveryFacadeProvider.getIfAvailable();
            if (facade == null) {
                recordAttemptAndScheduleRetry(claim.requestId(), claim.previousAttemptCount() + 1, attemptStart,
                        Instant.now(), "FAILED", null, "InvitationDeliveryFacade not available", isInvitation, claim);
                return;
            }

            Long invitationId = parseInvitationId(claim.credentialReference());
            int version = parseInvitationVersion(claim.credentialReference());

            MaterializationRequest matReq = MaterializationRequest.builder()
                    .invitationId(invitationId)
                    .version(version)
                    .eventReference(claim.businessEventReference())
                    .requestId(claim.requestId())
                    .purpose(MaterializationPurpose.SMTP_ATTEMPT)
                    .attemptId((long) (claim.previousAttemptCount() + 1))
                    .build();

            MaterializationOutcome outcome = facade.materialize(matReq);
            if (outcome == null || outcome.status() == MaterializationOutcome.Status.STALE) {
                transactionTemplate.executeWithoutResult(status -> {
                    NotificationRequest req = notificationRequestRepository.findById(claim.requestId()).orElse(null);
                    if (req != null) {
                        req.setDeliveryState("CANCELLED");
                        req.setTerminalAt(Instant.now());
                        req.setNextAttemptAt(null);
                        notificationRequestRepository.save(req);
                    }
                });
                log.info("Invitation request {} cancelled due to stale/superseded version", claim.requestId());
                return;
            }

            if (outcome.status() == MaterializationOutcome.Status.UNAVAILABLE) {
                recordAttemptAndScheduleRetry(claim.requestId(), claim.previousAttemptCount() + 1, attemptStart,
                        Instant.now(), "NETWORK_TEMPORARY", null, "Invitation materialization unavailable: " + outcome.safeReason(), isInvitation, claim);
                return;
            }

            // Substitute in memory only
            sendHtml = sendHtml.replace("{{setup_url}}", outcome.transientUrl());
            sendPlain = sendPlain.replace("{{setup_url}}", outcome.transientUrl());
        }

        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();

        if (mailSender == null) {
            recordAttemptAndScheduleRetry(claim.requestId(), claim.previousAttemptCount() + 1, attemptStart,
                    Instant.now(), "FAILED", null, "JavaMailSender bean is not configured in this environment", isInvitation, claim);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(claim.recipientEmail());
            helper.setSubject(claim.subject());
            helper.setText(sendPlain, sendHtml);

            mailSender.send(message);

            String messageId = message.getMessageID();
            recordSuccess(claim.requestId(), claim.previousAttemptCount() + 1, attemptStart, Instant.now(), messageId);
            if (isInvitation) {
                reportInvitationStatus(claim, "SENT", Instant.now());
            }

        } catch (Exception ex) {
            log.warn("SMTP attempt failed for notification request {}: {}", claim.requestId(), ex.getMessage());
            String outcomeCategory = classifyError(ex);
            String safeError = sanitizeError(ex.getMessage());
            recordAttemptAndScheduleRetry(claim.requestId(), claim.previousAttemptCount() + 1, attemptStart,
                    Instant.now(), outcomeCategory, null, safeError, isInvitation, claim);
        }
    }

    private void recordSuccess(Long requestId, int attemptNumber, Instant start, Instant end, String messageId) {
        transactionTemplate.executeWithoutResult(status -> {
            NotificationRequest request = notificationRequestRepository.findById(requestId).orElse(null);
            if (request == null) return;

            DeliveryAttempt attempt = new DeliveryAttempt();
            attempt.setNotificationRequest(request);
            attempt.setAttemptNumber(attemptNumber);
            attempt.setStartedAt(start);
            attempt.setCompletedAt(end);
            attempt.setOutcomeCategory("SUCCESS");
            attempt.setProviderMessageId(messageId);
            attempt.setSafeErrorSummary(null);
            deliveryAttemptRepository.save(attempt);

            request.setDeliveryState("SENT");
            request.setAttemptCount(attemptNumber);
            request.setTerminalAt(end);
            request.setNextAttemptAt(null);
            notificationRequestRepository.save(request);

            log.info("Notification request {} successfully delivered (attempt {})", requestId, attemptNumber);
        });
    }

    private void recordAttemptAndScheduleRetry(Long requestId, int attemptNumber, Instant start, Instant end,
                                               String outcomeCategory, String messageId, String safeError,
                                               boolean isInvitation, NotificationClaim claim) {
        transactionTemplate.executeWithoutResult(status -> {
            NotificationRequest request = notificationRequestRepository.findById(requestId).orElse(null);
            if (request == null) return;

            DeliveryAttempt attempt = new DeliveryAttempt();
            attempt.setNotificationRequest(request);
            attempt.setAttemptNumber(attemptNumber);
            attempt.setStartedAt(start);
            attempt.setCompletedAt(end);
            attempt.setOutcomeCategory(outcomeCategory);
            attempt.setProviderMessageId(messageId);
            attempt.setSafeErrorSummary(safeError);
            deliveryAttemptRepository.save(attempt);

            request.setAttemptCount(attemptNumber);

            if (isInvitation) {
                // Maximum 2 attempts for INTERNAL_ACCOUNT_INVITATION (FR-032)
                if ("REJECTED_PERMANENT".equalsIgnoreCase(outcomeCategory) || attemptNumber >= 2) {
                    request.setDeliveryState("FAILED");
                    request.setTerminalAt(end);
                    request.setNextAttemptAt(null);
                    reportInvitationStatus(claim, "FAILED", end);
                    log.warn("Invitation request {} reached terminal FAILED state after {} attempts", requestId, attemptNumber);
                } else if (attemptNumber == 1) {
                    request.setDeliveryState("RETRY_SCHEDULED");
                    request.setNextAttemptAt(end.plus(Duration.ofMinutes(1)));
                    reportInvitationStatus(claim, "RETRY_SCHEDULED", end);
                    log.info("Invitation request {} scheduled for retry 2 at {}", requestId, request.getNextAttemptAt());
                }
            } else {
                // Retry schedule: 1 minute for attempt 1, 5 minutes for attempt 2, max 3 attempts total (FR-013)
                if ("REJECTED_PERMANENT".equalsIgnoreCase(outcomeCategory) || attemptNumber >= 3) {
                    request.setDeliveryState("FAILED");
                    request.setTerminalAt(end);
                    request.setNextAttemptAt(null);
                    log.warn("Notification request {} reached terminal FAILED state after {} attempts", requestId, attemptNumber);
                } else if (attemptNumber == 1) {
                    request.setDeliveryState("RETRY_SCHEDULED");
                    request.setNextAttemptAt(end.plus(Duration.ofMinutes(1)));
                    log.info("Notification request {} scheduled for retry 2 at {}", requestId, request.getNextAttemptAt());
                } else if (attemptNumber == 2) {
                    request.setDeliveryState("RETRY_SCHEDULED");
                    request.setNextAttemptAt(end.plus(Duration.ofMinutes(5)));
                    log.info("Notification request {} scheduled for retry 3 at {}", requestId, request.getNextAttemptAt());
                }
            }
            notificationRequestRepository.save(request);
        });
    }

    private void reportInvitationStatus(NotificationClaim claim, String state, Instant now) {
        InvitationDeliveryFacade facade = invitationDeliveryFacadeProvider.getIfAvailable();
        if (facade != null && claim != null) {
            Long invId = parseInvitationId(claim.credentialReference());
            int ver = parseInvitationVersion(claim.credentialReference());
            if (invId != null) {
                facade.reportDeliveryState(new InvitationDeliveryReport(invId, ver, claim.requestId(), state, now));
            }
        }
    }

    private Long parseInvitationId(String credentialRef) {
        if (credentialRef == null || !credentialRef.contains(":")) return null;
        try {
            return Long.parseLong(credentialRef.split(":")[0]);
        } catch (Exception e) {
            return null;
        }
    }

    private int parseInvitationVersion(String credentialRef) {
        if (credentialRef == null || !credentialRef.contains(":")) return 1;
        try {
            return Integer.parseInt(credentialRef.split(":")[1]);
        } catch (Exception e) {
            return 1;
        }
    }

    private String classifyError(Exception ex) {
        if (ex instanceof MailParseException) {
            return "REJECTED_PERMANENT";
        }
        if (ex instanceof MailAuthenticationException) {
            return "REJECTED_PERMANENT";
        }
        String msg = ex.getMessage() != null ? ex.getMessage().toLowerCase() : "";
        if (msg.contains("timeout") || msg.contains("timed out")) {
            return "TIMEOUT";
        }
        if (msg.contains("refused") || msg.contains("connect") || msg.contains("unreachable")) {
            return "NETWORK_TEMPORARY";
        }
        if (msg.contains("550") || msg.contains("553") || msg.contains("unknown user") || msg.contains("invalid recipient")) {
            return "REJECTED_PERMANENT";
        }
        return "NETWORK_TEMPORARY";
    }

    private String sanitizeError(String raw) {
        if (raw == null) return "Unknown delivery error";
        String sanitized = raw.replaceAll("[a-zA-Z0-9_.+-]+@[a-zA-Z0-9-]+\\.[a-zA-Z0-9-.]+", "[REDACTED_EMAIL]");
        if (sanitized.length() > 500) {
            return sanitized.substring(0, 500);
        }
        return sanitized;
    }

    private String extractRecipientEmail(String recipientSnapshot) {
        try {
            Map<String, String> map = objectMapper.readValue(recipientSnapshot, new TypeReference<Map<String, String>>() {});
            return map.get("email");
        } catch (Exception e) {
            return "";
        }
    }

    private record NotificationClaim(
            Long requestId,
            String recipientEmail,
            String subject,
            String html,
            String plainText,
            int previousAttemptCount,
            String templateCode,
            String credentialReference,
            Instant credentialExpiresAt,
            String businessEventReference
    ) {}
}

