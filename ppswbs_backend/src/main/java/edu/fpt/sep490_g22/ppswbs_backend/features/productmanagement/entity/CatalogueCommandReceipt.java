package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Committed result of one catalogue command (data-model {@code catalogue_command_receipts}).
 * Unique per actor/action/command key; kept at least five years from commit. Stores only a
 * request digest and a safe result, never the full request or a token. Never grants authority.
 */
@Entity
@Table(name = "catalogue_command_receipts",
        uniqueConstraints = @UniqueConstraint(name = "uk_catalogue_command_receipts_key",
                columnNames = {"actor_id", "action", "command_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CatalogueCommandReceipt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_id", nullable = false, length = 128)
    private String actorId;

    @Column(name = "action", nullable = false, length = 80)
    private String action;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "command_id", nullable = false, length = 36)
    private UUID commandId;

    /** SHA-256 hex of the normalized request (target, expectedVersion, payload). */
    @Column(name = "request_digest", nullable = false, length = 64)
    private String requestDigest;

    @Column(name = "resource_type", nullable = false, length = 40)
    private String resourceType;

    @Column(name = "resource_id", length = 64)
    private String resourceId;

    /** HTTP status of the confirmed result, replayed as-is. */
    @Column(name = "result_status", nullable = false)
    private int resultStatus;

    @Column(name = "result_version")
    private Long resultVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "safe_result", nullable = false)
    private String safeResult;

    @Column(name = "committed_at", nullable = false, updatable = false)
    private Instant committedAt;

    public CatalogueCommandReceipt(String actorId, String action, UUID commandId, String requestDigest,
                                   String resourceType, String resourceId, int resultStatus, Long resultVersion,
                                   String safeResult, Instant committedAt) {
        this.actorId = Objects.requireNonNull(actorId, "actorId");
        this.action = Objects.requireNonNull(action, "action");
        this.commandId = Objects.requireNonNull(commandId, "commandId");
        this.requestDigest = Objects.requireNonNull(requestDigest, "requestDigest");
        this.resourceType = Objects.requireNonNull(resourceType, "resourceType");
        this.resourceId = resourceId;
        this.resultStatus = resultStatus;
        this.resultVersion = resultVersion;
        this.safeResult = Objects.requireNonNull(safeResult, "safeResult");
        this.committedAt = Objects.requireNonNull(committedAt, "committedAt");
    }
}
