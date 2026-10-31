package edu.fpt.sep490_g22.ppswbs_backend.workshopsettlement;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.CancellationHandoffOutboxRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.CancellationHandoffOutboxEvent;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.CancellationHandoffOutboxPublisher;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementCancellationCaseRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementHandoffIdempotencyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class CancellationHandoffOutboxIT {
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired CancellationHandoffOutboxRepository outboxRepository;
    @Autowired SettlementCancellationCaseRepository caseRepository;
    @Autowired SettlementHandoffIdempotencyRepository idempotencyRepository;
    @Autowired CancellationHandoffOutboxPublisher publisher;

    @BeforeEach
    void clean() {
        jdbcTemplate.execute("DELETE FROM workshop_cancellation_outbox");
        jdbcTemplate.execute("DELETE FROM settlement_cancellation_cases");
        jdbcTemplate.execute("DELETE FROM settlement_handoff_idempotency");
    }

    @Test
    void publishesDurableOutboxAndSettlementConsumesItIdempotently() {
        CancellationHandoffOutboxEvent event = outboxRepository.save(CancellationHandoffOutboxEvent.builder()
                .eventReference("EVENT-OUTBOX-1")
                .sessionReference("SES-OUTBOX-1")
                .affectedBookingReferencesJson("[\"BK-OUTBOX-1\"]")
                .cancellationReasonReference("FACILITY_ISSUE")
                .operationReference("OP-OUTBOX-1")
                .correlationReference("CORR-OUTBOX-1")
                .cancellationCaseReference("CASE-OPS-1")
                .createdAt(Instant.now())
                .build());

        publisher.publishPending();

        CancellationHandoffOutboxEvent published = outboxRepository.findById(event.getId()).orElseThrow();
        assertThat(published.getPublishedAt()).isNotNull();
        assertThat(caseRepository.findByOperationReference("OP-OUTBOX-1")).isPresent();
        assertThat(idempotencyRepository.findByOperationReference("OP-OUTBOX-1")).isPresent();
        assertThat(caseRepository.count()).isEqualTo(1);

        // Re-delivery with the same operation reference must replay, not create
        // another settlement case.
        publisher.publishPending();
        assertThat(caseRepository.count()).isEqualTo(1);
    }
}
