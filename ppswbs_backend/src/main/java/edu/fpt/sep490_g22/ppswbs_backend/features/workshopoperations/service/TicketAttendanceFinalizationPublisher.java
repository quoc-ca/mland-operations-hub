package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.TicketAttendanceFinalizationOutboxRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.TicketSessionEntitlementRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.TicketAttendanceFinalizationOutboxEvent;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.TicketAttendanceFinalizedEvent;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class TicketAttendanceFinalizationPublisher {
    public static final String EVENT_TYPE = "TICKET_ATTENDANCE_FINALIZED";
    private final TicketSessionEntitlementRepository entitlements;
    private final TicketAttendanceFinalizationOutboxRepository outbox;
    private final BookingAttendanceOutcomeService outcomes;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Autowired
    public TicketAttendanceFinalizationPublisher(
            TicketSessionEntitlementRepository entitlements,
            TicketAttendanceFinalizationOutboxRepository outbox,
            BookingAttendanceOutcomeService outcomes,
            ApplicationEventPublisher eventPublisher,
            ObjectProvider<Clock> clockProvider) {
        this(entitlements, outbox, outcomes, eventPublisher, clockProvider.getIfAvailable(Clock::systemUTC));
    }

    TicketAttendanceFinalizationPublisher(
            TicketSessionEntitlementRepository entitlements,
            TicketAttendanceFinalizationOutboxRepository outbox,
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
        for (String ticketReference : entitlements.findDistinctActiveTicketReferences()) {
            var evaluation = outcomes.evaluateTicket(ticketReference, now);
            if (evaluation.outcome().terminal() && !outbox.existsByTicketReferenceAndEventType(ticketReference, EVENT_TYPE)) {
                String eventId = "ev-att-" + UUID.randomUUID();
                outbox.save(TicketAttendanceFinalizationOutboxEvent.builder()
                        .eventId(eventId)
                        .ticketReference(ticketReference)
                        .eventType(EVENT_TYPE)
                        .windowClosedAt(evaluation.finalizedAt())
                        .createdAt(now)
                        .build());
            }
        }
    }

    @Scheduled(fixedDelayString = "${workshop.attendance-finalization.publish-delay-ms:1000}")
    @Transactional
    public void publishPending() {
        for (TicketAttendanceFinalizationOutboxEvent row : outbox.findTop50ByPublishedAtIsNullOrderByIdAsc()) {
            // Only the run that claims the row publishes it: overlapping scheduler runs or
            // several application instances cannot deliver the same ticket twice.
            if (outbox.markPublished(row.getId(), clock.instant()) != 1) continue;
            // Publish inside the transaction that claims the row: Spring Modulith records the
            // publication with this commit and delivers it to @ApplicationModuleListener
            // consumers after commit.
            eventPublisher.publishEvent(new TicketAttendanceFinalizedEvent(
                    row.getEventId(), row.getTicketReference(), row.getWindowClosedAt()));
        }
    }
}

