-- V12__loyalty_program_config_version.sql
-- Schema migration for Feature FE-06 (decided 2026-10-27 by the 006 owner).
-- The voucher quota counters live on promotion_program, so the row version (`version`, optimistic lock) also moves
-- whenever a checkout holds or settles a use. A Manager editing a programme while it sells would then get a stale
-- version error for a change that is not a configuration change. config_version counts configuration changes only
-- (edit, pause, resume) and is what the API exposes as the programme `version` / `expectedVersion`.
-- Append-only: earlier migrations are not edited. Existing rows start at 0.

ALTER TABLE promotion_program
    ADD COLUMN config_version BIGINT NOT NULL DEFAULT 0 AFTER created_by;

ALTER TABLE promotion_program
    ADD CONSTRAINT chk_promotion_program_config_version CHECK (config_version >= 0);
