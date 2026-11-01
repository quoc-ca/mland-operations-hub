package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingSessionCommitmentRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.WorkshopBookingRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.BookedSessionDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.BookingSummaryResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.BookingTicketDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.GuestLookupRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingSessionCommitment;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.WorkshopBooking;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.CustomerBookingAccessDecision;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.CustomerBookingAccessQuery;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopBookingFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopOperationsAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.DeliverySafeTicket;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.DeliverySafeTicketSet;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.SaleEligibilitySnapshot;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.SessionEntitlementProjection;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/** Builds a privacy-safe customer projection only after Booking-owned proof succeeds. */
@Service
@RequiredArgsConstructor
public class BookingLookupService {

    private final WorkshopBookingRepository bookings;
    private final BookingSessionCommitmentRepository commitments;
    private final WorkshopBookingFacade bookingFacade;
    private final WorkshopBookingAuthorizationService authorization;
    private final WorkshopOperationsAdapter operations;

    @Transactional(readOnly = true)
    public BookingSummaryResponse lookupGuest(GuestLookupRequest request) {
        if (request == null || request.getBookingCode() == null || request.getBookingCode().isBlank()) {
            throw new BookingValidationException("A booking code and exactly one contact proof are required");
        }
        boolean hasEmail = request.getContactEmail() != null && !request.getContactEmail().isBlank();
        boolean hasPhone = request.getContactPhone() != null && !request.getContactPhone().isBlank();
        if (hasEmail == hasPhone) {
            throw new BookingValidationException("Exactly one contact proof must be provided");
        }

        WorkshopBooking booking = findBooking(request.getBookingCode());
        CustomerBookingAccessDecision decision = bookingFacade.verifyCustomerBookingAccess(
                new CustomerBookingAccessQuery(booking.getBookingCode(), null,
                        hasEmail ? request.getContactEmail().trim() : null,
                        hasPhone ? request.getContactPhone().trim() : null));
        if (decision == null || !decision.authorized()) throw new BookingNotFoundException("Booking not found");
        return project(booking);
    }

    @Transactional(readOnly = true)
    public BookingSummaryResponse lookupMember(String authorizationHeader, String bookingCode) {
        AccountSummary member = authorization.requireMemberOwnership(authorizationHeader, null);
        WorkshopBooking booking = findBooking(bookingCode);
        CustomerBookingAccessDecision decision = bookingFacade.verifyCustomerBookingAccess(
                new CustomerBookingAccessQuery(booking.getBookingCode(), member.accountId(), null, null));
        if (decision == null || !decision.authorized()) throw new BookingNotFoundException("Booking not found");
        return project(booking);
    }

    private WorkshopBooking findBooking(String bookingCode) {
        if (bookingCode == null || bookingCode.isBlank()) throw new BookingNotFoundException("Booking not found");
        return bookings.findByBookingCode(bookingCode.trim())
                .orElseThrow(() -> new BookingNotFoundException("Booking not found"));
    }

    public BookingSummaryResponse projectForMagicLink(WorkshopBooking booking) {
        return project(booking);
    }

    private BookingSummaryResponse project(WorkshopBooking booking) {
        List<BookedSessionDto> sessions;
        List<BookingTicketDto> tickets;
        if (booking.getBookingStatus() == BookingStatus.CONFIRMED) {
            DeliverySafeTicketSet current = operations.getCurrentEntitlementProjection(booking.getBookingCode());
            if (current == null || current.tickets() == null) {
                throw new IllegalStateException("Current Operations ticket projection is unavailable");
            }
            CurrentProjection projection = currentConfirmedProjection(current);
            sessions = projection.sessions();
            tickets = projection.tickets();
        } else {
            sessions = currentPendingSessions(booking);
            tickets = List.of();
        }
        return BookingSummaryResponse.builder()
                .bookingCode(booking.getBookingCode())
                .bookingStatus(booking.getBookingStatus().name())
                .paymentStatus(booking.getPaymentStatus().name())
                .packageId(booking.getPackageId())
                .packageName(booking.getPackageNameSnapshot())
                .participantCount(booking.getParticipantCount())
                .holdExpiresAt(booking.getHoldExpiresAt())
                .sessions(sessions)
                .tickets(tickets)
                .build();
    }

