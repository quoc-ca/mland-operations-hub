CREATE TABLE retail_carts (
    id CHAR(36) NOT NULL,
    member_account_id BIGINT UNSIGNED NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_retail_carts_member FOREIGN KEY (member_account_id) REFERENCES accounts (id),
    UNIQUE KEY uq_retail_carts_member (member_account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE retail_cart_lines (
    id CHAR(36) NOT NULL,
    cart_id CHAR(36) NOT NULL,
    variant_id VARCHAR(128) NOT NULL,
    location_id BIGINT UNSIGNED NOT NULL,
    quantity TINYINT UNSIGNED NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_retail_cart_lines_cart FOREIGN KEY (cart_id) REFERENCES retail_carts (id) ON DELETE CASCADE,
    CONSTRAINT chk_retail_cart_lines_quantity CHECK (quantity BETWEEN 1 AND 99),
    UNIQUE KEY uq_retail_cart_lines_cart_variant_location (cart_id, variant_id, location_id),
    KEY idx_retail_cart_lines_cart (cart_id),
    KEY idx_retail_cart_lines_variant (variant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
