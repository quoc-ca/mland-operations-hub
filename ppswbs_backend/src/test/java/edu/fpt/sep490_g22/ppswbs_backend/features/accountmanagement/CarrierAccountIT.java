package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao.AccountLocationAssignmentRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao.AccountProfileRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao.AccountRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao.InternalAccountProvisioningRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dto.request.AccountSuspendRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dto.request.InternalAccountCreateRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dto.request.InternalProfileUpdateRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dto.request.ProfileUpdateRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dto.request.RoleLocationAssignRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.Account;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.AccountProfile;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.InternalAccountProvisioning;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.enums.AccountRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.enums.AccountStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.enums.InternalProvisioningStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.RosterOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.AccountBootstrapService;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:application-test.properties")
class CarrierAccountIT {

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

    @Autowired
    private AccountFacade accountFacade;

    @Autowired
    private AccountBootstrapService bootstrapService;

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
    @DisplayName("T084: Role catalogue contains CARRIER with exact Vietnamese display name")
    void roleCatalogue_includesCarrierRole() throws Exception {
        mockMvc.perform(get("/api/v1/roles")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.roles[?(@.code == 'CARRIER')].displayName").value("Đối tác vận chuyển (GHTK)"));
    }

    @Test
    @DisplayName("T085: Admin creates ACTIVE CARRIER with required fields, no locations, and write-only password")
    void adminCreatesCarrier_success() throws Exception {
        String idemKey = UUID.randomUUID().toString();
        InternalAccountCreateRequest request = InternalAccountCreateRequest.builder()
            .role(AccountRole.CARRIER)
            .email("carrier.partner@ghtk.vn")
            .password("CarrierPass@123")
            .fullName("Đối tác GHTK Miền Bắc")
            .phone("0912345678")
            .locationIds(null)
            .build();

        String responseJson = mockMvc.perform(post("/api/v1/internal-accounts")
                .header("Authorization", "Bearer " + adminToken)
                .header("Idempotency-Key", idemKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("CARRIER"))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.loginEmail").value("carrier.partner@ghtk.vn"))
            .andExpect(jsonPath("$.provisioningStatus").value("SETUP_COMPLETE"))
            .andExpect(jsonPath("$.password").doesNotExist())
            .andReturn().getResponse().getContentAsString();

        Long accountId = objectMapper.readTree(responseJson).get("accountId").asLong();

        // Verify DB persistence
        Account account = accountRepository.findById(accountId).orElseThrow();
        assertThat(account.getRole()).isEqualTo(AccountRole.CARRIER);
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.getLoginEmail()).isEqualTo("carrier.partner@ghtk.vn");
        assertThat(account.getFirebaseUid()).isNotNull();

        // Profile persisted
        AccountProfile profile = profileRepository.findByAccountId(accountId).orElseThrow();
        assertThat(profile.getFullName()).isEqualTo("Đối tác GHTK Miền Bắc");
        assertThat(profile.getPhone()).isEqualTo("0912345678");

        // No locations
        assertThat(locationAssignmentRepository.findByAccountIdAndActiveTrue(accountId)).isEmpty();

        // Provisioning record persisted
        InternalAccountProvisioning prov = provisioningRepository.findByAccountId(accountId).orElseThrow();
        assertThat(prov.getStatus()).isEqualTo(InternalProvisioningStatus.SETUP_COMPLETE);
        assertThat(prov.getIdempotencyKey()).isEqualTo(idemKey);

        // Idempotent retry returns existing account
        mockMvc.perform(post("/api/v1/internal-accounts")
                .header("Authorization", "Bearer " + adminToken)
                .header("Idempotency-Key", idemKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accountId").value(accountId))
            .andExpect(jsonPath("$.role").value("CARRIER"))
            .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("T085: Idempotency conflict when key reused with different email or role")
    void adminCreatesCarrier_idempotencyConflict() throws Exception {
        String idemKey = UUID.randomUUID().toString();
        InternalAccountCreateRequest request1 = InternalAccountCreateRequest.builder()
            .role(AccountRole.CARRIER)
            .email("carrier.one@ghtk.vn")
            .password("CarrierPass@123")
            .fullName("Đối tác GHTK 1")
            .phone("0912345678")
            .build();

        mockMvc.perform(post("/api/v1/internal-accounts")
                .header("Authorization", "Bearer " + adminToken)
                .header("Idempotency-Key", idemKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request1)))
            .andExpect(status().isOk());

        // Reusing same key with different email
        InternalAccountCreateRequest request2 = InternalAccountCreateRequest.builder()
            .role(AccountRole.CARRIER)
            .email("carrier.two@ghtk.vn")
            .password("CarrierPass@123")
            .fullName("Đối tác GHTK 2")
            .phone("0912345678")
            .build();

        mockMvc.perform(post("/api/v1/internal-accounts")
                .header("Authorization", "Bearer " + adminToken)
                .header("Idempotency-Key", idemKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request2)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_CONFLICT"));
    }

    @Test
    @DisplayName("T085: Reject CARRIER creation when locationIds provided")
    void adminCreatesCarrier_rejectsLocation() throws Exception {
        InternalAccountCreateRequest request = InternalAccountCreateRequest.builder()
            .role(AccountRole.CARRIER)
            .email("carrier.loc@ghtk.vn")
            .password("CarrierPass@123")
            .fullName("Đối tác GHTK")
            .phone("0912345678")
            .locationIds(List.of(1L))
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
    @DisplayName("T085: Reject CARRIER creation when password is weak or missing")
    void adminCreatesCarrier_rejectsWeakPassword() throws Exception {
        InternalAccountCreateRequest request = InternalAccountCreateRequest.builder()
            .role(AccountRole.CARRIER)
            .email("carrier.short@ghtk.vn")
            .password("12345") // < 6 chars
            .fullName("Đối tác GHTK")
            .phone("0912345678")
            .build();

        mockMvc.perform(post("/api/v1/internal-accounts")
                .header("Authorization", "Bearer " + adminToken)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("T085: Reject CARRIER creation when duplicate email exists in Mland")
    void adminCreatesCarrier_rejectsDuplicateEmail() throws Exception {
        // Pre-create account with email
        accountRepository.save(Account.builder()
            .firebaseUid("existing-member-uid")
            .loginEmail("duplicate@mland.vn")
            .role(AccountRole.MEMBER)
            .status(AccountStatus.ACTIVE)
            .version(0L)
            .build());

        InternalAccountCreateRequest request = InternalAccountCreateRequest.builder()
            .role(AccountRole.CARRIER)
            .email("duplicate@mland.vn")
            .password("CarrierPass@123")
            .fullName("Đối tác GHTK")
            .phone("0912345678")
            .build();

        mockMvc.perform(post("/api/v1/internal-accounts")
                .header("Authorization", "Bearer " + adminToken)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("INVALID_STATE"));
    }

    @Test
    @DisplayName("T086: CARRIER role transitions are forbidden in both directions")
    void roleTransitions_forbiddenForCarrier() throws Exception {
        // Create carrier
        Account carrier = accountRepository.save(Account.builder()
            .firebaseUid("carrier-uid-test")
            .loginEmail("carrier.trans@ghtk.vn")
            .role(AccountRole.CARRIER)
            .status(AccountStatus.ACTIVE)
            .version(0L)
            .build());

        // 1. CARRIER -> STAFF forbidden
        RoleLocationAssignRequest toStaffRequest = new RoleLocationAssignRequest(0L, AccountRole.STAFF, List.of(1L));
        mockMvc.perform(put("/api/v1/accounts/" + carrier.getId() + "/role-assignment")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(toStaffRequest)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        // 2. STAFF -> CARRIER forbidden
        Account staff = accountRepository.save(Account.builder()
            .firebaseUid("staff-uid-test")
            .loginEmail("staff.trans@mland.vn")
            .role(AccountRole.STAFF)
            .status(AccountStatus.ACTIVE)
            .version(0L)
            .build());

        RoleLocationAssignRequest toCarrierRequest = new RoleLocationAssignRequest(0L, AccountRole.CARRIER, List.of());
        mockMvc.perform(put("/api/v1/accounts/" + staff.getId() + "/role-assignment")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(toCarrierRequest)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("T086: Admin updates CARRIER profile (fullName, phone) but Member profile is self-managed")
    void adminProfileMaintenance_forCarrierAndMember() throws Exception {
        // Create carrier with profile
        Account carrier = accountRepository.save(Account.builder()
            .firebaseUid("carrier-uid-prof")
            .loginEmail("carrier.prof@ghtk.vn")
            .role(AccountRole.CARRIER)
            .status(AccountStatus.ACTIVE)
            .version(0L)
            .build());
        profileRepository.save(AccountProfile.builder()
            .accountId(carrier.getId())
            .fullName("Old Name")
            .phone("0911111111")
            .build());

        // Admin updates CARRIER profile
        InternalProfileUpdateRequest updateReq = InternalProfileUpdateRequest.builder()
            .version(0L)
            .fullName("New GHTK Name")
            .phone("0922222222")
            .build();

        mockMvc.perform(put("/api/v1/accounts/" + carrier.getId() + "/profile")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateReq)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.profile.fullName").value("New GHTK Name"))
            .andExpect(jsonPath("$.profile.phone").value("0922222222"));

        // Admin cannot update MEMBER profile
        Account member = accountRepository.save(Account.builder()
            .firebaseUid("member-uid-prof")
            .loginEmail("member@mland.vn")
            .role(AccountRole.MEMBER)
            .status(AccountStatus.ACTIVE)
            .version(0L)
            .build());

        mockMvc.perform(put("/api/v1/accounts/" + member.getId() + "/profile")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateReq)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("T086: CARRIER cannot self-edit profile or email")
    void carrierSelfEdit_forbidden() throws Exception {
        String carrierUid = "carrier-self-uid";
        String carrierToken = "carrier-token";
        Account carrier = accountRepository.save(Account.builder()
            .firebaseUid(carrierUid)
            .loginEmail("carrier.self@ghtk.vn")
            .role(AccountRole.CARRIER)
            .status(AccountStatus.ACTIVE)
            .version(0L)
            .build());
        profileRepository.save(AccountProfile.builder()
            .accountId(carrier.getId())
            .fullName("GHTK Driver")
            .phone("0912345678")
            .build());

        when(tokenVerificationService.verify(eq(carrierToken)))
            .thenReturn(new VerifiedIdentity(carrierUid, "carrier.self@ghtk.vn", true, Instant.now()));

        // PUT /api/v1/accounts/me/profile
        ProfileUpdateRequest profReq = ProfileUpdateRequest.builder()
            .fullName("New Driver Name")
            .phone("0988888888")
            .build();

        mockMvc.perform(put("/api/v1/accounts/me/profile")
                .header("Authorization", "Bearer " + carrierToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(profReq)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        // PUT /api/v1/accounts/me/email
        mockMvc.perform(put("/api/v1/accounts/me/email")
                .header("Authorization", "Bearer " + carrierToken))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("T086: CARRIER suspension and restoration")
    void carrierSuspensionAndRestoration_success() throws Exception {
        Account carrier = accountRepository.save(Account.builder()
            .firebaseUid("carrier-susp-uid")
            .loginEmail("carrier.susp@ghtk.vn")
            .role(AccountRole.CARRIER)
            .status(AccountStatus.ACTIVE)
            .version(0L)
            .build());

        provisioningRepository.save(InternalAccountProvisioning.builder()
            .accountId(carrier.getId())
            .idempotencyKey(UUID.randomUUID().toString())
            .status(InternalProvisioningStatus.SETUP_COMPLETE)
            .createdByAdminId(adminAccount.getId())
            .build());

        // Suspend
        AccountSuspendRequest suspReq = new AccountSuspendRequest("Vấn đề dịch vụ vận chuyển");
        mockMvc.perform(post("/api/v1/admin/accounts/" + carrier.getId() + "/suspend")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(suspReq)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("SUSPENDED"));

        Account suspendedAccount = accountRepository.findById(carrier.getId()).orElseThrow();
        assertThat(suspendedAccount.getStatus()).isEqualTo(AccountStatus.SUSPENDED);

        // Suspended access blocked by IdentityGate
        String carrierToken = "carrier-token-susp";
        when(tokenVerificationService.verify(eq(carrierToken)))
            .thenReturn(new VerifiedIdentity("carrier-susp-uid", "carrier.susp@ghtk.vn", true, Instant.now()));

        mockMvc.perform(get("/api/v1/accounts/me")
                .header("Authorization", "Bearer " + carrierToken))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ACCOUNT_SUSPENDED"));

        // Restore
        mockMvc.perform(post("/api/v1/admin/accounts/" + carrier.getId() + "/restore")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ACTIVE"));

        Account restoredAccount = accountRepository.findById(carrier.getId()).orElseThrow();
        assertThat(restoredAccount.getStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    @DisplayName("T087: Facade and Actor resolution maps CARRIER correctly")
    void facadeResolution_forCarrier() {
        Account carrier = accountRepository.save(Account.builder()
            .firebaseUid("carrier-facade-uid")
            .loginEmail("carrier.facade@ghtk.vn")
            .role(AccountRole.CARRIER)
            .status(AccountStatus.ACTIVE)
            .version(0L)
            .build());

        Optional<CurrentAccountActor> actorOpt = accountFacade.resolveActor("carrier-facade-uid", true);
        assertThat(actorOpt).isPresent();
        CurrentAccountActor actor = actorOpt.get();
        assertThat(actor.role()).isEqualTo(ActorRole.CARRIER);
        assertThat(actor.status()).isEqualTo(ActorStatus.ACTIVE);
        assertThat(actor.activeWorkshopLocationIds()).isEmpty();

        // Roster query never includes CARRIER
        RosterOutcome roster = accountFacade.findEligibleRoster(1L);
        assertThat(roster).isInstanceOf(RosterOutcome.Available.class);
        RosterOutcome.Available available = (RosterOutcome.Available) roster;
        assertThat(available.entries()).noneMatch(e -> e.accountId().value().equals(carrier.getId()));

        // Bootstrap on existing UID resolves CARRIER and does not create Member
        edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.VerifiedIdentity identity =
            new edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.VerifiedIdentity(
                "carrier-facade-uid", "carrier.facade@ghtk.vn", true, Instant.now()
            );
        Account bootstrapped = bootstrapService.bootstrapAccount(identity, UUID.randomUUID().toString());
        assertThat(bootstrapped.getId()).isEqualTo(carrier.getId());
        assertThat(bootstrapped.getRole()).isEqualTo(AccountRole.CARRIER);
    }

    @Test
    @DisplayName("T088: Demo seed fixture ghtk.test@mland.vn exists with CARRIER role and no locations")
    void demoSeedFixture_verified() {
        // Create demo account matching V188 migration if not already in memory
        Account carrier = accountRepository.findByFirebaseUid("t4532Dx1ApddAQMBB3YtxMEisNH3")
            .orElseGet(() -> {
                Account a = accountRepository.save(Account.builder()
                    .firebaseUid("t4532Dx1ApddAQMBB3YtxMEisNH3")
                    .loginEmail("ghtk.test@mland.vn")
                    .role(AccountRole.CARRIER)
                    .status(AccountStatus.ACTIVE)
                    .version(0L)
                    .build());
                profileRepository.save(AccountProfile.builder()
                    .accountId(a.getId())
                    .fullName("Đối tác vận chuyển (GHTK)")
                    .phone("0900000000")
                    .build());
                return a;
            });

        assertThat(carrier.getRole()).isEqualTo(AccountRole.CARRIER);
        assertThat(carrier.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(carrier.getLoginEmail()).isEqualTo("ghtk.test@mland.vn");
        assertThat(locationAssignmentRepository.findByAccountIdAndActiveTrue(carrier.getId())).isEmpty();

        AccountProfile profile = profileRepository.findByAccountId(carrier.getId()).orElseThrow();
        assertThat(profile.getFullName()).isEqualTo("Đối tác vận chuyển (GHTK)");
        assertThat(profile.getPhone()).isEqualTo("0900000000");
    }
}
