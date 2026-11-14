package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.TicketAttendanceFinalizationOutboxRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.TicketSessionEntitlementRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.AttendanceState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.SessionState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.TicketAttendanceFinalizationOutboxEvent;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.TicketSessionEntitlement;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopSession;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.TicketAttendanceFinalizedEvent;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.TicketAttendanceOutcome;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TicketAttendanceFinalizationPublisherTest {
    private static final Instant NOW = Instant.parse("2026-11-05T12:00:00Z");
    private final TicketSessionEntitlementRepository entitlements = mock(TicketSessionEntitlementRepository.class);
    private final TicketAttendanceFinalizationOutboxRepository outbox = mock(TicketAttendanceFinalizationOutboxRepository.class);
    private final BookingAttendanceOutcomeService outcomes = mock(BookingAttendanceOutcomeService.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final TicketAttendanceFinalizationPublisher publisher = new TicketAttendanceFinalizationPublisher(
            entitlements, outbox, outcomes, events, Clock.fixed(NOW, ZoneOffset.UTC));

    /** Evidence 2: Exactly once per ticket key. Replay does not create another outbox row. */
    @Test
    void createsOneDurablePublicationAndReplayDoesNotCreateAnother() {
        when(entitlements.findDistinctActiveTicketReferences()).thenReturn(List.of("TKT-OPS-1"));
        when(outcomes.evaluateTicket("TKT-OPS-1", NOW)).thenReturn(terminal(TicketAttendanceOutcome.Outcome.ATTENDED));
        when(outbox.existsByTicketReferenceAndEventType("TKT-OPS-1", "TICKET_ATTENDANCE_FINALIZED"))
                .thenReturn(false, true);

        publisher.createDuePublications();
        publisher.createDuePublications();

        verify(outbox, times(1)).save(argThat(row -> row.getTicketReference().equals("TKT-OPS-1")
                && row.getWindowClosedAt().equals(NOW)
                && row.getEventId() != null
                && row.getEventId().startsWith("ev-att-")));
    }

    /** Evidence 3: Emits one reference-only event after the outbox row is due, and replay publishes nothing new. */
    @Test
    void emitsOneReferenceOnlyEventAfterTheOutboxRowIsDueAndReplayPublishesNothing() {
        TicketAttendanceFinalizationOutboxEvent row = TicketAttendanceFinalizationOutboxEvent.builder()
                .eventId("ev-att-123")
                .ticketReference("TKT-OPS-2")
                .eventType("TICKET_ATTENDANCE_FINALIZED")
                .id(2L)
                .windowClosedAt(NOW)
                .createdAt(NOW)
                .build();
        when(outbox.findTop50ByPublishedAtIsNullOrderByIdAsc()).thenReturn(List.of(row), List.of());
        when(outbox.markPublished(2L, NOW)).thenReturn(1);

        publisher.publishPending();
        // Replay: finds nothing unpublished
        publisher.publishPending();

        var captor = org.mockito.ArgumentCaptor.forClass(TicketAttendanceFinalizedEvent.class);
        verify(events, times(1)).publishEvent(captor.capture());
        assertThat(captor.getValue()).isEqualTo(new TicketAttendanceFinalizedEvent("ev-att-123", "TKT-OPS-2", NOW));
        verify(outbox, times(1)).markPublished(2L, NOW);
        verifyNoMoreInteractions(events);
    }

    /** Evidence 4: Overlapping scheduler claim delivers once (does not publish a row that concurrent run claimed). */
    @Test
    void doesNotPublishARowThatAConcurrentRunAlreadyClaimed() {
        TicketAttendanceFinalizationOutboxEvent row = TicketAttendanceFinalizationOutboxEvent.builder()
                .eventId("ev-att-456")
                .ticketReference("TKT-OPS-3")
                .eventType("TICKET_ATTENDANCE_FINALIZED")
                .id(3L)
                .windowClosedAt(NOW)
                .createdAt(NOW)
                .build();
        when(outbox.findTop50ByPublishedAtIsNullOrderByIdAsc()).thenReturn(List.of(row));
        when(outbox.markPublished(3L, NOW)).thenReturn(0);

        publisher.publishPending();

        verifyNoInteractions(events);
    }

    /** Evidence 1: Publication at and after the window edge and none before it. */
    @Test
    void createsTheSinglePublicationAtAndAfterTheWindowEdgeAndNoneBeforeIt() {
        Instant sessionFinalizedAt = Instant.parse("2026-11-04T12:00:00Z");
        Instant windowClosesAt = sessionFinalizedAt.plusSeconds(24 * 3600); // 2026-11-05T12:00:00Z
        TicketSessionEntitlementRepository repository = mock(TicketSessionEntitlementRepository.class);
        TicketAttendanceFinalizationOutboxRepository rows = mock(TicketAttendanceFinalizationOutboxRepository.class);
        BookingAttendanceOutcomeService realOutcomes = new BookingAttendanceOutcomeService(repository);
        when(repository.findDistinctActiveTicketReferences()).thenReturn(List.of("TKT-OPS-W"));
        when(repository.findActiveByTicketReference("TKT-OPS-W")).thenReturn(List.of(finalizedNoShow(sessionFinalizedAt)));
        when(rows.existsByTicketReferenceAndEventType("TKT-OPS-W", "TICKET_ATTENDANCE_FINALIZED"))
                .thenReturn(false, true);

        // 1. Before the edge: none
        publisherAt(windowClosesAt.minusSeconds(1), repository, rows, realOutcomes).createDuePublications();
        verify(rows, never()).save(any());

        // 2. Exactly at the edge: published
        publisherAt(windowClosesAt, repository, rows, realOutcomes).createDuePublications();
        // 3. After the edge: replay does not duplicate
        publisherAt(windowClosesAt.plusSeconds(3600), repository, rows, realOutcomes).createDuePublications();

        verify(rows, times(1)).save(argThat(row -> row.getTicketReference().equals("TKT-OPS-W")
                && row.getWindowClosedAt().equals(windowClosesAt)
                && row.getEventId() != null));
        verifyNoInteractions(events);
    }

    private TicketAttendanceFinalizationPublisher publisherAt(Instant now, TicketSessionEntitlementRepository repository,
            TicketAttendanceFinalizationOutboxRepository rows, BookingAttendanceOutcomeService realOutcomes) {
        return new TicketAttendanceFinalizationPublisher(repository, rows, realOutcomes, events,
                Clock.fixed(now, ZoneOffset.UTC));
    }

    private static BookingAttendanceOutcomeService.Evaluation terminal(TicketAttendanceOutcome.Outcome outcome) {
        return new BookingAttendanceOutcomeService.Evaluation(new TicketAttendanceOutcome(true, outcome, NOW), NOW);
    }

    private static TicketSessionEntitlement finalizedNoShow(Instant sessionFinalizedAt) {
        WorkshopSession session = new WorkshopSession();
        session.setState(SessionState.ATTENDANCE_FINALIZED);
        session.setAttendanceFinalizedAt(sessionFinalizedAt);
        TicketSessionEntitlement item = new TicketSessionEntitlement();
        item.setSession(session);
        item.setAttendanceState(AttendanceState.NO_SHOW);
        return item;
    }
}

