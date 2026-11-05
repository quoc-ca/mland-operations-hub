package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.WorkshopFinalBillApiService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.FinalPaymentCheckoutService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class WorkshopSettlementProblemHandlerTest {
    private final WorkshopFinalBillApiService service = mock(WorkshopFinalBillApiService.class);
    private final FinalPaymentCheckoutService paymentCheckout = mock(FinalPaymentCheckoutService.class);
    private final LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        validator.afterPropertiesSet();
        mvc = standaloneSetup(new WorkshopFinalBillController(service, paymentCheckout))
                .setControllerAdvice(new WorkshopSettlementProblemHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(new ObjectMapper()))
                .setValidator(validator)
                .build();
    }

    @Test
    void missingIdempotencyHeaderReturnsSafeTypedProblemAndServerCorrelation() throws Exception {
        mvc.perform(post("/api/v1/workshop-bookings/BK-SETTLE-1/final-bill")
                        .header("X-Correlation-Id", "client-controlled-correlation")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.correlationReference").isNotEmpty())
                .andExpect(jsonPath("$.correlationReference").value(
                        org.hamcrest.Matchers.not("client-controlled-correlation")));

        verifyNoInteractions(service);
    }

    @Test
    void validationProblemDoesNotEchoGuestContactValues() throws Exception {
        mvc.perform(post("/api/v1/workshop-bookings/BK-SETTLE-2/final-bill/charges")
                        .header("Idempotency-Key", "settlement-validation-test")
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType("application/json")
                        .content("""
                                {"catalogueAddOnReference":"ADDON-1","contactEmail":"guest-private@example.test",
                                 "contactPhone":"0900000000","description":"","amount":0,"reason":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.correlationReference").isNotEmpty())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("guest-private@example.test"))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("0900000000"))));

        verifyNoInteractions(service);
    }
}
