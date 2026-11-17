-- V155: 003.3 (owner decision 2026-11-15, Hồ Hoàng Quốc). VNPay Refund and refund-QueryDR are no longer used: every
-- refund is the manual bank refund of FR-038/FR-039/FR-040 (see bank_refund_reference). These columns and the index
-- polling them only ever stored VNPay's answer to a refund request (V45) and no code reads or writes them any more.
-- The original_* columns stay: they record the customer's original payment, which a bank refund still points to.
ALTER TABLE settlement_refund_allocations
    DROP INDEX ix_settlement_refund_polling,
    DROP COLUMN provider_refund_transaction_number,
    DROP COLUMN provider_response_code,
    DROP COLUMN provider_transaction_type,
    DROP COLUMN provider_transaction_status,
    DROP COLUMN provider_accepted_at,
    DROP COLUMN last_query_at;
