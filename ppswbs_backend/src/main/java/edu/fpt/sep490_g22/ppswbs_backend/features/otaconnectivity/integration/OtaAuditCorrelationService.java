package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.integration;

import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Emits minimized OTA integration audit log entries and provides server-owned
 * correlation references. Raw provider data is deliberately not accepted by
 * this boundary.
 */
@Service
@Slf4j
public class OtaAuditCorrelationService {

    public String newCorrelationReference() {
        return UUID.randomUUID().toString();
    }

    /**
     * Keep only a canonical UUID correlation value. Missing or untrusted values
     * are replaced with a server-generated reference before they reach logs.
     */
    public String safeCorrelationReference(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return newCorrelationReference();
        }
        try {
            return UUID.fromString(candidate.trim()).toString();
        } catch (IllegalArgumentException exception) {
            return newCorrelationReference();
        }
    }

    public void record(
            IntegrationAction action,
            OtaProvider provider,
            Long mappingId,
            String correlationCandidate,
            IntegrationOutcome outcome
    ) {
        String correlationReference = safeCorrelationReference(correlationCandidate);
        log.info("OTA integration audit action={} provider={} mappingId={} correlationReference={} outcome={}",
                action, provider, mappingId, correlationReference, outcome);
    }

    public enum IntegrationAction {
        INBOUND_EVENT_RECEIVED,
        INBOUND_EVENT_PROCESSED,
        SNAPSHOT_CONSUMED,
        OUTBOUND_DELIVERY_ATTEMPTED,
        OUTBOUND_DELIVERY_COMPLETED,
        SALES_CLOSE_REQUESTED,
        RECONCILIATION_REQUIRED
    }

    public enum IntegrationOutcome {
        ACCEPTED,
        REJECTED,
        RETRYABLE_FAILURE,
        PERMANENT_FAILURE
    }
}
