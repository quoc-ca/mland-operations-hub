-- V9__product_management.sql
-- Schema migration for Feature FE-02 Product Management (T017).
-- Renumbered from V6 on 2026-10-27 before it was committed or shared: origin/develop already uses V6–V8.
-- Source: ppswbs-spec/features/002-product-management/data-model.md (12 catalogue tables).
-- Append-only: never edit after it has been applied; changes go into a new migration.
-- IDs are positive signed BIGINT; money and quantities are DECIMAL(19,0); timestamps are UTC DATETIME(6).
-- Every foreign key is RESTRICT: history is never cascaded away. Actor IDs are opaque strings from the
-- identity facade, so there is no foreign key to account tables.

CREATE TABLE catalogue_categories (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(200) NOT NULL,
    name_key VARBINARY(1600) NOT NULL,
    description TEXT NULL DEFAULT NULL,
    active TINYINT(1) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_catalogue_categories_name_key (name_key),
    KEY idx_catalogue_categories_active_name (active, name),
    CONSTRAINT chk_catalogue_categories_version CHECK (version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE catalogue_products (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    category_id BIGINT NOT NULL,
    published TINYINT(1) NOT NULL,
    first_published_at DATETIME(6) NULL DEFAULT NULL,
    deleted_at DATETIME(6) NULL DEFAULT NULL,
    deleted_by VARCHAR(128) NULL DEFAULT NULL,
    content_version BIGINT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_catalogue_products_listing (category_id, published, deleted_at, name, id),
    KEY idx_catalogue_products_created (created_at, id),
    CONSTRAINT fk_catalogue_products_category FOREIGN KEY (category_id)
        REFERENCES catalogue_categories (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_catalogue_products_versions CHECK (content_version >= 0 AND version >= 0),
    CONSTRAINT chk_catalogue_products_deleted CHECK (
        (deleted_at IS NULL AND deleted_by IS NULL) OR (deleted_at IS NOT NULL AND deleted_by IS NOT NULL)),
    CONSTRAINT chk_catalogue_products_deleted_unpublished CHECK (deleted_at IS NULL OR published = 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- applied_proposal_id / pending_proposal_id reference catalogue_price_proposals; those FKs are added
-- after that table exists (circular reference). The service checks the proposal belongs to the variant.
CREATE TABLE catalogue_variants (
    id BIGINT NOT NULL AUTO_INCREMENT,
    product_id BIGINT NOT NULL,
    ring_size SMALLINT NOT NULL,
    sku VARCHAR(25) NOT NULL,
    remaining_unsold DECIMAL(19,0) NOT NULL,
    sale_enabled TINYINT(1) NOT NULL,
    applied_price DECIMAL(19,0) NULL DEFAULT NULL,
    applied_proposal_id BIGINT NULL DEFAULT NULL,
    applied_sequence BIGINT NOT NULL DEFAULT 0,
    pending_proposal_id BIGINT NULL DEFAULT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_catalogue_variants_size (product_id, ring_size),
    UNIQUE KEY uk_catalogue_variants_sku (sku),
    KEY idx_catalogue_variants_applied_proposal (applied_proposal_id),
    KEY idx_catalogue_variants_pending_proposal (pending_proposal_id),
    CONSTRAINT fk_catalogue_variants_product FOREIGN KEY (product_id)
        REFERENCES catalogue_products (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_catalogue_variants_size CHECK (ring_size BETWEEN 5 AND 29),
    CONSTRAINT chk_catalogue_variants_quantity CHECK (remaining_unsold >= 0),
    CONSTRAINT chk_catalogue_variants_price CHECK (applied_price IS NULL OR applied_price > 0),
    CONSTRAINT chk_catalogue_variants_applied CHECK (
        (applied_price IS NULL AND applied_proposal_id IS NULL)
        OR (applied_price IS NOT NULL AND applied_proposal_id IS NOT NULL)),
    CONSTRAINT chk_catalogue_variants_versions CHECK (applied_sequence >= 0 AND version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE catalogue_product_reviews (
    id BIGINT NOT NULL AUTO_INCREMENT,
    product_id BIGINT NOT NULL,
    revision BIGINT NOT NULL,
    content_version BIGINT NOT NULL,
    state VARCHAR(20) NOT NULL,
    effective TINYINT(1) NOT NULL,
    submitted_by VARCHAR(128) NULL DEFAULT NULL,
    submitted_at DATETIME(6) NULL DEFAULT NULL,
    decided_by VARCHAR(128) NULL DEFAULT NULL,
    decided_at DATETIME(6) NULL DEFAULT NULL,
    rejection_reason VARCHAR(2000) NULL DEFAULT NULL,
    snapshot JSON NULL DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_catalogue_product_reviews_revision (product_id, revision),
    CONSTRAINT fk_catalogue_product_reviews_product FOREIGN KEY (product_id)
        REFERENCES catalogue_products (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_catalogue_product_reviews_state CHECK (state IN ('DRAFT', 'PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT chk_catalogue_product_reviews_revision CHECK (revision > 0 AND content_version >= 0),
    CONSTRAINT chk_catalogue_product_reviews_shape CHECK (
        (state = 'DRAFT' AND submitted_at IS NULL AND decided_at IS NULL AND rejection_reason IS NULL)
        OR (state = 'PENDING' AND submitted_at IS NOT NULL AND submitted_by IS NOT NULL AND snapshot IS NOT NULL
            AND decided_at IS NULL AND rejection_reason IS NULL)
        OR (state = 'APPROVED' AND submitted_at IS NOT NULL AND decided_at IS NOT NULL AND decided_by IS NOT NULL
            AND rejection_reason IS NULL)
        OR (state = 'REJECTED' AND submitted_at IS NOT NULL AND decided_at IS NOT NULL AND decided_by IS NOT NULL
            AND rejection_reason IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE catalogue_price_proposals (
    id BIGINT NOT NULL AUTO_INCREMENT,
    variant_id BIGINT NOT NULL,
    review_id BIGINT NULL DEFAULT NULL,
    revision BIGINT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    state VARCHAR(20) NOT NULL,
    effective TINYINT(1) NOT NULL,
    proposed_amount DECIMAL(19,0) NOT NULL,
    approved_amount DECIMAL(19,0) NULL DEFAULT NULL,
    currency CHAR(3) NOT NULL,
    base_applied_sequence BIGINT NOT NULL,
    terms_digest VARCHAR(64) NOT NULL,
    submitted_by VARCHAR(128) NULL DEFAULT NULL,
    submitted_at DATETIME(6) NULL DEFAULT NULL,
    decided_by VARCHAR(128) NULL DEFAULT NULL,
    decided_at DATETIME(6) NULL DEFAULT NULL,
    reason VARCHAR(2000) NULL DEFAULT NULL,
    supersedes_id BIGINT NULL DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_catalogue_price_proposals_variant_state (variant_id, state, effective),
    KEY idx_catalogue_price_proposals_review (review_id),
    KEY idx_catalogue_price_proposals_supersedes (supersedes_id),
    CONSTRAINT fk_catalogue_price_proposals_variant FOREIGN KEY (variant_id)
        REFERENCES catalogue_variants (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_catalogue_price_proposals_review FOREIGN KEY (review_id)
        REFERENCES catalogue_product_reviews (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_catalogue_price_proposals_supersedes FOREIGN KEY (supersedes_id)
        REFERENCES catalogue_price_proposals (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_catalogue_price_proposals_state CHECK (state IN ('DRAFT', 'PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT chk_catalogue_price_proposals_amounts CHECK (
        proposed_amount > 0 AND (approved_amount IS NULL OR approved_amount > 0)),
    CONSTRAINT chk_catalogue_price_proposals_currency CHECK (currency = 'VND'),
    CONSTRAINT chk_catalogue_price_proposals_numbers CHECK (
        revision > 0 AND version >= 0 AND base_applied_sequence >= 0),
    CONSTRAINT chk_catalogue_price_proposals_shape CHECK (
        (state = 'DRAFT' AND submitted_at IS NULL AND decided_at IS NULL AND approved_amount IS NULL AND reason IS NULL)
        OR (state = 'PENDING' AND submitted_at IS NOT NULL AND submitted_by IS NOT NULL AND decided_at IS NULL
            AND approved_amount IS NULL AND reason IS NULL)
        OR (state = 'APPROVED' AND decided_at IS NOT NULL AND decided_by IS NOT NULL AND approved_amount IS NOT NULL
            AND reason IS NULL)
        OR (state = 'REJECTED' AND decided_at IS NOT NULL AND decided_by IS NOT NULL AND approved_amount IS NULL
            AND reason IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE catalogue_variants
    ADD CONSTRAINT fk_catalogue_variants_applied_proposal FOREIGN KEY (applied_proposal_id)
        REFERENCES catalogue_price_proposals (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT fk_catalogue_variants_pending_proposal FOREIGN KEY (pending_proposal_id)
        REFERENCES catalogue_price_proposals (id) ON DELETE RESTRICT ON UPDATE RESTRICT;

CREATE TABLE catalogue_price_applications (
    id BIGINT NOT NULL AUTO_INCREMENT,
    variant_id BIGINT NOT NULL,
    proposal_id BIGINT NOT NULL,
    applied_sequence BIGINT NOT NULL,
    old_amount DECIMAL(19,0) NULL DEFAULT NULL,
    new_amount DECIMAL(19,0) NOT NULL,
    actor_id VARCHAR(128) NOT NULL,
    applied_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_catalogue_price_applications_sequence (variant_id, applied_sequence),
    UNIQUE KEY uk_catalogue_price_applications_proposal (proposal_id),
    CONSTRAINT fk_catalogue_price_applications_variant FOREIGN KEY (variant_id)
        REFERENCES catalogue_variants (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_catalogue_price_applications_proposal FOREIGN KEY (proposal_id)
        REFERENCES catalogue_price_proposals (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_catalogue_price_applications_amounts CHECK (
        new_amount > 0 AND (old_amount IS NULL OR old_amount > 0) AND applied_sequence > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE catalogue_media_objects (
    id CHAR(36) NOT NULL,
    storage_key VARCHAR(255) NOT NULL,
    provider_asset_id VARCHAR(255) NULL DEFAULT NULL,
    provider_version VARCHAR(64) NULL DEFAULT NULL,
    provider_format VARCHAR(20) NULL DEFAULT NULL,
    checksum VARCHAR(64) NOT NULL,
    content_type VARCHAR(20) NOT NULL,
    byte_count BIGINT NOT NULL,
    state VARCHAR(20) NOT NULL,
    uploaded_by VARCHAR(128) NOT NULL,
    command_id CHAR(36) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    last_verified_at DATETIME(6) NULL DEFAULT NULL,
    first_attached_at DATETIME(6) NULL DEFAULT NULL,
    last_unreferenced_at DATETIME(6) NULL DEFAULT NULL,
    delete_requested_at DATETIME(6) NULL DEFAULT NULL,
    deleted_at DATETIME(6) NULL DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_catalogue_media_objects_storage_key (storage_key),
    UNIQUE KEY uk_catalogue_media_objects_command (uploaded_by, command_id),
    KEY idx_catalogue_media_objects_cleanup (state, created_at),
    CONSTRAINT chk_catalogue_media_objects_state CHECK (
        state IN ('STAGED', 'READY', 'UNAVAILABLE', 'DELETE_PENDING', 'DELETED')),
    CONSTRAINT chk_catalogue_media_objects_type CHECK (content_type IN ('image/jpeg', 'image/png', 'image/webp')),
    CONSTRAINT chk_catalogue_media_objects_size CHECK (byte_count BETWEEN 1 AND 5000000),
    CONSTRAINT chk_catalogue_media_objects_provider CHECK (
        state NOT IN ('READY', 'UNAVAILABLE')
        OR (provider_asset_id IS NOT NULL AND provider_version IS NOT NULL AND provider_format IS NOT NULL)),
    CONSTRAINT chk_catalogue_media_objects_deleted CHECK (
        (state <> 'DELETE_PENDING' OR delete_requested_at IS NOT NULL)
        AND (state <> 'DELETED' OR deleted_at IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE catalogue_product_images (
    id BIGINT NOT NULL AUTO_INCREMENT,
    product_id BIGINT NOT NULL,
    media_object_id CHAR(36) NOT NULL,
    alt_text VARCHAR(200) NOT NULL,
    display_position INT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_catalogue_product_images_position (product_id, display_position),
    KEY idx_catalogue_product_images_media (media_object_id),
    CONSTRAINT fk_catalogue_product_images_product FOREIGN KEY (product_id)
        REFERENCES catalogue_products (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_catalogue_product_images_media FOREIGN KEY (media_object_id)
        REFERENCES catalogue_media_objects (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_catalogue_product_images_position CHECK (display_position BETWEEN 0 AND 9),
    CONSTRAINT chk_catalogue_product_images_alt CHECK (CHAR_LENGTH(TRIM(alt_text)) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE catalogue_media_references (
    id BIGINT NOT NULL AUTO_INCREMENT,
    media_object_id CHAR(36) NOT NULL,
    owner_module VARCHAR(40) NOT NULL,
    owner_record_ref VARCHAR(128) NOT NULL,
    linked_at DATETIME(6) NOT NULL,
    released_at DATETIME(6) NULL DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_catalogue_media_references_owner (media_object_id, owner_module, owner_record_ref),
    CONSTRAINT fk_catalogue_media_references_media FOREIGN KEY (media_object_id)
        REFERENCES catalogue_media_objects (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE catalogue_command_receipts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    actor_id VARCHAR(128) NOT NULL,
    action VARCHAR(80) NOT NULL,
    command_id CHAR(36) NOT NULL,
    request_digest VARCHAR(64) NOT NULL,
    resource_type VARCHAR(40) NOT NULL,
    resource_id VARCHAR(64) NULL DEFAULT NULL,
    result_status INT NOT NULL,
    result_version BIGINT NULL DEFAULT NULL,
    safe_result JSON NOT NULL,
    committed_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_catalogue_command_receipts_key (actor_id, action, command_id),
    KEY idx_catalogue_command_receipts_committed (committed_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE catalogue_audit_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    actor_id VARCHAR(128) NULL DEFAULT NULL,
    action VARCHAR(80) NOT NULL,
    resource_type VARCHAR(40) NOT NULL,
    resource_id VARCHAR(64) NULL DEFAULT NULL,
    resource_version BIGINT NULL DEFAULT NULL,
    resource_revision BIGINT NULL DEFAULT NULL,
    changed_fields JSON NULL DEFAULT NULL,
    safe_changes JSON NULL DEFAULT NULL,
    outcome VARCHAR(20) NOT NULL,
    correlation_id VARCHAR(64) NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_catalogue_audit_events_resource (resource_type, resource_id, occurred_at, id),
    KEY idx_catalogue_audit_events_occurred (occurred_at, id),
    CONSTRAINT chk_catalogue_audit_events_outcome CHECK (outcome IN ('SUCCESS', 'REJECTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Short-lived import preview (US9): payload and errors are cleared when the batch expires; the metadata
-- row is kept with audit/receipts. No foreign key to products: created_product_ids only describes the result.
CREATE TABLE catalogue_import_batches (
    id CHAR(36) NOT NULL,
    actor_id VARCHAR(128) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_sha256 CHAR(64) NOT NULL,
    state VARCHAR(20) NOT NULL,
    row_count INT NOT NULL,
    product_count INT NOT NULL,
    variant_count INT NOT NULL,
    error_count INT NOT NULL,
    errors JSON NULL DEFAULT NULL,
    parsed_payload JSON NULL DEFAULT NULL,
    created_command_id CHAR(36) NOT NULL,
    confirm_command_id CHAR(36) NULL DEFAULT NULL,
    created_product_ids JSON NULL DEFAULT NULL,
    expires_at DATETIME(6) NOT NULL,
    imported_at DATETIME(6) NULL DEFAULT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_catalogue_import_batches_created_command (actor_id, created_command_id),
    UNIQUE KEY uk_catalogue_import_batches_confirm_command (confirm_command_id),
    KEY idx_catalogue_import_batches_actor (actor_id, created_at),
    KEY idx_catalogue_import_batches_expiry (state, expires_at),
    CONSTRAINT chk_catalogue_import_batches_state CHECK (state IN ('VALIDATED', 'REJECTED', 'IMPORTED', 'EXPIRED')),
    CONSTRAINT chk_catalogue_import_batches_counts CHECK (
        row_count BETWEEN 0 AND 500 AND product_count >= 0 AND variant_count >= 0 AND error_count >= 0),
    CONSTRAINT chk_catalogue_import_batches_payload CHECK (state = 'VALIDATED' OR parsed_payload IS NULL),
    CONSTRAINT chk_catalogue_import_batches_imported CHECK (
        (state = 'IMPORTED' AND imported_at IS NOT NULL AND confirm_command_id IS NOT NULL)
        OR (state <> 'IMPORTED' AND imported_at IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
