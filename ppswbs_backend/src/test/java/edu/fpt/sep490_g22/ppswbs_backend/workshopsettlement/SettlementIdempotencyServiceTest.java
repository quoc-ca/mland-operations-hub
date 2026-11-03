package edu.fpt.sep490_g22.ppswbs_backend.workshopsettlement;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementIdempotencyOutcomeRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementIdempotencyOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.SettlementIdempotencyService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.WorkshopSettlementConflictException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SettlementIdempotencyServiceTest {
    @Mock SettlementIdempotencyOutcomeRepository repository;

    @Test void firstRequestPersistsCompletedOutcome() {
        when(repository.findByOperationReference("OP-1")).thenReturn(Optional.empty());
        SettlementIdempotencyService service = new SettlementIdempotencyService(repository);
        var result = service.execute("OP-1", "fp-1", () -> "CASE-1");
        assertThat(result).isEqualTo(new edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.SettlementIdempotencyResult(false, "CASE-1"));
        verify(repository).saveAndFlush(any(SettlementIdempotencyOutcome.class));
    }

    @Test void sameKeyAndFingerprintReplaysOriginalOutcome() {
        when(repository.findByOperationReference("OP-1")).thenReturn(Optional.of(existing("fp-1", "CASE-1")));
        SettlementIdempotencyService service = new SettlementIdempotencyService(repository);
        var result = service.execute("OP-1", "fp-1", () -> "CASE-2");
        assertThat(result.replayed()).isTrue();
        assertThat(result.outcomeReference()).isEqualTo("CASE-1");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test void changedPayloadWithSameKeyIsConflict() {
        when(repository.findByOperationReference("OP-1")).thenReturn(Optional.of(existing("fp-1", "CASE-1")));
        SettlementIdempotencyService service = new SettlementIdempotencyService(repository);
        assertThatThrownBy(() -> service.execute("OP-1", "fp-2", () -> "CASE-2"))
                .isInstanceOf(WorkshopSettlementConflictException.class)
                .hasMessage("IDEMPOTENCY_CONFLICT");
        verify(repository, never()).saveAndFlush(any());
    }

    private static SettlementIdempotencyOutcome existing(String fingerprint, String outcomeReference) {
        return SettlementIdempotencyOutcome.builder().operationReference("OP-1")
                .requestFingerprint(fingerprint).outcomeStatus("COMPLETED")
                .outcomeReference(outcomeReference).correlationReference("OP-1").build();
    }
}
