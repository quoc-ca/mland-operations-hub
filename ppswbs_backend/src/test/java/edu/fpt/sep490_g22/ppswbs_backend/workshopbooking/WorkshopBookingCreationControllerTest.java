package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.BookingSummaryResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.BookingCreationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.BookingIdempotencyClaim;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.BookingIdempotencyService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.MemberQuoteBookingAcceptanceService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.controller.WorkshopBookingCreationController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class WorkshopBookingCreationControllerTest {
    private BookingCreationService creation;
    private BookingIdempotencyService idempotency;
    private MemberQuoteBookingAcceptanceService memberQuoteAcceptance;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        creation = mock(BookingCreationService.class);
        idempotency = mock(BookingIdempotencyService.class);
        memberQuoteAcceptance = mock(MemberQuoteBookingAcceptanceService.class);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        mvc = MockMvcBuilders.standaloneSetup(new WorkshopBookingCreationController(
                creation, idempotency, mapper, memberQuoteAcceptance))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper)).build();
    }

    @Test
    void postsGuestBookingAndReturns201WithIdempotentResult() throws Exception {
        BookingSummaryResponse response = BookingSummaryResponse.builder().bookingCode("BK-TEST")
                .bookingStatus("PENDING_PAYMENT").paymentStatus("UNPAID").participantCount(1)
                .holdExpiresAt(Instant.parse("2026-10-31T03:30:00Z")).sessions(List.of()).tickets(List.of()).build();
        when(idempotency.acquireClaim(eq("guest-create-1"), eq("CREATE_GUEST_BOOKING"), anyString(), anyString()))
                .thenReturn(BookingIdempotencyClaim.live(1L, 1L));
        when(creation.createGuest(any(), anyString(), any())).thenReturn(response);

        mvc.perform(post("/api/v1/workshop-bookings")
                        .header("Idempotency-Key", "guest-create-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"packageId":7,"sessionReferences":["93eaef9a-3396-47b5-a18d-b97a43a08625"],"participantCount":1,"contactEmail":"guest@example.com","contactPhone":"0901234567"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bookingCode").value("BK-TEST"))
                .andExpect(jsonPath("$.bookingStatus").value("PENDING_PAYMENT"));
        verify(creation).createGuest(any(), anyString(), any());
    }

    @Test
    void acceptsMemberQuoteOnlyWithAuthenticatedMemberAndIdempotency() throws Exception {
        BookingSummaryResponse response = BookingSummaryResponse.builder().bookingCode("BK-MEMBER")
                .bookingStatus("PENDING_PAYMENT").paymentStatus("UNPAID").participantCount(1)
                .holdExpiresAt(Instant.parse("2026-10-31T03:30:00Z")).sessions(List.of()).tickets(List.of()).build();
        when(memberQuoteAcceptance.requireMemberAccountId("Bearer member-token", "93eaef9a-3396-47b5-a18d-b97a43a08625")).thenReturn(321L);
        when(idempotency.acquireClaim(eq("member-quote-0001"), eq("CREATE_MEMBER_QUOTE_BOOKING"), anyString(), anyString()))
                .thenReturn(BookingIdempotencyClaim.live(2L, 1L));
        when(memberQuoteAcceptance.accept(any(), eq(321L), anyString(), any())).thenReturn(response);

        mvc.perform(post("/api/v1/workshop-bookings")
                        .header("Authorization", "Bearer member-token")
                        .header("Idempotency-Key", "member-quote-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quoteRef":"93eaef9a-3396-47b5-a18d-b97a43a08625","contactEmail":"member@example.com","contactPhone":"0901234567"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bookingCode").value("BK-MEMBER"));
        verify(memberQuoteAcceptance).requireMemberAccountId("Bearer member-token", "93eaef9a-3396-47b5-a18d-b97a43a08625");
        verify(memberQuoteAcceptance).accept(any(), eq(321L), anyString(), any());
        verify(creation, never()).createGuest(any(), anyString(), any());
    }
}
