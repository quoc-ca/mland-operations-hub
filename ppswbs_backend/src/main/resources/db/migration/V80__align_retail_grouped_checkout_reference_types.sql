ALTER TABLE retail_checkout_quotes
    DROP FOREIGN KEY fk_retail_quote_checkout;

ALTER TABLE retail_orders
    DROP FOREIGN KEY fk_retail_orders_grouped_checkout;

ALTER TABLE retail_variant_holds
    DROP FOREIGN KEY fk_retail_variant_holds_checkout;

ALTER TABLE retail_grouped_checkouts
    MODIFY COLUMN id VARCHAR(36) NOT NULL;

ALTER TABLE retail_checkout_holds
    MODIFY COLUMN grouped_checkout_id VARCHAR(36) NOT NULL;

ALTER TABLE retail_checkout_quotes
    MODIFY COLUMN accepted_checkout_id VARCHAR(36) NULL;

ALTER TABLE retail_orders
    MODIFY COLUMN grouped_checkout_id VARCHAR(36) NOT NULL;

ALTER TABLE retail_variant_holds
    MODIFY COLUMN grouped_checkout_id VARCHAR(36) NOT NULL;

ALTER TABLE retail_checkout_holds
    ADD CONSTRAINT fk_retail_checkout_holds_checkout
        FOREIGN KEY (grouped_checkout_id) REFERENCES retail_grouped_checkouts (id) ON DELETE CASCADE;

ALTER TABLE retail_checkout_quotes
    ADD CONSTRAINT fk_retail_quote_checkout
        FOREIGN KEY (accepted_checkout_id) REFERENCES retail_grouped_checkouts (id);

ALTER TABLE retail_orders
    ADD CONSTRAINT fk_retail_orders_grouped_checkout
        FOREIGN KEY (grouped_checkout_id) REFERENCES retail_grouped_checkouts (id);

ALTER TABLE retail_variant_holds
    ADD CONSTRAINT fk_retail_variant_holds_checkout
        FOREIGN KEY (grouped_checkout_id) REFERENCES retail_grouped_checkouts (id) ON DELETE CASCADE;