    private List<BookedSessionDto> currentPendingSessions(WorkshopBooking booking) {
        List<BookingSessionCommitment> stored = commitments.findByBookingIdOrderByOrdinalAsc(booking.getId());
        if (stored.isEmpty()) throw new IllegalStateException("Booking has no session commitments");
        List<CurrentSession> current = new ArrayList<>(stored.size());
        for (BookingSessionCommitment commitment : stored) {
            UUID reference = parseSessionReference(commitment.getSessionReference());
            SaleEligibilitySnapshot snapshot = operations.getSaleEligibility(reference);
            if (snapshot == null || !reference.equals(snapshot.sessionReference())
                    || snapshot.locationReference() == null || snapshot.scheduledStart() == null
                    || snapshot.scheduledEnd() == null || !snapshot.scheduledEnd().isAfter(snapshot.scheduledStart())) {
                throw new IllegalStateException("Current Operations schedule projection is incomplete");
            }
            current.add(new CurrentSession(reference, snapshot.locationReference(),
                    snapshot.scheduledStart(), snapshot.scheduledEnd()));
        }
        current.sort(CurrentSession.ORDER);
        return toSessionDtos(current);
    }

    private CurrentProjection currentConfirmedProjection(DeliverySafeTicketSet projection) {
        Map<UUID, CurrentSession> currentByReference = new HashMap<>();
        Set<String> ticketCodes = new HashSet<>();
        Set<String> qrReferences = new HashSet<>();
        List<BookingTicketDto> ticketDtos = new ArrayList<>();
        for (DeliverySafeTicket ticket : projection.tickets()) {
            if (ticket == null || ticket.ticketReference() == null || ticket.ticketCode() == null
                    || ticket.ticketCode().isBlank() || ticket.qrReference() == null || ticket.qrReference().isBlank()
                    || !ticketCodes.add(ticket.ticketCode()) || !qrReferences.add(ticket.qrReference())
                    || ticket.sessionEntitlements() == null) {
                throw new IllegalStateException("Current Operations ticket projection contains an invalid ticket");
            }
            List<SessionEntitlementProjection> entitlements = ticket.sessionEntitlements();
            List<UUID> ticketSessionReferences = new ArrayList<>();
            for (SessionEntitlementProjection entitlement : entitlements) {
                if (entitlement == null || entitlement.sessionReference() == null
                        || entitlement.locationReference() == null || entitlement.scheduledStart() == null
                        || entitlement.scheduledEnd() == null
                        || !entitlement.scheduledEnd().isAfter(entitlement.scheduledStart())) {
                    throw new IllegalStateException("Current Operations session projection is incomplete");
                }
                CurrentSession current = new CurrentSession(entitlement.sessionReference(),
                        entitlement.locationReference(), entitlement.scheduledStart(), entitlement.scheduledEnd());
                CurrentSession prior = currentByReference.putIfAbsent(current.reference(), current);
                if (prior != null && !prior.equals(current)) {
                    throw new IllegalStateException("Operations returned conflicting current session facts");
                }
                ticketSessionReferences.add(current.reference());
            }
            ticketSessionReferences.sort(Comparator.comparing(ref -> currentByReference.get(ref), CurrentSession.ORDER));
            ticketDtos.add(BookingTicketDto.builder().ticketCode(ticket.ticketCode())
                    .qrReference(ticket.qrReference()).sessionReferences(List.copyOf(ticketSessionReferences)).build());
        }
        List<CurrentSession> current = currentByReference.values().stream().sorted(CurrentSession.ORDER).toList();
        ticketDtos.sort(Comparator.comparing(BookingTicketDto::getTicketCode));
        return new CurrentProjection(toSessionDtos(current), List.copyOf(ticketDtos));
    }

    private static List<BookedSessionDto> toSessionDtos(List<CurrentSession> sessions) {
        List<BookedSessionDto> result = new ArrayList<>(sessions.size());
        for (int index = 0; index < sessions.size(); index++) {
            CurrentSession session = sessions.get(index);
            result.add(new BookedSessionDto(session.reference(), session.locationReference(), index + 1,
                    session.scheduledStart(), session.scheduledEnd()));
        }
        return List.copyOf(result);
    }

    private static UUID parseSessionReference(String value) {
        try {
            UUID parsed = UUID.fromString(value);
            if (!parsed.toString().equals(value)) throw new IllegalArgumentException();
            return parsed;
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Stored session commitment is not a canonical UUID", exception);
        }
    }

    private record CurrentSession(UUID reference, Long locationReference, Instant scheduledStart, Instant scheduledEnd) {
        private static final Comparator<CurrentSession> ORDER = Comparator.comparing(CurrentSession::scheduledStart)
                .thenComparing(session -> session.reference().toString());
    }

    private record CurrentProjection(List<BookedSessionDto> sessions, List<BookingTicketDto> tickets) {}
}
