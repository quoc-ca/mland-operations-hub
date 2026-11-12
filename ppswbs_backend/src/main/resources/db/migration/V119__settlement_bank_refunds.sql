-- V119: bank refund requests (003.3 T097, FR-038..FR-040).
-- Mland money is refunded only by bank transfer outside the system: this schema calculates, records, tracks
-- and reconciles, and nothing here calls a gateway. A refund belongs to one ticket (or one single booking),
-- and the money refunded or pending for that ticket never exceeds what was collected for it, which the
-- service checks under the request lock. There is no 5,000 VND minimum.

CREATE TABLE settlement_refund_requests (
    id BIGINT NOT NULL AUTO_INCREMENT,
    request_reference VARCHAR(128) NOT NULL,
    -- The ticket code of a group ticket, or the booking code of a single direct booking.
    booking_reference VARCHAR(128) NOT NULL,
    group_reference VARCHAR(128) NULL DEFAULT NULL,
    reason ENUM('MLAND_CANCELLATION', 'LATE_PAYMENT_NOT_RECONFIRMED', 'OVERCHARGE_AFTER_FINALIZED',
        'EXCESS_OR_DUPLICATE_MONEY', 'OTA_ADDON_OVERCHARGE') NOT NULL,
    -- What this refund is for: a collection, a case, a folio correction. Keeps requests deduplicated.
    source_reference VARCHAR(128) NOT NULL,
    amount_vnd BIGINT NOT NULL,
    currency_code VARCHAR(3) NOT NULL DEFAULT 'VND',
    status ENUM('PENDING_ACCOUNT', 'PENDING_APPROVAL', 'PENDING_TRANSFER', 'PENDING_RECONCILIATION',
        'REFUNDED', 'CLOSED_UNREACHABLE') NOT NULL DEFAULT 'PENDING_ACCOUNT',

    -- Who the money goes back to. The default is the payer of the refunded money; another recipient needs
    -- the payer's recorded confirmation.
    payer_label VARCHAR(200) NULL DEFAULT NULL,
    recipient_bank_code VARCHAR(32) NULL DEFAULT NULL,
    -- AES-GCM, nonce in front; readable only by the approving Manager and the transferring actor (FR-040).
    recipient_account_encrypted VARCHAR(512) NULL DEFAULT NULL,
    recipient_account_masked VARCHAR(16) NULL DEFAULT NULL,
    recipient_holder_name VARCHAR(200) NULL DEFAULT NULL,
    recipient_is_payer BOOLEAN NOT NULL DEFAULT TRUE,
    payer_confirmed_other_recipient BOOLEAN NOT NULL DEFAULT FALSE,
    account_entry_channel ENUM('LINK', 'COUNTER') NULL DEFAULT NULL,
    account_entered_by_account_id BIGINT NULL DEFAULT NULL,
    account_entered_at DATETIME(6) NULL DEFAULT NULL,

    approved_by_account_id BIGINT NULL DEFAULT NULL,
    approved_at DATETIME(6) NULL DEFAULT NULL,
    approval_reason VARCHAR(1000) NULL DEFAULT NULL,
    transferred_by_account_id BIGINT NULL DEFAULT NULL,
    transferred_at DATETIME(6) NULL DEFAULT NULL,
    bank_transfer_reference VARCHAR(128) NULL DEFAULT NULL,
    transfer_proof_reference VARCHAR(255) NULL DEFAULT NULL,
    confirmed_by_account_id BIGINT NULL DEFAULT NULL,
    confirmed_at DATETIME(6) NULL DEFAULT NULL,

    closed_by_account_id BIGINT NULL DEFAULT NULL,
    closed_at DATETIME(6) NULL DEFAULT NULL,
    closure_reason VARCHAR(1000) NULL DEFAULT NULL,
    reopened_at DATETIME(6) NULL DEFAULT NULL,
    reminder_day3_at DATETIME(6) NULL DEFAULT NULL,
    reminder_day7_at DATETIME(6) NULL DEFAULT NULL,

    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_srr_request_reference (request_reference),
    -- FR-039: requests are deduplicated; the same money is never asked back twice.
    UNIQUE KEY uq_srr_source (reason, source_reference),
    KEY idx_srr_booking (booking_reference, status),
    KEY idx_srr_group (group_reference),
    KEY idx_srr_status_created (status, created_at),
    CONSTRAINT ck_srr_amount CHECK (amount_vnd > 0),
    -- The full number and its mask travel together, and only the mask survives REFUNDED (FR-040).
    CONSTRAINT ck_srr_account_pairing CHECK (
        recipient_account_encrypted IS NULL OR recipient_account_masked IS NOT NULL),
    -- Money only moves once an account is on file, so no state past PENDING_ACCOUNT lacks one.
    CONSTRAINT ck_srr_account_before_approval CHECK (
        status IN ('PENDING_ACCOUNT', 'CLOSED_UNREACHABLE') OR recipient_account_masked IS NOT NULL),
    CONSTRAINT ck_srr_approval_pairing CHECK (
        (approved_by_account_id IS NULL AND approved_at IS NULL)
        OR (approved_by_account_id IS NOT NULL AND approved_at IS NOT NULL AND approval_reason IS NOT NULL)),
    CONSTRAINT ck_srr_transfer_pairing CHECK (
        (transferred_by_account_id IS NULL AND transferred_at IS NULL)
        OR (transferred_by_account_id IS NOT NULL AND transferred_at IS NOT NULL
            AND bank_transfer_reference IS NOT NULL)),
    CONSTRAINT ck_srr_confirm_pairing CHECK (
        (confirmed_by_account_id IS NULL AND confirmed_at IS NULL)
        OR (confirmed_by_account_id IS NOT NULL AND confirmed_at IS NOT NULL)),
    -- REFUNDED is only reached through approval, transfer and a Manager's confirmation.
    CONSTRAINT ck_srr_refunded CHECK (
        status <> 'REFUNDED'
        OR (approved_at IS NOT NULL AND transferred_at IS NOT NULL AND confirmed_at IS NOT NULL)),
    CONSTRAINT ck_srr_closed CHECK (
        status <> 'CLOSED_UNREACHABLE' OR (closed_at IS NOT NULL AND closure_reason IS NOT NULL)),
    -- A recipient who is not the payer needs the payer's recorded confirmation (FR-040).
    CONSTRAINT ck_srr_other_recipient CHECK (
        recipient_is_payer = TRUE OR recipient_account_masked IS NULL OR payer_confirmed_other_recipient = TRUE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- FR-040: the account is collected through a single-use link valid 7 days; a resend revokes the old one.
CREATE TABLE settlement_refund_account_links (
    id BIGINT NOT NULL AUTO_INCREMENT,
    request_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    lifecycle_status ENUM('ACTIVE', 'USED', 'REVOKED', 'EXPIRED') NOT NULL DEFAULT 'ACTIVE',
    expires_at DATETIME(6) NOT NULL,
    used_at DATETIME(6) NULL DEFAULT NULL,
    revoked_at DATETIME(6) NULL DEFAULT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_srl_token_hash (token_hash),
    KEY idx_srl_request (request_id, lifecycle_status),
    CONSTRAINT fk_srl_request FOREIGN KEY (request_id) REFERENCES settlement_refund_requests (id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
