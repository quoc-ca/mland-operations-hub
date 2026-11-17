package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade;

import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TicketAttendanceFinalizedEventTest {

    @Test
    void payloadCarriesNoPiiAndNoOutcome() {
        RecordComponent[] components = TicketAttendanceFinalizedEvent.class.getRecordComponents();
        List<String> names = Arrays.stream(components).map(RecordComponent::getName).toList();

        // Exactly the 3 reference-only fields approved in BE-DESIGN-0004
        assertThat(names).containsExactly("eventId", "ticketReference", "windowClosedAt");

        // Asserts types: String, String, Instant
        assertThat(components[0].getType()).isEqualTo(String.class);
        assertThat(components[1].getType()).isEqualTo(String.class);
        assertThat(components[2].getType()).isEqualTo(Instant.class);

        // Instantiation check
        Instant now = Instant.now();
        TicketAttendanceFinalizedEvent event = new TicketAttendanceFinalizedEvent("ev-123", "ops-ref-456", now);
        assertThat(event.eventId()).isEqualTo("ev-123");
        assertThat(event.ticketReference()).isEqualTo("ops-ref-456");
        assertThat(event.windowClosedAt()).isEqualTo(now);
    }
}

