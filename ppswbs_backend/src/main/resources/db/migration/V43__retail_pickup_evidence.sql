ALTER TABLE retail_orders ADD COLUMN pickup_ready_confirmed BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE retail_pickup_evidence (
    id CHAR(36) NOT NULL PRIMARY KEY,
    retail_order_id CHAR(36) NOT NULL,
    location_id BIGINT UNSIGNED NOT NULL,
    actor_account_id BIGINT UNSIGNED NOT NULL,
    milestone VARCHAR(16) NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_retail_pickup_order FOREIGN KEY (retail_order_id) REFERENCES retail_orders(id),
    CONSTRAINT fk_retail_pickup_actor FOREIGN KEY (actor_account_id) REFERENCES accounts(id),
    CONSTRAINT chk_retail_pickup_milestone CHECK (milestone IN ('PREPARED', 'HANDED_OVER')),
    UNIQUE KEY uq_retail_pickup_order_milestone (retail_order_id, milestone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
