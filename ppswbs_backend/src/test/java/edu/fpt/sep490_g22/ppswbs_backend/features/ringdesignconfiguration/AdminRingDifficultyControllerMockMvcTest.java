package edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.controller.AdminRingDifficultyController;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.controller.WorkshopRingDesignExceptionHandler;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.domain.OptionScoreKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.dto.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.service.AdminRingDifficultyService;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.service.RingDesignException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminRingDifficultyControllerMockMvcTest {

    private MockMvc mockMvc;

    @Mock
    private AdminRingDifficultyService adminRingDifficultyService;

    @InjectMocks
    private AdminRingDifficultyController controller;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new WorkshopRingDesignExceptionHandler())
                .setMessageConverters(new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(ActorRole role, ActorStatus status) {
        CurrentAccountActor actor = new CurrentAccountActor(99L, role, status, true, List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(actor, null, List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
    }

    private PolicyValuesDto sampleCandidate() {
        return PolicyValuesDto.builder()
                .optionScores(List.of(OptionScoreDto.builder()
                        .kind(OptionScoreKind.MATERIAL)
                        .reference("MAT-SILVER-925")
                        .score("1.0000")
                        .build()))
                .engravingBonus("0.5000")
                .customImageBonusPerImage("0.3000")
                .thresholds(DifficultyThresholdsDto.builder()
                        .easyMax("3.0000")
                        .mediumMax("6.0000")
                        .hardMax("9.0000")
                        .veryHardMax("11.0000")
                        .waxAbove("9.0000")
                        .silverClayGemstonesAbove(5)
                        .build())
                .build();
    }

    @Test
    @DisplayName("GET /active without auth returns 401")
    void getActive_Unauthenticated_Returns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/workshop-ring-difficulty/active"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ADMIN_AUTH_REQUIRED"));
    }

    @Test
    @DisplayName("GET /active as MANAGER returns 403")
    void getActive_Manager_Returns403() throws Exception {
        authenticateAs(ActorRole.MANAGER, ActorStatus.ACTIVE);
        mockMvc.perform(get("/api/v1/admin/workshop-ring-difficulty/active"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ADMIN_ROLE_REQUIRED"));
    }

    @Test
    @DisplayName("GET /active as suspended ADMIN returns 403")
    void getActive_SuspendedAdmin_Returns403() throws Exception {
        authenticateAs(ActorRole.ADMIN, ActorStatus.SUSPENDED);
        mockMvc.perform(get("/api/v1/admin/workshop-ring-difficulty/active"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ADMIN_ROLE_REQUIRED"));
    }

    @Test
    @DisplayName("GET /active as active ADMIN returns 200")
    void getActive_ActiveAdmin_Returns200() throws Exception {
        authenticateAs(ActorRole.ADMIN, ActorStatus.ACTIVE);
        when(adminRingDifficultyService.getActivePolicy()).thenReturn(PolicyVersionDto.builder()
                .rulesVersion("2026.11-v1.0")
                .status("EFFECTIVE")
                .publishedBy("SYSTEM_SEED")
                .publishedAt(Instant.now())
                .build());

        mockMvc.perform(get("/api/v1/admin/workshop-ring-difficulty/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rulesVersion").value("2026.11-v1.0"))
                .andExpect(jsonPath("$.status").value("EFFECTIVE"));
    }

    @Test
    @DisplayName("POST /versions without previewReference delegates to service returning 400 PREVIEW_REQUIRED")
    void publish_MissingPreviewReference_Returns400() throws Exception {
        authenticateAs(ActorRole.ADMIN, ActorStatus.ACTIVE);
        PublishRequestDto req = PublishRequestDto.builder()
                .expectedActiveRulesVersion("2026.11-v1.0")
                .candidate(sampleCandidate())
                .previewReference(null)
                .build();

        when(adminRingDifficultyService.publishPolicy(any(), eq(99L), eq("IDEMP-1"), any()))
                .thenThrow(RingDesignException.badRequest("PREVIEW_REQUIRED", "Preview required"));

        mockMvc.perform(post("/api/v1/admin/workshop-ring-difficulty/versions")
                        .header("Idempotency-Key", "IDEMP-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PREVIEW_REQUIRED"));
    }

    @Test
    @DisplayName("POST /versions as active ADMIN returns 201")
    void publish_ActiveAdmin_Returns201() throws Exception {
        authenticateAs(ActorRole.ADMIN, ActorStatus.ACTIVE);
        PublishRequestDto req = PublishRequestDto.builder()
                .expectedActiveRulesVersion("2026.11-v1.0")
                .candidate(sampleCandidate())
                .previewReference(UUID.randomUUID().toString())
                .build();

        when(adminRingDifficultyService.publishPolicy(any(), eq(99L), eq("IDEMP-2"), any()))
                .thenReturn(PolicyVersionDto.builder()
                        .rulesVersion("2026.11-v2.0")
                        .status("EFFECTIVE")
                        .publishedBy("ADMIN:99")
                        .publishedAt(Instant.now())
                        .build());

        mockMvc.perform(post("/api/v1/admin/workshop-ring-difficulty/versions")
                        .header("Idempotency-Key", "IDEMP-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rulesVersion").value("2026.11-v2.0"))
                .andExpect(jsonPath("$.status").value("EFFECTIVE"));
    }
}
