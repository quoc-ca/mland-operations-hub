-- V161 seeded demo sessions with check_in_opens_at = start - 30 minutes while marking them DEFAULT.
-- Spec 003.2 FR-005a and WorkshopSessionService define the default as 60 minutes before start.
-- Only rows that are still DEFAULT and exactly 30 minutes before start are corrected;
-- MANAGER_SET rows are left untouched.
UPDATE workshop_sessions
SET check_in_opens_at = scheduled_start_at - INTERVAL 60 MINUTE
WHERE check_in_opening_source = 'DEFAULT'
  AND check_in_opens_at = scheduled_start_at - INTERVAL 30 MINUTE;
