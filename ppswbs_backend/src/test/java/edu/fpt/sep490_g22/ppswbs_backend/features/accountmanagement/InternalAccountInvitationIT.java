package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao.AccountProfileRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao.AccountRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao.InternalAccountInvitationRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dto.request.InternalAccountCreateRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dto.request.InvitationCompletionRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dto.request.InvitationExchangeRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dto.request.RetirementCommandRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dto.response.InvitationExchangeResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.Account;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.InternalAccountInvitation;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.enums.AccountRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.enums.AccountStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.InvitationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.TokenVerificationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.VerifiedIdentity;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:application-test.properties")
class InternalAccountInvitationIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TokenVerificationService tokenVerificationService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private AccountProfileRepository profileRepository;

    @Autowired
    private InternalAccountInvitationRepository invitationRepository;

    @Autowired
    private InvitationService invitationService;

    private String adminToken;
    private String adminUid;
    private Account adminAccount;

    @BeforeEach
    void setUp() {
        adminUid = "admin-uid-" + UUID.randomUUID();
        adminToken = "admin-test-token-" + UUID.randomUUID();

        adminAccount = accountRepository.save(Account.builder()
                .firebaseUid(adminUid)
                .role(AccountRole.ADMIN)
                .status(AccountStatus.ACTIVE)
                .build());

        when(tokenVerificationService.verify(eq(adminToken)))
                .thenReturn(new VerifiedIdentity(adminUid, "admin@mland.vn", true, Instant.now()));
    }

    @Test
    @DisplayName("T114: Admin creates STAFF -> PENDING_SETUP and InternalAccountInvitation created")
    void adminCreatesStaff_createsInvitation() throws Exception {
        String idemKey = UUID.randomUUID().toString();
        String staffEmail = "staff.inv." + UUID.randomUUID().toString().substring(0, 8) + "@mland.vn";

        InternalAccountCreateRequest request = InternalAccountCreateRequest.builder()
                .role(AccountRole.STAFF)
                .email(staffEmail)
                .fullName("Nguyen Van Staff")
                .phone("0981112233")
                .locationIds(List.of(101L))
                .build();

        String respStr = mockMvc.perform(post("/api/v1/admin/accounts/internal")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("Idempotency-Key", idemKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("STAFF"))
                .andExpect(jsonPath("$.status").value("PENDING_SETUP"))
                .andExpect(jsonPath("$.loginEmail").value(staffEmail))
                .andReturn().getResponse().getContentAsString();

        Map<?, ?> map = objectMapper.readValue(respStr, Map.class);
        Long accountId = Long.valueOf(map.get("accountId").toString());

        InternalAccountInvitation inv = invitationRepository.findTopByAccountIdOrderByInvitationVersionDesc(accountId).orElse(null);
        assertThat(inv).isNotNull();
        assertThat(inv.getInvitationVersion()).isEqualTo(1);
        assertThat(inv.getUseState()).isEqualTo("PENDING");
        assertThat(inv.getDeliveryState()).isIn("PENDING", "QUEUED");
        assertThat(inv.getSecretDigest()).isNotBlank();
        assertThat(inv.getExpiresAt()).isAfter(Instant.now().plusSeconds(47 * 3600));
    }

    @Test
    @DisplayName("T114: Resending invitation immediately triggers 409 cooldown")
    void resendInvitation_enforcesCooldown() throws Exception {
        String idemKey = UUID.randomUUID().toString();
        String staffEmail = "staff.cooldown." + UUID.randomUUID().toString().substring(0, 8) + "@mland.vn";

        InternalAccountCreateRequest request = InternalAccountCreateRequest.builder()
                .role(AccountRole.STAFF)
                .email(staffEmail)
                .fullName("Nguyen Van Cooldown")
                .phone("0982223344")
                .locationIds(List.of(101L))
                .build();

        String respStr = mockMvc.perform(post("/api/v1/admin/accounts/internal")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("Idempotency-Key", idemKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Map<?, ?> map = objectMapper.readValue(respStr, Map.class);
        Long accountId = Long.valueOf(map.get("accountId").toString());

        // Immediate resend -> 409 Cooldown
        mockMvc.perform(post("/api/v1/admin/accounts/" + accountId + "/setup-invitations/resend")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVITATION_COOLDOWN_ACTIVE"));
    }

    @Test
    @DisplayName("T114: Retirement command fails closed with HTTP 409 Conflict and RETIREMENT_BLOCKED")
    void retireForReplacement_failsClosedWith409() throws Exception {
        Account member = accountRepository.save(Account.builder()
                .firebaseUid("member-retire-uid-" + UUID.randomUUID())
                .role(AccountRole.MEMBER)
                .status(AccountStatus.ACTIVE)
                .loginEmail("member.retire@test.com")
                .build());

        RetirementCommandRequest cmd = RetirementCommandRequest.builder()
                .accountVersion(member.getVersion() != null ? member.getVersion() : 0L)
                .normalizedEmail("member.retire@test.com")
                .retailSourceVersion("v1")
                .workshopSourceVersion("v1")
                .confirm(true)
                .build();

        mockMvc.perform(post("/api/v1/admin/accounts/" + member.getId() + "/retire-for-replacement")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cmd)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RETIREMENT_BLOCKED"));
    }

    @Test
    @DisplayName("T114: Replacement review returns DEMO origin and UNAVAILABLE status")
    void replacementReview_returnsDemoUnavailable() throws Exception {
        Account member = accountRepository.save(Account.builder()
                .firebaseUid("member-review-uid-" + UUID.randomUUID())
                .role(AccountRole.MEMBER)
                .status(AccountStatus.ACTIVE)
                .loginEmail("member.review@test.com")
                .build());

        mockMvc.perform(get("/api/v1/admin/accounts/" + member.getId() + "/replacement-review")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.retail.status").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.workshop.status").value("UNAVAILABLE"));
    }

    @Test
    @DisplayName("T114: Public exchange of valid secret returns setup session and custom token")
    void invitationExchange_validSecret_success() throws Exception {
        String idemKey = UUID.randomUUID().toString();
        String staffEmail = "staff.exchange." + UUID.randomUUID().toString().substring(0, 8) + "@mland.vn";

        InternalAccountCreateRequest createReq = InternalAccountCreateRequest.builder()
                .role(AccountRole.STAFF)
                .email(staffEmail)
                .fullName("Nguyen Van Exchange")
                .phone("0983334455")
                .locationIds(List.of(101L))
                .build();

        String respStr = mockMvc.perform(post("/api/v1/admin/accounts/internal")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("Idempotency-Key", idemKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Map<?, ?> map = objectMapper.readValue(respStr, Map.class);
        Long accountId = Long.valueOf(map.get("accountId").toString());

        InternalAccountInvitation inv = invitationRepository.findTopByAccountIdOrderByInvitationVersionDesc(accountId).orElseThrow();
        String rawSecret = invitationService.deriveDeterministicSecret(accountId, inv.getInvitationVersion(), inv.getKeyVersion());

        InvitationExchangeRequest exchangeReq = new InvitationExchangeRequest(rawSecret);

        String exchangeResp = mockMvc.perform(post("/api/v1/internal-account-invitations/exchanges")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(exchangeReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.setupSession").isNotEmpty())
                .andExpect(jsonPath("$.firebaseCustomToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        InvitationExchangeResponse exchangeObj = objectMapper.readValue(exchangeResp, InvitationExchangeResponse.class);

        // Complete invitation
        String completedToken = "completed-fb-token-" + UUID.randomUUID();
        Account createdAcc = accountRepository.findById(accountId).orElseThrow();
        when(tokenVerificationService.verify(eq(completedToken)))
                .thenReturn(new VerifiedIdentity(createdAcc.getFirebaseUid(), staffEmail, true, Instant.now()));

        InvitationCompletionRequest completionReq = new InvitationCompletionRequest(
                exchangeObj.getSetupSession(),
                "PASSWORD"
        );

        mockMvc.perform(post("/api/v1/internal-account-invitations/completions")
                        .header("Authorization", "Bearer " + completedToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(completionReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.homeRoute").value("/staff"));

        // Verify account is ACTIVE in DB
        Account finalAcc = accountRepository.findById(accountId).orElseThrow();
        assertThat(finalAcc.getStatus()).isEqualTo(AccountStatus.ACTIVE);

        // Verify invitation state is USED
        InternalAccountInvitation finalInv = invitationRepository.findById(inv.getId()).orElseThrow();
        assertThat(finalInv.getUseState()).isEqualTo("USED");
    }

    @Test
    @DisplayName("T114: Exchange with invalid secret returns 400 Bad Request")
    void invitationExchange_invalidSecret_returnsBadRequest() throws Exception {
        InvitationExchangeRequest exchangeReq = new InvitationExchangeRequest("invalid_secret_abc123");

        mockMvc.perform(post("/api/v1/internal-account-invitations/exchanges")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(exchangeReq)))
                .andExpect(status().isBadRequest());
    }
}
