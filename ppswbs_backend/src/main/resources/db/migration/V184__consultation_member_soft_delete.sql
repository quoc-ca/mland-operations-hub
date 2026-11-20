-- V184: Member-side soft delete for consultation conversations

ALTER TABLE consultation_conversations
    MODIFY COLUMN context_type VARCHAR(32) NULL,
    MODIFY COLUMN context_reference VARCHAR(64) NULL,
    MODIFY COLUMN context_version INT NULL,
    MODIFY COLUMN context_snapshot JSON NULL,
    ADD COLUMN member_hidden_at TIMESTAMP(6) NULL,
    ADD COLUMN member_hidden_by_user_id BIGINT NULL;

CREATE INDEX idx_consultation_member_hidden_updated
    ON consultation_conversations (member_user_id, member_hidden_at, updated_at);
