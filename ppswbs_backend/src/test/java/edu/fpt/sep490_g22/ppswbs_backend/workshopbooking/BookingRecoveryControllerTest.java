package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.controller.BookingRecoveryController;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.BookingRecoveryCommandResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.BookingRecoveryService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.MediaType;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class BookingRecoveryControllerTest {

    @Test
    void exposesConfirmationDeliveryRouteAndReturnsSafeAcceptedOutcome() throws Exception {
        BookingRecoveryService service = mock(BookingRecoveryService.class);
        when(service.requestConfirmationDelivery(any(), anyString(), anyString(), anyString(), any()))
                .thenReturn(new BookingRecoveryCommandResult(202,
                        "{\"bookingCode\":\"BK-REC-13\",\"action\":\"RESEND_CONFIRMATION\",\"outcome\":\"ACCEPTED\",\"correlationId\":\"corr-2\"}"));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new BookingRecoveryController(service,
                new ObjectMapper())).build();

        mvc.perform(post("/api/v1/workshop-bookings/BK-REC-13/confirmation-deliveries")
                        .header("Idempotency-Key", "confirmation-resend-0001")
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contactPhone\":\"0900000000\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.outcome").value("ACCEPTED"))
                .andExpect(jsonPath("$.bookingAccessLink").doesNotExist())
                .andExpect(jsonPath("$.contactPhone").doesNotExist());

        verify(service).requestConfirmationDelivery(isNull(), eq("BK-REC-13"),
                eq("confirmation-resend-0001"), anyString(),
                argThat(request -> "0900000000".equals(request.getContactPhone())));
    }

    @Test
    void exposesLatePaymentFollowUpRouteAndDoesNotReturnPaymentPayload() throws Exception {
        BookingRecoveryService service = mock(BookingRecoveryService.class);
        when(service.handleLatePaymentFollowup(anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(new BookingRecoveryCommandResult(200,
                        "{\"bookingCode\":\"BK-LATE-1\",\"action\":\"RECORD\",\"outcome\":\"RECORDED\",\"correlationId\":\"corr-3\"}"));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new BookingRecoveryController(service,
                new ObjectMapper())).build();

        mvc.perform(post("/api/v1/workshop-bookings/BK-LATE-1/late-payment-follow-ups")
                        .header("Authorization", "Bearer manager-token")
                        .header("Idempotency-Key", "late-payment-followup-01")
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"RECORD\",\"reason\":\"Provider confirmed a late deposit\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("RECORDED"))
                .andExpect(jsonPath("$.secureHash").doesNotExist());

        verify(service).handleLatePaymentFollowup(eq("Bearer manager-token"), eq("BK-LATE-1"),
                eq("late-payment-followup-01"), anyString(),
                argThat(request -> "RECORD".equals(request.getAction())));
    }

    @Test
    void exposesManagerMagicLinkIncidentCommandAndReturnsOnlySafeOutcome() throws Exception {
        BookingRecoveryService service = mock(BookingRecoveryService.class);
        ObjectMapper mapper = new ObjectMapper();
        when(service.handleMagicLinkIncident(anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(new BookingRecoveryCommandResult(200,
                        "{\"bookingCode\":\"BK-REC-12\",\"action\":\"REVOKE\",\"outcome\":\"REVOKED\",\"correlationId\":\"corr-1\"}"));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new BookingRecoveryController(service, mapper)).build();

        mvc.perform(post("/api/v1/workshop-bookings/BK-REC-12/magic-link-incidents")
                        .header("Authorization", "Bearer manager-token")
                        .header("Idempotency-Key", "magic-link-incident-0001")
                        .header("X-Correlation-Id", "corr-1")
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType("application/json")
                        .content("{\"action\":\"REVOKE\",\"reason\":\"suspected exposure\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingCode").value("BK-REC-12"))
                .andExpect(jsonPath("$.action").value("REVOKE"))
                .andExpect(jsonPath("$.outcome").value("REVOKED"))
                .andExpect(jsonPath("$.bookingAccessLink").doesNotExist())
                .andExpect(jsonPath("$.contactEmail").doesNotExist());

        verify(service).handleMagicLinkIncident(eq("Bearer manager-token"), eq("BK-REC-12"),
                eq("magic-link-incident-0001"), eq("corr-1"),
                argThat(request -> "REVOKE".equals(request.getAction())));
    }
}
