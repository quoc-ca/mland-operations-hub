-- V203: removes OTA connectivity from the database (owner decision 2026-11-25, see
-- ppswbs-spec/features/003-workshop-management/ota-removal-2026-11-25.md). Destructive: the OTA tables and the
-- shop add-on bill tables of OTA bookings are dropped with their rows.
--
-- Guard: rows of shared tables that carry an OTA value, and OTA add-on bills, are never deleted or rewritten
-- silently. When any exists the CHECK below fails before anything is changed. Recovery: back up the schema,
-- decide what to do with those rows (a local development database can simply be recreated), delete the failed
-- 203 row from flyway_schema_history and start again.
CREATE TEMPORARY TABLE v203_guard (
    source_name VARCHAR(64) NOT NULL,
    leftover_rows BIGINT NOT NULL,
    CONSTRAINT ck_v203_no_leftover_ota_rows CHECK (leftover_rows = 0)
);

INSERT INTO v203_guard
SELECT 'attendance_tickets.sales_channel', COUNT(*) FROM attendance_tickets WHERE sales_channel <> 'DIRECT';
INSERT INTO v203_guard
SELECT 'settlement_refund_requests.reason', COUNT(*) FROM settlement_refund_requests
WHERE reason = 'OTA_ADDON_OVERCHARGE';
INSERT INTO v203_guard
SELECT 'settlement_cases.case_type', COUNT(*) FROM settlement_cases WHERE case_type = 'ADDON_REFUND';
INSERT INTO v203_guard
SELECT 'settlement_evidence_conclusions.conclusion', COUNT(*) FROM settlement_evidence_conclusions
WHERE conclusion = 'SHOP_ADDON_VERIFIED';
INSERT INTO v203_guard
SELECT 'settlement_addon_bills', COUNT(*) FROM settlement_addon_bills;
INSERT INTO v203_guard
SELECT 'workshop_bookings.booking_code', COUNT(*) FROM workshop_bookings WHERE booking_code LIKE 'OTA-%';

DROP TEMPORARY TABLE v203_guard;

-- OTA tables, referencing tables first.
DROP TABLE ota_booking_ticket_links;
DROP TABLE ota_customer_payment_evidence;
DROP TABLE ota_addon_bill_links;
DROP TABLE ota_payout_ledger_entries;
DROP TABLE ota_reconciliation_decisions;
DROP TABLE ota_reconciliation_work_items;
DROP TABLE ota_payout_positions;
DROP TABLE ota_channel_bookings;
DROP TABLE ota_delivery_attempts;
DROP TABLE ota_outbound_changes;
DROP TABLE ota_inbound_events;
DROP TABLE ota_readiness_check_evidence;
DROP TABLE ota_readiness_assessments;
DROP TABLE ota_control_outbox;
DROP TABLE ota_channel_price_snapshots;
DROP TABLE ota_channel_mappings;
DROP TABLE ota_channel_connections;
DROP TABLE workshop_session_ota_snapshots;

-- Shop add-on bill of an OTA booking: bill, payment attempts and refunds (V99, V104).
DROP TABLE settlement_addon_refunds;
DROP TABLE settlement_addon_payment_attempts;
DROP TABLE settlement_addon_bills;

-- Every ticket is sold by Mland, so the sales channel (V154) carries no information.
ALTER TABLE attendance_tickets DROP COLUMN sales_channel;

ALTER TABLE settlement_refund_requests
    MODIFY COLUMN reason ENUM('MLAND_CANCELLATION', 'LATE_PAYMENT_NOT_RECONFIRMED', 'OVERCHARGE_AFTER_FINALIZED',
        'EXCESS_OR_DUPLICATE_MONEY') NOT NULL;
