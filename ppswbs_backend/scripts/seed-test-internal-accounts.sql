-- Seed test internal accounts (ADMIN, MANAGER, STAFF, MEMBER) for E2E testing
-- Target: ppswbs_dev
-- Firebase project: mland-operation-hub

SET NAMES utf8mb4;
USE ppswbs_dev;

-- Ensure canonical workshop location 1 exists
INSERT INTO workshop_locations (id, code, display_name, address_snapshot, active, created_at, updated_at)
VALUES (1, 'LOC-Q1', 'Chi nhánh Quận 1 - TP. Hồ Chí Minh', '123 Nguyễn Huệ, Bến Nghé, Quận 1, TP. Hồ Chí Minh', 1, NOW(6), NOW(6))
ON DUPLICATE KEY UPDATE display_name = VALUES(display_name), active = 1;

-- 1. ADMIN (admin.test@mland.vn / AdminTest@123456)
INSERT INTO accounts (firebase_uid, login_email, role, status, version, created_at, updated_at)
VALUES ('9fxlc52Zu7S1ljw88EMN3qzW0Ib2', 'admin.test@mland.vn', 'ADMIN', 'ACTIVE', 0, NOW(6), NOW(6))
ON DUPLICATE KEY UPDATE role = 'ADMIN', status = 'ACTIVE', login_email = VALUES(login_email);

SET @admin_id = (SELECT id FROM accounts WHERE firebase_uid = '9fxlc52Zu7S1ljw88EMN3qzW0Ib2');

INSERT INTO account_profiles (account_id, full_name, phone, updated_at)
VALUES (@admin_id, 'Quản Trị Viên Hệ Thống', '0901000001', NOW(6))
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), phone = VALUES(phone);

-- 2. MANAGER (manager.test@mland.vn / ManagerTest@123456)
INSERT INTO accounts (firebase_uid, login_email, role, status, version, created_at, updated_at)
VALUES ('xhW4zbdGu9dAiDpog4g8W1pMXYz2', 'manager.test@mland.vn', 'MANAGER', 'ACTIVE', 0, NOW(6), NOW(6))
ON DUPLICATE KEY UPDATE role = 'MANAGER', status = 'ACTIVE', login_email = VALUES(login_email);

SET @manager_id = (SELECT id FROM accounts WHERE firebase_uid = 'xhW4zbdGu9dAiDpog4g8W1pMXYz2');

INSERT INTO account_profiles (account_id, full_name, phone, updated_at)
VALUES (@manager_id, 'Quản Lý Chi Nhánh', '0901000002', NOW(6))
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), phone = VALUES(phone);

INSERT INTO account_location_assignments (account_id, location_id, assigned_at, active)
SELECT @manager_id, 1, NOW(6), 1
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM account_location_assignments WHERE account_id = @manager_id AND location_id = 1 AND active = 1
);

-- 3. STAFF (staff.test@mland.vn / StaffTest@123456)
INSERT INTO accounts (firebase_uid, login_email, role, status, version, created_at, updated_at)
VALUES ('uqgudNtSTkWTkncqK30eJHyZ3J72', 'staff.test@mland.vn', 'STAFF', 'ACTIVE', 0, NOW(6), NOW(6))
ON DUPLICATE KEY UPDATE role = 'STAFF', status = 'ACTIVE', login_email = VALUES(login_email);

SET @staff_id = (SELECT id FROM accounts WHERE firebase_uid = 'uqgudNtSTkWTkncqK30eJHyZ3J72');

INSERT INTO account_profiles (account_id, full_name, phone, updated_at)
VALUES (@staff_id, 'Nhân Viên Workshop', '0901000003', NOW(6))
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), phone = VALUES(phone);

INSERT INTO account_location_assignments (account_id, location_id, assigned_at, active)
SELECT @staff_id, 1, NOW(6), 1
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM account_location_assignments WHERE account_id = @staff_id AND location_id = 1 AND active = 1
);

-- 4. MEMBER (customer.test@mland.vn / CustomerTest@123456)
INSERT INTO accounts (firebase_uid, login_email, role, status, version, created_at, updated_at)
VALUES ('w5ZQh5PjrtZ0KXAfR2mEuBFmTO12', 'customer.test@mland.vn', 'MEMBER', 'ACTIVE', 0, NOW(6), NOW(6))
ON DUPLICATE KEY UPDATE role = 'MEMBER', status = 'ACTIVE', login_email = VALUES(login_email);

SET @customer_id = (SELECT id FROM accounts WHERE firebase_uid = 'w5ZQh5PjrtZ0KXAfR2mEuBFmTO12');

INSERT INTO account_profiles (account_id, full_name, phone, updated_at)
VALUES (@customer_id, 'Khách Hàng Thành Viên', '0901000004', NOW(6))
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), phone = VALUES(phone);
