-- 003.2 FR-059: the automatic finalization writes attendance actions with no account (system actor).
ALTER TABLE attendance_actions
    MODIFY COLUMN actor_id BIGINT UNSIGNED NULL;
