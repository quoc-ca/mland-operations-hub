package edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.TokenVerificationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.VerifiedIdentity;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.dto.CreateDesignRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.dto.SaveDesignVersionRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class WorkshopRingDesignIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TokenVerificationService tokenVerificationService;

    @MockitoBean
    private AccountFacade accountFacade;

    private final String memberToken = "token-member-ring-it";
    private final String memberUid = "uid-member-ring-it";
    private final Long memberAccountId = 4001L;

    @BeforeEach
    void setUp() {
        when(tokenVerificationService.verify(memberToken)).thenReturn(
                new VerifiedIdentity(memberUid, "member-ring@mland.vn", true, Instant.now()));
        when(accountFacade.resolveActor(memberUid, true)).thenReturn(Optional.of(
                new CurrentAccountActor(memberAccountId, ActorRole.MEMBER, ActorStatus.ACTIVE, true, List.of())));
        when(accountFacade.resolveAccount(memberUid)).thenReturn(Optional.of(
                new AccountSummary(memberAccountId, ActorRole.MEMBER, ActorStatus.ACTIVE, "member-ring@mland.vn", Instant.now(), Instant.now())));
    }

    @Test
    void whenGetOptions_thenReturns200WithOptionsSchema() throws Exception {
        mockMvc.perform(get("/api/v1/workshop-design-options")
                        .param("packageReference", "PKG-COUPLE")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.packageReference").value("PKG-COUPLE"));
    }

    @Test
    void whenCreateAndGetDesign_thenReturns201And200() throws Exception {
        CreateDesignRequest req = CreateDesignRequest.builder()
                .packageReference("PKG-COUPLE")
                .startMode("BLANK")
                .build();

        String idempKey = "idemp-ring-" + UUID.randomUUID();

        MvcResult createResult = mockMvc.perform(post("/api/v1/workshop-designs")
                        .header("Authorization", "Bearer " + memberToken)
                        .header("Idempotency-Key", idempKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.designId").exists())
                .andReturn();

        String responseJson = createResult.getResponse().getContentAsString();
        Map<String, Object> map = objectMapper.readValue(responseJson, Map.class);
        String designId = (String) map.get("designId");

        // Retrieve design details
        mockMvc.perform(get("/api/v1/workshop-designs/" + designId)
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.designId").value(designId));
    }

    @Test
    void whenArchiveAndRestoreDesign_thenLifecycleSucceeds() throws Exception {
        CreateDesignRequest req = CreateDesignRequest.builder()
                .packageReference("PKG-COUPLE")
                .startMode("BLANK")
                .build();

        String idempKey = "idemp-ring-" + UUID.randomUUID();

        MvcResult createResult = mockMvc.perform(post("/api/v1/workshop-designs")
                        .header("Authorization", "Bearer " + memberToken)
                        .header("Idempotency-Key", idempKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.designId").exists())
                .andReturn();

        String responseJson = createResult.getResponse().getContentAsString();
        Map<String, Object> map = objectMapper.readValue(responseJson, Map.class);
        String designId = (String) map.get("designId");

        // Archive
        mockMvc.perform(post("/api/v1/workshop-designs/" + designId + "/archive")
                        .header("Authorization", "Bearer " + memberToken)
                        .header("Idempotency-Key", "idemp-archive-" + UUID.randomUUID())
                        .header("If-Match-Version", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));

        // Restore
        mockMvc.perform(post("/api/v1/workshop-designs/" + designId + "/restore")
                        .header("Authorization", "Bearer " + memberToken)
                        .header("Idempotency-Key", "idemp-restore-" + UUID.randomUUID())
                        .header("If-Match-Version", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SAVED"));
    }
}
