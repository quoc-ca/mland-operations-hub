-- V182: AI Guest Sessions and Daily Prompt Quota Usage
-- Purpose: Support calendar-day quota (Asia/Ho_Chi_Minh) for Guest (3/day) and Member (10/day)

CREATE TABLE IF NOT EXISTS ai_guest_sessions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_digest VARCHAR(128) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    last_seen_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    INDEX idx_ai_guest_status_exp (status, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ai_prompt_quota_usage (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    quota_scope VARCHAR(16) NOT NULL,
    quota_key_digest VARCHAR(128) NOT NULL,
    quota_date DATE NOT NULL,
    limit_count INT NOT NULL,
    used_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uq_ai_quota (quota_scope, quota_key_digest, quota_date),
    INDEX idx_ai_quota_date (quota_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
