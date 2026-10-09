package edu.fpt.sep490_g22.ppswbs_backend.members;

import edu.fpt.sep490_g22.ppswbs_backend.common.AuditEvent;
import edu.fpt.sep490_g22.ppswbs_backend.common.AuditEventWriter;
import edu.fpt.sep490_g22.ppswbs_backend.common.PolicyAcceptanceRequiredException;
import edu.fpt.sep490_g22.ppswbs_backend.common.SuspendedAccountException;
import edu.fpt.sep490_g22.ppswbs_backend.members.application.EntitlementEvaluator;
import edu.fpt.sep490_g22.ppswbs_backend.members.domain.Member;
import edu.fpt.sep490_g22.ppswbs_backend.members.domain.MemberStatus;
import edu.fpt.sep490_g22.ppswbs_backend.members.infrastructure.MemberAuthAuditEventRepository;
import edu.fpt.sep490_g22.ppswbs_backend.members.infrastructure.MemberRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class FoundationSecurityIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private EntitlementEvaluator entitlementEvaluator;

    @Autowired
    private AuditEventWriter auditEventWriter;

    @Autowired
    private MemberAuthAuditEventRepository auditEventRepository;

    @Test
    void whenMissingOrInvalidToken_thenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/members/me/entitlement"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));

        mockMvc.perform(get("/api/v1/members/me/entitlement")
                        .header("Authorization", "Bearer invalid-token-xyz"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void whenDuplicateExternalUserId_thenThrowsException() {
        Member member1 = Member.builder()
                .externalUserId("uid-unique-101")
                .email("test1@example.com")
                .status(MemberStatus.ACTIVE)
                .build();
        memberRepository.saveAndFlush(member1);

        Member member2 = Member.builder()
                .externalUserId("uid-unique-101")
                .email("test2@example.com")
                .status(MemberStatus.ACTIVE)
                .build();

        assertThrows(DataIntegrityViolationException.class, () -> {
            memberRepository.saveAndFlush(member2);
        });
    }

    @Test
    void whenMemberPendingPolicy_thenEntitlementEvaluatorThrowsPolicyAcceptanceRequired() {
        Member member = Member.builder()
                .externalUserId("uid-pending-policy")
                .email("pending@example.com")
                .status(MemberStatus.PENDING_POLICY_ACCEPTANCE)
                .build();
        memberRepository.saveAndFlush(member);

        assertThrows(PolicyAcceptanceRequiredException.class, () -> {
            entitlementEvaluator.verifyActiveEntitlement(member);
        });
    }

    @Test
    void whenMemberSuspended_thenEntitlementEvaluatorThrowsSuspendedAccountException() {
        Member member = Member.builder()
                .externalUserId("uid-suspended")
                .email("suspended@example.com")
                .status(MemberStatus.SUSPENDED)
                .build();
        memberRepository.saveAndFlush(member);

        assertThrows(SuspendedAccountException.class, () -> {
            entitlementEvaluator.verifyActiveEntitlement(member);
        });
    }

    @Test
    void whenWritingAuditEvent_thenPersistsSafelyWithoutSecrets() {
        AuditEvent event = AuditEvent.builder()
                .memberId(1L)
                .eventType("MEMBER_SIGN_IN")
                .occurredAt(Instant.now())
                .correlationId("cid-12345")
                .provider("google.com")
                .safeMetadata("{\"method\":\"google\"}")
                .build();

        auditEventWriter.writeEvent(event);

        assertEquals(1, auditEventRepository.count());
    }
}
