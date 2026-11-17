-- Demo data for Feature 003 workshop booking.
-- Target: ppswbs_workshop_test_20261029 only.
-- Safe to run repeatedly: existing rows are retained and demo rows are upserted.

USE ppswbs_workshop_test_20261029;
SET NAMES utf8mb4;

START TRANSACTION;

-- Keep existing fixture rows for foreign-key safety, but make the public catalogue
-- contain only the packages shown on the M.Land workshop poster.
UPDATE workshop_packages
SET state = 'PUBLISHED', updated_at = CURRENT_TIMESTAMP(6)
WHERE name IN (
  'Signature Workshop - Silver 925',
  'Signature Workshop - Brass',
  'Silver Clay Workshop - 5g',
  'Silver Clay Workshop - 7g',
  'Wax Casting Workshop'
);

UPDATE workshop_packages
SET state = 'RETIRED', updated_at = CURRENT_TIMESTAMP(6)
WHERE state = 'PUBLISHED'
  AND name NOT IN (
    'Signature Workshop - Silver 925',
    'Signature Workshop - Brass',
    'Silver Clay Workshop - 5g',
    'Silver Clay Workshop - 7g',
    'Wax Casting Workshop'
  );

INSERT INTO workshop_packages (
  name, description, public_price, min_participants, max_participants,
  required_session_count, same_location_required, minimum_same_day_slot_gap,
  required_foundry_working_days_between_sessions, state, created_at, updated_at
)
SELECT 'Signature Workshop - Silver 925',
       'Silver 925 (maximum 4g). Learn torch heating, soldering and shaping; take your personalised ring home the same day.',
       900000, 1, 8, 1, 1, 0, 0, 'PUBLISHED', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
WHERE NOT EXISTS (SELECT 1 FROM workshop_packages WHERE name = 'Signature Workshop - Silver 925');

INSERT INTO workshop_packages (
  name, description, public_price, min_participants, max_participants,
  required_session_count, same_location_required, minimum_same_day_slot_gap,
  required_foundry_working_days_between_sessions, state, created_at, updated_at
)
SELECT 'Signature Workshop - Brass',
       'Brass (maximum 10g). Includes a protective care kit; take your personalised piece home the same day.',
       750000, 1, 8, 1, 1, 0, 0, 'PUBLISHED', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
WHERE NOT EXISTS (SELECT 1 FROM workshop_packages WHERE name = 'Signature Workshop - Brass');

INSERT INTO workshop_packages (
  name, description, public_price, min_participants, max_participants,
  required_session_count, same_location_required, minimum_same_day_slot_gap,
  required_foundry_working_days_between_sessions, state, created_at, updated_at
)
SELECT 'Silver Clay Workshop - 5g',
       'Premium S999 silver clay, 5g package, standard ring design and up to 5 stones. Same-day pickup.',
       1350000, 1, 8, 1, 1, 0, 0, 'PUBLISHED', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
WHERE NOT EXISTS (SELECT 1 FROM workshop_packages WHERE name = 'Silver Clay Workshop - 5g');

INSERT INTO workshop_packages (
  name, description, public_price, min_participants, max_participants,
  required_session_count, same_location_required, minimum_same_day_slot_gap,
  required_foundry_working_days_between_sessions, state, created_at, updated_at
)
SELECT 'Silver Clay Workshop - 7g',
       'Premium S999 silver clay, 7g package with unlimited stones. Same-day pickup.',
       1950000, 1, 8, 1, 1, 0, 0, 'PUBLISHED', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
WHERE NOT EXISTS (SELECT 1 FROM workshop_packages WHERE name = 'Silver Clay Workshop - 7g');

INSERT INTO workshop_packages (
  name, description, public_price, min_participants, max_participants,
  required_session_count, same_location_required, minimum_same_day_slot_gap,
  required_foundry_working_days_between_sessions, state, created_at, updated_at
)
SELECT 'Wax Casting Workshop',
       'S999/S925/Gold casting experience. Two sessions with exactly two foundry working days between sessions; metal cost is not included.',
       1000000, 1, 8, 2, 1, 0, 2, 'PUBLISHED', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
WHERE NOT EXISTS (SELECT 1 FROM workshop_packages WHERE name = 'Wax Casting Workshop');

-- One realistic demo branch, with the three poster slots: 09:30-12:00,
-- 13:00-16:00 and 16:00-19:00.
INSERT INTO workshop_locations (code, display_name, address_snapshot, active, created_at, updated_at)
SELECT 'MLAND-THUDUC-DEMO',
       'MLAND Thủ Đức',
       '45 Võ Văn Ngân, Linh Chiểu, TP. Thủ Đức',
       1, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
