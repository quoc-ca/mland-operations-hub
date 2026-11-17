-- V9__workshop_booking.sql
-- Schema migration for Feature 003.1 Customer Workshop Booking Core

CREATE TABLE workshop_bookings (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    booking_code VARCHAR(32) NOT NULL,
    package_id BIGINT UNSIGNED NOT NULL,
    package_name_snapshot VARCHAR(255) NOT NULL,
    contact_email VARCHAR(254) NOT NULL,
    contact_phone VARCHAR(32) NOT NULL,
    member_account_id BIGINT UNSIGNED NULL DEFAULT NULL,
    participant_count INT NOT NULL,
    booking_status ENUM('PENDING_PAYMENT', 'CONFIRMED', 'EXPIRED', 'CANCELLED') NOT NULL DEFAULT 'PENDING_PAYMENT',
    payment_status ENUM('UNPAID', 'DEPOSIT_PAID', 'FULLY_PAID') NOT NULL DEFAULT 'UNPAID',
    hold_expires_at DATETIME(6) NULL DEFAULT NULL,
    confirmed_at DATETIME(6) NULL DEFAULT NULL,
    cancelled_at DATETIME(6) NULL DEFAULT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_wb_booking_code (booking_code),
    KEY idx_wb_member_account (member_account_id),
    KEY idx_wb_status (booking_status, payment_status),
    KEY idx_wb_hold_expires (booking_status, hold_expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE booking_session_commitments (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    booking_id BIGINT UNSIGNED NOT NULL,
    session_id BIGINT UNSIGNED NOT NULL,
    location_id BIGINT UNSIGNED NOT NULL,
    ordinal INT NOT NULL,
    seat_count INT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_bsc_booking FOREIGN KEY (booking_id) REFERENCES workshop_bookings (id) ON DELETE CASCADE,
    CONSTRAINT fk_bsc_location FOREIGN KEY (location_id) REFERENCES workshop_locations (id),
    UNIQUE KEY uq_bsc_booking_ordinal (booking_id, ordinal),
    UNIQUE KEY uq_bsc_booking_session (booking_id, session_id),
    KEY idx_bsc_session (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE booking_capacity_holds (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    booking_id BIGINT UNSIGNED NOT NULL,
    session_commitment_id BIGINT UNSIGNED NOT NULL,
    hold_reference VARCHAR(64) NOT NULL,
    hold_status VARCHAR(32) NOT NULL DEFAULT 'RESERVED',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_bch_booking FOREIGN KEY (booking_id) REFERENCES workshop_bookings (id) ON DELETE CASCADE,
    CONSTRAINT fk_bch_commitment FOREIGN KEY (session_commitment_id) REFERENCES booking_session_commitments (id) ON DELETE CASCADE,
    UNIQUE KEY uq_bch_hold_reference (hold_reference),
    KEY idx_bch_booking (booking_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE booking_invoices (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    booking_id BIGINT UNSIGNED NOT NULL,
    invoice_number VARCHAR(64) NOT NULL,
    price_per_participant BIGINT NOT NULL,
    package_total BIGINT NOT NULL,
    post_promotion_amount BIGINT NOT NULL,
    deposit_amount BIGINT NOT NULL,
    remaining_amount BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'VND',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_bi_booking FOREIGN KEY (booking_id) REFERENCES workshop_bookings (id) ON DELETE CASCADE,
    UNIQUE KEY uq_bi_booking_id (booking_id),
    UNIQUE KEY uq_bi_invoice_number (invoice_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE booking_payment_attempts (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    booking_id BIGINT UNSIGNED NOT NULL,
    invoice_id BIGINT UNSIGNED NOT NULL,
    attempt_reference VARCHAR(64) NOT NULL,
    merchant_reference VARCHAR(128) NOT NULL,
    attempt_amount BIGINT NOT NULL,
    status ENUM('INITIATED', 'PENDING', 'SUCCESS', 'FAILED', 'EXPIRED') NOT NULL DEFAULT 'INITIATED',
    payment_url_snapshot VARCHAR(512) NULL DEFAULT NULL,
    expires_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_bpa_booking FOREIGN KEY (booking_id) REFERENCES workshop_bookings (id) ON DELETE CASCADE,
    CONSTRAINT fk_bpa_invoice FOREIGN KEY (invoice_id) REFERENCES booking_invoices (id) ON DELETE CASCADE,
    UNIQUE KEY uq_bpa_attempt_reference (attempt_reference),
    KEY idx_bpa_merchant_reference (merchant_reference),
    KEY idx_bpa_booking (booking_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE booking_payment_receipts (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    booking_id BIGINT UNSIGNED NOT NULL,
    payment_attempt_id BIGINT UNSIGNED NULL DEFAULT NULL,
    provider_event_id VARCHAR(128) NOT NULL,
    provider_name VARCHAR(32) NOT NULL DEFAULT 'VNPAY',
    amount_received BIGINT NOT NULL,
    received_at DATETIME(6) NOT NULL,
    safe_payload_hash VARCHAR(64) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_bpr_booking FOREIGN KEY (booking_id) REFERENCES workshop_bookings (id) ON DELETE CASCADE,
    CONSTRAINT fk_bpr_attempt FOREIGN KEY (payment_attempt_id) REFERENCES booking_payment_attempts (id) ON DELETE SET NULL,
    UNIQUE KEY uq_bpr_provider_event (provider_event_id),
    KEY idx_bpr_booking (booking_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE booking_tickets (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    booking_id BIGINT UNSIGNED NOT NULL,
    ticket_code VARCHAR(64) NOT NULL,
    qr_reference VARCHAR(128) NOT NULL,
    delivery_status ENUM('PENDING', 'DELIVERED', 'REDELIVERY_REQUESTED') NOT NULL DEFAULT 'PENDING',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_bt_booking FOREIGN KEY (booking_id) REFERENCES workshop_bookings (id) ON DELETE CASCADE,
    UNIQUE KEY uq_bt_ticket_code (ticket_code),
    UNIQUE KEY uq_bt_qr_reference (qr_reference),
    KEY idx_bt_booking (booking_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE booking_ticket_session_entitlements (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    booking_ticket_id BIGINT UNSIGNED NOT NULL,
    session_commitment_id BIGINT UNSIGNED NOT NULL,
    session_id BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_btse_ticket FOREIGN KEY (booking_ticket_id) REFERENCES booking_tickets (id) ON DELETE CASCADE,
    CONSTRAINT fk_btse_commitment FOREIGN KEY (session_commitment_id) REFERENCES booking_session_commitments (id) ON DELETE CASCADE,
    UNIQUE KEY uq_btse_ticket_commitment (booking_ticket_id, session_commitment_id),
    KEY idx_btse_session (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE booking_magic_links (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    booking_id BIGINT UNSIGNED NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    lifecycle_status ENUM('ACTIVE', 'REVOKED', 'EXPIRED') NOT NULL DEFAULT 'ACTIVE',
    expires_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6) NULL DEFAULT NULL,
    revocation_reason VARCHAR(1000) NULL DEFAULT NULL,
    revoked_by_account_id BIGINT UNSIGNED NULL DEFAULT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_bml_booking FOREIGN KEY (booking_id) REFERENCES workshop_bookings (id) ON DELETE CASCADE,
    UNIQUE KEY uq_bml_token_hash (token_hash),
    KEY idx_bml_booking (booking_id),
    KEY idx_bml_status_expires (lifecycle_status, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE booking_notification_intents (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    booking_id BIGINT UNSIGNED NOT NULL,
    notification_reference VARCHAR(64) NOT NULL,
    notification_kind VARCHAR(64) NOT NULL,
    correlation_reference VARCHAR(64) NOT NULL,
    delivery_snapshot JSON NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_bni_booking FOREIGN KEY (booking_id) REFERENCES workshop_bookings (id) ON DELETE CASCADE,
    UNIQUE KEY uq_bni_reference (notification_reference),
    KEY idx_bni_booking (booking_id),
    KEY idx_bni_correlation (correlation_reference)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE booking_audit_evidence (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    booking_code VARCHAR(32) NOT NULL,
    location_id BIGINT UNSIGNED NOT NULL,
    actor_type VARCHAR(32) NOT NULL,
    actor_id BIGINT UNSIGNED NULL DEFAULT NULL,
    action VARCHAR(64) NOT NULL,
    outcome VARCHAR(32) NOT NULL,
    correlation_reference VARCHAR(64) NOT NULL,
    manager_reason VARCHAR(1000) NULL DEFAULT NULL,
    occurred_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_bae_booking (booking_code),
    KEY idx_bae_location (location_id),
    KEY idx_bae_correlation (correlation_reference),
    KEY idx_bae_occurred (occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE booking_idempotency_records (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    idempotency_key VARCHAR(128) NOT NULL,
    command_action VARCHAR(80) NOT NULL,
    correlation_reference VARCHAR(64) NOT NULL,
    semantic_fingerprint VARCHAR(255) NOT NULL,
    lifecycle_status ENUM('IN_PROGRESS', 'COMPLETED') NOT NULL DEFAULT 'IN_PROGRESS',
    claim_version BIGINT UNSIGNED NOT NULL DEFAULT 1,
    lease_expires_at DATETIME(6) NOT NULL,
    claimed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    completed_at DATETIME(6) NULL DEFAULT NULL,
    status_code INT NULL DEFAULT NULL,
    response_payload JSON NULL DEFAULT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_bir_key_action (idempotency_key, command_action),
    KEY idx_bir_cleanup (lifecycle_status, completed_at),
    KEY idx_bir_lease (lifecycle_status, lease_expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
