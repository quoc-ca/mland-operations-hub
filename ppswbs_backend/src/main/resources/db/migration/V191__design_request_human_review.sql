-- Flyway Migration V191 (renumbered from V188, which FE-08 already used): Design Request and Human Review (Feature 004 Re-architecture)
-- Replaces AI analysis with human review by Staff/Manager and supports up to 5 images with blur filtering

ALTER TABLE design_requests
    MODIFY COLUMN owner_account_id BIGINT NULL,
    ADD COLUMN guest_session_id VARCHAR(128) NULL,
    ADD COLUMN tracking_token VARCHAR(256) NULL,
    ADD COLUMN tracking_token_hash VARCHAR(128) NULL,
    ADD COLUMN tracking_token_expires_at TIMESTAMP NULL,
    ADD COLUMN decision_explanation VARCHAR(2000) NULL;

ALTER TABLE image_consents
    MODIFY COLUMN actor_account_id BIGINT NULL;

CREATE TABLE IF NOT EXISTS design_request_images (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    design_request_id VARCHAR(36) NOT NULL,
    image_reference VARCHAR(64) NOT NULL UNIQUE,
    storage_path VARCHAR(512) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(64) NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    quality_status VARCHAR(32) NOT NULL DEFAULT 'PASS',
    quality_reason VARCHAR(255) NULL,
    quality_score DOUBLE NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_dri_design_request FOREIGN KEY (design_request_id) REFERENCES design_requests(id) ON DELETE CASCADE,
    INDEX idx_dri_request (design_request_id)
-- No explicit charset/collation: design_request_id must use the same collation as design_requests.id (V62, database
-- default), otherwise MySQL refuses the foreign key with error 3780.
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS guest_design_request_quota (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_digest VARCHAR(128) NOT NULL,
    quota_date DATE NOT NULL,
    request_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uq_guest_quota_date (session_digest, quota_date),
    INDEX idx_guest_quota_date (quota_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
