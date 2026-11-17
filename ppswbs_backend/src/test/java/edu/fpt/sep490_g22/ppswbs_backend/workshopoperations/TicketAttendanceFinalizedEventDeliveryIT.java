package edu.fpt.sep490_g22.ppswbs_backend.workshopoperations;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.TicketAttendanceFinalizationOutboxRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.TicketAttendanceFinalizationOutboxEvent;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.TicketAttendanceFinalizedEvent;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.TicketAttendanceFinalizationPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 003.2 T092 / 003.3 T105: a durable attendance-finalization row reaches a real transaction-bound
 * consumer exactly once, after commit, and a replayed publish run delivers nothing new.
 */
@SpringBootTest
@ActiveProfiles("test")
class TicketAttendanceFinalizedEventDeliveryIT {

    @Autowired private TicketAttendanceFinalizationPublisher publisher;
    @Autowired private TicketAttendanceFinalizationOutboxRepository outbox;
    @Autowired private RecordingConsumer consumer;

    @Test
    void finalizedOutcomeIsDeliveredOnceAfterCommitAndReplayDeliversNothingNew() throws InterruptedException {
        String ticketReference = "TKT-T092-" + UUID.randomUUID();
        String eventId = "ev-it-" + UUID.randomUUID();
        Instant windowClosedAt = Instant.parse("2026-11-05T12:00:00Z").truncatedTo(ChronoUnit.MICROS);
        TicketAttendanceFinalizationOutboxEvent row = outbox.saveAndFlush(TicketAttendanceFinalizationOutboxEvent.builder()
                .eventId(eventId)
                .ticketReference(ticketReference)
                .eventType(TicketAttendanceFinalizationPublisher.EVENT_TYPE)
                .windowClosedAt(windowClosedAt)
                .createdAt(windowClosedAt)
                .build());

        publisher.publishPending();
        publisher.publishPending();
        waitForDelivery(ticketReference);

        assertThat(consumer.received(ticketReference))
                .containsExactly(new TicketAttendanceFinalizedEvent(eventId, ticketReference, windowClosedAt));
        assertThat(outbox.findById(row.getId()).orElseThrow().getPublishedAt()).isNotNull();

        // A later run (scheduler replay) finds no unpublished row and delivers nothing new.
        publisher.publishPending();
        Thread.sleep(300);
        assertThat(consumer.received(ticketReference)).hasSize(1);
    }

    private void waitForDelivery(String ticketReference) throws InterruptedException {
        for (int i = 0; i < 50 && consumer.received(ticketReference).isEmpty(); i++) Thread.sleep(100);
    }

    @TestConfiguration
    static class ConsumerConfiguration {
        @Bean RecordingConsumer recordingConsumer() { return new RecordingConsumer(); }
    }

    static class RecordingConsumer {
        private final List<TicketAttendanceFinalizedEvent> events = new CopyOnWriteArrayList<>();

        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        void on(TicketAttendanceFinalizedEvent event) { events.add(event); }

        List<TicketAttendanceFinalizedEvent> received(String ticketReference) {
            return events.stream().filter(event -> event.ticketReference().equals(ticketReference)).toList();
        }
    }
}

