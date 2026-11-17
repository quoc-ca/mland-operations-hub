-- FE-08 T124 / FE-02 FR-053: holds, guard and settlement per size x branch.
-- A hold now belongs to (grouped checkout, variant, branch) so a hold at one branch never reduces availability
-- at another branch. The size-level guard table stays: branch mutations lock the size row first and then the
-- branch row, so callers that still lock by size are serialized with branch callers.

ALTER TABLE retail_variant_holds
    ADD COLUMN location_id BIGINT UNSIGNED NULL AFTER variant_id;

-- Backfill from the accepted branch orders of the same checkout. Before this migration one hold row summed a
-- size across branches; a checkout that bought the same size at several branches (development data only) is
-- attributed to the lowest branch id. Active holds of that kind expire within their original deadline.
UPDATE retail_variant_holds hold
    JOIN (
        SELECT o.grouped_checkout_id AS checkout_id, i.variant_id AS variant_id, MIN(o.location_id) AS location_id
        FROM retail_orders o
        JOIN retail_order_items i ON i.retail_order_id = o.id
        GROUP BY o.grouped_checkout_id, i.variant_id
    ) source ON source.checkout_id = hold.grouped_checkout_id AND source.variant_id = hold.variant_id
SET hold.location_id = source.location_id
WHERE hold.location_id IS NULL;

-- Holds without any matching order item cannot be attributed; they are never valid business holds.
UPDATE retail_variant_holds
SET status = 'RELEASED'
WHERE location_id IS NULL AND status = 'HELD';
UPDATE retail_variant_holds
SET location_id = 0
WHERE location_id IS NULL;

ALTER TABLE retail_variant_holds
    MODIFY COLUMN location_id BIGINT UNSIGNED NOT NULL;

-- Add the branch-scoped unique key before dropping the old one: both start with grouped_checkout_id, which the
-- checkout foreign key needs as an index.
ALTER TABLE retail_variant_holds
    ADD UNIQUE KEY uq_retail_variant_holds_checkout_variant_location (grouped_checkout_id, variant_id, location_id);
ALTER TABLE retail_variant_holds
    DROP INDEX uq_retail_variant_holds_checkout_variant;
ALTER TABLE retail_variant_holds
    ADD KEY idx_retail_variant_holds_branch_active (variant_id, location_id, status, expires_at);

CREATE TABLE retail_branch_availability_guards (
    variant_id BIGINT UNSIGNED NOT NULL,
    location_id BIGINT UNSIGNED NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (variant_id, location_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
