CREATE TABLE settlement_folio_corrections (
    id BIGINT NOT NULL AUTO_INCREMENT,
    correction_reference VARCHAR(128) NOT NULL,
    case_reference VARCHAR(128) NOT NULL,
    booking_reference VARCHAR(128) NOT NULL,
    bill_reference VARCHAR(128) NOT NULL,
    amount_vnd BIGINT NOT NULL,
    currency_code CHAR(3) NOT NULL DEFAULT 'VND',
    reason VARCHAR(255) NOT NULL,
    manager_actor_reference VARCHAR(128) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_settlement_folio_correction_ref (correction_reference),
    UNIQUE KEY uq_settlement_folio_correction_case (case_reference),
    KEY ix_settlement_folio_correction_booking (booking_reference),
    KEY ix_settlement_folio_correction_bill (bill_reference),
    CONSTRAINT ck_settlement_folio_correction_amount CHECK (amount_vnd > 0),
    CONSTRAINT ck_settlement_folio_correction_currency CHECK (currency_code = 'VND')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

