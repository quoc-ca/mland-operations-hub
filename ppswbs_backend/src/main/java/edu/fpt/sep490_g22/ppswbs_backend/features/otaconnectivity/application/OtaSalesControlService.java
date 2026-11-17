package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.exception.OtaMappingLifecycleException;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.exception.OtaResourceNotFoundException;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.integration.OtaAuditCorrelationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelMapping;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelMappingRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaControlOutboxCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaControlOutboxRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaMappingStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaReconciliationDecision;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaReconciliationDecisionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaReconciliationWorkItem;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaReconciliationWorkItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/** Manager sales-control actions, persisted independently from Operations snapshots. */
@Service
@RequiredArgsConstructor
public class OtaSalesControlService {
    private final OtaChannelMappingRepository mappingRepository;
    private final OtaControlOutboxRepository controlOutboxRepository;
    private final OtaReconciliationDecisionRepository decisionRepository;
    private final OtaAuditCorrelationService auditCorrelationService;
    private final OtaReconciliationWorkItemRepository workItemRepository;

    /** Same category as an exhausted inventory change, so the reconciliation filter needs no new value. */
    static final String DELIVERY_FAILURE_CATEGORY = "INVENTORY_DELIVERY_FAILURE";

    /**
     * 003.4 T120 (FR-004a, FR-004c): records the final provider answer for one locked, queued sales-close command.
     * The Manager decision stays immutable; only the outbox projection changes. On exhaustion the mapping stays
     * sales-closed (Mland already refuses new OTA bookings) and one open reconciliation work item is ensured.
     */
    @Transactional
    public void recordSalesCloseDelivery(OtaControlOutboxCommand command, OtaDeliveryResult result) {
        if (command == null || result == null || result.outcome() == null) {
            throw new IllegalArgumentException("A control command and a delivery result are required");
        }
        OtaChannelMapping mapping = command.getMapping();
        String correlation = auditCorrelationService.safeCorrelationReference(command.getCorrelationReference());
        if (result.outcome() == OtaDeliveryOutcome.DELIVERED) {
            command.recordDelivered(result.correlationReference());
            controlOutboxRepository.save(command);
            auditCorrelationService.record(OtaAuditCorrelationService.IntegrationAction.OUTBOUND_DELIVERY_COMPLETED,
                    mapping.getConnection().getProviderCode(), mapping.getId(), correlation,
                    OtaAuditCorrelationService.IntegrationOutcome.ACCEPTED);
            return;
        }
        if (result.outcome() != OtaDeliveryOutcome.EXHAUSTED) {
            throw new IllegalArgumentException("Only a final delivery result can be recorded on a control command");
        }
        command.recordExhausted(result.failureCategory(), result.correlationReference());
        controlOutboxRepository.save(command);
        if (!workItemRepository.existsByMapping_IdAndCategoryAndStatus(
                mapping.getId(), DELIVERY_FAILURE_CATEGORY, "OPEN")) {
            workItemRepository.save(OtaReconciliationWorkItem.builder()
                    .mapping(mapping)
                    .locationReference(mapping.getLocationReference())
                    .category(DELIVERY_FAILURE_CATEGORY)
                    .status("OPEN")
                    .auditReference(correlation)
                    .build());
        }
        auditCorrelationService.record(OtaAuditCorrelationService.IntegrationAction.RECONCILIATION_REQUIRED,
                mapping.getConnection().getProviderCode(), mapping.getId(), correlation,
                OtaAuditCorrelationService.IntegrationOutcome.REJECTED);
    }

    @Transactional
    public OtaOperationAccepted requestSalesClose(String mappingPublicReference, String reason,
                                                   String idempotencyKey, AccountSummary actor) {
        validate(mappingPublicReference, reason, idempotencyKey, actor);
        String mappingReference = UUID.fromString(mappingPublicReference).toString();
        String actorReference = actor.accountId().toString();
        String idempotencyHash = sha256(idempotencyKey.trim());
        String requestFingerprint = sha256("SALES_CLOSE\u0000" + mappingReference + "\u0000" + reason.trim());
        var previous = decisionRepository.findByActorAccountReferenceAndIdempotencyKeyHash(
                actorReference, idempotencyHash);
        if (previous.isPresent()) {
            OtaReconciliationDecision decision = previous.get();
            if (!"SALES_CLOSE".equals(decision.getActionCode())
                    || !requestFingerprint.equals(decision.getRequestFingerprint())) {
                throw new OtaMappingLifecycleException("Idempotency-Key was already used for another request");
            }
            return new OtaOperationAccepted(UUID.fromString(decision.getOperationReference()),
                    UUID.fromString(decision.getCorrelationReference()), decision.getOutcome());
        }

        OtaChannelMapping mapping = mappingRepository.findByPublicReferenceForUpdate(mappingReference)
                .orElseThrow(() -> new OtaResourceNotFoundException("OTA channel mapping was not found"));
        if (mapping.getStatus() != OtaMappingStatus.BOOKING_ENABLED
                && mapping.getStatus() != OtaMappingStatus.SALES_CLOSED) {
            throw new OtaMappingLifecycleException("Sales can only be closed for an enabled or sales-closed mapping");
        }

        String operationReference = UUID.randomUUID().toString();
        String correlationReference = auditCorrelationService.newCorrelationReference();
        mapping.closeSales();
        decisionRepository.save(OtaReconciliationDecision.record(null, mapping,
                mapping.getConnection().getProviderCode().name(), mapping.getLocationReference(), actorReference,
                actor.role().name(), "SALES_CLOSE", "BUSINESS", reason.trim(), "QUEUED", operationReference,
                correlationReference, idempotencyHash, requestFingerprint, null));
        controlOutboxRepository.save(OtaControlOutboxCommand.salesClose(operationReference, mapping,
                actorReference, reason.trim(), idempotencyHash, requestFingerprint, correlationReference));
        mappingRepository.save(mapping);
        auditCorrelationService.record(OtaAuditCorrelationService.IntegrationAction.SALES_CLOSE_REQUESTED,
                mapping.getConnection().getProviderCode(), mapping.getId(), correlationReference,
                OtaAuditCorrelationService.IntegrationOutcome.ACCEPTED);
        return new OtaOperationAccepted(UUID.fromString(operationReference), UUID.fromString(correlationReference),
                "QUEUED");
    }

    private static void validate(String mappingReference, String reason, String key, AccountSummary actor) {
        try {
            UUID.fromString(mappingReference);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("A valid mapping UUID is required");
        }
        if (reason == null || reason.isBlank() || reason.trim().length() > 1000
                || key == null || key.trim().length() < 8 || key.trim().length() > 128) {
            throw new IllegalArgumentException("A reason and valid Idempotency-Key are required");
        }
        if (actor == null || actor.accountId() == null || actor.status() != ActorStatus.ACTIVE
                || actor.role() != ActorRole.MANAGER) {
            throw new OtaMappingLifecycleException("Sales closure requires an active Manager");
        }
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
