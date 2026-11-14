-- ==============================================================================
-- V111__notification_management.sql
-- Feature FE-007: Notification Management Schema and Seed Data
-- ==============================================================================

-- 1. notification_template_definition
CREATE TABLE notification_template_definition (
    id BIGINT AUTO_INCREMENT NOT NULL,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(255) NULL,
    owning_module VARCHAR(64) NOT NULL,
    lifecycle_status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    allowed_placeholders TEXT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_ntd_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. notification_template_version
CREATE TABLE notification_template_version (
    id BIGINT AUTO_INCREMENT NOT NULL,
    template_definition_id BIGINT NOT NULL,
    version_number INT NOT NULL,
    subject_template VARCHAR(255) NOT NULL,
    html_template MEDIUMTEXT NOT NULL,
    plain_text_template MEDIUMTEXT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    created_by VARCHAR(64) NOT NULL,
    activated_by VARCHAR(64) NULL,
    activated_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_ntv_definition FOREIGN KEY (template_definition_id) REFERENCES notification_template_definition (id),
    UNIQUE KEY uq_ntv_def_version (template_definition_id, version_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. notification_request
CREATE TABLE notification_request (
    id BIGINT AUTO_INCREMENT NOT NULL,
    business_event_reference VARCHAR(128) NOT NULL,
    template_code VARCHAR(64) NOT NULL,
    template_version_id BIGINT NOT NULL,
    recipient_role VARCHAR(32) NOT NULL DEFAULT 'CUSTOMER',
    recipient_address_fingerprint VARCHAR(64) NOT NULL,
    recipient_address_masked VARCHAR(128) NOT NULL,
    recipient_snapshot TEXT NOT NULL,
    business_data_snapshot MEDIUMTEXT NOT NULL,
    artifact_reference VARCHAR(255) NULL,
    credential_reference VARCHAR(255) NULL,
    credential_expires_at DATETIME(6) NULL,
    delivery_state VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    attempt_count INT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME(6) NULL,
    processing_started_at DATETIME(6) NULL,
    smtp_invocation_started_at DATETIME(6) NULL,
    terminal_at DATETIME(6) NULL,
    original_request_id BIGINT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_nr_version FOREIGN KEY (template_version_id) REFERENCES notification_template_version (id),
    CONSTRAINT fk_nr_original FOREIGN KEY (original_request_id) REFERENCES notification_request (id),
    UNIQUE KEY uq_nr_idempotency (business_event_reference, template_code, recipient_role, recipient_address_fingerprint, original_request_id),
    KEY idx_nr_worker_poll (delivery_state, next_attempt_at, id),
    KEY idx_nr_retention (terminal_at, delivery_state)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. notification_rendition
CREATE TABLE notification_rendition (
    id BIGINT AUTO_INCREMENT NOT NULL,
    notification_request_id BIGINT NOT NULL,
    rendered_subject VARCHAR(255) NOT NULL,
    rendered_html MEDIUMTEXT NOT NULL,
    rendered_plain_text MEDIUMTEXT NOT NULL,
    rendered_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_nrd_request FOREIGN KEY (notification_request_id) REFERENCES notification_request (id),
    UNIQUE KEY uq_nrd_request (notification_request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. notification_delivery_attempt
CREATE TABLE notification_delivery_attempt (
    id BIGINT AUTO_INCREMENT NOT NULL,
    notification_request_id BIGINT NOT NULL,
    attempt_number INT NOT NULL,
    started_at DATETIME(6) NOT NULL,
    completed_at DATETIME(6) NOT NULL,
    outcome_category VARCHAR(32) NOT NULL,
    provider_message_id VARCHAR(128) NULL,
    safe_error_summary VARCHAR(512) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_nda_request FOREIGN KEY (notification_request_id) REFERENCES notification_request (id),
    KEY idx_nda_request_attempt (notification_request_id, attempt_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 6. notification_operational_failure
CREATE TABLE notification_operational_failure (
    id BIGINT AUTO_INCREMENT NOT NULL,
    business_event_reference VARCHAR(128) NOT NULL,
    template_code VARCHAR(64) NOT NULL,
    recipient_fingerprint VARCHAR(64) NULL,
    failure_code VARCHAR(64) NOT NULL,
    safe_reason VARCHAR(512) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_nof_event (business_event_reference, template_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 7. notification_operational_audit
CREATE TABLE notification_operational_audit (
    id BIGINT AUTO_INCREMENT NOT NULL,
    notification_request_id BIGINT NOT NULL,
    actor_account_id VARCHAR(64) NOT NULL,
    actor_role VARCHAR(32) NOT NULL,
    action_type VARCHAR(64) NOT NULL,
    reason VARCHAR(512) NOT NULL,
    previous_state VARCHAR(32) NOT NULL,
    new_state VARCHAR(32) NOT NULL,
    duplicate_risk_accepted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_noa_request FOREIGN KEY (notification_request_id) REFERENCES notification_request (id),
    KEY idx_noa_actor (actor_account_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 8. notification_tombstone
CREATE TABLE notification_tombstone (
    id BIGINT AUTO_INCREMENT NOT NULL,
    original_request_id BIGINT NOT NULL,
    business_event_reference VARCHAR(128) NOT NULL,
    template_code VARCHAR(64) NOT NULL,
    terminal_state VARCHAR(32) NOT NULL,
    attempt_count INT NOT NULL,
    request_created_at DATETIME(6) NOT NULL,
    request_terminal_at DATETIME(6) NOT NULL,
    scrubbed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_nt_orig_req (original_request_id),
    KEY idx_nt_scrubbed (scrubbed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==============================================================================
-- Seed Data for Initial Templates (T014)
-- ==============================================================================

-- BOOKING_CONFIRMED
INSERT INTO notification_template_definition (code, name, description, owning_module, lifecycle_status, allowed_placeholders, created_at, updated_at)
VALUES (
    'BOOKING_CONFIRMED',
    'Xác nhận đặt lịch Workshop',
    'Gộp booking, tiền cọc và vé QR',
    'workshopbooking',
    'ACTIVE',
    '["customerName", "bookingCode", "depositAmount", "workshopTitle", "startTime", "locationAddress", "ticketUrl"]',
    CURRENT_TIMESTAMP(6),
    CURRENT_TIMESTAMP(6)
);

INSERT INTO notification_template_version (template_definition_id, version_number, subject_template, html_template, plain_text_template, status, created_by, activated_by, activated_at, created_at, updated_at)
SELECT id, 1,
    '[Mland] Xác nhận đặt lịch workshop #{{bookingCode}}',
    '<div style="font-family: sans-serif; padding: 20px;"><p>Xin chào {{customerName}},</p><p>Đơn đặt lịch <strong>#{{bookingCode}}</strong> cho workshop <strong>{{workshopTitle}}</strong> tại {{locationAddress}} vào lúc {{startTime}} đã được xác nhận.</p><p>Số tiền cọc: {{depositAmount}}.</p><p><a href="{{ticketUrl}}" style="background: #2563eb; color: #fff; padding: 10px 18px; text-decoration: none; border-radius: 4px;">Xem vé tham gia workshop</a></p><p>Trân trọng,<br>Đội ngũ Mland</p></div>',
    'Xin chào {{customerName}}, Đơn đặt lịch #{{bookingCode}} cho workshop {{workshopTitle}} tại {{locationAddress}} vào lúc {{startTime}} đã được xác nhận. Số tiền cọc: {{depositAmount}}. Xem vé: {{ticketUrl}}',
    'ACTIVE',
    'system',
    'system',
    CURRENT_TIMESTAMP(6),
    CURRENT_TIMESTAMP(6),
    CURRENT_TIMESTAMP(6)
FROM notification_template_definition
WHERE code = 'BOOKING_CONFIRMED';

-- RETAIL_ORDER_CONFIRMED
INSERT INTO notification_template_definition (code, name, description, owning_module, lifecycle_status, allowed_placeholders, created_at, updated_at)
VALUES (
    'RETAIL_ORDER_CONFIRMED',
    'Xác nhận đơn hàng bán lẻ',
    'Đơn hàng thanh toán VNPay hoặc 0 đồng',
    'readyringsales',
    'ACTIVE',
    '["customerName", "orderCode", "orderTotal", "orderDetailsUrl"]',
    CURRENT_TIMESTAMP(6),
    CURRENT_TIMESTAMP(6)
);

INSERT INTO notification_template_version (template_definition_id, version_number, subject_template, html_template, plain_text_template, status, created_by, activated_by, activated_at, created_at, updated_at)
SELECT id, 1,
    '[Mland] Xác nhận đơn hàng #{{orderCode}}',
    '<div style="font-family: sans-serif; padding: 20px;"><p>Xin chào {{customerName}},</p><p>Đơn hàng <strong>#{{orderCode}}</strong> của bạn đã được xác nhận thành công.</p><p>Tổng tiền thanh toán: {{orderTotal}}.</p><p><a href="{{orderDetailsUrl}}" style="background: #2563eb; color: #fff; padding: 10px 18px; text-decoration: none; border-radius: 4px;">Xem chi tiết đơn hàng</a></p><p>Trân trọng,<br>Đội ngũ Mland</p></div>',
    'Xin chào {{customerName}}, Đơn hàng #{{orderCode}} của bạn đã được xác nhận thành công. Tổng tiền thanh toán: {{orderTotal}}. Xem chi tiết: {{orderDetailsUrl}}',
    'ACTIVE',
    'system',
    'system',
    CURRENT_TIMESTAMP(6),
    CURRENT_TIMESTAMP(6),
    CURRENT_TIMESTAMP(6)
FROM notification_template_definition
WHERE code = 'RETAIL_ORDER_CONFIRMED';

-- AUTH_EMAIL_VERIFICATION
INSERT INTO notification_template_definition (code, name, description, owning_module, lifecycle_status, allowed_placeholders, created_at, updated_at)
VALUES (
    'AUTH_EMAIL_VERIFICATION',
    'Xác minh Email',
    'Xác minh tài khoản qua Firebase Auth',
    'firebase_auth',
    'EXTERNAL',
    '["firebaseVerificationUrl"]',
    CURRENT_TIMESTAMP(6),
    CURRENT_TIMESTAMP(6)
);

