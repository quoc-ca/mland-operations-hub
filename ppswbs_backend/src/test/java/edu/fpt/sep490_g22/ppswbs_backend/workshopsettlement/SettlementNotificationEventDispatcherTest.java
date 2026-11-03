package edu.fpt.sep490_g22.ppswbs_backend.workshopsettlement;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementNotificationHandoffRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementNotificationHandoff;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.SettlementNotificationReady;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.SettlementNotificationEventDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class SettlementNotificationEventDispatcherTest {
    private final SettlementNotificationHandoffRepository repository = mock(SettlementNotificationHandoffRepository.class);
    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    private final SettlementNotificationEventDispatcher dispatcher =
            new SettlementNotificationEventDispatcher(repository, publisher);

    @Test
    void eventContainsOnlyReferencesAndSuccessfulPublishMarksOutboxDispatched() {
        SettlementNotificationHandoff handoff = handoff();
        when(repository.findByNotificationReference("NOTIF-1")).thenReturn(Optional.of(handoff));

        dispatcher.dispatch("NOTIF-1");

        var captor = org.mockito.ArgumentCaptor.forClass(SettlementNotificationReady.class);
        verify(publisher).publishEvent(captor.capture());
        SettlementNotificationReady event = captor.getValue();
        assertThat(event.notificationReference()).isEqualTo("NOTIF-1");
        assertThat(event.settlementReference()).isEqualTo("BILL-1");
        assertThat(event.notificationKind()).isEqualTo("FINAL_PAYMENT_VERIFIED");
        assertThat(event.correlationReference()).isEqualTo("CORR-1");
        assertThat(event.toString()).doesNotContain("member@example.test", "payment-action-link");
        assertThat(handoff.getDispatchedAt()).isNotNull();
        verify(repository).save(handoff);
    }

    @Test
    void leavesExpiredOrAlreadyDispatchedRowsAlone() {
        SettlementNotificationHandoff expired = handoff();
        expired.setPayloadExpiresAt(Instant.now().minusSeconds(1));
        when(repository.findByNotificationReference("NOTIF-1")).thenReturn(Optional.of(expired));

        dispatcher.dispatch("NOTIF-1");

        verifyNoInteractions(publisher);
        verify(repository, never()).save(any());
    }

    private SettlementNotificationHandoff handoff() {
        return SettlementNotificationHandoff.builder().notificationReference("NOTIF-1")
                .settlementReference("BILL-1").notificationKind("FINAL_PAYMENT_VERIFIED")
                .correlationReference("CORR-1").storedRecipient("member@example.test")
                .renderedSettlementContent("Payment confirmed")
                .payloadExpiresAt(Instant.now().plusSeconds(86400)).createdAt(Instant.now()).build();
    }
}
