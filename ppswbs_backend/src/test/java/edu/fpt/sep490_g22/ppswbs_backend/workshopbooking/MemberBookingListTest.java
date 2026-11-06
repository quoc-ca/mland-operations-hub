package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.controller.BookingLookupController;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingSessionCommitmentRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.WorkshopBookingRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.BookingSummaryResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.WorkshopBooking;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopBookingFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopOperationsAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.BookingLookupService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.WorkshopBookingAuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A Member sees their own bookings (and tickets) without typing a booking code. */
class MemberBookingListTest {

    private static final String BEARER = "Bearer member-token";

    private WorkshopBookingRepository bookings;
    private BookingSessionCommitmentRepository commitments;
    private WorkshopBookingAuthorizationService authorization;
    private BookingLookupService service;

    @BeforeEach
    void setUp() {
        bookings = mock(WorkshopBookingRepository.class);
        commitments = mock(BookingSessionCommitmentRepository.class);
        authorization = mock(WorkshopBookingAuthorizationService.class);
        service = new BookingLookupService(bookings, commitments, mock(WorkshopBookingFacade.class), authorization,
                mock(WorkshopOperationsAdapter.class));
        when(authorization.requireMemberOwnership(BEARER, null))
                .thenReturn(new AccountSummary(42L, null, null, "member@example.com", Instant.now(), Instant.now()));
    }

    private static WorkshopBooking booking(long id, String code, BookingStatus status, Instant createdAt) {
        return WorkshopBooking.builder().id(id).bookingCode(code).bookingStatus(status).memberAccountId(42L)
                .packageId(145L).packageNameSnapshot("Silver").participantCount(1).createdAt(createdAt).build();
    }

    @Test
    void onlyTheSignedInMembersBookingsAreRequested() {
        when(bookings.findByMemberAccountId(42L)).thenReturn(List.of());

        assertThat(service.listMemberBookings(BEARER)).isEmpty();

        verify(bookings).findByMemberAccountId(42L);
        verify(bookings, never()).findByMemberAccountId(org.mockito.ArgumentMatchers.longThat(id -> id != 42L));
    }

    @Test
    void anExpiredUnpaidBookingIsLeftOut() {
        when(bookings.findByMemberAccountId(42L)).thenReturn(List.of(
                booking(1, "BK-EXPIRED", BookingStatus.EXPIRED, Instant.now())));

        assertThat(service.listMemberBookings(BEARER)).isEmpty();
    }

    @Test
    void oneBookingThatCannotBeProjectedDoesNotFailTheWholeList() {
        // No stored session commitments: the pending projection cannot be built, so this booking is skipped.
        when(commitments.findByBookingIdOrderByOrdinalAsc(anyLong())).thenReturn(List.of());
        when(bookings.findByMemberAccountId(42L)).thenReturn(List.of(
                booking(1, "BK-BROKEN", BookingStatus.PENDING_PAYMENT, Instant.now())));

        assertThat(service.listMemberBookings(BEARER)).isEmpty();
    }

    @Test
    void theListEndpointPassesTheBearerAndReturnsTheBookings() throws Exception {
        BookingLookupService lookup = mock(BookingLookupService.class);
        BookingSummaryResponse first = BookingSummaryResponse.builder().bookingCode("BK-1").bookingStatus("CONFIRMED")
                .paymentStatus("DEPOSIT_PAID").participantCount(1).build();
        BookingSummaryResponse second = BookingSummaryResponse.builder().bookingCode("BK-2").bookingStatus("PENDING_PAYMENT")
                .paymentStatus("UNPAID").participantCount(2).build();
        when(lookup.listMemberBookings(BEARER)).thenReturn(List.of(first, second));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new BookingLookupController(lookup)).build();

        mvc.perform(get("/api/v1/members/me/workshop-bookings").header("Authorization", BEARER).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().json(new ObjectMapper().findAndRegisterModules().writeValueAsString(List.of(first, second))));

        verify(lookup).listMemberBookings(BEARER);
    }
}
