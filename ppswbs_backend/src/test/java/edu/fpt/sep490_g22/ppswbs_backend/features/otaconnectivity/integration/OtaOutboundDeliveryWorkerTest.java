package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.integration;

import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.adapter.OtaInventoryAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.adapter.OtaInventoryAdapterRegistry;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.OtaDeliveryOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.OtaDeliveryResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.OtaSyncDeliveryService;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 003.4 T105: one attempt per pending change per poll, recorded through OtaSyncDeliveryService. */
class OtaOutboundDeliveryWorkerTest {

    private final OtaOutboundChangeRepository changes = mock(OtaOutboundChangeRepository.class);
    private final OtaDeliveryAttemptRepository attempts = mock(OtaDeliveryAttemptRepository.class);
    private final OtaInventoryAdapterRegistry adapters = mock(OtaInventoryAdapterRegistry.class);
    private final OtaSyncDeliveryService deliveries = mock(OtaSyncDeliveryService.class);
    private final OtaInventoryAdapter klook = mock(OtaInventoryAdapter.class);
    private OtaOutboundDeliveryWorker worker;
    private OtaOutboundChange change;
    private OtaChannelMapping mapping;

    @BeforeEach
    void setUp() {
        PlatformTransactionManager tx = mock(PlatformTransactionManager.class);
        when(tx.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        worker = new OtaOutboundDeliveryWorker(changes, attempts, adapters, deliveries, tx, 3);
        OtaChannelConnection connection = OtaChannelConnection.builder().providerCode(OtaProvider.KLOOK).build();
        mapping = OtaChannelMapping.builder().connection(connection).status(OtaMappingStatus.BOOKING_ENABLED).build();
        change = OtaOutboundChange.builder().id(7L).mapping(mapping).changeKind(OtaChangeKind.AVAILABILITY)
                .status("QUEUED").build();
        when(changes.findTop50ByStatusInOrderByCreatedAtAscIdAsc(List.of("QUEUED", "RETRYABLE"))).thenReturn(List.of(change));
        when(changes.findByIdForUpdate(7L)).thenReturn(Optional.of(change));
        when(adapters.findAdapter(OtaProvider.KLOOK)).thenReturn(Optional.of(klook));
    }

    private static OtaDeliveryResult result(int attempt, OtaDeliveryOutcome outcome) {
        Instant now = Instant.now();
        return new OtaDeliveryResult(attempt, now, now, outcome, "corr-1", null);
    }

    private OtaDeliveryResult recorded() {
        ArgumentCaptor<OtaDeliveryResult> captor = ArgumentCaptor.forClass(OtaDeliveryResult.class);
        verify(deliveries).recordDeliveryResult(eq(7L), captor.capture());
        return captor.getValue();
    }

    @Test
    void aQueuedChangeIsSentAsTheNextAttemptAndRecorded() {
        when(attempts.countByOutboundChangeId(7L)).thenReturn(1L);
        when(klook.deliver(mapping, change, 2)).thenReturn(result(2, OtaDeliveryOutcome.DELIVERED));

        assertThat(worker.deliverPending()).isEqualTo(1);
        assertThat(recorded().outcome()).isEqualTo(OtaDeliveryOutcome.DELIVERED);
        assertThat(recorded().attemptNumber()).isEqualTo(2);
    }

    @Test
    void aRetryableAnswerAtTheAttemptCapIsRecordedAsExhausted() {
        when(attempts.countByOutboundChangeId(7L)).thenReturn(2L);
        when(klook.deliver(mapping, change, 3)).thenReturn(result(3, OtaDeliveryOutcome.RETRYABLE));

        worker.deliverPending();

        assertThat(recorded().outcome()).isEqualTo(OtaDeliveryOutcome.EXHAUSTED);
        assertThat(recorded().failureCategory()).isEqualTo("RETRIES_EXHAUSTED");
    }

    @Test
    void aProviderWithoutAnAdapterExhaustsAtOnce() {
        when(adapters.findAdapter(OtaProvider.KLOOK)).thenReturn(Optional.empty());

        worker.deliverPending();

        assertThat(recorded().outcome()).isEqualTo(OtaDeliveryOutcome.EXHAUSTED);
        assertThat(recorded().failureCategory()).isEqualTo("NO_ADAPTER");
    }

    @Test
    void anAdapterErrorIsARetryableAttempt() {
        when(klook.deliver(any(), any(), anyInt())).thenThrow(new IllegalStateException("boom"));

        worker.deliverPending();

        assertThat(recorded().outcome()).isEqualTo(OtaDeliveryOutcome.RETRYABLE);
        assertThat(recorded().failureCategory()).isEqualTo("ADAPTER_ERROR");
    }

    @Test
    void aChangeDeliveredByAnotherRunMeanwhileIsNotSentAgain() {
        OtaOutboundChange delivered = OtaOutboundChange.builder().id(7L).mapping(mapping)
                .changeKind(OtaChangeKind.AVAILABILITY).status("DELIVERED").build();
        when(changes.findByIdForUpdate(7L)).thenReturn(Optional.of(delivered));

        assertThat(worker.deliverPending()).isZero();
        verifyNoInteractions(klook);
        verify(deliveries, never()).recordDeliveryResult(anyLong(), any());
    }
}
