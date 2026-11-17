-- V179: 003.3 T186 (FR-060, Q5 option A, decided 2026-11-17). A ticket may move to another location, so the branch of a
-- VNPay EXCESS money row is fixed when its evidence is recorded and never follows the ticket afterwards. Rows written
-- before this migration stay NULL and are read against the ticket's current location, as before.
ALTER TABLE settlement_evidence_conclusions
    ADD COLUMN branch_id BIGINT NULL AFTER amount_vnd;
