-- FE-08 / FE-10 (Session 2026-11-14): the moment a branch order became PAID, so the Manager dashboard can put
-- verified retail revenue in the right Vietnam-time period. Set by RetailOrder.markPaid() from now on.
ALTER TABLE retail_orders
    ADD COLUMN paid_at DATETIME(6) NULL DEFAULT NULL AFTER payment_state;

-- Existing paid orders: counter cash takes the Staff confirmation time; others the checkout's last update time.
UPDATE retail_orders o
JOIN retail_cash_collections c ON c.retail_order_id = o.id
SET o.paid_at = c.confirmed_at
WHERE o.payment_state = 'PAID' AND o.paid_at IS NULL;

UPDATE retail_orders o
JOIN retail_grouped_checkouts g ON g.id = o.grouped_checkout_id
SET o.paid_at = g.updated_at
WHERE o.payment_state = 'PAID' AND o.paid_at IS NULL;

CREATE INDEX idx_retail_orders_location_paid_at ON retail_orders (location_id, paid_at);
