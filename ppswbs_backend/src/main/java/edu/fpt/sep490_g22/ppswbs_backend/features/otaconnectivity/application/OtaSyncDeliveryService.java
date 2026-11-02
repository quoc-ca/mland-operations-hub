package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application;

import com.fasterxml.jackson.databind.node.ObjectNode;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.integration.OtaAuditCorrelationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelMapping;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelMappingRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChangeKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaDeliveryAttempt;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaDeliveryAttemptRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaMappingStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaOutboundChange;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaOutboundChangeRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaReconciliationWorkItem;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaReconciliationWorkItemRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.WorkshopSessionOtaSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Consumes accepted Operations snapshots into OTA-owned durable changes.
 * Provider calls are deliberately left to the asynchronous adapter worker.
 */
@Service
@RequiredArgsConstructor
public class OtaSyncDeliveryService {

    private static final String RECONCILIATION_CATEGORY = "INVENTORY_DELIVERY_FAILURE";
    private static final String SOURCE_GAP_CATEGORY = "SOURCE_VERSION_GAP";
    private static final String SOURCE_ORDER_CATEGORY = "SOURCE_ORDER_UNCERTAIN";
    private static final Pattern SAFE_FAILURE_CATEGORY = Pattern.compile("[A-Z0-9_]{1,64}");

    private final OtaChannelMappingRepository mappingRepository;
    private final OtaOutboundChangeRepository outboundChangeRepository;
    private final OtaDeliveryAttemptRepository deliveryAttemptRepository;
    private final OtaReconciliationWorkItemRepository reconciliationWorkItemRepository;
    private final OtaAuditCorrelationService auditCorrelationService;

    /**
     * Materializes each allowlisted part of a complete Operations snapshot once
     * per active mapping. A duplicate or older source version is a no-op.
     */
    @Transactional
    public int acceptSnapshot(WorkshopSessionOtaSnapshot snapshot) {
        validate(snapshot);
        List<OtaChannelMapping> mappings = mappingRepository.findAllBySessionReference(snapshot.sessionReference());
        int created = 0;
        for (OtaChannelMapping mapping : mappings) {
            if (!isPublishable(mapping) || !snapshot.locationReference().equals(mapping.getLocationReference())) {
                continue;
            }
            long latestVersion = outboundChangeRepository
                    .findTopByMappingIdOrderBySourceVersionDescIdDesc(mapping.getId())
                    .map(OtaOutboundChange::getSourceVersion)
                    .orElse(0L);
            if (snapshot.sourceVersion() <= latestVersion) {
                if (snapshot.sourceVersion() == latestVersion
                        && isConflictingSourceChange(mapping.getId(), snapshot)) {
                    suspendForSourceUncertainty(mapping, SOURCE_ORDER_CATEGORY);
                }
                continue;
            }
            if (latestVersion > 0 && snapshot.sourceVersion() > latestVersion + 1) {
                suspendForSourceUncertainty(mapping, SOURCE_GAP_CATEGORY);
                continue;
            }
            for (Map.Entry<OtaChangeKind, ObjectNode> change : canonicalChanges(snapshot).entrySet()) {
                var existing = outboundChangeRepository.findByMappingIdAndSourceVersionAndChangeKind(
                        mapping.getId(), snapshot.sourceVersion(), change.getKey());
                if (existing.isPresent()) {
                    continue;
                }
                outboundChangeRepository.save(OtaOutboundChange.builder()
                        .mapping(mapping)
                        .sourceVersion(snapshot.sourceVersion())
                        .sourceChangeReference(snapshot.changeReference())
                        .changeKind(change.getKey())
                        .canonicalSnapshot(change.getValue())
                        .status(OtaOutboundStatus.QUEUED.name())
                        .acceptedAt(Instant.now())
                        .build());
                created++;
            }
        }
        return created;
    }

