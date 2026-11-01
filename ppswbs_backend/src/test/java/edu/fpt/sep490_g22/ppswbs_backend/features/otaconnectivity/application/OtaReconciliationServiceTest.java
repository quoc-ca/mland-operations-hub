package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.integration.OtaAuditCorrelationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelConnection;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelMapping;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelMappingRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChangeKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaMappingStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaOutboundChange;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaOutboundChangeRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaProvider;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaReconciliationDecision;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaReconciliationDecisionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaReconciliationWorkItem;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaReconciliationWorkItemRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.WorkshopOtaResyncFacade;
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
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OtaReconciliationServiceTest {
    @Mock private OtaChannelMappingRepository mappingRepository;
    @Mock private OtaReconciliationWorkItemRepository workItemRepository;
    @Mock private OtaReconciliationDecisionRepository decisionRepository;
    @Mock private OtaOutboundChangeRepository outboundChangeRepository;
    @Mock private OtaSyncDeliveryService syncDeliveryService;
    @Mock private WorkshopOtaResyncFacade operationsResyncFacade;

    private OtaReconciliationService service;
    private OtaChannelMapping mapping;
    private OtaReconciliationWorkItem workItem;
    private final AtomicReference<OtaReconciliationDecision> latestDecision = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        service = new OtaReconciliationService(mappingRepository, workItemRepository, decisionRepository,
                outboundChangeRepository, syncDeliveryService, operationsResyncFacade,
                new OtaAuditCorrelationService());
        mapping = mapping(OtaMappingStatus.DRAFT);
        workItem = OtaReconciliationWorkItem.builder().id(91L).publicReference("00000000-0000-4000-8000-000000000091").mapping(mapping)
                .locationReference("location-ref-1").category("SOURCE_VERSION_GAP").status("OPEN").build();
        when(decisionRepository.findByActorAccountReferenceAndIdempotencyKeyHash(any(), any()))
                .thenAnswer(call -> Optional.ofNullable(latestDecision.get()));
    }

    @Test
    void globalManagerCanQueuePreparationSnapshotAndReplaySameIdempotencyKey() {
        stubDecisionSave();
        when(mappingRepository.save(any(OtaChannelMapping.class))).thenAnswer(call -> call.getArgument(0));
        when(mappingRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(mapping));
        when(operationsResyncFacade.resync("session-ref-1")).thenReturn(snapshot(7L));

        OtaOperationAccepted first = service.requestSync(12L, "PREPARATION", "prepare mapping", "idem-key-0001",
                actor(ActorRole.MANAGER, 5L));
        OtaOperationAccepted replay = service.requestSync(12L, "PREPARATION", "prepare mapping", "idem-key-0001",
                actor(ActorRole.MANAGER, 5L));

        assertThat(mapping.getStatus()).isEqualTo(OtaMappingStatus.PREPARATION_SYNC);
        assertThat(first).isEqualTo(replay);
        assertThat(first.status()).isEqualTo("QUEUED");
        verify(syncDeliveryService).queueAuthoritativeSnapshot(mapping, snapshot(7L));
        verify(operationsResyncFacade).resync("session-ref-1");
        ArgumentCaptor<OtaReconciliationDecision> decision = ArgumentCaptor.forClass(OtaReconciliationDecision.class);
        verify(decisionRepository).saveAndFlush(decision.capture());
        assertThat(decision.getValue().getActorAccountReference()).isEqualTo("5");
        assertThat(decision.getValue().getActorRole()).isEqualTo("MANAGER");
        assertThat(decision.getValue().getActionScope()).isEqualTo("BUSINESS");
        assertThat(decision.getValue().getSourceVersion()).isEqualTo(7L);
    }

    @Test
    void activeAdminCanRequestResyncAndMappingRemainsSuspendedUntilAllKindsAreDelivered() {
        stubDecisionSave();
        when(workItemRepository.save(any(OtaReconciliationWorkItem.class))).thenAnswer(call -> call.getArgument(0));
        mapping = mapping(OtaMappingStatus.SYNC_SUSPENDED);
        workItem = OtaReconciliationWorkItem.builder().id(91L).publicReference("00000000-0000-4000-8000-000000000091").mapping(mapping)
                .locationReference("location-ref-1").category("SOURCE_VERSION_GAP").status("OPEN").build();
        when(workItemRepository.findByIdForUpdate(91L)).thenReturn(Optional.of(workItem));
        when(operationsResyncFacade.resync("session-ref-1")).thenReturn(snapshot(9L));

        OtaReconciliationActionResult result = service.resolve(91L, "RESYNC_REQUESTED", "resync after gap",
                "idem-key-0002", actor(ActorRole.ADMIN, 6L));

        assertThat(result.status()).isEqualTo("IN_PROGRESS");
        assertThat(mapping.getStatus()).isEqualTo(OtaMappingStatus.SYNC_SUSPENDED);
        verify(syncDeliveryService).queueAuthoritativeSnapshot(mapping, snapshot(9L));
        assertThat(latestDecision.get().getSourceVersion()).isEqualTo(9L);
        assertThat(latestDecision.get().getActionScope()).isEqualTo("RECOVERY");
    }

    @Test
    void mappingCannotBeAcceptedUntilFullResyncDeliveryAndCurrentVersionAreConfirmed() {
        mapping = mapping(OtaMappingStatus.SYNC_SUSPENDED);
        workItem = OtaReconciliationWorkItem.builder().id(91L).publicReference("00000000-0000-4000-8000-000000000091").mapping(mapping)
                .locationReference("location-ref-1").category("SOURCE_VERSION_GAP").status("IN_PROGRESS").build();
        OtaReconciliationDecision resync = OtaReconciliationDecision.record(workItem, mapping, "KLOOK",
                "location-ref-1", "5", "MANAGER", "RESYNC_REQUESTED", "RECOVERY", "resync", "QUEUED",
                "00000000-0000-4000-8000-000000000001", "00000000-0000-4000-8000-000000000002",
                "a".repeat(64), "b".repeat(64), 9L);
        when(workItemRepository.findByIdForUpdate(91L)).thenReturn(Optional.of(workItem));
        when(decisionRepository.findTopByWorkItem_IdAndActionCodeOrderByRecordedAtDescIdDesc(91L,
                "RESYNC_REQUESTED")).thenReturn(Optional.of(resync));
        when(outboundChangeRepository.findAllByMapping_IdAndSourceVersion(12L, 9L)).thenReturn(List.of(
                outbound(OtaChangeKind.SCHEDULE, "DELIVERED"), outbound(OtaChangeKind.AVAILABILITY, "QUEUED"),
                outbound(OtaChangeKind.SALE_STATE, "DELIVERED")));

        assertThatThrownBy(() -> service.resolve(91L, "ACCEPTED", "confirm resync", "idem-key-0003",
                actor(ActorRole.MANAGER, 5L)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("must be delivered");

        assertThat(mapping.getStatus()).isEqualTo(OtaMappingStatus.SYNC_SUSPENDED);
        assertThat(workItem.getStatus()).isEqualTo("IN_PROGRESS");
        verify(operationsResyncFacade, never()).resync("session-ref-1");
    }

    @Test
    void acceptedResyncReturnsMappingOnlyToSalesClosedAfterProviderAndOperationsConfirmation() {
        stubDecisionSave();
        when(workItemRepository.save(any(OtaReconciliationWorkItem.class))).thenAnswer(call -> call.getArgument(0));
        when(mappingRepository.save(any(OtaChannelMapping.class))).thenAnswer(call -> call.getArgument(0));
        mapping = mapping(OtaMappingStatus.SYNC_SUSPENDED);
        workItem = OtaReconciliationWorkItem.builder().id(91L).publicReference("00000000-0000-4000-8000-000000000091").mapping(mapping)
                .locationReference("location-ref-1").category("SOURCE_VERSION_GAP").status("IN_PROGRESS").build();
        OtaReconciliationDecision resync = OtaReconciliationDecision.record(workItem, mapping, "KLOOK",
                "location-ref-1", "5", "MANAGER", "RESYNC_REQUESTED", "RECOVERY", "resync", "QUEUED",
                "00000000-0000-4000-8000-000000000001", "00000000-0000-4000-8000-000000000002",
                "a".repeat(64), "b".repeat(64), 9L);
        when(workItemRepository.findByIdForUpdate(91L)).thenReturn(Optional.of(workItem));
        when(decisionRepository.findTopByWorkItem_IdAndActionCodeOrderByRecordedAtDescIdDesc(91L,
                "RESYNC_REQUESTED")).thenReturn(Optional.of(resync));
        when(outboundChangeRepository.findAllByMapping_IdAndSourceVersion(12L, 9L)).thenReturn(List.of(
                outbound(OtaChangeKind.SCHEDULE, "DELIVERED"), outbound(OtaChangeKind.AVAILABILITY, "DELIVERED"),
                outbound(OtaChangeKind.SALE_STATE, "DELIVERED")));
        when(operationsResyncFacade.resync("session-ref-1")).thenReturn(snapshot(9L));

        OtaReconciliationActionResult result = service.resolve(91L, "ACCEPTED", "provider confirmed resync",
                "idem-key-0004", actor(ActorRole.MANAGER, 5L));

        assertThat(result.status()).isEqualTo("RESOLVED");
        assertThat(mapping.getStatus()).isEqualTo(OtaMappingStatus.SALES_CLOSED);
        assertThat(workItem.getDecision()).isEqualTo("ACCEPTED");
    }

    private OtaOutboundChange outbound(OtaChangeKind kind, String status) {
        return OtaOutboundChange.builder().mapping(mapping).sourceVersion(9L).sourceChangeReference("change-9")
                .changeKind(kind).status(status).build();
    }

    private void stubDecisionSave() {
        when(decisionRepository.saveAndFlush(any(OtaReconciliationDecision.class))).thenAnswer(call -> {
            OtaReconciliationDecision decision = call.getArgument(0);
            latestDecision.set(decision);
            return decision;
        });
    }

    private static OtaChannelMapping mapping(OtaMappingStatus status) {
        return OtaChannelMapping.builder().id(12L).publicReference("00000000-0000-4000-8000-000000000012")
                .connection(OtaChannelConnection.builder().providerCode(OtaProvider.KLOOK).status("ACTIVE").build())
                .offerReference("offer-1").sessionReference("session-ref-1").locationReference("location-ref-1")
                .externalProductId("product-1").externalOptionId("option-1").status(status).build();
    }

    private static AccountSummary actor(ActorRole role, long id) {
        return new AccountSummary(id, role, ActorStatus.ACTIVE, null, null, null);
    }

    private static WorkshopSessionOtaSnapshot snapshot(long version) {
        return new WorkshopSessionOtaSnapshot("session-ref-1", "location-ref-1",
                Instant.parse("2026-11-01T10:00:00Z"), Instant.parse("2026-11-01T11:00:00Z"),
                3, "PUBLISHED", version, "change-" + version, Instant.parse("2026-10-30T10:00:00Z"));
    }
}
