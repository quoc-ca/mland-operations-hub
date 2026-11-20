-- FE-08 Session 2026-11-20: when a branch order was received (picked up at the counter or delivered by GHTK), shown in
-- "Đơn mua" and the Staff history. Existing orders take it from the pickup handover or the shipment history.
ALTER TABLE retail_orders ADD COLUMN completed_at DATETIME(6) NULL DEFAULT NULL AFTER paid_at;

UPDATE retail_orders o
JOIN retail_pickup_evidence e ON e.retail_order_id = o.id AND e.milestone = 'HANDED_OVER'
SET o.completed_at = e.occurred_at
WHERE o.completed_at IS NULL;

UPDATE retail_orders o
JOIN retail_shipments s ON s.branch_order_id = o.id
JOIN retail_shipment_status_history h ON h.shipment_id = s.id AND h.status = 'DELIVERED'
SET o.completed_at = h.created_at
WHERE o.completed_at IS NULL;
