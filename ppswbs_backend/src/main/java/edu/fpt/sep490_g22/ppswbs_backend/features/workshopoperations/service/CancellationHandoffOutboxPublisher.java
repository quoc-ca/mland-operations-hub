package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.common.workshop.CancellationHandoffRequestedEvent;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.CancellationHandoffOutboxRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.CancellationHandoffOutboxEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CancellationHandoffOutboxPublisher {
    private final CancellationHandoffOutboxRepository repository;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelayString = "${workshop.cancellation-outbox.poll-delay-ms:1000}")
    @Transactional
    public void publishPending() {
        for (CancellationHandoffOutboxEvent row : repository.findTop50ByPublishedAtIsNullOrderByIdAsc()) {
            publish(row);
        }
    }

    @Transactional
    public void publish(CancellationHandoffOutboxEvent row) {
        try {
            List<String> bookings = deserializeBookings(row.getAffectedBookingReferencesJson());
            eventPublisher.publishEvent(new CancellationHandoffRequestedEvent(
                    row.getEventReference(), row.getSessionReference(), bookings,
                    row.getCancellationReasonReference(), row.getOperationReference(),
                    row.getCorrelationReference(), row.getCancellationCaseReference()));
            row.setPublishedAt(Instant.now());
            repository.save(row);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to publish cancellation handoff outbox event", ex);
        }
    }

    private List<String> deserializeBookings(String payload) throws Exception {
        try {
            return objectMapper.readValue(payload, new TypeReference<>() {});
        } catch (com.fasterxml.jackson.databind.exc.MismatchedInputException encodedJson) {
            // H2 exposes JSON columns as a quoted VARCHAR; MySQL returns the
            // native JSON text. Accept both representations without changing
            // the durable payload contract.
            String nested = objectMapper.readValue(payload, String.class);
            return objectMapper.readValue(nested, new TypeReference<>() {});
        }
    }
}
