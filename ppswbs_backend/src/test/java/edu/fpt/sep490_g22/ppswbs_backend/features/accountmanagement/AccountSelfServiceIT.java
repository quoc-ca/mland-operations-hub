package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao.AccountProfileRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao.AccountRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dto.request.ProfileUpdateRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.Account;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.AccountProfile;
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
class AccountSelfServiceIT {

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

    @BeforeEach
    void setUp() {
        profileRepository.deleteAll();
        accountRepository.deleteAll();
    }

    @Test
    @DisplayName("US2-1: Unverified email user can read account info but emailVerified is false")
    void whenUnverifiedUserReadsAccount_thenEmailVerifiedIsFalse() throws Exception {
        String uid = "unverified-user-" + UUID.randomUUID();
        String token = "unverified-token-" + uid;

        Account account = accountRepository.save(Account.builder()
            .firebaseUid(uid)
            .loginEmail("unverified@example.com")
            .emailVerifiedAt(null)
            .role(AccountRole.MEMBER)
            .status(AccountStatus.ACTIVE)
            .version(0L)
            .build());

        when(tokenVerificationService.verify(eq(token)))
            .thenReturn(new VerifiedIdentity(uid, "unverified@example.com", false /* unverified */, Instant.now()));

        mockMvc.perform(get("/api/v1/accounts/me")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.emailVerified").value(false))
            .andExpect(jsonPath("$.email").value("unverified@example.com"));
    }

    @Test
    @DisplayName("US2-2: Unverified user attempting profile update is rejected with 403 EMAIL_NOT_VERIFIED")
    void whenUnverifiedUserUpdatesProfile_thenForbiddenEmailNotVerified() throws Exception {
        String uid = "unverified-user-" + UUID.randomUUID();
        String token = "unverified-token-" + uid;

        accountRepository.save(Account.builder()
            .firebaseUid(uid)
            .loginEmail("unverified@example.com")
            .emailVerifiedAt(null)
            .role(AccountRole.MEMBER)
            .status(AccountStatus.ACTIVE)
            .version(0L)
            .build());

        when(tokenVerificationService.verify(eq(token)))
            .thenReturn(new VerifiedIdentity(uid, "unverified@example.com", false, Instant.now()));

        ProfileUpdateRequest updateReq = ProfileUpdateRequest.builder()
            .fullName("Jane Doe")
            .phone("0912345678")
            .build();

        mockMvc.perform(put("/api/v1/accounts/me/profile")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateReq)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
    }

    @Test
    @DisplayName("US2-3: Verified user updates profile successfully")
    void whenVerifiedUserUpdatesProfile_thenSucceeds() throws Exception {
        String uid = "verified-user-" + UUID.randomUUID();
        String token = "verified-token-" + uid;

        Account account = accountRepository.save(Account.builder()
            .firebaseUid(uid)
            .loginEmail("verified@example.com")
            .emailVerifiedAt(Instant.now())
            .role(AccountRole.MEMBER)
            .status(AccountStatus.ACTIVE)
            .version(0L)
            .build());

        when(tokenVerificationService.verify(eq(token)))
            .thenReturn(new VerifiedIdentity(uid, "verified@example.com", true, Instant.now()));

        ProfileUpdateRequest updateReq = ProfileUpdateRequest.builder()
            .fullName("Jane Doe")
            .phone("0912345678")
            .build();

        mockMvc.perform(put("/api/v1/accounts/me/profile")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateReq)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.fullName").value("Jane Doe"))
            .andExpect(jsonPath("$.phone").value("0912345678"));

        AccountProfile savedProfile = profileRepository.findByAccountId(account.getId()).orElseThrow();
        assertThat(savedProfile.getFullName()).isEqualTo("Jane Doe");
        assertThat(savedProfile.getPhone()).isEqualTo("0912345678");
    }

    @Test
    @DisplayName("US2-4: Email sync updates account loginEmail from verified token")
    void whenEmailSynchronized_thenAccountEmailIsUpdated() throws Exception {
        String uid = "sync-user-" + UUID.randomUUID();
        String token = "sync-token-" + uid;

        Account account = accountRepository.save(Account.builder()
            .firebaseUid(uid)
            .loginEmail("old-email@example.com")
            .emailVerifiedAt(Instant.now())
            .role(AccountRole.MEMBER)
            .status(AccountStatus.ACTIVE)
            .version(0L)
            .build());

        when(tokenVerificationService.verify(eq(token)))
            .thenReturn(new VerifiedIdentity(uid, "new-email@example.com", true, Instant.now()));

        mockMvc.perform(put("/api/v1/accounts/me/email")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("new-email@example.com"))
            .andExpect(jsonPath("$.emailVerified").value(true));

        Account updated = accountRepository.findById(account.getId()).orElseThrow();
        assertThat(updated.getLoginEmail()).isEqualTo("new-email@example.com");
    }
}
