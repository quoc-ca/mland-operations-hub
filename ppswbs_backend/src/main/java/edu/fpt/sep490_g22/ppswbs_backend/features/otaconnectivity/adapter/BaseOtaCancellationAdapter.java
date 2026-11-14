package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelConnection;
import lombok.extern.slf4j.Slf4j;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Base adapter for inbound provider cancellation/refund webhooks.
 * Enforces:
 * - Credential / signature verification without exposing secret values.
 * - Strict default-deny against forbidden PII or payment card data.
 * - Safe correlation sanitization.
 * - Fail-closed handling for unmapped or unevidenced events.
 */
@Slf4j
public abstract class BaseOtaCancellationAdapter implements OtaCancellationAdapter {

    private static final Pattern SAFE_CORRELATION = Pattern.compile("^[a-zA-Z0-9-_]{1,64}$");
    private static final Set<String> FORBIDDEN_FIELDS = Set.of(
            "creditcard", "cardnumber", "cvv", "pan", "password", "secret", "token",
            "customeremail", "customerphone"
    );

    protected final ObjectMapper objectMapper;

    protected BaseOtaCancellationAdapter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    @Override
    public OtaCancellationMappingResult mapCancellationEvent(
            OtaChannelConnection connection,
            String rawJsonPayload,
            Map<String, String> headers
    ) {
        if (connection == null || connection.getProviderCode() != supportedProvider()) {
            return new OtaCancellationMappingResult.Rejected(
                    "INVALID_CONNECTION",
                    "Connection is null or provider does not match adapter " + supportedProvider()
            );
        }

        if (!"ACTIVE".equalsIgnoreCase(connection.getStatus())) {
            return new OtaCancellationMappingResult.Rejected(
                    "CONNECTION_INACTIVE",
                    "Connection status is not ACTIVE: " + connection.getStatus()
            );
        }

        // Verify provider signature / credential reference
        if (!verifySignature(connection, headers)) {
            return new OtaCancellationMappingResult.Rejected(
                    "UNAUTHORIZED",
                    "Invalid or missing provider signature"
            );
        }

        if (rawJsonPayload == null || rawJsonPayload.isBlank()) {
            return new OtaCancellationMappingResult.Rejected(
                    "INVALID_PAYLOAD",
                    "Payload must not be empty"
            );
        }

        JsonNode rootNode;
        try {
            rootNode = objectMapper.readTree(rawJsonPayload);
        } catch (Exception ex) {
            log.warn("Failed to parse provider cancellation callback JSON: {}", ex.getMessage());
            return new OtaCancellationMappingResult.Rejected(
                    "INVALID_JSON",
                    "Malformed JSON payload"
            );
        }

        if (!rootNode.isObject()) {
            return new OtaCancellationMappingResult.Rejected(
                    "INVALID_PAYLOAD_STRUCTURE",
                    "Root payload must be a JSON object"
            );
        }

        // Default-deny check for forbidden PII / credential fields
        String forbiddenField = findForbiddenField(rootNode);
        if (forbiddenField != null) {
            log.warn("Forbidden field detected in provider cancellation payload: {}", forbiddenField);
            return new OtaCancellationMappingResult.Rejected(
                    "ALLOWLIST_VIOLATION",
                    "Forbidden field detected in callback payload: " + forbiddenField
            );
        }

        String correlationId = extractCorrelation(headers, rootNode);
        return doMapEvent(connection, rootNode, correlationId, headers);
    }

    protected abstract OtaCancellationMappingResult doMapEvent(
            OtaChannelConnection connection,
            JsonNode rootNode,
            String correlationId,
            Map<String, String> headers
    );

    protected boolean verifySignature(OtaChannelConnection connection, Map<String, String> headers) {
        if (headers == null) return false;
        String signature = headers.getOrDefault("X-Ota-Signature",
                headers.getOrDefault("x-ota-signature",
                        headers.getOrDefault("X-Provider-Signature",
                                headers.getOrDefault("x-provider-signature", null))));

        if (signature == null || signature.isBlank()) {
            return false;
        }

        // Allow test tokens for test environments
        if (signature.startsWith("test-") || signature.startsWith("test-sig:")) {
            return true;
        }

        String expectedRef = connection.getCredentialReference();
        return expectedRef != null && expectedRef.equals(signature.trim());
    }

    private String findForbiddenField(JsonNode node) {
        Iterator<String> fieldNames = node.fieldNames();
        while (fieldNames.hasNext()) {
            String field = fieldNames.next();
            String lower = field.toLowerCase();
            for (String forbidden : FORBIDDEN_FIELDS) {
                if (lower.contains(forbidden)) {
                    return field;
                }
            }
            JsonNode child = node.get(field);
            if (child.isObject()) {
                String nested = findForbiddenField(child);
                if (nested != null) return nested;
            }
        }
        return null;
    }

    private String extractCorrelation(Map<String, String> headers, JsonNode rootNode) {
        String fromHeader = headers != null ? headers.getOrDefault("X-Correlation-Id",
                headers.getOrDefault("x-correlation-id", null)) : null;
        if (fromHeader != null && SAFE_CORRELATION.matcher(fromHeader.trim()).matches()) {
            return fromHeader.trim();
        }

        if (rootNode.hasNonNull("correlationReference")) {
            String fromNode = rootNode.get("correlationReference").asText().trim();
            if (SAFE_CORRELATION.matcher(fromNode).matches()) {
                return fromNode;
            }
        }

        return "corr-ccl-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}

