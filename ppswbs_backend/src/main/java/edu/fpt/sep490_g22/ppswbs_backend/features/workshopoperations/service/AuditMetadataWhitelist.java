package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 003.2 FR-051 / plan Phase 19 decision 4: the only way stored audit metadata leaves the server. The stored JSON is
 * parsed tolerantly (invalid, empty or non-object JSON gives an empty map), only the whitelisted keys are kept, every
 * kept value is returned as a string, and a value that looks like an e-mail address is dropped so a writer that put
 * personal data under an allowed key still cannot leak it. A key that is not on the list is never returned, so a
 * future writer cannot expose a field by accident.
 */
@Component
public class AuditMetadataWhitelist {

    /** Keys seen in {@code operations_audit_events.metadata} today (plan decision 4) plus {@code assignedAccountId}. */
    static final Set<String> ALLOWED_KEYS = Set.of(
            "fromState", "toState", "bookingCode", "ticketCode", "bookingReference", "reasonCode",
            "ticketReference", "qrGeneration", "ticketCount", "releasedSeats", "movedTickets", "recipientCount",
            "code", "status", "assignedAccountId");

    private static final Pattern EMAIL_LIKE = Pattern.compile("[^\\s@]+@[^\\s@]+");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** @return an unmodifiable map of whitelisted keys to string values; empty when nothing can be shown */
    public Map<String, String> filter(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return Collections.emptyMap();
        }
        JsonNode root;
        try {
            root = MAPPER.readTree(rawJson);
        } catch (Exception ex) {
            return Collections.emptyMap();
        }
        if (root == null || !root.isObject()) {
            return Collections.emptyMap();
        }
        Map<String, String> kept = new LinkedHashMap<>();
        root.fields().forEachRemaining(entry -> {
            JsonNode value = entry.getValue();
            if (!ALLOWED_KEYS.contains(entry.getKey()) || value == null || !value.isValueNode() || value.isNull()) {
                return;
            }
            String text = value.asText();
            if (!EMAIL_LIKE.matcher(text).find()) {
                kept.put(entry.getKey(), text);
            }
        });
        return Collections.unmodifiableMap(kept);
    }
}
