-- Durable FE-08 quote and per-location/item snapshots; existing demo checkouts remain unchanged.
CREATE TABLE retail_checkout_quotes (
    id CHAR(36) NOT NULL,
    member_account_id BIGINT UNSIGNED NOT NULL,
    cart_id CHAR(36) NOT NULL,
    cart_version BIGINT NOT NULL,
    selection_choice VARCHAR(16) NOT NULL,
    voucher_code VARCHAR(128) NULL,
    requested_points DECIMAL(19,0) NOT NULL,
    terms_digest VARCHAR(128) NOT NULL,
    currency CHAR(3) NOT NULL,
    merchandise_subtotal DECIMAL(19,0) NOT NULL,
    shipping_total DECIMAL(19,0) NOT NULL,
    program_discount DECIMAL(19,0) NOT NULL,
    points_discount DECIMAL(19,0) NOT NULL,
    payable_total DECIMAL(19,0) NOT NULL,
    status VARCHAR(16) NOT NULL,
    accepted_checkout_id CHAR(36) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    expires_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_retail_quote_member FOREIGN KEY (member_account_id) REFERENCES accounts (id),
    CONSTRAINT fk_retail_quote_cart FOREIGN KEY (cart_id) REFERENCES retail_carts (id),
    CONSTRAINT fk_retail_quote_checkout FOREIGN KEY (accepted_checkout_id) REFERENCES retail_grouped_checkouts (id),
    UNIQUE KEY uq_retail_quote_checkout (accepted_checkout_id),
    KEY idx_retail_quote_member_expiry (member_account_id, expires_at),
    CONSTRAINT chk_retail_quote_selection CHECK (
        (selection_choice = 'VOUCHER' AND voucher_code IS NOT NULL AND CHAR_LENGTH(TRIM(voucher_code)) > 0)
        OR (selection_choice IN ('NONE', 'AUTO') AND voucher_code IS NULL)),
    CONSTRAINT chk_retail_quote_state CHECK (
        (status = 'OPEN' AND accepted_checkout_id IS NULL)
        OR (status = 'ACCEPTED' AND accepted_checkout_id IS NOT NULL)),
    CONSTRAINT chk_retail_quote_amounts CHECK (
        requested_points >= 0 AND merchandise_subtotal >= 0 AND shipping_total >= 0
        AND program_discount >= 0 AND points_discount >= 0 AND payable_total >= 0
        AND payable_total = merchandise_subtotal + shipping_total - program_discount - points_discount),
    CONSTRAINT chk_retail_quote_expiry CHECK (expires_at > created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE retail_checkout_quote_branches (
    id CHAR(36) NOT NULL,
    quote_id CHAR(36) NOT NULL,
    location_id BIGINT UNSIGNED NOT NULL,
    fulfilment_method VARCHAR(16) NOT NULL,
    shipping_quote_id VARCHAR(128) NULL,
    shipping_charge DECIMAL(19,0) NOT NULL,
    subtotal DECIMAL(19,0) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_retail_quote_branch_quote FOREIGN KEY (quote_id) REFERENCES retail_checkout_quotes (id) ON DELETE CASCADE,
    UNIQUE KEY uq_retail_quote_branch_location (quote_id, location_id),
    CONSTRAINT chk_retail_quote_branch_amounts CHECK (shipping_charge >= 0 AND subtotal >= 0),
    CONSTRAINT chk_retail_quote_branch_method CHECK (
        (fulfilment_method = 'PICKUP' AND shipping_quote_id IS NULL AND shipping_charge = 0)
        OR (fulfilment_method = 'GHTK' AND shipping_quote_id IS NOT NULL AND CHAR_LENGTH(TRIM(shipping_quote_id)) > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE retail_checkout_quote_items (
    id CHAR(36) NOT NULL,
    quote_branch_id CHAR(36) NOT NULL,
    product_id BIGINT UNSIGNED NOT NULL,
    variant_id BIGINT UNSIGNED NOT NULL,
    quantity TINYINT UNSIGNED NOT NULL,
    unit_price DECIMAL(19,0) NOT NULL,
    line_total DECIMAL(19,0) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_retail_quote_item_branch FOREIGN KEY (quote_branch_id) REFERENCES retail_checkout_quote_branches (id) ON DELETE CASCADE,
    UNIQUE KEY uq_retail_quote_item_variant (quote_branch_id, variant_id),
    CONSTRAINT chk_retail_quote_item_quantity CHECK (quantity BETWEEN 1 AND 99),
    CONSTRAINT chk_retail_quote_item_amounts CHECK (unit_price >= 0 AND line_total = unit_price * quantity)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
