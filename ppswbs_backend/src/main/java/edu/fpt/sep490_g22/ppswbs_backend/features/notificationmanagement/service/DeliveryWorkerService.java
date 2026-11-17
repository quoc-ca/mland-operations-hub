package edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final String fromAddress;

    public DeliveryWorkerService(
            NotificationRequestRepository notificationRequestRepository,
            DeliveryAttemptRepository deliveryAttemptRepository,
            RenditionService renditionService,
            ObjectProvider<JavaMailSender> mailSenderProvider,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager,
            @Value("${spring.mail.username:noreply@mland.vn}") String fromAddress) {
        this.notificationRequestRepository = notificationRequestRepository;
        this.deliveryAttemptRepository = deliveryAttemptRepository;
        this.renditionService = renditionService;
        this.mailSenderProvider = mailSenderProvider;
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
                    rendition.getRenderedHtml(), rendition.getRenderedPlainText(), request.getAttemptCount());
        });

        if (claim == null) {
            return;
        }

        Instant attemptStart = Instant.now();
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();

        if (mailSender == null) {
            recordAttemptAndScheduleRetry(claim.requestId(), claim.previousAttemptCount() + 1, attemptStart,
                    Instant.now(), "FAILED", null, "JavaMailSender bean is not configured in this environment");
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(claim.recipientEmail());
            helper.setSubject(claim.subject());
            helper.setText(claim.plainText(), claim.html());

            mailSender.send(message);

            String messageId = message.getMessageID();
            recordSuccess(claim.requestId(), claim.previousAttemptCount() + 1, attemptStart, Instant.now(), messageId);

        } catch (Exception ex) {
            log.warn("SMTP attempt failed for notification request {}: {}", claim.requestId(), ex.getMessage());
            String outcomeCategory = classifyError(ex);
            String safeError = sanitizeError(ex.getMessage());
            recordAttemptAndScheduleRetry(claim.requestId(), claim.previousAttemptCount() + 1, attemptStart,
                    Instant.now(), outcomeCategory, null, safeError);
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
                                               String outcomeCategory, String messageId, String safeError) {
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
            notificationRequestRepository.save(request);
        });
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

    private record NotificationClaim(Long requestId, String recipientEmail, String subject, String html, String plainText, int previousAttemptCount) {}
}

