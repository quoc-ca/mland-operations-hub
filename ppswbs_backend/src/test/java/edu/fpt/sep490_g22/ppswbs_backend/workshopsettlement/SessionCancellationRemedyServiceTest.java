package edu.fpt.sep490_g22.ppswbs_backend.workshopsettlement;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.TicketBookingRef;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.DeliverySafeTicket;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.DeliverySafeTicketSet;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.EquivalentReplacementSessions;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.SessionEntitlementProjection;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopBookingSettlementAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopOperationsSettlementAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.SessionCancellationRemedyService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.TicketMlandRemedyService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 003.3 T112 / FR-043 / US9: the cancellation handoff becomes one Mland remedy per affected ticket. */
class SessionCancellationRemedyServiceTest {

    private final WorkshopOperationsSettlementAdapter operations = mock(WorkshopOperationsSettlementAdapter.class);
    private final WorkshopBookingSettlementAdapter booking = mock(WorkshopBookingSettlementAdapter.class);
    private final TicketMlandRemedyService remedies = mock(TicketMlandRemedyService.class);
    private final edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.SettlementCustomerNotifier notifier =
            mock(edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.SettlementCustomerNotifier.class);
    private final SessionCancellationRemedyService service =
            new SessionCancellationRemedyService(operations, booking, remedies, notifier);

    private final UUID cancelled = UUID.randomUUID();
    private final UUID otherSession = UUID.randomUUID();

    private DeliverySafeTicket ticket(UUID reference, String code, UUID... sessions) {
        return new DeliverySafeTicket(reference, "qr-" + code, code, java.util.Arrays.stream(sessions)
                .map(session -> new SessionEntitlementProjection(session, 1L, Instant.now(), Instant.now())).toList());
    }

    private void line(String bookingLine, DeliverySafeTicket... tickets) {
        when(operations.ticketProjection(bookingLine)).thenReturn(new DeliverySafeTicketSet(List.of(tickets)));
        for (DeliverySafeTicket ticket : tickets) {
            when(booking.findTicketBookingByOperationsReference(ticket.ticketReference().toString()))
                    .thenReturn(Optional.of(new TicketBookingRef("CODE-" + ticket.ticketCode(), "GRP-1")));
        }
    }

    @Test
    @DisplayName("only tickets holding the cancelled session get a remedy; an offer lists every found session")
    void offersEveryEquivalentSessionToTicketsHoldingTheCancelledSession() {
        DeliverySafeTicket holds = ticket(UUID.randomUUID(), "T1", cancelled);
        DeliverySafeTicket holdsOther = ticket(UUID.randomUUID(), "T2", otherSession);
        DeliverySafeTicket twoSessions = ticket(UUID.randomUUID(), "T3", otherSession, cancelled);
        line("GRP-1-L1", holds, holdsOther, twoSessions);
        UUID found1 = UUID.randomUUID();
        UUID found2 = UUID.randomUUID();
        Instant start1 = Instant.now().plus(3, ChronoUnit.DAYS);
        Instant start2 = Instant.now().plus(5, ChronoUnit.DAYS);
        when(remedies.canOpenRemedy(anyString(), eq(cancelled.toString()))).thenReturn(true);
        when(operations.equivalentReplacementSessions(any(), eq(cancelled))).thenReturn(new EquivalentReplacementSessions(List.of(
                new EquivalentReplacementSessions.Candidate(found1, start1, start1.plus(2, ChronoUnit.HOURS)),
                new EquivalentReplacementSessions.Candidate(found2, start2, start2.plus(2, ChronoUnit.HOURS)))));

        int opened = service.openRemedies(cancelled.toString(), List.of("GRP-1-L1"), "CORR-1");

        assertThat(opened).isEqualTo(2);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TicketMlandRemedyService.ReplacementOption>> options = ArgumentCaptor.forClass(List.class);
        verify(remedies).createOffer(eq("CODE-T1"), eq(cancelled.toString()), options.capture(), eq("CORR-1"));
        verify(remedies).createOffer(eq("CODE-T3"), eq(cancelled.toString()), any(List.class), eq("CORR-1"));
        verify(remedies, never()).createOffer(eq("CODE-T2"), anyString(), any(List.class), anyString());
        assertThat(options.getValue()).extracting(TicketMlandRemedyService.ReplacementOption::sessionReference)
                .containsExactly(found1.toString(), found2.toString());
        verify(remedies, never()).openDirectRefund(anyString(), anyString(), anyString());
        verify(notifier).notifyMlandRemedyRecipientOf("CODE-T1", "CORR-1");
        verify(notifier).notifyMlandRemedyRecipientOf("CODE-T3", "CORR-1");
        verify(notifier, never()).notifyMlandRemedyRecipientOf(org.mockito.ArgumentMatchers.eq("CODE-T2"), anyString());
    }

    @Test
    @DisplayName("no equivalent session -> the ticket goes straight to the 100% bank refund")
    void noEquivalentSessionMeansDirectRefund() {
        DeliverySafeTicket holds = ticket(UUID.randomUUID(), "T1", cancelled);
        line("GRP-1-L1", holds);
        when(remedies.canOpenRemedy(anyString(), anyString())).thenReturn(true);
        when(operations.equivalentReplacementSessions(any(), eq(cancelled))).thenReturn(EquivalentReplacementSessions.none());

        assertThat(service.openRemedies(cancelled.toString(), List.of("GRP-1-L1"), "CORR-2")).isEqualTo(1);

        verify(remedies).openDirectRefund("CODE-T1", cancelled.toString(), "CORR-2");
        verify(remedies, never()).createOffer(anyString(), anyString(), any(List.class), anyString());
    }

    @Test
    @DisplayName("a ticket whose folio is no longer open is skipped, and a ticket shared by two lines is handled once")
    void skipsSettledTicketsAndDuplicates() {
        DeliverySafeTicket settled = ticket(UUID.randomUUID(), "T1", cancelled);
        DeliverySafeTicket open = ticket(UUID.randomUUID(), "T2", cancelled);
        line("GRP-1-L1", settled, open);
        when(operations.ticketProjection("GRP-1-L2")).thenReturn(new DeliverySafeTicketSet(List.of(open)));
        when(remedies.canOpenRemedy("CODE-T1", cancelled.toString())).thenReturn(false);
        when(remedies.canOpenRemedy("CODE-T2", cancelled.toString())).thenReturn(true);
        when(operations.equivalentReplacementSessions(any(), eq(cancelled))).thenReturn(EquivalentReplacementSessions.none());

        assertThat(service.openRemedies(cancelled.toString(), List.of("GRP-1-L1", "GRP-1-L2", "GRP-1-L1"), "CORR-3"))
                .isEqualTo(1);

        verify(remedies).openDirectRefund("CODE-T2", cancelled.toString(), "CORR-3");
        verify(remedies, never()).openDirectRefund(eq("CODE-T1"), anyString(), anyString());
    }

    @Test
    @DisplayName("a booking line that holds only a capacity hold (no ticket) opens nothing")
    void holdOnlyLineOpensNothing() {
        when(operations.ticketProjection("GRP-1-L9")).thenReturn(new DeliverySafeTicketSet(List.of()));

        assertThat(service.openRemedies(cancelled.toString(), List.of("GRP-1-L9"), "CORR-4")).isZero();
        verify(remedies, never()).openDirectRefund(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("a session reference that is not a UUID opens nothing")
    void invalidSessionReference() {
        assertThat(service.openRemedies("not-a-uuid", List.of("GRP-1-L1"), "CORR-5")).isZero();
    }
}
