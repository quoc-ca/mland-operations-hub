package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable business audit record of a catalogue action (data-model {@code catalogue_audit_events}).
 * Kept five years from {@code occurredAt}. Holds only safe values: never a token, secret, full payload
 * or uploaded file content. Not linked by FK to account audit.
 */
@Entity
@Table(name = "catalogue_audit_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CatalogueAuditEvent {

    public enum Outcome {
        /** Committed together with the mutation it describes. */
        SUCCESS,
        /** Business rejection, written on its own after the mutation was rolled back. */
        REJECTED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Opaque actor reference from the identity facade; null only for system jobs. */
    @Column(name = "actor_id", length = 128)
    private String actorId;

    @Column(name = "action", nullable = false, length = 80)
    private String action;

    @Column(name = "resource_type", nullable = false, length = 40)
    private String resourceType;

    @Column(name = "resource_id", length = 64)
    private String resourceId;

    @Column(name = "resource_version")
    private Long resourceVersion;

    @Column(name = "resource_revision")
    private Long resourceRevision;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "changed_fields")
    private List<String> changedFields;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "safe_changes")
    private Map<String, Object> safeChanges;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, length = 20)
    private Outcome outcome;

    @Column(name = "correlation_id", nullable = false, length = 64)
    private String correlationId;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    public CatalogueAuditEvent(String actorId, String action, String resourceType, String resourceId,
                               Long resourceVersion, Long resourceRevision, List<String> changedFields,
                               Map<String, Object> safeChanges, Outcome outcome, String correlationId,
                               Instant occurredAt) {
        this.actorId = actorId;
        this.action = Objects.requireNonNull(action, "action");
        this.resourceType = Objects.requireNonNull(resourceType, "resourceType");
        this.resourceId = resourceId;
        this.resourceVersion = resourceVersion;
        this.resourceRevision = resourceRevision;
        this.changedFields = changedFields == null ? List.of() : List.copyOf(changedFields);
        // Values may be null (for example "old value: none"), so Map.copyOf is not used.
        this.safeChanges = safeChanges == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(safeChanges));
        this.outcome = Objects.requireNonNull(outcome, "outcome");
        this.correlationId = Objects.requireNonNull(correlationId, "correlationId");
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
    }
}
