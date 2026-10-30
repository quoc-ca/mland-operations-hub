package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.integration.OtaAuditCorrelationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelConnection;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelMapping;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelMappingRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChangeKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaDeliveryAttempt;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaDeliveryAttemptRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaMappingStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaOutboundChange;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaOutboundChangeRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaProvider;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaReconciliationWorkItem;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaReconciliationWorkItemRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.WorkshopSessionOtaSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OtaSyncDeliveryServiceTest {

    @Mock private OtaChannelMappingRepository mappingRepository;
    @Mock private OtaOutboundChangeRepository outboundRepository;
    @Mock private OtaDeliveryAttemptRepository attemptRepository;
    @Mock private OtaReconciliationWorkItemRepository workItemRepository;

    private OtaSyncDeliveryService service;
    private OtaChannelMapping mapping;

    @BeforeEach
    void setUp() {
        service = new OtaSyncDeliveryService(mappingRepository, outboundRepository, attemptRepository,
                workItemRepository, new OtaAuditCorrelationService());
        mapping = OtaChannelMapping.builder()
                .id(71L)
                .connection(OtaChannelConnection.builder().providerCode(OtaProvider.KLOOK).build())
                .sessionReference("session-ref-1")
                .locationReference("location-ref-1")
                .status(OtaMappingStatus.PREPARATION_SYNC)
                .build();
    }

    @Test
    void materializesOneAllowlistedOutboundChangePerSnapshotKind() {
        when(mappingRepository.findAllBySessionReference("session-ref-1")).thenReturn(List.of(mapping));
        when(outboundRepository.findTopByMappingIdOrderBySourceVersionDescIdDesc(71L)).thenReturn(Optional.empty());
        when(outboundRepository.findByMappingIdAndSourceVersionAndChangeKind(any(), any(), any()))
                .thenReturn(Optional.empty());

        int created = service.acceptSnapshot(snapshot(4L));

        assertThat(created).isEqualTo(3);
        ArgumentCaptor<OtaOutboundChange> captor = ArgumentCaptor.forClass(OtaOutboundChange.class);
        verify(outboundRepository, org.mockito.Mockito.times(3)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(OtaOutboundChange::getChangeKind)
                .containsExactlyInAnyOrder(OtaChangeKind.SCHEDULE, OtaChangeKind.AVAILABILITY, OtaChangeKind.SALE_STATE);
        OtaOutboundChange availability = captor.getAllValues().stream()
                .filter(change -> change.getChangeKind() == OtaChangeKind.AVAILABILITY)
                .findFirst().orElseThrow();
        assertThat(availability.getStatus()).isEqualTo("QUEUED");
        assertThat(availability.getCanonicalSnapshot().get("sellableAvailability").asInt()).isEqualTo(12);
        assertThat(availability.getCanonicalSnapshot().toString())
                .doesNotContain("contact", "customer", "ticket", "payment", "email");
    }

    @Test
    void ignoresDuplicateOrOlderSourceVersion() {
        OtaOutboundChange current = OtaOutboundChange.builder()
                .sourceVersion(8L).sourceChangeReference("change-8").build();
        when(mappingRepository.findAllBySessionReference("session-ref-1")).thenReturn(List.of(mapping));
        when(outboundRepository.findTopByMappingIdOrderBySourceVersionDescIdDesc(71L))
                .thenReturn(Optional.of(current));

        assertThat(service.acceptSnapshot(snapshot(8L))).isZero();

        verify(outboundRepository, never()).save(any(OtaOutboundChange.class));
    }

    @Test
    void sourceVersionGapSuspendsOnlyAffectedMappingAndRequestsAuthoritativeResync() {
        mapping = OtaChannelMapping.builder()
                .id(71L).connection(OtaChannelConnection.builder().providerCode(OtaProvider.KLOOK).build())
                .sessionReference("session-ref-1").locationReference("location-ref-1")
                .status(OtaMappingStatus.BOOKING_ENABLED).build();
        OtaOutboundChange latest = OtaOutboundChange.builder()
                .sourceVersion(3L).sourceChangeReference("change-3").build();
        when(mappingRepository.findAllBySessionReference("session-ref-1")).thenReturn(List.of(mapping));
        when(outboundRepository.findTopByMappingIdOrderBySourceVersionDescIdDesc(71L))
                .thenReturn(Optional.of(latest));
        when(workItemRepository.existsByMapping_IdAndCategoryAndStatus(71L, "SOURCE_VERSION_GAP", "OPEN"))
                .thenReturn(false);

        assertThat(service.acceptSnapshot(snapshot(5L))).isZero();

        assertThat(mapping.getStatus()).isEqualTo(OtaMappingStatus.SYNC_SUSPENDED);
        verify(mappingRepository).save(mapping);
        verify(outboundRepository, never()).save(any(OtaOutboundChange.class));
        ArgumentCaptor<OtaReconciliationWorkItem> workItem = ArgumentCaptor.forClass(OtaReconciliationWorkItem.class);
        verify(workItemRepository).save(workItem.capture());
        assertThat(workItem.getValue().getCategory()).isEqualTo("SOURCE_VERSION_GAP");
        assertThat(workItem.getValue().getDecision()).isNull();
        assertThat(workItem.getValue().getStatus()).isEqualTo("OPEN");
        assertThat(workItem.getValue().getLocationReference()).isEqualTo("location-ref-1");
    }

    @Test
    void conflictingChangeReferenceAtSameVersionSuspendsAndDeduplicatesOpenResyncWork() {
        OtaOutboundChange current = OtaOutboundChange.builder()
                .sourceVersion(8L).sourceChangeReference("different-change").build();
        when(mappingRepository.findAllBySessionReference("session-ref-1")).thenReturn(List.of(mapping));
        when(outboundRepository.findTopByMappingIdOrderBySourceVersionDescIdDesc(71L))
                .thenReturn(Optional.of(current));
        when(outboundRepository.findByMappingIdAndSourceVersionAndChangeKind(71L, 8L, OtaChangeKind.SCHEDULE))
                .thenReturn(Optional.of(current));
        when(workItemRepository.existsByMapping_IdAndCategoryAndStatus(71L, "SOURCE_ORDER_UNCERTAIN", "OPEN"))
                .thenReturn(true);

        assertThat(service.acceptSnapshot(snapshot(8L))).isZero();

        assertThat(mapping.getStatus()).isEqualTo(OtaMappingStatus.SYNC_SUSPENDED);
        verify(mappingRepository).save(mapping);
        verify(workItemRepository, never()).save(any(OtaReconciliationWorkItem.class));
        verify(outboundRepository, never()).save(any(OtaOutboundChange.class));
    }

    @Test
    void authoritativeSameVersionConflictFailsClosedWithoutRewritingOutboundEvidence() {
        var originalPayload = JsonNodeFactory.instance.objectNode().put("scheduledStart", "2026-11-01T10:00:00Z");
        OtaOutboundChange delivered = OtaOutboundChange.builder()
                .mapping(mapping)
                .sourceVersion(8L)
                .sourceChangeReference("change-8")
                .changeKind(OtaChangeKind.SCHEDULE)
                .canonicalSnapshot(originalPayload)
                .status("DELIVERED")
                .build();
        when(outboundRepository.findTopByMappingIdOrderBySourceVersionDescIdDesc(71L))
                .thenReturn(Optional.of(delivered));
        when(outboundRepository.findByMappingIdAndSourceVersionAndChangeKind(71L, 8L, OtaChangeKind.SCHEDULE))
                .thenReturn(Optional.of(delivered));

        assertThatThrownBy(() -> service.queueAuthoritativeSnapshot(mapping, snapshot(8L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("conflicts with a recorded source version");

        assertThat(delivered.getStatus()).isEqualTo("DELIVERED");
        assertThat(delivered.getCanonicalSnapshot()).isSameAs(originalPayload);
        verify(outboundRepository, never()).save(any(OtaOutboundChange.class));
    }

    @Test
    void preparationSyncReturnsToSalesClosedOnlyAfterEverySnapshotKindIsDelivered() {
        OtaOutboundChange change = OtaOutboundChange.builder()
                .id(202L).mapping(mapping).sourceVersion(4L).sourceChangeReference("change-4")
                .changeKind(OtaChangeKind.SALE_STATE).canonicalSnapshot(JsonNodeFactory.instance.objectNode())
                .status("QUEUED").build();
        when(outboundRepository.findById(202L)).thenReturn(Optional.of(change));
        when(attemptRepository.findByOutboundChangeIdAndAttemptNumber(202L, 1)).thenReturn(Optional.empty());
        when(outboundRepository.findAllByMapping_IdAndSourceVersion(71L, 4L)).thenReturn(List.of(
                OtaOutboundChange.builder().mapping(mapping).sourceVersion(4L).changeKind(OtaChangeKind.SCHEDULE)
                        .status("DELIVERED").build(),
                OtaOutboundChange.builder().mapping(mapping).sourceVersion(4L).changeKind(OtaChangeKind.AVAILABILITY)
                        .status("DELIVERED").build(), change));

        service.recordDeliveryResult(202L, new OtaDeliveryResult(1, at("2026-10-29T10:00:00Z"),
                at("2026-10-29T10:00:01Z"), OtaDeliveryOutcome.DELIVERED, null, null));

        assertThat(mapping.getStatus()).isEqualTo(OtaMappingStatus.SALES_CLOSED);
        verify(mappingRepository).save(mapping);
    }

    @Test
    void exhaustedDeliveryAppendsAttemptSuspendsOnlyMappingAndCreatesOpenReconciliation() {
        OtaOutboundChange change = OtaOutboundChange.builder()
                .id(101L)
                .mapping(mapping)
                .sourceVersion(4L)
                .changeKind(OtaChangeKind.AVAILABILITY)
                .canonicalSnapshot(JsonNodeFactory.instance.objectNode())
                .status("QUEUED")
                .build();
        when(outboundRepository.findById(101L)).thenReturn(Optional.of(change));
        when(attemptRepository.findByOutboundChangeIdAndAttemptNumber(101L, 2)).thenReturn(Optional.empty());

        service.recordDeliveryResult(101L, new OtaDeliveryResult(2, at("2026-10-29T10:00:00Z"),
                at("2026-10-29T10:00:01Z"), OtaDeliveryOutcome.EXHAUSTED, "untrusted-provider-token", "TIMEOUT"));

        assertThat(mapping.getStatus()).isEqualTo(OtaMappingStatus.SYNC_SUSPENDED);
        assertThat(change.getStatus()).isEqualTo("EXHAUSTED");
        ArgumentCaptor<OtaDeliveryAttempt> attempt = ArgumentCaptor.forClass(OtaDeliveryAttempt.class);
        verify(attemptRepository).save(attempt.capture());
        assertThat(attempt.getValue().getAttemptNumber()).isEqualTo(2);
        assertThat(attempt.getValue().getOutcome()).isEqualTo("EXHAUSTED");
        assertThat(attempt.getValue().getProviderCorrelationReference()).doesNotContain("untrusted-provider-token");
        ArgumentCaptor<OtaReconciliationWorkItem> workItem = ArgumentCaptor.forClass(OtaReconciliationWorkItem.class);
        verify(workItemRepository).save(workItem.capture());
        assertThat(workItem.getValue().getStatus()).isEqualTo("OPEN");
        assertThat(workItem.getValue().getLocationReference()).isEqualTo("location-ref-1");
        verify(mappingRepository).save(mapping);
    }

    private static WorkshopSessionOtaSnapshot snapshot(long version) {
        return new WorkshopSessionOtaSnapshot("session-ref-1", "location-ref-1",
                at("2026-11-01T10:00:00Z"), at("2026-11-01T11:00:00Z"), 12, "PUBLISHED", version,
                "change-" + version, at("2026-10-29T10:00:00Z"));
    }

    private static Instant at(String value) {
        return Instant.parse(value);
    }
}
