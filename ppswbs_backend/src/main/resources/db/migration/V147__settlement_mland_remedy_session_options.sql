-- V147: 003.3 T112 (FR-043, decided 2026-11-14). A Mland cancellation offers EVERY valid equivalent session and the
-- buyer chooses one, so the offer must keep the whole list (with each start time) and the chosen session. The list is
-- unbounded, so the old 512-character reference column becomes TEXT. Rows already written keep their values; an
-- offer without options is read from replacement_session_references and replacement_start_at.
ALTER TABLE settlement_ticket_mland_remedies
    MODIFY COLUMN replacement_session_references TEXT NOT NULL,
    ADD COLUMN replacement_options TEXT NULL AFTER replacement_session_references,
    ADD COLUMN selected_replacement_session_reference VARCHAR(64) NULL AFTER buyer_answer;
