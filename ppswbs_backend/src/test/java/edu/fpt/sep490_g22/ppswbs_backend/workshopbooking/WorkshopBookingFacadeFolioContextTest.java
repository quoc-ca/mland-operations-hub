package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingGroupLineRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.TicketBookingRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingGroup;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingGroupLine;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingGroupLineSession;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.TicketBooking;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.TicketFolioContext;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopBookingFacadeImpl;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopOperationsAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.WorkshopBookingProblemException;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.DeliverySafeTicket;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.DeliverySafeTicketSet;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.SessionEntitlementProjection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 003.1 T154 / FR-053: the location, sessions and first start of a ticket come from its CURRENT Operations sessions,
 * the line copy being only the booking-time answer before an ACTIVE Operations ticket exists (Q1, Q2) and never a
 * fallback when Operations fails (Q3).
 */
@ExtendWith(MockitoExtension.class)
class WorkshopBookingFacadeFolioContextTest {

    private static final String LINE_REF = "LINE-REF-1";
    private static final Instant START_1 = Instant.parse("2026-12-01T02:00:00Z");
    private static final Instant START_2 = Instant.parse("2026-12-02T02:00:00Z");

    @Mock
    private TicketBookingRepository ticketBookingRepository;
    @Mock
    private BookingGroupLineRepository bookingGroupLineRepository;
    @Mock
    private WorkshopOperationsAdapter operationsAdapter;
    @InjectMocks
    private WorkshopBookingFacadeImpl facade;

    private TicketBooking ticket(String operationsReference) {
        BookingGroup group = BookingGroup.builder().id(10L).groupCode("GRP-1").build();
        TicketBooking ticket = TicketBooking.builder().id(100L).ticketCode("TK-1").group(group).lineIndex(0)
                .packageId(5L).operationsTicketReference(operationsReference).build();
        BookingGroupLine line = BookingGroupLine.builder().id(20L).lineIndex(0).lineReference(LINE_REF).packageId(5L)
                .sessions(List.of(
                        BookingGroupLineSession.builder().ordinal(1).sessionReference("COPY-1").locationReference(1L)
                                .scheduledStart(START_1).build(),
                        BookingGroupLineSession.builder().ordinal(2).sessionReference("COPY-2").locationReference(1L)
                                .scheduledStart(START_2).build()))
                .build();
        when(ticketBookingRepository.findByTicketCode("TK-1")).thenReturn(Optional.of(ticket));
        when(bookingGroupLineRepository.findByGroupIdOrderByLineIndexAsc(10L)).thenReturn(List.of(line));
        return ticket;
    }

    private static SessionEntitlementProjection entitlement(UUID session, long location, Instant start) {
        return new SessionEntitlementProjection(session, location, start, start.plusSeconds(3600));
    }

    @Test
    @DisplayName("Current Operations location and sessions win over the line copy, ordered by start")
    void currentOperationsSessionsWin() {
        UUID mine = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        UUID s1 = UUID.randomUUID();
        UUID s2 = UUID.randomUUID();
        ticket(mine.toString());
        // Another ticket of the same line stays where it is; only "mine" has moved to location 7 (listed unordered).
        when(operationsAdapter.getBookingTicketProjection(LINE_REF)).thenReturn(new DeliverySafeTicketSet(List.of(
                new DeliverySafeTicket(other, "QR-O", "TK-O", List.of(entitlement(UUID.randomUUID(), 1L, START_1))),
                new DeliverySafeTicket(mine, "QR-M", "TK-1", List.of(
                        entitlement(s2, 7L, START_2), entitlement(s1, 7L, START_1))))));

        TicketFolioContext context = facade.getTicketFolioContext("TK-1").orElseThrow();

        assertThat(context.locationReference()).isEqualTo(7L);
        assertThat(context.sessionReferences()).containsExactly(s1.toString(), s2.toString());
        assertThat(context.firstSessionStart()).isEqualTo(START_1);
        assertThat(context.operationsTicketReference()).isEqualTo(mine.toString());
    }

    @Test
    @DisplayName("Another ticket of the line that did not move reads its own location, not the moved one")
    void unmovedTicketReadsItsOwnLocation() {
        UUID mine = UUID.randomUUID();
        UUID session = UUID.randomUUID();
        ticket(mine.toString());
        when(operationsAdapter.getBookingTicketProjection(LINE_REF)).thenReturn(new DeliverySafeTicketSet(List.of(
                new DeliverySafeTicket(mine, "QR-M", "TK-1", List.of(entitlement(session, 1L, START_1))),
                new DeliverySafeTicket(UUID.randomUUID(), "QR-O", "TK-O",
                        List.of(entitlement(UUID.randomUUID(), 7L, START_1))))));

        assertThat(facade.getTicketFolioContext("TK-1").orElseThrow().locationReference()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Q1: no Operations ticket yet uses the line copy and never calls Operations")
    void noOperationsTicketUsesCopy() {
        ticket(null);

        TicketFolioContext context = facade.getTicketFolioContext("TK-1").orElseThrow();

        assertThat(context.locationReference()).isEqualTo(1L);
        assertThat(context.sessionReferences()).containsExactly("COPY-1", "COPY-2");
        assertThat(context.firstSessionStart()).isEqualTo(START_1);
        verifyNoInteractions(operationsAdapter);
    }

    @Test
    @DisplayName("Q2: a ticket missing from the ACTIVE projection (cancelled, revoked) uses the line copy")
    void ticketNotActiveUsesCopy() {
        ticket(UUID.randomUUID().toString());
        when(operationsAdapter.getBookingTicketProjection(LINE_REF)).thenReturn(new DeliverySafeTicketSet(List.of()));

        TicketFolioContext context = facade.getTicketFolioContext("TK-1").orElseThrow();

        assertThat(context.locationReference()).isEqualTo(1L);
        assertThat(context.sessionReferences()).containsExactly("COPY-1", "COPY-2");
    }

    @Test
    @DisplayName("Q3: Operations unavailable fails closed with 503 DEPENDENCY_UNAVAILABLE, no copy fallback")
    void operationsUnavailableFailsClosed() {
        ticket(UUID.randomUUID().toString());
        when(operationsAdapter.getBookingTicketProjection(LINE_REF)).thenThrow(new QueryTimeoutException("down"));

        assertThatThrownBy(() -> facade.getTicketFolioContext("TK-1"))
                .isInstanceOfSatisfying(WorkshopBookingProblemException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(ex.getCode()).isEqualTo("DEPENDENCY_UNAVAILABLE");
                });
    }
}
