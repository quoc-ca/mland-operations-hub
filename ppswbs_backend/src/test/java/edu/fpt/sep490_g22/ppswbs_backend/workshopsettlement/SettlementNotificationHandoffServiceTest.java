package edu.fpt.sep490_g22.ppswbs_backend.workshopsettlement;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementNotificationHandoffRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementNotificationHandoff;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.SettlementNotificationEventDispatcher;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.SettlementNotificationHandoffService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SettlementNotificationHandoffServiceTest {
    private final SettlementNotificationHandoffRepository repository = mock(SettlementNotificationHandoffRepository.class);
    private final SettlementNotificationEventDispatcher dispatcher = mock(SettlementNotificationEventDispatcher.class);
    private final SettlementNotificationHandoffService service =
            new SettlementNotificationHandoffService(repository, dispatcher);

    @AfterEach
    void cleanupSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void persistsSnapshotAndPublishesOnlyReferenceEventAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();
        Instant before = Instant.now();

        String reference = service.create("BILL-123", "FINAL_PAYMENT_VERIFIED", "CORR-123",
                "member@example.test", "Your workshop payment was verified");

        verify(repository).saveAndFlush(any(SettlementNotificationHandoff.class));
        verifyNoInteractions(dispatcher);
        var callbacks = TransactionSynchronizationManager.getSynchronizations();
        assertThat(callbacks).hasSize(1);
        callbacks.forEach(callback -> callback.afterCommit());

        verify(dispatcher).dispatch(reference);

        var handoffCaptor = org.mockito.ArgumentCaptor.forClass(SettlementNotificationHandoff.class);
        verify(repository).saveAndFlush(handoffCaptor.capture());
        SettlementNotificationHandoff saved = handoffCaptor.getValue();
        assertThat(saved.getPayloadExpiresAt()).isEqualTo(saved.getCreatedAt().plus(Duration.ofDays(30)));
        assertThat(saved.getCreatedAt()).isAfterOrEqualTo(before);
    }

    @Test
    void rejectsSnapshotCreationWithoutAnActiveTransactionSynchronization() {
        assertThatThrownBy(() -> service.create("BILL-123", "REFUND_VERIFIED", "CORR-123",
                "member@example.test", "Refund completed"))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(repository, dispatcher);
    }
}