WHERE NOT EXISTS (SELECT 1 FROM workshop_locations WHERE code = 'MLAND-THUDUC-DEMO');

-- Use the first existing test location as the canonical public demo branch so
-- the current frontend default selection immediately has sessions available.
UPDATE workshop_locations
SET display_name = 'MLAND Thủ Đức',
    address_snapshot = '45 Võ Văn Ngân, Linh Chiểu, TP. Thủ Đức',
    active = 1,
    updated_at = CURRENT_TIMESTAMP(6)
WHERE id = 1;

UPDATE workshop_locations
SET active = 0, updated_at = CURRENT_TIMESTAMP(6)
WHERE code = 'MLAND-THUDUC-DEMO';

SET @demo_location_id = (
  SELECT id FROM workshop_locations WHERE id = 1 LIMIT 1
);

UPDATE workshop_slots
SET active = 0, updated_at = CURRENT_TIMESTAMP(6)
WHERE location_id = @demo_location_id AND label = 'Standard Morning Slot';

INSERT INTO workshop_slots (
  reference, location_id, label, start_time, end_time, default_capacity,
  active, version, created_at, updated_at
)
SELECT UUID(), @demo_location_id, 'Morning (09:30-12:00)', '09:30:00', '12:00:00', 8,
       1, 0, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
WHERE NOT EXISTS (
  SELECT 1 FROM workshop_slots
  WHERE location_id = @demo_location_id AND label = 'Morning (09:30-12:00)'
);

INSERT INTO workshop_slots (
  reference, location_id, label, start_time, end_time, default_capacity,
  active, version, created_at, updated_at
)
SELECT UUID(), @demo_location_id, 'Afternoon (13:00-16:00)', '13:00:00', '16:00:00', 8,
       1, 0, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
WHERE NOT EXISTS (
  SELECT 1 FROM workshop_slots
  WHERE location_id = @demo_location_id AND label = 'Afternoon (13:00-16:00)'
);

INSERT INTO workshop_slots (
  reference, location_id, label, start_time, end_time, default_capacity,
  active, version, created_at, updated_at
)
SELECT UUID(), @demo_location_id, 'Evening (16:00-19:00)', '16:00:00', '19:00:00', 8,
       1, 0, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
WHERE NOT EXISTS (
  SELECT 1 FROM workshop_slots
  WHERE location_id = @demo_location_id AND label = 'Evening (16:00-19:00)'
);

-- Future sessions for all three slots. The date range includes enough weekdays
-- for Wax Casting pairs (for example Mon->Thu and Fri->Wed) and weekends for
-- testing ordinary single-session packages.
INSERT INTO workshop_sessions (
  reference, location_id, slot_id, workshop_date, scheduled_start_at,
  scheduled_end_at, check_in_opens_at, check_in_opening_source, capacity,
  state, version, created_at, updated_at
)
WITH RECURSIVE demo_dates AS (
  SELECT DATE('2026-11-05') AS workshop_date
  UNION ALL
  SELECT workshop_date + INTERVAL 1 DAY
  FROM demo_dates
  WHERE workshop_date < DATE('2026-12-15')
)
SELECT UUID(), s.location_id, s.id, d.workshop_date,
       TIMESTAMP(d.workshop_date, s.start_time),
       TIMESTAMP(d.workshop_date, s.end_time),
       TIMESTAMP(d.workshop_date, s.start_time) - INTERVAL 30 MINUTE,
       'DEFAULT', s.default_capacity, 'PUBLISHED', 0,
       CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
FROM demo_dates d
JOIN workshop_slots s ON s.location_id = @demo_location_id AND s.active = 1
WHERE NOT EXISTS (
  SELECT 1 FROM workshop_sessions existing
  WHERE existing.location_id = s.location_id
    AND existing.workshop_date = d.workshop_date
    AND existing.slot_id = s.id
);

COMMIT;

-- Verification (read-only):
SELECT id, name, public_price, min_participants, max_participants,
       required_session_count, required_foundry_working_days_between_sessions, state
FROM workshop_packages
WHERE state = 'PUBLISHED'
ORDER BY id;

SELECT l.code, l.display_name, s.label, s.start_time, s.end_time,
       COUNT(ws.id) AS published_sessions
FROM workshop_locations l
JOIN workshop_slots s ON s.location_id = l.id AND s.active = 1
LEFT JOIN workshop_sessions ws ON ws.slot_id = s.id AND ws.state = 'PUBLISHED'
WHERE l.id = @demo_location_id
GROUP BY l.code, l.display_name, s.label, s.start_time, s.end_time
ORDER BY s.start_time;
