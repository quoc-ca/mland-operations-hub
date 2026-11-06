package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.controller.DepositPaymentController;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.DepositPaymentAttemptResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.DepositPaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.time.Instant;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class DepositPaymentControllerTest {
    @Mock DepositPaymentService service;
    private MockMvc mvc;

    @BeforeEach void setUp() { mvc = MockMvcBuilders.standaloneSetup(new DepositPaymentController(service)).build(); }

    @Test void exposesPaymentAttemptContract() throws Exception {
        when(service.initiate(eq("BK-1"), eq("idempotency-key-0001"), anyString()))
                .thenReturn(new DepositPaymentAttemptResponse("ATT-1", "DEP-BK-1-1", 50_000, "VND", "https://sandbox/payment", Instant.parse("2030-01-01T00:10:00Z"), "PENDING"));
        mvc.perform(post("/api/v1/workshop-bookings/BK-1/payment-attempts")
                        .header("Idempotency-Key", "idempotency-key-0001")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attemptReference").value("ATT-1"))
                .andExpect(jsonPath("$.merchantReference").value("DEP-BK-1-1"))
                .andExpect(jsonPath("$.amountVnd").value(50000))
                .andExpect(jsonPath("$.currency").value("VND"));
    }

    @Test void requiresIdempotencyKey() throws Exception {
        mvc.perform(post("/api/v1/workshop-bookings/BK-1/payment-attempts"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