    /** Queues one authoritative complete snapshot for a specifically requested mapping recovery. */
    @Transactional
    public int queueAuthoritativeSnapshot(OtaChannelMapping mapping, WorkshopSessionOtaSnapshot snapshot) {
        validate(snapshot);
        if (mapping == null || mapping.getId() == null
                || !snapshot.sessionReference().equals(mapping.getSessionReference())
                || !snapshot.locationReference().equals(mapping.getLocationReference())) {
            throw new IllegalArgumentException("Authoritative snapshot does not match the requested mapping");
        }
        long latestVersion = outboundChangeRepository
                .findTopByMappingIdOrderBySourceVersionDescIdDesc(mapping.getId())
                .map(OtaOutboundChange::getSourceVersion)
                .orElse(0L);
        if (snapshot.sourceVersion() < latestVersion) {
            throw new IllegalStateException("Authoritative snapshot is older than the last queued change");
        }

        int queued = 0;
        for (Map.Entry<OtaChangeKind, ObjectNode> change : canonicalChanges(snapshot).entrySet()) {
            var existing = outboundChangeRepository.findByMappingIdAndSourceVersionAndChangeKind(
                    mapping.getId(), snapshot.sourceVersion(), change.getKey());
            if (existing.isPresent()) {
                OtaOutboundChange current = existing.get();
                if (!snapshot.changeReference().equals(current.getSourceChangeReference())
                        || !change.getValue().equals(current.getCanonicalSnapshot())) {
                    throw new IllegalStateException("Authoritative snapshot conflicts with a recorded source version");
                }
                current.setStatus(OtaOutboundStatus.QUEUED.name());
                outboundChangeRepository.save(current);
            } else {
                outboundChangeRepository.save(OtaOutboundChange.builder()
                        .mapping(mapping)
                        .sourceVersion(snapshot.sourceVersion())
                        .sourceChangeReference(snapshot.changeReference())
                        .changeKind(change.getKey())
                        .canonicalSnapshot(change.getValue())
                        .status(OtaOutboundStatus.QUEUED.name())
                        .acceptedAt(Instant.now())
                        .build());
            }
            queued++;
        }
        return queued;
    }

    private boolean isConflictingSourceChange(Long mappingId, WorkshopSessionOtaSnapshot snapshot) {
        return outboundChangeRepository.findByMappingIdAndSourceVersionAndChangeKind(
                        mappingId, snapshot.sourceVersion(), OtaChangeKind.SCHEDULE)
                .map(existing -> !snapshot.changeReference().equals(existing.getSourceChangeReference()))
                .orElse(false);
    }

    private void suspendForSourceUncertainty(OtaChannelMapping mapping, String category) {
        if (mapping.getStatus() != OtaMappingStatus.SYNC_SUSPENDED
                && mapping.getStatus() != OtaMappingStatus.DISABLED
                && mapping.getStatus() != OtaMappingStatus.DRAFT) {
            mapping.suspendAfterSyncFailure();
            mappingRepository.save(mapping);
        }
        String correlationReference = auditCorrelationService.newCorrelationReference();
        if (!reconciliationWorkItemRepository.existsByMapping_IdAndCategoryAndStatus(
                mapping.getId(), category, "OPEN")) {
            reconciliationWorkItemRepository.save(OtaReconciliationWorkItem.builder()
                    .mapping(mapping)
                    .locationReference(mapping.getLocationReference())
                    .category(category)
                    .status("OPEN")
                    .auditReference(correlationReference)
                    .build());
        }
        auditCorrelationService.record(OtaAuditCorrelationService.IntegrationAction.RECONCILIATION_REQUIRED,
                mapping.getConnection().getProviderCode(), mapping.getId(), correlationReference,
                OtaAuditCorrelationService.IntegrationOutcome.REJECTED);
    }

    /**
     * Records one adapter result. The adapter reports EXHAUSTED only after its
     * configured provider-specific retry policy is exhausted.
     */
    @Transactional
    public void recordDeliveryResult(Long outboundChangeId, OtaDeliveryResult result) {
        validate(result);
        OtaOutboundChange change = outboundChangeRepository.findById(outboundChangeId)
                .orElseThrow(() -> new IllegalArgumentException("Outbound change not found"));
        if (deliveryAttemptRepository.findByOutboundChangeIdAndAttemptNumber(
                outboundChangeId, result.attemptNumber()).isPresent()) {
            return;
        }

        String safeCorrelation = auditCorrelationService.safeCorrelationReference(result.correlationReference());
        OtaDeliveryAttempt attempt = OtaDeliveryAttempt.builder()
                .outboundChange(change)
                .attemptNumber(result.attemptNumber())
                .startedAt(result.startedAt())
                .completedAt(result.completedAt())
                .outcome(result.outcome().name())
                .providerCorrelationReference(safeCorrelation)
                .failureCategory(safeFailureCategory(result.failureCategory()))
                .build();
        deliveryAttemptRepository.save(attempt);
        change.setStatus(outboundStatus(result.outcome()).name());
        outboundChangeRepository.save(change);

        if (result.outcome() == OtaDeliveryOutcome.DELIVERED) {
            completePreparationAfterConfirmedSnapshot(change);
        }

        if (result.outcome() == OtaDeliveryOutcome.EXHAUSTED) {
            OtaChannelMapping mapping = change.getMapping();
            if (mapping.getStatus() != OtaMappingStatus.DISABLED) {
                mapping.suspendAfterSyncFailure();
                mappingRepository.save(mapping);
                reconciliationWorkItemRepository.save(OtaReconciliationWorkItem.builder()
                        .mapping(mapping)
                        .locationReference(mapping.getLocationReference())
                        .category(RECONCILIATION_CATEGORY)
                        .status("OPEN")
                        .auditReference(safeCorrelation)
                        .build());
            }
        }
    }

