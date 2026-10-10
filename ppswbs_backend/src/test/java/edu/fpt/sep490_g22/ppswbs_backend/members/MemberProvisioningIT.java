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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class MemberProvisioningIT {

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
    void whenNewUIDProvisions_thenCreatesMemberWithPendingStatus() throws Exception {
        tokenVerifier.registerToken("token-uid-100", "uid-100", "alice@example.com", true);

        mockMvc.perform(put("/api/v1/members/me")
                        .header("Authorization", "Bearer token-uid-100"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.externalUserId").value("uid-100"))
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.status").value("PENDING_POLICY_ACCEPTANCE"));

        Member member = memberRepository.findByExternalUserId("uid-100").orElseThrow();
        assertEquals(MemberStatus.PENDING_POLICY_ACCEPTANCE, member.getStatus());
    }

    @Test
    void whenSameUIDProvisionsTwice_thenReturnsExistingMemberWith200() throws Exception {
        tokenVerifier.registerToken("token-uid-101", "uid-101", "bob@example.com", true);

        // First call
        mockMvc.perform(put("/api/v1/members/me")
                        .header("Authorization", "Bearer token-uid-101"))
                .andExpect(status().isCreated());

        // Second call
        mockMvc.perform(put("/api/v1/members/me")
                        .header("Authorization", "Bearer token-uid-101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.externalUserId").value("uid-101"));

        assertEquals(1, memberRepository.findAll().stream().filter(m -> m.getExternalUserId().equals("uid-101")).count());
    }

    @Test
    void whenDifferentUIDHasMatchingEmail_thenDoesNotMergeAccounts() throws Exception {
        tokenVerifier.registerToken("token-uid-google", "uid-google-201", "shared@example.com", true);
        tokenVerifier.registerToken("token-uid-password", "uid-password-202", "shared@example.com", true);

        // Register Google user first
        mockMvc.perform(put("/api/v1/members/me")
                        .header("Authorization", "Bearer token-uid-google"))
                .andExpect(status().isCreated());

        // Register Email/Password user second with matching email
        mockMvc.perform(put("/api/v1/members/me")
                        .header("Authorization", "Bearer token-uid-password"))
                .andExpect(status().isCreated());

        // Verify two distinct accounts exist in DB
        Member m1 = memberRepository.findByExternalUserId("uid-google-201").orElseThrow();
        Member m2 = memberRepository.findByExternalUserId("uid-password-202").orElseThrow();
        assertNotEquals(m1.getId(), m2.getId());
    }
}
