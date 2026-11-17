-- 003.3 T154 (FR-039, CHK007, plan O-1): the optional receipt image of a recorded bank transfer. Only metadata is
-- stored (no bytes, no provider URL); the object lives in Cloudinary as an authenticated image under the 003.3 folder.
-- UPLOADED -> ATTACHED when RECORD_TRANSFER names the image; an UPLOADED row never attached for 24 hours is cleaned up
-- (DELETE_PENDING -> DELETED). An ATTACHED row follows the refund record and is kept at least 5 years after the
-- request ends (the deletion job is T166, after V1).
CREATE TABLE settlement_refund_receipt_images (
    id CHAR(36) NOT NULL,
    refund_request_id BIGINT NOT NULL,
    storage_key VARCHAR(255) NOT NULL,
    provider_asset_id VARCHAR(255) NULL DEFAULT NULL,
    provider_version VARCHAR(64) NULL DEFAULT NULL,
    provider_format VARCHAR(20) NULL DEFAULT NULL,
    checksum VARCHAR(64) NOT NULL,
    content_type VARCHAR(20) NOT NULL,
    byte_count BIGINT NOT NULL,
    state VARCHAR(20) NOT NULL DEFAULT 'UPLOADED',
    uploaded_by_account_id BIGINT NOT NULL,
    upload_idempotency_key VARCHAR(128) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    attached_at DATETIME(6) NULL DEFAULT NULL,
    attached_by_account_id BIGINT NULL DEFAULT NULL,
    attached_bank_transfer_reference VARCHAR(128) NULL DEFAULT NULL,
    delete_requested_at DATETIME(6) NULL DEFAULT NULL,
    deleted_at DATETIME(6) NULL DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_srri_storage_key (storage_key),
    UNIQUE KEY uq_srri_upload_command (uploaded_by_account_id, upload_idempotency_key),
    KEY idx_srri_cleanup (state, created_at),
    KEY idx_srri_request (refund_request_id, state),
    CONSTRAINT fk_srri_request FOREIGN KEY (refund_request_id) REFERENCES settlement_refund_requests (id),
    CONSTRAINT chk_srri_state CHECK (state IN ('UPLOADED', 'ATTACHED', 'DELETE_PENDING', 'DELETED')),
    CONSTRAINT chk_srri_type CHECK (content_type IN ('image/jpeg', 'image/png', 'image/webp')),
    CONSTRAINT chk_srri_size CHECK (byte_count BETWEEN 1 AND 5000000),
    CONSTRAINT chk_srri_attached CHECK (state <> 'ATTACHED' OR (attached_at IS NOT NULL AND attached_by_account_id IS NOT NULL
        AND attached_bank_transfer_reference IS NOT NULL)),
    CONSTRAINT chk_srri_deleted CHECK ((state <> 'DELETE_PENDING' OR delete_requested_at IS NOT NULL)
        AND (state <> 'DELETED' OR deleted_at IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
