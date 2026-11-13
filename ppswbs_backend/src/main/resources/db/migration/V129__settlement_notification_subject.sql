-- 003.3 FR-040 (decision 2026-11-13): the refund-account email (template 7) carries a subject rendered by
-- Settlement. Its private link reuses payment_action_link / payment_action_link_expires_at from V41.
ALTER TABLE settlement_notification_handoffs
    ADD COLUMN rendered_subject VARCHAR(255) NULL AFTER rendered_settlement_content;
