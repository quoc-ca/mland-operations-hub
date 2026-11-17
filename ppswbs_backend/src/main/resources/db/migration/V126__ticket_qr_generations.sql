-- 003.2 FR-033 / T093: QR rotation on recipient change. The active QR stays on attendance_tickets;
-- every revoked QR is kept here so check-in can refuse it with TICKET_QR_REVOKED.
ALTER TABLE attendance_tickets
    ADD COLUMN qr_generation INT NOT NULL DEFAULT 1,
    ADD CONSTRAINT ck_at_qr_generation CHECK (qr_generation >= 1);

CREATE TABLE ticket_qr_generations (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    ticket_id BIGINT UNSIGNED NOT NULL,
    qr_generation INT NOT NULL,
    qr_reference VARCHAR(64) NOT NULL,
    revoke_reason VARCHAR(32) NOT NULL,
    revoked_at DATETIME(6) NOT NULL,
    correlation_reference VARCHAR(64) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_tqg_qr (qr_reference),
    UNIQUE KEY uq_tqg_ticket_generation (ticket_id, qr_generation),
    CONSTRAINT fk_tqg_ticket FOREIGN KEY (ticket_id) REFERENCES attendance_tickets (id),
    CONSTRAINT ck_tqg_generation CHECK (qr_generation >= 1),
    CONSTRAINT ck_tqg_reason CHECK (revoke_reason IN ('RECIPIENT_CHANGED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
