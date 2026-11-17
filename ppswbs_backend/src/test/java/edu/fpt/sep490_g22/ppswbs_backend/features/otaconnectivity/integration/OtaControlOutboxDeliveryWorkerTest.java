package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.integration;

import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.adapter.OtaInventoryAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.adapter.OtaInventoryAdapterRegistry;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.OtaDeliveryOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.OtaDeliveryResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.OtaSalesControlService;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 003.4 T120: queued Manager sales-close commands reach the provider and end DELIVERED or EXHAUSTED. */
class OtaControlOutboxDeliveryWorkerTest {

    private final OtaControlOutboxRepository commands = mock(OtaControlOutboxRepository.class);
    private final OtaInventoryAdapterRegistry adapters = mock(OtaInventoryAdapterRegistry.class);
    private final OtaSalesControlService salesControl = mock(OtaSalesControlService.class);
    private final OtaInventoryAdapter klook = mock(OtaInventoryAdapter.class);
    private OtaControlOutboxDeliveryWorker worker;
    private OtaControlOutboxCommand command;
    private OtaChannelMapping mapping;
    private String operationReference;

    @BeforeEach
    void setUp() {
        PlatformTransactionManager tx = mock(PlatformTransactionManager.class);
        when(tx.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        worker = new OtaControlOutboxDeliveryWorker(commands, adapters, salesControl, tx, 3);
        OtaChannelConnection connection = OtaChannelConnection.builder().providerCode(OtaProvider.KLOOK).build();
        mapping = OtaChannelMapping.builder().id(71L).connection(connection)
                .status(OtaMappingStatus.SALES_CLOSED).build();
        operationReference = UUID.randomUUID().toString();
        command = OtaControlOutboxCommand.salesClose(operationReference, mapping, "42", "Venue flooded",
                "a".repeat(64), "b".repeat(64), UUID.randomUUID().toString());
        when(commands.findTop50ByStatusOrderByCreatedAtAscIdAsc("QUEUED")).thenReturn(List.of(command));
        when(commands.findByIdForUpdate(any())).thenReturn(Optional.of(command));
        when(adapters.findAdapter(OtaProvider.KLOOK)).thenReturn(Optional.of(klook));
    }

    private static OtaDeliveryResult result(int attempt, OtaDeliveryOutcome outcome) {
        Instant now = Instant.now();
        return new OtaDeliveryResult(attempt, now, now, outcome, "corr-1", outcome == OtaDeliveryOutcome.DELIVERED
                ? null : "PROVIDER_5XX");
    }

    private OtaDeliveryResult recorded() {
        ArgumentCaptor<OtaDeliveryResult> captor = ArgumentCaptor.forClass(OtaDeliveryResult.class);
        verify(salesControl).recordSalesCloseDelivery(eq(command), captor.capture());
        return captor.getValue();
    }

    @Test
    void aQueuedSalesCloseIsSentToTheProviderAndRecordedDelivered() {
        when(klook.deliverSalesClose(mapping, operationReference, 1)).thenReturn(result(1, OtaDeliveryOutcome.DELIVERED));

        assertThat(worker.deliverPending()).isEqualTo(1);

        assertThat(recorded().outcome()).isEqualTo(OtaDeliveryOutcome.DELIVERED);
        verify(klook).deliverSalesClose(mapping, operationReference, 1);
    }

    @Test
    void aRetryableAnswerIsRetriedUntilDelivered() {
        when(klook.deliverSalesClose(mapping, operationReference, 1)).thenReturn(result(1, OtaDeliveryOutcome.RETRYABLE));
        when(klook.deliverSalesClose(mapping, operationReference, 2)).thenReturn(result(2, OtaDeliveryOutcome.DELIVERED));

        worker.deliverPending();

        assertThat(recorded().outcome()).isEqualTo(OtaDeliveryOutcome.DELIVERED);
        assertThat(recorded().attemptNumber()).isEqualTo(2);
    }

    @Test
    void retryableAnswersUpToTheCapAreRecordedExhausted() {
        when(klook.deliverSalesClose(eq(mapping), eq(operationReference), anyInt()))
                .thenAnswer(invocation -> result(invocation.getArgument(2), OtaDeliveryOutcome.RETRYABLE));

        worker.deliverPending();

        verify(klook, times(3)).deliverSalesClose(eq(mapping), eq(operationReference), anyInt());
        assertThat(recorded().outcome()).isEqualTo(OtaDeliveryOutcome.EXHAUSTED);
        assertThat(recorded().failureCategory()).isEqualTo("RETRIES_EXHAUSTED");
    }

    @Test
    void aPermanentProviderRefusalIsNotRetried() {
        when(klook.deliverSalesClose(mapping, operationReference, 1)).thenReturn(result(1, OtaDeliveryOutcome.EXHAUSTED));

        worker.deliverPending();

        verify(klook, times(1)).deliverSalesClose(any(), any(), anyInt());
        assertThat(recorded().outcome()).isEqualTo(OtaDeliveryOutcome.EXHAUSTED);
    }

    @Test
    void anAdapterExceptionCountsAsRetryable() {
        when(klook.deliverSalesClose(eq(mapping), eq(operationReference), anyInt()))
                .thenThrow(new IllegalStateException("boom"));

        worker.deliverPending();

        verify(klook, times(3)).deliverSalesClose(any(), any(), anyInt());
        assertThat(recorded().outcome()).isEqualTo(OtaDeliveryOutcome.EXHAUSTED);
    }

    @Test
    void aMissingAdapterIsRecordedExhaustedWithoutSending() {
        when(adapters.findAdapter(OtaProvider.KLOOK)).thenReturn(Optional.empty());

        worker.deliverPending();

        assertThat(recorded().outcome()).isEqualTo(OtaDeliveryOutcome.EXHAUSTED);
        assertThat(recorded().failureCategory()).isEqualTo("NO_ADAPTER");
    }

    @Test
    void aCommandAlreadyHandledByAnotherRunIsSkipped() {
        command.recordDelivered("corr-0");

        assertThat(worker.deliverPending()).isZero();

        verifyNoInteractions(klook);
        verify(salesControl, never()).recordSalesCloseDelivery(any(), any());
    }
}
