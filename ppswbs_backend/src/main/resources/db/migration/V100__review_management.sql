CREATE TABLE reviews (
    id VARCHAR(36) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_reference VARCHAR(128) NOT NULL,
    target_type VARCHAR(32) NOT NULL,
    target_reference VARCHAR(128) NOT NULL,
    owner_type VARCHAR(16) NOT NULL,
    owner_reference VARCHAR(128) NOT NULL,
    masked_display_name VARCHAR(128) NOT NULL,
    rating TINYINT UNSIGNED NOT NULL,
    body_text TEXT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PUBLIC',
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT chk_reviews_rating CHECK (rating BETWEEN 1 AND 5),
    UNIQUE KEY uq_reviews_source (source_type, source_reference),
    KEY idx_reviews_target_status (target_type, target_reference, status),
    KEY idx_reviews_status_created (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE review_revisions (
    id VARCHAR(36) NOT NULL,
    review_id VARCHAR(36) NOT NULL,
    revision_number INT NOT NULL,
    rating TINYINT UNSIGNED NOT NULL,
    body_text TEXT NULL,
    changed_by_type VARCHAR(16) NOT NULL,
    changed_by_reference VARCHAR(128) NOT NULL,
    changed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    reason VARCHAR(1000) NULL,
    correlation_id VARCHAR(128) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_review_revisions_review FOREIGN KEY (review_id) REFERENCES reviews (id) ON DELETE CASCADE,
    CONSTRAINT chk_review_revisions_rating CHECK (rating BETWEEN 1 AND 5),
    UNIQUE KEY uq_review_revisions_rev (review_id, revision_number),
    KEY idx_review_revisions_review (review_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE review_replies (
    id VARCHAR(36) NOT NULL,
    review_id VARCHAR(36) NOT NULL,
    body_text TEXT NOT NULL,
    created_by_manager_id BIGINT UNSIGNED NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_review_replies_review FOREIGN KEY (review_id) REFERENCES reviews (id) ON DELETE CASCADE,
    UNIQUE KEY uq_review_replies_review (review_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE review_moderation_events (
    id VARCHAR(36) NOT NULL,
    review_id VARCHAR(36) NOT NULL,
    actor_id BIGINT UNSIGNED NULL,
    actor_role VARCHAR(32) NOT NULL,
    action VARCHAR(32) NOT NULL,
    previous_status VARCHAR(16) NULL,
    new_status VARCHAR(16) NULL,
    reason VARCHAR(1000) NOT NULL,
    correlation_id VARCHAR(128) NULL,
    idempotency_key VARCHAR(128) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_review_mod_review FOREIGN KEY (review_id) REFERENCES reviews (id) ON DELETE CASCADE,
    KEY idx_review_mod_review (review_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE guest_eligibility_handles (
    id VARCHAR(36) NOT NULL,
    handle_hash VARCHAR(64) NOT NULL,
    contact_type VARCHAR(16) NOT NULL,
    contact_value_masked VARCHAR(254) NOT NULL,
    source_references_json TEXT NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    consumed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_geh_hash (handle_hash),
    KEY idx_geh_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE review_idempotency_records (
    id VARCHAR(36) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    command_type VARCHAR(32) NOT NULL,
    actor_scope VARCHAR(128) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    response_body MEDIUMTEXT NULL,
    status VARCHAR(32) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    expires_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_rir_key_scope (idempotency_key, actor_scope),
    KEY idx_rir_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

