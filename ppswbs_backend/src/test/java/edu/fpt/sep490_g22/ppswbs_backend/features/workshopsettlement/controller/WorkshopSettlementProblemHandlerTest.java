package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.FolioCorrectionService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.TicketFolioApiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class WorkshopSettlementProblemHandlerTest {
    private final TicketFolioApiService folioApi = mock(TicketFolioApiService.class);
    private final FolioCorrectionService correctionService = mock(FolioCorrectionService.class);
    private final LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        validator.afterPropertiesSet();
        mvc = standaloneSetup(new TicketFolioController(folioApi, correctionService))
                .setControllerAdvice(new WorkshopSettlementProblemHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(new ObjectMapper()))
                .setValidator(validator)
                .build();
    }

    @Test
    void malformedBodyReturnsSafeTypedProblemAndServerCorrelation() throws Exception {
        mvc.perform(post("/api/v1/workshop-tickets/TK-SETTLE-1/folio/charges")
                        .header("X-Correlation-Id", "client-controlled-correlation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("not-json")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.correlationReference").isNotEmpty())
                .andExpect(jsonPath("$.correlationReference").value(
                        org.hamcrest.Matchers.not("client-controlled-correlation")));

        verifyNoInteractions(folioApi);
    }

    @Test
    void validationProblemDoesNotEchoGuestContactValues() throws Exception {
        mvc.perform(post("/api/v1/workshop-tickets/TK-SETTLE-2/folio/charges")
                        .header("Idempotency-Key", "settlement-validation-test")
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType("application/json")
                        .content("""
                                {"catalogueAddOnReference":"ADDON-1","contactEmail":"guest-private@example.test",
                                 "contactPhone":"0900000000","description":"","amount":0,"reason":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(content().string(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("guest-private@example.test"))))
                .andExpect(content().string(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("0900000000"))));

        verifyNoInteractions(folioApi);
    }
}
