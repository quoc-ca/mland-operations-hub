-- One-file demo data for M.Land workshops: location, slots, packages, future sessions
-- and the four internal test accounts (ADMIN, MANAGER, STAFF, MEMBER).
--
-- Merges seed-workshop-demo.sql and seed-test-internal-accounts.sql into one script
-- that does not depend on a schema name. Choose the schema on the command line:
--   mysql --default-character-set=utf8mb4 -u <user> -p <schema> < seed-demo-data-all.sql
-- Requires MySQL 8 (recursive CTE) and a schema already migrated by Flyway.
-- Safe to re-run: existing rows are kept and demo rows are upserted.
-- Run it on YOUR local dev/test schema only, never on a shared database: it also
-- retires every PUBLISHED package that is not on the poster list below.
--
-- Accounts: the Firebase users below must exist in Firebase project
-- "mland-operation-hub". Passwords live in seed-test-internal-accounts.sql, not here.

SET NAMES utf8mb4;

START TRANSACTION;

-- ---------------------------------------------------------------------------
-- 1. Location (canonical id 1) and the three poster slots
-- ---------------------------------------------------------------------------
INSERT INTO workshop_locations (id, code, display_name, address_snapshot, active, created_at, updated_at)
VALUES (1, 'LOC-Q1', 'MLAND Thủ Đức', '45 Võ Văn Ngân, Linh Chiểu, TP. Thủ Đức', 1, NOW(6), NOW(6))
ON DUPLICATE KEY UPDATE display_name = VALUES(display_name),
                        address_snapshot = VALUES(address_snapshot),
                        active = 1,
                        updated_at = CURRENT_TIMESTAMP(6);

SET @location_id = 1;

UPDATE workshop_slots
SET active = 0, updated_at = CURRENT_TIMESTAMP(6)
WHERE location_id = @location_id AND label = 'Standard Morning Slot';

INSERT INTO workshop_slots (reference, location_id, label, start_time, end_time, default_capacity,
                            active, version, created_at, updated_at)
SELECT UUID(), @location_id, 'Morning (09:30-12:00)', '09:30:00', '12:00:00', 8,
       1, 0, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
WHERE NOT EXISTS (SELECT 1 FROM workshop_slots
                  WHERE location_id = @location_id AND start_time = '09:30:00' AND active = 1);

INSERT INTO workshop_slots (reference, location_id, label, start_time, end_time, default_capacity,
                            active, version, created_at, updated_at)
SELECT UUID(), @location_id, 'Afternoon (13:00-16:00)', '13:00:00', '16:00:00', 8,
       1, 0, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
WHERE NOT EXISTS (SELECT 1 FROM workshop_slots
                  WHERE location_id = @location_id AND start_time = '13:00:00' AND active = 1);

INSERT INTO workshop_slots (reference, location_id, label, start_time, end_time, default_capacity,
                            active, version, created_at, updated_at)
SELECT UUID(), @location_id, 'Evening (16:00-19:00)', '16:00:00', '19:00:00', 8,
       1, 0, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
WHERE NOT EXISTS (SELECT 1 FROM workshop_slots
                  WHERE location_id = @location_id AND start_time = '16:00:00' AND active = 1);

-- ---------------------------------------------------------------------------
-- 2. Workshop packages (the five on the M.Land poster)
-- ---------------------------------------------------------------------------
UPDATE workshop_packages
SET state = 'RETIRED', updated_at = CURRENT_TIMESTAMP(6)
WHERE state = 'PUBLISHED'
  AND name NOT IN ('Signature Workshop - Silver 925', 'Signature Workshop - Brass',
                   'Silver Clay Workshop - 5g', 'Silver Clay Workshop - 7g',
                   'Wax Casting Workshop');

INSERT INTO workshop_packages (name, description, public_price, min_participants, max_participants,
                               required_session_count, same_location_required, minimum_same_day_slot_gap,
                               required_foundry_working_days_between_sessions, state, created_at, updated_at)
SELECT 'Signature Workshop - Silver 925',
       'Silver 925 (maximum 4g). Learn torch heating, soldering and shaping; take your personalised ring home the same day.',
       900000, 1, 8, 1, 1, 0, 0, 'PUBLISHED', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
WHERE NOT EXISTS (SELECT 1 FROM workshop_packages WHERE name = 'Signature Workshop - Silver 925');

INSERT INTO workshop_packages (name, description, public_price, min_participants, max_participants,
                               required_session_count, same_location_required, minimum_same_day_slot_gap,
                               required_foundry_working_days_between_sessions, state, created_at, updated_at)
