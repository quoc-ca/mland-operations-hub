-- V112: cart quotes and the group's own lines/holds (003.1 T092, FR-030..FR-036).
-- V111 added the group, its tickets and their price snapshots but no place for the cart lines a group was
-- built from, nor for the capacity holds T093 must commit or release. Both live here. All amounts are whole VND.

CREATE TABLE booking_group_lines (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    group_id BIGINT UNSIGNED NOT NULL,
    line_index INT NOT NULL,
    line_reference VARCHAR(48) NOT NULL,
    package_id BIGINT UNSIGNED NOT NULL,
    package_name_snapshot VARCHAR(255) NOT NULL,
    price_per_participant_vnd BIGINT NOT NULL,
    ticket_count INT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_bgl_group_line (group_id, line_index),
    UNIQUE KEY uq_bgl_line_reference (line_reference),
    CONSTRAINT fk_bgl_group FOREIGN KEY (group_id) REFERENCES booking_groups (id) ON DELETE CASCADE,
    CONSTRAINT ck_bgl_line_index CHECK (line_index >= 0),
    CONSTRAINT ck_bgl_price CHECK (price_per_participant_vnd >= 0),
    CONSTRAINT ck_bgl_tickets CHECK (ticket_count >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- One required session of one line, with the capacity hold Operations returned for it. Every ticket of the
-- line takes one seat in each of these sessions, so the hold belongs to the line, not to a ticket.
CREATE TABLE booking_group_line_sessions (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    line_id BIGINT UNSIGNED NOT NULL,
    ordinal INT NOT NULL,
    session_reference CHAR(36) NOT NULL,
    location_reference BIGINT UNSIGNED NULL DEFAULT NULL,
    scheduled_start DATETIME(6) NULL DEFAULT NULL,
    hold_reference CHAR(36) NULL DEFAULT NULL,
    hold_status ENUM('RESERVED', 'COMMITTED', 'RELEASED') NOT NULL DEFAULT 'RESERVED',
    PRIMARY KEY (id),
    UNIQUE KEY uq_bgls_line_ordinal (line_id, ordinal),
    UNIQUE KEY uq_bgls_line_session (line_id, session_reference),
    KEY idx_bgls_hold (hold_reference),
    CONSTRAINT fk_bgls_line FOREIGN KEY (line_id) REFERENCES booking_group_lines (id) ON DELETE CASCADE,
    CONSTRAINT ck_bgls_ordinal CHECK (ordinal >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- A quote of a whole cart. It holds no capacity and is not a booking; the group is created from it, which
-- re-reads prices and availability and refuses on drift. Benefits are 0 until the 006 cart contract lands
-- (see 003.1 cart-benefit-contract-request.md), so the columns exist but only ever hold 0 for now.
CREATE TABLE workshop_cart_quotes (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    quote_ref CHAR(36) NOT NULL,
    buyer_member_account_id BIGINT UNSIGNED NULL DEFAULT NULL,
    lines_json JSON NOT NULL,
    gross_vnd BIGINT NOT NULL,
    voucher_discount_vnd BIGINT NOT NULL DEFAULT 0,
    points_used BIGINT NOT NULL DEFAULT 0,
    point_discount_vnd BIGINT NOT NULL DEFAULT 0,
    final_total_vnd BIGINT NOT NULL,
    group_collection_vnd BIGINT NOT NULL,
    payment_mode ENUM('VNPAY', 'INTERNAL_FREE', 'INTERNAL_DEFERRED') NOT NULL,
    quote_state ENUM('OPEN', 'ACCEPTED') NOT NULL DEFAULT 'OPEN',
    accepted_group_code VARCHAR(32) NULL DEFAULT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    expires_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_wcq_quote_ref (quote_ref),
    KEY idx_wcq_buyer (buyer_member_account_id),
    KEY idx_wcq_state_expiry (quote_state, expires_at),
    CONSTRAINT ck_wcq_amounts CHECK (
        gross_vnd >= 0 AND voucher_discount_vnd >= 0 AND points_used >= 0 AND point_discount_vnd >= 0
        AND group_collection_vnd >= 0
        AND final_total_vnd = gross_vnd - voucher_discount_vnd - point_discount_vnd
        AND group_collection_vnd <= final_total_vnd),
    -- Same FR-036 floor as booking_groups: VNPay only from 5,000 VND, internal modes collect nothing now.
    CONSTRAINT ck_wcq_vnpay_floor CHECK (payment_mode <> 'VNPAY' OR group_collection_vnd >= 5000),
    CONSTRAINT ck_wcq_internal_zero CHECK (payment_mode = 'VNPAY' OR group_collection_vnd = 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