    private void completePreparationAfterConfirmedSnapshot(OtaOutboundChange deliveredChange) {
        OtaChannelMapping mapping = deliveredChange.getMapping();
        if (mapping.getStatus() != OtaMappingStatus.PREPARATION_SYNC) {
            return;
        }
        List<OtaOutboundChange> changes = outboundChangeRepository.findAllByMapping_IdAndSourceVersion(
                mapping.getId(), deliveredChange.getSourceVersion());
        if (changes.size() == 3
                && changes.stream().map(OtaOutboundChange::getChangeKind)
                .collect(java.util.stream.Collectors.toSet())
                .equals(java.util.EnumSet.of(OtaChangeKind.SCHEDULE, OtaChangeKind.AVAILABILITY,
                        OtaChangeKind.SALE_STATE))
                && changes.stream().allMatch(change -> OtaOutboundStatus.DELIVERED.name().equals(change.getStatus()))) {
            mapping.completePreparationSync();
            mappingRepository.save(mapping);
        }
    }

    private static Map<OtaChangeKind, ObjectNode> canonicalChanges(WorkshopSessionOtaSnapshot snapshot) {
        EnumMap<OtaChangeKind, ObjectNode> changes = new EnumMap<>(OtaChangeKind.class);

        ObjectNode schedule = new ObjectNode(com.fasterxml.jackson.databind.node.JsonNodeFactory.instance);
        schedule.put("sourceVersion", snapshot.sourceVersion());
        schedule.put("sourceChangeReference", snapshot.changeReference());
        schedule.put("effectiveAt", snapshot.effectiveAt().toString());
        schedule.put("scheduledStart", snapshot.scheduledStart().toString());
        schedule.put("scheduledEnd", snapshot.scheduledEnd().toString());
        changes.put(OtaChangeKind.SCHEDULE, schedule);

        ObjectNode availability = new ObjectNode(com.fasterxml.jackson.databind.node.JsonNodeFactory.instance);
        availability.put("sourceVersion", snapshot.sourceVersion());
        availability.put("sourceChangeReference", snapshot.changeReference());
        availability.put("effectiveAt", snapshot.effectiveAt().toString());
        availability.put("sellableAvailability", snapshot.sellableAvailability());
        changes.put(OtaChangeKind.AVAILABILITY, availability);

        ObjectNode saleState = new ObjectNode(com.fasterxml.jackson.databind.node.JsonNodeFactory.instance);
        saleState.put("sourceVersion", snapshot.sourceVersion());
        saleState.put("sourceChangeReference", snapshot.changeReference());
        saleState.put("effectiveAt", snapshot.effectiveAt().toString());
        saleState.put("saleState", snapshot.saleState());
        changes.put(OtaChangeKind.SALE_STATE, saleState);
        return changes;
    }

    private static boolean isPublishable(OtaChannelMapping mapping) {
        return mapping.getStatus() == OtaMappingStatus.PREPARATION_SYNC
                || mapping.getStatus() == OtaMappingStatus.SALES_CLOSED
                || mapping.getStatus() == OtaMappingStatus.BOOKING_ENABLED;
    }

    private static OtaOutboundStatus outboundStatus(OtaDeliveryOutcome outcome) {
        return switch (outcome) {
            case DELIVERED -> OtaOutboundStatus.DELIVERED;
            case RETRYABLE -> OtaOutboundStatus.RETRYABLE;
            case EXHAUSTED -> OtaOutboundStatus.EXHAUSTED;
        };
    }

    private static String safeFailureCategory(String candidate) {
        return candidate != null && SAFE_FAILURE_CATEGORY.matcher(candidate).matches()
                ? candidate
                : "UNCLASSIFIED";
    }

    private static void validate(WorkshopSessionOtaSnapshot snapshot) {
        if (snapshot == null
                || blank(snapshot.sessionReference())
                || blank(snapshot.locationReference())
                || blank(snapshot.changeReference())
                || snapshot.sourceVersion() < 1
                || snapshot.sellableAvailability() < 0
                || snapshot.scheduledStart() == null
                || snapshot.scheduledEnd() == null
                || snapshot.effectiveAt() == null
                || !snapshot.scheduledEnd().isAfter(snapshot.scheduledStart())) {
            throw new IllegalArgumentException("Invalid Operations OTA snapshot");
        }
    }

    private static void validate(OtaDeliveryResult result) {
        if (result == null || result.attemptNumber() < 1 || result.outcome() == null
                || result.startedAt() == null || result.completedAt() == null
                || result.completedAt().isBefore(result.startedAt())) {
            throw new IllegalArgumentException("Invalid OTA delivery result");
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
