package edu.fpt.sep490_g22.ppswbs_backend.members;

import edu.fpt.sep490_g22.ppswbs_backend.members.domain.Member;
import edu.fpt.sep490_g22.ppswbs_backend.members.domain.MemberStatus;
import edu.fpt.sep490_g22.ppswbs_backend.members.infrastructure.MemberPolicyAcceptanceRepository;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class PolicyAcceptanceIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private MemberPolicyAcceptanceRepository acceptanceRepository;

    @Autowired
    private TestFirebaseTokenVerifier tokenVerifier;

    @BeforeEach
    void setUp() {
        tokenVerifier.clearTokens();
    }

    @Test
    void whenPublicGetPolicies_thenReturnsEffectivePolicyVersions() throws Exception {
        mockMvc.perform(get("/api/v1/policies/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.policies").isArray())
                .andExpect(jsonPath("$.policies[0].version").value("v1.0.0"));
    }

    @Test
    void whenAcceptCurrentPolicies_thenActivatesMemberEntitlement() throws Exception {
        tokenVerifier.registerToken("token-uid-policy", "uid-policy-300", "policy@example.com", true);

        // Provision member
        mockMvc.perform(put("/api/v1/members/me")
                        .header("Authorization", "Bearer token-uid-policy"))
                .andExpect(status().isCreated());

        String body = """
                {
                    "termsVersion": "v1.0.0",
                    "privacyVersion": "v1.0.0"
                }
                """;

        // Accept policy
        mockMvc.perform(post("/api/v1/members/me/policy-acceptances")
                        .header("Authorization", "Bearer token-uid-policy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.entitlementActive").value(true))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        Member member = memberRepository.findByExternalUserId("uid-policy-300").orElseThrow();
        assertEquals(MemberStatus.ACTIVE, member.getStatus());
        assertEquals(2, acceptanceRepository.findAllByMemberId(member.getId()).size());
    }

    @Test
    void whenAcceptInvalidPolicyVersion_thenReturnsBadRequest() throws Exception {
        tokenVerifier.registerToken("token-uid-invalid-policy", "uid-policy-301", "policy2@example.com", true);

        mockMvc.perform(put("/api/v1/members/me")
                        .header("Authorization", "Bearer token-uid-invalid-policy"))
                .andExpect(status().isCreated());

        String body = """
                {
                    "termsVersion": "v9.9.9",
                    "privacyVersion": "v1.0.0"
                }
                """;

        mockMvc.perform(post("/api/v1/members/me/policy-acceptances")
                        .header("Authorization", "Bearer token-uid-invalid-policy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
}
