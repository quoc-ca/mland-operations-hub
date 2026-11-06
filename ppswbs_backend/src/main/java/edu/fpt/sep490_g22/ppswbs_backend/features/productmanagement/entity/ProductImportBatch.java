package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueErrorCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Result of validating one import file (data-model {@code catalogue_import_batches}, FR-039–045).
 *
 * <ul>
 *   <li>The original file is never stored; only its SHA-256, the normalized rows ({@code parsedPayload},
 *       VALIDATED only) and every row error ({@code errors}, not truncated).</li>
 *   <li>A batch expires 60 minutes after creation (spec A-003). After that its payload and errors are
 *       removed by the cleaner; counts and metadata stay with receipt/audit for five years.</li>
 *   <li>Payload and errors are JSON documents written by the import service.</li>
 * </ul>
 */
@Entity
@Table(name = "catalogue_import_batches")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductImportBatch {

    public static final Duration TIME_TO_LIVE = Duration.ofMinutes(60);

    public enum State {
        VALIDATED,
        REJECTED,
        IMPORTED,
        EXPIRED
    }

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "id", nullable = false, length = 36)
    private UUID id;

    @Column(name = "actor_id", nullable = false, length = 128)
    private String actorId;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "file_sha256", nullable = false, length = 64, columnDefinition = "char(64)")
    private String fileSha256;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 20)
    private State state;

    @Column(name = "row_count", nullable = false)
    private int rowCount;

    @Column(name = "product_count", nullable = false)
    private int productCount;

    @Column(name = "variant_count", nullable = false)
    private int variantCount;

    @Column(name = "error_count", nullable = false)
    private int errorCount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "errors")
    private String errors;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parsed_payload")
    private String parsedPayload;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "created_command_id", nullable = false, length = 36)
    private UUID createdCommandId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "confirm_command_id", length = 36)
    private UUID confirmCommandId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "created_product_ids")
    private String createdProducts;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "imported_at")
    private Instant importedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * New batch from one validation command. With no errors it is VALIDATED and keeps the payload;
     * otherwise it is REJECTED and keeps only the errors.
     */
    public static ProductImportBatch validated(UUID id, String actorId, String fileName, String fileSha256,
                                               UUID commandId, int rowCount, int productCount, int variantCount,
                                               int errorCount, String errorsJson, String payloadJson, Instant now) {
        ProductImportBatch batch = new ProductImportBatch();
        batch.id = Objects.requireNonNull(id, "id");
        batch.actorId = Objects.requireNonNull(actorId, "actorId");
        batch.fileName = Objects.requireNonNull(fileName, "fileName");
        batch.fileSha256 = Objects.requireNonNull(fileSha256, "fileSha256");
        batch.createdCommandId = Objects.requireNonNull(commandId, "commandId");
        batch.rowCount = rowCount;
        batch.errorCount = errorCount;
        batch.errors = errorsJson;
        if (errorCount == 0) {
            batch.state = State.VALIDATED;
            batch.productCount = productCount;
            batch.variantCount = variantCount;
            batch.parsedPayload = Objects.requireNonNull(payloadJson, "payloadJson");
        } else {
            batch.state = State.REJECTED;
        }
        batch.createdAt = Objects.requireNonNull(now, "now");
        batch.updatedAt = now;
        batch.expiresAt = now.plus(TIME_TO_LIVE);
        return batch;
    }

    public boolean isExpiredAt(Instant now) {
        return state == State.EXPIRED || !now.isBefore(expiresAt);
    }

    /** Confirmation succeeded: the payload is removed and the created products are recorded. */
    public void markImported(UUID confirmCommandId, String createdJson, Instant now) {
        requireState(State.VALIDATED);
        this.state = State.IMPORTED;
        this.confirmCommandId = Objects.requireNonNull(confirmCommandId, "confirmCommandId");
        this.createdProducts = Objects.requireNonNull(createdJson, "createdJson");
        this.importedAt = now;
        this.parsedPayload = null;
        this.updatedAt = now;
    }

    /** Re-validation at confirmation found changed data: nothing is created and the batch is rejected. */
    public void markStale(int errorCount, String errorsJson, Instant now) {
        requireState(State.VALIDATED);
        this.state = State.REJECTED;
        this.errorCount = errorCount;
        this.errors = errorsJson;
        this.parsedPayload = null;
        this.updatedAt = now;
    }

    /**
     * Removes payload and errors of a batch past its expiry; a VALIDATED batch becomes EXPIRED.
     * Returns whether anything changed, so the cleaner is idempotent.
     */
    public boolean expire(Instant now) {
        if (now.isBefore(expiresAt)) {
            return false;
        }
        boolean changed = errors != null || parsedPayload != null || state == State.VALIDATED;
        if (state == State.VALIDATED) {
            state = State.EXPIRED;
        }
        errors = null;
        parsedPayload = null;
        if (changed) {
            updatedAt = now;
        }
        return changed;
    }

    private void requireState(State expected) {
        if (state != expected) {
            throw new CatalogueException(CatalogueErrorCode.STALE_VERSION);
        }
    }
}
