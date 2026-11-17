-- 003.1 FR-041 / T095 (decision 2026-11-13): buyer verification by OTP, recipient-change audit fields and
-- group notification intents. Append-only; V113 tables are extended, never rewritten.

ALTER TABLE ticket_recipient_changes
    ADD COLUMN change_kind VARCHAR(16) NOT NULL DEFAULT 'CHANGE',
    ADD COLUMN qr_rotated TINYINT(1) NOT NULL DEFAULT 0,
    ADD COLUMN counted_online TINYINT(1) NOT NULL DEFAULT 0,
    ADD COLUMN reason VARCHAR(500) NULL,
    ADD COLUMN correlation_reference VARCHAR(64) NULL,
    ADD COLUMN idempotency_key VARCHAR(128) NULL,
    ADD UNIQUE KEY uq_trc_idempotency (idempotency_key),
    ADD CONSTRAINT ck_trc_kind CHECK (change_kind IN ('FIRST_SEND', 'CHANGE')),
    ADD CONSTRAINT ck_trc_actor CHECK (actor_type IN ('MEMBER', 'GUEST', 'STAFF', 'MANAGER')),
    ADD CONSTRAINT ck_trc_channel CHECK (channel IN ('ONLINE', 'SHOP'));

-- One OTP challenge per request. Only a salted hash of the OTP is stored. A verified challenge yields a
-- single-use buyer_verification_reference scoped to the group.
CREATE TABLE booking_buyer_verifications (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    verification_reference CHAR(36) NOT NULL,
    group_id BIGINT UNSIGNED NOT NULL,
    status VARCHAR(16) NOT NULL,
    otp_hash CHAR(64) NULL,
    otp_salt CHAR(32) NULL,
    failed_attempts INT NOT NULL DEFAULT 0,
    otp_expires_at DATETIME(6) NOT NULL,
    buyer_verification_reference CHAR(36) NULL,
    reference_expires_at DATETIME(6) NULL,
    consumed_at DATETIME(6) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_bbv_reference (verification_reference),
    UNIQUE KEY uq_bbv_buyer_reference (buyer_verification_reference),
    KEY idx_bbv_group_created (group_id, created_at),
    CONSTRAINT fk_bbv_group FOREIGN KEY (group_id) REFERENCES booking_groups (id) ON DELETE CASCADE,
    CONSTRAINT ck_bbv_status CHECK (status IN ('PROOF_MISMATCH', 'PENDING', 'VERIFIED', 'CONSUMED', 'LOCKED', 'SUPERSEDED')),
    CONSTRAINT ck_bbv_attempts CHECK (failed_attempts BETWEEN 0 AND 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Delivery snapshots for booking-group emails. The event carries only notification_reference; Feature 007 pulls
-- the snapshot through WorkshopBookingNotificationFacade. Snapshots holding an OTP are scrubbed at expiry.
CREATE TABLE booking_group_notification_intents (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    notification_reference VARCHAR(64) NOT NULL,
    group_id BIGINT UNSIGNED NOT NULL,
    ticket_id BIGINT UNSIGNED NULL,
    notification_kind VARCHAR(64) NOT NULL,
    correlation_reference VARCHAR(64) NOT NULL,
    delivery_snapshot JSON NULL,
    snapshot_expires_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_bgni_reference (notification_reference),
    KEY idx_bgni_group (group_id, created_at),
    KEY idx_bgni_scrub (snapshot_expires_at),
    CONSTRAINT fk_bgni_group FOREIGN KEY (group_id) REFERENCES booking_groups (id) ON DELETE CASCADE,
    CONSTRAINT fk_bgni_ticket FOREIGN KEY (ticket_id) REFERENCES ticket_bookings (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
