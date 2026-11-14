-- V115: group confirmation (003.1 T093, FR-038, FR-039).
-- A confirmed ticket carries the QR identity Workshop Operations issued for it, and the group gets one
-- Magic Link so the buyer reaches every ticket of the group (FR-042). Group payment attempts already exist
-- in V113; nothing here collects money.

ALTER TABLE ticket_bookings
    ADD COLUMN operations_ticket_reference CHAR(36) NULL DEFAULT NULL AFTER package_name_snapshot,
    ADD COLUMN qr_reference VARCHAR(128) NULL DEFAULT NULL AFTER operations_ticket_reference,
    ADD COLUMN confirmed_at DATETIME(6) NULL DEFAULT NULL AFTER financial_status,
    ADD UNIQUE KEY uq_tb_qr_reference (qr_reference),
    ADD UNIQUE KEY uq_tb_operations_ticket (operations_ticket_reference);

-- A ticket is only ever issued a QR by Operations at confirmation, so the two travel together.
ALTER TABLE ticket_bookings
    ADD CONSTRAINT ck_tb_qr_pairing CHECK (
        (operations_ticket_reference IS NULL AND qr_reference IS NULL)
        OR (operations_ticket_reference IS NOT NULL AND qr_reference IS NOT NULL));

CREATE TABLE booking_group_magic_links (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    group_id BIGINT UNSIGNED NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    lifecycle_status ENUM('ACTIVE', 'REVOKED', 'EXPIRED') NOT NULL DEFAULT 'ACTIVE',
    expires_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6) NULL DEFAULT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_bgml_token_hash (token_hash),
    KEY idx_bgml_group (group_id),
    KEY idx_bgml_status_expires (lifecycle_status, expires_at),
    CONSTRAINT fk_bgml_group FOREIGN KEY (group_id) REFERENCES booking_groups (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
