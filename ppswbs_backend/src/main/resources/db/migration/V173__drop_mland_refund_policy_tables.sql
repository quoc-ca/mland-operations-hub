-- 003.3 T139 (FR-043, decided 2026-11-15): the Mland-cancellation refund policy is removed. Mland always refunds 100%,
-- so these tables only held percentage parameters and per-booking copies of them, never payment evidence. The history
-- of policy changes stays in settlement_business_audits. No archive or export by decision.
DROP TABLE IF EXISTS settlement_booking_mland_refund_policy_snapshots;
DROP TABLE IF EXISTS settlement_refund_policy_snapshots;
DROP TABLE IF EXISTS settlement_refund_policy_revisions;
