package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao.AccountProfileRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao.AccountRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dto.request.AccountSuspendRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dto.request.InternalProfileUpdateRequest;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:application-test.properties")
class AccountProtectionIT {

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

    private String adminToken;
    private Account adminAccount;

    @BeforeEach
    void setUp() {
        profileRepository.deleteAll();
        accountRepository.deleteAll();

        String adminUid = "admin-" + UUID.randomUUID();
        adminToken = "admin-token-" + adminUid;
        adminAccount = accountRepository.save(Account.builder()
            .firebaseUid(adminUid)
            .loginEmail("admin@mland.vn")
            .role(AccountRole.ADMIN)
            .status(AccountStatus.ACTIVE)
            .version(0L)
            .build());

        when(tokenVerificationService.verify(eq(adminToken)))
            .thenReturn(new VerifiedIdentity(adminUid, "admin@mland.vn", true, Instant.now()));
    }

    @Test
    @DisplayName("US4-1: Suspending an ADMIN account is strictly forbidden")
    void whenAttemptToSuspendAdmin_thenForbidden() throws Exception {
        AccountSuspendRequest req = new AccountSuspendRequest("Cannot suspend self or other admin");

        mockMvc.perform(post("/api/v1/admin/accounts/" + adminAccount.getId() + "/suspend")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("US4-2: Stale version update triggers 409 OPTIMISTIC_LOCK_CONFLICT")
    void whenStaleVersionProvided_thenOptimisticLockConflict() throws Exception {
        Account staff = accountRepository.save(Account.builder()
            .firebaseUid("staff-" + UUID.randomUUID())
            .loginEmail("staff@mland.vn")
            .role(AccountRole.STAFF)
            .status(AccountStatus.ACTIVE)
            .version(5L) // Current version is 5
            .build());

        InternalProfileUpdateRequest updateReq = InternalProfileUpdateRequest.builder()
            .version(4L) // Stale version
            .fullName("Updated Staff")
            .phone("0987654321")
            .build();

        mockMvc.perform(put("/api/v1/admin/accounts/" + staff.getId() + "/profile")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateReq)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("OPTIMISTIC_LOCK_CONFLICT"));
    }
}
