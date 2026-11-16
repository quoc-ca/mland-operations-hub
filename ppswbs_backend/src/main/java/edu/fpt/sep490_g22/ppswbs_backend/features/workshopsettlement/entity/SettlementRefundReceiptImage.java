package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Metadata of the optional receipt image of a recorded refund transfer (003.3 T154, FR-039). No bytes and no
 * provider URL are stored; the object is an authenticated Cloudinary image read only through the backend.
 */
@Entity
@Table(name = "settlement_refund_receipt_images")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SettlementRefundReceiptImage {

    public static final String UPLOADED = "UPLOADED";
    public static final String ATTACHED = "ATTACHED";
    public static final String DELETE_PENDING = "DELETE_PENDING";
    public static final String DELETED = "DELETED";

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "refund_request_id", nullable = false, updatable = false)
    private Long refundRequestId;

    @Column(name = "storage_key", nullable = false, updatable = false, length = 255)
    private String storageKey;

    @Column(name = "provider_asset_id", length = 255)
    private String providerAssetId;

    @Column(name = "provider_version", length = 64)
    private String providerVersion;

    @Column(name = "provider_format", length = 20)
    private String providerFormat;

    @Column(nullable = false, updatable = false, length = 64)
    private String checksum;

    @Column(name = "content_type", nullable = false, updatable = false, length = 20)
    private String contentType;

    @Column(name = "byte_count", nullable = false, updatable = false)
    private long byteCount;

    @Column(nullable = false, length = 20)
    private String state;

    @Column(name = "uploaded_by_account_id", nullable = false, updatable = false)
    private Long uploadedByAccountId;

    @Column(name = "upload_idempotency_key", nullable = false, updatable = false, length = 128)
    private String uploadIdempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "attached_at")
    private Instant attachedAt;

    @Column(name = "attached_by_account_id")
    private Long attachedByAccountId;

    @Column(name = "attached_bank_transfer_reference", length = 128)
    private String attachedBankTransferReference;

    @Column(name = "delete_requested_at")
    private Instant deleteRequestedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        if (state == null) state = UPLOADED;
    }
}
