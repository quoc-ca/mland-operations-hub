package edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao.AccountRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.Account;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.enums.AccountRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.enums.AccountStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.TokenVerificationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.VerifiedIdentity;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.controller.dto.CreateTemplateVersionRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.controller.dto.RedeliverRequestCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.controller.dto.ResendRequestCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.entity.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.facade.NotificationFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.facade.NotificationIntentResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.facade.SubmitNotificationIntentCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.service.*;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:application-test.properties")
public class NotificationManagementIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private NotificationFacade notificationFacade;

    @Autowired
    private TemplateManagementService templateManagementService;

    @Autowired
    private OperationalService operationalService;

    @Autowired
    private DeliveryWorkerService deliveryWorkerService;

    @Autowired
    private RecoveryService recoveryService;

    @Autowired
    private RetentionService retentionService;

    @Autowired
    private RenditionService renditionService;

    @Autowired
    private TemplateDefinitionRepository templateDefinitionRepository;

    @Autowired
    private TemplateVersionRepository templateVersionRepository;

    @Autowired
    private NotificationRequestRepository notificationRequestRepository;

    @Autowired
    private DeliveryAttemptRepository deliveryAttemptRepository;

    @Autowired
    private OperationalAuditRepository operationalAuditRepository;

    @Autowired
    private RenditionRepository renditionRepository;

    @Autowired
    private TombstoneRepository tombstoneRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockBean
    private JavaMailSender javaMailSender;

    @MockBean
    private TokenVerificationService tokenVerificationService;

    private TemplateVersion bookingConfirmedVersion;

    @BeforeEach
    void setUp() {
        TemplateDefinition bookingDef = templateDefinitionRepository.findByCode("BOOKING_CONFIRMED")
                .orElseGet(() -> {
                    TemplateDefinition d = new TemplateDefinition();
                    d.setCode("BOOKING_CONFIRMED");
                    d.setName("Xác nhận đặt lịch");
                    d.setOwningModule("workshopbooking");
                    d.setLifecycleStatus("ACTIVE");
                    d.setAllowedPlaceholders("[\"customerName\", \"bookingCode\", \"ticketUrl\", \"orderCode\", \"orderTotal\"]");
                    return templateDefinitionRepository.save(d);
                });

        bookingConfirmedVersion = templateVersionRepository.findByTemplateDefinitionIdAndStatus(bookingDef.getId(), "ACTIVE")
                .orElseGet(() -> {
                    TemplateVersion v = new TemplateVersion();
                    v.setTemplateDefinition(bookingDef);
                    v.setVersionNumber(1);
                    v.setSubjectTemplate("Booking {{bookingCode}}");
                    v.setHtmlTemplate("<p>Dear {{customerName}}, your booking is {{bookingCode}}</p>");
                    v.setPlainTextTemplate("Dear {{customerName}}, your booking is {{bookingCode}}");
                    v.setStatus("ACTIVE");
                    v.setCreatedBy("system");
                    v.setActivatedBy("system");
                    v.setActivatedAt(Instant.now());
                    return templateVersionRepository.save(v);
                });

        TemplateDefinition retailDef = templateDefinitionRepository.findByCode("RETAIL_ORDER_CONFIRMED")
                .orElseGet(() -> {
                    TemplateDefinition d = new TemplateDefinition();
                    d.setCode("RETAIL_ORDER_CONFIRMED");
                    d.setName("Xác nhận đơn hàng");
                    d.setOwningModule("readyringsales");
                    d.setLifecycleStatus("ACTIVE");
                    d.setAllowedPlaceholders("[\"customerName\", \"orderCode\", \"orderTotal\"]");
                    return templateDefinitionRepository.save(d);
                });

        templateVersionRepository.findByTemplateDefinitionIdAndStatus(retailDef.getId(), "ACTIVE")
                .orElseGet(() -> {
                    TemplateVersion v = new TemplateVersion();
                    v.setTemplateDefinition(retailDef);
                    v.setVersionNumber(1);
                    v.setSubjectTemplate("Order {{orderCode}}");
                    v.setHtmlTemplate("<p>Dear {{customerName}}, your order is {{orderCode}}</p>");
                    v.setPlainTextTemplate("Dear {{customerName}}, your order is {{orderCode}}");
                    v.setStatus("ACTIVE");
                    v.setCreatedBy("system");
                    v.setActivatedBy("system");
                    v.setActivatedAt(Instant.now());
                    return templateVersionRepository.save(v);
                });
    }

    @Test
    @DisplayName("ITC-007-001: In-transaction notification request rolls back completely on caller business exception")
    void verifyRollbackSafetyOnBusinessTransactionException() {
        SubmitNotificationIntentCommand cmd = SubmitNotificationIntentCommand.builder()
                .businessEventReference("BK-TEST-ROLLBACK-001")
                .templateCode("BOOKING_CONFIRMED")
                .recipientEmail("customer.rollback@example.com")
                .recipientName("Tester Rollback")
                .businessDataSnapshot(Map.of("customerName", "Tester Rollback", "bookingCode", "BK-TEST-ROLLBACK-001"))
                .build();

        TransactionTemplate tt = new TransactionTemplate(transactionManager);
        tt.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        assertThatThrownBy(() -> tt.execute(status -> {
            NotificationIntentResult result = notificationFacade.submitNotificationIntent(cmd);
            assertThat(result.getStatus()).isEqualTo(NotificationIntentResult.Status.ENQUEUED);
            throw new RuntimeException("Simulated payment confirmation failure");
        })).hasMessageContaining("Simulated payment confirmation failure");

        List<NotificationRequest> found = notificationRequestRepository.findAll().stream()
                .filter(r -> "BK-TEST-ROLLBACK-001".equals(r.getBusinessEventReference()))
                .toList();
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("ITC-007-002: Concurrent event submission produces exactly one durable request and DUPLICATE_IGNORED for others")
    void verifyConcurrentEventSubmissionProducesExactlyOneRequest() throws Exception {
        SubmitNotificationIntentCommand cmd = SubmitNotificationIntentCommand.builder()
                .businessEventReference("ORD-CONCUR-2026-001")
                .templateCode("RETAIL_ORDER_CONFIRMED")
                .recipientEmail("buyer.idempotent@example.com")
                .businessDataSnapshot(Map.of(
                        "customerName", "Buyer A",
                        "orderCode", "ORD-CONCUR-2026-001",
                        "orderTotal", "1,500,000 VND"
                ))
                .build();

        int concurrency = 5;
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<NotificationIntentResult>> futures = new ArrayList<>();

        for (int i = 0; i < concurrency; i++) {
            futures.add(executor.submit(() -> {
                startLatch.await();
                return notificationFacade.submitNotificationIntent(cmd);
            }));
        }

        startLatch.countDown();
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        List<NotificationIntentResult> results = new ArrayList<>();
        for (Future<NotificationIntentResult> f : futures) {
            results.add(f.get());
        }

        long enqueuedCount = results.stream()
                .filter(r -> r.getStatus() == NotificationIntentResult.Status.ENQUEUED)
                .count();
        long duplicateCount = results.stream()
                .filter(r -> r.getStatus() == NotificationIntentResult.Status.DUPLICATE_IGNORED)
                .count();

        assertThat(enqueuedCount).isEqualTo(1);
        assertThat(duplicateCount).isEqualTo(4);

        Long originalId = results.stream()
                .filter(r -> r.getStatus() == NotificationIntentResult.Status.ENQUEUED)
                .findFirst().get().getNotificationRequestId();

        for (NotificationIntentResult r : results) {
            assertThat(r.getNotificationRequestId()).isEqualTo(originalId);
        }

        List<NotificationRequest> saved = notificationRequestRepository.findAll().stream()
                .filter(r -> "ORD-CONCUR-2026-001".equals(r.getBusinessEventReference()))
                .toList();
        assertThat(saved).hasSize(1);
    }

    @Test
    @DisplayName("ITC-007-003: Scheduled delivery worker polls pending requests and delivers rendered email via SMTP")
    void verifyWorkerPollAndGreenMailDelivery() {
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);

        SubmitNotificationIntentCommand cmd = SubmitNotificationIntentCommand.builder()
                .businessEventReference("BK-E2E-INT-001")
                .templateCode("BOOKING_CONFIRMED")
                .recipientEmail("customer.real@example.com")
                .recipientName("Nguyen Van A")
                .businessDataSnapshot(Map.of(
                        "customerName", "Nguyen Van A",
                        "bookingCode", "BK-E2E-INT-001",
                        "ticketUrl", "https://mland.vn/tickets/sample-123"
                ))
                .build();

        NotificationIntentResult enqueued = notificationFacade.submitNotificationIntent(cmd);
        assertThat(enqueued.getStatus()).isEqualTo(NotificationIntentResult.Status.ENQUEUED);

        deliveryWorkerService.pollAndDeliver();

        NotificationRequest updated = notificationRequestRepository.findById(enqueued.getNotificationRequestId()).orElseThrow();
        assertThat(updated.getDeliveryState()).isEqualTo("SENT");
        assertThat(updated.getAttemptCount()).isEqualTo(1);
        assertThat(updated.getTerminalAt()).isNotNull();

        verify(javaMailSender, atLeastOnce()).send(any(MimeMessage.class));

        Optional<Rendition> rendition = renditionRepository.findByNotificationRequestId(updated.getId());
        assertThat(rendition).isPresent();
        assertThat(rendition.get().getRenderedSubject()).contains("BK-E2E-INT-001");
        assertThat(rendition.get().getRenderedHtml()).contains("Nguyen Van A");

        List<DeliveryAttempt> attempts = deliveryAttemptRepository.findByNotificationRequestIdOrderByAttemptNumberAsc(updated.getId());
        assertThat(attempts).hasSize(1);
        assertThat(attempts.get(0).getOutcomeCategory()).isEqualTo("SUCCESS");
    }

    @Test
    @DisplayName("ITC-007-004: Application restart transitions interrupted delivery with started SMTP to UNKNOWN_OUTCOME")
    void verifyStartupRecoveryTransitionsCrashToUnknownOutcome() {
        NotificationRequest crashedReq = new NotificationRequest();
        crashedReq.setBusinessEventReference("BK-CRASH-TEST-999");
        crashedReq.setTemplateCode("BOOKING_CONFIRMED");
        crashedReq.setTemplateVersion(bookingConfirmedVersion);
        crashedReq.setRecipientRole("CUSTOMER");
        crashedReq.setRecipientAddressFingerprint("fp_crash");
        crashedReq.setRecipientAddressMasked("c***h@example.com");
        crashedReq.setRecipientSnapshot("{\"email\":\"crash@example.com\"}");
        crashedReq.setBusinessDataSnapshot("{\"bookingCode\":\"BK-CRASH-TEST-999\"}");
        crashedReq.setDeliveryState("PROCESSING");
        crashedReq.setProcessingStartedAt(Instant.now().minusSeconds(60));
        crashedReq.setSmtpInvocationStartedAt(Instant.now().minusSeconds(30));
        crashedReq.setAttemptCount(1);
        crashedReq = notificationRequestRepository.save(crashedReq);

        int recovered = recoveryService.recoverInterruptedRequests();
        assertThat(recovered).isGreaterThanOrEqualTo(1);

        NotificationRequest refreshed = notificationRequestRepository.findById(crashedReq.getId()).orElseThrow();
        assertThat(refreshed.getDeliveryState()).isEqualTo("UNKNOWN_OUTCOME");

        reset(javaMailSender);
        deliveryWorkerService.pollAndDeliver();

        verify(javaMailSender, never()).send(any(MimeMessage.class));
        NotificationRequest afterPoll = notificationRequestRepository.findById(crashedReq.getId()).orElseThrow();
        assertThat(afterPoll.getDeliveryState()).isEqualTo("UNKNOWN_OUTCOME");
    }

    @Test
    @DisplayName("ITC-007-005: Notification requests remain pinned to their creation version despite template activation")
    void verifyTemplateVersionPinningPreservesEarlierRendition() {
        // 1. Enqueue Request #1 with Version 1 active
        SubmitNotificationIntentCommand cmd1 = SubmitNotificationIntentCommand.builder()
                .businessEventReference("BK-PIN-001")
                .templateCode("BOOKING_CONFIRMED")
                .recipientEmail("customer1@example.com")
                .businessDataSnapshot(Map.of("bookingCode", "BK-PIN-001", "customerName", "Customer 1"))
                .build();
        NotificationIntentResult res1 = notificationFacade.submitNotificationIntent(cmd1);
        assertThat(res1.getStatus()).isEqualTo(NotificationIntentResult.Status.ENQUEUED);

        TransactionTemplate tt = new TransactionTemplate(transactionManager);
        int initialVerNumber = tt.execute(status -> {
            NotificationRequest r1 = notificationRequestRepository.findById(res1.getNotificationRequestId()).orElseThrow();
            return r1.getTemplateVersion().getVersionNumber();
        });

        // 2. Create Draft Version 2
        CreateTemplateVersionRequest draftReq = CreateTemplateVersionRequest.builder()
                .subjectTemplate("[V2] Updated Booking #{{bookingCode}}")
                .htmlTemplate("<p>[V2] Hello {{customerName}}</p>")
                .plainTextTemplate("[V2] Hello {{customerName}}")
                .build();
        var draftDto = templateManagementService.createDraft("BOOKING_CONFIRMED", draftReq, "admin_user");
        int newVerNumber = draftDto.versionNumber();

        // 3. Activate Version 2
        templateManagementService.activateVersion("BOOKING_CONFIRMED", newVerNumber, "admin_user");

        // 4. Enqueue Request #2 with Version 2 active
        SubmitNotificationIntentCommand cmd2 = SubmitNotificationIntentCommand.builder()
                .businessEventReference("BK-PIN-002")
                .templateCode("BOOKING_CONFIRMED")
                .recipientEmail("customer2@example.com")
                .businessDataSnapshot(Map.of("bookingCode", "BK-PIN-002", "customerName", "Customer 2"))
                .build();
        NotificationIntentResult res2 = notificationFacade.submitNotificationIntent(cmd2);
        assertThat(res2.getStatus()).isEqualTo(NotificationIntentResult.Status.ENQUEUED);

        // Assert pinning inside transaction
        tt.executeWithoutResult(status -> {
            NotificationRequest r1 = notificationRequestRepository.findById(res1.getNotificationRequestId()).orElseThrow();
            NotificationRequest r2 = notificationRequestRepository.findById(res2.getNotificationRequestId()).orElseThrow();

            assertThat(r1.getTemplateVersion().getVersionNumber()).isEqualTo(initialVerNumber);
            assertThat(r2.getTemplateVersion().getVersionNumber()).isEqualTo(newVerNumber);

            Rendition rend1 = renditionService.getOrCreateRendition(r1);
            Rendition rend2 = renditionService.getOrCreateRendition(r2);

            assertThat(rend1.getRenderedSubject()).doesNotContain("[V2]");
            assertThat(rend2.getRenderedSubject()).contains("[V2]");
        });
    }

    @Test
    @DisplayName("ITC-007-006: REST controller endpoints enforce global role authorization matrix")
    void verifyRestEndpointsRbacMatrix() throws Exception {
        // 1. Anonymous request returns 401
        mockMvc.perform(get("/api/v1/notification-templates"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/notification-requests"))
                .andExpect(status().isUnauthorized());

        // Setup accounts
        createOrGetAccount("staff-uid", "staff@mland.vn", AccountRole.STAFF);
        createOrGetAccount("member-uid", "member@mland.vn", AccountRole.MEMBER);
        createOrGetAccount("manager-uid", "mgr@mland.vn", AccountRole.MANAGER);
        createOrGetAccount("admin-uid", "admin@mland.vn", AccountRole.ADMIN);

        when(tokenVerificationService.verify("staff-token"))
                .thenReturn(new VerifiedIdentity("staff-uid", "staff@mland.vn", true, Instant.now()));
        when(tokenVerificationService.verify("member-token"))
                .thenReturn(new VerifiedIdentity("member-uid", "member@mland.vn", true, Instant.now()));
        when(tokenVerificationService.verify("manager-token"))
                .thenReturn(new VerifiedIdentity("manager-uid", "mgr@mland.vn", true, Instant.now()));
        when(tokenVerificationService.verify("admin-token"))
                .thenReturn(new VerifiedIdentity("admin-uid", "admin@mland.vn", true, Instant.now()));

        // 2. STAFF and MEMBER get 403 Forbidden
        mockMvc.perform(get("/api/v1/notification-templates")
                .header("Authorization", "Bearer staff-token"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/notification-templates")
                .header("Authorization", "Bearer member-token"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/notification-requests")
                .header("Authorization", "Bearer staff-token"))
                .andExpect(status().isForbidden());

        // 3. MANAGER gets 200 OK for GET templates and GET requests
        mockMvc.perform(get("/api/v1/notification-templates")
                .header("Authorization", "Bearer manager-token"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/notification-requests")
                .header("Authorization", "Bearer manager-token"))
                .andExpect(status().isOk());

        // Create a SENT request and an UNKNOWN_OUTCOME request for resend testing
        NotificationRequest sentReq = createTestRequest("SENT");
        NotificationRequest unknownReq = createTestRequest("UNKNOWN_OUTCOME");

        ResendRequestCommand resendCmd = ResendRequestCommand.builder()
                .reason("Customer request")
                .duplicateRiskAccepted(false)
                .build();

        // MANAGER can resend SENT request -> 201 Created
        mockMvc.perform(post("/api/v1/notification-requests/" + sentReq.getId() + "/resend")
                .header("Authorization", "Bearer manager-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(resendCmd)))
                .andExpect(status().isCreated());

        // MANAGER cannot resend UNKNOWN_OUTCOME request -> 403 Forbidden
        mockMvc.perform(post("/api/v1/notification-requests/" + unknownReq.getId() + "/resend")
                .header("Authorization", "Bearer manager-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(resendCmd)))
                .andExpect(status().isForbidden());

        // ADMIN can resend UNKNOWN_OUTCOME with duplicateRiskAccepted = true -> 201 Created
        ResendRequestCommand adminRiskCmd = ResendRequestCommand.builder()
                .reason("Admin risk accepted resend")
                .duplicateRiskAccepted(true)
                .build();

        mockMvc.perform(post("/api/v1/notification-requests/" + unknownReq.getId() + "/resend")
                .header("Authorization", "Bearer admin-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(adminRiskCmd)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("ITC-007-007: Retention job sweeps requests older than 90 days from DB and creates persistent tombstone")
    void verifyRetentionJobScrubsPiiAndGeneratesTombstoneInDb() {
        NotificationRequest oldReq = new NotificationRequest();
        oldReq.setBusinessEventReference("BK-RETENTION-888");
        oldReq.setTemplateCode("BOOKING_CONFIRMED");
        oldReq.setTemplateVersion(bookingConfirmedVersion);
        oldReq.setRecipientRole("CUSTOMER");
        oldReq.setRecipientAddressFingerprint("fp_retention");
        oldReq.setRecipientAddressMasked("c***l@example.com");
        oldReq.setRecipientSnapshot("{\"email\": \"confidential@example.com\"}");
        oldReq.setBusinessDataSnapshot("{\"bookingCode\":\"BK-RETENTION-888\",\"customerName\":\"VIP\"}");
        oldReq.setDeliveryState("SENT");
        oldReq.setAttemptCount(1);
        oldReq.setCreatedAt(Instant.now().minus(95, ChronoUnit.DAYS));
        oldReq.setTerminalAt(Instant.now().minus(92, ChronoUnit.DAYS));
        oldReq = notificationRequestRepository.save(oldReq);

        Rendition rendition = new Rendition();
        rendition.setNotificationRequest(oldReq);
        rendition.setRenderedSubject("Subject 888");
        rendition.setRenderedHtml("<p>Secret content</p>");
        rendition.setRenderedPlainText("Secret content");
        renditionRepository.save(rendition);

        int scrubbed = retentionService.sweepExpiredData(90);
        assertThat(scrubbed).isGreaterThanOrEqualTo(1);

        NotificationRequest scrubbedReq = notificationRequestRepository.findById(oldReq.getId()).orElseThrow();
        assertThat(scrubbedReq.getRecipientSnapshot()).isEqualTo("[SCRUBBED]");
        assertThat(scrubbedReq.getBusinessDataSnapshot()).isEqualTo("[SCRUBBED]");

        Optional<Rendition> afterRendition = renditionRepository.findByNotificationRequestId(oldReq.getId());
        assertThat(afterRendition).isEmpty();

        Optional<Tombstone> tombstone = tombstoneRepository.findByOriginalRequestId(oldReq.getId());
        assertThat(tombstone).isPresent();
        assertThat(tombstone.get().getBusinessEventReference()).isEqualTo("BK-RETENTION-888");
        assertThat(tombstone.get().getTerminalState()).isEqualTo("SENT");
    }

    @Test
    @DisplayName("ITC-007-008: Redelivery with refreshed credential communicates across module facade to reissue link")
    void verifyRedeliverWithRefreshedCredentialCrossModule() {
        NotificationRequest original = createTestRequest("SENT");
        original.setBusinessDataSnapshot("{\"bookingCode\":\"BK-ORIGINAL-700\"}");
        original.setCredentialReference("CRED-OLD-700");
        original = notificationRequestRepository.save(original);

        edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor managerActor =
                new edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor(
                        1L,
                        edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole.MANAGER,
                        edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus.ACTIVE,
                        true,
                        List.of()
                );
        RedeliverRequestCommand cmd = RedeliverRequestCommand.builder()
                .reason("Khách hàng yêu cầu cấp lại link sau 24h")
                .build();

        var response = operationalService.redeliverWithRefreshedCredential(original.getId(), managerActor, cmd);
        assertThat(response.newRequestId()).isNotNull();
        assertThat(response.originalRequestId()).isEqualTo(original.getId());
        assertThat(response.deliveryState()).isEqualTo("PENDING");

        NotificationRequest newReq = notificationRequestRepository.findById(response.newRequestId()).orElseThrow();
        assertThat(newReq.getOriginalRequest().getId()).isEqualTo(original.getId());
        assertThat(newReq.getBusinessDataSnapshot()).isEqualTo(original.getBusinessDataSnapshot());
        assertThat(newReq.getDeliveryState()).isEqualTo("PENDING");

        NotificationRequest origAfter = notificationRequestRepository.findById(original.getId()).orElseThrow();
        assertThat(origAfter.getDeliveryState()).isEqualTo("SENT");

        List<OperationalAuditRecord> audits = operationalAuditRepository.findByNotificationRequestIdOrderByCreatedAtAsc(original.getId());
        assertThat(audits).anyMatch(a -> "REDELIVER_REFRESHED_CREDENTIAL".equals(a.getActionType()));
    }

    private Account createOrGetAccount(String uid, String email, AccountRole role) {
        return accountRepository.findByFirebaseUid(uid).orElseGet(() -> {
            Account a = Account.builder()
                    .firebaseUid(uid)
                    .loginEmail(email)
                    .role(role)
                    .status(AccountStatus.ACTIVE)
                    .build();
            return accountRepository.save(a);
        });
    }

    private NotificationRequest createTestRequest(String state) {
        NotificationRequest req = new NotificationRequest();
        req.setBusinessEventReference("EVT-" + UUID.randomUUID());
        req.setTemplateCode("BOOKING_CONFIRMED");
        req.setTemplateVersion(bookingConfirmedVersion);
        req.setRecipientRole("CUSTOMER");
        req.setRecipientAddressFingerprint("fp_" + UUID.randomUUID());
        req.setRecipientAddressMasked("c***r@example.com");
        req.setRecipientSnapshot("{\"email\":\"customer@example.com\"}");
        req.setBusinessDataSnapshot("{\"bookingCode\":\"BK-TEST\"}");
        req.setDeliveryState(state);
        req.setAttemptCount("SENT".equals(state) ? 1 : 0);
        if ("SENT".equals(state) || "UNKNOWN_OUTCOME".equals(state)) {
            req.setTerminalAt(Instant.now());
        }
        return notificationRequestRepository.save(req);
    }
}
