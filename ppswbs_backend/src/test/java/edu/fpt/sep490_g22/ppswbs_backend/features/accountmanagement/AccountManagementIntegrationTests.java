package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao.AccountLocationAssignmentRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao.AccountProfileRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao.AccountRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao.InternalAccountProvisioningRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dto.request.AccountSuspendRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dto.request.InternalAccountCreateRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dto.request.ProfileUpdateRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.Account;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.enums.AccountRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.enums.AccountStatus;
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
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AccountManagementIntegrationTests {

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
    private AccountLocationAssignmentRepository locationAssignmentRepository;

    @Autowired
    private InternalAccountProvisioningRepository provisioningRepository;

    @BeforeEach
    void setUp() {
        locationAssignmentRepository.deleteAll();
        provisioningRepository.deleteAll();
        profileRepository.deleteAll();
        accountRepository.deleteAll();
    }

    @Test
    @DisplayName("V-02: Bootstrap idempotency (PUT /api/v1/accounts/me called twice produces 1 row)")
    void testBootstrapIdempotency() throws Exception {
        String uid = "uid-" + UUID.randomUUID();
        String token = "valid-token-" + uid;

        when(tokenVerificationService.verify(eq(token)))
            .thenReturn(new VerifiedIdentity(uid, "user@test.com", true, Instant.now()));

        // Call 1
        mockMvc.perform(put("/api/v1/accounts/me")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("MEMBER"))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.loginEmail").value("user@test.com"));

        // Call 2 (idempotent repeat)
        mockMvc.perform(put("/api/v1/accounts/me")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("MEMBER"))
            .andExpect(jsonPath("$.status").value("ACTIVE"));

        assertThat(accountRepository.findByFirebaseUid(uid)).isPresent();
        assertThat(accountRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("V-03: Internal account creation idempotency (POST /admin/accounts/internal)")
    void testInternalAccountCreationIdempotency() throws Exception {
        String adminUid = "admin-" + UUID.randomUUID();
        String adminToken = "admin-token";

        // Create Admin in DB
        Account admin = accountRepository.save(Account.builder()
            .firebaseUid(adminUid)
            .role(AccountRole.ADMIN)
            .status(AccountStatus.ACTIVE)
            .build());

        when(tokenVerificationService.verify(eq(adminToken)))
            .thenReturn(new VerifiedIdentity(adminUid, "admin@mland.vn", true, Instant.now()));

        String idemKey = UUID.randomUUID().toString();
        InternalAccountCreateRequest request = InternalAccountCreateRequest.builder()
            .role(AccountRole.STAFF)
            .fullName("Nguyen Van A")
            .phone("0987654321")
            .locationIds(List.of(101L))
            .build();

        // Call 1: Success
        String response1 = mockMvc.perform(post("/api/v1/admin/accounts/internal")
                .header("Authorization", "Bearer " + adminToken)
                .header("Idempotency-Key", idemKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("STAFF"))
            .andExpect(jsonPath("$.status").value("PENDING_SETUP"))
            .andReturn().getResponse().getContentAsString();

        // Call 2: Identical repeat returns 200 with same result
        String response2 = mockMvc.perform(post("/api/v1/admin/accounts/internal")
                .header("Authorization", "Bearer " + adminToken)
                .header("Idempotency-Key", idemKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("STAFF"))
            .andReturn().getResponse().getContentAsString();

        assertThat(response1).isEqualTo(response2);

        // Call 3: Changed payload with same key returns 409
        InternalAccountCreateRequest conflictRequest = InternalAccountCreateRequest.builder()
            .role(AccountRole.STAFF)
            .fullName("Nguyen Van A")
            .phone("0987654321")
            .locationIds(List.of(999L)) // Changed location
            .build();

        mockMvc.perform(post("/api/v1/admin/accounts/internal")
                .header("Authorization", "Bearer " + adminToken)
                .header("Idempotency-Key", idemKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(conflictRequest)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_CONFLICT"));
    }

    @Test
    @DisplayName("V-04: Suspend account -> 403 ACCOUNT_SUSPENDED")
    void testSuspensionEnforcement() throws Exception {
        String adminUid = "admin-" + UUID.randomUUID();
        String adminToken = "admin-token-2";
        Account admin = accountRepository.save(Account.builder()
            .firebaseUid(adminUid)
            .role(AccountRole.ADMIN)
            .status(AccountStatus.ACTIVE)
            .build());

        when(tokenVerificationService.verify(eq(adminToken)))
            .thenReturn(new VerifiedIdentity(adminUid, "admin@mland.vn", true, Instant.now()));

        // Target user
        String userUid = "user-" + UUID.randomUUID();
        String userToken = "user-token-2";
        Account target = accountRepository.save(Account.builder()
            .firebaseUid(userUid)
            .role(AccountRole.MEMBER)
            .status(AccountStatus.ACTIVE)
            .build());

        when(tokenVerificationService.verify(eq(userToken)))
            .thenReturn(new VerifiedIdentity(userUid, "target@user.com", true, Instant.now()));

        // Admin suspends target
        AccountSuspendRequest suspendReq = new AccountSuspendRequest("Policy violation");
        mockMvc.perform(post("/api/v1/admin/accounts/" + target.getId() + "/suspend")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(suspendReq)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("SUSPENDED"));

        // User attempts GET /accounts/me -> 403 ACCOUNT_SUSPENDED
        mockMvc.perform(get("/api/v1/accounts/me")
                .header("Authorization", "Bearer " + userToken))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ACCOUNT_SUSPENDED"));
    }

    @Test
    @DisplayName("V-05: Restoration gate blocked until revocation confirmed")
    void testRestorationGate() throws Exception {
        String adminUid = "admin-" + UUID.randomUUID();
        String adminToken = "admin-token-3";
        Account admin = accountRepository.save(Account.builder()
            .firebaseUid(adminUid)
            .role(AccountRole.ADMIN)
            .status(AccountStatus.ACTIVE)
            .build());

        when(tokenVerificationService.verify(eq(adminToken)))
            .thenReturn(new VerifiedIdentity(adminUid, "admin@mland.vn", true, Instant.now()));

        // Suspended target with sessionRevocationConfirmedAt = null
        Account target = accountRepository.save(Account.builder()
            .firebaseUid("target-" + UUID.randomUUID())
            .role(AccountRole.STAFF)
            .status(AccountStatus.SUSPENDED)
            .sessionRevocationConfirmedAt(null)
            .build());

        // Restore attempt -> 409 REVOCATION_PENDING
        mockMvc.perform(post("/api/v1/admin/accounts/" + target.getId() + "/restore")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("REVOCATION_PENDING"));

        // Confirm revocation
        target.setSessionRevocationConfirmedAt(Instant.now());
        accountRepository.save(target);

        // Restore attempt again -> 200 OK, status ACTIVE
        mockMvc.perform(post("/api/v1/admin/accounts/" + target.getId() + "/restore")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("V-06: Profile update requires verified email")
    void testProfileUpdateRequiresVerifiedEmail() throws Exception {
        String uid = "user-" + UUID.randomUUID();
        String unverifiedToken = "unverified-token";
        String verifiedToken = "verified-token";

        Account user = accountRepository.save(Account.builder()
            .firebaseUid(uid)
            .role(AccountRole.MEMBER)
            .status(AccountStatus.ACTIVE)
            .build());

        when(tokenVerificationService.verify(eq(unverifiedToken)))
            .thenReturn(new VerifiedIdentity(uid, "user@test.com", false /* unverified */, Instant.now()));

        when(tokenVerificationService.verify(eq(verifiedToken)))
            .thenReturn(new VerifiedIdentity(uid, "user@test.com", true /* verified */, Instant.now()));

        ProfileUpdateRequest updateReq = ProfileUpdateRequest.builder()
            .fullName("John Doe")
            .phone("0123456789")
            .build();

        // Unverified token -> 403 EMAIL_NOT_VERIFIED
        mockMvc.perform(put("/api/v1/accounts/me/profile")
                .header("Authorization", "Bearer " + unverifiedToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateReq)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));

        // Verified token -> 200 OK
        mockMvc.perform(put("/api/v1/accounts/me/profile")
                .header("Authorization", "Bearer " + verifiedToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateReq)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.fullName").value("John Doe"))
            .andExpect(jsonPath("$.phone").value("0123456789"));
    }

    @Test
    @DisplayName("V-08: Fixed Role Catalogue (GET /api/v1/admin/roles returns 4 fixed roles)")
    void testRoleCatalogue() throws Exception {
        mockMvc.perform(get("/api/v1/admin/roles"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.roles.length()").value(4))
            .andExpect(jsonPath("$.roles[0].code").value("MEMBER"))
            .andExpect(jsonPath("$.roles[1].code").value("STAFF"))
            .andExpect(jsonPath("$.roles[2].code").value("MANAGER"))
            .andExpect(jsonPath("$.roles[3].code").value("ADMIN"));
    }
}
