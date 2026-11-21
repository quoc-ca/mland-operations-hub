-- V188: Add CARRIER role to accounts table and seed demo fixture
-- Revision R-CAR (2026-11-20) approved by Tech Lead Phan Sơn

ALTER TABLE accounts MODIFY COLUMN role ENUM('MEMBER', 'STAFF', 'MANAGER', 'CARRIER', 'ADMIN') NOT NULL DEFAULT 'MEMBER';

-- 5.5 CARRIER fixture: ghtk.test@mland.vn
SET @now = NOW(6);
INSERT INTO accounts (firebase_uid, login_email, role, status, version, created_at, updated_at)
VALUES ('t4532Dx1ApddAQMBB3YtxMEisNH3', 'ghtk.test@mland.vn', 'CARRIER', 'ACTIVE', 0, @now, @now)
ON DUPLICATE KEY UPDATE role = 'CARRIER', status = 'ACTIVE', login_email = VALUES(login_email);

SET @carrier_id = (SELECT id FROM accounts WHERE firebase_uid = 't4532Dx1ApddAQMBB3YtxMEisNH3');
INSERT INTO account_profiles (account_id, full_name, phone, updated_at)
VALUES (@carrier_id, 'Đối tác vận chuyển (GHTK)', '0900000000', @now)
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), phone = VALUES(phone);
