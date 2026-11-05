package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao.AccountRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.Account;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.enums.AccountRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.enums.AccountStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.TokenExpiredException;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.TokenRevokedException;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.TokenVerificationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.VerifiedIdentity;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.support.LoyaltyTestSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * T072 (006) over HTTP on MySQL 8.4 with 001's REAL identity gate and actor resolution; only the Firebase signature
 * check ({@link TokenVerificationService}) is replaced, because 001 does not initialise Firebase Admin yet (Phan Sơn,
 * 2026-11-01). The session rules FE-06 relies on: a revoked or expired token is 401, a cookie never replaces the
 * bearer, and the account's current status and role are read on every request — a suspended Manager or one demoted
 * to Member loses FE-06 management at once, a Member never reaches Manager routes, an Admin has no FE-06 authority.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(LoyaltyTestSupport.MYSQL_PROFILE)
class LoyaltySessionIT {

    private static final String MANAGER_ROUTE = "/api/v1/promotion-programs";
    private static final String MEMBER_ROUTE = "/api/v1/members/me/loyalty-accounts/current";

    @Autowired private MockMvc mvc;
    @Autowired private AccountRepository accounts;
    @Autowired private TransactionTemplate transaction;
    @MockitoBean private TokenVerificationService tokens;

    private record SignedIn(long accountId, String bearer, String token) {
    }

    private SignedIn signIn(AccountRole role) {
        String uid = "loyalty-session-it-" + UUID.randomUUID();
        Account account = accounts.save(Account.builder().firebaseUid(uid).loginEmail(uid + "@example.test")
                .role(role).status(AccountStatus.ACTIVE).build());
        String token = "token-" + uid;
        when(tokens.verify(token)).thenReturn(new VerifiedIdentity(uid, uid + "@example.test", true, Instant.now()));
        return new SignedIn(account.getId(), "Bearer " + token, token);
    }

    private int status(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request).andReturn().getResponse().getStatus();
    }

    private int as(SignedIn who, String route) throws Exception {
        return status(get(route).header(HttpHeaders.AUTHORIZATION, who.bearer()));
    }

    @Test
    void aRevokedOrExpiredTokenIsRefused() throws Exception {
        SignedIn manager = signIn(AccountRole.MANAGER);
        assertThat(as(manager, MANAGER_ROUTE)).isEqualTo(200);

        when(tokens.verify(manager.token())).thenThrow(new TokenRevokedException("revoked"));
        assertThat(as(manager, MANAGER_ROUTE)).isEqualTo(401);

        SignedIn member = signIn(AccountRole.MEMBER);
        when(tokens.verify(member.token())).thenThrow(new TokenExpiredException("expired"));
        assertThat(as(member, MEMBER_ROUTE)).isEqualTo(401);
    }

    @Test
    void aCookieNeverReplacesTheBearer() throws Exception {
        SignedIn manager = signIn(AccountRole.MANAGER);
        assertThat(status(get(MANAGER_ROUTE).cookie(new Cookie("token", manager.token())))).isEqualTo(401);
        assertThat(status(get(MANAGER_ROUTE).cookie(new Cookie("Authorization", manager.bearer())))).isEqualTo(401);
    }

    @Test
    void theCurrentStatusAndRoleAreReadOnEveryRequest() throws Exception {
        SignedIn manager = signIn(AccountRole.MANAGER);
        assertThat(as(manager, MANAGER_ROUTE)).isEqualTo(200);

        changeAccount(manager.accountId(), AccountRole.MANAGER, AccountStatus.SUSPENDED);
        assertThat(as(manager, MANAGER_ROUTE)).as("suspended with a still-valid token").isIn(401, 403);

        changeAccount(manager.accountId(), AccountRole.MEMBER, AccountStatus.ACTIVE);
        assertThat(as(manager, MANAGER_ROUTE)).as("demoted to Member").isEqualTo(403);
        assertThat(as(manager, MEMBER_ROUTE)).as("now a Member").isEqualTo(200);
    }

    @Test
    void noMemberToManagerAndNoAdminAuthority() throws Exception {
        assertThat(as(signIn(AccountRole.MEMBER), MANAGER_ROUTE)).isEqualTo(403);
        SignedIn admin = signIn(AccountRole.ADMIN);
        assertThat(as(admin, MANAGER_ROUTE)).isEqualTo(403);
        assertThat(as(admin, MEMBER_ROUTE)).isEqualTo(403);
        assertThat(as(signIn(AccountRole.STAFF), MANAGER_ROUTE)).as("FE-06 management is Manager-only").isEqualTo(403);
    }

    private void changeAccount(long accountId, AccountRole role, AccountStatus status) {
        transaction.executeWithoutResult(tx -> {
            Account account = accounts.findById(accountId).orElseThrow();
            account.setRole(role);
            account.setStatus(status);
        });
    }
}
