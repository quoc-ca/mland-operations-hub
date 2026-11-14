package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao.AccountLocationAssignmentRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao.AccountProfileRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao.AccountRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao.InternalAccountProvisioningRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dto.request.InternalAccountCreateRequest;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:application-test.properties")
class InternalAccountAdministrationIT {

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

    private String adminToken;
    private Account adminAccount;

    @BeforeEach
    void setUp() {
        locationAssignmentRepository.deleteAll();
        provisioningRepository.deleteAll();
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
    @DisplayName("US3-1: Staff creation requires exactly 1 location assignment")
    void whenStaffCreatedWithMultipleLocations_thenFailsValidation() throws Exception {
        InternalAccountCreateRequest request = InternalAccountCreateRequest.builder()
            .role(AccountRole.STAFF)
            .fullName("Staff Multi Location")
            .phone("0981112233")
            .locationIds(List.of(101L, 102L)) // 2 locations invalid for STAFF
            .build();

        mockMvc.perform(post("/api/v1/internal-accounts")
                .header("Authorization", "Bearer " + adminToken)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("LOCATION_NOT_ACTIVE"));
    }

    @Test
    @DisplayName("US3-2: Manager creation succeeds with multiple locations")
    void whenManagerCreatedWithMultipleLocations_thenSucceeds() throws Exception {
        InternalAccountCreateRequest request = InternalAccountCreateRequest.builder()
            .role(AccountRole.MANAGER)
            .fullName("Manager Multi Location")
            .phone("0981112233")
            .locationIds(List.of(101L, 102L)) // valid for MANAGER
            .build();

        mockMvc.perform(post("/api/v1/internal-accounts")
                .header("Authorization", "Bearer " + adminToken)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("MANAGER"))
            .andExpect(jsonPath("$.status").value("PENDING_SETUP"));
    }

    @Test
    @DisplayName("US3-3: Resending invitation enforces 5-minute cooldown")
    void whenResendingInvitationImmediately_thenFailsCooldown() throws Exception {
        InternalAccountCreateRequest request = InternalAccountCreateRequest.builder()
            .role(AccountRole.STAFF)
            .fullName("Staff Cooldown Test")
            .phone("0981112233")
            .locationIds(List.of(101L))
            .build();

        String response = mockMvc.perform(post("/api/v1/internal-accounts")
                .header("Authorization", "Bearer " + adminToken)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        Long accountId = objectMapper.readTree(response).get("accountId").asLong();

        // Resend immediately (less than 5 min)
        mockMvc.perform(post("/api/v1/accounts/" + accountId + "/setup-invitations")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("INVITATION_COOLDOWN_ACTIVE"));
    }
}