SELECT 'Signature Workshop - Brass',
       'Brass (maximum 10g). Includes a protective care kit; take your personalised piece home the same day.',
       750000, 1, 8, 1, 1, 0, 0, 'PUBLISHED', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
WHERE NOT EXISTS (SELECT 1 FROM workshop_packages WHERE name = 'Signature Workshop - Brass');

INSERT INTO workshop_packages (name, description, public_price, min_participants, max_participants,
                               required_session_count, same_location_required, minimum_same_day_slot_gap,
                               required_foundry_working_days_between_sessions, state, created_at, updated_at)
SELECT 'Silver Clay Workshop - 5g',
       'Premium S999 silver clay, 5g package, standard ring design and up to 5 stones. Same-day pickup.',
       1350000, 1, 8, 1, 1, 0, 0, 'PUBLISHED', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
WHERE NOT EXISTS (SELECT 1 FROM workshop_packages WHERE name = 'Silver Clay Workshop - 5g');

INSERT INTO workshop_packages (name, description, public_price, min_participants, max_participants,
                               required_session_count, same_location_required, minimum_same_day_slot_gap,
                               required_foundry_working_days_between_sessions, state, created_at, updated_at)
SELECT 'Silver Clay Workshop - 7g',
       'Premium S999 silver clay, 7g package with unlimited stones. Same-day pickup.',
       1950000, 1, 8, 1, 1, 0, 0, 'PUBLISHED', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
WHERE NOT EXISTS (SELECT 1 FROM workshop_packages WHERE name = 'Silver Clay Workshop - 7g');

INSERT INTO workshop_packages (name, description, public_price, min_participants, max_participants,
                               required_session_count, same_location_required, minimum_same_day_slot_gap,
                               required_foundry_working_days_between_sessions, state, created_at, updated_at)
SELECT 'Wax Casting Workshop',
       'S999/S925/Gold casting experience. Two sessions with at least two foundry working days between sessions; metal cost is not included.',
       1000000, 1, 8, 2, 1, 0, 2, 'PUBLISHED', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
WHERE NOT EXISTS (SELECT 1 FROM workshop_packages WHERE name = 'Wax Casting Workshop');

UPDATE workshop_packages
SET state = 'PUBLISHED', updated_at = CURRENT_TIMESTAMP(6)
WHERE name IN ('Signature Workshop - Silver 925', 'Signature Workshop - Brass',
               'Silver Clay Workshop - 5g', 'Silver Clay Workshop - 7g', 'Wax Casting Workshop')
  AND state <> 'PUBLISHED';

-- ---------------------------------------------------------------------------
-- 3. Future sessions for the three slots: tomorrow through 45 days ahead
--    (Asia/Ho_Chi_Minh), so there are always weekdays for Wax Casting pairs
--    (Mon->Thu, Fri->Wed) and weekends for single-session packages.
-- ---------------------------------------------------------------------------
SET @first_day = DATE(UTC_TIMESTAMP() + INTERVAL 7 HOUR) + INTERVAL 1 DAY;

INSERT INTO workshop_sessions (reference, location_id, slot_id, workshop_date, scheduled_start_at,
                               scheduled_end_at, check_in_opens_at, check_in_opening_source, capacity,
                               state, version, created_at, updated_at)
WITH RECURSIVE demo_dates AS (
  SELECT @first_day AS workshop_date
  UNION ALL
  SELECT workshop_date + INTERVAL 1 DAY FROM demo_dates
  WHERE workshop_date < @first_day + INTERVAL 44 DAY
)
SELECT UUID(), s.location_id, s.id, d.workshop_date,
       TIMESTAMP(d.workshop_date, s.start_time),
       TIMESTAMP(d.workshop_date, s.end_time),
       TIMESTAMP(d.workshop_date, s.start_time) - INTERVAL 30 MINUTE,
       'DEFAULT', s.default_capacity, 'PUBLISHED', 0,
       CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
FROM demo_dates d
JOIN workshop_slots s ON s.location_id = @location_id AND s.active = 1
WHERE NOT EXISTS (SELECT 1 FROM workshop_sessions existing
                  WHERE existing.location_id = s.location_id
                    AND existing.workshop_date = d.workshop_date
                    AND existing.slot_id = s.id);

-- ---------------------------------------------------------------------------
-- 4. Internal test accounts (Firebase UIDs of project mland-operation-hub)
-- ---------------------------------------------------------------------------
-- ADMIN  admin.test@mland.vn
INSERT INTO accounts (firebase_uid, login_email, role, status, version, created_at, updated_at)
VALUES ('9fxlc52Zu7S1ljw88EMN3qzW0Ib2', 'admin.test@mland.vn', 'ADMIN', 'ACTIVE', 0, NOW(6), NOW(6))
ON DUPLICATE KEY UPDATE role = 'ADMIN', status = 'ACTIVE', login_email = VALUES(login_email);
SET @admin_id = (SELECT id FROM accounts WHERE firebase_uid = '9fxlc52Zu7S1ljw88EMN3qzW0Ib2');
INSERT INTO account_profiles (account_id, full_name, phone, updated_at)
VALUES (@admin_id, 'Quản Trị Viên Hệ Thống', '0901000001', NOW(6))
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), phone = VALUES(phone);

