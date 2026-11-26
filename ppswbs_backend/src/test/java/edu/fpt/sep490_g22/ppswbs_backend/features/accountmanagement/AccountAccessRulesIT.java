package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao.AccountProfileRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao.AccountRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.Account;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.AccountProfile;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.enums.AccountRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.enums.AccountStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.TokenVerificationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.VerifiedIdentity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 001 FR-053 (pending setup admission) and FR-054 (Manager Staff picker). */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:application-test.properties")
@Transactional
class AccountAccessRulesIT {

    private static final String STAFF_LIST = "/api/v1/accounts?role=STAFF&status=ACTIVE&size=100";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private AccountProfileRepository profileRepository;

    @MockitoBean
    private TokenVerificationService tokenVerificationService;

    @Test
    void pendingAccountReadsItsOwnAccountAndProfileOnly() throws Exception {
        String token = signedIn(AccountRole.MANAGER, AccountStatus.PENDING_SETUP, false);

        mockMvc.perform(as(token, get("/api/v1/accounts/me"))).andExpect(status().isOk());
        mockMvc.perform(as(token, get("/api/v1/accounts/me/profile"))).andExpect(status().isOk());
        mockMvc.perform(as(token, put("/api/v1/accounts/me")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_SETUP"));

        for (MockHttpServletRequestBuilder request : List.of(
                get(STAFF_LIST),
                get("/api/v1/design-review-requests"),
                put("/api/v1/accounts/me/profile").contentType(MediaType.APPLICATION_JSON).content("{}"),
                put("/api/v1/accounts/me/email"),
                get("/api/v1/accounts/me/addresses"))) {
            mockMvc.perform(as(token, request))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCOUNT_PENDING_SETUP"));
        }
    }

    @Test
    void pendingAccountReachesInvitationCompletion() throws Exception {
        String token = signedIn(AccountRole.STAFF, AccountStatus.PENDING_SETUP, true);

        // The setup session is unknown, so the completion service itself answers: the gate let the call through.
        mockMvc.perform(as(token, post("/api/v1/internal-account-invitations/completions"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"setupSession\":\"unknown\",\"setupChoice\":\"PASSWORD\"}"))
                .andExpect(jsonPath("$.code").value(not("ACCOUNT_PENDING_SETUP")));
    }

    @Test
    void managerListsActiveStaffWithoutEmailAndSearchIsIgnored() throws Exception {
        Account staff = account(AccountRole.STAFF, AccountStatus.ACTIVE, "Picker Staff");
        String manager = signedIn(AccountRole.MANAGER, AccountStatus.ACTIVE, true);

        mockMvc.perform(as(manager, get(STAFF_LIST + "&q=nobody-matches-this")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].accountId").value(hasItem(staff.getId().intValue())))
                .andExpect(jsonPath("$.data[*].fullName").value(hasItem("Picker Staff")))
                .andExpect(jsonPath("$.data[*].role").value(everyItem(is("STAFF"))))
                .andExpect(jsonPath("$.data[*].status").value(everyItem(is("ACTIVE"))))
                .andExpect(jsonPath("$.data[*].loginEmail").isEmpty())
                .andExpect(jsonPath("$.data[*].revocationStatus").isEmpty());
    }

    @Test
    void managerMayNotListAnythingElse() throws Exception {
        String manager = signedIn(AccountRole.MANAGER, AccountStatus.ACTIVE, true);

        for (String query : List.of("", "?role=STAFF", "?status=ACTIVE", "?role=MEMBER&status=ACTIVE",
                "?role=ADMIN&status=ACTIVE", "?role=STAFF&status=SUSPENDED")) {
            for (String path : List.of("/api/v1/accounts", "/api/v1/admin/accounts")) {
                mockMvc.perform(as(manager, get(path + query))).andExpect(status().isForbidden());
            }
        }
    }

    @Test
    void adminStillSeesEmailAndStaffIsRefused() throws Exception {
        Account member = account(AccountRole.MEMBER, AccountStatus.ACTIVE, "Listed Member");
        String admin = signedIn(AccountRole.ADMIN, AccountStatus.ACTIVE, true);
        String staff = signedIn(AccountRole.STAFF, AccountStatus.ACTIVE, true);

        mockMvc.perform(as(admin, get("/api/v1/admin/accounts?role=MEMBER&size=100")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].loginEmail").value(hasItem(member.getLoginEmail())));
        mockMvc.perform(as(staff, get(STAFF_LIST))).andExpect(status().isForbidden());
    }

    private MockHttpServletRequestBuilder as(String token, MockHttpServletRequestBuilder request) {
        return request.header("Authorization", "Bearer " + token);
    }

    private String signedIn(AccountRole role, AccountStatus status, boolean emailVerified) {
        Account account = account(role, status, "Access Test");
        String token = "token-" + account.getFirebaseUid();
        when(tokenVerificationService.verify(token)).thenReturn(new VerifiedIdentity(
                account.getFirebaseUid(), account.getLoginEmail(), emailVerified, Instant.now()));
        return token;
    }

    private Account account(AccountRole role, AccountStatus status, String fullName) {
        String uid = "access-" + UUID.randomUUID();
        Account account = accountRepository.save(Account.builder()
                .firebaseUid(uid)
                .loginEmail(uid + "@example.test")
                .role(role)
                .status(status)
                .version(0L)
                .build());
        profileRepository.save(AccountProfile.builder().accountId(account.getId()).fullName(fullName).build());
        return account;
    }
}
