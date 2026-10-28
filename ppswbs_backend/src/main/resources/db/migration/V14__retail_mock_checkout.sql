CREATE TABLE retail_grouped_checkouts (
    id CHAR(36) NOT NULL,
    member_account_id BIGINT UNSIGNED NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    cart_version BIGINT NOT NULL,
    currency CHAR(3) NOT NULL,
    merchandise_subtotal DECIMAL(19,2) NOT NULL,
    shipping_charge DECIMAL(19,2) NOT NULL,
    discount_amount DECIMAL(19,2) NOT NULL,
    payable_amount DECIMAL(19,2) NOT NULL,
    payment_state VARCHAR(32) NOT NULL,
    payment_initiated_at DATETIME(6) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_retail_grouped_checkouts_member FOREIGN KEY (member_account_id) REFERENCES accounts (id),
    CONSTRAINT chk_retail_grouped_checkouts_non_negative CHECK (merchandise_subtotal >= 0 AND shipping_charge >= 0 AND discount_amount >= 0 AND payable_amount >= 0),
    UNIQUE KEY uq_retail_grouped_checkouts_member_idempotency (member_account_id, idempotency_key),
    KEY idx_retail_grouped_checkouts_member_created (member_account_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE retail_orders (
    id CHAR(36) NOT NULL,
    grouped_checkout_id CHAR(36) NOT NULL,
    location_id BIGINT UNSIGNED NOT NULL,
    fulfilment_method VARCHAR(16) NOT NULL,
    payment_state VARCHAR(32) NOT NULL,
    fulfilment_state VARCHAR(32) NOT NULL,
    subtotal DECIMAL(19,2) NOT NULL,
    shipping_charge DECIMAL(19,2) NOT NULL,
    payable_amount DECIMAL(19,2) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_retail_orders_grouped_checkout FOREIGN KEY (grouped_checkout_id) REFERENCES retail_grouped_checkouts (id),
    CONSTRAINT chk_retail_orders_non_negative CHECK (subtotal >= 0 AND shipping_charge >= 0 AND payable_amount >= 0),
    UNIQUE KEY uq_retail_orders_checkout_location (grouped_checkout_id, location_id),
    KEY idx_retail_orders_checkout (grouped_checkout_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE retail_order_items (
    id CHAR(36) NOT NULL,
    retail_order_id CHAR(36) NOT NULL,
    variant_id VARCHAR(128) NOT NULL,
    quantity TINYINT UNSIGNED NOT NULL,
    unit_price DECIMAL(19,2) NOT NULL,
    line_total DECIMAL(19,2) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_retail_order_items_order FOREIGN KEY (retail_order_id) REFERENCES retail_orders (id) ON DELETE CASCADE,
    CONSTRAINT chk_retail_order_items_quantity CHECK (quantity BETWEEN 1 AND 99),
    CONSTRAINT chk_retail_order_items_non_negative CHECK (unit_price >= 0 AND line_total >= 0),
    KEY idx_retail_order_items_order (retail_order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE retail_checkout_holds (
    id CHAR(36) NOT NULL,
    grouped_checkout_id CHAR(36) NOT NULL,
    status VARCHAR(32) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_retail_checkout_holds_checkout FOREIGN KEY (grouped_checkout_id) REFERENCES retail_grouped_checkouts (id) ON DELETE CASCADE,
    UNIQUE KEY uq_retail_checkout_holds_checkout (grouped_checkout_id),
    KEY idx_retail_checkout_holds_status_expiry (status, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
