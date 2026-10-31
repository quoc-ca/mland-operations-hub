package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.controller.BookingMagicLinkExchangeController;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.BookingSummaryResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.MagicLinkService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BookingMagicLinkExchangeControllerTest {
    @Test
    void exchangesProtectedTokenAndReturnsOnlyBookingProjection() throws Exception {
        MagicLinkService service = mock(MagicLinkService.class);
        when(service.exchange("opaque-secret-token")).thenReturn(BookingSummaryResponse.builder()
                .bookingCode("BK-LINK-1").bookingStatus("CONFIRMED").paymentStatus("DEPOSIT_PAID")
                .participantCount(1).build());
        var mvc = MockMvcBuilders.standaloneSetup(new BookingMagicLinkExchangeController(service)).build();

        mvc.perform(post("/api/v1/booking-magic-links/exchanges")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"opaque-secret-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingCode").value("BK-LINK-1"))
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.contactEmail").doesNotExist());

        verify(service).exchange(eq("opaque-secret-token"));
    }
}
