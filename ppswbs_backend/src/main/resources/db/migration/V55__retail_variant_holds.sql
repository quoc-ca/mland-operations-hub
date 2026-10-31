CREATE TABLE retail_variant_availability_guards (
    variant_id BIGINT UNSIGNED NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (variant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE retail_variant_holds (
    id CHAR(36) NOT NULL,
    grouped_checkout_id CHAR(36) NOT NULL,
    variant_id BIGINT UNSIGNED NOT NULL,
    quantity TINYINT UNSIGNED NOT NULL,
    status VARCHAR(16) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_retail_variant_holds_checkout
        FOREIGN KEY (grouped_checkout_id) REFERENCES retail_grouped_checkouts (id) ON DELETE CASCADE,
    CONSTRAINT chk_retail_variant_holds_quantity CHECK (quantity BETWEEN 1 AND 99),
    CONSTRAINT chk_retail_variant_holds_status CHECK (status IN ('HELD', 'SETTLED', 'RELEASED', 'EXPIRED')),
    UNIQUE KEY uq_retail_variant_holds_checkout_variant (grouped_checkout_id, variant_id),
    KEY idx_retail_variant_holds_active (variant_id, status, expires_at),
    KEY idx_retail_variant_holds_expiry (status, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
