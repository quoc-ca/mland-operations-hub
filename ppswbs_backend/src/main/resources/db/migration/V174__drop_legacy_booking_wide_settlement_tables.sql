-- 003.3 T174: the booking-wide settlement model was removed (T121, unrequested by the spec). These three tables have
-- no entity, repository or reader left; per-ticket money lives in settlement_folio_collections and
-- settlement_refund_requests.
DROP TABLE IF EXISTS settlement_final_payment_attempts;
DROP TABLE IF EXISTS settlement_customer_cancellation_snapshots;
DROP TABLE IF EXISTS settlement_mland_remedy_snapshots;
