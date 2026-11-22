-- Flyway Migration V190: Custom Manufacturing and Order Intake Integration (Feature 004 & Custom Manufacturing)

ALTER TABLE design_requests
    ADD COLUMN design_reference VARCHAR(64) NULL,
    ADD COLUMN design_version INT NULL,
    ADD COLUMN quote_reference VARCHAR(64) NULL;

CREATE TABLE IF NOT EXISTS custom_design_reference_snapshots (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    request_id VARCHAR(36) NOT NULL,
    design_reference VARCHAR(64) NOT NULL,
    design_version INT NOT NULL DEFAULT 1,
    package_reference VARCHAR(64) NULL,
    snapshot_json LONGTEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_cdrs_request (request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS custom_design_request_revisions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    request_id VARCHAR(36) NOT NULL,
    revision_number INT NOT NULL,
    description VARCHAR(4000) NULL,
    design_reference VARCHAR(64) NULL,
    design_version INT NULL,
    image_references_json TEXT NULL,
    clarification_response VARCHAR(2000) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_cdrr_request (request_id, revision_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS custom_orders (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    custom_order_reference VARCHAR(64) NOT NULL UNIQUE,
    member_account_id BIGINT NOT NULL,
    design_request_id VARCHAR(36) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'QUOTE_PENDING',
    production_status VARCHAR(32) NULL,
    fulfilment_status VARCHAR(32) NULL,
    shipping_address VARCHAR(500) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_co_member (member_account_id, created_at),
    INDEX idx_co_request (design_request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS custom_order_quotes (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    custom_order_id VARCHAR(36) NOT NULL,
    quote_reference VARCHAR(64) NOT NULL UNIQUE,
    quote_generation INT NOT NULL DEFAULT 1,
    total_amount BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'VND',
    expires_at TIMESTAMP NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'QUOTE_PENDING',
    approved_by_manager BOOLEAN NOT NULL DEFAULT FALSE,
    manager_account_id BIGINT NULL,
    manager_reason VARCHAR(2000) NULL,
    payment_status VARCHAR(32) NOT NULL DEFAULT 'UNPAID',
    payment_mode VARCHAR(32) NOT NULL DEFAULT 'FULL_CUSTOM_ORDER_PAYMENT',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_coq_order (custom_order_id, quote_generation)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS custom_order_payment_context (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    custom_order_id VARCHAR(36) NOT NULL,
    quote_id VARCHAR(36) NOT NULL,
    merchant_reference VARCHAR(64) NOT NULL UNIQUE,
    payment_attempt_reference VARCHAR(64) NOT NULL UNIQUE,
    amount_vnd BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'VND',
    status VARCHAR(32) NOT NULL DEFAULT 'INITIATED',
    vnpay_transaction_no VARCHAR(64) NULL,
    payment_url TEXT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_copc_order (custom_order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS custom_order_idempotency_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    command_key VARCHAR(128) NOT NULL,
    action VARCHAR(64) NOT NULL,
    actor_scope VARCHAR(32) NOT NULL,
    request_fingerprint VARCHAR(128) NOT NULL,
    response_reference VARCHAR(128) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_coir_key_action (command_key, action)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE payment_owner_registrations DROP CHECK ck_payment_owner_kind;
ALTER TABLE payment_owner_registrations
    ADD CONSTRAINT ck_payment_owner_kind CHECK (payment_kind IN ('DEPOSIT', 'FINAL_PAYMENT', 'REFUND', 'RETAIL', 'CUSTOM_ORDER_FULL_PAYMENT'));