-- MANAGER  manager.test@mland.vn (location 1)
INSERT INTO accounts (firebase_uid, login_email, role, status, version, created_at, updated_at)
VALUES ('xhW4zbdGu9dAiDpog4g8W1pMXYz2', 'manager.test@mland.vn', 'MANAGER', 'ACTIVE', 0, NOW(6), NOW(6))
ON DUPLICATE KEY UPDATE role = 'MANAGER', status = 'ACTIVE', login_email = VALUES(login_email);
SET @manager_id = (SELECT id FROM accounts WHERE firebase_uid = 'xhW4zbdGu9dAiDpog4g8W1pMXYz2');
INSERT INTO account_profiles (account_id, full_name, phone, updated_at)
VALUES (@manager_id, 'Quản Lý Chi Nhánh', '0901000002', NOW(6))
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), phone = VALUES(phone);
INSERT INTO account_location_assignments (account_id, location_id, assigned_at, active)
SELECT @manager_id, 1, NOW(6), 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM account_location_assignments
                  WHERE account_id = @manager_id AND location_id = 1 AND active = 1);

-- STAFF  staff.test@mland.vn (location 1)
INSERT INTO accounts (firebase_uid, login_email, role, status, version, created_at, updated_at)
VALUES ('uqgudNtSTkWTkncqK30eJHyZ3J72', 'staff.test@mland.vn', 'STAFF', 'ACTIVE', 0, NOW(6), NOW(6))
ON DUPLICATE KEY UPDATE role = 'STAFF', status = 'ACTIVE', login_email = VALUES(login_email);
SET @staff_id = (SELECT id FROM accounts WHERE firebase_uid = 'uqgudNtSTkWTkncqK30eJHyZ3J72');
INSERT INTO account_profiles (account_id, full_name, phone, updated_at)
VALUES (@staff_id, 'Nhân Viên Workshop', '0901000003', NOW(6))
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), phone = VALUES(phone);
INSERT INTO account_location_assignments (account_id, location_id, assigned_at, active)
SELECT @staff_id, 1, NOW(6), 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM account_location_assignments
                  WHERE account_id = @staff_id AND location_id = 1 AND active = 1);

-- MEMBER  customer.test@mland.vn
INSERT INTO accounts (firebase_uid, login_email, role, status, version, created_at, updated_at)
VALUES ('w5ZQh5PjrtZ0KXAfR2mEuBFmTO12', 'customer.test@mland.vn', 'MEMBER', 'ACTIVE', 0, NOW(6), NOW(6))
ON DUPLICATE KEY UPDATE role = 'MEMBER', status = 'ACTIVE', login_email = VALUES(login_email);
SET @customer_id = (SELECT id FROM accounts WHERE firebase_uid = 'w5ZQh5PjrtZ0KXAfR2mEuBFmTO12');
INSERT INTO account_profiles (account_id, full_name, phone, updated_at)
VALUES (@customer_id, 'Khách Hàng Thành Viên', '0901000004', NOW(6))
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), phone = VALUES(phone);

COMMIT;

-- ---------------------------------------------------------------------------
-- Verification (read-only)
-- ---------------------------------------------------------------------------
SELECT id, name, public_price, min_participants, max_participants, required_session_count,
       required_foundry_working_days_between_sessions, state
FROM workshop_packages WHERE state = 'PUBLISHED' ORDER BY id;

SELECT l.id, l.display_name, s.label, s.start_time, s.end_time, COUNT(ws.id) AS published_sessions
FROM workshop_locations l
JOIN workshop_slots s ON s.location_id = l.id AND s.active = 1
LEFT JOIN workshop_sessions ws ON ws.slot_id = s.id AND ws.state = 'PUBLISHED'
WHERE l.id = @location_id
GROUP BY l.id, l.display_name, s.label, s.start_time, s.end_time
ORDER BY s.start_time;

SELECT a.id, a.login_email, a.role, a.status, p.full_name
FROM accounts a LEFT JOIN account_profiles p ON p.account_id = a.id
WHERE a.login_email IN ('admin.test@mland.vn', 'manager.test@mland.vn',
                        'staff.test@mland.vn', 'customer.test@mland.vn')
ORDER BY a.id;
