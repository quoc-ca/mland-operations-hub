-- V111: booking groups and per-person ticket bookings (003.1 FR-030..FR-043, decision 2026-11-12).
-- Dev/test data only exists, so the model is added beside workshop_bookings without converting rows; a
-- single-person booking is a group of one. Recipient, change-history, group payment and late-payment tables
-- are created now so later tasks only add entities and logic. All amounts are whole VND.

CREATE TABLE booking_groups (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    group_code VARCHAR(32) NOT NULL,
    buyer_member_account_id BIGINT UNSIGNED NULL DEFAULT NULL,
    contact_email VARCHAR(254) NOT NULL,
    contact_phone VARCHAR(32) NOT NULL,
    group_status ENUM('PENDING_PAYMENT', 'CONFIRMED', 'EXPIRED', 'CANCELLED') NOT NULL DEFAULT 'PENDING_PAYMENT',
    payment_mode ENUM('VNPAY', 'INTERNAL_FREE', 'INTERNAL_DEFERRED') NOT NULL,
    group_collection_vnd BIGINT NOT NULL,
    hold_expires_at DATETIME(6) NULL DEFAULT NULL,
    payment_deadline_at DATETIME(6) NULL DEFAULT NULL,
    voucher_use_reference VARCHAR(128) NULL DEFAULT NULL,
    loyalty_policy_revision BIGINT NULL DEFAULT NULL,
    confirmed_at DATETIME(6) NULL DEFAULT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_bg_group_code (group_code),
    KEY idx_bg_buyer (buyer_member_account_id),
    KEY idx_bg_status_hold (group_status, hold_expires_at),
    CONSTRAINT ck_bg_collection CHECK (group_collection_vnd >= 0),
    -- VNPay only when the whole group collects at least the 5,000 VND minimum (FR-036).
    CONSTRAINT ck_bg_vnpay_floor CHECK (payment_mode <> 'VNPAY' OR group_collection_vnd >= 5000),
    CONSTRAINT ck_bg_internal_zero CHECK (payment_mode = 'VNPAY' OR group_collection_vnd = 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ticket_bookings (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    ticket_code VARCHAR(40) NOT NULL,
    group_id BIGINT UNSIGNED NOT NULL,
    line_index INT NOT NULL,
    participant_ordinal INT NOT NULL,
    package_id BIGINT UNSIGNED NOT NULL,
    package_name_snapshot VARCHAR(255) NOT NULL,
    ticket_status ENUM('PENDING_PAYMENT', 'CONFIRMED', 'EXPIRED', 'CANCELLED', 'COMPLETED', 'NO_SHOW')
        NOT NULL DEFAULT 'PENDING_PAYMENT',
    financial_status ENUM('UNPAID', 'DEPOSIT_PAID', 'FULLY_PAID', 'REFUND_PENDING', 'REFUNDED')
        NOT NULL DEFAULT 'UNPAID',
    customer_changes_used INT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_tb_ticket_code (ticket_code),
    UNIQUE KEY uq_tb_group_line_person (group_id, line_index, participant_ordinal),
    KEY idx_tb_status (ticket_status, financial_status),
    CONSTRAINT fk_tb_group FOREIGN KEY (group_id) REFERENCES booking_groups (id) ON DELETE CASCADE,
    CONSTRAINT ck_tb_changes CHECK (customer_changes_used >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ticket_price_snapshots (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    ticket_id BIGINT UNSIGNED NOT NULL,
    package_price_vnd BIGINT NOT NULL,
    voucher_discount_vnd BIGINT NOT NULL DEFAULT 0,
    points_used BIGINT NOT NULL DEFAULT 0,
    point_discount_vnd BIGINT NOT NULL DEFAULT 0,
    final_price_vnd BIGINT NOT NULL,
    collected_at_booking_vnd BIGINT NOT NULL,
    collection_kind ENUM('FREE', 'FULL', 'DEPOSIT', 'DEFERRED_BELOW_VNPAY_MINIMUM') NOT NULL,
    remaining_vnd BIGINT NOT NULL,
    non_refundable_disclosed BOOLEAN NOT NULL DEFAULT FALSE,
    loyalty_policy_revision BIGINT NULL DEFAULT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_tps_ticket (ticket_id),
    CONSTRAINT fk_tps_ticket FOREIGN KEY (ticket_id) REFERENCES ticket_bookings (id) ON DELETE CASCADE,
    CONSTRAINT ck_tps_amounts CHECK (
        package_price_vnd >= 0 AND voucher_discount_vnd >= 0 AND points_used >= 0 AND point_discount_vnd >= 0
        AND voucher_discount_vnd + point_discount_vnd <= package_price_vnd
        AND final_price_vnd = package_price_vnd - voucher_discount_vnd - point_discount_vnd
        AND collected_at_booking_vnd >= 0 AND remaining_vnd >= 0
        AND collected_at_booking_vnd + remaining_vnd = final_price_vnd),
    -- FR-035: 0 free; 1..200,000 full; above 200,000 a 200,000 deposit (min(final, 200,000)); group under 5,000 deferred (collected 0).
    CONSTRAINT ck_tps_kind CHECK (
        (collection_kind = 'FREE' AND final_price_vnd = 0 AND collected_at_booking_vnd = 0)
        OR (collection_kind = 'FULL' AND final_price_vnd BETWEEN 1 AND 200000
            AND collected_at_booking_vnd = final_price_vnd)
        OR (collection_kind = 'DEPOSIT' AND final_price_vnd >= 200001 AND collected_at_booking_vnd = 200000)
        OR (collection_kind = 'DEFERRED_BELOW_VNPAY_MINIMUM' AND final_price_vnd >= 1
            AND collected_at_booking_vnd = 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ticket_recipients (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    ticket_id BIGINT UNSIGNED NOT NULL,
    recipient_email VARCHAR(254) NOT NULL,
    change_count INT NOT NULL DEFAULT 0,
    qr_generation INT NOT NULL DEFAULT 1,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_tr_ticket (ticket_id),
    CONSTRAINT fk_tr_ticket FOREIGN KEY (ticket_id) REFERENCES ticket_bookings (id) ON DELETE CASCADE,
    CONSTRAINT ck_tr_changes CHECK (change_count >= 0 AND qr_generation >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ticket_recipient_changes (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    ticket_id BIGINT UNSIGNED NOT NULL,
    old_email_masked VARCHAR(254) NULL DEFAULT NULL,
    new_email_masked VARCHAR(254) NOT NULL,
    actor_type VARCHAR(16) NOT NULL,
    actor_reference VARCHAR(128) NULL DEFAULT NULL,
    channel VARCHAR(16) NOT NULL,
    changed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_trc_ticket (ticket_id, changed_at),
    CONSTRAINT fk_trc_ticket FOREIGN KEY (ticket_id) REFERENCES ticket_bookings (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE group_payment_attempts (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    group_id BIGINT UNSIGNED NOT NULL,
    attempt_reference VARCHAR(64) NOT NULL,
    merchant_reference VARCHAR(64) NOT NULL,
    amount_vnd BIGINT NOT NULL,
    provider_create_date VARCHAR(14) NULL DEFAULT NULL,
    payment_url_snapshot VARCHAR(2048) NULL DEFAULT NULL,
    attempt_status ENUM('PENDING', 'SUCCESS', 'FAILED', 'EXPIRED') NOT NULL DEFAULT 'PENDING',
    expires_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_gpa_attempt (attempt_reference),
    UNIQUE KEY uq_gpa_merchant (merchant_reference),
    KEY idx_gpa_group (group_id, attempt_status),
    CONSTRAINT fk_gpa_group FOREIGN KEY (group_id) REFERENCES booking_groups (id) ON DELETE CASCADE,
    CONSTRAINT ck_gpa_amount CHECK (amount_vnd >= 5000)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE late_payment_reviews (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    group_id BIGINT UNSIGNED NOT NULL,
    verified_evidence_reference VARCHAR(128) NOT NULL,
    amount_vnd BIGINT NOT NULL,
    arrived_at DATETIME(6) NOT NULL,
    failed_conditions VARCHAR(255) NULL DEFAULT NULL,
    outcome ENUM('AUTO_RECONFIRMED', 'PENDING_MANAGER', 'MANAGER_CONFIRMED', 'REFUND_REQUESTED') NOT NULL,
    decided_by_account_id BIGINT UNSIGNED NULL DEFAULT NULL,
    decision_reason VARCHAR(1000) NULL DEFAULT NULL,
    decided_at DATETIME(6) NULL DEFAULT NULL,
    result_due_at DATETIME(6) NULL DEFAULT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_lpr_evidence (verified_evidence_reference),
    KEY idx_lpr_queue (outcome, result_due_at),
    CONSTRAINT fk_lpr_group FOREIGN KEY (group_id) REFERENCES booking_groups (id) ON DELETE CASCADE,
    CONSTRAINT ck_lpr_amount CHECK (amount_vnd > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
