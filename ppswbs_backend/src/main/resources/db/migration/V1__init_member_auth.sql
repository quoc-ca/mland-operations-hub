-- Flyway Migration V1: Member Authentication & Policy Acceptance Tables

CREATE TABLE members (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    external_user_id VARCHAR(128) NOT NULL UNIQUE,
    email VARCHAR(255) NULL,
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(64) NOT NULL DEFAULT 'PENDING_POLICY_ACCEPTANCE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_members_email (email)
);

CREATE TABLE policy_documents (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    policy_type VARCHAR(64) NOT NULL,
    version VARCHAR(64) NOT NULL,
    content_uri VARCHAR(512) NOT NULL,
    effective_at TIMESTAMP NOT NULL,
    state VARCHAR(64) NOT NULL DEFAULT 'EFFECTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_policy_type_version (policy_type, version)
);

CREATE TABLE member_policy_acceptances (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id BIGINT NOT NULL,
    policy_document_id BIGINT NOT NULL,
    accepted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    locale VARCHAR(16) NOT NULL DEFAULT 'vi',
    correlation_id VARCHAR(64) NOT NULL,
    UNIQUE KEY uk_member_policy (member_id, policy_document_id),
    CONSTRAINT fk_mpa_member FOREIGN KEY (member_id) REFERENCES members (id),
    CONSTRAINT fk_mpa_policy FOREIGN KEY (policy_document_id) REFERENCES policy_documents (id)
);

CREATE TABLE member_auth_audit_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id BIGINT NULL,
    event_type VARCHAR(64) NOT NULL,
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    correlation_id VARCHAR(64) NOT NULL,
    provider VARCHAR(64) NULL,
    safe_metadata TEXT NULL,
    INDEX idx_audit_member_occurred (member_id, occurred_at)
);

CREATE TABLE workshop_bookings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    booking_code VARCHAR(64) NOT NULL UNIQUE,
    member_id BIGINT NULL,
    contact_email VARCHAR(255) NOT NULL,
    canonical_email VARCHAR(255) NOT NULL,
    confirmation_state VARCHAR(64) NOT NULL DEFAULT 'EMAIL_CONFIRMATION_PENDING',
    booking_status VARCHAR(64) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_bookings_canonical_email (canonical_email),
    CONSTRAINT fk_wb_member FOREIGN KEY (member_id) REFERENCES members (id)
);

CREATE TABLE booking_email_confirmations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    booking_id BIGINT NOT NULL UNIQUE,
    token_hash VARCHAR(128) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    confirmed_at TIMESTAMP NULL,
    state VARCHAR(64) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_bec_booking FOREIGN KEY (booking_id) REFERENCES workshop_bookings (id)
);

-- Seed effective policy documents v1.0.0
INSERT INTO policy_documents (policy_type, version, content_uri, effective_at, state)
VALUES 
    ('TERMS_OF_USE', 'v1.0.0', '/policies/terms-v1.0.0.html', CURRENT_TIMESTAMP, 'EFFECTIVE'),
    ('PRIVACY_POLICY', 'v1.0.0', '/policies/privacy-v1.0.0.html', CURRENT_TIMESTAMP, 'EFFECTIVE');
