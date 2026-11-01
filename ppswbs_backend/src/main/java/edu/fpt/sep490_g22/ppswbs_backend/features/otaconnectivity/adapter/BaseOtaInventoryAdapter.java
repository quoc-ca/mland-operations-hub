package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.OtaDeliveryOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.OtaDeliveryResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelMapping;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChangeKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaMappingStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaOutboundChange;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaProvider;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
public abstract class BaseOtaInventoryAdapter implements OtaInventoryAdapter {

    private static final int DEFAULT_MAX_RETRIES = 3;
    private static final Pattern SAFE_CORRELATION = Pattern.compile("^[a-zA-Z0-9-_]{1,64}$");
    private static final Set<String> FORBIDDEN_FIELDS = Set.of(
            "customer", "contact", "email", "phone", "ticket", "card", "payment", "token", "secret"
    );

    private static final Set<String> ALLOWED_SCHEDULE_FIELDS = Set.of(
            "sourceVersion", "sourceChangeReference", "scheduledStart", "scheduledEnd", "locationReference"
    );

    private static final Set<String> ALLOWED_AVAILABILITY_FIELDS = Set.of(
            "sourceVersion", "sourceChangeReference", "sellableAvailability"
    );

    private static final Set<String> ALLOWED_SALE_STATE_FIELDS = Set.of(
            "sourceVersion", "sourceChangeReference", "saleState"
    );

    private final OtaProviderTransport transport;
    private final ObjectMapper objectMapper;
    private final int maxRetries;

    protected BaseOtaInventoryAdapter(OtaProviderTransport transport, ObjectMapper objectMapper) {
        this(transport, objectMapper, DEFAULT_MAX_RETRIES);
    }

    protected BaseOtaInventoryAdapter(OtaProviderTransport transport, ObjectMapper objectMapper, int maxRetries) {
        this.transport = transport;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.maxRetries = maxRetries;
    }

    protected abstract String buildEndpointUrl(OtaChannelMapping mapping, OtaChangeKind changeKind);

