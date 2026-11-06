package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.SettlementCaseProjectionDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.RequiredSessionChangeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class RequiredSessionChangeControllerTest {
    private final RequiredSessionChangeService service = mock(RequiredSessionChangeService.class);
    private final LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        validator.afterPropertiesSet();
        mvc = standaloneSetup(new RequiredSessionChangeController(service))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(new ObjectMapper()))
                .setValidator(validator)
                .build();
    }

    @Test
    void postCreatesCaseWithIdempotencyAndCorrelationHeaders() throws Exception {
        when(service.open(eq("BK-1"), eq("change-1"), eq("CORR-1"), any(), isNull()))
                .thenReturn(projection());

        mvc.perform(post("/api/v1/workshop-bookings/BK-1/required-session-change-cases")
                        .header("Idempotency-Key", "change-1")
                        .header("X-Correlation-Id", "CORR-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetRequiredSessionReferences\":[\"11111111-1111-1111-1111-111111111111\"],\"reason\":\"schedule conflict\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.caseReference").value("CASE-1"))
                .andExpect(jsonPath("$.caseType").value("REQUIRED_SESSION_CHANGE"));

        verify(service).open(eq("BK-1"), eq("change-1"), eq("CORR-1"), any(), isNull());
    }

    @Test
    void getBindsGuestContactProofAndDoesNotExposeIt() throws Exception {
        when(service.read("BK-1", "CASE-1", "guest@example.test", null, null)).thenReturn(projection());

        mvc.perform(get("/api/v1/workshop-bookings/BK-1/required-session-change-cases/CASE-1")
                        .param("contactEmail", "guest@example.test")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingCode").value("BK-1"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("guest@example.test"))));

        verify(service).read("BK-1", "CASE-1", "guest@example.test", null, null);
    }

    @Test
    void postValidatesRequiredTargetSetAndReason() throws Exception {
        mvc.perform(post("/api/v1/workshop-bookings/BK-1/required-session-change-cases")
                        .header("Idempotency-Key", "change-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetRequiredSessionReferences\":[],\"reason\":\" \"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    private static SettlementCaseProjectionDto projection() {
        return new SettlementCaseProjectionDto("CASE-1", "REQUIRED_SESSION_CHANGE", "RESOLVED", "BK-1", "CORR-1");
    }
}
