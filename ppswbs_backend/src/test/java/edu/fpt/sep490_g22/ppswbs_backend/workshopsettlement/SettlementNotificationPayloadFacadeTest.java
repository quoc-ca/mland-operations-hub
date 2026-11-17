package edu.fpt.sep490_g22.ppswbs_backend.workshopsettlement;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementCancellationCaseRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementHandoffIdempotencyRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementNotificationHandoffRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementNotificationHandoff;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.SettlementNotificationDeliveryPayloadQuery;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.FinalPaymentService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.WorkshopSettlementFacadeImpl;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class SettlementNotificationPayloadFacadeTest {
    private final SettlementNotificationHandoffRepository notifications = mock(SettlementNotificationHandoffRepository.class);
    private final WorkshopSettlementFacadeImpl facade = new WorkshopSettlementFacadeImpl(
            mock(SettlementCancellationCaseRepository.class), mock(SettlementHandoffIdempotencyRepository.class),
            new ObjectMapper(), mock(FinalPaymentService.class), notifications);

    @Test
    void returnsOnlyRecipientAndRenderedOutcomeContent() {
        Instant now = Instant.now();
        when(notifications.findByNotificationReference("NOTIF-1")).thenReturn(Optional.of(
                handoff(now.plusSeconds(3600))));

        var payload = facade.getSettlementNotificationDeliveryPayload(
                new SettlementNotificationDeliveryPayloadQuery("NOTIF-1"));

        assertThat(payload).isNotNull();
        assertThat(payload.storedRecipient()).isEqualTo("member@example.test");
        assertThat(payload.renderedSettlementContent()).isEqualTo("Payment confirmed");
    }

    @Test
    void doesNotReturnPayloadAfterThirtyDayRetention() {
        when(notifications.findByNotificationReference("NOTIF-2")).thenReturn(Optional.of(
                handoff(Instant.now().minusSeconds(1))));

        assertThat(facade.getSettlementNotificationDeliveryPayload(
                new SettlementNotificationDeliveryPayloadQuery("NOTIF-2"))).isNull();
    }

    private SettlementNotificationHandoff handoff(Instant payloadExpiry) {
        return SettlementNotificationHandoff.builder().notificationReference("NOTIF-1")
                .settlementReference("BILL-1").notificationKind("FINAL_PAYMENT_VERIFIED")
                .correlationReference("CORR-1").storedRecipient("member@example.test")
                .renderedSettlementContent("Payment confirmed")
                .payloadExpiresAt(payloadExpiry).createdAt(Instant.now()).build();
    }
}
