package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao.AccountRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.Account;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.enums.AccountRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.enums.AccountStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.InvalidTokenException;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.TokenExpiredException;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.TokenRevokedException;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.TokenVerificationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.VerifiedIdentity;
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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:application-test.properties")
class AccountManagementSecurityIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TokenVerificationService tokenVerificationService;

    @Autowired
    private AccountRepository accountRepository;

    @Test
    @DisplayName("Request to protected route without Bearer token returns 401 MISSING_BEARER_TOKEN")
    void whenMissingBearerToken_thenReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("MISSING_BEARER_TOKEN"))
            .andExpect(jsonPath("$.correlationId").isNotEmpty())
            .andExpect(jsonPath("$.timestamp").isNotEmpty())
            .andExpect(header().exists("X-Correlation-Id"));
    }

    @Test
    @DisplayName("Request with invalid Bearer token returns 401 INVALID_TOKEN")
    void whenInvalidToken_thenReturns401() throws Exception {
        when(tokenVerificationService.verify("invalid-token"))
            .thenThrow(new InvalidTokenException("Invalid token signature"));

        mockMvc.perform(get("/api/v1/accounts/me")
                .header("Authorization", "Bearer invalid-token"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("INVALID_TOKEN"))
            .andExpect(jsonPath("$.correlationId").isNotEmpty());
    }

    @Test
    @DisplayName("Request with expired token returns 401 TOKEN_EXPIRED")
    void whenExpiredToken_thenReturns401() throws Exception {
        when(tokenVerificationService.verify("expired-token"))
            .thenThrow(new TokenExpiredException("Token has expired"));

        mockMvc.perform(get("/api/v1/accounts/me")
                .header("Authorization", "Bearer expired-token"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("TOKEN_EXPIRED"))
            .andExpect(jsonPath("$.correlationId").isNotEmpty());
    }

    @Test
    @DisplayName("Request with revoked token returns 401 TOKEN_REVOKED")
    void whenRevokedToken_thenReturns401() throws Exception {
        when(tokenVerificationService.verify("revoked-token"))
            .thenThrow(new TokenRevokedException("Token was revoked"));

        mockMvc.perform(get("/api/v1/accounts/me")
                .header("Authorization", "Bearer revoked-token"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("TOKEN_REVOKED"))
            .andExpect(jsonPath("$.correlationId").isNotEmpty());
    }

    @Test
    @DisplayName("Request from suspended account returns 403 ACCOUNT_SUSPENDED")
    void whenSuspendedAccount_thenReturns403() throws Exception {
        String uid = "suspended-uid-" + UUID.randomUUID();
        Account suspended = Account.builder()
            .firebaseUid(uid)
            .loginEmail("suspended@test.com")
            .role(AccountRole.MEMBER)
            .status(AccountStatus.SUSPENDED)
            .build();
        accountRepository.save(suspended);

        when(tokenVerificationService.verify("suspended-token"))
            .thenReturn(new VerifiedIdentity(uid, "suspended@test.com", true, Instant.now()));

        mockMvc.perform(get("/api/v1/accounts/me")
                .header("Authorization", "Bearer suspended-token"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ACCOUNT_SUSPENDED"))
            .andExpect(jsonPath("$.correlationId").isNotEmpty());
    }

    @Test
    @DisplayName("Non-API unmapped route is denied by default (default-deny)")
    void whenNonApiRoute_thenDefaultDeny() throws Exception {
        mockMvc.perform(get("/admin/private-dashboard"))
            .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("CORS pre-flight request with allowed origin returns CORS headers")
    void whenCorsPreflight_thenReturnsCorsHeaders() throws Exception {
        mockMvc.perform(options("/api/v1/accounts/me")
                .header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "GET"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }
}
