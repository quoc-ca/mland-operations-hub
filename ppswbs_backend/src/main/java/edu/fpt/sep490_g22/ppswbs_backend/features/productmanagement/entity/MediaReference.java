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
 * Registry entry saying that an owning record still uses a media object (data-model
 * {@code catalogue_media_references}). Products and reviews write it in their own transaction; FE-08
 * retains through the productmanagement facade. A product soft delete does not release history.
 */
@Entity
@Table(name = "catalogue_media_references",
        uniqueConstraints = @UniqueConstraint(name = "uk_catalogue_media_references_owner",
                columnNames = {"media_object_id", "owner_module", "owner_record_ref"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MediaReference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "media_object_id", nullable = false, length = 36)
    private UUID mediaObjectId;

    @Column(name = "owner_module", nullable = false, length = 40)
    private String ownerModule;

    @Column(name = "owner_record_ref", nullable = false, length = 128)
    private String ownerRecordRef;

    @Column(name = "linked_at", nullable = false)
    private Instant linkedAt;

    @Column(name = "released_at")
    private Instant releasedAt;

    public static MediaReference link(UUID mediaObjectId, String ownerModule, String ownerRecordRef, Instant at) {
        MediaReference reference = new MediaReference();
        reference.mediaObjectId = Objects.requireNonNull(mediaObjectId, "mediaObjectId");
        reference.ownerModule = Objects.requireNonNull(ownerModule, "ownerModule");
        reference.ownerRecordRef = Objects.requireNonNull(ownerRecordRef, "ownerRecordRef");
        reference.linkedAt = Objects.requireNonNull(at, "at");
        return reference;
    }

    public boolean isActive() {
        return releasedAt == null;
    }

    /** The same owner uses the media again (for example an image re-attached to the same product). */
    public void reactivate(Instant at) {
        if (releasedAt != null) {
            this.releasedAt = null;
            this.linkedAt = Objects.requireNonNull(at, "at");
        }
    }

    /** Idempotent: releasing an already released reference keeps the original time. */
    public void release(Instant at) {
        if (releasedAt == null) {
            this.releasedAt = Objects.requireNonNull(at, "at");
        }
    }
}
