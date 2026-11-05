package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.controller.BookingPendingHoldReleaseController;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.BookingPendingHoldReleaseService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.BookingRecoveryCommandResult;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BookingPendingHoldReleaseControllerTest {

    @Test
    void guestCanSendOneStoredContactProofAndRequiredReason() throws Exception {
        BookingPendingHoldReleaseService service = mock(BookingPendingHoldReleaseService.class);
        when(service.release(any(), anyString(), anyString(), anyString(), any()))
                .thenReturn(new BookingRecoveryCommandResult(204, ""));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new BookingPendingHoldReleaseController(service, new ObjectMapper())).build();

        mvc.perform(post("/api/v1/workshop-bookings/BK-1/pending-hold-release")
                        .header("Idempotency-Key", "pending-release-guest-001")
                        .accept(MediaType.APPLICATION_JSON).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"contactPhone":"0900000000","reason":"Plans changed"}
                                """))
                .andExpect(status().isNoContent());

        verify(service).release(isNull(), eq("BK-1"), eq("pending-release-guest-001"), anyString(),
                argThat(request -> "0900000000".equals(request.getContactPhone())
                        && "Plans changed".equals(request.getReason())));
    }

    @Test
    void memberSendsBearerIdentityAndReasonWithoutContactProof() throws Exception {
        BookingPendingHoldReleaseService service = mock(BookingPendingHoldReleaseService.class);
        when(service.release(anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(new BookingRecoveryCommandResult(204, ""));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new BookingPendingHoldReleaseController(service, new ObjectMapper())).build();

        mvc.perform(post("/api/v1/workshop-bookings/BK-2/pending-hold-release")
                        .header("Authorization", "Bearer member-token")
                        .header("Idempotency-Key", "pending-release-member-01")
                        .accept(MediaType.APPLICATION_JSON).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"No longer needed"}
                                """))
                .andExpect(status().isNoContent());

        verify(service).release(eq("Bearer member-token"), eq("BK-2"), eq("pending-release-member-01"),
                anyString(), argThat(request -> request.getContactEmail() == null
                        && request.getContactPhone() == null && "No longer needed".equals(request.getReason())));
    }
}
