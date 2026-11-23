-- Align any legacy COMPLETED payment state in retail_grouped_checkouts to PAID
UPDATE retail_grouped_checkouts
SET payment_state = 'PAID'
WHERE payment_state = 'COMPLETED';
