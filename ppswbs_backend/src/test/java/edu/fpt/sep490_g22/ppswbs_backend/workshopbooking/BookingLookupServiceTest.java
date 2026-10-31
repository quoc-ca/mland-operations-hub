package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingSessionCommitmentRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.WorkshopBookingRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.BookingSummaryResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.GuestLookupRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingSessionCommitment;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.PaymentStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.WorkshopBooking;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.CustomerBookingAccessDecision;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.CustomerBookingAccessQuery;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopBookingFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopOperationsAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.BookingLookupService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.BookingNotFoundException;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.WorkshopBookingAuthorizationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.DeliverySafeTicket;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.DeliverySafeTicketSet;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.SaleEligibilitySnapshot;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.SessionEntitlementProjection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingLookupServiceTest {

    @Mock private WorkshopBookingRepository bookings;
    @Mock private BookingSessionCommitmentRepository commitments;
    @Mock private WorkshopBookingFacade bookingFacade;
    @Mock private WorkshopBookingAuthorizationService authorization;
    @Mock private WorkshopOperationsAdapter operations;
    @InjectMocks private BookingLookupService service;

    @Test
    void guestProofReturnsCurrentPendingScheduleAndNeverTicketsOrContactPii() throws Exception {
        UUID sessionRef = UUID.randomUUID();
        Instant start = Instant.parse("2026-11-15T09:00:00Z");
        WorkshopBooking booking = booking(BookingStatus.PENDING_PAYMENT, null);
        when(bookings.findByBookingCode(booking.getBookingCode())).thenReturn(Optional.of(booking));
        when(bookingFacade.verifyCustomerBookingAccess(any())).thenReturn(new CustomerBookingAccessDecision(true));
        when(commitments.findByBookingIdOrderByOrdinalAsc(booking.getId())).thenReturn(List.of(commitment(booking, sessionRef, 1)));
        when(operations.getSaleEligibility(sessionRef)).thenReturn(new SaleEligibilitySnapshot(sessionRef, false,
                "SALES_PAUSED", 31L, start, start.plusSeconds(5400), 0, null));

        GuestLookupRequest request = GuestLookupRequest.builder().bookingCode(booking.getBookingCode())
                .contactEmail(" GUEST@EXAMPLE.TEST ").build();
        BookingSummaryResponse response = service.lookupGuest(request);

        assertThat(response.getBookingStatus()).isEqualTo("PENDING_PAYMENT");
        assertThat(response.getPackageId()).isEqualTo(77L);
        assertThat(response.getPackageName()).isEqualTo("Workshop package");
        assertThat(response.getSessions()).singleElement().satisfies(session -> {
            assertThat(session.getSessionReference()).isEqualTo(sessionRef);
            assertThat(session.getLocationId()).isEqualTo(31L);
            assertThat(session.getScheduledStart()).isEqualTo(start);
        });
        assertThat(response.getTickets()).isEmpty();
        assertThat(new ObjectMapper().findAndRegisterModules().writeValueAsString(response))
                .doesNotContain("contactEmail", "contactPhone", "guest@example.test");
        ArgumentCaptor<CustomerBookingAccessQuery> query = ArgumentCaptor.forClass(CustomerBookingAccessQuery.class);
        verify(bookingFacade).verifyCustomerBookingAccess(query.capture());
        assertThat(query.getValue().contactEmail()).isEqualTo("GUEST@EXAMPLE.TEST");
        verify(operations, never()).getCurrentEntitlementProjection(any());
    }

    @Test
    void wrongGuestProofDisclosesNoBookingAndDoesNotCallOperations() {
        WorkshopBooking booking = booking(BookingStatus.PENDING_PAYMENT, null);
        when(bookings.findByBookingCode(booking.getBookingCode())).thenReturn(Optional.of(booking));
        when(bookingFacade.verifyCustomerBookingAccess(any())).thenReturn(new CustomerBookingAccessDecision(false));

        assertThatThrownBy(() -> service.lookupGuest(GuestLookupRequest.builder()
                .bookingCode(booking.getBookingCode()).contactPhone("wrong").build()))
                .isInstanceOf(BookingNotFoundException.class);
        verifyNoInteractions(operations, commitments);
    }

    @Test
    void memberLookupUsesCanonicalAuthenticatedAccountOwnership() {
        Long memberId = 92L;
        WorkshopBooking booking = booking(BookingStatus.PENDING_PAYMENT, memberId);
        UUID sessionRef = UUID.randomUUID();
        Instant start = Instant.parse("2026-11-16T09:00:00Z");
        when(authorization.requireMemberOwnership("Bearer member-token", null)).thenReturn(
                new AccountSummary(memberId, ActorRole.MEMBER, ActorStatus.ACTIVE, "member@example.test", null, null));
        when(bookings.findByBookingCode(booking.getBookingCode())).thenReturn(Optional.of(booking));
        when(bookingFacade.verifyCustomerBookingAccess(any())).thenReturn(new CustomerBookingAccessDecision(true));
        when(commitments.findByBookingIdOrderByOrdinalAsc(booking.getId())).thenReturn(List.of(commitment(booking, sessionRef, 1)));
        when(operations.getSaleEligibility(sessionRef)).thenReturn(new SaleEligibilitySnapshot(sessionRef, true,
                "PUBLISHED", 31L, start, start.plusSeconds(3600), 5, null));

        BookingSummaryResponse response = service.lookupMember("Bearer member-token", booking.getBookingCode());

        assertThat(response.getTickets()).isEmpty();
        ArgumentCaptor<CustomerBookingAccessQuery> query = ArgumentCaptor.forClass(CustomerBookingAccessQuery.class);
        verify(bookingFacade).verifyCustomerBookingAccess(query.capture());
        assertThat(query.getValue().memberAccountId()).isEqualTo(memberId);
        assertThat(query.getValue().contactEmail()).isNull();
        assertThat(query.getValue().contactPhone()).isNull();
    }

    @Test
    void confirmedLookupUsesCurrentOperationsScheduleAndTheIssuedQrReference() {
        WorkshopBooking booking = booking(BookingStatus.CONFIRMED, null);
        UUID firstSession = UUID.randomUUID();
        UUID movedSession = UUID.randomUUID();
        Instant oldDate = Instant.parse("2026-11-12T09:00:00Z");
        Instant currentDate = Instant.parse("2026-11-20T09:00:00Z");
        when(bookings.findByBookingCode(booking.getBookingCode())).thenReturn(Optional.of(booking));
        when(bookingFacade.verifyCustomerBookingAccess(any())).thenReturn(new CustomerBookingAccessDecision(true));
        when(operations.getCurrentEntitlementProjection(booking.getBookingCode())).thenReturn(new DeliverySafeTicketSet(List.of(
                new DeliverySafeTicket(UUID.randomUUID(), "QR-OPAQUE-ONE", "TK-ONE", List.of(
                        new SessionEntitlementProjection(firstSession, 31L, oldDate, oldDate.plusSeconds(3600)),
                        new SessionEntitlementProjection(movedSession, 31L, currentDate, currentDate.plusSeconds(3600)))))));

        BookingSummaryResponse response = service.lookupGuest(GuestLookupRequest.builder()
                .bookingCode(booking.getBookingCode()).contactPhone("0900000000").build());

        assertThat(response.getSessions()).extracting(session -> session.getSessionReference())
                .containsExactly(firstSession, movedSession);
        assertThat(response.getSessions().get(1).getScheduledStart()).isEqualTo(currentDate);
        assertThat(response.getTickets()).singleElement().satisfies(ticket -> {
            assertThat(ticket.getTicketCode()).isEqualTo("TK-ONE");
            assertThat(ticket.getQrReference()).isEqualTo("QR-OPAQUE-ONE");
            assertThat(ticket.getSessionReferences()).containsExactly(firstSession, movedSession);
        });
    }

    @Test
    void cancelledLookupShowsCurrentSessionFactsWithoutIssuingTickets() {
        WorkshopBooking booking = booking(BookingStatus.CANCELLED, null);
        UUID sessionRef = UUID.randomUUID();
        Instant start = Instant.parse("2026-11-22T09:00:00Z");
        when(bookings.findByBookingCode(booking.getBookingCode())).thenReturn(Optional.of(booking));
        when(bookingFacade.verifyCustomerBookingAccess(any())).thenReturn(new CustomerBookingAccessDecision(true));
        when(commitments.findByBookingIdOrderByOrdinalAsc(booking.getId()))
                .thenReturn(List.of(commitment(booking, sessionRef, 1)));
        when(operations.getSaleEligibility(sessionRef)).thenReturn(new SaleEligibilitySnapshot(sessionRef,
                false, "CLOSED", 31L, start, start.plusSeconds(3600), 0, null));

        BookingSummaryResponse response = service.lookupGuest(GuestLookupRequest.builder()
                .bookingCode(booking.getBookingCode()).contactPhone("0900000000").build());

        assertThat(response.getBookingStatus()).isEqualTo("CANCELLED");
        assertThat(response.getSessions()).hasSize(1);
        assertThat(response.getTickets()).isEmpty();
        verify(operations, never()).getCurrentEntitlementProjection(anyString());
    }

    private static WorkshopBooking booking(BookingStatus status, Long memberId) {
        return WorkshopBooking.builder().id(15L).bookingCode("BK-LOOKUP-001").packageId(77L)
                .packageNameSnapshot("Workshop package").contactEmail("guest@example.test")
                .contactPhone("0900000000").memberAccountId(memberId).participantCount(1)
                .bookingStatus(status).paymentStatus(status == BookingStatus.CONFIRMED
                        ? PaymentStatus.DEPOSIT_PAID : PaymentStatus.UNPAID)
                .holdExpiresAt(Instant.parse("2026-11-10T09:00:00Z")).build();
    }

    private static BookingSessionCommitment commitment(WorkshopBooking booking, UUID reference, int ordinal) {
        return BookingSessionCommitment.builder().booking(booking).sessionReference(reference.toString())
                .locationId(31L).ordinal(ordinal).seatCount(booking.getParticipantCount()).build();
    }
}
