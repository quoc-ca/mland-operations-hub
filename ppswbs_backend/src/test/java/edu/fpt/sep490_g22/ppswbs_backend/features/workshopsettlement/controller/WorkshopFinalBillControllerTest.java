package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.FinalBillProjectionDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.FinalPaymentAttemptResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.FinalPaymentCheckoutService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.WorkshopFinalBillApiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class WorkshopFinalBillControllerTest {
    private final WorkshopFinalBillApiService service = mock(WorkshopFinalBillApiService.class);
    private final FinalPaymentCheckoutService paymentCheckout = mock(FinalPaymentCheckoutService.class);
    private final LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        validator.afterPropertiesSet();
        mvc = standaloneSetup(new WorkshopFinalBillController(service, paymentCheckout))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(new ObjectMapper().findAndRegisterModules()))
                .setValidator(validator)
                .build();
    }

    @Test
    void getFinalBillDelegatesGuestProofAndReturnsOnlyCustomerProjection() throws Exception {
        when(service.read(eq("BK-1"), eq("guest@example.test"), isNull(), isNull()))
                .thenReturn(projection());

        mvc.perform(get("/api/v1/workshop-bookings/BK-1/final-bill")
                        .param("contactEmail", "guest@example.test")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingCode").value("BK-1"))
                .andExpect(jsonPath("$.outstandingAmount").value(0))
                .andExpect(jsonPath("$.charges[0].description").value("Workshop materials"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("guest@example.test"))));

        verify(service).read("BK-1", "guest@example.test", null, null);
    }

    @Test
    void createFinalBillUsesIdempotencyAndReturnsCreated() throws Exception {
        when(service.create(eq("BK-1"), eq("create-bill-1"), isNull(), isNull())).thenReturn(projection());

        mvc.perform(post("/api/v1/workshop-bookings/BK-1/final-bill")
                        .header("Idempotency-Key", "create-bill-1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bookingCode").value("BK-1"));

        verify(service).create("BK-1", "create-bill-1", null, null);
    }

    @Test
    void finalPaymentAttemptRouteReturnsShortLivedCheckoutWithoutAcceptingClientAmount() throws Exception {
        when(paymentCheckout.start(eq("BK-1"), eq("final-payment-attempt-001"), isNull(), anyString(), isNull()))
                .thenReturn(new FinalPaymentAttemptResponse("FPA-1", "FIN-1", 75_000L, "VND",
                        "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?token=opaque",
                        java.time.Instant.parse("2026-10-31T16:10:00Z"), "PENDING"));

        mvc.perform(post("/api/v1/workshop-bookings/BK-1/final-bill/payment-attempts")
                        .header("Idempotency-Key", "final-payment-attempt-001")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amountVnd").value(75_000))
                .andExpect(jsonPath("$.currency").value("VND"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.paymentUrl").value(org.hamcrest.Matchers.startsWith("https://sandbox.vnpayment.vn/")));

        verify(paymentCheckout).start(eq("BK-1"), eq("final-payment-attempt-001"), isNull(), anyString(), isNull());
    }

    @Test
    void chargeAndFinalizationRoutesBindValidatedDtos() throws Exception {
        when(service.addCharge(eq("BK-1"), eq("charge-1"), isNull(), any(), isNull())).thenReturn(projection());
        when(service.finalizeBill(eq("BK-1"), eq("finalize-1"), isNull(), any(), isNull())).thenReturn(projection());

        mvc.perform(post("/api/v1/workshop-bookings/BK-1/final-bill/charges")
                        .header("Idempotency-Key", "charge-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Workshop materials\",\"amount\":5000,\"reason\":\"Used during class\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingCode").value("BK-1"));

        mvc.perform(post("/api/v1/workshop-bookings/BK-1/final-bill/finalizations")
                        .header("Idempotency-Key", "finalize-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingCode").value("BK-1"));

        verify(service).addCharge(eq("BK-1"), eq("charge-1"), isNull(), any(), isNull());
        verify(service).finalizeBill(eq("BK-1"), eq("finalize-1"), isNull(), any(), isNull());
    }

    @Test
    void rejectsLegacyManagerDebtOverrideField() throws Exception {
        mvc.perform(post("/api/v1/workshop-bookings/BK-1/final-bill/finalizations")
                        .header("Idempotency-Key", "finalize-manager-override")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"managerException\":true,\"reason\":\"No credit workflow\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    private static FinalBillProjectionDto projection() {
        return new FinalBillProjectionDto("BK-1", "OPEN", "VND", 0, 100_000, 0,
                List.of(new FinalBillProjectionDto.CustomerVisibleChargeDto("Workshop materials", 5_000)));
    }
}
