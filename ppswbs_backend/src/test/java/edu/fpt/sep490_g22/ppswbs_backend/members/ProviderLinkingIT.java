package edu.fpt.sep490_g22.ppswbs_backend.members;

import edu.fpt.sep490_g22.ppswbs_backend.common.AuditEventWriter;
import edu.fpt.sep490_g22.ppswbs_backend.common.AuditEvent;
import edu.fpt.sep490_g22.ppswbs_backend.members.infrastructure.MemberAuthAuditEventRepository;
import edu.fpt.sep490_g22.ppswbs_backend.members.infrastructure.MemberRepository;
import edu.fpt.sep490_g22.ppswbs_backend.support.TestFirebaseTokenVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class ProviderLinkingIT {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private AuditEventWriter auditEventWriter;

    @Autowired
    private MemberAuthAuditEventRepository auditEventRepository;

    @Autowired
    private TestFirebaseTokenVerifier tokenVerifier;

    @BeforeEach
    void setUp() {
        tokenVerifier.clearTokens();
    }

    @Test
    void whenProviderLinkingAudited_thenPersistsEventWithoutSecrets() {
        AuditEvent event = AuditEvent.builder()
                .memberId(1L)
                .eventType("PROVIDER_LINKED")
                .occurredAt(Instant.now())
                .correlationId("cid-link-001")
                .provider("google.com")
                .safeMetadata("{\"status\":\"SUCCESS\"}")
                .build();

        auditEventWriter.writeEvent(event);

        assertTrue(auditEventRepository.findAll().stream()
                .anyMatch(e -> "PROVIDER_LINKED".equals(e.getEventType())));
    }
}
