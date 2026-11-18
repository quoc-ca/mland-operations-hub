-- V183: Staff Consultation Chat
-- Feature-013: Member and Staff/Manager consultation conversation and messages

CREATE TABLE IF NOT EXISTS consultation_conversations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_reference VARCHAR(64) NOT NULL UNIQUE,
    member_user_id BIGINT NOT NULL,
    context_type VARCHAR(32) NOT NULL,
    context_reference VARCHAR(64) NOT NULL,
    context_version INT NOT NULL,
    context_snapshot JSON NOT NULL,
    assigned_location_id BIGINT NULL,
    claimed_by_user_id BIGINT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    aggregate_version BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    closed_at TIMESTAMP(6) NULL,
    INDEX idx_consultation_member (member_user_id),
    INDEX idx_consultation_location (assigned_location_id),
    INDEX idx_consultation_status (status),
    INDEX idx_consultation_updated (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS consultation_messages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    sender_user_id BIGINT NOT NULL,
    sender_role VARCHAR(16) NOT NULL,
    client_message_reference VARCHAR(128) NOT NULL,
    message_text TEXT NOT NULL,
    correlation_id VARCHAR(64) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_consultation_msg_ref (conversation_id, sender_user_id, client_message_reference),
    INDEX idx_consultation_msg_conv_created (conversation_id, created_at),
    INDEX idx_consultation_msg_sender_created (sender_user_id, created_at),
    CONSTRAINT fk_consultation_messages_conv FOREIGN KEY (conversation_id)
        REFERENCES consultation_conversations (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS consultation_read_states (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    last_read_message_id BIGINT NOT NULL,
    read_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_consultation_read_state (conversation_id, user_id),
    INDEX idx_consultation_read_user (user_id),
    CONSTRAINT fk_consultation_read_states_conv FOREIGN KEY (conversation_id)
        REFERENCES consultation_conversations (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS consultation_claim_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    actor_user_id BIGINT NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    previous_claimant_id BIGINT NULL,
    new_claimant_id BIGINT NULL,
    previous_location_id BIGINT NULL,
    new_location_id BIGINT NULL,
    correlation_id VARCHAR(64) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_consultation_claim_conv (conversation_id),
    CONSTRAINT fk_consultation_claim_conv FOREIGN KEY (conversation_id)
        REFERENCES consultation_conversations (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS consultation_audit_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT NULL,
    actor_user_id BIGINT NULL,
    actor_role VARCHAR(16) NULL,
    action VARCHAR(32) NOT NULL,
    outcome VARCHAR(16) NOT NULL,
    context_type VARCHAR(32) NULL,
    message_length INT NULL,
    safe_code VARCHAR(64) NULL,
    correlation_id VARCHAR(64) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_consultation_audit_conv (conversation_id),
    INDEX idx_consultation_audit_actor (actor_user_id),
    INDEX idx_consultation_audit_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS consultation_idempotency_records (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    idempotency_key VARCHAR(128) NOT NULL,
    actor_scope VARCHAR(128) NOT NULL,
    command_type VARCHAR(32) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    response_body MEDIUMTEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    expires_at TIMESTAMP(6) NOT NULL,
    UNIQUE KEY uk_cir_key_scope (idempotency_key, actor_scope),
    INDEX idx_cir_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
