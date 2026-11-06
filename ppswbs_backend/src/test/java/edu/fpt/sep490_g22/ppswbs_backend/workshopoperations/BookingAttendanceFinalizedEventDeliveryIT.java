package edu.fpt.sep490_g22.ppswbs_backend.workshopoperations;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.BookingAttendanceFinalizationOutboxRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.BookingAttendanceFinalizationOutboxEvent;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.BookingAttendanceFinalizedEvent;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.BookingAttendanceFinalizationPublisher;
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
 * 003.2 T088: a durable attendance-finalization row reaches a real transaction-bound
 * consumer exactly once, after commit, and a replayed publish run delivers nothing new.
 * A {@link TransactionalEventListener} (the delivery contract behind Spring Modulith's
 * {@code @ApplicationModuleListener}) receives only events published inside a transaction.
 */
@SpringBootTest
@ActiveProfiles("test")
class BookingAttendanceFinalizedEventDeliveryIT {

    @Autowired private BookingAttendanceFinalizationPublisher publisher;
    @Autowired private BookingAttendanceFinalizationOutboxRepository outbox;
    @Autowired private RecordingConsumer consumer;

    @Test
    void finalizedOutcomeIsDeliveredOnceAfterCommitAndReplayDeliversNothingNew() throws InterruptedException {
        String bookingReference = "BK-T088-" + UUID.randomUUID();
        Instant finalizedAt = Instant.parse("2026-11-05T12:00:00Z").truncatedTo(ChronoUnit.MICROS);
        BookingAttendanceFinalizationOutboxEvent row = outbox.saveAndFlush(BookingAttendanceFinalizationOutboxEvent.builder()
                .bookingReference(bookingReference).eventType("BOOKING_ATTENDANCE_FINALIZED")
                .finalizedAt(finalizedAt).createdAt(finalizedAt).build());

        publisher.publishPending();
        publisher.publishPending();
        waitForDelivery(bookingReference);

        assertThat(consumer.received(bookingReference))
                .containsExactly(new BookingAttendanceFinalizedEvent(bookingReference, finalizedAt));
        assertThat(outbox.findById(row.getId()).orElseThrow().getPublishedAt()).isNotNull();

        // A later run (scheduler replay) finds no unpublished row and delivers nothing new.
        publisher.publishPending();
        Thread.sleep(300);
        assertThat(consumer.received(bookingReference)).hasSize(1);
    }

    private void waitForDelivery(String bookingReference) throws InterruptedException {
        for (int i = 0; i < 50 && consumer.received(bookingReference).isEmpty(); i++) Thread.sleep(100);
    }

    @TestConfiguration
    static class ConsumerConfiguration {
        @Bean RecordingConsumer recordingConsumer() { return new RecordingConsumer(); }
    }

    static class RecordingConsumer {
        private final List<BookingAttendanceFinalizedEvent> events = new CopyOnWriteArrayList<>();

        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        void on(BookingAttendanceFinalizedEvent event) { events.add(event); }

        List<BookingAttendanceFinalizedEvent> received(String bookingReference) {
            return events.stream().filter(event -> event.bookingReference().equals(bookingReference)).toList();
        }
    }
}
