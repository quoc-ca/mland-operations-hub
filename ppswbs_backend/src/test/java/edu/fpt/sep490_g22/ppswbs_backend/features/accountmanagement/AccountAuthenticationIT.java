package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao.AccountLocationAssignmentRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao.AccountProfileRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao.AccountRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao.InternalAccountProvisioningRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.Account;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.AccountLocationAssignment;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.InternalAccountProvisioning;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.enums.AccountRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.enums.AccountStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.enums.InternalProvisioningStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.TokenVerificationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.VerifiedIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:application-test.properties")
class AccountAuthenticationIT {

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
    @DisplayName("US1-1: Successful first-time bootstrap creates MEMBER account")
    void whenFirstTimeBootstrap_thenCreatesActiveMemberAccount() throws Exception {
        String uid = "firebase-user-" + UUID.randomUUID();
        String token = "valid-token-" + uid;

        when(tokenVerificationService.verify(eq(token)))
            .thenReturn(new VerifiedIdentity(uid, "customer@example.com", true, Instant.now()));

        mockMvc.perform(put("/api/v1/accounts/me")
                .header("Authorization", "Bearer " + token)
                .header("X-Correlation-Id", UUID.randomUUID().toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("MEMBER"))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.email").value("customer@example.com"))
            .andExpect(jsonPath("$.emailVerified").value(true));

        Account account = accountRepository.findByFirebaseUid(uid).orElseThrow();
        assertThat(account.getRole()).isEqualTo(AccountRole.MEMBER);
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.getLoginEmail()).isEqualTo("customer@example.com");
    }

    @Test
    @DisplayName("US1-2: Bootstrap repeat idempotency preserves original account without duplicates")
    void whenBootstrapRepeated_thenReturnsExistingAccountWithoutDuplicate() throws Exception {
        String uid = "firebase-user-" + UUID.randomUUID();
        String token = "token-" + uid;

        when(tokenVerificationService.verify(eq(token)))
            .thenReturn(new VerifiedIdentity(uid, "repeat@example.com", true, Instant.now()));

        // Call 1
        mockMvc.perform(put("/api/v1/accounts/me")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());

        // Call 2
        mockMvc.perform(put("/api/v1/accounts/me")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("repeat@example.com"));

        assertThat(accountRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("US1-3: Different Firebase UIDs produce distinct accounts (collision isolation)")
    void whenDifferentUids_thenProducesSeparateAccounts() throws Exception {
        String uid1 = "uid-" + UUID.randomUUID();
        String uid2 = "uid-" + UUID.randomUUID();
        String token1 = "token-1";
        String token2 = "token-2";

        when(tokenVerificationService.verify(eq(token1)))
            .thenReturn(new VerifiedIdentity(uid1, "user1@example.com", true, Instant.now()));
        when(tokenVerificationService.verify(eq(token2)))
            .thenReturn(new VerifiedIdentity(uid2, "user2@example.com", true, Instant.now()));

        mockMvc.perform(put("/api/v1/accounts/me")
                .header("Authorization", "Bearer " + token1))
            .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/accounts/me")
                .header("Authorization", "Bearer " + token2))
            .andExpect(status().isOk());

        assertThat(accountRepository.count()).isEqualTo(2);
        assertThat(accountRepository.findByFirebaseUid(uid1)).isPresent();
        assertThat(accountRepository.findByFirebaseUid(uid2)).isPresent();
    }

    @Test
    @DisplayName("US3-1: Internal account in PENDING_SETUP activates upon verified bootstrap")
    void whenPendingInternalAccountBootstrapsWithVerifiedEmail_thenActivates() throws Exception {
        String internalUid = "uid-staff-" + UUID.randomUUID();
        String staffToken = "token-staff";

        // Pre-provision internal account in PENDING_SETUP with location
        Account internalAccount = accountRepository.save(Account.builder()
            .firebaseUid(internalUid)
            .role(AccountRole.STAFF)
            .status(AccountStatus.PENDING_SETUP)
            .loginEmail("staff@mland.vn")
            .version(0L)
            .build());

        locationAssignmentRepository.save(AccountLocationAssignment.builder()
            .accountId(internalAccount.getId())
            .locationId(101L)
            .assignedAt(Instant.now())
            .active(true)
            .build());

        InternalAccountProvisioning prov = provisioningRepository.save(InternalAccountProvisioning.builder()
            .accountId(internalAccount.getId())
            .idempotencyKey(UUID.randomUUID().toString())
            .status(InternalProvisioningStatus.INVITATION_SENT)
            .invitationLastSentAt(Instant.now())
            .createdByAdminId(internalAccount.getId())
            .build());

        when(tokenVerificationService.verify(eq(staffToken)))
            .thenReturn(new VerifiedIdentity(internalUid, "staff@mland.vn", true /* verified */, Instant.now()));

        // Call bootstrap
        mockMvc.perform(put("/api/v1/accounts/me")
                .header("Authorization", "Bearer " + staffToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("STAFF"))
            .andExpect(jsonPath("$.status").value("ACTIVE"));

        Account updated = accountRepository.findById(internalAccount.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(updated.getRole()).isEqualTo(AccountRole.STAFF);

        InternalAccountProvisioning updatedProv = provisioningRepository.findById(prov.getId()).orElseThrow();
        assertThat(updatedProv.getStatus()).isEqualTo(InternalProvisioningStatus.SETUP_COMPLETE);
    }
}
