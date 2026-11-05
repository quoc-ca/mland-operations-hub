package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.exception.OtaMappingLifecycleException;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.exception.OtaResourceNotFoundException;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.integration.OtaAuditCorrelationService;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OtaReconciliationService {
    private static final String OPEN = "OPEN";
    private static final String IN_PROGRESS = "IN_PROGRESS";
    private static final String RESOLVED = "RESOLVED";
    private static final String ESCALATED = "ESCALATED";
    private static final String PREPARATION_SYNC = "PREPARATION_SYNC";
    private static final String RESYNC_REQUESTED = "RESYNC_REQUESTED";
    private static final EnumSet<OtaChangeKind> COMPLETE_SNAPSHOT_KINDS =
            EnumSet.of(OtaChangeKind.SCHEDULE, OtaChangeKind.AVAILABILITY, OtaChangeKind.SALE_STATE);

    private final OtaChannelMappingRepository mappingRepository;
    private final OtaReconciliationWorkItemRepository workItemRepository;
    private final OtaReconciliationDecisionRepository decisionRepository;
    private final OtaOutboundChangeRepository outboundChangeRepository;
    private final OtaSyncDeliveryService syncDeliveryService;
    private final WorkshopOtaResyncFacade operationsResyncFacade;
    private final OtaAuditCorrelationService auditCorrelationService;

    @Transactional
    public List<OtaReconciliationWorkItemView> listWorkItems(String status, AccountSummary actor) {
        requireManagerOrAdmin(actor);
        String normalizedStatus = status == null || status.isBlank() ? null : status.trim();
        if (normalizedStatus != null && !List.of(OPEN, IN_PROGRESS, RESOLVED, ESCALATED).contains(normalizedStatus)) {
            throw new IllegalArgumentException("Unsupported reconciliation status");
        }
        List<OtaReconciliationWorkItem> items = normalizedStatus == null
                ? workItemRepository.findMappingLinkedOrderByCreatedAtAsc()
                : workItemRepository.findMappingLinkedByStatusOrderByCreatedAtAsc(normalizedStatus);
        return items.stream().map(item -> new OtaReconciliationWorkItemView(
                UUID.fromString(item.getPublicReference()),
                UUID.fromString(item.getMapping().getPublicReference()),
                item.getStatus(),
                UUID.fromString(item.getAuditReference()),
                item.getCategory())).toList();
    }

    @Transactional
    public OtaOperationAccepted requestSync(Long mappingId, String mode, String reason,
                                            String idempotencyKey, AccountSummary actor) {
        validateAction(mappingId, reason, idempotencyKey);
        requireManagerOrAdmin(actor);
        boolean fullResync = "FULL_RESYNC".equals(mode);
        if (!fullResync && !"PREPARATION".equals(mode)) {
            throw new IllegalArgumentException("Sync mode must be PREPARATION or FULL_RESYNC");
        }
        if (!fullResync && actor.role() != ActorRole.MANAGER) {
            throw new OtaMappingLifecycleException("Preparation sync requires an active Manager");
        }
        String actionCode = fullResync ? RESYNC_REQUESTED : PREPARATION_SYNC;
        String actionScope = fullResync ? "RECOVERY" : "BUSINESS";
        String fingerprint = fingerprint(actionCode, mappingId.toString(), reason);
        OtaReconciliationDecision replay = prior(actor, idempotencyKey, fingerprint);
        if (replay != null) {
            return accepted(replay);
        }

        OtaChannelMapping mapping = mappingRepository.findByIdForUpdate(mappingId)
                .orElseThrow(() -> new OtaMappingLifecycleException("OTA channel mapping was not found"));
        OtaReconciliationWorkItem item = null;
        if (fullResync) {
            if (mapping.getStatus() != OtaMappingStatus.SYNC_SUSPENDED) {
                throw new OtaMappingLifecycleException("Full resync requires a suspended mapping");
            }
            item = workItemRepository.findFirstByMapping_IdAndStatusOrderByCreatedAtAsc(mappingId, OPEN)
                    .orElseGet(() -> workItemRepository.saveAndFlush(newWorkItem(mapping, "MANUAL_FULL_RESYNC")));
            item.setStatus(IN_PROGRESS);
            item.setDecision(RESYNC_REQUESTED);
        } else if (mapping.getStatus() == OtaMappingStatus.DRAFT
                || mapping.getStatus() == OtaMappingStatus.SALES_CLOSED) {
            transition(mapping::startPreparationSync);
            mappingRepository.save(mapping);
        } else if (mapping.getStatus() != OtaMappingStatus.PREPARATION_SYNC) {
            throw new OtaMappingLifecycleException("Preparation sync is not allowed in the current mapping state");
        }

        WorkshopSessionOtaSnapshot snapshot = authoritativeSnapshot(mapping);
        syncDeliveryService.queueAuthoritativeSnapshot(mapping, snapshot);
        String operationReference = UUID.randomUUID().toString();
        String correlationReference = auditCorrelationService.newCorrelationReference();
        OtaReconciliationDecision decision = saveDecision(item, mapping, actor, actionCode, actionScope,
                reason, "QUEUED", operationReference, correlationReference, idempotencyKey,
                fingerprint, snapshot.sourceVersion());
        if (item != null) {
            item.setDecision(RESYNC_REQUESTED);
            item.setResolutionReference(decision.getDecisionReference());
            item.setAuditReference(correlationReference);
            workItemRepository.save(item);
        }
        auditCorrelationService.record(OtaAuditCorrelationService.IntegrationAction.SNAPSHOT_CONSUMED,
                mapping.getConnection().getProviderCode(), mapping.getId(), correlationReference,
                OtaAuditCorrelationService.IntegrationOutcome.ACCEPTED);
        return accepted(decision);
    }

    @Transactional
    public OtaReconciliationActionResult resolve(Long workItemId, String resolution, String reason,
                                                  String idempotencyKey, AccountSummary actor) {
        validateAction(workItemId, reason, idempotencyKey);
        requireManagerOrAdmin(actor);
        if (!List.of(RESYNC_REQUESTED, "ACCEPTED", "REJECTED", ESCALATED).contains(resolution)) {
            throw new IllegalArgumentException("Unsupported reconciliation resolution");
        }
        String fingerprint = fingerprint("RESOLUTION", workItemId.toString(), resolution, reason);
        OtaReconciliationDecision replay = prior(actor, idempotencyKey, fingerprint);
        if (replay != null) {
            return actionResult(replay.getWorkItem(), replay.getOutcome(), replay.getCorrelationReference());
        }
        OtaReconciliationWorkItem item = workItemRepository.findByIdForUpdate(workItemId)
                .orElseThrow(() -> new OtaMappingLifecycleException("Reconciliation work item was not found"));
        if (item.getMapping() == null) {
            throw new OtaMappingLifecycleException("This reconciliation action requires a mapping-linked work item");
        }
        if (RESOLVED.equals(item.getStatus()) || ESCALATED.equals(item.getStatus())) {
            throw new OtaMappingLifecycleException("Reconciliation work item is no longer actionable");
        }
        OtaChannelMapping mapping = item.getMapping();
        Long sourceVersion = null;
        String actionScope = actor.role() == ActorRole.ADMIN ? "RECOVERY" : "RECONCILIATION";
        String outcome;

        if (RESYNC_REQUESTED.equals(resolution)) {
            if (mapping == null || mapping.getStatus() != OtaMappingStatus.SYNC_SUSPENDED) {
                throw new OtaMappingLifecycleException("Only a suspended mapping can request full resync");
            }
            WorkshopSessionOtaSnapshot snapshot = authoritativeSnapshot(mapping);
            syncDeliveryService.queueAuthoritativeSnapshot(mapping, snapshot);
            sourceVersion = snapshot.sourceVersion();
            item.setStatus(IN_PROGRESS);
            item.setDecision(RESYNC_REQUESTED);
            outcome = IN_PROGRESS;
        } else if ("ACCEPTED".equals(resolution)) {
            if (mapping != null && mapping.getStatus() == OtaMappingStatus.SYNC_SUSPENDED) {
                OtaReconciliationDecision resync = decisionRepository
                        .findTopByWorkItem_IdAndActionCodeOrderByRecordedAtDescIdDesc(workItemId, RESYNC_REQUESTED)
                        .orElseThrow(() -> new OtaMappingLifecycleException("A full resync must be requested first"));
                sourceVersion = resync.getSourceVersion();
                confirmDeliveredResync(item, mapping, sourceVersion);
                mapping.returnToSalesClosedAfterConfirmedFullResync();
                mappingRepository.save(mapping);
            }
            item.setStatus(RESOLVED);
            item.setDecision("ACCEPTED");
            outcome = RESOLVED;
        } else if ("REJECTED".equals(resolution)) {
            suspendAffectedMapping(mapping);
            item.setStatus(OPEN);
            item.setDecision("REJECTED");
            outcome = OPEN;
        } else {
            suspendAffectedMapping(mapping);
            item.setStatus(ESCALATED);
            item.setDecision(ESCALATED);
            outcome = ESCALATED;
        }

        UUID correlation = UUID.randomUUID();
        OtaReconciliationDecision decision = saveDecision(item, mapping, actor, resolution, actionScope,
                reason, outcome, UUID.randomUUID().toString(), correlation.toString(), idempotencyKey,
                fingerprint, sourceVersion);
        item.setResolutionReference(decision.getDecisionReference());
        item.setAuditReference(correlation.toString());
        workItemRepository.save(item);
        auditCorrelationService.record(OtaAuditCorrelationService.IntegrationAction.RECONCILIATION_REQUIRED,
                provider(item, mapping), mapping == null ? null : mapping.getId(), correlation.toString(),
                "ACCEPTED".equals(resolution) ? OtaAuditCorrelationService.IntegrationOutcome.ACCEPTED
                        : OtaAuditCorrelationService.IntegrationOutcome.REJECTED);
        return actionResult(item, outcome, correlation.toString());
    }

    @Transactional
    public OtaReconciliationActionResult resolveByPublicReference(String workItemPublicReference, String resolution,
            String reason, String idempotencyKey, AccountSummary actor) {
        requireManagerOrAdmin(actor);
        UUID reference;
        try {
            reference = UUID.fromString(OtaPublicReference.require(workItemPublicReference));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Work item reference must be a UUID");
        }
        OtaReconciliationWorkItem item = workItemRepository
                .findMappingLinkedByPublicReferenceForUpdate(reference.toString())
                .orElseThrow(() -> new OtaResourceNotFoundException("Mapping-linked reconciliation item was not found"));
        return resolve(item.getId(), resolution, reason, idempotencyKey, actor);
    }

    private void suspendAffectedMapping(OtaChannelMapping mapping) {
        if (mapping != null && mapping.getStatus() != OtaMappingStatus.SYNC_SUSPENDED
                && mapping.getStatus() != OtaMappingStatus.DISABLED
                && mapping.getStatus() != OtaMappingStatus.DRAFT) {
            mapping.suspendAfterSyncFailure();
            mappingRepository.save(mapping);
        }
    }

    private OtaReconciliationActionResult actionResult(OtaReconciliationWorkItem item, String status,
                                                        String correlationReference) {
        if (item == null) {
            throw new OtaMappingLifecycleException("Reconciliation action result is unavailable");
        }
        return new OtaReconciliationActionResult(UUID.fromString(item.getPublicReference()),
                UUID.fromString(item.getMapping().getPublicReference()), status,
                UUID.fromString(correlationReference), item.getCategory());
    }

    private void confirmDeliveredResync(OtaReconciliationWorkItem item, OtaChannelMapping mapping,
                                         Long sourceVersion) {
        if (sourceVersion == null) {
            throw new OtaMappingLifecycleException("Full resync source version is missing");
        }
        List<OtaOutboundChange> changes = outboundChangeRepository
                .findAllByMapping_IdAndSourceVersion(mapping.getId(), sourceVersion);
        if (changes.size() != COMPLETE_SNAPSHOT_KINDS.size()
                || !changes.stream().map(OtaOutboundChange::getChangeKind).collect(java.util.stream.Collectors.toSet())
                .equals(COMPLETE_SNAPSHOT_KINDS)
                || changes.stream().anyMatch(change -> !"DELIVERED".equals(change.getStatus()))) {
            throw new OtaMappingLifecycleException("All authoritative snapshot kinds must be delivered before acceptance");
        }
        WorkshopSessionOtaSnapshot current = authoritativeSnapshot(mapping);
        if (current.sourceVersion() != sourceVersion) {
            throw new OtaMappingLifecycleException("Operations has a newer snapshot; request another full resync");
        }
    }

    private WorkshopSessionOtaSnapshot authoritativeSnapshot(OtaChannelMapping mapping) {
        WorkshopSessionOtaSnapshot snapshot = operationsResyncFacade.resync(mapping.getSessionReference());
        if (snapshot == null || !mapping.getSessionReference().equals(snapshot.sessionReference())
                || !mapping.getLocationReference().equals(snapshot.locationReference())
                || snapshot.sourceVersion() < 1) {
            throw new OtaMappingLifecycleException("Operations returned an invalid authoritative snapshot");
        }
        return snapshot;
    }

    private OtaReconciliationWorkItem newWorkItem(OtaChannelMapping mapping, String category) {
        return OtaReconciliationWorkItem.builder().mapping(mapping)
                .locationReference(mapping.getLocationReference()).category(category).status(OPEN).build();
    }

    private OtaReconciliationDecision saveDecision(OtaReconciliationWorkItem item, OtaChannelMapping mapping,
            AccountSummary actor, String action, String scope, String reason, String outcome,
            String operationReference, String correlationReference, String idempotencyKey,
            String fingerprint, Long sourceVersion) {
        OtaProvider provider = provider(item, mapping);
        String location = mapping != null ? mapping.getLocationReference()
                : item != null ? item.getLocationReference() : null;
        if (provider == null || location == null) {
            throw new OtaMappingLifecycleException("The affected provider or location cannot be resolved yet");
        }
        OtaReconciliationDecision decision = OtaReconciliationDecision.record(item, mapping,
                provider.name(), location, actor.accountId().toString(), actor.role().name(), action, scope,
                reason, outcome, operationReference, correlationReference, hash(idempotencyKey), fingerprint,
                sourceVersion);
        return decisionRepository.saveAndFlush(decision);
    }

    private OtaProvider provider(OtaReconciliationWorkItem item, OtaChannelMapping mapping) {
        if (mapping != null) {
            return mapping.getConnection().getProviderCode();
        }
        if (item != null && item.getInboundEvent() != null) {
            return item.getInboundEvent().getConnection().getProviderCode();
        }
        return null;
    }

    private OtaReconciliationDecision prior(AccountSummary actor, String key, String fingerprint) {
        String keyHash = hash(key);
        OtaReconciliationDecision prior = decisionRepository
                .findByActorAccountReferenceAndIdempotencyKeyHash(actor.accountId().toString(), keyHash)
                .orElse(null);
        if (prior != null && !prior.getRequestFingerprint().equals(fingerprint)) {
            throw new OtaMappingLifecycleException("Idempotency key was already used for a different request");
        }
        return prior;
    }

    private static OtaOperationAccepted accepted(OtaReconciliationDecision decision) {
        return new OtaOperationAccepted(UUID.fromString(decision.getOperationReference()),
                UUID.fromString(decision.getCorrelationReference()), decision.getOutcome());
    }

    private static void requireManagerOrAdmin(AccountSummary actor) {
        if (actor == null || actor.accountId() == null || actor.status() != ActorStatus.ACTIVE
                || (actor.role() != ActorRole.MANAGER && actor.role() != ActorRole.ADMIN)) {
            throw new OtaMappingLifecycleException("An active Manager or Admin is required");
        }
    }

    private static void requireManager(AccountSummary actor) {
        requireManagerOrAdmin(actor);
        if (actor.role() != ActorRole.MANAGER) {
            throw new OtaMappingLifecycleException("This business action requires an active Manager");
        }
    }

    private static void validateAction(Long subjectId, String reason, String key) {
        if (subjectId == null || subjectId < 1 || reason == null || reason.isBlank()
                || reason.trim().length() > 1000 || key == null || key.trim().length() < 8
                || key.trim().length() > 128) {
            throw new IllegalArgumentException("A subject, reason and valid Idempotency-Key are required");
        }
    }

    private static void transition(Runnable action) {
        try {
            action.run();
        } catch (IllegalStateException exception) {
            throw new OtaMappingLifecycleException(exception.getMessage());
        }
    }

    private static String fingerprint(String... values) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                for (String value : values) {
                    byte[] part = value.getBytes(StandardCharsets.UTF_8);
                    output.writeInt(part.length);
                    output.write(part);
                }
            }
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Unable to fingerprint OTA operation", exception);
        }
    }

    private static String hash(String value) {
        return fingerprint(value.trim());
    }
}
