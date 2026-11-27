package edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao.AccountRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.Account;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.InternalAccountInvitation;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.enums.AccountRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.enums.AccountStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.InvitationDeliveryFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.MaterializationOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.InvitationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.TokenVerificationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.VerifiedIdentity;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.controller.dto.CancelRequestCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.controller.dto.ResendRequestCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.dao.DeliveryAttemptRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.dao.NotificationRequestRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.dao.RenditionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.dao.TemplateDefinitionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.dao.TemplateVersionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.entity.DeliveryAttempt;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.entity.NotificationRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.entity.Rendition;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.entity.TemplateDefinition;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.entity.TemplateVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:application-test.properties")
class InternalAccountInvitationNotificationIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TokenVerificationService tokenVerificationService;

    @MockBean
    private InvitationService invitationService;

    @Autowired
    private NotificationRequestRepository notificationRequestRepository;

    @Autowired
    private DeliveryAttemptRepository deliveryAttemptRepository;

    @Autowired
    private RenditionRepository renditionRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TemplateVersionRepository templateVersionRepository;

    @Autowired
    private TemplateDefinitionRepository templateDefinitionRepository;

    private String adminToken;
    private String adminUid;
    private Account adminAccount;

    private String managerToken;
    private String managerUid;
    private Account managerAccount;

    @BeforeEach
    void setUp() {
        adminUid = "admin-notif-" + UUID.randomUUID();
        adminToken = "admin-notif-token-" + UUID.randomUUID();
        adminAccount = accountRepository.save(Account.builder()
                .firebaseUid(adminUid)
                .role(AccountRole.ADMIN)
                .status(AccountStatus.ACTIVE)
                .build());

        when(tokenVerificationService.verify(eq(adminToken)))
                .thenReturn(new VerifiedIdentity(adminUid, "admin@mland.vn", true, Instant.now()));

        managerUid = "mgr-notif-" + UUID.randomUUID();
        managerToken = "mgr-notif-token-" + UUID.randomUUID();
        managerAccount = accountRepository.save(Account.builder()
                .firebaseUid(managerUid)
                .role(AccountRole.MANAGER)
                .status(AccountStatus.ACTIVE)
                .build());

        when(tokenVerificationService.verify(eq(managerToken)))
                .thenReturn(new VerifiedIdentity(managerUid, "manager@mland.vn", true, Instant.now()));
    }

    private TemplateVersion getOrCreateInvitationTemplateVersion() {
        return templateVersionRepository.findByTemplateDefinitionCodeAndStatus("INTERNAL_ACCOUNT_INVITATION", "ACTIVE")
                .orElseGet(() -> {
                    TemplateDefinition def = templateDefinitionRepository.findByCode("INTERNAL_ACCOUNT_INVITATION")
                            .orElseGet(() -> {
                                TemplateDefinition d = new TemplateDefinition();
                                d.setCode("INTERNAL_ACCOUNT_INVITATION");
                                d.setName("Thư mời tạo tài khoản nội bộ");
                                d.setOwningModule("ACCOUNT_MANAGEMENT");
                                d.setLifecycleStatus("ACTIVE");
                                d.setAllowedPlaceholders("[\"recipient_name\", \"setup_url\", \"expiry_time\"]");
                                d.setCreatedAt(Instant.now());
                                d.setUpdatedAt(Instant.now());
                                return templateDefinitionRepository.save(d);
                            });

                    TemplateVersion tv = new TemplateVersion();
                    tv.setTemplateDefinition(def);
                    tv.setVersionNumber(1);
                    tv.setStatus("ACTIVE");
                    tv.setCreatedBy("system");
                    tv.setSubjectTemplate("Thư mời tạo tài khoản nội bộ");
                    tv.setHtmlTemplate("<p>Truy cập: {{setup_url}}</p>");
                    tv.setPlainTextTemplate("Truy cap: {{setup_url}}");
                    tv.setCreatedAt(Instant.now());
                    tv.setUpdatedAt(Instant.now());
                    return templateVersionRepository.save(tv);
                });
    }

    @Test
    @DisplayName("T042: Manual invitation content retrieval by ADMIN after 2 definite failures returns 200 with transient link")
    void getFailedInvitationManualContent_success() throws Exception {
        TemplateVersion tv = getOrCreateInvitationTemplateVersion();
        NotificationRequest request = new NotificationRequest();
        request.setBusinessEventReference("INVITATION-100-v1");
        request.setTemplateCode("INTERNAL_ACCOUNT_INVITATION");
        request.setTemplateVersion(tv);
        request.setRecipientRole("STAFF");
        request.setDeliveryState("FAILED");
        request.setAttemptCount(2);
        request.setRecipientAddressMasked("s***f@mland.vn");
        request.setRecipientAddressFingerprint("sha256-dummy");
        request.setRecipientSnapshot("{\"email\":\"staff@mland.vn\"}");
        request.setBusinessDataSnapshot("{\"setup_url\":\"{{setup_url}}\"}");
        request.setCredentialReference("100:1");
        request.setCredentialExpiresAt(Instant.now().plusSeconds(86400));
        request.setCreatedAt(Instant.now());
        request.setTerminalAt(Instant.now());
        request = notificationRequestRepository.save(request);

        // Add 2 failed delivery attempts
        DeliveryAttempt a1 = new DeliveryAttempt();
        a1.setNotificationRequest(request);
        a1.setAttemptNumber(1);
        a1.setStartedAt(Instant.now().minusSeconds(60));
        a1.setCompletedAt(Instant.now().minusSeconds(55));
        a1.setOutcomeCategory("NETWORK_TEMPORARY");
        a1.setSafeErrorSummary("Timeout");
        deliveryAttemptRepository.save(a1);

        DeliveryAttempt a2 = new DeliveryAttempt();
        a2.setNotificationRequest(request);
        a2.setAttemptNumber(2);
        a2.setStartedAt(Instant.now().minusSeconds(30));
        a2.setCompletedAt(Instant.now().minusSeconds(25));
        a2.setOutcomeCategory("REJECTED_PERMANENT");
        a2.setSafeErrorSummary("550 User not found");
        deliveryAttemptRepository.save(a2);

        // Add rendition with {{setup_url}}
        Rendition rendition = new Rendition();
        rendition.setNotificationRequest(request);
        rendition.setRenderedSubject("Thư mời tham gia Mland: {{setup_url}}");
        rendition.setRenderedHtml("<p>Truy cập link sau để thiết lập: <a href=\"{{setup_url}}\">{{setup_url}}</a></p>");
        rendition.setRenderedPlainText("Truy cap: {{setup_url}}");
        renditionRepository.save(rendition);

        // Mock materialization facade response
        String transientUrl = "https://hub.mland.vn/invitations/setup?secret=mock_transient_secret_999";
        when(invitationService.materialize(any()))
                .thenReturn(MaterializationOutcome.available(transientUrl, Instant.now().plusSeconds(86400)));

        mockMvc.perform(get("/api/v1/admin/notification-requests/" + request.getId() + "/manual-invitation")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Pragma", "no-cache"))
                .andExpect(jsonPath("$.requestId").value(request.getId()))
                .andExpect(jsonPath("$.invitationVersion").value(1))
                .andExpect(jsonPath("$.html").value("<p>Truy cập link sau để thiết lập: <a href=\"" + transientUrl + "\">" + transientUrl + "</a></p>"))
                .andExpect(jsonPath("$.plainText").value("Truy cap: " + transientUrl));
    }

    @Test
    @DisplayName("T042: Non-ADMIN (MANAGER) cannot access manual invitation content -> 403")
    void getFailedInvitationManualContent_forbiddenForManager() throws Exception {
        mockMvc.perform(get("/api/v1/admin/notification-requests/999/manual-invitation")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("T042: Request not in FAILED state cannot retrieve manual content -> 409")
    void getFailedInvitationManualContent_notFailed_conflict() throws Exception {
        TemplateVersion tv = getOrCreateInvitationTemplateVersion();
        NotificationRequest request = new NotificationRequest();
        request.setBusinessEventReference("INVITATION-101-v1");
        request.setTemplateCode("INTERNAL_ACCOUNT_INVITATION");
        request.setTemplateVersion(tv);
        request.setRecipientRole("STAFF");
        request.setDeliveryState("PENDING");
        request.setAttemptCount(0);
        request.setRecipientAddressMasked("s***f@mland.vn");
        request.setRecipientAddressFingerprint("sha256-dummy");
        request.setRecipientSnapshot("{\"email\":\"staff@mland.vn\"}");
        request.setBusinessDataSnapshot("{\"setup_url\":\"{{setup_url}}\"}");
        request.setCredentialReference("101:1");
        request.setCreatedAt(Instant.now());
        request = notificationRequestRepository.save(request);

        mockMvc.perform(get("/api/v1/admin/notification-requests/" + request.getId() + "/manual-invitation")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REQUEST_NOT_FAILED"));
    }

    @Test
    @DisplayName("T042: Generic resend rejects INTERNAL_ACCOUNT_INVITATION -> 400")
    void genericResend_rejectsInvitation() throws Exception {
        TemplateVersion tv = getOrCreateInvitationTemplateVersion();
        NotificationRequest request = new NotificationRequest();
        request.setBusinessEventReference("INVITATION-102-v1");
        request.setTemplateCode("INTERNAL_ACCOUNT_INVITATION");
        request.setTemplateVersion(tv);
        request.setRecipientRole("STAFF");
        request.setDeliveryState("SENT");
        request.setAttemptCount(1);
        request.setRecipientAddressMasked("s***f@mland.vn");
        request.setRecipientAddressFingerprint("sha256-dummy");
        request.setRecipientSnapshot("{\"email\":\"staff@mland.vn\"}");
        request.setBusinessDataSnapshot("{\"setup_url\":\"{{setup_url}}\"}");
        request.setCredentialReference("102:1");
        request.setCreatedAt(Instant.now());
        request = notificationRequestRepository.save(request);

        ResendRequestCommand cmd = new ResendRequestCommand("Resend please", false);
        mockMvc.perform(post("/api/v1/notification-requests/" + request.getId() + "/resend")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cmd)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVITATION_RESEND_FORBIDDEN"));
    }

    @Test
    @DisplayName("T042: Generic cancel rejects INTERNAL_ACCOUNT_INVITATION -> 400")
    void genericCancel_rejectsInvitation() throws Exception {
        TemplateVersion tv = getOrCreateInvitationTemplateVersion();
        NotificationRequest request = new NotificationRequest();
        request.setBusinessEventReference("INVITATION-103-v1");
        request.setTemplateCode("INTERNAL_ACCOUNT_INVITATION");
        request.setTemplateVersion(tv);
        request.setRecipientRole("STAFF");
        request.setDeliveryState("PENDING");
        request.setAttemptCount(0);
        request.setRecipientAddressMasked("s***f@mland.vn");
        request.setRecipientAddressFingerprint("sha256-dummy");
        request.setRecipientSnapshot("{\"email\":\"staff@mland.vn\"}");
        request.setBusinessDataSnapshot("{\"setup_url\":\"{{setup_url}}\"}");
        request.setCredentialReference("103:1");
        request.setCreatedAt(Instant.now());
        request = notificationRequestRepository.save(request);

        CancelRequestCommand cmd = new CancelRequestCommand("Cancel please");
        mockMvc.perform(post("/api/v1/notification-requests/" + request.getId() + "/cancel")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cmd)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVITATION_CANCEL_FORBIDDEN"));
    }
}
