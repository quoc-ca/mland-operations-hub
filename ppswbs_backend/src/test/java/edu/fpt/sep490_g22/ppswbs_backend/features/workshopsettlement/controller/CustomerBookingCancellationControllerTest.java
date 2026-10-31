package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.SettlementCaseProjectionDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.CustomerBookingCancellationService;
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

class CustomerBookingCancellationControllerTest {
    private final CustomerBookingCancellationService service = mock(CustomerBookingCancellationService.class);
    private final LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        validator.afterPropertiesSet();
        mvc = standaloneSetup(new CustomerBookingCancellationController(service))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(new ObjectMapper()))
                .setValidator(validator)
                .build();
    }

    @Test
    void memberPostMayOmitProofBodyAndBindsRequestHeaders() throws Exception {
        when(service.open("BK-1", "cancel-1", "CORR-1", null, null)).thenReturn(projection());

        mvc.perform(post("/api/v1/workshop-bookings/BK-1/cancellation-cases")
                        .header("Idempotency-Key", "cancel-1")
                        .header("X-Correlation-Id", "CORR-1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.caseReference").value("CASE-1"));

        verify(service).open("BK-1", "cancel-1", "CORR-1", null, null);
    }

    @Test
    void guestProofIsBoundButNeverReturned() throws Exception {
        when(service.open(eq("BK-1"), eq("cancel-2"), isNull(), any(), isNull())).thenReturn(projection());

        mvc.perform(post("/api/v1/workshop-bookings/BK-1/cancellation-cases")
                        .header("Idempotency-Key", "cancel-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contactEmail\":\"guest@example.test\"}"))
                .andExpect(status().isCreated())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("guest@example.test"))));

        verify(service).open(eq("BK-1"), eq("cancel-2"), isNull(),
                argThat(proof -> "guest@example.test".equals(proof.contactEmail())), isNull());
    }

    @Test
    void malformedGuestEmailIsRejectedBeforeService() throws Exception {
        mvc.perform(post("/api/v1/workshop-bookings/BK-1/cancellation-cases")
                        .header("Idempotency-Key", "cancel-3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contactEmail\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void getBindsGuestProofQueryAndReturnsSafeProjection() throws Exception {
        when(service.read(eq("BK-1"), eq("CASE-1"), any(), isNull())).thenReturn(projection());

        mvc.perform(get("/api/v1/workshop-bookings/BK-1/cancellation-cases/CASE-1")
                        .param("contactPhone", "+84901234567")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingCode").value("BK-1"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("+84901234567"))));

        verify(service).read(eq("BK-1"), eq("CASE-1"),
                argThat(proof -> proof.contactEmail() == null && "+84901234567".equals(proof.contactPhone())), isNull());
    }

    private static SettlementCaseProjectionDto projection() {
        return new SettlementCaseProjectionDto("CASE-1", "CUSTOMER_CANCELLATION", "PENDING_PROVIDER", "BK-1", "CORR-1");
    }
}
