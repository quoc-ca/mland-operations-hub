ALTER TABLE retail_checkout_quotes
    DROP FOREIGN KEY fk_retail_quote_cart;

ALTER TABLE retail_carts
    MODIFY COLUMN id VARCHAR(36) NOT NULL;

ALTER TABLE retail_cart_lines
    MODIFY COLUMN cart_id VARCHAR(36) NOT NULL;

ALTER TABLE retail_checkout_quotes
    MODIFY COLUMN cart_id VARCHAR(36) NOT NULL;

ALTER TABLE retail_cart_lines
    ADD CONSTRAINT fk_retail_cart_lines_cart
        FOREIGN KEY (cart_id) REFERENCES retail_carts (id) ON DELETE CASCADE;

ALTER TABLE retail_checkout_quotes
    ADD CONSTRAINT fk_retail_quote_cart
        FOREIGN KEY (cart_id) REFERENCES retail_carts (id);