    @Override
    public OtaDeliveryResult deliver(OtaChannelMapping mapping, OtaOutboundChange change, int attemptNumber) {
        Instant startedAt = Instant.now();

        if (mapping == null || mapping.getConnection() == null) {
            return new OtaDeliveryResult(attemptNumber, startedAt, Instant.now(),
                    OtaDeliveryOutcome.EXHAUSTED, sanitizeCorrelation(null), "INVALID_MAPPING");
        }

        if (mapping.getConnection().getProviderCode() != supportedProvider()) {
            return new OtaDeliveryResult(attemptNumber, startedAt, Instant.now(),
                    OtaDeliveryOutcome.EXHAUSTED, sanitizeCorrelation(null), "UNSUPPORTED_PROVIDER");
        }

        if (mapping.getStatus() == OtaMappingStatus.DISABLED) {
            return new OtaDeliveryResult(attemptNumber, startedAt, Instant.now(),
                    OtaDeliveryOutcome.EXHAUSTED, sanitizeCorrelation(null), "MAPPING_DISABLED");
        }

        // Validate allowlisted fields and default-deny forbidden data
        try {
            validateAllowlist(change);
        } catch (IllegalArgumentException ex) {
            log.warn("Outbound change contains non-allowlisted or forbidden fields: {}", ex.getMessage());
            return new OtaDeliveryResult(attemptNumber, startedAt, Instant.now(),
                    OtaDeliveryOutcome.EXHAUSTED, sanitizeCorrelation(null), "ALLOWLIST_VIOLATION");
        }

        String endpointUrl = buildEndpointUrl(mapping, change.getChangeKind());
        String payload = buildProviderPayload(mapping, change);
        String correlationHeader = UUID.randomUUID().toString();

        Map<String, String> headers = Map.of(
                "Content-Type", "application/json",
                "X-Correlation-Id", correlationHeader,
                "X-Provider-Auth-Ref", mapping.getConnection().getCredentialReference() != null
                        ? mapping.getConnection().getCredentialReference() : "ref-none"
        );

        OtaTransportRequest request = new OtaTransportRequest(endpointUrl, "POST", headers, payload);

        try {
            OtaTransportResponse response = transport.send(request);
            Instant completedAt = Instant.now();
            String safeCorrelation = sanitizeCorrelation(response.headers().get("X-Correlation-Id"));
            if (safeCorrelation == null) {
                safeCorrelation = sanitizeCorrelation(correlationHeader);
            }

            int status = response.statusCode();
            if (status >= 200 && status < 300) {
                return new OtaDeliveryResult(attemptNumber, startedAt, completedAt,
                        OtaDeliveryOutcome.DELIVERED, safeCorrelation, null);
            }

            if (status >= 400 && status < 500) {
                // Permanent client / auth / mapping error -> EXHAUSTED without futile retry
                return new OtaDeliveryResult(attemptNumber, startedAt, completedAt,
                        OtaDeliveryOutcome.EXHAUSTED, safeCorrelation, "CLIENT_ERROR_4XX");
            }

            // 5xx Server Error -> transient retryable if under limit
            OtaDeliveryOutcome outcome = attemptNumber < maxRetries
                    ? OtaDeliveryOutcome.RETRYABLE
                    : OtaDeliveryOutcome.EXHAUSTED;
            return new OtaDeliveryResult(attemptNumber, startedAt, completedAt,
                    outcome, safeCorrelation, "PROVIDER_5XX");

        } catch (Exception ex) {
            Instant completedAt = Instant.now();
            String safeCorrelation = sanitizeCorrelation(correlationHeader);
            OtaDeliveryOutcome outcome = attemptNumber < maxRetries
                    ? OtaDeliveryOutcome.RETRYABLE
                    : OtaDeliveryOutcome.EXHAUSTED;
            return new OtaDeliveryResult(attemptNumber, startedAt, completedAt,
                    outcome, safeCorrelation, "TRANSIENT_IO_ERROR");
        }
    }

    private void validateAllowlist(OtaOutboundChange change) {
        JsonNode snapshot = change.getCanonicalSnapshot();
        if (snapshot == null || !snapshot.isObject()) {
            throw new IllegalArgumentException("Snapshot must be a valid JSON object");
        }

        Set<String> allowedFields = switch (change.getChangeKind()) {
            case SCHEDULE -> ALLOWED_SCHEDULE_FIELDS;
            case AVAILABILITY -> ALLOWED_AVAILABILITY_FIELDS;
            case SALE_STATE -> ALLOWED_SALE_STATE_FIELDS;
        };

        Iterator<String> fieldNames = snapshot.fieldNames();
        while (fieldNames.hasNext()) {
            String field = fieldNames.next();
            String lower = field.toLowerCase();
            for (String forbidden : FORBIDDEN_FIELDS) {
                if (lower.contains(forbidden)) {
                    throw new IllegalArgumentException("Forbidden field detected in snapshot: " + field);
                }
            }
            if (!allowedFields.contains(field)) {
                throw new IllegalArgumentException("Non-allowlisted field detected: " + field);
            }
        }
    }

    protected String buildProviderPayload(OtaChannelMapping mapping, OtaOutboundChange change) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("provider", supportedProvider().name());
        root.put("productId", mapping.getExternalProductId());
        root.put("optionId", mapping.getExternalOptionId());
        root.put("changeKind", change.getChangeKind().name());
        root.set("data", change.getCanonicalSnapshot());
        return root.toString();
    }

    private String sanitizeCorrelation(String value) {
        if (value == null || value.isBlank()) {
            return "corr-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }
        String trimmed = value.trim();
        if (SAFE_CORRELATION.matcher(trimmed).matches()) {
            return trimmed;
        }
        return "corr-sanitized-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
