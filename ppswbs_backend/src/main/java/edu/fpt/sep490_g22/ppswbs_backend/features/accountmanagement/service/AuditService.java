package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao.AccountAuditEventRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.AccountAuditEvent;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.AuditOutcome;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AccountAuditEventRepository auditEventRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Allowed metadata keys per data-model.md Section 2.5
    private static final Set<String> ALLOWED_METADATA_KEYS = Set.of(
        "previous_role",
        "new_role",
        "previous_status",
        "new_status",
        "assigned_location_ids",
        "removed_location_ids",
        "lock_reason"
    );

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordEvent(
        Long actorAccountId,
        Long targetAccountId,
        String action,
        AuditOutcome outcome,
        String correlationId,
        Map<String, Object> rawMetadata
    ) {
        String sanitizedJson = null;
        if (rawMetadata != null && !rawMetadata.isEmpty()) {
            Map<String, Object> sanitized = new HashMap<>();
            for (Map.Entry<String, Object> entry : rawMetadata.entrySet()) {
                if (ALLOWED_METADATA_KEYS.contains(entry.getKey())) {
                    sanitized.put(entry.getKey(), entry.getValue());
                }
            }
            try {
                sanitizedJson = objectMapper.writeValueAsString(sanitized);
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize audit metadata for correlationId={}", correlationId);
            }
        }

        AccountAuditEvent event = AccountAuditEvent.builder()
            .actorAccountId(actorAccountId)
            .targetAccountId(targetAccountId)
            .action(action)
            .outcome(outcome)
            .correlationId(correlationId != null ? correlationId : "unknown")
            .occurredAt(Instant.now())
            .metadata(sanitizedJson)
            .build();

        auditEventRepository.save(event);
    }
}
