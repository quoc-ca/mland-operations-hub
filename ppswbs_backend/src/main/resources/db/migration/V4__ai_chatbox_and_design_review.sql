-- Flyway Migration V4: AI Chatbox Analysis and Suggestion (Feature 004)
-- Append-only migration

CREATE TABLE ai_text_interactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    actor_scope VARCHAR(16) NOT NULL,
    member_id BIGINT NULL,
    prompt_text VARCHAR(2000) NOT NULL,
    response_text TEXT NULL,
    scope VARCHAR(32) NOT NULL,
    provider_correlation_id VARCHAR(128) NULL,
    safe_failure_code VARCHAR(64) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    CONSTRAINT chk_ati_actor_scope CHECK (actor_scope IN ('GUEST', 'MEMBER')),
    CONSTRAINT chk_ati_scope CHECK (scope IN ('PRODUCT', 'WORKSHOP', 'STORE', 'FAQ', 'OUT_OF_SCOPE')),
    INDEX idx_ati_expires (expires_at),
    INDEX idx_ati_member (member_id, created_at),
    CONSTRAINT fk_ati_member FOREIGN KEY (member_id) REFERENCES members (id)
);

CREATE TABLE booking_design_requests (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    member_id BIGINT NOT NULL,
    booking_code VARCHAR(64) NOT NULL,
    design_version INT NOT NULL DEFAULT 1,
    idempotency_key VARCHAR(128) NOT NULL,
    analysis_status VARCHAR(32) NOT NULL,
    review_status VARCHAR(32) NOT NULL,
    estimated_value BIGINT NULL,
    next_action VARCHAR(64) NOT NULL,
    safe_failure_code VARCHAR(64) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT chk_bdr_analysis_status CHECK (analysis_status IN ('PENDING', 'PROCESSING', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT chk_bdr_review_status CHECK (review_status IN ('NEED_REVIEW', 'ACCEPTED', 'REJECTED', 'CLARIFICATION_REQUIRED')),
    UNIQUE KEY uk_bdr_member_version_idemp (member_id, booking_code, design_version, idempotency_key),
    INDEX idx_bdr_review_queue (review_status, analysis_status, created_at),
    INDEX idx_bdr_member (member_id, created_at),
    CONSTRAINT fk_bdr_member FOREIGN KEY (member_id) REFERENCES members (id)
);

CREATE TABLE image_consents (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id BIGINT NOT NULL,
    booking_code VARCHAR(64) NOT NULL,
    purpose VARCHAR(64) NOT NULL,
    consent_version VARCHAR(64) NOT NULL,
    affirmative_action VARCHAR(64) NOT NULL DEFAULT 'CHECKBOX_CONFIRMED',
    accepted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_ic_member_booking (member_id, booking_code, consent_version),
    CONSTRAINT fk_ic_member FOREIGN KEY (member_id) REFERENCES members (id)
);

CREATE TABLE temporary_image_references (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    design_request_id VARCHAR(36) NOT NULL,
    storage_object_key VARCHAR(512) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(64) NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP NULL,
    deletion_outcome VARCHAR(64) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_tir_pending_deletion (deleted, created_at),
    CONSTRAINT fk_tir_request FOREIGN KEY (design_request_id) REFERENCES booking_design_requests (id)
);

CREATE TABLE design_analysis_results (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    design_request_id VARCHAR(36) NOT NULL UNIQUE,
    schema_version VARCHAR(32) NOT NULL,
    image_quality_json TEXT NULL,
    candidate_features_json TEXT NULL,
    confidence_json TEXT NULL,
    safe_explanation TEXT NULL,
    provider_correlation_id VARCHAR(128) NULL,
    processed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_dar_request FOREIGN KEY (design_request_id) REFERENCES booking_design_requests (id)
);

CREATE TABLE human_review_decisions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    design_request_id VARCHAR(36) NOT NULL,
    reviewer_user_id VARCHAR(128) NOT NULL,
    reviewer_role VARCHAR(32) NOT NULL,
    design_version INT NOT NULL,
    decision VARCHAR(32) NOT NULL,
    reason VARCHAR(2000) NOT NULL,
    estimated_value_snapshot BIGINT NULL,
    decided_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_hrd_reviewer_role CHECK (reviewer_role IN ('STAFF', 'OWNER', 'MANAGER')),
    CONSTRAINT chk_hrd_decision CHECK (decision IN ('ACCEPTED', 'REJECTED', 'CLARIFICATION_REQUIRED')),
    INDEX idx_hrd_request_timeline (design_request_id, decided_at),
    CONSTRAINT fk_hrd_request FOREIGN KEY (design_request_id) REFERENCES booking_design_requests (id)
);

CREATE TABLE price_suggestions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    proposal_id VARCHAR(36) NOT NULL,
    target_type VARCHAR(32) NOT NULL,
    advisory BOOLEAN NOT NULL DEFAULT TRUE,
    suggestion_status VARCHAR(32) NOT NULL,
    suggested_price DOUBLE NULL,
    explanation TEXT NULL,
    provider_correlation_id VARCHAR(128) NULL,
    manager_decision VARCHAR(32) NULL,
    final_price DOUBLE NULL,
    requested_by VARCHAR(128) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    decided_at TIMESTAMP NULL,
    CONSTRAINT chk_ps_target_type CHECK (target_type IN ('WORKSHOP_PACKAGE', 'RETAIL_PRODUCT')),
    CONSTRAINT chk_ps_suggestion_status CHECK (suggestion_status IN ('SUCCEEDED', 'INVALID', 'FAILED')),
    INDEX idx_ps_proposal (proposal_id, created_at)
);
