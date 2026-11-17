-- Decision 2026-11-17 (Owner 003): a slot is only a time window; capacity belongs to each session.
-- Existing workshop_sessions.capacity values were already copied and are kept as is.
ALTER TABLE workshop_slots DROP COLUMN default_capacity;
