package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.BookingAttendanceFinalizationOutboxRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.TicketSessionEntitlementRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.BookingAttendanceFinalizationOutboxEvent;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.BookingAttendanceFinalizedEvent;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Instant;

@Service
public class BookingAttendanceFinalizationPublisher {
    static final String EVENT_TYPE = "BOOKING_ATTENDANCE_FINALIZED";
    private final TicketSessionEntitlementRepository entitlements;
    private final BookingAttendanceFinalizationOutboxRepository outbox;
    private final BookingAttendanceOutcomeService outcomes;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Autowired
    public BookingAttendanceFinalizationPublisher(
            TicketSessionEntitlementRepository entitlements,
            BookingAttendanceFinalizationOutboxRepository outbox,
            BookingAttendanceOutcomeService outcomes,
            ApplicationEventPublisher eventPublisher,
            ObjectProvider<Clock> clockProvider) {
        this(entitlements, outbox, outcomes, eventPublisher, clockProvider.getIfAvailable(Clock::systemUTC));
    }

    BookingAttendanceFinalizationPublisher(
            TicketSessionEntitlementRepository entitlements,
            BookingAttendanceFinalizationOutboxRepository outbox,
            BookingAttendanceOutcomeService outcomes,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.entitlements = entitlements;
        this.outbox = outbox;
        this.outcomes = outcomes;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${workshop.attendance-finalization.poll-delay-ms:60000}")
    @Transactional
    public void createDuePublications() {
        Instant now = clock.instant();
        for (String bookingReference : entitlements.findDistinctActiveBookingReferences()) {
            var evaluation = outcomes.evaluate(bookingReference, now);
            if (evaluation.outcome().terminal() && !outbox.existsByBookingReferenceAndEventType(bookingReference, EVENT_TYPE)) {
                outbox.save(BookingAttendanceFinalizationOutboxEvent.builder().bookingReference(bookingReference)
                        .eventType(EVENT_TYPE).finalizedAt(evaluation.finalizedAt()).createdAt(now).build());
            }
        }
    }

    @Scheduled(fixedDelayString = "${workshop.attendance-finalization.publish-delay-ms:1000}")
    @Transactional
    public void publishPending() {
        for (BookingAttendanceFinalizationOutboxEvent row : outbox.findTop50ByPublishedAtIsNullOrderByIdAsc()) {
            // Only the run that claims the row publishes it: overlapping scheduler runs or
            // several application instances cannot deliver the same booking twice.
            if (outbox.markPublished(row.getId(), clock.instant()) != 1) continue;
            // Publish inside the transaction that claims the row: Spring Modulith records the
            // publication with this commit and delivers it to @ApplicationModuleListener
            // consumers after commit. Publishing from afterCommit would bypass both.
            eventPublisher.publishEvent(new BookingAttendanceFinalizedEvent(
                    row.getBookingReference(), row.getFinalizedAt()));
        }
    }
}
