-- 003.3 T135 (FR-057): the Manager's documentation-only record of an undercharge Mland absorbs on a FINALIZED ticket
-- folio. It collects nothing and changes no folio, line, balance, Loyalty earn, cash or cash close. Immutable except
-- the one void (ACTIVE -> VOIDED). The cap against the folio total (T156) is checked in the service under the folio lock.
CREATE TABLE settlement_absorbed_undercharges (
    id BIGINT NOT NULL AUTO_INCREMENT,
    undercharge_reference VARCHAR(128) NOT NULL,
    ticket_code VARCHAR(128) NOT NULL,
    bill_reference VARCHAR(128) NOT NULL,
    location_reference BIGINT NOT NULL,
    amount_vnd BIGINT NOT NULL,
    currency_code CHAR(3) NOT NULL DEFAULT 'VND',
    reason VARCHAR(1000) NOT NULL,
    recorded_by_account_id BIGINT NOT NULL,
    recorded_at TIMESTAMP(6) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    status VARCHAR(16) NOT NULL,
    void_reason VARCHAR(1000) NULL,
    voided_by_account_id BIGINT NULL,
    voided_at TIMESTAMP(6) NULL,
    void_idempotency_key VARCHAR(128) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_settlement_absorbed_undercharge_ref (undercharge_reference),
    UNIQUE KEY uq_settlement_absorbed_undercharge_idem (ticket_code, idempotency_key),
    KEY ix_settlement_absorbed_undercharge_ticket (ticket_code, status),
    KEY ix_settlement_absorbed_undercharge_period (location_reference, recorded_at, status),
    CONSTRAINT ck_settlement_absorbed_undercharge_amount CHECK (amount_vnd > 0),
    CONSTRAINT ck_settlement_absorbed_undercharge_currency CHECK (currency_code = 'VND'),
    CONSTRAINT ck_settlement_absorbed_undercharge_status CHECK (status IN ('ACTIVE', 'VOIDED')),
    CONSTRAINT ck_settlement_absorbed_undercharge_void CHECK (
        (status = 'ACTIVE' AND voided_at IS NULL AND void_reason IS NULL)
        OR (status = 'VOIDED' AND voided_at IS NOT NULL AND void_reason IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
