-- V117: folio collections and the per-branch cash day close (003.3 T094-T096, FR-031..FR-036).
-- A folio's balance may be collected in several parts and mixed methods. A collection keys on bill_reference
-- like settlement_charges does, so it belongs to exactly one ticket or booking folio. All amounts are whole
-- VND. A PENDING_VERIFICATION collection never reduces a balance; only a CONFIRMED one does.

CREATE TABLE settlement_folio_collections (
    id BIGINT NOT NULL AUTO_INCREMENT,
    collection_reference VARCHAR(128) NOT NULL,
    bill_reference VARCHAR(128) NOT NULL,
    booking_reference VARCHAR(128) NOT NULL,
    method ENUM('VNPAY', 'CASH', 'BANK_TRANSFER') NOT NULL,
    -- The amount applied to the folio. For cash this is never the amount handed over, which may be larger.
    amount_vnd BIGINT NOT NULL,
    cash_received_vnd BIGINT NULL DEFAULT NULL,
    cash_change_vnd BIGINT NULL DEFAULT NULL,
    -- FR-034: money that arrived twice or above the balance is kept in full and flagged for a Manager refund,
    -- which 003.3 T097/T098 turn into a refund request. It never finalizes or earns twice.
    excess_vnd BIGINT NOT NULL DEFAULT 0,
    status ENUM('PENDING_VERIFICATION', 'CONFIRMED', 'FAILED', 'VOIDED') NOT NULL DEFAULT 'PENDING_VERIFICATION',
    -- VNPay merchant reference, the customer's bank transaction reference, or the cash receipt number.
    external_reference VARCHAR(128) NULL DEFAULT NULL,
    payer_label VARCHAR(200) NULL DEFAULT NULL,
    branch_id BIGINT NULL DEFAULT NULL,
    -- Vietnam working day this cash belongs to; set when cash is confirmed, null for other methods.
    cash_business_day DATE NULL DEFAULT NULL,
    recorded_by_account_id BIGINT NULL DEFAULT NULL,
    confirmed_by_account_id BIGINT NULL DEFAULT NULL,
    confirmed_at DATETIME(6) NULL DEFAULT NULL,
    currency_code VARCHAR(3) NOT NULL DEFAULT 'VND',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_sfc_collection_reference (collection_reference),
    -- FR-034: the same bank transaction or cash confirmation is never recorded twice. MySQL allows repeated
    -- NULLs in a unique index, so a collection without an external reference is unaffected.
    UNIQUE KEY uq_sfc_method_external (method, external_reference),
    KEY idx_sfc_bill (bill_reference, status),
    KEY idx_sfc_cash_day (branch_id, cash_business_day, status),
    CONSTRAINT ck_sfc_amount CHECK (amount_vnd > 0 AND excess_vnd >= 0 AND excess_vnd <= amount_vnd),
    -- FR-033: the 5,000 VND floor applies to each VNPay collection; cash and transfer have no floor here.
    CONSTRAINT ck_sfc_vnpay_floor CHECK (method <> 'VNPAY' OR amount_vnd >= 5000),
    -- FR-032: cash records what was handed over and what was returned; only the applied amount hits the folio.
    CONSTRAINT ck_sfc_cash_amounts CHECK (
        (method <> 'CASH' AND cash_received_vnd IS NULL AND cash_change_vnd IS NULL)
        OR (method = 'CASH' AND cash_received_vnd >= amount_vnd AND cash_change_vnd >= 0
            AND cash_received_vnd - cash_change_vnd = amount_vnd)),
    CONSTRAINT ck_sfc_confirmed_pairing CHECK (
        (status = 'CONFIRMED' AND confirmed_at IS NOT NULL)
        OR (status <> 'CONFIRMED' AND confirmed_at IS NULL)),
    CONSTRAINT ck_sfc_cash_day CHECK (method = 'CASH' OR cash_business_day IS NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- FR-036: one cash close per branch per Vietnam working day. A difference needs a reason and a Manager.
CREATE TABLE settlement_cash_day_closes (
    id BIGINT NOT NULL AUTO_INCREMENT,
    close_reference VARCHAR(128) NOT NULL,
    branch_id BIGINT NOT NULL,
    business_day DATE NOT NULL,
    expected_cash_vnd BIGINT NOT NULL,
    counted_cash_vnd BIGINT NOT NULL,
    difference_vnd BIGINT NOT NULL,
    difference_reason VARCHAR(1000) NULL DEFAULT NULL,
    status ENUM('CLOSED', 'PENDING_MANAGER_CONFIRMATION') NOT NULL,
    closed_by_account_id BIGINT NOT NULL,
    closed_at DATETIME(6) NOT NULL,
    confirmed_by_account_id BIGINT NULL DEFAULT NULL,
    confirmed_at DATETIME(6) NULL DEFAULT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_scdc_close_reference (close_reference),
    UNIQUE KEY uq_scdc_branch_day (branch_id, business_day),
    CONSTRAINT ck_scdc_amounts CHECK (
        expected_cash_vnd >= 0 AND counted_cash_vnd >= 0
        AND difference_vnd = counted_cash_vnd - expected_cash_vnd),
    -- A difference may not be closed silently: it carries a reason and waits for a Manager.
    CONSTRAINT ck_scdc_difference_reason CHECK (
        difference_vnd = 0 OR (difference_reason IS NOT NULL AND difference_reason <> '')),
    CONSTRAINT ck_scdc_status CHECK (
        (status = 'CLOSED' AND (difference_vnd = 0 OR confirmed_by_account_id IS NOT NULL))
        OR (status = 'PENDING_MANAGER_CONFIRMATION' AND difference_vnd <> 0)),
    CONSTRAINT ck_scdc_confirmed_pairing CHECK (
        (confirmed_by_account_id IS NULL AND confirmed_at IS NULL)
        OR (confirmed_by_account_id IS NOT NULL AND confirmed_at IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- FR-035: handing a product over before verification, and recording a real debt, are Manager-only exceptions.
ALTER TABLE settlement_final_bills
    ADD COLUMN handover_approved_by_account_id BIGINT NULL DEFAULT NULL,
    ADD COLUMN handover_reason VARCHAR(1000) NULL DEFAULT NULL,
    ADD COLUMN handover_approved_at DATETIME(6) NULL DEFAULT NULL,
    ADD COLUMN debt_amount_vnd BIGINT NULL DEFAULT NULL,
    ADD COLUMN debt_reason VARCHAR(1000) NULL DEFAULT NULL,
    ADD COLUMN debt_recorded_by_account_id BIGINT NULL DEFAULT NULL,
    ADD COLUMN debt_recorded_at DATETIME(6) NULL DEFAULT NULL,
    ADD COLUMN debt_closed_by_account_id BIGINT NULL DEFAULT NULL,
    ADD COLUMN debt_closed_at DATETIME(6) NULL DEFAULT NULL;
