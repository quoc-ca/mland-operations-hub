package edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.controller.WorkshopDesignOptionsController;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.controller.WorkshopRingDesignController;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.controller.WorkshopRingDesignExceptionHandler;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.dto.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.rules.DesignOptionRulesPort;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.service.RingDesignException;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.service.WorkshopRingDesignApplicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class WorkshopRingDesignControllerMockMvcTest {

    private MockMvc mockMvc;

    @Mock
    private WorkshopRingDesignApplicationService applicationService;

    @Mock
    private DesignOptionRulesPort rulesPort;

    @InjectMocks
    private WorkshopRingDesignController designController;

    @InjectMocks
    private WorkshopDesignOptionsController optionsController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(designController, optionsController)
                .setControllerAdvice(new WorkshopRingDesignExceptionHandler())
                .setMessageConverters(new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/workshop-design-options should return 200 with options schema")
    void getOptions_Success() throws Exception {
        DesignOptionsResponseDto optionsDto = DesignOptionsResponseDto.builder()
                .packageReference("PKG-COUPLE")
                .rulesVersion("v1.0")
                .materials(Collections.emptyList())
                .shapes(Collections.emptyList())
                .sizes(List.of("8", "9", "10"))
                .components(Collections.emptyList())
                .attachments(Collections.emptyList())
                .engravingConfig(DesignOptionsResponseDto.EngravingConfigDto.builder().supported(true).build())
                .models(Collections.emptyList())
                .build();

        when(rulesPort.loadPackageOptions("PKG-COUPLE", null)).thenReturn(optionsDto);

        mockMvc.perform(get("/api/v1/workshop-design-options")
                        .param("packageReference", "PKG-COUPLE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.packageReference").value("PKG-COUPLE"))
                .andExpect(jsonPath("$.rulesVersion").value("v1.0"))
                .andExpect(jsonPath("$.sizes[0]").value("8"));
    }

    @Test
    @DisplayName("POST /api/v1/workshop-designs should return 201 Created")
    void createDesign_Success() throws Exception {
        CreateDesignRequest req = CreateDesignRequest.builder()
                .packageReference("PKG-COUPLE")
                .startMode("BLANK")
                .build();

        DesignResponseDto res = DesignResponseDto.builder()
                .designId("DSG-1234567890123456")
                .packageReference("PKG-COUPLE")
                .designVersion(1)
                .status("DRAFT")
                .build();

        when(applicationService.createDraft(any(), any(), any(), eq("IDEMP-KEY-1234567890")))
                .thenReturn(res);

        mockMvc.perform(post("/api/v1/workshop-designs")
                        .header("Idempotency-Key", "IDEMP-KEY-1234567890")
                        .header("X-Guest-Design-Session", "GDS-SESSION-1234567890")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.designId").value("DSG-1234567890123456"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.designVersion").value(1));
    }

    @Test
    @DisplayName("POST /api/v1/workshop-designs should return 400 when Idempotency-Key is missing")
    void createDesign_MissingIdempotencyHeader_Returns400() throws Exception {
        CreateDesignRequest req = CreateDesignRequest.builder()
                .packageReference("PKG-COUPLE")
                .startMode("BLANK")
                .build();

        mockMvc.perform(post("/api/v1/workshop-designs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DESIGN_INPUT_INVALID"));
    }

    @Test
    @DisplayName("GET /api/v1/workshop-designs/{designId} should return 200 with design detail")
    void getDesign_Success() throws Exception {
        DesignResponseDto res = DesignResponseDto.builder()
                .designId("DSG-1234567890123456")
                .packageReference("PKG-COUPLE")
                .designVersion(1)
                .status("DRAFT")
                .build();

        when(applicationService.getDesign(eq("DSG-1234567890123456"), any(), any())).thenReturn(res);

        mockMvc.perform(get("/api/v1/workshop-designs/DSG-1234567890123456")
                        .header("X-Guest-Design-Session", "GDS-SESSION-1234567890"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.designId").value("DSG-1234567890123456"));
    }

    @Test
    @DisplayName("POST /api/v1/workshop-designs/{designId}/versions should return 200")
    void saveVersion_Success() throws Exception {
        SaveDesignVersionRequest req = SaveDesignVersionRequest.builder()
                .packageReference("PKG-COUPLE")
                .materialReference("MAT-SILVER-925")
                .shapeReference("SHP-ROUND")
                .ringSize("8")
                .components(Collections.emptyList())
                .attachments(Collections.emptyList())
                .engraving(EngravingInputDto.builder().enabled(false).build())
                .build();

        DesignResponseDto res = DesignResponseDto.builder()
                .designId("DSG-1234567890123456")
                .packageReference("PKG-COUPLE")
                .designVersion(2)
                .status("VALIDATED")
                .evaluation(EvaluationResultDto.builder().estimatedPrice("250000.00").currency("VND").build())
                .build();

        when(applicationService.saveVersion(eq("DSG-1234567890123456"), any(), any(), any(), eq(1L), eq("IDEMP-KEY-SAVE-01")))
                .thenReturn(res);

        mockMvc.perform(post("/api/v1/workshop-designs/DSG-1234567890123456/versions")
                        .header("Idempotency-Key", "IDEMP-KEY-SAVE-01")
                        .header("If-Match-Version", 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.designVersion").value(2))
                .andExpect(jsonPath("$.status").value("VALIDATED"))
                .andExpect(jsonPath("$.evaluation.estimatedPrice").value("250000.00"));
    }

    @Test
    @DisplayName("POST /api/v1/workshop-designs/{designId}/archive should return 200 with ARCHIVED status")
    void archiveDesign_Success() throws Exception {
        DesignResponseDto res = DesignResponseDto.builder()
                .designId("DSG-1234567890123456")
                .packageReference("PKG-COUPLE")
                .designVersion(1)
                .status("ARCHIVED")
                .build();

        when(applicationService.archive(eq("DSG-1234567890123456"), any(), any(), eq(1L), eq("IDEMP-KEY-ARCHIVE")))
                .thenReturn(res);

        mockMvc.perform(post("/api/v1/workshop-designs/DSG-1234567890123456/archive")
                        .header("Idempotency-Key", "IDEMP-KEY-ARCHIVE")
                        .header("If-Match-Version", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));
    }

    @Test
    @DisplayName("POST /api/v1/workshop-designs/{designId}/attach-to-booking should return 200 with BookingDesignSnapshot")
    void attachToBooking_Success() throws Exception {
        AttachDesignRequestDto req = AttachDesignRequestDto.builder()
                .bookingReference("BKG-TEST-123")
                .packageReference("PKG-COUPLE")
                .branchReference("BRANCH-HCM-01")
                .build();

        BookingDesignSnapshotDto snapshot = BookingDesignSnapshotDto.builder()
                .designReference("DSG-1234567890123456")
                .designVersion(2)
                .packageReference("PKG-COUPLE")
                .snapshot(Map.of("material", "SILVER"))
                .build();

        when(applicationService.attachToBooking(eq("DSG-1234567890123456"), any(), any(), any(), eq(2L), eq("IDEMP-ATTACH")))
                .thenReturn(snapshot);

        mockMvc.perform(post("/api/v1/workshop-designs/DSG-1234567890123456/attach-to-booking")
                        .header("Idempotency-Key", "IDEMP-ATTACH")
                        .header("If-Match-Version", 2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.designReference").value("DSG-1234567890123456"))
                .andExpect(jsonPath("$.designVersion").value(2))
                .andExpect(jsonPath("$.snapshot.material").value("SILVER"));
    }

    @Test
    @DisplayName("Should return 401 when Guest session has expired")
    void error_SessionExpired_Returns401() throws Exception {
        when(applicationService.getDesign(any(), any(), any()))
                .thenThrow(RingDesignException.sessionExpired("Guest session expired"));

        mockMvc.perform(get("/api/v1/workshop-designs/DSG-1234567890123456")
                        .header("X-Guest-Design-Session", "GDS-EXPIRED"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("DESIGN_SESSION_EXPIRED"));
    }
}

