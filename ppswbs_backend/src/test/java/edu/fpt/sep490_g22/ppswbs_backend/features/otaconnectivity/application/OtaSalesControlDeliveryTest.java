package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application;

import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.integration.OtaAuditCorrelationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 003.4 T120 (FR-004a, FR-004c): the provider answer to a sales-close command updates only the outbox projection. */
class OtaSalesControlDeliveryTest {

    private final OtaChannelMappingRepository mappings = mock(OtaChannelMappingRepository.class);
    private final OtaControlOutboxRepository commands = mock(OtaControlOutboxRepository.class);
    private final OtaReconciliationDecisionRepository decisions = mock(OtaReconciliationDecisionRepository.class);
    private final OtaReconciliationWorkItemRepository workItems = mock(OtaReconciliationWorkItemRepository.class);
    private final OtaAuditCorrelationService audit = spy(new OtaAuditCorrelationService());
    private OtaSalesControlService service;
    private OtaChannelMapping mapping;
    private OtaControlOutboxCommand command;

    @BeforeEach
    void setUp() {
        service = new OtaSalesControlService(mappings, commands, decisions, audit, workItems);
        mapping = OtaChannelMapping.builder().id(71L)
                .connection(OtaChannelConnection.builder().providerCode(OtaProvider.KLOOK).build())
                .locationReference("location-ref-1")
                .status(OtaMappingStatus.SALES_CLOSED).build();
        command = OtaControlOutboxCommand.salesClose(UUID.randomUUID().toString(), mapping, "42", "Venue flooded",
                "a".repeat(64), "b".repeat(64), UUID.randomUUID().toString());
    }

    private static OtaDeliveryResult result(OtaDeliveryOutcome outcome, String category) {
        Instant now = Instant.now();
        return new OtaDeliveryResult(1, now, now, outcome, "corr-provider-1", category);
    }

    @Test
    void deliveredMarksTheCommandDeliveredAndOpensNoWorkItem() {
        service.recordSalesCloseDelivery(command, result(OtaDeliveryOutcome.DELIVERED, null));

        assertThat(command.getStatus()).isEqualTo("DELIVERED");
        assertThat(command.getProviderCorrelationReference()).isEqualTo("corr-provider-1");
        assertThat(command.getDeliveredAt()).isNotNull();
        assertThat(mapping.getStatus()).isEqualTo(OtaMappingStatus.SALES_CLOSED);
        verify(commands).save(command);
        verify(workItems, never()).save(any());
        verifyNoInteractions(decisions);
        verify(audit).record(eq(OtaAuditCorrelationService.IntegrationAction.OUTBOUND_DELIVERY_COMPLETED),
                eq(OtaProvider.KLOOK), eq(71L), anyString(), eq(OtaAuditCorrelationService.IntegrationOutcome.ACCEPTED));
    }

    @Test
    void exhaustedKeepsSalesClosedOpensOneWorkItemAndAudits() {
        service.recordSalesCloseDelivery(command, result(OtaDeliveryOutcome.EXHAUSTED, "RETRIES_EXHAUSTED"));

        assertThat(command.getStatus()).isEqualTo("EXHAUSTED");
        assertThat(command.getFailureCategory()).isEqualTo("RETRIES_EXHAUSTED");
        assertThat(mapping.getStatus()).isEqualTo(OtaMappingStatus.SALES_CLOSED);
        ArgumentCaptor<OtaReconciliationWorkItem> item = ArgumentCaptor.forClass(OtaReconciliationWorkItem.class);
        verify(workItems).save(item.capture());
        assertThat(item.getValue().getCategory()).isEqualTo("INVENTORY_DELIVERY_FAILURE");
        assertThat(item.getValue().getStatus()).isEqualTo("OPEN");
        assertThat(item.getValue().getLocationReference()).isEqualTo("location-ref-1");
        verifyNoInteractions(decisions);
        verify(audit).record(eq(OtaAuditCorrelationService.IntegrationAction.RECONCILIATION_REQUIRED),
                eq(OtaProvider.KLOOK), eq(71L), anyString(), eq(OtaAuditCorrelationService.IntegrationOutcome.REJECTED));
    }

    @Test
    void exhaustedReusesAnOpenWorkItemForTheMapping() {
        when(workItems.existsByMapping_IdAndCategoryAndStatus(71L, "INVENTORY_DELIVERY_FAILURE", "OPEN"))
                .thenReturn(true);

        service.recordSalesCloseDelivery(command, result(OtaDeliveryOutcome.EXHAUSTED, "CLIENT_ERROR_4XX"));

        assertThat(command.getStatus()).isEqualTo("EXHAUSTED");
        verify(workItems, never()).save(any());
    }

    @Test
    void aRetryableResultIsNotAFinalAnswer() {
        assertThatThrownBy(() -> service.recordSalesCloseDelivery(command, result(OtaDeliveryOutcome.RETRYABLE, "X")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(command.getStatus()).isEqualTo("QUEUED");
    }
}
