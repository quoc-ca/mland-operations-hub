-- 003.3 T169 (FR-022a, CHK019): Idempotency-Key on the refund-account link. One row per (link token hash, key). The
-- request is kept only as a keyed (HMAC) fingerprint, never the account number, holder name or token. The row is
-- removed 30 days after the link expires (retain_until), by the refund-account sweep.
CREATE TABLE settlement_refund_link_submissions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    token_hash VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    retain_until DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_srls_token_key (token_hash, idempotency_key),
    KEY idx_srls_retain_until (retain_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
