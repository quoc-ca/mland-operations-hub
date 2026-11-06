package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.BookingAttendanceFinalizationOutboxRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.TicketSessionEntitlementRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.AttendanceState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.BookingAttendanceFinalizationOutboxEvent;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.SessionState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.TicketSessionEntitlement;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopSession;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.BookingAttendanceFinalizedEvent;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.BookingAttendanceOutcome;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BookingAttendanceFinalizationPublisherTest {
    private static final Instant NOW = Instant.parse("2026-11-05T12:00:00Z");
    private final TicketSessionEntitlementRepository entitlements = mock(TicketSessionEntitlementRepository.class);
    private final BookingAttendanceFinalizationOutboxRepository outbox = mock(BookingAttendanceFinalizationOutboxRepository.class);
    private final BookingAttendanceOutcomeService outcomes = mock(BookingAttendanceOutcomeService.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final BookingAttendanceFinalizationPublisher publisher = new BookingAttendanceFinalizationPublisher(
            entitlements, outbox, outcomes, events, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test void createsOneDurablePublicationAndReplayDoesNotCreateAnother() {
        when(entitlements.findDistinctActiveBookingReferences()).thenReturn(List.of("BK-1"));
        when(outcomes.evaluate("BK-1", NOW)).thenReturn(terminal(BookingAttendanceOutcome.Outcome.ATTENDED));
        when(outbox.existsByBookingReferenceAndEventType("BK-1", "BOOKING_ATTENDANCE_FINALIZED"))
                .thenReturn(false, true);

        publisher.createDuePublications();
        publisher.createDuePublications();

        verify(outbox, times(1)).save(argThat(row -> row.getBookingReference().equals("BK-1")
                && row.getFinalizedAt().equals(NOW)));
    }

    @Test void emitsOneReferenceOnlyEventAfterTheOutboxRowIsDue() {
        BookingAttendanceFinalizationOutboxEvent row = BookingAttendanceFinalizationOutboxEvent.builder()
                .bookingReference("BK-2").eventType("BOOKING_ATTENDANCE_FINALIZED")
                .id(2L).finalizedAt(NOW).createdAt(NOW).build();
        when(outbox.findTop50ByPublishedAtIsNullOrderByIdAsc()).thenReturn(List.of(row), List.of());
        when(outbox.markPublished(2L, NOW)).thenReturn(1);

        publisher.publishPending();
        publisher.publishPending();

        var captor = org.mockito.ArgumentCaptor.forClass(BookingAttendanceFinalizedEvent.class);
        verify(events, times(1)).publishEvent(captor.capture());
        assertThat(captor.getValue()).isEqualTo(new BookingAttendanceFinalizedEvent("BK-2", NOW));
        verify(outbox, times(1)).markPublished(2L, NOW);
        verifyNoMoreInteractions(events);
    }

    @Test void doesNotPublishARowThatAConcurrentRunAlreadyClaimed() {
        BookingAttendanceFinalizationOutboxEvent row = BookingAttendanceFinalizationOutboxEvent.builder()
                .bookingReference("BK-3").eventType("BOOKING_ATTENDANCE_FINALIZED")
                .id(3L).finalizedAt(NOW).createdAt(NOW).build();
        when(outbox.findTop50ByPublishedAtIsNullOrderByIdAsc()).thenReturn(List.of(row));
        when(outbox.markPublished(3L, NOW)).thenReturn(0);

        publisher.publishPending();

        verifyNoInteractions(events);
    }

    @Test void createsTheSinglePublicationOnlyOnceTheCorrectionWindowCloses() {
        Instant sessionFinalizedAt = Instant.parse("2026-11-04T12:00:00Z");
        Instant windowClosesAt = sessionFinalizedAt.plusSeconds(24 * 3600);
        TicketSessionEntitlementRepository repository = mock(TicketSessionEntitlementRepository.class);
        BookingAttendanceFinalizationOutboxRepository rows = mock(BookingAttendanceFinalizationOutboxRepository.class);
        BookingAttendanceOutcomeService realOutcomes = new BookingAttendanceOutcomeService(repository);
        when(repository.findDistinctActiveBookingReferences()).thenReturn(List.of("BK-W"));
        when(repository.findActiveByBookingReference("BK-W")).thenReturn(List.of(finalizedNoShow(sessionFinalizedAt)));
        when(rows.existsByBookingReferenceAndEventType("BK-W", "BOOKING_ATTENDANCE_FINALIZED"))
                .thenReturn(false, true);

        publisherAt(windowClosesAt.minusSeconds(1), repository, rows, realOutcomes).createDuePublications();
        verify(rows, never()).save(any());

        publisherAt(windowClosesAt, repository, rows, realOutcomes).createDuePublications();
        publisherAt(windowClosesAt.plusSeconds(3600), repository, rows, realOutcomes).createDuePublications();

        verify(rows, times(1)).save(argThat(row -> row.getBookingReference().equals("BK-W")
                && row.getFinalizedAt().equals(windowClosesAt)));
        verifyNoInteractions(events);
    }

    private BookingAttendanceFinalizationPublisher publisherAt(Instant now, TicketSessionEntitlementRepository repository,
            BookingAttendanceFinalizationOutboxRepository rows, BookingAttendanceOutcomeService realOutcomes) {
        return new BookingAttendanceFinalizationPublisher(repository, rows, realOutcomes, events,
                Clock.fixed(now, ZoneOffset.UTC));
    }

    private static TicketSessionEntitlement finalizedNoShow(Instant finalizedAt) {
        WorkshopSession session = new WorkshopSession();
        session.setState(SessionState.ATTENDANCE_FINALIZED);
        session.setAttendanceFinalizedAt(finalizedAt);
        TicketSessionEntitlement item = new TicketSessionEntitlement();
        item.setSession(session);
        item.setAttendanceState(AttendanceState.NO_SHOW);
        return item;
    }

    private BookingAttendanceOutcomeService.Evaluation terminal(BookingAttendanceOutcome.Outcome outcome) {
        return new BookingAttendanceOutcomeService.Evaluation(new BookingAttendanceOutcome(true, outcome), NOW);
    }
}
