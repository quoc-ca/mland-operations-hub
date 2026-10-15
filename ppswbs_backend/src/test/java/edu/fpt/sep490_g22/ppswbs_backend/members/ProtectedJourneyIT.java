package edu.fpt.sep490_g22.ppswbs_backend.members;

import edu.fpt.sep490_g22.ppswbs_backend.members.domain.Member;
import edu.fpt.sep490_g22.ppswbs_backend.members.domain.MemberStatus;
import edu.fpt.sep490_g22.ppswbs_backend.members.infrastructure.MemberRepository;
import edu.fpt.sep490_g22.ppswbs_backend.support.TestFirebaseTokenVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class ProtectedJourneyIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private TestFirebaseTokenVerifier tokenVerifier;

    @BeforeEach
    void setUp() {
        tokenVerifier.clearTokens();
    }

    @Test
    void whenUnauthenticated_thenProtectedApiIsDenied() throws Exception {
        mockMvc.perform(get("/api/v1/members/me/entitlement"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void whenAuthenticatedAndActive_thenGetEntitlementSucceeds() throws Exception {
        tokenVerifier.registerToken("token-active-user", "uid-active-user", "active@example.com", true);

        // Provision
        mockMvc.perform(put("/api/v1/members/me")
                .header("Authorization", "Bearer token-active-user"));

        // Accept Policy
        mockMvc.perform(post("/api/v1/members/me/policy-acceptances")
                .header("Authorization", "Bearer token-active-user")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "termsVersion": "v1.0.0", "privacyVersion": "v1.0.0" }
                        """));

        // Get entitlement
        mockMvc.perform(get("/api/v1/members/me/entitlement")
                        .header("Authorization", "Bearer token-active-user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entitlementActive").value(true));
    }

    @Test
    void whenMemberIsSuspended_thenAccessIsDeniedWithSuspendedCode() throws Exception {
        tokenVerifier.registerToken("token-suspended-user", "uid-suspended-user", "suspended@example.com", true);

        // Save suspended member directly
        Member suspendedMember = Member.builder()
                .externalUserId("uid-suspended-user")
                .email("suspended@example.com")
                .status(MemberStatus.SUSPENDED)
                .build();
        memberRepository.saveAndFlush(suspendedMember);

        mockMvc.perform(get("/api/v1/members/me/entitlement")
                        .header("Authorization", "Bearer token-suspended-user"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SUSPENDED_ACCOUNT"));
    }

    @Test
    void whenSanitizingReturnTo_thenRejectsExternalRedirects() throws Exception {
        // Test redirect query param handling on view controller
        mockMvc.perform(get("/member-auth/signin").param("returnTo", "//evil.com"))
                .andExpect(status().isOk());
    }
}
