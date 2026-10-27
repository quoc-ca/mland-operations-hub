package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueErrorCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import java.util.Set;
import java.util.UUID;

/**
 * Stored image object on Cloudinary (data-model {@code catalogue_media_objects}, FR-008).
 *
 * <p>Lifecycle: STAGED → READY ↔ UNAVAILABLE → DELETE_PENDING → DELETED. Provider fields are required
 * before READY. Objects are private and served through the application. Cleanup eligibility
 * (never attached for 24 hours, detached and unreferenced for 30 days) is decided by the cleanup
 * service from the reference registry; deleting a product never makes its images eligible by itself.
 */
@Entity
@Table(name = "catalogue_media_objects",
        uniqueConstraints = @UniqueConstraint(name = "uk_catalogue_media_objects_storage_key", columnNames = "storage_key"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CatalogueMediaObject {

    public enum State {
        STAGED,
        READY,
        UNAVAILABLE,
        DELETE_PENDING,
        DELETED
    }

    public static final long MAX_BYTES = 5_000_000L;
    public static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "id", nullable = false, length = 36)
    private UUID id;

    /** Deterministic provider public_id; unique. */
    @Column(name = "storage_key", nullable = false, length = 255)
    private String storageKey;

    @Column(name = "provider_asset_id", length = 255)
    private String providerAssetId;

    @Column(name = "provider_version", length = 64)
    private String providerVersion;

    @Column(name = "provider_format", length = 20)
    private String providerFormat;

    /** SHA-256 hex of the verified content. */
    @Column(name = "checksum", nullable = false, length = 64)
    private String checksum;

    @Column(name = "content_type", nullable = false, length = 20)
    private String contentType;

    @Column(name = "byte_count", nullable = false)
    private long byteCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 20)
    private State state;

    @Column(name = "uploaded_by", nullable = false, length = 128)
    private String uploadedBy;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "command_id", nullable = false, length = 36)
    private UUID commandId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_verified_at")
    private Instant lastVerifiedAt;

    @Column(name = "first_attached_at")
    private Instant firstAttachedAt;

    @Column(name = "last_unreferenced_at")
    private Instant lastUnreferencedAt;

    @Column(name = "delete_requested_at")
    private Instant deleteRequestedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    /** Reserves an object before the upload; size and type are checked against the verified content. */
    public static CatalogueMediaObject stage(UUID id, String storageKey, String checksum, String contentType,
                                             long byteCount, String uploadedBy, UUID commandId, Instant at) {
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new CatalogueException(CatalogueErrorCode.MEDIA_TYPE_UNSUPPORTED);
        }
        if (byteCount <= 0 || byteCount > MAX_BYTES) {
            throw new CatalogueException(CatalogueErrorCode.MEDIA_TOO_LARGE);
        }
        CatalogueMediaObject media = new CatalogueMediaObject();
        media.id = Objects.requireNonNull(id, "id");
        media.storageKey = Objects.requireNonNull(storageKey, "storageKey");
        media.checksum = Objects.requireNonNull(checksum, "checksum");
        media.contentType = contentType;
        media.byteCount = byteCount;
        media.uploadedBy = Objects.requireNonNull(uploadedBy, "uploadedBy");
        media.commandId = Objects.requireNonNull(commandId, "commandId");
        media.createdAt = Objects.requireNonNull(at, "at");
        media.state = State.STAGED;
        return media;
    }

    /** The provider object was verified; all provider fields are required before READY. */
    public void markReady(String assetId, String version, String format, Instant verifiedAt) {
        if (state != State.STAGED && state != State.UNAVAILABLE) {
            throw new IllegalStateException("Cannot mark " + state + " media as READY");
        }
        if (isBlank(assetId) || isBlank(version) || isBlank(format)) {
            throw new IllegalArgumentException("Provider asset id, version and format are required before READY");
        }
        this.providerAssetId = assetId;
        this.providerVersion = version;
        this.providerFormat = format;
        this.lastVerifiedAt = Objects.requireNonNull(verifiedAt, "verifiedAt");
        this.state = State.READY;
    }

    /** Usable object became unreadable at the provider; publication history is not changed. */
    public void markUnavailable(Instant checkedAt) {
        if (state != State.READY) {
            throw new IllegalStateException("Only READY media can become UNAVAILABLE");
        }
        this.lastVerifiedAt = Objects.requireNonNull(checkedAt, "checkedAt");
        this.state = State.UNAVAILABLE;
    }

    /** Attach or retain is refused while the object is being deleted or no longer usable. */
    public boolean canBeAttached() {
        return state == State.READY;
    }

    public void recordAttached(Instant at) {
        if (!canBeAttached()) {
            throw new CatalogueException(CatalogueErrorCode.MEDIA_NOT_FOUND);
        }
        if (firstAttachedAt == null) {
            this.firstAttachedAt = Objects.requireNonNull(at, "at");
        }
        this.lastUnreferencedAt = null;
    }

    public void recordUnreferenced(Instant at) {
        this.lastUnreferencedAt = Objects.requireNonNull(at, "at");
    }

    /** Committed before the provider delete call, outside any lock. */
    public void requestDelete(Instant at) {
        if (state == State.DELETE_PENDING || state == State.DELETED) {
            return;
        }
        this.deleteRequestedAt = Objects.requireNonNull(at, "at");
        this.state = State.DELETE_PENDING;
    }

    public void markDeleted(Instant at) {
        if (state != State.DELETE_PENDING) {
            throw new IllegalStateException("Only DELETE_PENDING media can be marked DELETED");
        }
        this.deletedAt = Objects.requireNonNull(at, "at");
        this.state = State.DELETED;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
