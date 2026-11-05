package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.controller.BookingLookupController;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.BookingSummaryResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.GuestLookupRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.BookingLookupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BookingLookupControllerTest {

    private BookingLookupService lookupService;
    private MockMvc mvc;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        lookupService = mock(BookingLookupService.class);
        mapper = new ObjectMapper().findAndRegisterModules();
        mvc = MockMvcBuilders.standaloneSetup(new BookingLookupController(lookupService)).build();
    }

    @Test
    void guestLookupRequiresSingleProofAndReturnsAuthorizedProjection() throws Exception {
        BookingSummaryResponse response = BookingSummaryResponse.builder()
                .bookingCode("BK-LOOKUP-1").bookingStatus("PENDING_PAYMENT").paymentStatus("UNPAID")
                .participantCount(1).build();
        when(lookupService.lookupGuest(any(GuestLookupRequest.class))).thenReturn(response);

        mvc.perform(post("/api/v1/booking-lookups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bookingCode":"BK-LOOKUP-1","contactPhone":"0900000000"}
                                """))
                .andExpect(status().isOk())
                .andExpect(content().json(mapper.writeValueAsString(response)));

        verify(lookupService).lookupGuest(any(GuestLookupRequest.class));
    }

    @Test
    void guestLookupRejectsMissingOrMultipleProofsBeforeService() throws Exception {
        mvc.perform(post("/api/v1/booking-lookups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bookingCode":"BK-LOOKUP-1","contactPhone":"0900000000","contactEmail":"guest@example.test"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void memberLookupPassesBearerAndBookingCodeToOwnershipService() throws Exception {
        BookingSummaryResponse response = BookingSummaryResponse.builder()
                .bookingCode("BK-MEMBER-1").bookingStatus("CONFIRMED").paymentStatus("DEPOSIT_PAID")
                .participantCount(1).build();
        when(lookupService.lookupMember("Bearer signed-member-token", "BK-MEMBER-1")).thenReturn(response);

        mvc.perform(get("/api/v1/members/me/workshop-bookings/BK-MEMBER-1")
                        .header("Authorization", "Bearer signed-member-token")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().json(mapper.writeValueAsString(response)));

        verify(lookupService).lookupMember(eq("Bearer signed-member-token"), eq("BK-MEMBER-1"));
    }
}
