ALTER TABLE retail_order_items
    DROP FOREIGN KEY fk_retail_order_items_order;

ALTER TABLE retail_pickup_evidence
    DROP FOREIGN KEY fk_retail_pickup_order;

ALTER TABLE retail_orders
    MODIFY COLUMN id VARCHAR(36) NOT NULL;

ALTER TABLE retail_order_items
    MODIFY COLUMN id VARCHAR(36) NOT NULL,
    MODIFY COLUMN retail_order_id VARCHAR(36) NOT NULL,
    MODIFY COLUMN quantity INT NOT NULL;

ALTER TABLE retail_pickup_evidence
    MODIFY COLUMN id VARCHAR(36) NOT NULL,
    MODIFY COLUMN retail_order_id VARCHAR(36) NOT NULL;

ALTER TABLE retail_order_items
    ADD CONSTRAINT fk_retail_order_items_order
        FOREIGN KEY (retail_order_id) REFERENCES retail_orders (id) ON DELETE CASCADE;

ALTER TABLE retail_pickup_evidence
    ADD CONSTRAINT fk_retail_pickup_order
        FOREIGN KEY (retail_order_id) REFERENCES retail_orders (id);
