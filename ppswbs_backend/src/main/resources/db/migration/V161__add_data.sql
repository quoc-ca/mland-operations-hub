-- V161__add_data.sql
-- All-in-one comprehensive seed data for PPSWBS:
-- 1. Locations & Poster schedule slots (HN-HOANKIEM: 83 Mã Mây, HN-TAYHO: 3B Đặng Thái Thân, DN-HAICHAU)
-- 2. Official workshop packages (5 poster packages)
-- 3. Dynamic future workshop sessions (Next 45 days for all slots & active locations)
-- 4. Workshop fixed add-on catalogue (Gold plating, Laser engraving, Extra clay, Cleaning kit)
-- 5. Internal & Member accounts (ADMIN, MANAGER, STAFF, MEMBER with Firebase UIDs)
-- 6. Rich retail jewellery catalogue (4 categories, 21 published rings, Cloudinary media, reviews, variants, branch stocks)
-- 7. 10 Comprehensive promotions (Retail vouchers, auto-discounts, workshop vouchers, workshop auto-discount)
-- 8. Member voucher assignments & Member loyalty points (100,000 points with ledger)
--
-- Safe to run repeatedly: rows are upserted or guarded by NOT EXISTS / ON DUPLICATE KEY UPDATE.

SET NAMES utf8mb4;
SET @old_fk = @@FOREIGN_KEY_CHECKS;
SET FOREIGN_KEY_CHECKS = 0;
SET @now = UTC_TIMESTAMP(6);

-- ===========================================================================
-- 1. WORKSHOP LOCATIONS & SLOTS (HN-HOANKIEM, HN-TAYHO, DN-HAICHAU)
-- ===========================================================================

-- Location 1: Hoàn Kiếm (83 Mã Mây)
INSERT INTO workshop_locations (id, code, display_name, address_snapshot, active, created_at, updated_at)
VALUES (1, 'HN-HOANKIEM', 'Mland Hà Nội – Hoàn Kiếm', '83 Mã Mây, Hàng Buồm, Hoàn Kiếm, Hà Nội', 1, @now, @now)
ON DUPLICATE KEY UPDATE code = VALUES(code),
                        display_name = VALUES(display_name),
                        address_snapshot = VALUES(address_snapshot),
                        active = 1,
                        updated_at = @now;

-- Location 2: Tây Hồ / Cửa Nam (Ngõ 3B Đặng Thái Thân)
INSERT INTO workshop_locations (id, code, display_name, address_snapshot, active, created_at, updated_at)
VALUES (2, 'HN-TAYHO', 'Mland Hà Nội – Tây Hồ', 'P105, khu tập thể cuối ngõ 3B Đặng Thái Thân, P. Cửa Nam, Hoàn Kiếm, Hà Nội', 1, @now, @now)
ON DUPLICATE KEY UPDATE code = VALUES(code),
                        display_name = VALUES(display_name),
                        address_snapshot = VALUES(address_snapshot),
                        active = 1,
                        updated_at = @now;

-- Location 3: Đà Nẵng – Hải Châu (trạng thái chưa hoạt động)
INSERT INTO workshop_locations (id, code, display_name, address_snapshot, active, created_at, updated_at)
VALUES (3, 'DN-HAICHAU', 'Mland Đà Nẵng – Hải Châu', '88 Bạch Đằng, Hải Châu, Đà Nẵng', 0, @now, @now)
ON DUPLICATE KEY UPDATE code = VALUES(code),
                        display_name = VALUES(display_name),
                        address_snapshot = VALUES(address_snapshot),
                        active = 0,
                        updated_at = @now;

-- 3 standard poster slots for all active workshop locations: 09:30-12:00, 13:00-16:00, 16:00-19:00
INSERT INTO workshop_slots (reference, location_id, label, start_time, end_time, default_capacity,
                            active, version, created_at, updated_at)
SELECT UUID(), l.id, 'Morning (09:30-12:00)', '09:30:00', '12:00:00', 8, 1, 0, @now, @now
FROM workshop_locations l
WHERE l.active = 1
  AND NOT EXISTS (SELECT 1 FROM workshop_slots s WHERE s.location_id = l.id AND s.label = 'Morning (09:30-12:00)');

INSERT INTO workshop_slots (reference, location_id, label, start_time, end_time, default_capacity,
                            active, version, created_at, updated_at)
SELECT UUID(), l.id, 'Afternoon (13:00-16:00)', '13:00:00', '16:00:00', 8, 1, 0, @now, @now
FROM workshop_locations l
WHERE l.active = 1
  AND NOT EXISTS (SELECT 1 FROM workshop_slots s WHERE s.location_id = l.id AND s.label = 'Afternoon (13:00-16:00)');

INSERT INTO workshop_slots (reference, location_id, label, start_time, end_time, default_capacity,
                            active, version, created_at, updated_at)
SELECT UUID(), l.id, 'Evening (16:00-19:00)', '16:00:00', '19:00:00', 8, 1, 0, @now, @now
FROM workshop_locations l
WHERE l.active = 1
  AND NOT EXISTS (SELECT 1 FROM workshop_slots s WHERE s.location_id = l.id AND s.label = 'Evening (16:00-19:00)');

-- ===========================================================================
-- 2. WORKSHOP PACKAGES (5 official poster packages)
-- ===========================================================================
INSERT INTO workshop_packages (
    name, description, public_price, min_participants, max_participants,
    required_session_count, same_location_required, minimum_same_day_slot_gap,
    required_foundry_working_days_between_sessions, state, created_at, updated_at
)
SELECT 'Signature Workshop - Silver 925',
       'Silver 925 (tối đa 4g). Học kỹ thuật gia nhiệt, hàn nối và tạo hình; hoàn thiện và mang nhẫn về ngay trong ngày.',
       900000, 1, 8, 1, 1, 0, 0, 'PUBLISHED', @now, @now
WHERE NOT EXISTS (SELECT 1 FROM workshop_packages WHERE name = 'Signature Workshop - Silver 925');

INSERT INTO workshop_packages (
    name, description, public_price, min_participants, max_participants,
    required_session_count, same_location_required, minimum_same_day_slot_gap,
    required_foundry_working_days_between_sessions, state, created_at, updated_at
)
SELECT 'Signature Workshop - Brass',
       'Đồng thau nguyên chất (tối đa 10g). Bao gồm bộ kit chăm sóc bề mặt; hoàn thiện và mang sản phẩm về ngay trong ngày.',
       750000, 1, 8, 1, 1, 0, 0, 'PUBLISHED', @now, @now
WHERE NOT EXISTS (SELECT 1 FROM workshop_packages WHERE name = 'Signature Workshop - Brass');

INSERT INTO workshop_packages (
    name, description, public_price, min_participants, max_participants,
    required_session_count, same_location_required, minimum_same_day_slot_gap,
    required_foundry_working_days_between_sessions, state, created_at, updated_at
)
SELECT 'Silver Clay Workshop - 5g',
       'Đất sét bạc cao cấp S999, gói 5g bao gồm thiết kế nhẫn tiêu chuẩn và gắn tối đa 5 viên đá CZ. Lấy sản phẩm trong ngày.',
       1350000, 1, 8, 1, 1, 0, 0, 'PUBLISHED', @now, @now
WHERE NOT EXISTS (SELECT 1 FROM workshop_packages WHERE name = 'Silver Clay Workshop - 5g');

INSERT INTO workshop_packages (
    name, description, public_price, min_participants, max_participants,
    required_session_count, same_location_required, minimum_same_day_slot_gap,
    required_foundry_working_days_between_sessions, state, created_at, updated_at
)
SELECT 'Silver Clay Workshop - 7g',
       'Đất sét bạc cao cấp S999, gói 7g đính đá không giới hạn. Lấy sản phẩm trong ngày.',
       1950000, 1, 8, 1, 1, 0, 0, 'PUBLISHED', @now, @now
WHERE NOT EXISTS (SELECT 1 FROM workshop_packages WHERE name = 'Silver Clay Workshop - 7g');

INSERT INTO workshop_packages (
    name, description, public_price, min_participants, max_participants,
    required_session_count, same_location_required, minimum_same_day_slot_gap,
    required_foundry_working_days_between_sessions, state, created_at, updated_at
)
SELECT 'Wax Casting Workshop',
       'Trải nghiệm đúc khuôn sáp chuyên sâu S999/S925/Vàng. Gồm 2 buổi trải nghiệm cách nhau ít nhất 2 ngày làm việc xưởng đúc.',
       1000000, 1, 8, 2, 1, 0, 2, 'PUBLISHED', @now, @now
WHERE NOT EXISTS (SELECT 1 FROM workshop_packages WHERE name = 'Wax Casting Workshop');

UPDATE workshop_packages
SET state = 'PUBLISHED', updated_at = @now
WHERE name IN ('Signature Workshop - Silver 925', 'Signature Workshop - Brass',
               'Silver Clay Workshop - 5g', 'Silver Clay Workshop - 7g', 'Wax Casting Workshop')
  AND state <> 'PUBLISHED';

-- ===========================================================================
-- 3. DYNAMIC FUTURE WORKSHOP SESSIONS (Next 45 days)
-- ===========================================================================
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
       @now, @now
FROM demo_dates d
JOIN workshop_slots s ON s.active = 1
JOIN workshop_locations l ON l.id = s.location_id AND l.active = 1
WHERE NOT EXISTS (SELECT 1 FROM workshop_sessions existing
                  WHERE existing.location_id = s.location_id
                    AND existing.workshop_date = d.workshop_date
                    AND existing.slot_id = s.id);

-- ===========================================================================
-- 4. WORKSHOP ADD-ONS (Fixed Add-on Catalogue for each Package & Location)
-- ===========================================================================
INSERT INTO workshop_addons (reference, package_id, location_id, description, price_vnd, state, created_at, updated_at)
SELECT UUID(), p.id, l.id, 'Xi mạ vàng 14K/18K cao cấp', 150000, 'ACTIVE', @now, @now
FROM workshop_packages p CROSS JOIN workshop_locations l
WHERE p.state = 'PUBLISHED' AND l.active = 1
  AND NOT EXISTS (
      SELECT 1 FROM workshop_addons a
      WHERE a.package_id = p.id AND a.location_id = l.id AND a.description = 'Xi mạ vàng 14K/18K cao cấp'
  );

INSERT INTO workshop_addons (reference, package_id, location_id, description, price_vnd, state, created_at, updated_at)
SELECT UUID(), p.id, l.id, 'Khắc chữ laser cá nhân hóa theo yêu cầu', 100000, 'ACTIVE', @now, @now
FROM workshop_packages p CROSS JOIN workshop_locations l
WHERE p.state = 'PUBLISHED' AND l.active = 1
  AND NOT EXISTS (
      SELECT 1 FROM workshop_addons a
      WHERE a.package_id = p.id AND a.location_id = l.id AND a.description = 'Khắc chữ laser cá nhân hóa theo yêu cầu'
  );

INSERT INTO workshop_addons (reference, package_id, location_id, description, price_vnd, state, created_at, updated_at)
SELECT UUID(), p.id, l.id, 'Bổ sung đất sét bạc S999 gói 5g', 900000, 'ACTIVE', @now, @now
FROM workshop_packages p CROSS JOIN workshop_locations l
WHERE p.state = 'PUBLISHED' AND l.active = 1
  AND p.name LIKE 'Silver Clay Workshop%'
  AND NOT EXISTS (
      SELECT 1 FROM workshop_addons a
      WHERE a.package_id = p.id AND a.location_id = l.id AND a.description = 'Bổ sung đất sét bạc S999 gói 5g'
  );

INSERT INTO workshop_addons (reference, package_id, location_id, description, price_vnd, state, created_at, updated_at)
SELECT UUID(), p.id, l.id, 'Bộ kit khăn lau & dung dịch làm sáng trang sức', 50000, 'ACTIVE', @now, @now
FROM workshop_packages p CROSS JOIN workshop_locations l
WHERE p.state = 'PUBLISHED' AND l.active = 1
  AND NOT EXISTS (
      SELECT 1 FROM workshop_addons a
      WHERE a.package_id = p.id AND a.location_id = l.id AND a.description = 'Bộ kit khăn lau & dung dịch làm sáng trang sức'
  );

-- ===========================================================================
-- 5. INTERNAL & MEMBER ACCOUNTS (Firebase project: mland-operation-hub)
-- ===========================================================================

-- 5.1 ADMIN: admin.test@mland.vn
INSERT INTO accounts (firebase_uid, login_email, role, status, version, created_at, updated_at)
VALUES ('9fxlc52Zu7S1ljw88EMN3qzW0Ib2', 'admin.test@mland.vn', 'ADMIN', 'ACTIVE', 0, @now, @now)
ON DUPLICATE KEY UPDATE role = 'ADMIN', status = 'ACTIVE', login_email = VALUES(login_email);

SET @admin_id = (SELECT id FROM accounts WHERE firebase_uid = '9fxlc52Zu7S1ljw88EMN3qzW0Ib2');
INSERT INTO account_profiles (account_id, full_name, phone, updated_at)
VALUES (@admin_id, 'Quản Trị Viên Hệ Thống', '0901000001', @now)
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), phone = VALUES(phone);

-- 5.2 MANAGER: manager.test@mland.vn (Phụ trách cả 2 chi nhánh)
INSERT INTO accounts (firebase_uid, login_email, role, status, version, created_at, updated_at)
VALUES ('xhW4zbdGu9dAiDpog4g8W1pMXYz2', 'manager.test@mland.vn', 'MANAGER', 'ACTIVE', 0, @now, @now)
ON DUPLICATE KEY UPDATE role = 'MANAGER', status = 'ACTIVE', login_email = VALUES(login_email);

SET @manager_id = (SELECT id FROM accounts WHERE firebase_uid = 'xhW4zbdGu9dAiDpog4g8W1pMXYz2');
INSERT INTO account_profiles (account_id, full_name, phone, updated_at)
VALUES (@manager_id, 'Quản Lý Chuỗi Chi Nhánh', '0901000002', @now)
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), phone = VALUES(phone);

INSERT INTO account_location_assignments (account_id, location_id, assigned_at, active)
SELECT @manager_id, l.id, @now, 1
FROM workshop_locations l
WHERE l.active = 1
  AND NOT EXISTS (
      SELECT 1 FROM account_location_assignments
      WHERE account_id = @manager_id AND location_id = l.id AND active = 1
  );

-- 5.3 STAFF: staff.test@mland.vn (Gán tại Chi nhánh 1 - Hoàn Kiếm)
INSERT INTO accounts (firebase_uid, login_email, role, status, version, created_at, updated_at)
VALUES ('uqgudNtSTkWTkncqK30eJHyZ3J72', 'staff.test@mland.vn', 'STAFF', 'ACTIVE', 0, @now, @now)
ON DUPLICATE KEY UPDATE role = 'STAFF', status = 'ACTIVE', login_email = VALUES(login_email);

SET @staff_id = (SELECT id FROM accounts WHERE firebase_uid = 'uqgudNtSTkWTkncqK30eJHyZ3J72');
INSERT INTO account_profiles (account_id, full_name, phone, updated_at)
VALUES (@staff_id, 'Nhân Viên Workshop Hoàn Kiếm', '0901000003', @now)
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), phone = VALUES(phone);

INSERT INTO account_location_assignments (account_id, location_id, assigned_at, active)
SELECT @staff_id, 1, @now, 1 FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM account_location_assignments
    WHERE account_id = @staff_id AND location_id = 1 AND active = 1
);

-- 5.4 MEMBER: customer.test@mland.vn
INSERT INTO accounts (firebase_uid, login_email, role, status, version, created_at, updated_at)
VALUES ('w5ZQh5PjrtZ0KXAfR2mEuBFmTO12', 'customer.test@mland.vn', 'MEMBER', 'ACTIVE', 0, @now, @now)
ON DUPLICATE KEY UPDATE role = 'MEMBER', status = 'ACTIVE', login_email = VALUES(login_email);

SET @customer_id = (SELECT id FROM accounts WHERE firebase_uid = 'w5ZQh5PjrtZ0KXAfR2mEuBFmTO12');
INSERT INTO account_profiles (account_id, full_name, phone, updated_at)
VALUES (@customer_id, 'Khách Hàng Thành Viên', '0901000004', @now)
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), phone = VALUES(phone);

-- Ensure @branches_ok evaluates to true for the rich sample below
SET @branches_ok = NOT EXISTS (SELECT 1 FROM workshop_locations WHERE (id = 1) <> (code = 'HN-HOANKIEM') OR (id = 2) <> (code = 'HN-TAYHO') OR (id = 3) <> (code = 'DN-HAICHAU'));

-- ===========================================================================
-- 6. RICH RETAIL CATALOGUE & 7. COMPREHENSIVE PROMOTIONS
-- ===========================================================================

-- ===== Catalogue =====
SET @run_catalogue = @branches_ok AND NOT EXISTS (SELECT 1 FROM catalogue_products WHERE name = 'Nhẫn bạc vân búa bản nhọn' AND deleted_at IS NULL);
SET @bp  = (SELECT COALESCE(MAX(id), 0) FROM catalogue_products);
SET @bv  = (SELECT COALESCE(MAX(id), 0) FROM catalogue_variants);
SET @bs  = (SELECT COALESCE(MAX(id), 0) FROM catalogue_variant_branch_stock);
SET @bi  = (SELECT COALESCE(MAX(id), 0) FROM catalogue_product_images);
SET @bm  = (SELECT COALESCE(MAX(id), 0) FROM catalogue_media_references);
SET @br  = (SELECT COALESCE(MAX(id), 0) FROM catalogue_product_reviews);
SET @bpp = (SELECT COALESCE(MAX(id), 0) FROM catalogue_price_proposals);
SET @ba  = (SELECT COALESCE(MAX(id), 0) FROM catalogue_price_applications);

-- Categories (an existing category with the same name is reused)
INSERT INTO `catalogue_categories` (`name`, `name_key`, `description`, `active`, `version`, `created_at`, `updated_at`)
SELECT 'Nhẫn bạc trơn', X'6E68E1BAAB6E2062E1BAA163207472C6A16E', 'Nhẫn bạc 925 không đính đá, rèn và đánh vân thủ công tại xưởng Mland.', 1, 0, '2026-11-12 17:29:43.424570', '2026-11-12 17:29:43.424570' FROM DUAL WHERE @run_catalogue AND NOT EXISTS (SELECT 1 FROM catalogue_categories WHERE name_key = X'6E68E1BAAB6E2062E1BAA163207472C6A16E');
INSERT INTO `catalogue_categories` (`name`, `name_key`, `description`, `active`, `version`, `created_at`, `updated_at`)
SELECT 'Nhẫn đá quý', X'6E68E1BAAB6E20C491C3A1207175C3BD', 'Nhẫn bạc đính đá quý tự nhiên đã mài: thạch anh tím, peridot, garnet, citrine, hổ phách.', 1, 0, '2026-11-12 17:29:43.534027', '2026-11-12 17:29:43.534027' FROM DUAL WHERE @run_catalogue AND NOT EXISTS (SELECT 1 FROM catalogue_categories WHERE name_key = X'6E68E1BAAB6E20C491C3A1207175C3BD');
INSERT INTO `catalogue_categories` (`name`, `name_key`, `description`, `active`, `version`, `created_at`, `updated_at`)
SELECT 'Nhẫn mã não', X'6E68E1BAAB6E206DC3A3206EC3A36F', 'Nhẫn bạc gắn mã não cây và mã não rêu; mỗi viên một hoa văn tự nhiên, không chiếc nào giống chiếc nào.', 1, 0, '2026-11-12 17:29:43.571062', '2026-11-12 17:29:43.571062' FROM DUAL WHERE @run_catalogue AND NOT EXISTS (SELECT 1 FROM catalogue_categories WHERE name_key = X'6E68E1BAAB6E206DC3A3206EC3A36F');
INSERT INTO `catalogue_categories` (`name`, `name_key`, `description`, `active`, `version`, `created_at`, `updated_at`)
SELECT 'Nhẫn đá thô', X'6E68E1BAAB6E20C491C3A1207468C3B4', 'Nhẫn bạc giữ nguyên dáng tinh thể hoặc viên đá thô, ôm đá bằng chấu và dây bạc.', 1, 0, '2026-11-12 17:29:43.598096', '2026-11-12 17:29:43.598096' FROM DUAL WHERE @run_catalogue AND NOT EXISTS (SELECT 1 FROM catalogue_categories WHERE name_key = X'6E68E1BAAB6E20C491C3A1207468C3B4');

-- Media (Cloudinary assets)
INSERT INTO `catalogue_media_objects` (`id`, `storage_key`, `provider_asset_id`, `provider_version`, `provider_format`, `checksum`, `content_type`, `byte_count`, `state`, `uploaded_by`, `command_id`, `created_at`, `last_verified_at`, `first_attached_at`, `last_unreferenced_at`, `delete_requested_at`, `deleted_at`)
SELECT '0d5cf1c7-615f-4b52-99fd-2c5a2802261b', 'ppswbs/catalogue/0d5cf1c7-615f-4b52-99fd-2c5a2802261b', '2714ee2e7777b7eee24a23b1e4c5973e', '1794504636', 'jpg', '40018d7a8d0ddcad3ce382324b9ea8b8454a18b4d0b7546f4520c6d61a1a69a0', 'image/jpeg', 105791, 'READY', 'system:catalogue-seed', 'fa9b762e-bf46-3498-ad50-92a787e08893', '2026-11-12 17:30:35.538870', '2026-11-12 17:30:37.480690', '2026-11-12 17:30:37.508305', NULL, NULL, NULL FROM DUAL WHERE @run_catalogue AND NOT EXISTS (SELECT 1 FROM catalogue_media_objects WHERE id = '0d5cf1c7-615f-4b52-99fd-2c5a2802261b');
INSERT INTO `catalogue_media_objects` (`id`, `storage_key`, `provider_asset_id`, `provider_version`, `provider_format`, `checksum`, `content_type`, `byte_count`, `state`, `uploaded_by`, `command_id`, `created_at`, `last_verified_at`, `first_attached_at`, `last_unreferenced_at`, `delete_requested_at`, `deleted_at`)
SELECT '1b83213c-2617-44e2-858f-126f42177005', 'ppswbs/catalogue/1b83213c-2617-44e2-858f-126f42177005', '1f789c6ad14c78ca9c3e586db24b534b', '1794504592', 'jpg', 'e94d4b409fc24d93622560855307ec11d4888af4fc0b327bf01210a397cffee1', 'image/jpeg', 130975, 'READY', 'system:catalogue-seed', '33b4fbba-a7d6-36cf-99a2-62a8e2bbcfc1', '2026-11-12 17:29:51.336880', '2026-11-12 17:29:53.060633', '2026-11-12 17:29:53.079608', NULL, NULL, NULL FROM DUAL WHERE @run_catalogue AND NOT EXISTS (SELECT 1 FROM catalogue_media_objects WHERE id = '1b83213c-2617-44e2-858f-126f42177005');
INSERT INTO `catalogue_media_objects` (`id`, `storage_key`, `provider_asset_id`, `provider_version`, `provider_format`, `checksum`, `content_type`, `byte_count`, `state`, `uploaded_by`, `command_id`, `created_at`, `last_verified_at`, `first_attached_at`, `last_unreferenced_at`, `delete_requested_at`, `deleted_at`)
SELECT '1e14017b-5e7a-426f-ad43-f43b59c97b8f', 'ppswbs/catalogue/1e14017b-5e7a-426f-ad43-f43b59c97b8f', '6e156bdac4f44e85b38716232dc14b70', '1794504622', 'jpg', '1ca0dbdaaebb8f7d7e36b06799dcfdf4ed726386b20ad1687602c42a4f9e845d', 'image/jpeg', 132982, 'READY', 'system:catalogue-seed', '40dbe838-7fe4-32fb-a2b0-f9d641836da5', '2026-11-12 17:30:21.198806', '2026-11-12 17:30:22.999575', '2026-11-12 17:30:23.022787', NULL, NULL, NULL FROM DUAL WHERE @run_catalogue AND NOT EXISTS (SELECT 1 FROM catalogue_media_objects WHERE id = '1e14017b-5e7a-426f-ad43-f43b59c97b8f');
INSERT INTO `catalogue_media_objects` (`id`, `storage_key`, `provider_asset_id`, `provider_version`, `provider_format`, `checksum`, `content_type`, `byte_count`, `state`, `uploaded_by`, `command_id`, `created_at`, `last_verified_at`, `first_attached_at`, `last_unreferenced_at`, `delete_requested_at`, `deleted_at`)
SELECT '2331db2e-0239-4e78-8f29-ddba3a61596b', 'ppswbs/catalogue/2331db2e-0239-4e78-8f29-ddba3a61596b', '43371f0d4fb776c8e0227d9ac1c032fd', '1794504613', 'jpg', 'ff278b1413243834bed80e63def60b189f6514157611bdeecc88f5e09793f4af', 'image/jpeg', 131695, 'READY', 'system:catalogue-seed', 'f30f1c55-1496-3006-91af-b8abfffeaae6', '2026-11-12 17:30:11.885270', '2026-11-12 17:30:13.557596', '2026-11-12 17:30:13.578467', NULL, NULL, NULL FROM DUAL WHERE @run_catalogue AND NOT EXISTS (SELECT 1 FROM catalogue_media_objects WHERE id = '2331db2e-0239-4e78-8f29-ddba3a61596b');
INSERT INTO `catalogue_media_objects` (`id`, `storage_key`, `provider_asset_id`, `provider_version`, `provider_format`, `checksum`, `content_type`, `byte_count`, `state`, `uploaded_by`, `command_id`, `created_at`, `last_verified_at`, `first_attached_at`, `last_unreferenced_at`, `delete_requested_at`, `deleted_at`)
SELECT '26dc16ef-6378-467f-8f9c-5c8e323f4d0d', 'ppswbs/catalogue/26dc16ef-6378-467f-8f9c-5c8e323f4d0d', '68c2fdf8cfdd6ebb033f2df62b3721ac', '1794504603', 'jpg', '2a915aaa1d1773cb789d58a61ebe20cd026041bbf2a07884c712f8cbba195536', 'image/jpeg', 109308, 'READY', 'system:catalogue-seed', 'f3e2f420-1a35-3b3e-8387-aa899574aab5', '2026-11-12 17:30:02.261891', '2026-11-12 17:30:03.939180', '2026-11-12 17:30:03.964974', NULL, NULL, NULL FROM DUAL WHERE @run_catalogue AND NOT EXISTS (SELECT 1 FROM catalogue_media_objects WHERE id = '26dc16ef-6378-467f-8f9c-5c8e323f4d0d');
INSERT INTO `catalogue_media_objects` (`id`, `storage_key`, `provider_asset_id`, `provider_version`, `provider_format`, `checksum`, `content_type`, `byte_count`, `state`, `uploaded_by`, `command_id`, `created_at`, `last_verified_at`, `first_attached_at`, `last_unreferenced_at`, `delete_requested_at`, `deleted_at`)
SELECT '2b3bc822-f55d-493e-86f0-1e19263b3ad6', 'ppswbs/catalogue/2b3bc822-f55d-493e-86f0-1e19263b3ad6', '7054b07ea77241a68708b9400efec5ac', '1794504620', 'jpg', '432411fd0e960cedfd3a45cc11b776dc7e546198385fb50e6e7836617e502647', 'image/jpeg', 131894, 'READY', 'system:catalogue-seed', 'ff76960f-37fd-3fbc-8c7c-fd4ea1ae3253', '2026-11-12 17:30:18.879503', '2026-11-12 17:30:20.797666', '2026-11-12 17:30:20.815669', NULL, NULL, NULL FROM DUAL WHERE @run_catalogue AND NOT EXISTS (SELECT 1 FROM catalogue_media_objects WHERE id = '2b3bc822-f55d-493e-86f0-1e19263b3ad6');
INSERT INTO `catalogue_media_objects` (`id`, `storage_key`, `provider_asset_id`, `provider_version`, `provider_format`, `checksum`, `content_type`, `byte_count`, `state`, `uploaded_by`, `command_id`, `created_at`, `last_verified_at`, `first_attached_at`, `last_unreferenced_at`, `delete_requested_at`, `deleted_at`)
SELECT '2f0167bc-480b-4693-bbbf-acf7b60c7ef4', 'ppswbs/catalogue/2f0167bc-480b-4693-bbbf-acf7b60c7ef4', '421472ee5551ca79d5c7565de630555b', '1794504628', 'jpg', '7f1dc58014877ee47d45f330849fe2326bdc620d111a92a7c42a196b15cd2ad4', 'image/jpeg', 99555, 'READY', 'system:catalogue-seed', '5d9afa64-1ccd-3cdd-b869-f94b14785651', '2026-11-12 17:30:27.578861', '2026-11-12 17:30:29.262914', '2026-11-12 17:30:29.279608', NULL, NULL, NULL FROM DUAL WHERE @run_catalogue AND NOT EXISTS (SELECT 1 FROM catalogue_media_objects WHERE id = '2f0167bc-480b-4693-bbbf-acf7b60c7ef4');
INSERT INTO `catalogue_media_objects` (`id`, `storage_key`, `provider_asset_id`, `provider_version`, `provider_format`, `checksum`, `content_type`, `byte_count`, `state`, `uploaded_by`, `command_id`, `created_at`, `last_verified_at`, `first_attached_at`, `last_unreferenced_at`, `delete_requested_at`, `deleted_at`)
SELECT '381ee35b-acbb-4705-9eea-83a3fead5bca', 'ppswbs/catalogue/381ee35b-acbb-4705-9eea-83a3fead5bca', '157c9b9191efa1d96572fa2eb7b3d455', '1794504624', 'jpg', '5213c4d2bc01e439522fad0e2f8b03f1a7a36f24121d9281ac7d2f48b8dd78bb', 'image/jpeg', 128109, 'READY', 'system:catalogue-seed', '10b8731e-1da3-35f3-8dc0-bc81b6852ca7', '2026-11-12 17:30:23.359386', '2026-11-12 17:30:25.088062', '2026-11-12 17:30:25.113208', NULL, NULL, NULL FROM DUAL WHERE @run_catalogue AND NOT EXISTS (SELECT 1 FROM catalogue_media_objects WHERE id = '381ee35b-acbb-4705-9eea-83a3fead5bca');
INSERT INTO `catalogue_media_objects` (`id`, `storage_key`, `provider_asset_id`, `provider_version`, `provider_format`, `checksum`, `content_type`, `byte_count`, `state`, `uploaded_by`, `command_id`, `created_at`, `last_verified_at`, `first_attached_at`, `last_unreferenced_at`, `delete_requested_at`, `deleted_at`)
SELECT '3e5c1f53-8309-4e24-b9be-3872cd8c03aa', 'ppswbs/catalogue/3e5c1f53-8309-4e24-b9be-3872cd8c03aa', 'ca674cae4b9f5d24b1b62828a42c87d7', '1794504610', 'jpg', '0ea2decd1680311afa8454582d5e409af8659d5ead140957c7f291dc751294df', 'image/jpeg', 136959, 'READY', 'system:catalogue-seed', '08a1dc4b-1207-3185-ae78-fef84aaf5a01', '2026-11-12 17:30:09.546381', '2026-11-12 17:30:11.561459', '2026-11-12 17:30:11.573822', NULL, NULL, NULL FROM DUAL WHERE @run_catalogue AND NOT EXISTS (SELECT 1 FROM catalogue_media_objects WHERE id = '3e5c1f53-8309-4e24-b9be-3872cd8c03aa');
INSERT INTO `catalogue_media_objects` (`id`, `storage_key`, `provider_asset_id`, `provider_version`, `provider_format`, `checksum`, `content_type`, `byte_count`, `state`, `uploaded_by`, `command_id`, `created_at`, `last_verified_at`, `first_attached_at`, `last_unreferenced_at`, `delete_requested_at`, `deleted_at`)
SELECT '4d950a38-c94e-4e44-af30-e8c272898fb3', 'ppswbs/catalogue/4d950a38-c94e-4e44-af30-e8c272898fb3', '12452068997ff310c1d841b0c9694828', '1794504590', 'jpg', '76e2c5ffe2ecc252243d5ce8d8ca92d29a42fd9eaa8ec9dad85623a2380fb3b2', 'image/jpeg', 128858, 'READY', 'system:catalogue-seed', '2c109f0d-9135-3461-b0d7-b679a5ecb844', '2026-11-12 17:29:49.152828', '2026-11-12 17:29:50.896454', '2026-11-12 17:29:50.918974', NULL, NULL, NULL FROM DUAL WHERE @run_catalogue AND NOT EXISTS (SELECT 1 FROM catalogue_media_objects WHERE id = '4d950a38-c94e-4e44-af30-e8c272898fb3');
INSERT INTO `catalogue_media_objects` (`id`, `storage_key`, `provider_asset_id`, `provider_version`, `provider_format`, `checksum`, `content_type`, `byte_count`, `state`, `uploaded_by`, `command_id`, `created_at`, `last_verified_at`, `first_attached_at`, `last_unreferenced_at`, `delete_requested_at`, `deleted_at`)
SELECT '5056511b-6a97-436b-b8a3-75fb0ba5623c', 'ppswbs/catalogue/5056511b-6a97-436b-b8a3-75fb0ba5623c', '007ff4966fb31727b46937b69eff4acc', '1794504616', 'jpg', 'cd5b565a2a6f6bba27ef513077d9b3f3ab4284408ac286aad66b5c7d7a0148a5', 'image/jpeg', 124456, 'READY', 'system:catalogue-seed', 'b7d0ce59-09a1-3f8e-a055-e6cd32de6356', '2026-11-12 17:30:15.714956', '2026-11-12 17:30:18.393879', '2026-11-12 17:30:18.416782', NULL, NULL, NULL FROM DUAL WHERE @run_catalogue AND NOT EXISTS (SELECT 1 FROM catalogue_media_objects WHERE id = '5056511b-6a97-436b-b8a3-75fb0ba5623c');
INSERT INTO `catalogue_media_objects` (`id`, `storage_key`, `provider_asset_id`, `provider_version`, `provider_format`, `checksum`, `content_type`, `byte_count`, `state`, `uploaded_by`, `command_id`, `created_at`, `last_verified_at`, `first_attached_at`, `last_unreferenced_at`, `delete_requested_at`, `deleted_at`)
SELECT '59039fdd-df4a-42cf-9990-3ca68d22b17b', 'ppswbs/catalogue/59039fdd-df4a-42cf-9990-3ca68d22b17b', 'ec9f76f358af4ba23e129f19e025af99', '1794504608', 'jpg', 'debe9d21530a60ba8364d853743208e5b7c11d6ee99cb4c50dbc4db38779e502', 'image/jpeg', 118294, 'READY', 'system:catalogue-seed', '797e3b9c-92ef-3cf7-88f3-08658594cbf1', '2026-11-12 17:30:06.732226', '2026-11-12 17:30:08.572492', '2026-11-12 17:30:08.625428', NULL, NULL, NULL FROM DUAL WHERE @run_catalogue AND NOT EXISTS (SELECT 1 FROM catalogue_media_objects WHERE id = '59039fdd-df4a-42cf-9990-3ca68d22b17b');
INSERT INTO `catalogue_media_objects` (`id`, `storage_key`, `provider_asset_id`, `provider_version`, `provider_format`, `checksum`, `content_type`, `byte_count`, `state`, `uploaded_by`, `command_id`, `created_at`, `last_verified_at`, `first_attached_at`, `last_unreferenced_at`, `delete_requested_at`, `deleted_at`)
SELECT '8391d99d-589e-4d13-ba18-e2cd85a9244b', 'ppswbs/catalogue/8391d99d-589e-4d13-ba18-e2cd85a9244b', '15e7655ff6feda75eaac6234b451f8b9', '1794504596', 'jpg', '5f213c09f0de1b8d09804ad4388c30d331bdd24503f126a503786269be843c21', 'image/jpeg', 111531, 'READY', 'system:catalogue-seed', '2e8ec1b7-ed61-3f15-bd7c-f8159255f00b', '2026-11-12 17:29:55.687395', '2026-11-12 17:29:57.370636', '2026-11-12 17:29:57.385635', NULL, NULL, NULL FROM DUAL WHERE @run_catalogue AND NOT EXISTS (SELECT 1 FROM catalogue_media_objects WHERE id = '8391d99d-589e-4d13-ba18-e2cd85a9244b');
INSERT INTO `catalogue_media_objects` (`id`, `storage_key`, `provider_asset_id`, `provider_version`, `provider_format`, `checksum`, `content_type`, `byte_count`, `state`, `uploaded_by`, `command_id`, `created_at`, `last_verified_at`, `first_attached_at`, `last_unreferenced_at`, `delete_requested_at`, `deleted_at`)
SELECT '84690e58-5f17-4572-b6c6-e1a87ab10dbc', 'ppswbs/catalogue/84690e58-5f17-4572-b6c6-e1a87ab10dbc', 'f0daf033166c0f3cd6d706879ff249c1', '1794504605', 'jpg', 'cba21f328f7d3d2d91baa96f5102ec9fb7e1eaa209c944ed49e56ab02d4be47d', 'image/jpeg', 122748, 'READY', 'system:catalogue-seed', 'b1e3bc8c-94f6-3e2f-8632-6ff68d6d1708', '2026-11-12 17:30:04.496506', '2026-11-12 17:30:06.264385', '2026-11-12 17:30:06.283666', NULL, NULL, NULL FROM DUAL WHERE @run_catalogue AND NOT EXISTS (SELECT 1 FROM catalogue_media_objects WHERE id = '84690e58-5f17-4572-b6c6-e1a87ab10dbc');
INSERT INTO `catalogue_media_objects` (`id`, `storage_key`, `provider_asset_id`, `provider_version`, `provider_format`, `checksum`, `content_type`, `byte_count`, `state`, `uploaded_by`, `command_id`, `created_at`, `last_verified_at`, `first_attached_at`, `last_unreferenced_at`, `delete_requested_at`, `deleted_at`)
SELECT '88a46e90-2a00-4c5d-a70a-8c1b3dd61963', 'ppswbs/catalogue/88a46e90-2a00-4c5d-a70a-8c1b3dd61963', 'c2bc0e788f47c29f15dbb2ab2132701b', '1794504585', 'jpg', '9360340a96b9b371d70097c3c3d653099d15bf47b10e788620ce4f0740116d6c', 'image/jpeg', 125248, 'READY', 'system:catalogue-seed', 'd6a85d08-30a8-3b2b-93be-ebe53a9ca7c3', '2026-11-12 17:29:43.752577', '2026-11-12 17:29:46.087191', '2026-11-12 17:29:48.028525', NULL, NULL, NULL FROM DUAL WHERE @run_catalogue AND NOT EXISTS (SELECT 1 FROM catalogue_media_objects WHERE id = '88a46e90-2a00-4c5d-a70a-8c1b3dd61963');
INSERT INTO `catalogue_media_objects` (`id`, `storage_key`, `provider_asset_id`, `provider_version`, `provider_format`, `checksum`, `content_type`, `byte_count`, `state`, `uploaded_by`, `command_id`, `created_at`, `last_verified_at`, `first_attached_at`, `last_unreferenced_at`, `delete_requested_at`, `deleted_at`)
SELECT '88bc7456-ce95-4f6d-8eea-86791efc7120', 'ppswbs/catalogue/88bc7456-ce95-4f6d-8eea-86791efc7120', '7f0dc5668cdf47f128606065f235f82a', '1794504599', 'jpg', '1c436aaffefb011ee2e3ff8f9a09a1fb3fef58e27aff34d394654b94c225af1b', 'image/jpeg', 142349, 'READY', 'system:catalogue-seed', '4632afe2-bfa0-3387-91d3-fae97b589a68', '2026-11-12 17:29:57.785432', '2026-11-12 17:29:59.552885', '2026-11-12 17:29:59.570348', NULL, NULL, NULL FROM DUAL WHERE @run_catalogue AND NOT EXISTS (SELECT 1 FROM catalogue_media_objects WHERE id = '88bc7456-ce95-4f6d-8eea-86791efc7120');
INSERT INTO `catalogue_media_objects` (`id`, `storage_key`, `provider_asset_id`, `provider_version`, `provider_format`, `checksum`, `content_type`, `byte_count`, `state`, `uploaded_by`, `command_id`, `created_at`, `last_verified_at`, `first_attached_at`, `last_unreferenced_at`, `delete_requested_at`, `deleted_at`)
SELECT '9c6e6aa3-38bb-494d-85b7-7cb64fe6f76f', 'ppswbs/catalogue/9c6e6aa3-38bb-494d-85b7-7cb64fe6f76f', 'fc6a29a0be31fd6b8d3fe3575e03d4f8', '1794504587', 'jpg', 'a66dcb44f9ac98de0f9f5250da92879d5c2640be4f66c4ecd97d9c1fffc84e79', 'image/jpeg', 129720, 'READY', 'system:catalogue-seed', '7d1d7291-92b3-384a-a76f-e5b6f8aa84f0', '2026-11-12 17:29:46.162596', '2026-11-12 17:29:47.949847', '2026-11-12 17:29:48.028525', NULL, NULL, NULL FROM DUAL WHERE @run_catalogue AND NOT EXISTS (SELECT 1 FROM catalogue_media_objects WHERE id = '9c6e6aa3-38bb-494d-85b7-7cb64fe6f76f');
INSERT INTO `catalogue_media_objects` (`id`, `storage_key`, `provider_asset_id`, `provider_version`, `provider_format`, `checksum`, `content_type`, `byte_count`, `state`, `uploaded_by`, `command_id`, `created_at`, `last_verified_at`, `first_attached_at`, `last_unreferenced_at`, `delete_requested_at`, `deleted_at`)
SELECT 'a419904e-f035-4623-89d2-e179e51de87d', 'ppswbs/catalogue/a419904e-f035-4623-89d2-e179e51de87d', 'b197e71f924fc575c099403edf6dca33', '1794504630', 'jpg', 'a61dae69fcae80c11188f710e6080c6511a1b1b47efc8a842c33e205ed508b88', 'image/jpeg', 132112, 'READY', 'system:catalogue-seed', 'a9af77aa-b02e-3608-af9a-bbcf622d3e92', '2026-11-12 17:30:29.635597', '2026-11-12 17:30:31.340187', '2026-11-12 17:30:31.355178', NULL, NULL, NULL FROM DUAL WHERE @run_catalogue AND NOT EXISTS (SELECT 1 FROM catalogue_media_objects WHERE id = 'a419904e-f035-4623-89d2-e179e51de87d');
INSERT INTO `catalogue_media_objects` (`id`, `storage_key`, `provider_asset_id`, `provider_version`, `provider_format`, `checksum`, `content_type`, `byte_count`, `state`, `uploaded_by`, `command_id`, `created_at`, `last_verified_at`, `first_attached_at`, `last_unreferenced_at`, `delete_requested_at`, `deleted_at`)
SELECT 'a90ba336-f29f-4e28-a0d3-60f9f8f83e78', 'ppswbs/catalogue/a90ba336-f29f-4e28-a0d3-60f9f8f83e78', '28e9d6d79c7e175d87970092295e3af8', '1794504594', 'jpg', '65e07cb444448b2b87046799519a8a906aeace152bfdc469783857b7dac3356a', 'image/jpeg', 123933, 'READY', 'system:catalogue-seed', 'e5deb297-7e6c-3acc-a618-2525b2678092', '2026-11-12 17:29:53.486947', '2026-11-12 17:29:55.265905', '2026-11-12 17:29:55.288068', NULL, NULL, NULL FROM DUAL WHERE @run_catalogue AND NOT EXISTS (SELECT 1 FROM catalogue_media_objects WHERE id = 'a90ba336-f29f-4e28-a0d3-60f9f8f83e78');
INSERT INTO `catalogue_media_objects` (`id`, `storage_key`, `provider_asset_id`, `provider_version`, `provider_format`, `checksum`, `content_type`, `byte_count`, `state`, `uploaded_by`, `command_id`, `created_at`, `last_verified_at`, `first_attached_at`, `last_unreferenced_at`, `delete_requested_at`, `deleted_at`)
SELECT 'ae57c551-032d-4f91-a808-2f2fed7721fd', 'ppswbs/catalogue/ae57c551-032d-4f91-a808-2f2fed7721fd', '2c1a807be17fee7637d850add006e556', '1794504632', 'jpg', 'd85f4178ad0e6df53e080862a27b14c223f41e60928896b249bb43f2da7441be', 'image/jpeg', 98853, 'READY', 'system:catalogue-seed', '8e5342e0-b89c-3cff-9ae6-d694fcf68935', '2026-11-12 17:30:31.687458', '2026-11-12 17:30:33.766985', '2026-11-12 17:30:35.260070', NULL, NULL, NULL FROM DUAL WHERE @run_catalogue AND NOT EXISTS (SELECT 1 FROM catalogue_media_objects WHERE id = 'ae57c551-032d-4f91-a808-2f2fed7721fd');
INSERT INTO `catalogue_media_objects` (`id`, `storage_key`, `provider_asset_id`, `provider_version`, `provider_format`, `checksum`, `content_type`, `byte_count`, `state`, `uploaded_by`, `command_id`, `created_at`, `last_verified_at`, `first_attached_at`, `last_unreferenced_at`, `delete_requested_at`, `deleted_at`)
SELECT 'd6282338-a736-4c17-acf8-b5e2b7d0715e', 'ppswbs/catalogue/d6282338-a736-4c17-acf8-b5e2b7d0715e', 'fdb23381746984780825626589108ebb', '1794504634', 'jpg', '30a85ea0727e02be5d221a520c74a89a1e8414fc1000a6156f191c2a38d84864', 'image/jpeg', 89496, 'READY', 'system:catalogue-seed', '3daff134-9d3e-346b-851e-286ea29cc2f4', '2026-11-12 17:30:33.795675', '2026-11-12 17:30:35.245153', '2026-11-12 17:30:35.260070', NULL, NULL, NULL FROM DUAL WHERE @run_catalogue AND NOT EXISTS (SELECT 1 FROM catalogue_media_objects WHERE id = 'd6282338-a736-4c17-acf8-b5e2b7d0715e');
INSERT INTO `catalogue_media_objects` (`id`, `storage_key`, `provider_asset_id`, `provider_version`, `provider_format`, `checksum`, `content_type`, `byte_count`, `state`, `uploaded_by`, `command_id`, `created_at`, `last_verified_at`, `first_attached_at`, `last_unreferenced_at`, `delete_requested_at`, `deleted_at`)
SELECT 'da58b598-476a-42a5-88da-cb8595ed9452', 'ppswbs/catalogue/da58b598-476a-42a5-88da-cb8595ed9452', 'cf372a6eb97b825d4218bb936e509650', '1794504615', 'jpg', '2f9899dcb02921643bf58db486fcbdea9781ba5a97d3257db1df45dc5eb0b877', 'image/jpeg', 112322, 'READY', 'system:catalogue-seed', '8e73b992-c311-314a-bb9e-eb812600403e', '2026-11-12 17:30:13.991205', '2026-11-12 17:30:15.684957', '2026-11-12 17:30:18.416782', NULL, NULL, NULL FROM DUAL WHERE @run_catalogue AND NOT EXISTS (SELECT 1 FROM catalogue_media_objects WHERE id = 'da58b598-476a-42a5-88da-cb8595ed9452');
INSERT INTO `catalogue_media_objects` (`id`, `storage_key`, `provider_asset_id`, `provider_version`, `provider_format`, `checksum`, `content_type`, `byte_count`, `state`, `uploaded_by`, `command_id`, `created_at`, `last_verified_at`, `first_attached_at`, `last_unreferenced_at`, `delete_requested_at`, `deleted_at`)
SELECT 'ddc93f57-3e47-4778-aef3-ab9a6be038ae', 'ppswbs/catalogue/ddc93f57-3e47-4778-aef3-ab9a6be038ae', 'a94ac2eb435079e4c76d61ac9b6ee7c9', '1794504626', 'jpg', '18df066be6459b105236e198cf1e85eea7a2f704113a589c3330368955749c1d', 'image/jpeg', 111333, 'READY', 'system:catalogue-seed', 'fade014f-aa68-3263-b0fd-60ba2579ec79', '2026-11-12 17:30:25.442203', '2026-11-12 17:30:27.241352', '2026-11-12 17:30:27.257750', NULL, NULL, NULL FROM DUAL WHERE @run_catalogue AND NOT EXISTS (SELECT 1 FROM catalogue_media_objects WHERE id = 'ddc93f57-3e47-4778-aef3-ab9a6be038ae');
INSERT INTO `catalogue_media_objects` (`id`, `storage_key`, `provider_asset_id`, `provider_version`, `provider_format`, `checksum`, `content_type`, `byte_count`, `state`, `uploaded_by`, `command_id`, `created_at`, `last_verified_at`, `first_attached_at`, `last_unreferenced_at`, `delete_requested_at`, `deleted_at`)
SELECT 'df38bb58-4723-4b28-8373-297eaac8fa30', 'ppswbs/catalogue/df38bb58-4723-4b28-8373-297eaac8fa30', '7710a30743e59e075d85b0db06a0d5b7', '1794504601', 'jpg', 'da36308faa936d50b77e9f4357799986bb1835f2f3c2c0c2d85ca6fc0833cf3f', 'image/jpeg', 102300, 'READY', 'system:catalogue-seed', '074189fc-2199-394e-9e0d-336f467b4370', '2026-11-12 17:29:59.979259', '2026-11-12 17:30:01.874499', '2026-11-12 17:30:01.894597', NULL, NULL, NULL FROM DUAL WHERE @run_catalogue AND NOT EXISTS (SELECT 1 FROM catalogue_media_objects WHERE id = 'df38bb58-4723-4b28-8373-297eaac8fa30');

-- Products
INSERT INTO `catalogue_products` (`id`, `name`, `description`, `category_id`, `published`, `first_published_at`, `deleted_at`, `deleted_by`, `content_version`, `version`, `created_at`, `updated_at`)
SELECT (@bp+1), 'Nhẫn bạc vân búa bản nhọn', 'Nhẫn bạc 925 bản dẹt, mặt trên vuốt nhọn nhẹ và đánh vân búa thủ công nên bắt sáng lấp lánh. Đeo hằng ngày hoặc đeo chồng cùng nhẫn khác đều hợp.', (SELECT id FROM catalogue_categories WHERE name_key = X'6E68E1BAAB6E2062E1BAA163207472C6A16E'), 1, '2026-11-12 17:29:49.084142', NULL, NULL, 0, 1, '2026-11-12 17:29:48.029524', '2026-11-12 17:29:49.110954' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_products` (`id`, `name`, `description`, `category_id`, `published`, `first_published_at`, `deleted_at`, `deleted_by`, `content_version`, `version`, `created_at`, `updated_at`)
SELECT (@bp+2), 'Nhẫn bạc mèo xoắn', 'Nhẫn hở bằng bạc 925: một đầu là dáng mèo ngồi cắt từ tấm bạc, đầu kia cuộn xoắn ốc như đuôi mèo. Kiểu hở nên nới hoặc khép nhẹ được cho vừa tay.', (SELECT id FROM catalogue_categories WHERE name_key = X'6E68E1BAAB6E2062E1BAA163207472C6A16E'), 1, '2026-11-12 17:29:51.303512', NULL, NULL, 0, 1, '2026-11-12 17:29:50.919975', '2026-11-12 17:29:51.311702' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_products` (`id`, `name`, `description`, `category_id`, `published`, `first_published_at`, `deleted_at`, `deleted_by`, `content_version`, `version`, `created_at`, `updated_at`)
SELECT (@bp+3), 'Nhẫn bạc trăng khuyết thạch anh tím', 'Vầng trăng khuyết bạc ôm một viên thạch anh tím vuông nhỏ, đai nhẫn mảnh. Thiết kế tối giản, hợp đeo ngón áp út hoặc ngón út.', (SELECT id FROM catalogue_categories WHERE name_key = X'6E68E1BAAB6E20C491C3A1207175C3BD'), 1, '2026-11-12 17:29:53.450958', NULL, NULL, 0, 1, '2026-11-12 17:29:53.080587', '2026-11-12 17:29:53.458951' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_products` (`id`, `name`, `description`, `category_id`, `published`, `first_published_at`, `deleted_at`, `deleted_by`, `content_version`, `version`, `created_at`, `updated_at`)
SELECT (@bp+4), 'Nhẫn bạc sóng nước peridot', 'Đai bạc uốn lượn như con sóng, giữa sóng là viên peridot xanh lá mài giác, chấu giữ đá gọn gàng.', (SELECT id FROM catalogue_categories WHERE name_key = X'6E68E1BAAB6E20C491C3A1207175C3BD'), 1, '2026-11-12 17:29:55.651108', NULL, NULL, 0, 1, '2026-11-12 17:29:55.288068', '2026-11-12 17:29:55.659118' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_products` (`id`, `name`, `description`, `category_id`, `published`, `first_published_at`, `deleted_at`, `deleted_by`, `content_version`, `version`, `created_at`, `updated_at`)
SELECT (@bp+5), 'Nhẫn bạc mắt garnet', 'Hai sợi bạc tách ra rồi khép lại thành hình con mắt, ở giữa là viên garnet tím đỏ tròn trong ổ bạc.', (SELECT id FROM catalogue_categories WHERE name_key = X'6E68E1BAAB6E20C491C3A1207175C3BD'), 1, '2026-11-12 17:29:57.746730', NULL, NULL, 0, 1, '2026-11-12 17:29:57.385635', '2026-11-12 17:29:57.755916' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_products` (`id`, `name`, `description`, `category_id`, `published`, `first_published_at`, `deleted_at`, `deleted_by`, `content_version`, `version`, `created_at`, `updated_at`)
SELECT (@bp+6), 'Nhẫn bạc xoáy citrine', 'Hai vòng bạc mảnh xoáy quanh một viên citrine vàng chanh, tạo cảm giác nhẹ và trong.', (SELECT id FROM catalogue_categories WHERE name_key = X'6E68E1BAAB6E20C491C3A1207175C3BD'), 1, '2026-11-12 17:29:59.949850', NULL, NULL, 0, 1, '2026-11-12 17:29:59.570348', '2026-11-12 17:29:59.954382' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_products` (`id`, `name`, `description`, `category_id`, `published`, `first_published_at`, `deleted_at`, `deleted_by`, `content_version`, `version`, `created_at`, `updated_at`)
SELECT (@bp+7), 'Nhẫn bạc garnet tròn', 'Viên garnet đỏ rượu mài giác tròn, viền chấu hoa nhỏ trên đai bạc mảnh; món quà dễ chọn cho người sinh tháng Một.', (SELECT id FROM catalogue_categories WHERE name_key = X'6E68E1BAAB6E20C491C3A1207175C3BD'), 1, '2026-11-12 17:30:02.233351', NULL, NULL, 0, 1, '2026-11-12 17:30:01.895598', '2026-11-12 17:30:02.238359' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_products` (`id`, `name`, `description`, `category_id`, `published`, `first_published_at`, `deleted_at`, `deleted_by`, `content_version`, `version`, `created_at`, `updated_at`)
SELECT (@bp+8), 'Nhẫn bạc hổ phách', 'Viên hổ phách cam mài cabochon trong ổ bạc tròn, đai nhẫn đánh vân búa nhẹ. Màu ấm, càng đeo càng bóng.', (SELECT id FROM catalogue_categories WHERE name_key = X'6E68E1BAAB6E20C491C3A1207175C3BD'), 1, '2026-11-12 17:30:04.445630', NULL, NULL, 0, 1, '2026-11-12 17:30:03.964974', '2026-11-12 17:30:04.455136' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_products` (`id`, `name`, `description`, `category_id`, `published`, `first_published_at`, `deleted_at`, `deleted_by`, `content_version`, `version`, `created_at`, `updated_at`)
SELECT (@bp+9), 'Nhẫn bạc cành lá citrine', 'Cành bạc nhỏ có lá ôm lấy viên citrine vàng hổ phách, đai nhẫn mảnh như nhánh cây.', (SELECT id FROM catalogue_categories WHERE name_key = X'6E68E1BAAB6E20C491C3A1207175C3BD'), 1, '2026-11-12 17:30:06.687952', NULL, NULL, 0, 1, '2026-11-12 17:30:06.283666', '2026-11-12 17:30:06.699385' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_products` (`id`, `name`, `description`, `category_id`, `published`, `first_published_at`, `deleted_at`, `deleted_by`, `content_version`, `version`, `created_at`, `updated_at`)
SELECT (@bp+10), 'Nhẫn bạc peridot oval', 'Viên peridot xanh ô liu mài oval trong ổ bạc, hai đầu đai nhẫn xoắn nhẹ ôm lấy ổ đá.', (SELECT id FROM catalogue_categories WHERE name_key = X'6E68E1BAAB6E20C491C3A1207175C3BD'), 1, '2026-11-12 17:30:09.451958', NULL, NULL, 0, 1, '2026-11-12 17:30:08.625428', '2026-11-12 17:30:09.462385' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_products` (`id`, `name`, `description`, `category_id`, `published`, `first_published_at`, `deleted_at`, `deleted_by`, `content_version`, `version`, `created_at`, `updated_at`)
SELECT (@bp+11), 'Nhẫn hoa văn vàng thạch anh tím', 'Đai nhẫn mạ vàng chạm hoa lá cổ điển, đính viên thạch anh tím mài hình hạt thóc bằng bốn chấu.', (SELECT id FROM catalogue_categories WHERE name_key = X'6E68E1BAAB6E20C491C3A1207175C3BD'), 1, '2026-11-12 17:30:11.850273', NULL, NULL, 0, 1, '2026-11-12 17:30:11.573822', '2026-11-12 17:30:11.857293' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_products` (`id`, `name`, `description`, `category_id`, `published`, `first_published_at`, `deleted_at`, `deleted_by`, `content_version`, `version`, `created_at`, `updated_at`)
SELECT (@bp+12), 'Nhẫn bạc lá ngọc xanh', 'Viên chalcedony xanh ngọc mài cabochon, hai bên điểm lá bạc chạm gân, nhẹ nhàng và tươi mát.', (SELECT id FROM catalogue_categories WHERE name_key = X'6E68E1BAAB6E20C491C3A1207175C3BD'), 1, '2026-11-12 17:30:13.954500', NULL, NULL, 0, 1, '2026-11-12 17:30:13.578467', '2026-11-12 17:30:13.963498' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_products` (`id`, `name`, `description`, `category_id`, `published`, `first_published_at`, `deleted_at`, `deleted_by`, `content_version`, `version`, `created_at`, `updated_at`)
SELECT (@bp+13), 'Nhẫn bạc mã não cây chữ nhật', 'Mặt mã não cây cắt chữ nhật, vân đen như cành cây in trong đá trong; hai vai nhẫn chạm hoa văn nhỏ. Mỗi viên một hoa văn riêng.', (SELECT id FROM catalogue_categories WHERE name_key = X'6E68E1BAAB6E206DC3A3206EC3A36F'), 1, '2026-11-12 17:30:18.838739', NULL, NULL, 0, 1, '2026-11-12 17:30:18.416782', '2026-11-12 17:30:18.847747' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_products` (`id`, `name`, `description`, `category_id`, `published`, `first_published_at`, `deleted_at`, `deleted_by`, `content_version`, `version`, `created_at`, `updated_at`)
SELECT (@bp+14), 'Nhẫn bạc mã não cây vuông', 'Mặt mã não cây vuông bo góc, nền đá sáng với vân cành nâu nhạt, viền bạc mảnh.', (SELECT id FROM catalogue_categories WHERE name_key = X'6E68E1BAAB6E206DC3A3206EC3A36F'), 1, '2026-11-12 17:30:21.158377', NULL, NULL, 0, 1, '2026-11-12 17:30:20.815669', '2026-11-12 17:30:21.168907' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_products` (`id`, `name`, `description`, `category_id`, `published`, `first_published_at`, `deleted_at`, `deleted_by`, `content_version`, `version`, `created_at`, `updated_at`)
SELECT (@bp+15), 'Nhẫn bạc mã não cây oval ngang', 'Mặt mã não cây oval đặt ngang, vân đen loang như tranh thủy mặc trên nền đá xám trong.', (SELECT id FROM catalogue_categories WHERE name_key = X'6E68E1BAAB6E206DC3A3206EC3A36F'), 1, '2026-11-12 17:30:23.323413', NULL, NULL, 0, 1, '2026-11-12 17:30:23.022787', '2026-11-12 17:30:23.330307' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_products` (`id`, `name`, `description`, `category_id`, `published`, `first_published_at`, `deleted_at`, `deleted_by`, `content_version`, `version`, `created_at`, `updated_at`)
SELECT (@bp+16), 'Nhẫn bạc mã não cây oval dọc', 'Mặt mã não cây oval đặt dọc theo ngón tay, vân cành mảnh, làm ngón tay trông thon dài hơn.', (SELECT id FROM catalogue_categories WHERE name_key = X'6E68E1BAAB6E206DC3A3206EC3A36F'), 1, '2026-11-12 17:30:25.411224', NULL, NULL, 0, 1, '2026-11-12 17:30:25.113208', '2026-11-12 17:30:25.416203' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_products` (`id`, `name`, `description`, `category_id`, `published`, `first_published_at`, `deleted_at`, `deleted_by`, `content_version`, `version`, `created_at`, `updated_at`)
SELECT (@bp+17), 'Nhẫn bạc mã não rêu trái tim', 'Mã não rêu mài trái tim, bên trong có những mảng xanh rêu như khu vườn thu nhỏ, ổ bạc ôm sát.', (SELECT id FROM catalogue_categories WHERE name_key = X'6E68E1BAAB6E206DC3A3206EC3A36F'), 1, '2026-11-12 17:30:27.542654', NULL, NULL, 0, 1, '2026-11-12 17:30:27.258752', '2026-11-12 17:30:27.552771' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_products` (`id`, `name`, `description`, `category_id`, `published`, `first_published_at`, `deleted_at`, `deleted_by`, `content_version`, `version`, `created_at`, `updated_at`)
SELECT (@bp+18), 'Nhẫn bạc mã não rêu giọt nước', 'Viên mã não rêu lớn mài giọt nước, vân xanh đậm nhạt đan xen; đai bạc trơn để đá làm điểm nhấn.', (SELECT id FROM catalogue_categories WHERE name_key = X'6E68E1BAAB6E206DC3A3206EC3A36F'), 1, '2026-11-12 17:30:29.601556', NULL, NULL, 0, 1, '2026-11-12 17:30:29.279608', '2026-11-12 17:30:29.609565' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_products` (`id`, `name`, `description`, `category_id`, `published`, `first_published_at`, `deleted_at`, `deleted_by`, `content_version`, `version`, `created_at`, `updated_at`)
SELECT (@bp+19), 'Nhẫn bạc ngọc xanh quấn thừng', 'Viên chalcedony xanh ngọc thuôn dài được giữ bằng sợi bạc xoắn thừng quấn quanh đai nhẫn bản dẹt.', (SELECT id FROM catalogue_categories WHERE name_key = X'6E68E1BAAB6E20C491C3A1207468C3B4'), 1, '2026-11-12 17:30:31.657309', NULL, NULL, 0, 1, '2026-11-12 17:30:31.355178', '2026-11-12 17:30:31.663378' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_products` (`id`, `name`, `description`, `category_id`, `published`, `first_published_at`, `deleted_at`, `deleted_by`, `content_version`, `version`, `created_at`, `updated_at`)
SELECT (@bp+20), 'Nhẫn bạc thạch anh thô', 'Viên thạch anh thô còn nguyên mặt tinh thể, trong lòng có ánh xanh tím, ôm bằng viền bạc gò tay.', (SELECT id FROM catalogue_categories WHERE name_key = X'6E68E1BAAB6E20C491C3A1207468C3B4'), 1, '2026-11-12 17:30:35.508052', NULL, NULL, 0, 1, '2026-11-12 17:30:35.260070', '2026-11-12 17:30:35.513014' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_products` (`id`, `name`, `description`, `category_id`, `published`, `first_published_at`, `deleted_at`, `deleted_by`, `content_version`, `version`, `created_at`, `updated_at`)
SELECT (@bp+21), 'Nhẫn bạc hở tourmaline xanh', 'Nhẫn hở với hai thanh tourmaline xanh lá thô ở hai đầu, kẹp bằng khâu bạc; nới khép nhẹ cho vừa tay.', (SELECT id FROM catalogue_categories WHERE name_key = X'6E68E1BAAB6E20C491C3A1207468C3B4'), 1, '2026-11-12 17:30:37.772959', NULL, NULL, 0, 1, '2026-11-12 17:30:37.509310', '2026-11-12 17:30:37.777010' FROM DUAL WHERE @run_catalogue;

-- Variants (prices are linked to their proposals further down)
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+1), (@bp+1), 7, CONCAT('SP', LPAD((@bp+1), 6, '0'), '-S', LPAD(7, 2, '0')), 5, 1, 390000, (@bpp+1), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+2), (@bp+1), 8, CONCAT('SP', LPAD((@bp+1), 6, '0'), '-S', LPAD(8, 2, '0')), 5, 1, 390000, (@bpp+2), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+3), (@bp+1), 9, CONCAT('SP', LPAD((@bp+1), 6, '0'), '-S', LPAD(9, 2, '0')), 5, 1, 390000, (@bpp+3), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+4), (@bp+1), 10, CONCAT('SP', LPAD((@bp+1), 6, '0'), '-S', LPAD(10, 2, '0')), 5, 1, 390000, (@bpp+4), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+5), (@bp+1), 11, CONCAT('SP', LPAD((@bp+1), 6, '0'), '-S', LPAD(11, 2, '0')), 5, 1, 390000, (@bpp+5), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+6), (@bp+1), 12, CONCAT('SP', LPAD((@bp+1), 6, '0'), '-S', LPAD(12, 2, '0')), 5, 1, 390000, (@bpp+6), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+7), (@bp+1), 13, CONCAT('SP', LPAD((@bp+1), 6, '0'), '-S', LPAD(13, 2, '0')), 5, 1, 390000, (@bpp+7), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+8), (@bp+1), 14, CONCAT('SP', LPAD((@bp+1), 6, '0'), '-S', LPAD(14, 2, '0')), 5, 1, 390000, (@bpp+8), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+9), (@bp+2), 6, CONCAT('SP', LPAD((@bp+2), 6, '0'), '-S', LPAD(6, 2, '0')), 3, 1, 420000, (@bpp+9), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+10), (@bp+2), 7, CONCAT('SP', LPAD((@bp+2), 6, '0'), '-S', LPAD(7, 2, '0')), 3, 1, 420000, (@bpp+10), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+11), (@bp+2), 8, CONCAT('SP', LPAD((@bp+2), 6, '0'), '-S', LPAD(8, 2, '0')), 3, 1, 420000, (@bpp+11), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+12), (@bp+2), 9, CONCAT('SP', LPAD((@bp+2), 6, '0'), '-S', LPAD(9, 2, '0')), 3, 1, 420000, (@bpp+12), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+13), (@bp+2), 10, CONCAT('SP', LPAD((@bp+2), 6, '0'), '-S', LPAD(10, 2, '0')), 3, 1, 420000, (@bpp+13), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+14), (@bp+2), 11, CONCAT('SP', LPAD((@bp+2), 6, '0'), '-S', LPAD(11, 2, '0')), 3, 1, 420000, (@bpp+14), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+15), (@bp+2), 12, CONCAT('SP', LPAD((@bp+2), 6, '0'), '-S', LPAD(12, 2, '0')), 3, 1, 420000, (@bpp+15), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+16), (@bp+3), 6, CONCAT('SP', LPAD((@bp+3), 6, '0'), '-S', LPAD(6, 2, '0')), 4, 1, 520000, (@bpp+16), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+17), (@bp+3), 7, CONCAT('SP', LPAD((@bp+3), 6, '0'), '-S', LPAD(7, 2, '0')), 4, 1, 520000, (@bpp+17), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+18), (@bp+3), 8, CONCAT('SP', LPAD((@bp+3), 6, '0'), '-S', LPAD(8, 2, '0')), 4, 1, 520000, (@bpp+18), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+19), (@bp+3), 9, CONCAT('SP', LPAD((@bp+3), 6, '0'), '-S', LPAD(9, 2, '0')), 4, 1, 520000, (@bpp+19), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+20), (@bp+3), 10, CONCAT('SP', LPAD((@bp+3), 6, '0'), '-S', LPAD(10, 2, '0')), 4, 1, 520000, (@bpp+20), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+21), (@bp+3), 11, CONCAT('SP', LPAD((@bp+3), 6, '0'), '-S', LPAD(11, 2, '0')), 4, 1, 520000, (@bpp+21), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+22), (@bp+3), 12, CONCAT('SP', LPAD((@bp+3), 6, '0'), '-S', LPAD(12, 2, '0')), 4, 1, 520000, (@bpp+22), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+23), (@bp+3), 13, CONCAT('SP', LPAD((@bp+3), 6, '0'), '-S', LPAD(13, 2, '0')), 4, 1, 520000, (@bpp+23), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+24), (@bp+4), 6, CONCAT('SP', LPAD((@bp+4), 6, '0'), '-S', LPAD(6, 2, '0')), 4, 1, 560000, (@bpp+24), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+25), (@bp+4), 7, CONCAT('SP', LPAD((@bp+4), 6, '0'), '-S', LPAD(7, 2, '0')), 4, 1, 560000, (@bpp+25), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+26), (@bp+4), 8, CONCAT('SP', LPAD((@bp+4), 6, '0'), '-S', LPAD(8, 2, '0')), 4, 1, 560000, (@bpp+26), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+27), (@bp+4), 9, CONCAT('SP', LPAD((@bp+4), 6, '0'), '-S', LPAD(9, 2, '0')), 4, 1, 560000, (@bpp+27), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+28), (@bp+4), 10, CONCAT('SP', LPAD((@bp+4), 6, '0'), '-S', LPAD(10, 2, '0')), 4, 1, 560000, (@bpp+28), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+29), (@bp+4), 11, CONCAT('SP', LPAD((@bp+4), 6, '0'), '-S', LPAD(11, 2, '0')), 4, 1, 560000, (@bpp+29), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+30), (@bp+4), 12, CONCAT('SP', LPAD((@bp+4), 6, '0'), '-S', LPAD(12, 2, '0')), 4, 1, 560000, (@bpp+30), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+31), (@bp+4), 13, CONCAT('SP', LPAD((@bp+4), 6, '0'), '-S', LPAD(13, 2, '0')), 4, 1, 560000, (@bpp+31), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+32), (@bp+5), 7, CONCAT('SP', LPAD((@bp+5), 6, '0'), '-S', LPAD(7, 2, '0')), 4, 1, 540000, (@bpp+32), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+33), (@bp+5), 8, CONCAT('SP', LPAD((@bp+5), 6, '0'), '-S', LPAD(8, 2, '0')), 4, 1, 540000, (@bpp+33), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+34), (@bp+5), 9, CONCAT('SP', LPAD((@bp+5), 6, '0'), '-S', LPAD(9, 2, '0')), 4, 1, 540000, (@bpp+34), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+35), (@bp+5), 10, CONCAT('SP', LPAD((@bp+5), 6, '0'), '-S', LPAD(10, 2, '0')), 4, 1, 540000, (@bpp+35), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+36), (@bp+5), 11, CONCAT('SP', LPAD((@bp+5), 6, '0'), '-S', LPAD(11, 2, '0')), 4, 1, 540000, (@bpp+36), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+37), (@bp+5), 12, CONCAT('SP', LPAD((@bp+5), 6, '0'), '-S', LPAD(12, 2, '0')), 4, 1, 540000, (@bpp+37), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+38), (@bp+5), 13, CONCAT('SP', LPAD((@bp+5), 6, '0'), '-S', LPAD(13, 2, '0')), 4, 1, 540000, (@bpp+38), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+39), (@bp+5), 14, CONCAT('SP', LPAD((@bp+5), 6, '0'), '-S', LPAD(14, 2, '0')), 4, 1, 540000, (@bpp+39), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+40), (@bp+6), 6, CONCAT('SP', LPAD((@bp+6), 6, '0'), '-S', LPAD(6, 2, '0')), 4, 1, 520000, (@bpp+40), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+41), (@bp+6), 7, CONCAT('SP', LPAD((@bp+6), 6, '0'), '-S', LPAD(7, 2, '0')), 4, 1, 520000, (@bpp+41), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+42), (@bp+6), 8, CONCAT('SP', LPAD((@bp+6), 6, '0'), '-S', LPAD(8, 2, '0')), 4, 1, 520000, (@bpp+42), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+43), (@bp+6), 9, CONCAT('SP', LPAD((@bp+6), 6, '0'), '-S', LPAD(9, 2, '0')), 4, 1, 520000, (@bpp+43), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+44), (@bp+6), 10, CONCAT('SP', LPAD((@bp+6), 6, '0'), '-S', LPAD(10, 2, '0')), 4, 1, 520000, (@bpp+44), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+45), (@bp+6), 11, CONCAT('SP', LPAD((@bp+6), 6, '0'), '-S', LPAD(11, 2, '0')), 4, 1, 520000, (@bpp+45), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+46), (@bp+6), 12, CONCAT('SP', LPAD((@bp+6), 6, '0'), '-S', LPAD(12, 2, '0')), 4, 1, 520000, (@bpp+46), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+47), (@bp+6), 13, CONCAT('SP', LPAD((@bp+6), 6, '0'), '-S', LPAD(13, 2, '0')), 4, 1, 520000, (@bpp+47), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+48), (@bp+7), 6, CONCAT('SP', LPAD((@bp+7), 6, '0'), '-S', LPAD(6, 2, '0')), 4, 1, 480000, (@bpp+48), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+49), (@bp+7), 7, CONCAT('SP', LPAD((@bp+7), 6, '0'), '-S', LPAD(7, 2, '0')), 4, 1, 480000, (@bpp+49), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+50), (@bp+7), 8, CONCAT('SP', LPAD((@bp+7), 6, '0'), '-S', LPAD(8, 2, '0')), 4, 1, 480000, (@bpp+50), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+51), (@bp+7), 9, CONCAT('SP', LPAD((@bp+7), 6, '0'), '-S', LPAD(9, 2, '0')), 4, 1, 480000, (@bpp+51), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+52), (@bp+7), 10, CONCAT('SP', LPAD((@bp+7), 6, '0'), '-S', LPAD(10, 2, '0')), 4, 1, 480000, (@bpp+52), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+53), (@bp+7), 11, CONCAT('SP', LPAD((@bp+7), 6, '0'), '-S', LPAD(11, 2, '0')), 4, 1, 480000, (@bpp+53), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+54), (@bp+7), 12, CONCAT('SP', LPAD((@bp+7), 6, '0'), '-S', LPAD(12, 2, '0')), 4, 1, 480000, (@bpp+54), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+55), (@bp+8), 6, CONCAT('SP', LPAD((@bp+8), 6, '0'), '-S', LPAD(6, 2, '0')), 3, 1, 590000, (@bpp+55), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+56), (@bp+8), 7, CONCAT('SP', LPAD((@bp+8), 6, '0'), '-S', LPAD(7, 2, '0')), 3, 1, 590000, (@bpp+56), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+57), (@bp+8), 8, CONCAT('SP', LPAD((@bp+8), 6, '0'), '-S', LPAD(8, 2, '0')), 3, 1, 590000, (@bpp+57), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+58), (@bp+8), 9, CONCAT('SP', LPAD((@bp+8), 6, '0'), '-S', LPAD(9, 2, '0')), 3, 1, 590000, (@bpp+58), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+59), (@bp+8), 10, CONCAT('SP', LPAD((@bp+8), 6, '0'), '-S', LPAD(10, 2, '0')), 3, 1, 590000, (@bpp+59), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+60), (@bp+8), 11, CONCAT('SP', LPAD((@bp+8), 6, '0'), '-S', LPAD(11, 2, '0')), 3, 1, 590000, (@bpp+60), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+61), (@bp+8), 12, CONCAT('SP', LPAD((@bp+8), 6, '0'), '-S', LPAD(12, 2, '0')), 3, 1, 590000, (@bpp+61), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+62), (@bp+8), 13, CONCAT('SP', LPAD((@bp+8), 6, '0'), '-S', LPAD(13, 2, '0')), 3, 1, 590000, (@bpp+62), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+63), (@bp+9), 6, CONCAT('SP', LPAD((@bp+9), 6, '0'), '-S', LPAD(6, 2, '0')), 4, 1, 560000, (@bpp+63), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+64), (@bp+9), 7, CONCAT('SP', LPAD((@bp+9), 6, '0'), '-S', LPAD(7, 2, '0')), 4, 1, 560000, (@bpp+64), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+65), (@bp+9), 8, CONCAT('SP', LPAD((@bp+9), 6, '0'), '-S', LPAD(8, 2, '0')), 4, 1, 560000, (@bpp+65), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+66), (@bp+9), 9, CONCAT('SP', LPAD((@bp+9), 6, '0'), '-S', LPAD(9, 2, '0')), 4, 1, 560000, (@bpp+66), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+67), (@bp+9), 10, CONCAT('SP', LPAD((@bp+9), 6, '0'), '-S', LPAD(10, 2, '0')), 4, 1, 650000, (@bpp+172), 2, NULL, 6 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+68), (@bp+9), 11, CONCAT('SP', LPAD((@bp+9), 6, '0'), '-S', LPAD(11, 2, '0')), 4, 1, 650000, (@bpp+173), 2, NULL, 6 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+69), (@bp+9), 12, CONCAT('SP', LPAD((@bp+9), 6, '0'), '-S', LPAD(12, 2, '0')), 4, 1, 650000, (@bpp+174), 2, NULL, 8 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+70), (@bp+10), 7, CONCAT('SP', LPAD((@bp+10), 6, '0'), '-S', LPAD(7, 2, '0')), 4, 1, 550000, (@bpp+70), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+71), (@bp+10), 8, CONCAT('SP', LPAD((@bp+10), 6, '0'), '-S', LPAD(8, 2, '0')), 4, 1, 550000, (@bpp+71), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+72), (@bp+10), 9, CONCAT('SP', LPAD((@bp+10), 6, '0'), '-S', LPAD(9, 2, '0')), 4, 1, 550000, (@bpp+72), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+73), (@bp+10), 10, CONCAT('SP', LPAD((@bp+10), 6, '0'), '-S', LPAD(10, 2, '0')), 4, 1, 550000, (@bpp+73), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+74), (@bp+10), 11, CONCAT('SP', LPAD((@bp+10), 6, '0'), '-S', LPAD(11, 2, '0')), 4, 1, 550000, (@bpp+74), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+75), (@bp+10), 12, CONCAT('SP', LPAD((@bp+10), 6, '0'), '-S', LPAD(12, 2, '0')), 4, 1, 550000, (@bpp+75), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+76), (@bp+10), 13, CONCAT('SP', LPAD((@bp+10), 6, '0'), '-S', LPAD(13, 2, '0')), 4, 1, 550000, (@bpp+76), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+77), (@bp+10), 14, CONCAT('SP', LPAD((@bp+10), 6, '0'), '-S', LPAD(14, 2, '0')), 4, 1, 550000, (@bpp+77), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+78), (@bp+11), 6, CONCAT('SP', LPAD((@bp+11), 6, '0'), '-S', LPAD(6, 2, '0')), 3, 1, 690000, (@bpp+78), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+79), (@bp+11), 7, CONCAT('SP', LPAD((@bp+11), 6, '0'), '-S', LPAD(7, 2, '0')), 3, 1, 690000, (@bpp+79), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+80), (@bp+11), 8, CONCAT('SP', LPAD((@bp+11), 6, '0'), '-S', LPAD(8, 2, '0')), 3, 1, 690000, (@bpp+80), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+81), (@bp+11), 9, CONCAT('SP', LPAD((@bp+11), 6, '0'), '-S', LPAD(9, 2, '0')), 3, 1, 690000, (@bpp+81), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+82), (@bp+11), 10, CONCAT('SP', LPAD((@bp+11), 6, '0'), '-S', LPAD(10, 2, '0')), 3, 1, 690000, (@bpp+82), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+83), (@bp+11), 11, CONCAT('SP', LPAD((@bp+11), 6, '0'), '-S', LPAD(11, 2, '0')), 3, 1, 690000, (@bpp+83), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+84), (@bp+11), 12, CONCAT('SP', LPAD((@bp+11), 6, '0'), '-S', LPAD(12, 2, '0')), 3, 1, 690000, (@bpp+84), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+85), (@bp+12), 6, CONCAT('SP', LPAD((@bp+12), 6, '0'), '-S', LPAD(6, 2, '0')), 4, 1, 520000, (@bpp+85), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+86), (@bp+12), 7, CONCAT('SP', LPAD((@bp+12), 6, '0'), '-S', LPAD(7, 2, '0')), 4, 1, 520000, (@bpp+86), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+87), (@bp+12), 8, CONCAT('SP', LPAD((@bp+12), 6, '0'), '-S', LPAD(8, 2, '0')), 4, 1, 520000, (@bpp+87), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+88), (@bp+12), 9, CONCAT('SP', LPAD((@bp+12), 6, '0'), '-S', LPAD(9, 2, '0')), 4, 1, 520000, (@bpp+88), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+89), (@bp+12), 10, CONCAT('SP', LPAD((@bp+12), 6, '0'), '-S', LPAD(10, 2, '0')), 4, 1, 520000, (@bpp+89), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+90), (@bp+12), 11, CONCAT('SP', LPAD((@bp+12), 6, '0'), '-S', LPAD(11, 2, '0')), 4, 1, 520000, (@bpp+90), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+91), (@bp+12), 12, CONCAT('SP', LPAD((@bp+12), 6, '0'), '-S', LPAD(12, 2, '0')), 4, 1, 520000, (@bpp+91), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+92), (@bp+13), 8, CONCAT('SP', LPAD((@bp+13), 6, '0'), '-S', LPAD(8, 2, '0')), 2, 1, 750000, (@bpp+92), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+93), (@bp+13), 9, CONCAT('SP', LPAD((@bp+13), 6, '0'), '-S', LPAD(9, 2, '0')), 2, 1, 750000, (@bpp+93), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+94), (@bp+13), 10, CONCAT('SP', LPAD((@bp+13), 6, '0'), '-S', LPAD(10, 2, '0')), 2, 1, 750000, (@bpp+94), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+95), (@bp+13), 11, CONCAT('SP', LPAD((@bp+13), 6, '0'), '-S', LPAD(11, 2, '0')), 2, 1, 750000, (@bpp+95), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+96), (@bp+13), 12, CONCAT('SP', LPAD((@bp+13), 6, '0'), '-S', LPAD(12, 2, '0')), 2, 1, 750000, (@bpp+96), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+97), (@bp+13), 13, CONCAT('SP', LPAD((@bp+13), 6, '0'), '-S', LPAD(13, 2, '0')), 2, 1, 750000, (@bpp+97), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+98), (@bp+13), 14, CONCAT('SP', LPAD((@bp+13), 6, '0'), '-S', LPAD(14, 2, '0')), 2, 1, 750000, (@bpp+98), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+99), (@bp+13), 15, CONCAT('SP', LPAD((@bp+13), 6, '0'), '-S', LPAD(15, 2, '0')), 2, 1, 750000, (@bpp+99), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+100), (@bp+14), 7, CONCAT('SP', LPAD((@bp+14), 6, '0'), '-S', LPAD(7, 2, '0')), 2, 1, 680000, (@bpp+100), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+101), (@bp+14), 8, CONCAT('SP', LPAD((@bp+14), 6, '0'), '-S', LPAD(8, 2, '0')), 2, 1, 680000, (@bpp+101), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+102), (@bp+14), 9, CONCAT('SP', LPAD((@bp+14), 6, '0'), '-S', LPAD(9, 2, '0')), 2, 1, 680000, (@bpp+102), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+103), (@bp+14), 10, CONCAT('SP', LPAD((@bp+14), 6, '0'), '-S', LPAD(10, 2, '0')), 2, 1, 680000, (@bpp+103), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+104), (@bp+14), 11, CONCAT('SP', LPAD((@bp+14), 6, '0'), '-S', LPAD(11, 2, '0')), 2, 1, 680000, (@bpp+104), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+105), (@bp+14), 12, CONCAT('SP', LPAD((@bp+14), 6, '0'), '-S', LPAD(12, 2, '0')), 2, 1, 680000, (@bpp+105), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+106), (@bp+14), 13, CONCAT('SP', LPAD((@bp+14), 6, '0'), '-S', LPAD(13, 2, '0')), 2, 1, 680000, (@bpp+106), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+107), (@bp+14), 14, CONCAT('SP', LPAD((@bp+14), 6, '0'), '-S', LPAD(14, 2, '0')), 2, 1, 680000, (@bpp+107), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+108), (@bp+15), 8, CONCAT('SP', LPAD((@bp+15), 6, '0'), '-S', LPAD(8, 2, '0')), 2, 1, 720000, (@bpp+108), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+109), (@bp+15), 9, CONCAT('SP', LPAD((@bp+15), 6, '0'), '-S', LPAD(9, 2, '0')), 2, 1, 720000, (@bpp+109), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+110), (@bp+15), 10, CONCAT('SP', LPAD((@bp+15), 6, '0'), '-S', LPAD(10, 2, '0')), 2, 1, 720000, (@bpp+110), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+111), (@bp+15), 11, CONCAT('SP', LPAD((@bp+15), 6, '0'), '-S', LPAD(11, 2, '0')), 2, 1, 720000, (@bpp+111), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+112), (@bp+15), 12, CONCAT('SP', LPAD((@bp+15), 6, '0'), '-S', LPAD(12, 2, '0')), 2, 1, 720000, (@bpp+112), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+113), (@bp+15), 13, CONCAT('SP', LPAD((@bp+15), 6, '0'), '-S', LPAD(13, 2, '0')), 2, 1, 720000, (@bpp+113), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+114), (@bp+15), 14, CONCAT('SP', LPAD((@bp+15), 6, '0'), '-S', LPAD(14, 2, '0')), 2, 1, 720000, (@bpp+114), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+115), (@bp+15), 15, CONCAT('SP', LPAD((@bp+15), 6, '0'), '-S', LPAD(15, 2, '0')), 2, 1, 720000, (@bpp+115), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+116), (@bp+16), 7, CONCAT('SP', LPAD((@bp+16), 6, '0'), '-S', LPAD(7, 2, '0')), 2, 1, 680000, (@bpp+116), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+117), (@bp+16), 8, CONCAT('SP', LPAD((@bp+16), 6, '0'), '-S', LPAD(8, 2, '0')), 2, 1, 680000, (@bpp+117), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+118), (@bp+16), 9, CONCAT('SP', LPAD((@bp+16), 6, '0'), '-S', LPAD(9, 2, '0')), 2, 1, 680000, (@bpp+118), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+119), (@bp+16), 10, CONCAT('SP', LPAD((@bp+16), 6, '0'), '-S', LPAD(10, 2, '0')), 2, 1, 680000, (@bpp+119), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+120), (@bp+16), 11, CONCAT('SP', LPAD((@bp+16), 6, '0'), '-S', LPAD(11, 2, '0')), 2, 1, 680000, (@bpp+120), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+121), (@bp+16), 12, CONCAT('SP', LPAD((@bp+16), 6, '0'), '-S', LPAD(12, 2, '0')), 2, 1, 680000, (@bpp+121), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+122), (@bp+16), 13, CONCAT('SP', LPAD((@bp+16), 6, '0'), '-S', LPAD(13, 2, '0')), 2, 1, 680000, (@bpp+122), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+123), (@bp+16), 14, CONCAT('SP', LPAD((@bp+16), 6, '0'), '-S', LPAD(14, 2, '0')), 2, 1, 680000, (@bpp+123), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+124), (@bp+17), 6, CONCAT('SP', LPAD((@bp+17), 6, '0'), '-S', LPAD(6, 2, '0')), 3, 1, 620000, (@bpp+124), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+125), (@bp+17), 7, CONCAT('SP', LPAD((@bp+17), 6, '0'), '-S', LPAD(7, 2, '0')), 3, 1, 620000, (@bpp+125), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+126), (@bp+17), 8, CONCAT('SP', LPAD((@bp+17), 6, '0'), '-S', LPAD(8, 2, '0')), 3, 1, 620000, (@bpp+126), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+127), (@bp+17), 9, CONCAT('SP', LPAD((@bp+17), 6, '0'), '-S', LPAD(9, 2, '0')), 3, 1, 620000, (@bpp+127), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+128), (@bp+17), 10, CONCAT('SP', LPAD((@bp+17), 6, '0'), '-S', LPAD(10, 2, '0')), 3, 1, 620000, (@bpp+128), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+129), (@bp+17), 11, CONCAT('SP', LPAD((@bp+17), 6, '0'), '-S', LPAD(11, 2, '0')), 3, 1, 620000, (@bpp+129), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+130), (@bp+17), 12, CONCAT('SP', LPAD((@bp+17), 6, '0'), '-S', LPAD(12, 2, '0')), 3, 1, 620000, (@bpp+130), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+131), (@bp+17), 13, CONCAT('SP', LPAD((@bp+17), 6, '0'), '-S', LPAD(13, 2, '0')), 3, 1, 620000, (@bpp+131), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+132), (@bp+18), 7, CONCAT('SP', LPAD((@bp+18), 6, '0'), '-S', LPAD(7, 2, '0')), 3, 1, 650000, (@bpp+132), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+133), (@bp+18), 8, CONCAT('SP', LPAD((@bp+18), 6, '0'), '-S', LPAD(8, 2, '0')), 3, 1, 650000, (@bpp+133), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+134), (@bp+18), 9, CONCAT('SP', LPAD((@bp+18), 6, '0'), '-S', LPAD(9, 2, '0')), 3, 1, 650000, (@bpp+134), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+135), (@bp+18), 10, CONCAT('SP', LPAD((@bp+18), 6, '0'), '-S', LPAD(10, 2, '0')), 3, 1, 650000, (@bpp+135), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+136), (@bp+18), 11, CONCAT('SP', LPAD((@bp+18), 6, '0'), '-S', LPAD(11, 2, '0')), 3, 1, 650000, (@bpp+136), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+137), (@bp+18), 12, CONCAT('SP', LPAD((@bp+18), 6, '0'), '-S', LPAD(12, 2, '0')), 3, 1, 650000, (@bpp+137), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+138), (@bp+18), 13, CONCAT('SP', LPAD((@bp+18), 6, '0'), '-S', LPAD(13, 2, '0')), 3, 1, 650000, (@bpp+138), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+139), (@bp+18), 14, CONCAT('SP', LPAD((@bp+18), 6, '0'), '-S', LPAD(14, 2, '0')), 3, 1, 650000, (@bpp+139), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+140), (@bp+19), 7, CONCAT('SP', LPAD((@bp+19), 6, '0'), '-S', LPAD(7, 2, '0')), 4, 1, 480000, (@bpp+140), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+141), (@bp+19), 8, CONCAT('SP', LPAD((@bp+19), 6, '0'), '-S', LPAD(8, 2, '0')), 4, 1, 480000, (@bpp+141), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+142), (@bp+19), 9, CONCAT('SP', LPAD((@bp+19), 6, '0'), '-S', LPAD(9, 2, '0')), 4, 1, 480000, (@bpp+142), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+143), (@bp+19), 10, CONCAT('SP', LPAD((@bp+19), 6, '0'), '-S', LPAD(10, 2, '0')), 4, 1, 500000, (@bpp+167), 2, NULL, 6 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+144), (@bp+19), 11, CONCAT('SP', LPAD((@bp+19), 6, '0'), '-S', LPAD(11, 2, '0')), 4, 1, 500000, (@bpp+168), 2, NULL, 6 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+145), (@bp+19), 12, CONCAT('SP', LPAD((@bp+19), 6, '0'), '-S', LPAD(12, 2, '0')), 4, 1, 500000, (@bpp+169), 2, NULL, 6 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+146), (@bp+19), 13, CONCAT('SP', LPAD((@bp+19), 6, '0'), '-S', LPAD(13, 2, '0')), 4, 1, 500000, (@bpp+170), 2, NULL, 6 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+147), (@bp+19), 14, CONCAT('SP', LPAD((@bp+19), 6, '0'), '-S', LPAD(14, 2, '0')), 4, 1, 500000, (@bpp+171), 2, NULL, 6 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+148), (@bp+20), 6, CONCAT('SP', LPAD((@bp+20), 6, '0'), '-S', LPAD(6, 2, '0')), 3, 1, 590000, (@bpp+148), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+149), (@bp+20), 7, CONCAT('SP', LPAD((@bp+20), 6, '0'), '-S', LPAD(7, 2, '0')), 3, 1, 590000, (@bpp+149), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+150), (@bp+20), 8, CONCAT('SP', LPAD((@bp+20), 6, '0'), '-S', LPAD(8, 2, '0')), 3, 1, 590000, (@bpp+150), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+151), (@bp+20), 9, CONCAT('SP', LPAD((@bp+20), 6, '0'), '-S', LPAD(9, 2, '0')), 3, 1, 590000, (@bpp+151), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+152), (@bp+20), 10, CONCAT('SP', LPAD((@bp+20), 6, '0'), '-S', LPAD(10, 2, '0')), 3, 1, 650000, (@bpp+164), 2, NULL, 6 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+153), (@bp+20), 11, CONCAT('SP', LPAD((@bp+20), 6, '0'), '-S', LPAD(11, 2, '0')), 3, 1, 650000, (@bpp+165), 2, NULL, 6 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+154), (@bp+20), 12, CONCAT('SP', LPAD((@bp+20), 6, '0'), '-S', LPAD(12, 2, '0')), 3, 1, 650000, (@bpp+166), 3, NULL, 9 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+155), (@bp+21), 6, CONCAT('SP', LPAD((@bp+21), 6, '0'), '-S', LPAD(6, 2, '0')), 1, 1, 640000, (@bpp+155), 1, NULL, 4 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+156), (@bp+21), 7, CONCAT('SP', LPAD((@bp+21), 6, '0'), '-S', LPAD(7, 2, '0')), 1, 1, 640000, (@bpp+156), 1, NULL, 4 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+157), (@bp+21), 8, CONCAT('SP', LPAD((@bp+21), 6, '0'), '-S', LPAD(8, 2, '0')), 2, 1, 640000, (@bpp+157), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+158), (@bp+21), 9, CONCAT('SP', LPAD((@bp+21), 6, '0'), '-S', LPAD(9, 2, '0')), 2, 1, 640000, (@bpp+158), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+159), (@bp+21), 10, CONCAT('SP', LPAD((@bp+21), 6, '0'), '-S', LPAD(10, 2, '0')), 2, 1, 640000, (@bpp+159), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+160), (@bp+21), 11, CONCAT('SP', LPAD((@bp+21), 6, '0'), '-S', LPAD(11, 2, '0')), 2, 1, 640000, (@bpp+160), 1, NULL, 3 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variants` (`id`, `product_id`, `ring_size`, `sku`, `remaining_unsold`, `sale_enabled`, `applied_price`, `applied_proposal_id`, `applied_sequence`, `pending_proposal_id`, `version`)
SELECT (@bv+161), (@bp+21), 12, CONCAT('SP', LPAD((@bp+21), 6, '0'), '-S', LPAD(12, 2, '0')), 2, 1, 700000, (@bpp+162), 2, NULL, 6 FROM DUAL WHERE @run_catalogue;

-- Stock per branch
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+1), (@bv+1), 1, 3, 1, 0, '2026-11-13 00:29:48.068169', '2026-11-13 00:29:48.068169' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+2), (@bv+2), 1, 3, 1, 0, '2026-11-13 00:29:48.078614', '2026-11-13 00:29:48.078614' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+3), (@bv+3), 1, 3, 1, 0, '2026-11-13 00:29:48.080178', '2026-11-13 00:29:48.080178' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+4), (@bv+4), 1, 3, 1, 0, '2026-11-13 00:29:48.083865', '2026-11-13 00:29:48.083865' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+5), (@bv+5), 1, 3, 1, 0, '2026-11-13 00:29:48.085776', '2026-11-13 00:29:48.085776' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+6), (@bv+6), 1, 3, 1, 0, '2026-11-13 00:29:48.087439', '2026-11-13 00:29:48.087439' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+7), (@bv+7), 1, 3, 1, 0, '2026-11-13 00:29:48.088890', '2026-11-13 00:29:48.088890' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+8), (@bv+8), 1, 3, 1, 0, '2026-11-13 00:29:48.090355', '2026-11-13 00:29:48.090355' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+9), (@bv+1), 2, 2, 1, 0, '2026-11-13 00:29:48.091914', '2026-11-13 00:29:48.091914' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+10), (@bv+2), 2, 2, 1, 0, '2026-11-13 00:29:48.093157', '2026-11-13 00:29:48.093157' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+11), (@bv+3), 2, 2, 1, 0, '2026-11-13 00:29:48.094524', '2026-11-13 00:29:48.094524' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+12), (@bv+4), 2, 2, 1, 0, '2026-11-13 00:29:48.096027', '2026-11-13 00:29:48.096027' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+13), (@bv+5), 2, 2, 1, 0, '2026-11-13 00:29:48.097592', '2026-11-13 00:29:48.097592' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+14), (@bv+6), 2, 2, 1, 0, '2026-11-13 00:29:48.100493', '2026-11-13 00:29:48.100493' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+15), (@bv+7), 2, 2, 1, 0, '2026-11-13 00:29:48.101954', '2026-11-13 00:29:48.101954' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+16), (@bv+8), 2, 2, 1, 0, '2026-11-13 00:29:48.103577', '2026-11-13 00:29:48.103577' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+17), (@bv+9), 1, 2, 1, 0, '2026-11-13 00:29:50.926822', '2026-11-13 00:29:50.926822' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+18), (@bv+10), 1, 2, 1, 0, '2026-11-13 00:29:50.927460', '2026-11-13 00:29:50.927460' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+19), (@bv+11), 1, 2, 1, 0, '2026-11-13 00:29:50.928002', '2026-11-13 00:29:50.928002' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+20), (@bv+12), 1, 2, 1, 0, '2026-11-13 00:29:50.929141', '2026-11-13 00:29:50.929141' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+21), (@bv+13), 1, 2, 1, 0, '2026-11-13 00:29:50.929686', '2026-11-13 00:29:50.929686' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+22), (@bv+14), 1, 2, 1, 0, '2026-11-13 00:29:50.930486', '2026-11-13 00:29:50.930486' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+23), (@bv+15), 1, 2, 1, 0, '2026-11-13 00:29:50.931933', '2026-11-13 00:29:50.931933' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+24), (@bv+9), 2, 1, 1, 0, '2026-11-13 00:29:50.932915', '2026-11-13 00:29:50.932915' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+25), (@bv+10), 2, 1, 1, 0, '2026-11-13 00:29:50.933843', '2026-11-13 00:29:50.933843' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+26), (@bv+11), 2, 1, 1, 0, '2026-11-13 00:29:50.934923', '2026-11-13 00:29:50.934923' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+27), (@bv+12), 2, 1, 1, 0, '2026-11-13 00:29:50.935965', '2026-11-13 00:29:50.935965' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+28), (@bv+13), 2, 1, 1, 0, '2026-11-13 00:29:50.936913', '2026-11-13 00:29:50.936913' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+29), (@bv+14), 2, 1, 1, 0, '2026-11-13 00:29:50.937573', '2026-11-13 00:29:50.937573' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+30), (@bv+15), 2, 1, 1, 0, '2026-11-13 00:29:50.938085', '2026-11-13 00:29:50.938085' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+31), (@bv+16), 1, 2, 1, 0, '2026-11-13 00:29:53.091004', '2026-11-13 00:29:53.091004' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+32), (@bv+17), 1, 2, 1, 0, '2026-11-13 00:29:53.091937', '2026-11-13 00:29:53.091937' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+33), (@bv+18), 1, 2, 1, 0, '2026-11-13 00:29:53.092512', '2026-11-13 00:29:53.092512' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+34), (@bv+19), 1, 2, 1, 0, '2026-11-13 00:29:53.092955', '2026-11-13 00:29:53.092955' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+35), (@bv+20), 1, 2, 1, 0, '2026-11-13 00:29:53.093356', '2026-11-13 00:29:53.093356' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+36), (@bv+21), 1, 2, 1, 0, '2026-11-13 00:29:53.094048', '2026-11-13 00:29:53.094048' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+37), (@bv+22), 1, 2, 1, 0, '2026-11-13 00:29:53.094592', '2026-11-13 00:29:53.094592' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+38), (@bv+23), 1, 2, 1, 0, '2026-11-13 00:29:53.095280', '2026-11-13 00:29:53.095280' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+39), (@bv+16), 2, 2, 1, 0, '2026-11-13 00:29:53.095856', '2026-11-13 00:29:53.095856' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+40), (@bv+17), 2, 2, 1, 0, '2026-11-13 00:29:53.097303', '2026-11-13 00:29:53.097303' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+41), (@bv+18), 2, 2, 1, 0, '2026-11-13 00:29:53.098433', '2026-11-13 00:29:53.098433' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+42), (@bv+19), 2, 2, 1, 0, '2026-11-13 00:29:53.099123', '2026-11-13 00:29:53.099123' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+43), (@bv+20), 2, 2, 1, 0, '2026-11-13 00:29:53.099606', '2026-11-13 00:29:53.099606' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+44), (@bv+21), 2, 2, 1, 0, '2026-11-13 00:29:53.100122', '2026-11-13 00:29:53.100122' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+45), (@bv+22), 2, 2, 1, 0, '2026-11-13 00:29:53.100554', '2026-11-13 00:29:53.100554' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+46), (@bv+23), 2, 2, 1, 0, '2026-11-13 00:29:53.100995', '2026-11-13 00:29:53.100995' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+47), (@bv+24), 1, 2, 1, 0, '2026-11-13 00:29:55.301710', '2026-11-13 00:29:55.301710' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+48), (@bv+25), 1, 2, 1, 0, '2026-11-13 00:29:55.302472', '2026-11-13 00:29:55.302472' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+49), (@bv+26), 1, 2, 1, 0, '2026-11-13 00:29:55.303200', '2026-11-13 00:29:55.303200' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+50), (@bv+27), 1, 2, 1, 0, '2026-11-13 00:29:55.303795', '2026-11-13 00:29:55.303795' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+51), (@bv+28), 1, 2, 1, 0, '2026-11-13 00:29:55.304420', '2026-11-13 00:29:55.304420' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+52), (@bv+29), 1, 2, 1, 0, '2026-11-13 00:29:55.304911', '2026-11-13 00:29:55.304911' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+53), (@bv+30), 1, 2, 1, 0, '2026-11-13 00:29:55.305412', '2026-11-13 00:29:55.305412' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+54), (@bv+31), 1, 2, 1, 0, '2026-11-13 00:29:55.305903', '2026-11-13 00:29:55.305903' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+55), (@bv+24), 2, 2, 1, 0, '2026-11-13 00:29:55.306383', '2026-11-13 00:29:55.306383' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+56), (@bv+25), 2, 2, 1, 0, '2026-11-13 00:29:55.306836', '2026-11-13 00:29:55.306836' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+57), (@bv+26), 2, 2, 1, 0, '2026-11-13 00:29:55.307250', '2026-11-13 00:29:55.307250' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+58), (@bv+27), 2, 2, 1, 0, '2026-11-13 00:29:55.307692', '2026-11-13 00:29:55.307692' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+59), (@bv+28), 2, 2, 1, 0, '2026-11-13 00:29:55.308508', '2026-11-13 00:29:55.308508' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+60), (@bv+29), 2, 2, 1, 0, '2026-11-13 00:29:55.309436', '2026-11-13 00:29:55.309436' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+61), (@bv+30), 2, 2, 1, 0, '2026-11-13 00:29:55.310091', '2026-11-13 00:29:55.310091' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+62), (@bv+31), 2, 2, 1, 0, '2026-11-13 00:29:55.310672', '2026-11-13 00:29:55.310672' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+63), (@bv+32), 1, 3, 1, 0, '2026-11-13 00:29:57.394393', '2026-11-13 00:29:57.394393' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+64), (@bv+33), 1, 3, 1, 0, '2026-11-13 00:29:57.396763', '2026-11-13 00:29:57.396763' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+65), (@bv+34), 1, 3, 1, 0, '2026-11-13 00:29:57.398459', '2026-11-13 00:29:57.398459' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+66), (@bv+35), 1, 3, 1, 0, '2026-11-13 00:29:57.399607', '2026-11-13 00:29:57.399607' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+67), (@bv+36), 1, 3, 1, 0, '2026-11-13 00:29:57.400854', '2026-11-13 00:29:57.400854' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+68), (@bv+37), 1, 3, 1, 0, '2026-11-13 00:29:57.401855', '2026-11-13 00:29:57.401855' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+69), (@bv+38), 1, 3, 1, 0, '2026-11-13 00:29:57.402552', '2026-11-13 00:29:57.402552' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+70), (@bv+39), 1, 3, 1, 0, '2026-11-13 00:29:57.403186', '2026-11-13 00:29:57.403186' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+71), (@bv+32), 2, 1, 1, 0, '2026-11-13 00:29:57.403734', '2026-11-13 00:29:57.403734' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+72), (@bv+33), 2, 1, 1, 0, '2026-11-13 00:29:57.404466', '2026-11-13 00:29:57.404466' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+73), (@bv+34), 2, 1, 1, 0, '2026-11-13 00:29:57.405255', '2026-11-13 00:29:57.405255' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+74), (@bv+35), 2, 1, 1, 0, '2026-11-13 00:29:57.406596', '2026-11-13 00:29:57.406596' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+75), (@bv+36), 2, 1, 1, 0, '2026-11-13 00:29:57.407525', '2026-11-13 00:29:57.407525' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+76), (@bv+37), 2, 1, 1, 0, '2026-11-13 00:29:57.408460', '2026-11-13 00:29:57.408460' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+77), (@bv+38), 2, 1, 1, 0, '2026-11-13 00:29:57.409208', '2026-11-13 00:29:57.409208' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+78), (@bv+39), 2, 1, 1, 0, '2026-11-13 00:29:57.410078', '2026-11-13 00:29:57.410078' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+79), (@bv+40), 1, 2, 1, 0, '2026-11-13 00:29:59.580540', '2026-11-13 00:29:59.580540' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+80), (@bv+41), 1, 2, 1, 0, '2026-11-13 00:29:59.602106', '2026-11-13 00:29:59.602106' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+81), (@bv+42), 1, 2, 1, 0, '2026-11-13 00:29:59.603324', '2026-11-13 00:29:59.603324' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+82), (@bv+43), 1, 2, 1, 0, '2026-11-13 00:29:59.603962', '2026-11-13 00:29:59.603962' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+83), (@bv+44), 1, 2, 1, 0, '2026-11-13 00:29:59.604532', '2026-11-13 00:29:59.604532' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+84), (@bv+45), 1, 2, 1, 0, '2026-11-13 00:29:59.605140', '2026-11-13 00:29:59.605140' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+85), (@bv+46), 1, 2, 1, 0, '2026-11-13 00:29:59.605779', '2026-11-13 00:29:59.605779' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+86), (@bv+47), 1, 2, 1, 0, '2026-11-13 00:29:59.606692', '2026-11-13 00:29:59.606692' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+87), (@bv+40), 2, 2, 1, 0, '2026-11-13 00:29:59.609584', '2026-11-13 00:29:59.609584' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+88), (@bv+41), 2, 2, 1, 0, '2026-11-13 00:29:59.613440', '2026-11-13 00:29:59.613440' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+89), (@bv+42), 2, 2, 1, 0, '2026-11-13 00:29:59.615820', '2026-11-13 00:29:59.615820' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+90), (@bv+43), 2, 2, 1, 0, '2026-11-13 00:29:59.616916', '2026-11-13 00:29:59.616916' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+91), (@bv+44), 2, 2, 1, 0, '2026-11-13 00:29:59.617512', '2026-11-13 00:29:59.617512' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+92), (@bv+45), 2, 2, 1, 0, '2026-11-13 00:29:59.617955', '2026-11-13 00:29:59.617955' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+93), (@bv+46), 2, 2, 1, 0, '2026-11-13 00:29:59.618440', '2026-11-13 00:29:59.618440' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+94), (@bv+47), 2, 2, 1, 0, '2026-11-13 00:29:59.618899', '2026-11-13 00:29:59.618899' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+95), (@bv+48), 1, 2, 1, 0, '2026-11-13 00:30:01.908513', '2026-11-13 00:30:01.908513' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+96), (@bv+49), 1, 2, 1, 0, '2026-11-13 00:30:01.910506', '2026-11-13 00:30:01.910506' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+97), (@bv+50), 1, 2, 1, 0, '2026-11-13 00:30:01.911873', '2026-11-13 00:30:01.911873' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+98), (@bv+51), 1, 2, 1, 0, '2026-11-13 00:30:01.912989', '2026-11-13 00:30:01.912989' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+99), (@bv+52), 1, 2, 1, 0, '2026-11-13 00:30:01.914619', '2026-11-13 00:30:01.914619' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+100), (@bv+53), 1, 2, 1, 0, '2026-11-13 00:30:01.916072', '2026-11-13 00:30:01.916072' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+101), (@bv+54), 1, 2, 1, 0, '2026-11-13 00:30:01.917260', '2026-11-13 00:30:01.917260' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+102), (@bv+48), 2, 2, 1, 0, '2026-11-13 00:30:01.918451', '2026-11-13 00:30:01.918451' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+103), (@bv+49), 2, 2, 1, 0, '2026-11-13 00:30:01.919372', '2026-11-13 00:30:01.919372' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+104), (@bv+50), 2, 2, 1, 0, '2026-11-13 00:30:01.920184', '2026-11-13 00:30:01.920184' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+105), (@bv+51), 2, 2, 1, 0, '2026-11-13 00:30:01.920895', '2026-11-13 00:30:01.920895' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+106), (@bv+52), 2, 2, 1, 0, '2026-11-13 00:30:01.921327', '2026-11-13 00:30:01.921327' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+107), (@bv+53), 2, 2, 1, 0, '2026-11-13 00:30:01.921873', '2026-11-13 00:30:01.921873' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+108), (@bv+54), 2, 2, 1, 0, '2026-11-13 00:30:01.922396', '2026-11-13 00:30:01.922396' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+109), (@bv+55), 1, 2, 1, 0, '2026-11-13 00:30:03.971629', '2026-11-13 00:30:03.971629' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+110), (@bv+56), 1, 2, 1, 0, '2026-11-13 00:30:03.972055', '2026-11-13 00:30:03.972055' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+111), (@bv+57), 1, 2, 1, 0, '2026-11-13 00:30:03.972412', '2026-11-13 00:30:03.972412' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+112), (@bv+58), 1, 2, 1, 0, '2026-11-13 00:30:03.972784', '2026-11-13 00:30:03.972784' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+113), (@bv+59), 1, 2, 1, 0, '2026-11-13 00:30:03.973289', '2026-11-13 00:30:03.973289' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+114), (@bv+60), 1, 2, 1, 0, '2026-11-13 00:30:03.974074', '2026-11-13 00:30:03.974074' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+115), (@bv+61), 1, 2, 1, 0, '2026-11-13 00:30:03.974681', '2026-11-13 00:30:03.974681' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+116), (@bv+62), 1, 2, 1, 0, '2026-11-13 00:30:03.975372', '2026-11-13 00:30:03.975372' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+117), (@bv+55), 2, 1, 1, 0, '2026-11-13 00:30:03.976535', '2026-11-13 00:30:03.976535' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+118), (@bv+56), 2, 1, 1, 0, '2026-11-13 00:30:03.977521', '2026-11-13 00:30:03.977521' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+119), (@bv+57), 2, 1, 1, 0, '2026-11-13 00:30:03.978316', '2026-11-13 00:30:03.978316' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+120), (@bv+58), 2, 1, 1, 0, '2026-11-13 00:30:03.979849', '2026-11-13 00:30:03.979849' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+121), (@bv+59), 2, 1, 1, 0, '2026-11-13 00:30:03.980506', '2026-11-13 00:30:03.980506' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+122), (@bv+60), 2, 1, 1, 0, '2026-11-13 00:30:03.980942', '2026-11-13 00:30:03.980942' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+123), (@bv+61), 2, 1, 1, 0, '2026-11-13 00:30:03.981321', '2026-11-13 00:30:03.981321' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+124), (@bv+62), 2, 1, 1, 0, '2026-11-13 00:30:03.981642', '2026-11-13 00:30:03.981642' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+125), (@bv+63), 1, 2, 1, 0, '2026-11-13 00:30:06.294490', '2026-11-13 00:30:06.294490' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+126), (@bv+64), 1, 2, 1, 0, '2026-11-13 00:30:06.295574', '2026-11-13 00:30:06.295574' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+127), (@bv+65), 1, 2, 1, 0, '2026-11-13 00:30:06.296212', '2026-11-13 00:30:06.296212' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+128), (@bv+66), 1, 2, 1, 0, '2026-11-13 00:30:06.296933', '2026-11-13 00:30:06.296933' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+129), (@bv+67), 1, 2, 1, 0, '2026-11-13 00:30:06.297714', '2026-11-13 00:30:06.297714' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+130), (@bv+68), 1, 2, 1, 0, '2026-11-13 00:30:06.298457', '2026-11-13 00:30:06.298457' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+131), (@bv+69), 1, 2, 1, 0, '2026-11-13 00:30:06.299117', '2026-11-13 00:30:06.299117' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+132), (@bv+63), 2, 2, 1, 0, '2026-11-13 00:30:06.299718', '2026-11-13 00:30:06.299718' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+133), (@bv+64), 2, 2, 1, 0, '2026-11-13 00:30:06.300241', '2026-11-13 00:30:06.300241' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+134), (@bv+65), 2, 2, 1, 0, '2026-11-13 00:30:06.300713', '2026-11-13 00:30:06.300713' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+135), (@bv+66), 2, 2, 1, 0, '2026-11-13 00:30:06.301209', '2026-11-13 00:30:06.301209' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+136), (@bv+67), 2, 2, 1, 0, '2026-11-13 00:30:06.301795', '2026-11-13 00:30:06.301795' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+137), (@bv+68), 2, 2, 1, 0, '2026-11-13 00:30:06.302274', '2026-11-13 00:30:06.302274' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+138), (@bv+69), 2, 2, 1, 2, '2026-11-13 00:30:06.302763', '2026-11-14 20:28:23.796372' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+139), (@bv+70), 1, 2, 1, 0, '2026-11-13 00:30:08.635552', '2026-11-13 00:30:08.635552' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+140), (@bv+71), 1, 2, 1, 0, '2026-11-13 00:30:08.635974', '2026-11-13 00:30:08.635974' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+141), (@bv+72), 1, 2, 1, 0, '2026-11-13 00:30:08.636336', '2026-11-13 00:30:08.636336' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+142), (@bv+73), 1, 2, 1, 0, '2026-11-13 00:30:08.636793', '2026-11-13 00:30:08.636793' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+143), (@bv+74), 1, 2, 1, 0, '2026-11-13 00:30:08.637133', '2026-11-13 00:30:08.637133' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+144), (@bv+75), 1, 2, 1, 0, '2026-11-13 00:30:08.637595', '2026-11-13 00:30:08.637595' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+145), (@bv+76), 1, 2, 1, 0, '2026-11-13 00:30:08.637949', '2026-11-13 00:30:08.637949' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+146), (@bv+77), 1, 2, 1, 0, '2026-11-13 00:30:08.638254', '2026-11-13 00:30:08.638254' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+147), (@bv+70), 2, 2, 1, 0, '2026-11-13 00:30:08.638593', '2026-11-13 00:30:08.638593' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+148), (@bv+71), 2, 2, 1, 0, '2026-11-13 00:30:08.638939', '2026-11-13 00:30:08.638939' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+149), (@bv+72), 2, 2, 1, 0, '2026-11-13 00:30:08.639334', '2026-11-13 00:30:08.639334' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+150), (@bv+73), 2, 2, 1, 0, '2026-11-13 00:30:08.639633', '2026-11-13 00:30:08.639633' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+151), (@bv+74), 2, 2, 1, 0, '2026-11-13 00:30:08.639939', '2026-11-13 00:30:08.639939' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+152), (@bv+75), 2, 2, 1, 0, '2026-11-13 00:30:08.640454', '2026-11-13 00:30:08.640454' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+153), (@bv+76), 2, 2, 1, 0, '2026-11-13 00:30:08.641798', '2026-11-13 00:30:08.641798' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+154), (@bv+77), 2, 2, 1, 0, '2026-11-13 00:30:08.642593', '2026-11-13 00:30:08.642593' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+155), (@bv+78), 1, 2, 1, 0, '2026-11-13 00:30:11.580501', '2026-11-13 00:30:11.580501' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+156), (@bv+79), 1, 2, 1, 0, '2026-11-13 00:30:11.580903', '2026-11-13 00:30:11.580903' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+157), (@bv+80), 1, 2, 1, 0, '2026-11-13 00:30:11.581226', '2026-11-13 00:30:11.581226' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+158), (@bv+81), 1, 2, 1, 0, '2026-11-13 00:30:11.581561', '2026-11-13 00:30:11.581561' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+159), (@bv+82), 1, 2, 1, 0, '2026-11-13 00:30:11.581944', '2026-11-13 00:30:11.581944' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+160), (@bv+83), 1, 2, 1, 0, '2026-11-13 00:30:11.582382', '2026-11-13 00:30:11.582382' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+161), (@bv+84), 1, 2, 1, 0, '2026-11-13 00:30:11.582683', '2026-11-13 00:30:11.582683' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+162), (@bv+78), 2, 1, 1, 0, '2026-11-13 00:30:11.583042', '2026-11-13 00:30:11.583042' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+163), (@bv+79), 2, 1, 1, 0, '2026-11-13 00:30:11.583373', '2026-11-13 00:30:11.583373' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+164), (@bv+80), 2, 1, 1, 0, '2026-11-13 00:30:11.583696', '2026-11-13 00:30:11.583696' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+165), (@bv+81), 2, 1, 1, 0, '2026-11-13 00:30:11.584029', '2026-11-13 00:30:11.584029' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+166), (@bv+82), 2, 1, 1, 0, '2026-11-13 00:30:11.584344', '2026-11-13 00:30:11.584344' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+167), (@bv+83), 2, 1, 1, 0, '2026-11-13 00:30:11.584687', '2026-11-13 00:30:11.584687' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+168), (@bv+84), 2, 1, 1, 0, '2026-11-13 00:30:11.585136', '2026-11-13 00:30:11.585136' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+169), (@bv+85), 1, 2, 1, 0, '2026-11-13 00:30:13.586560', '2026-11-13 00:30:13.586560' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+170), (@bv+86), 1, 2, 1, 0, '2026-11-13 00:30:13.587068', '2026-11-13 00:30:13.587068' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+171), (@bv+87), 1, 2, 1, 0, '2026-11-13 00:30:13.587631', '2026-11-13 00:30:13.587631' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+172), (@bv+88), 1, 2, 1, 0, '2026-11-13 00:30:13.588160', '2026-11-13 00:30:13.588160' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+173), (@bv+89), 1, 2, 1, 0, '2026-11-13 00:30:13.588692', '2026-11-13 00:30:13.588692' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+174), (@bv+90), 1, 2, 1, 0, '2026-11-13 00:30:13.590580', '2026-11-13 00:30:13.590580' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+175), (@bv+91), 1, 2, 1, 0, '2026-11-13 00:30:13.591715', '2026-11-13 00:30:13.591715' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+176), (@bv+85), 2, 2, 1, 0, '2026-11-13 00:30:13.592921', '2026-11-13 00:30:13.592921' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+177), (@bv+86), 2, 2, 1, 0, '2026-11-13 00:30:13.593671', '2026-11-13 00:30:13.593671' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+178), (@bv+87), 2, 2, 1, 0, '2026-11-13 00:30:13.594207', '2026-11-13 00:30:13.594207' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+179), (@bv+88), 2, 2, 1, 0, '2026-11-13 00:30:13.594924', '2026-11-13 00:30:13.594924' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+180), (@bv+89), 2, 2, 1, 0, '2026-11-13 00:30:13.595423', '2026-11-13 00:30:13.595423' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+181), (@bv+90), 2, 2, 1, 0, '2026-11-13 00:30:13.595854', '2026-11-13 00:30:13.595854' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+182), (@bv+91), 2, 2, 1, 0, '2026-11-13 00:30:13.596305', '2026-11-13 00:30:13.596305' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+183), (@bv+92), 1, 1, 1, 0, '2026-11-13 00:30:18.430363', '2026-11-13 00:30:18.430363' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+184), (@bv+93), 1, 1, 1, 0, '2026-11-13 00:30:18.431192', '2026-11-13 00:30:18.431192' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+185), (@bv+94), 1, 1, 1, 0, '2026-11-13 00:30:18.431889', '2026-11-13 00:30:18.431889' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+186), (@bv+95), 1, 1, 1, 0, '2026-11-13 00:30:18.432580', '2026-11-13 00:30:18.432580' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+187), (@bv+96), 1, 1, 1, 0, '2026-11-13 00:30:18.433328', '2026-11-13 00:30:18.433328' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+188), (@bv+97), 1, 1, 1, 0, '2026-11-13 00:30:18.433932', '2026-11-13 00:30:18.433932' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+189), (@bv+98), 1, 1, 1, 0, '2026-11-13 00:30:18.434602', '2026-11-13 00:30:18.434602' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+190), (@bv+99), 1, 1, 1, 0, '2026-11-13 00:30:18.435160', '2026-11-13 00:30:18.435160' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+191), (@bv+92), 2, 1, 1, 0, '2026-11-13 00:30:18.435757', '2026-11-13 00:30:18.435757' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+192), (@bv+93), 2, 1, 1, 0, '2026-11-13 00:30:18.436390', '2026-11-13 00:30:18.436390' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+193), (@bv+94), 2, 1, 1, 0, '2026-11-13 00:30:18.437146', '2026-11-13 00:30:18.437146' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+194), (@bv+95), 2, 1, 1, 0, '2026-11-13 00:30:18.438892', '2026-11-13 00:30:18.438892' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+195), (@bv+96), 2, 1, 1, 0, '2026-11-13 00:30:18.440119', '2026-11-13 00:30:18.440119' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+196), (@bv+97), 2, 1, 1, 0, '2026-11-13 00:30:18.441094', '2026-11-13 00:30:18.441094' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+197), (@bv+98), 2, 1, 1, 0, '2026-11-13 00:30:18.441852', '2026-11-13 00:30:18.441852' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+198), (@bv+99), 2, 1, 1, 0, '2026-11-13 00:30:18.442590', '2026-11-13 00:30:18.442590' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+199), (@bv+100), 1, 1, 1, 0, '2026-11-13 00:30:20.825031', '2026-11-13 00:30:20.825031' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+200), (@bv+101), 1, 1, 1, 0, '2026-11-13 00:30:20.825439', '2026-11-13 00:30:20.825439' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+201), (@bv+102), 1, 1, 1, 0, '2026-11-13 00:30:20.831004', '2026-11-13 00:30:20.831004' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+202), (@bv+103), 1, 1, 1, 0, '2026-11-13 00:30:20.831869', '2026-11-13 00:30:20.831869' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+203), (@bv+104), 1, 1, 1, 0, '2026-11-13 00:30:20.832394', '2026-11-13 00:30:20.832394' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+204), (@bv+105), 1, 1, 1, 0, '2026-11-13 00:30:20.832936', '2026-11-13 00:30:20.832936' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+205), (@bv+106), 1, 1, 1, 0, '2026-11-13 00:30:20.833486', '2026-11-13 00:30:20.833486' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+206), (@bv+107), 1, 1, 1, 0, '2026-11-13 00:30:20.833872', '2026-11-13 00:30:20.833872' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+207), (@bv+100), 2, 1, 1, 0, '2026-11-13 00:30:20.834259', '2026-11-13 00:30:20.834259' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+208), (@bv+101), 2, 1, 1, 0, '2026-11-13 00:30:20.834629', '2026-11-13 00:30:20.834629' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+209), (@bv+102), 2, 1, 1, 0, '2026-11-13 00:30:20.834991', '2026-11-13 00:30:20.834991' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+210), (@bv+103), 2, 1, 1, 0, '2026-11-13 00:30:20.835544', '2026-11-13 00:30:20.835544' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+211), (@bv+104), 2, 1, 1, 0, '2026-11-13 00:30:20.836171', '2026-11-13 00:30:20.836171' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+212), (@bv+105), 2, 1, 1, 0, '2026-11-13 00:30:20.837537', '2026-11-13 00:30:20.837537' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+213), (@bv+106), 2, 1, 1, 0, '2026-11-13 00:30:20.838620', '2026-11-13 00:30:20.838620' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+214), (@bv+107), 2, 1, 1, 0, '2026-11-13 00:30:20.839553', '2026-11-13 00:30:20.839553' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+215), (@bv+108), 1, 1, 1, 0, '2026-11-13 00:30:23.030991', '2026-11-13 00:30:23.030991' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+216), (@bv+109), 1, 1, 1, 0, '2026-11-13 00:30:23.031365', '2026-11-13 00:30:23.031365' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+217), (@bv+110), 1, 1, 1, 0, '2026-11-13 00:30:23.031804', '2026-11-13 00:30:23.031804' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+218), (@bv+111), 1, 1, 1, 0, '2026-11-13 00:30:23.032171', '2026-11-13 00:30:23.032171' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+219), (@bv+112), 1, 1, 1, 0, '2026-11-13 00:30:23.032547', '2026-11-13 00:30:23.032547' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+220), (@bv+113), 1, 1, 1, 0, '2026-11-13 00:30:23.032892', '2026-11-13 00:30:23.032892' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+221), (@bv+114), 1, 1, 1, 0, '2026-11-13 00:30:23.033253', '2026-11-13 00:30:23.033253' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+222), (@bv+115), 1, 1, 1, 0, '2026-11-13 00:30:23.033605', '2026-11-13 00:30:23.033605' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+223), (@bv+108), 2, 1, 1, 0, '2026-11-13 00:30:23.033999', '2026-11-13 00:30:23.033999' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+224), (@bv+109), 2, 1, 1, 0, '2026-11-13 00:30:23.034365', '2026-11-13 00:30:23.034365' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+225), (@bv+110), 2, 1, 1, 0, '2026-11-13 00:30:23.034754', '2026-11-13 00:30:23.034754' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+226), (@bv+111), 2, 1, 1, 0, '2026-11-13 00:30:23.035048', '2026-11-13 00:30:23.035048' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+227), (@bv+112), 2, 1, 1, 0, '2026-11-13 00:30:23.035387', '2026-11-13 00:30:23.035387' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+228), (@bv+113), 2, 1, 1, 0, '2026-11-13 00:30:23.036625', '2026-11-13 00:30:23.036625' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+229), (@bv+114), 2, 1, 1, 0, '2026-11-13 00:30:23.037540', '2026-11-13 00:30:23.037540' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+230), (@bv+115), 2, 1, 1, 0, '2026-11-13 00:30:23.038712', '2026-11-13 00:30:23.038712' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+231), (@bv+116), 1, 1, 1, 0, '2026-11-13 00:30:25.123498', '2026-11-13 00:30:25.123498' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+232), (@bv+117), 1, 1, 1, 0, '2026-11-13 00:30:25.124083', '2026-11-13 00:30:25.124083' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+233), (@bv+118), 1, 1, 1, 0, '2026-11-13 00:30:25.124560', '2026-11-13 00:30:25.124560' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+234), (@bv+119), 1, 1, 1, 0, '2026-11-13 00:30:25.124984', '2026-11-13 00:30:25.124984' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+235), (@bv+120), 1, 1, 1, 0, '2026-11-13 00:30:25.125564', '2026-11-13 00:30:25.125564' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+236), (@bv+121), 1, 1, 1, 0, '2026-11-13 00:30:25.126086', '2026-11-13 00:30:25.126086' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+237), (@bv+122), 1, 1, 1, 0, '2026-11-13 00:30:25.126662', '2026-11-13 00:30:25.126662' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+238), (@bv+123), 1, 1, 1, 0, '2026-11-13 00:30:25.127054', '2026-11-13 00:30:25.127054' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+239), (@bv+116), 2, 1, 1, 0, '2026-11-13 00:30:25.127645', '2026-11-13 00:30:25.127645' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+240), (@bv+117), 2, 1, 1, 0, '2026-11-13 00:30:25.128282', '2026-11-13 00:30:25.128282' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+241), (@bv+118), 2, 1, 1, 0, '2026-11-13 00:30:25.128891', '2026-11-13 00:30:25.128891' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+242), (@bv+119), 2, 1, 1, 0, '2026-11-13 00:30:25.129372', '2026-11-13 00:30:25.129372' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+243), (@bv+120), 2, 1, 1, 0, '2026-11-13 00:30:25.129822', '2026-11-13 00:30:25.129822' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+244), (@bv+121), 2, 1, 1, 0, '2026-11-13 00:30:25.130414', '2026-11-13 00:30:25.130414' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+245), (@bv+122), 2, 1, 1, 0, '2026-11-13 00:30:25.131295', '2026-11-13 00:30:25.131295' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+246), (@bv+123), 2, 1, 1, 0, '2026-11-13 00:30:25.132054', '2026-11-13 00:30:25.132054' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+247), (@bv+124), 1, 2, 1, 0, '2026-11-13 00:30:27.266169', '2026-11-13 00:30:27.266169' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+248), (@bv+125), 1, 2, 1, 0, '2026-11-13 00:30:27.266610', '2026-11-13 00:30:27.266610' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+249), (@bv+126), 1, 2, 1, 0, '2026-11-13 00:30:27.266996', '2026-11-13 00:30:27.266996' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+250), (@bv+127), 1, 2, 1, 0, '2026-11-13 00:30:27.267611', '2026-11-13 00:30:27.267611' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+251), (@bv+128), 1, 2, 1, 0, '2026-11-13 00:30:27.268699', '2026-11-13 00:30:27.268699' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+252), (@bv+129), 1, 2, 1, 0, '2026-11-13 00:30:27.269196', '2026-11-13 00:30:27.269196' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+253), (@bv+130), 1, 2, 1, 0, '2026-11-13 00:30:27.269735', '2026-11-13 00:30:27.269735' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+254), (@bv+131), 1, 2, 1, 0, '2026-11-13 00:30:27.270320', '2026-11-13 00:30:27.270320' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+255), (@bv+124), 2, 1, 1, 0, '2026-11-13 00:30:27.271152', '2026-11-13 00:30:27.271152' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+256), (@bv+125), 2, 1, 1, 0, '2026-11-13 00:30:27.271682', '2026-11-13 00:30:27.271682' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+257), (@bv+126), 2, 1, 1, 0, '2026-11-13 00:30:27.272113', '2026-11-13 00:30:27.272113' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+258), (@bv+127), 2, 1, 1, 0, '2026-11-13 00:30:27.272467', '2026-11-13 00:30:27.272467' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+259), (@bv+128), 2, 1, 1, 0, '2026-11-13 00:30:27.272785', '2026-11-13 00:30:27.272785' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+260), (@bv+129), 2, 1, 1, 0, '2026-11-13 00:30:27.273098', '2026-11-13 00:30:27.273098' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+261), (@bv+130), 2, 1, 1, 0, '2026-11-13 00:30:27.273372', '2026-11-13 00:30:27.273372' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+262), (@bv+131), 2, 1, 1, 0, '2026-11-13 00:30:27.273638', '2026-11-13 00:30:27.273638' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+263), (@bv+132), 1, 1, 1, 0, '2026-11-13 00:30:29.286608', '2026-11-13 00:30:29.286608' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+264), (@bv+133), 1, 1, 1, 0, '2026-11-13 00:30:29.287223', '2026-11-13 00:30:29.287223' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+265), (@bv+134), 1, 1, 1, 0, '2026-11-13 00:30:29.287946', '2026-11-13 00:30:29.287946' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+266), (@bv+135), 1, 1, 1, 0, '2026-11-13 00:30:29.288520', '2026-11-13 00:30:29.288520' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+267), (@bv+136), 1, 1, 1, 0, '2026-11-13 00:30:29.288945', '2026-11-13 00:30:29.288945' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+268), (@bv+137), 1, 1, 1, 0, '2026-11-13 00:30:29.289321', '2026-11-13 00:30:29.289321' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+269), (@bv+138), 1, 1, 1, 0, '2026-11-13 00:30:29.289659', '2026-11-13 00:30:29.289659' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+270), (@bv+139), 1, 1, 1, 0, '2026-11-13 00:30:29.289988', '2026-11-13 00:30:29.289988' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+271), (@bv+132), 2, 2, 1, 0, '2026-11-13 00:30:29.290339', '2026-11-13 00:30:29.290339' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+272), (@bv+133), 2, 2, 1, 0, '2026-11-13 00:30:29.290678', '2026-11-13 00:30:29.290678' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+273), (@bv+134), 2, 2, 1, 0, '2026-11-13 00:30:29.291032', '2026-11-13 00:30:29.291032' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+274), (@bv+135), 2, 2, 1, 0, '2026-11-13 00:30:29.291313', '2026-11-13 00:30:29.291313' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+275), (@bv+136), 2, 2, 1, 0, '2026-11-13 00:30:29.291580', '2026-11-13 00:30:29.291580' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+276), (@bv+137), 2, 2, 1, 0, '2026-11-13 00:30:29.291884', '2026-11-13 00:30:29.291884' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+277), (@bv+138), 2, 2, 1, 0, '2026-11-13 00:30:29.292228', '2026-11-13 00:30:29.292228' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+278), (@bv+139), 2, 2, 1, 0, '2026-11-13 00:30:29.292568', '2026-11-13 00:30:29.292568' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+279), (@bv+140), 1, 2, 1, 0, '2026-11-13 00:30:31.364933', '2026-11-13 00:30:31.364933' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+280), (@bv+141), 1, 2, 1, 0, '2026-11-13 00:30:31.365614', '2026-11-13 00:30:31.365614' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+281), (@bv+142), 1, 2, 1, 0, '2026-11-13 00:30:31.366946', '2026-11-13 00:30:31.366946' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+282), (@bv+143), 1, 2, 1, 0, '2026-11-13 00:30:31.368125', '2026-11-13 00:30:31.368125' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+283), (@bv+144), 1, 2, 1, 0, '2026-11-13 00:30:31.368994', '2026-11-13 00:30:31.368994' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+284), (@bv+145), 1, 2, 1, 0, '2026-11-13 00:30:31.369567', '2026-11-13 00:30:31.369567' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+285), (@bv+146), 1, 2, 1, 0, '2026-11-13 00:30:31.370306', '2026-11-13 00:30:31.370306' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+286), (@bv+147), 1, 2, 1, 0, '2026-11-13 00:30:31.370846', '2026-11-13 00:30:31.370846' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+287), (@bv+140), 2, 2, 1, 0, '2026-11-13 00:30:31.371351', '2026-11-13 00:30:31.371351' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+288), (@bv+141), 2, 2, 1, 0, '2026-11-13 00:30:31.371867', '2026-11-13 00:30:31.371867' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+289), (@bv+142), 2, 2, 1, 0, '2026-11-13 00:30:31.372430', '2026-11-13 00:30:31.372430' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+290), (@bv+143), 2, 2, 1, 0, '2026-11-13 00:30:31.372863', '2026-11-13 00:30:31.372863' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+291), (@bv+144), 2, 2, 1, 0, '2026-11-13 00:30:31.373430', '2026-11-13 00:30:31.373430' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+292), (@bv+145), 2, 2, 1, 0, '2026-11-13 00:30:31.374083', '2026-11-13 00:30:31.374083' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+293), (@bv+146), 2, 2, 1, 0, '2026-11-13 00:30:31.374527', '2026-11-13 00:30:31.374527' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+294), (@bv+147), 2, 2, 1, 0, '2026-11-13 00:30:31.375308', '2026-11-13 00:30:31.375308' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+295), (@bv+148), 1, 2, 1, 0, '2026-11-13 00:30:35.267070', '2026-11-13 00:30:35.267070' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+296), (@bv+149), 1, 2, 1, 0, '2026-11-13 00:30:35.268430', '2026-11-13 00:30:35.268430' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+297), (@bv+150), 1, 2, 1, 0, '2026-11-13 00:30:35.269054', '2026-11-13 00:30:35.269054' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+298), (@bv+151), 1, 2, 1, 0, '2026-11-13 00:30:35.269475', '2026-11-13 00:30:35.269475' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+299), (@bv+152), 1, 2, 1, 0, '2026-11-13 00:30:35.269811', '2026-11-13 00:30:35.269811' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+300), (@bv+153), 1, 2, 1, 0, '2026-11-13 00:30:35.271652', '2026-11-13 00:30:35.271652' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+301), (@bv+154), 1, 2, 1, 0, '2026-11-13 00:30:35.272116', '2026-11-13 00:30:35.272116' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+302), (@bv+148), 2, 1, 1, 0, '2026-11-13 00:30:35.272528', '2026-11-13 00:30:35.272528' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+303), (@bv+149), 2, 1, 1, 0, '2026-11-13 00:30:35.272878', '2026-11-13 00:30:35.272878' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+304), (@bv+150), 2, 1, 1, 0, '2026-11-13 00:30:35.273274', '2026-11-13 00:30:35.273274' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+305), (@bv+151), 2, 1, 1, 0, '2026-11-13 00:30:35.273713', '2026-11-13 00:30:35.273713' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+306), (@bv+152), 2, 1, 1, 0, '2026-11-13 00:30:35.274010', '2026-11-13 00:30:35.274010' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+307), (@bv+153), 2, 1, 1, 0, '2026-11-13 00:30:35.274340', '2026-11-13 00:30:35.274340' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+308), (@bv+154), 2, 1, 1, 0, '2026-11-13 00:30:35.274706', '2026-11-13 00:30:35.274706' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+309), (@bv+155), 1, 0, 1, 1, '2026-11-13 00:30:37.517400', '2026-11-14 20:18:23.993310' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+310), (@bv+156), 1, 0, 1, 1, '2026-11-13 00:30:37.518211', '2026-11-14 20:21:06.137567' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+311), (@bv+157), 1, 1, 1, 0, '2026-11-13 00:30:37.519465', '2026-11-13 00:30:37.519465' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+312), (@bv+158), 1, 1, 1, 0, '2026-11-13 00:30:37.520019', '2026-11-13 00:30:37.520019' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+313), (@bv+159), 1, 1, 1, 0, '2026-11-13 00:30:37.520692', '2026-11-13 00:30:37.520692' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+314), (@bv+160), 1, 1, 1, 0, '2026-11-13 00:30:37.521295', '2026-11-13 00:30:37.521295' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+315), (@bv+161), 1, 1, 1, 0, '2026-11-13 00:30:37.522136', '2026-11-13 00:30:37.522136' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+316), (@bv+155), 2, 1, 1, 0, '2026-11-13 00:30:37.522679', '2026-11-13 00:30:37.522679' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+317), (@bv+156), 2, 1, 1, 0, '2026-11-13 00:30:37.523257', '2026-11-13 00:30:37.523257' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+318), (@bv+157), 2, 1, 1, 0, '2026-11-13 00:30:37.523834', '2026-11-13 00:30:37.523834' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+319), (@bv+158), 2, 1, 1, 0, '2026-11-13 00:30:37.524340', '2026-11-13 00:30:37.524340' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+320), (@bv+159), 2, 1, 1, 0, '2026-11-13 00:30:37.524739', '2026-11-13 00:30:37.524739' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+321), (@bv+160), 2, 1, 1, 0, '2026-11-13 00:30:37.525174', '2026-11-13 00:30:37.525174' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_variant_branch_stock` (`id`, `variant_id`, `location_id`, `remaining_unsold`, `sale_enabled`, `version`, `created_at`, `updated_at`)
SELECT (@bs+322), (@bv+161), 2, 1, 1, 0, '2026-11-13 00:30:37.525543', '2026-11-13 00:30:37.525543' FROM DUAL WHERE @run_catalogue;

-- Images
INSERT INTO `catalogue_product_images` (`id`, `product_id`, `media_object_id`, `alt_text`, `display_position`, `version`)
SELECT (@bi+1), (@bp+1), '88a46e90-2a00-4c5d-a70a-8c1b3dd61963', 'Nhẫn bạc vân búa bản nhọn trong hộp', 0, 0 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_product_images` (`id`, `product_id`, `media_object_id`, `alt_text`, `display_position`, `version`)
SELECT (@bi+2), (@bp+1), '9c6e6aa3-38bb-494d-85b7-7cb64fe6f76f', 'Nhẫn bạc vân búa nhìn nghiêng', 1, 0 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_product_images` (`id`, `product_id`, `media_object_id`, `alt_text`, `display_position`, `version`)
SELECT (@bi+3), (@bp+2), '4d950a38-c94e-4e44-af30-e8c272898fb3', 'Nhẫn bạc hở hình mèo và đuôi xoắn ốc', 0, 0 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_product_images` (`id`, `product_id`, `media_object_id`, `alt_text`, `display_position`, `version`)
SELECT (@bi+4), (@bp+3), '1b83213c-2617-44e2-858f-126f42177005', 'Nhẫn bạc trăng khuyết đính thạch anh tím', 0, 0 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_product_images` (`id`, `product_id`, `media_object_id`, `alt_text`, `display_position`, `version`)
SELECT (@bi+5), (@bp+4), 'a90ba336-f29f-4e28-a0d3-60f9f8f83e78', 'Nhẫn bạc sóng nước đính peridot xanh lá', 0, 0 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_product_images` (`id`, `product_id`, `media_object_id`, `alt_text`, `display_position`, `version`)
SELECT (@bi+6), (@bp+5), '8391d99d-589e-4d13-ba18-e2cd85a9244b', 'Nhẫn bạc hình con mắt đính garnet', 0, 0 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_product_images` (`id`, `product_id`, `media_object_id`, `alt_text`, `display_position`, `version`)
SELECT (@bi+7), (@bp+6), '88bc7456-ce95-4f6d-8eea-86791efc7120', 'Nhẫn bạc hai vòng xoáy đính citrine vàng', 0, 0 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_product_images` (`id`, `product_id`, `media_object_id`, `alt_text`, `display_position`, `version`)
SELECT (@bi+8), (@bp+7), 'df38bb58-4723-4b28-8373-297eaac8fa30', 'Nhẫn bạc đính garnet đỏ tròn', 0, 0 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_product_images` (`id`, `product_id`, `media_object_id`, `alt_text`, `display_position`, `version`)
SELECT (@bi+9), (@bp+8), '26dc16ef-6378-467f-8f9c-5c8e323f4d0d', 'Nhẫn bạc đính hổ phách cam', 0, 0 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_product_images` (`id`, `product_id`, `media_object_id`, `alt_text`, `display_position`, `version`)
SELECT (@bi+10), (@bp+9), '84690e58-5f17-4572-b6c6-e1a87ab10dbc', 'Nhẫn bạc cành lá đính citrine', 0, 0 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_product_images` (`id`, `product_id`, `media_object_id`, `alt_text`, `display_position`, `version`)
SELECT (@bi+11), (@bp+10), '59039fdd-df4a-42cf-9990-3ca68d22b17b', 'Nhẫn bạc đính peridot oval', 0, 0 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_product_images` (`id`, `product_id`, `media_object_id`, `alt_text`, `display_position`, `version`)
SELECT (@bi+12), (@bp+11), '3e5c1f53-8309-4e24-b9be-3872cd8c03aa', 'Nhẫn mạ vàng hoa văn đính thạch anh tím', 0, 0 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_product_images` (`id`, `product_id`, `media_object_id`, `alt_text`, `display_position`, `version`)
SELECT (@bi+13), (@bp+12), '2331db2e-0239-4e78-8f29-ddba3a61596b', 'Nhẫn bạc đá xanh ngọc điểm lá bạc', 0, 0 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_product_images` (`id`, `product_id`, `media_object_id`, `alt_text`, `display_position`, `version`)
SELECT (@bi+14), (@bp+13), 'da58b598-476a-42a5-88da-cb8595ed9452', 'Nhẫn bạc mã não cây mặt chữ nhật', 0, 0 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_product_images` (`id`, `product_id`, `media_object_id`, `alt_text`, `display_position`, `version`)
SELECT (@bi+15), (@bp+13), '5056511b-6a97-436b-b8a3-75fb0ba5623c', 'Nhẫn bạc mã não cây mặt chữ nhật nhìn chéo', 1, 0 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_product_images` (`id`, `product_id`, `media_object_id`, `alt_text`, `display_position`, `version`)
SELECT (@bi+16), (@bp+14), '2b3bc822-f55d-493e-86f0-1e19263b3ad6', 'Nhẫn bạc mã não cây mặt vuông', 0, 0 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_product_images` (`id`, `product_id`, `media_object_id`, `alt_text`, `display_position`, `version`)
SELECT (@bi+17), (@bp+15), '1e14017b-5e7a-426f-ad43-f43b59c97b8f', 'Nhẫn bạc mã não cây oval đặt ngang', 0, 0 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_product_images` (`id`, `product_id`, `media_object_id`, `alt_text`, `display_position`, `version`)
SELECT (@bi+18), (@bp+16), '381ee35b-acbb-4705-9eea-83a3fead5bca', 'Nhẫn bạc mã não cây oval đặt dọc', 0, 0 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_product_images` (`id`, `product_id`, `media_object_id`, `alt_text`, `display_position`, `version`)
SELECT (@bi+19), (@bp+17), 'ddc93f57-3e47-4778-aef3-ab9a6be038ae', 'Nhẫn bạc mã não rêu hình trái tim', 0, 0 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_product_images` (`id`, `product_id`, `media_object_id`, `alt_text`, `display_position`, `version`)
SELECT (@bi+20), (@bp+18), '2f0167bc-480b-4693-bbbf-acf7b60c7ef4', 'Nhẫn bạc mã não rêu hình giọt nước', 0, 0 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_product_images` (`id`, `product_id`, `media_object_id`, `alt_text`, `display_position`, `version`)
SELECT (@bi+21), (@bp+19), 'a419904e-f035-4623-89d2-e179e51de87d', 'Nhẫn bạc ngọc xanh quấn dây bạc xoắn', 0, 0 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_product_images` (`id`, `product_id`, `media_object_id`, `alt_text`, `display_position`, `version`)
SELECT (@bi+22), (@bp+20), 'ae57c551-032d-4f91-a808-2f2fed7721fd', 'Nhẫn bạc thạch anh thô trong hộp', 0, 0 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_product_images` (`id`, `product_id`, `media_object_id`, `alt_text`, `display_position`, `version`)
SELECT (@bi+23), (@bp+20), 'd6282338-a736-4c17-acf8-b5e2b7d0715e', 'Nhẫn bạc thạch anh thô nhìn nghiêng', 1, 0 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_product_images` (`id`, `product_id`, `media_object_id`, `alt_text`, `display_position`, `version`)
SELECT (@bi+24), (@bp+21), '0d5cf1c7-615f-4b52-99fd-2c5a2802261b', 'Nhẫn bạc hở hai thanh tourmaline xanh', 0, 0 FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_media_references` (`id`, `media_object_id`, `owner_module`, `owner_record_ref`, `linked_at`, `released_at`)
SELECT (@bm+1), '9c6e6aa3-38bb-494d-85b7-7cb64fe6f76f', 'productmanagement', CONCAT('product:', (@bp+1)), '2026-11-12 17:29:48.028525', NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_media_references` (`id`, `media_object_id`, `owner_module`, `owner_record_ref`, `linked_at`, `released_at`)
SELECT (@bm+2), '88a46e90-2a00-4c5d-a70a-8c1b3dd61963', 'productmanagement', CONCAT('product:', (@bp+1)), '2026-11-12 17:29:48.028525', NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_media_references` (`id`, `media_object_id`, `owner_module`, `owner_record_ref`, `linked_at`, `released_at`)
SELECT (@bm+3), '4d950a38-c94e-4e44-af30-e8c272898fb3', 'productmanagement', CONCAT('product:', (@bp+2)), '2026-11-12 17:29:50.918974', NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_media_references` (`id`, `media_object_id`, `owner_module`, `owner_record_ref`, `linked_at`, `released_at`)
SELECT (@bm+4), '1b83213c-2617-44e2-858f-126f42177005', 'productmanagement', CONCAT('product:', (@bp+3)), '2026-11-12 17:29:53.079608', NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_media_references` (`id`, `media_object_id`, `owner_module`, `owner_record_ref`, `linked_at`, `released_at`)
SELECT (@bm+5), 'a90ba336-f29f-4e28-a0d3-60f9f8f83e78', 'productmanagement', CONCAT('product:', (@bp+4)), '2026-11-12 17:29:55.288068', NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_media_references` (`id`, `media_object_id`, `owner_module`, `owner_record_ref`, `linked_at`, `released_at`)
SELECT (@bm+6), '8391d99d-589e-4d13-ba18-e2cd85a9244b', 'productmanagement', CONCAT('product:', (@bp+5)), '2026-11-12 17:29:57.385635', NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_media_references` (`id`, `media_object_id`, `owner_module`, `owner_record_ref`, `linked_at`, `released_at`)
SELECT (@bm+7), '88bc7456-ce95-4f6d-8eea-86791efc7120', 'productmanagement', CONCAT('product:', (@bp+6)), '2026-11-12 17:29:59.570348', NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_media_references` (`id`, `media_object_id`, `owner_module`, `owner_record_ref`, `linked_at`, `released_at`)
SELECT (@bm+8), 'df38bb58-4723-4b28-8373-297eaac8fa30', 'productmanagement', CONCAT('product:', (@bp+7)), '2026-11-12 17:30:01.894597', NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_media_references` (`id`, `media_object_id`, `owner_module`, `owner_record_ref`, `linked_at`, `released_at`)
SELECT (@bm+9), '26dc16ef-6378-467f-8f9c-5c8e323f4d0d', 'productmanagement', CONCAT('product:', (@bp+8)), '2026-11-12 17:30:03.964974', NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_media_references` (`id`, `media_object_id`, `owner_module`, `owner_record_ref`, `linked_at`, `released_at`)
SELECT (@bm+10), '84690e58-5f17-4572-b6c6-e1a87ab10dbc', 'productmanagement', CONCAT('product:', (@bp+9)), '2026-11-12 17:30:06.283666', NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_media_references` (`id`, `media_object_id`, `owner_module`, `owner_record_ref`, `linked_at`, `released_at`)
SELECT (@bm+11), '59039fdd-df4a-42cf-9990-3ca68d22b17b', 'productmanagement', CONCAT('product:', (@bp+10)), '2026-11-12 17:30:08.625428', NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_media_references` (`id`, `media_object_id`, `owner_module`, `owner_record_ref`, `linked_at`, `released_at`)
SELECT (@bm+12), '3e5c1f53-8309-4e24-b9be-3872cd8c03aa', 'productmanagement', CONCAT('product:', (@bp+11)), '2026-11-12 17:30:11.573822', NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_media_references` (`id`, `media_object_id`, `owner_module`, `owner_record_ref`, `linked_at`, `released_at`)
SELECT (@bm+13), '2331db2e-0239-4e78-8f29-ddba3a61596b', 'productmanagement', CONCAT('product:', (@bp+12)), '2026-11-12 17:30:13.578467', NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_media_references` (`id`, `media_object_id`, `owner_module`, `owner_record_ref`, `linked_at`, `released_at`)
SELECT (@bm+14), '5056511b-6a97-436b-b8a3-75fb0ba5623c', 'productmanagement', CONCAT('product:', (@bp+13)), '2026-11-12 17:30:18.416782', NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_media_references` (`id`, `media_object_id`, `owner_module`, `owner_record_ref`, `linked_at`, `released_at`)
SELECT (@bm+15), 'da58b598-476a-42a5-88da-cb8595ed9452', 'productmanagement', CONCAT('product:', (@bp+13)), '2026-11-12 17:30:18.416782', NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_media_references` (`id`, `media_object_id`, `owner_module`, `owner_record_ref`, `linked_at`, `released_at`)
SELECT (@bm+16), '2b3bc822-f55d-493e-86f0-1e19263b3ad6', 'productmanagement', CONCAT('product:', (@bp+14)), '2026-11-12 17:30:20.815669', NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_media_references` (`id`, `media_object_id`, `owner_module`, `owner_record_ref`, `linked_at`, `released_at`)
SELECT (@bm+17), '1e14017b-5e7a-426f-ad43-f43b59c97b8f', 'productmanagement', CONCAT('product:', (@bp+15)), '2026-11-12 17:30:23.022787', NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_media_references` (`id`, `media_object_id`, `owner_module`, `owner_record_ref`, `linked_at`, `released_at`)
SELECT (@bm+18), '381ee35b-acbb-4705-9eea-83a3fead5bca', 'productmanagement', CONCAT('product:', (@bp+16)), '2026-11-12 17:30:25.113208', NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_media_references` (`id`, `media_object_id`, `owner_module`, `owner_record_ref`, `linked_at`, `released_at`)
SELECT (@bm+19), 'ddc93f57-3e47-4778-aef3-ab9a6be038ae', 'productmanagement', CONCAT('product:', (@bp+17)), '2026-11-12 17:30:27.257750', NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_media_references` (`id`, `media_object_id`, `owner_module`, `owner_record_ref`, `linked_at`, `released_at`)
SELECT (@bm+20), '2f0167bc-480b-4693-bbbf-acf7b60c7ef4', 'productmanagement', CONCAT('product:', (@bp+18)), '2026-11-12 17:30:29.279608', NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_media_references` (`id`, `media_object_id`, `owner_module`, `owner_record_ref`, `linked_at`, `released_at`)
SELECT (@bm+21), 'a419904e-f035-4623-89d2-e179e51de87d', 'productmanagement', CONCAT('product:', (@bp+19)), '2026-11-12 17:30:31.355178', NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_media_references` (`id`, `media_object_id`, `owner_module`, `owner_record_ref`, `linked_at`, `released_at`)
SELECT (@bm+22), 'd6282338-a736-4c17-acf8-b5e2b7d0715e', 'productmanagement', CONCAT('product:', (@bp+20)), '2026-11-12 17:30:35.260070', NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_media_references` (`id`, `media_object_id`, `owner_module`, `owner_record_ref`, `linked_at`, `released_at`)
SELECT (@bm+23), 'ae57c551-032d-4f91-a808-2f2fed7721fd', 'productmanagement', CONCAT('product:', (@bp+20)), '2026-11-12 17:30:35.260070', NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_media_references` (`id`, `media_object_id`, `owner_module`, `owner_record_ref`, `linked_at`, `released_at`)
SELECT (@bm+24), '0d5cf1c7-615f-4b52-99fd-2c5a2802261b', 'productmanagement', CONCAT('product:', (@bp+21)), '2026-11-12 17:30:37.508305', NULL FROM DUAL WHERE @run_catalogue;

-- Reviews (approved) with their snapshot ids renumbered
INSERT INTO `catalogue_product_reviews` (`id`, `product_id`, `revision`, `content_version`, `state`, `effective`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `rejection_reason`, `snapshot`)
SELECT (@br+1), (@bp+1), 1, 0, 'APPROVED', 1, 'system:catalogue-seed', '2026-11-12 17:29:48.368265', 'system:catalogue-seed', '2026-11-12 17:29:48.515323', NULL, JSON_SET('{"name":"Nhẫn bạc vân búa bản nhọn","images":[{"altText":"Nhẫn bạc vân búa bản nhọn trong hộp","mediaId":"88a46e90-2a00-4c5d-a70a-8c1b3dd61963","position":0},{"altText":"Nhẫn bạc vân búa nhìn nghiêng","mediaId":"9c6e6aa3-38bb-494d-85b7-7cb64fe6f76f","position":1}],"variants":[{"sku":"SP000001-S07","size":7,"variantId":"1","proposalId":"1","proposedPrice":"390000","proposalRevision":1,"quantityAtSubmission":"5"},{"sku":"SP000001-S08","size":8,"variantId":"2","proposalId":"2","proposedPrice":"390000","proposalRevision":1,"quantityAtSubmission":"5"},{"sku":"SP000001-S09","size":9,"variantId":"3","proposalId":"3","proposedPrice":"390000","proposalRevision":1,"quantityAtSubmission":"5"},{"sku":"SP000001-S10","size":10,"variantId":"4","proposalId":"4","proposedPrice":"390000","proposalRevision":1,"quantityAtSubmission":"5"},{"sku":"SP000001-S11","size":11,"variantId":"5","proposalId":"5","proposedPrice":"390000","proposalRevision":1,"quantityAtSubmission":"5"},{"sku":"SP000001-S12","size":12,"variantId":"6","proposalId":"6","proposedPrice":"390000","proposalRevision":1,"quantityAtSubmission":"5"},{"sku":"SP000001-S13","size":13,"variantId":"7","proposalId":"7","proposedPrice":"390000","proposalRevision":1,"quantityAtSubmission":"5"},{"sku":"SP000001-S14","size":14,"variantId":"8","proposalId":"8","proposedPrice":"390000","proposalRevision":1,"quantityAtSubmission":"5"}],"productId":"1","categoryId":"1","description":"Nhẫn bạc 925 bản dẹt, mặt trên vuốt nhọn nhẹ và đánh vân búa thủ công nên bắt sáng lấp lánh. Đeo hằng ngày hoặc đeo chồng cùng nhẫn khác đều hợp.","categoryName":"Nhẫn bạc trơn","contentVersion":0}', '$.variants[0].variantId', CAST((@bv+1) AS CHAR), '$.variants[0].proposalId', CAST((@bpp+1) AS CHAR), '$.variants[0].sku', CONCAT('SP', LPAD((@bp+1), 6, '0'), '-S', LPAD(7, 2, '0')), '$.variants[1].variantId', CAST((@bv+2) AS CHAR), '$.variants[1].proposalId', CAST((@bpp+2) AS CHAR), '$.variants[1].sku', CONCAT('SP', LPAD((@bp+1), 6, '0'), '-S', LPAD(8, 2, '0')), '$.variants[2].variantId', CAST((@bv+3) AS CHAR), '$.variants[2].proposalId', CAST((@bpp+3) AS CHAR), '$.variants[2].sku', CONCAT('SP', LPAD((@bp+1), 6, '0'), '-S', LPAD(9, 2, '0')), '$.variants[3].variantId', CAST((@bv+4) AS CHAR), '$.variants[3].proposalId', CAST((@bpp+4) AS CHAR), '$.variants[3].sku', CONCAT('SP', LPAD((@bp+1), 6, '0'), '-S', LPAD(10, 2, '0')), '$.variants[4].variantId', CAST((@bv+5) AS CHAR), '$.variants[4].proposalId', CAST((@bpp+5) AS CHAR), '$.variants[4].sku', CONCAT('SP', LPAD((@bp+1), 6, '0'), '-S', LPAD(11, 2, '0')), '$.variants[5].variantId', CAST((@bv+6) AS CHAR), '$.variants[5].proposalId', CAST((@bpp+6) AS CHAR), '$.variants[5].sku', CONCAT('SP', LPAD((@bp+1), 6, '0'), '-S', LPAD(12, 2, '0')), '$.variants[6].variantId', CAST((@bv+7) AS CHAR), '$.variants[6].proposalId', CAST((@bpp+7) AS CHAR), '$.variants[6].sku', CONCAT('SP', LPAD((@bp+1), 6, '0'), '-S', LPAD(13, 2, '0')), '$.variants[7].variantId', CAST((@bv+8) AS CHAR), '$.variants[7].proposalId', CAST((@bpp+8) AS CHAR), '$.variants[7].sku', CONCAT('SP', LPAD((@bp+1), 6, '0'), '-S', LPAD(14, 2, '0'))) FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_product_reviews` (`id`, `product_id`, `revision`, `content_version`, `state`, `effective`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `rejection_reason`, `snapshot`)
SELECT (@br+2), (@bp+2), 1, 0, 'APPROVED', 1, 'system:catalogue-seed', '2026-11-12 17:29:51.004674', 'system:catalogue-seed', '2026-11-12 17:29:51.055328', NULL, JSON_SET('{"name":"Nhẫn bạc mèo xoắn","images":[{"altText":"Nhẫn bạc hở hình mèo và đuôi xoắn ốc","mediaId":"4d950a38-c94e-4e44-af30-e8c272898fb3","position":0}],"variants":[{"sku":"SP000002-S06","size":6,"variantId":"9","proposalId":"9","proposedPrice":"420000","proposalRevision":1,"quantityAtSubmission":"3"},{"sku":"SP000002-S07","size":7,"variantId":"10","proposalId":"10","proposedPrice":"420000","proposalRevision":1,"quantityAtSubmission":"3"},{"sku":"SP000002-S08","size":8,"variantId":"11","proposalId":"11","proposedPrice":"420000","proposalRevision":1,"quantityAtSubmission":"3"},{"sku":"SP000002-S09","size":9,"variantId":"12","proposalId":"12","proposedPrice":"420000","proposalRevision":1,"quantityAtSubmission":"3"},{"sku":"SP000002-S10","size":10,"variantId":"13","proposalId":"13","proposedPrice":"420000","proposalRevision":1,"quantityAtSubmission":"3"},{"sku":"SP000002-S11","size":11,"variantId":"14","proposalId":"14","proposedPrice":"420000","proposalRevision":1,"quantityAtSubmission":"3"},{"sku":"SP000002-S12","size":12,"variantId":"15","proposalId":"15","proposedPrice":"420000","proposalRevision":1,"quantityAtSubmission":"3"}],"productId":"2","categoryId":"1","description":"Nhẫn hở bằng bạc 925: một đầu là dáng mèo ngồi cắt từ tấm bạc, đầu kia cuộn xoắn ốc như đuôi mèo. Kiểu hở nên nới hoặc khép nhẹ được cho vừa tay.","categoryName":"Nhẫn bạc trơn","contentVersion":0}', '$.variants[0].variantId', CAST((@bv+9) AS CHAR), '$.variants[0].proposalId', CAST((@bpp+9) AS CHAR), '$.variants[0].sku', CONCAT('SP', LPAD((@bp+2), 6, '0'), '-S', LPAD(6, 2, '0')), '$.variants[1].variantId', CAST((@bv+10) AS CHAR), '$.variants[1].proposalId', CAST((@bpp+10) AS CHAR), '$.variants[1].sku', CONCAT('SP', LPAD((@bp+2), 6, '0'), '-S', LPAD(7, 2, '0')), '$.variants[2].variantId', CAST((@bv+11) AS CHAR), '$.variants[2].proposalId', CAST((@bpp+11) AS CHAR), '$.variants[2].sku', CONCAT('SP', LPAD((@bp+2), 6, '0'), '-S', LPAD(8, 2, '0')), '$.variants[3].variantId', CAST((@bv+12) AS CHAR), '$.variants[3].proposalId', CAST((@bpp+12) AS CHAR), '$.variants[3].sku', CONCAT('SP', LPAD((@bp+2), 6, '0'), '-S', LPAD(9, 2, '0')), '$.variants[4].variantId', CAST((@bv+13) AS CHAR), '$.variants[4].proposalId', CAST((@bpp+13) AS CHAR), '$.variants[4].sku', CONCAT('SP', LPAD((@bp+2), 6, '0'), '-S', LPAD(10, 2, '0')), '$.variants[5].variantId', CAST((@bv+14) AS CHAR), '$.variants[5].proposalId', CAST((@bpp+14) AS CHAR), '$.variants[5].sku', CONCAT('SP', LPAD((@bp+2), 6, '0'), '-S', LPAD(11, 2, '0')), '$.variants[6].variantId', CAST((@bv+15) AS CHAR), '$.variants[6].proposalId', CAST((@bpp+15) AS CHAR), '$.variants[6].sku', CONCAT('SP', LPAD((@bp+2), 6, '0'), '-S', LPAD(12, 2, '0'))) FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_product_reviews` (`id`, `product_id`, `revision`, `content_version`, `state`, `effective`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `rejection_reason`, `snapshot`)
SELECT (@br+3), (@bp+3), 1, 0, 'APPROVED', 1, 'system:catalogue-seed', '2026-11-12 17:29:53.155806', 'system:catalogue-seed', '2026-11-12 17:29:53.194797', NULL, JSON_SET('{"name":"Nhẫn bạc trăng khuyết thạch anh tím","images":[{"altText":"Nhẫn bạc trăng khuyết đính thạch anh tím","mediaId":"1b83213c-2617-44e2-858f-126f42177005","position":0}],"variants":[{"sku":"SP000003-S06","size":6,"variantId":"16","proposalId":"16","proposedPrice":"520000","proposalRevision":1,"quantityAtSubmission":"4"},{"sku":"SP000003-S07","size":7,"variantId":"17","proposalId":"17","proposedPrice":"520000","proposalRevision":1,"quantityAtSubmission":"4"},{"sku":"SP000003-S08","size":8,"variantId":"18","proposalId":"18","proposedPrice":"520000","proposalRevision":1,"quantityAtSubmission":"4"},{"sku":"SP000003-S09","size":9,"variantId":"19","proposalId":"19","proposedPrice":"520000","proposalRevision":1,"quantityAtSubmission":"4"},{"sku":"SP000003-S10","size":10,"variantId":"20","proposalId":"20","proposedPrice":"520000","proposalRevision":1,"quantityAtSubmission":"4"},{"sku":"SP000003-S11","size":11,"variantId":"21","proposalId":"21","proposedPrice":"520000","proposalRevision":1,"quantityAtSubmission":"4"},{"sku":"SP000003-S12","size":12,"variantId":"22","proposalId":"22","proposedPrice":"520000","proposalRevision":1,"quantityAtSubmission":"4"},{"sku":"SP000003-S13","size":13,"variantId":"23","proposalId":"23","proposedPrice":"520000","proposalRevision":1,"quantityAtSubmission":"4"}],"productId":"3","categoryId":"2","description":"Vầng trăng khuyết bạc ôm một viên thạch anh tím vuông nhỏ, đai nhẫn mảnh. Thiết kế tối giản, hợp đeo ngón áp út hoặc ngón út.","categoryName":"Nhẫn đá quý","contentVersion":0}', '$.variants[0].variantId', CAST((@bv+16) AS CHAR), '$.variants[0].proposalId', CAST((@bpp+16) AS CHAR), '$.variants[0].sku', CONCAT('SP', LPAD((@bp+3), 6, '0'), '-S', LPAD(6, 2, '0')), '$.variants[1].variantId', CAST((@bv+17) AS CHAR), '$.variants[1].proposalId', CAST((@bpp+17) AS CHAR), '$.variants[1].sku', CONCAT('SP', LPAD((@bp+3), 6, '0'), '-S', LPAD(7, 2, '0')), '$.variants[2].variantId', CAST((@bv+18) AS CHAR), '$.variants[2].proposalId', CAST((@bpp+18) AS CHAR), '$.variants[2].sku', CONCAT('SP', LPAD((@bp+3), 6, '0'), '-S', LPAD(8, 2, '0')), '$.variants[3].variantId', CAST((@bv+19) AS CHAR), '$.variants[3].proposalId', CAST((@bpp+19) AS CHAR), '$.variants[3].sku', CONCAT('SP', LPAD((@bp+3), 6, '0'), '-S', LPAD(9, 2, '0')), '$.variants[4].variantId', CAST((@bv+20) AS CHAR), '$.variants[4].proposalId', CAST((@bpp+20) AS CHAR), '$.variants[4].sku', CONCAT('SP', LPAD((@bp+3), 6, '0'), '-S', LPAD(10, 2, '0')), '$.variants[5].variantId', CAST((@bv+21) AS CHAR), '$.variants[5].proposalId', CAST((@bpp+21) AS CHAR), '$.variants[5].sku', CONCAT('SP', LPAD((@bp+3), 6, '0'), '-S', LPAD(11, 2, '0')), '$.variants[6].variantId', CAST((@bv+22) AS CHAR), '$.variants[6].proposalId', CAST((@bpp+22) AS CHAR), '$.variants[6].sku', CONCAT('SP', LPAD((@bp+3), 6, '0'), '-S', LPAD(12, 2, '0')), '$.variants[7].variantId', CAST((@bv+23) AS CHAR), '$.variants[7].proposalId', CAST((@bpp+23) AS CHAR), '$.variants[7].sku', CONCAT('SP', LPAD((@bp+3), 6, '0'), '-S', LPAD(13, 2, '0'))) FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_product_reviews` (`id`, `product_id`, `revision`, `content_version`, `state`, `effective`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `rejection_reason`, `snapshot`)
SELECT (@br+4), (@bp+4), 1, 0, 'APPROVED', 1, 'system:catalogue-seed', '2026-11-12 17:29:55.369288', 'system:catalogue-seed', '2026-11-12 17:29:55.405193', NULL, JSON_SET('{"name":"Nhẫn bạc sóng nước peridot","images":[{"altText":"Nhẫn bạc sóng nước đính peridot xanh lá","mediaId":"a90ba336-f29f-4e28-a0d3-60f9f8f83e78","position":0}],"variants":[{"sku":"SP000004-S06","size":6,"variantId":"24","proposalId":"24","proposedPrice":"560000","proposalRevision":1,"quantityAtSubmission":"4"},{"sku":"SP000004-S07","size":7,"variantId":"25","proposalId":"25","proposedPrice":"560000","proposalRevision":1,"quantityAtSubmission":"4"},{"sku":"SP000004-S08","size":8,"variantId":"26","proposalId":"26","proposedPrice":"560000","proposalRevision":1,"quantityAtSubmission":"4"},{"sku":"SP000004-S09","size":9,"variantId":"27","proposalId":"27","proposedPrice":"560000","proposalRevision":1,"quantityAtSubmission":"4"},{"sku":"SP000004-S10","size":10,"variantId":"28","proposalId":"28","proposedPrice":"560000","proposalRevision":1,"quantityAtSubmission":"4"},{"sku":"SP000004-S11","size":11,"variantId":"29","proposalId":"29","proposedPrice":"560000","proposalRevision":1,"quantityAtSubmission":"4"},{"sku":"SP000004-S12","size":12,"variantId":"30","proposalId":"30","proposedPrice":"560000","proposalRevision":1,"quantityAtSubmission":"4"},{"sku":"SP000004-S13","size":13,"variantId":"31","proposalId":"31","proposedPrice":"560000","proposalRevision":1,"quantityAtSubmission":"4"}],"productId":"4","categoryId":"2","description":"Đai bạc uốn lượn như con sóng, giữa sóng là viên peridot xanh lá mài giác, chấu giữ đá gọn gàng.","categoryName":"Nhẫn đá quý","contentVersion":0}', '$.variants[0].variantId', CAST((@bv+24) AS CHAR), '$.variants[0].proposalId', CAST((@bpp+24) AS CHAR), '$.variants[0].sku', CONCAT('SP', LPAD((@bp+4), 6, '0'), '-S', LPAD(6, 2, '0')), '$.variants[1].variantId', CAST((@bv+25) AS CHAR), '$.variants[1].proposalId', CAST((@bpp+25) AS CHAR), '$.variants[1].sku', CONCAT('SP', LPAD((@bp+4), 6, '0'), '-S', LPAD(7, 2, '0')), '$.variants[2].variantId', CAST((@bv+26) AS CHAR), '$.variants[2].proposalId', CAST((@bpp+26) AS CHAR), '$.variants[2].sku', CONCAT('SP', LPAD((@bp+4), 6, '0'), '-S', LPAD(8, 2, '0')), '$.variants[3].variantId', CAST((@bv+27) AS CHAR), '$.variants[3].proposalId', CAST((@bpp+27) AS CHAR), '$.variants[3].sku', CONCAT('SP', LPAD((@bp+4), 6, '0'), '-S', LPAD(9, 2, '0')), '$.variants[4].variantId', CAST((@bv+28) AS CHAR), '$.variants[4].proposalId', CAST((@bpp+28) AS CHAR), '$.variants[4].sku', CONCAT('SP', LPAD((@bp+4), 6, '0'), '-S', LPAD(10, 2, '0')), '$.variants[5].variantId', CAST((@bv+29) AS CHAR), '$.variants[5].proposalId', CAST((@bpp+29) AS CHAR), '$.variants[5].sku', CONCAT('SP', LPAD((@bp+4), 6, '0'), '-S', LPAD(11, 2, '0')), '$.variants[6].variantId', CAST((@bv+30) AS CHAR), '$.variants[6].proposalId', CAST((@bpp+30) AS CHAR), '$.variants[6].sku', CONCAT('SP', LPAD((@bp+4), 6, '0'), '-S', LPAD(12, 2, '0')), '$.variants[7].variantId', CAST((@bv+31) AS CHAR), '$.variants[7].proposalId', CAST((@bpp+31) AS CHAR), '$.variants[7].sku', CONCAT('SP', LPAD((@bp+4), 6, '0'), '-S', LPAD(13, 2, '0'))) FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_product_reviews` (`id`, `product_id`, `revision`, `content_version`, `state`, `effective`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `rejection_reason`, `snapshot`)
SELECT (@br+5), (@bp+5), 1, 0, 'APPROVED', 1, 'system:catalogue-seed', '2026-11-12 17:29:57.477031', 'system:catalogue-seed', '2026-11-12 17:29:57.521565', NULL, JSON_SET('{"name":"Nhẫn bạc mắt garnet","images":[{"altText":"Nhẫn bạc hình con mắt đính garnet","mediaId":"8391d99d-589e-4d13-ba18-e2cd85a9244b","position":0}],"variants":[{"sku":"SP000005-S07","size":7,"variantId":"32","proposalId":"32","proposedPrice":"540000","proposalRevision":1,"quantityAtSubmission":"4"},{"sku":"SP000005-S08","size":8,"variantId":"33","proposalId":"33","proposedPrice":"540000","proposalRevision":1,"quantityAtSubmission":"4"},{"sku":"SP000005-S09","size":9,"variantId":"34","proposalId":"34","proposedPrice":"540000","proposalRevision":1,"quantityAtSubmission":"4"},{"sku":"SP000005-S10","size":10,"variantId":"35","proposalId":"35","proposedPrice":"540000","proposalRevision":1,"quantityAtSubmission":"4"},{"sku":"SP000005-S11","size":11,"variantId":"36","proposalId":"36","proposedPrice":"540000","proposalRevision":1,"quantityAtSubmission":"4"},{"sku":"SP000005-S12","size":12,"variantId":"37","proposalId":"37","proposedPrice":"540000","proposalRevision":1,"quantityAtSubmission":"4"},{"sku":"SP000005-S13","size":13,"variantId":"38","proposalId":"38","proposedPrice":"540000","proposalRevision":1,"quantityAtSubmission":"4"},{"sku":"SP000005-S14","size":14,"variantId":"39","proposalId":"39","proposedPrice":"540000","proposalRevision":1,"quantityAtSubmission":"4"}],"productId":"5","categoryId":"2","description":"Hai sợi bạc tách ra rồi khép lại thành hình con mắt, ở giữa là viên garnet tím đỏ tròn trong ổ bạc.","categoryName":"Nhẫn đá quý","contentVersion":0}', '$.variants[0].variantId', CAST((@bv+32) AS CHAR), '$.variants[0].proposalId', CAST((@bpp+32) AS CHAR), '$.variants[0].sku', CONCAT('SP', LPAD((@bp+5), 6, '0'), '-S', LPAD(7, 2, '0')), '$.variants[1].variantId', CAST((@bv+33) AS CHAR), '$.variants[1].proposalId', CAST((@bpp+33) AS CHAR), '$.variants[1].sku', CONCAT('SP', LPAD((@bp+5), 6, '0'), '-S', LPAD(8, 2, '0')), '$.variants[2].variantId', CAST((@bv+34) AS CHAR), '$.variants[2].proposalId', CAST((@bpp+34) AS CHAR), '$.variants[2].sku', CONCAT('SP', LPAD((@bp+5), 6, '0'), '-S', LPAD(9, 2, '0')), '$.variants[3].variantId', CAST((@bv+35) AS CHAR), '$.variants[3].proposalId', CAST((@bpp+35) AS CHAR), '$.variants[3].sku', CONCAT('SP', LPAD((@bp+5), 6, '0'), '-S', LPAD(10, 2, '0')), '$.variants[4].variantId', CAST((@bv+36) AS CHAR), '$.variants[4].proposalId', CAST((@bpp+36) AS CHAR), '$.variants[4].sku', CONCAT('SP', LPAD((@bp+5), 6, '0'), '-S', LPAD(11, 2, '0')), '$.variants[5].variantId', CAST((@bv+37) AS CHAR), '$.variants[5].proposalId', CAST((@bpp+37) AS CHAR), '$.variants[5].sku', CONCAT('SP', LPAD((@bp+5), 6, '0'), '-S', LPAD(12, 2, '0')), '$.variants[6].variantId', CAST((@bv+38) AS CHAR), '$.variants[6].proposalId', CAST((@bpp+38) AS CHAR), '$.variants[6].sku', CONCAT('SP', LPAD((@bp+5), 6, '0'), '-S', LPAD(13, 2, '0')), '$.variants[7].variantId', CAST((@bv+39) AS CHAR), '$.variants[7].proposalId', CAST((@bpp+39) AS CHAR), '$.variants[7].sku', CONCAT('SP', LPAD((@bp+5), 6, '0'), '-S', LPAD(14, 2, '0'))) FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_product_reviews` (`id`, `product_id`, `revision`, `content_version`, `state`, `effective`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `rejection_reason`, `snapshot`)
SELECT (@br+6), (@bp+6), 1, 0, 'APPROVED', 1, 'system:catalogue-seed', '2026-11-12 17:29:59.688717', 'system:catalogue-seed', '2026-11-12 17:29:59.750157', NULL, JSON_SET('{"name":"Nhẫn bạc xoáy citrine","images":[{"altText":"Nhẫn bạc hai vòng xoáy đính citrine vàng","mediaId":"88bc7456-ce95-4f6d-8eea-86791efc7120","position":0}],"variants":[{"sku":"SP000006-S06","size":6,"variantId":"40","proposalId":"40","proposedPrice":"520000","proposalRevision":1,"quantityAtSubmission":"4"},{"sku":"SP000006-S07","size":7,"variantId":"41","proposalId":"41","proposedPrice":"520000","proposalRevision":1,"quantityAtSubmission":"4"},{"sku":"SP000006-S08","size":8,"variantId":"42","proposalId":"42","proposedPrice":"520000","proposalRevision":1,"quantityAtSubmission":"4"},{"sku":"SP000006-S09","size":9,"variantId":"43","proposalId":"43","proposedPrice":"520000","proposalRevision":1,"quantityAtSubmission":"4"},{"sku":"SP000006-S10","size":10,"variantId":"44","proposalId":"44","proposedPrice":"520000","proposalRevision":1,"quantityAtSubmission":"4"},{"sku":"SP000006-S11","size":11,"variantId":"45","proposalId":"45","proposedPrice":"520000","proposalRevision":1,"quantityAtSubmission":"4"},{"sku":"SP000006-S12","size":12,"variantId":"46","proposalId":"46","proposedPrice":"520000","proposalRevision":1,"quantityAtSubmission":"4"},{"sku":"SP000006-S13","size":13,"variantId":"47","proposalId":"47","proposedPrice":"520000","proposalRevision":1,"quantityAtSubmission":"4"}],"productId":"6","categoryId":"2","description":"Hai vòng bạc mảnh xoáy quanh một viên citrine vàng chanh, tạo cảm giác nhẹ và trong.","categoryName":"Nhẫn đá quý","contentVersion":0}', '$.variants[0].variantId', CAST((@bv+40) AS CHAR), '$.variants[0].proposalId', CAST((@bpp+40) AS CHAR), '$.variants[0].sku', CONCAT('SP', LPAD((@bp+6), 6, '0'), '-S', LPAD(6, 2, '0')), '$.variants[1].variantId', CAST((@bv+41) AS CHAR), '$.variants[1].proposalId', CAST((@bpp+41) AS CHAR), '$.variants[1].sku', CONCAT('SP', LPAD((@bp+6), 6, '0'), '-S', LPAD(7, 2, '0')), '$.variants[2].variantId', CAST((@bv+42) AS CHAR), '$.variants[2].proposalId', CAST((@bpp+42) AS CHAR), '$.variants[2].sku', CONCAT('SP', LPAD((@bp+6), 6, '0'), '-S', LPAD(8, 2, '0')), '$.variants[3].variantId', CAST((@bv+43) AS CHAR), '$.variants[3].proposalId', CAST((@bpp+43) AS CHAR), '$.variants[3].sku', CONCAT('SP', LPAD((@bp+6), 6, '0'), '-S', LPAD(9, 2, '0')), '$.variants[4].variantId', CAST((@bv+44) AS CHAR), '$.variants[4].proposalId', CAST((@bpp+44) AS CHAR), '$.variants[4].sku', CONCAT('SP', LPAD((@bp+6), 6, '0'), '-S', LPAD(10, 2, '0')), '$.variants[5].variantId', CAST((@bv+45) AS CHAR), '$.variants[5].proposalId', CAST((@bpp+45) AS CHAR), '$.variants[5].sku', CONCAT('SP', LPAD((@bp+6), 6, '0'), '-S', LPAD(11, 2, '0')), '$.variants[6].variantId', CAST((@bv+46) AS CHAR), '$.variants[6].proposalId', CAST((@bpp+46) AS CHAR), '$.variants[6].sku', CONCAT('SP', LPAD((@bp+6), 6, '0'), '-S', LPAD(12, 2, '0')), '$.variants[7].variantId', CAST((@bv+47) AS CHAR), '$.variants[7].proposalId', CAST((@bpp+47) AS CHAR), '$.variants[7].sku', CONCAT('SP', LPAD((@bp+6), 6, '0'), '-S', LPAD(13, 2, '0'))) FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_product_reviews` (`id`, `product_id`, `revision`, `content_version`, `state`, `effective`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `rejection_reason`, `snapshot`)
SELECT (@br+7), (@bp+7), 1, 0, 'APPROVED', 1, 'system:catalogue-seed', '2026-11-12 17:30:01.982217', 'system:catalogue-seed', '2026-11-12 17:30:02.021682', NULL, JSON_SET('{"name":"Nhẫn bạc garnet tròn","images":[{"altText":"Nhẫn bạc đính garnet đỏ tròn","mediaId":"df38bb58-4723-4b28-8373-297eaac8fa30","position":0}],"variants":[{"sku":"SP000007-S06","size":6,"variantId":"48","proposalId":"48","proposedPrice":"480000","proposalRevision":1,"quantityAtSubmission":"4"},{"sku":"SP000007-S07","size":7,"variantId":"49","proposalId":"49","proposedPrice":"480000","proposalRevision":1,"quantityAtSubmission":"4"},{"sku":"SP000007-S08","size":8,"variantId":"50","proposalId":"50","proposedPrice":"480000","proposalRevision":1,"quantityAtSubmission":"4"},{"sku":"SP000007-S09","size":9,"variantId":"51","proposalId":"51","proposedPrice":"480000","proposalRevision":1,"quantityAtSubmission":"4"},{"sku":"SP000007-S10","size":10,"variantId":"52","proposalId":"52","proposedPrice":"480000","proposalRevision":1,"quantityAtSubmission":"4"},{"sku":"SP000007-S11","size":11,"variantId":"53","proposalId":"53","proposedPrice":"480000","proposalRevision":1,"quantityAtSubmission":"4"},{"sku":"SP000007-S12","size":12,"variantId":"54","proposalId":"54","proposedPrice":"480000","proposalRevision":1,"quantityAtSubmission":"4"}],"productId":"7","categoryId":"2","description":"Viên garnet đỏ rượu mài giác tròn, viền chấu hoa nhỏ trên đai bạc mảnh; món quà dễ chọn cho người sinh tháng Một.","categoryName":"Nhẫn đá quý","contentVersion":0}', '$.variants[0].variantId', CAST((@bv+48) AS CHAR), '$.variants[0].proposalId', CAST((@bpp+48) AS CHAR), '$.variants[0].sku', CONCAT('SP', LPAD((@bp+7), 6, '0'), '-S', LPAD(6, 2, '0')), '$.variants[1].variantId', CAST((@bv+49) AS CHAR), '$.variants[1].proposalId', CAST((@bpp+49) AS CHAR), '$.variants[1].sku', CONCAT('SP', LPAD((@bp+7), 6, '0'), '-S', LPAD(7, 2, '0')), '$.variants[2].variantId', CAST((@bv+50) AS CHAR), '$.variants[2].proposalId', CAST((@bpp+50) AS CHAR), '$.variants[2].sku', CONCAT('SP', LPAD((@bp+7), 6, '0'), '-S', LPAD(8, 2, '0')), '$.variants[3].variantId', CAST((@bv+51) AS CHAR), '$.variants[3].proposalId', CAST((@bpp+51) AS CHAR), '$.variants[3].sku', CONCAT('SP', LPAD((@bp+7), 6, '0'), '-S', LPAD(9, 2, '0')), '$.variants[4].variantId', CAST((@bv+52) AS CHAR), '$.variants[4].proposalId', CAST((@bpp+52) AS CHAR), '$.variants[4].sku', CONCAT('SP', LPAD((@bp+7), 6, '0'), '-S', LPAD(10, 2, '0')), '$.variants[5].variantId', CAST((@bv+53) AS CHAR), '$.variants[5].proposalId', CAST((@bpp+53) AS CHAR), '$.variants[5].sku', CONCAT('SP', LPAD((@bp+7), 6, '0'), '-S', LPAD(11, 2, '0')), '$.variants[6].variantId', CAST((@bv+54) AS CHAR), '$.variants[6].proposalId', CAST((@bpp+54) AS CHAR), '$.variants[6].sku', CONCAT('SP', LPAD((@bp+7), 6, '0'), '-S', LPAD(12, 2, '0'))) FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_product_reviews` (`id`, `product_id`, `revision`, `content_version`, `state`, `effective`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `rejection_reason`, `snapshot`)
SELECT (@br+8), (@bp+8), 1, 0, 'APPROVED', 1, 'system:catalogue-seed', '2026-11-12 17:30:04.023113', 'system:catalogue-seed', '2026-11-12 17:30:04.084610', NULL, JSON_SET('{"name":"Nhẫn bạc hổ phách","images":[{"altText":"Nhẫn bạc đính hổ phách cam","mediaId":"26dc16ef-6378-467f-8f9c-5c8e323f4d0d","position":0}],"variants":[{"sku":"SP000008-S06","size":6,"variantId":"55","proposalId":"55","proposedPrice":"590000","proposalRevision":1,"quantityAtSubmission":"3"},{"sku":"SP000008-S07","size":7,"variantId":"56","proposalId":"56","proposedPrice":"590000","proposalRevision":1,"quantityAtSubmission":"3"},{"sku":"SP000008-S08","size":8,"variantId":"57","proposalId":"57","proposedPrice":"590000","proposalRevision":1,"quantityAtSubmission":"3"},{"sku":"SP000008-S09","size":9,"variantId":"58","proposalId":"58","proposedPrice":"590000","proposalRevision":1,"quantityAtSubmission":"3"},{"sku":"SP000008-S10","size":10,"variantId":"59","proposalId":"59","proposedPrice":"590000","proposalRevision":1,"quantityAtSubmission":"3"},{"sku":"SP000008-S11","size":11,"variantId":"60","proposalId":"60","proposedPrice":"590000","proposalRevision":1,"quantityAtSubmission":"3"},{"sku":"SP000008-S12","size":12,"variantId":"61","proposalId":"61","proposedPrice":"590000","proposalRevision":1,"quantityAtSubmission":"3"},{"sku":"SP000008-S13","size":13,"variantId":"62","proposalId":"62","proposedPrice":"590000","proposalRevision":1,"quantityAtSubmission":"3"}],"productId":"8","categoryId":"2","description":"Viên hổ phách cam mài cabochon trong ổ bạc tròn, đai nhẫn đánh vân búa nhẹ. Màu ấm, càng đeo càng bóng.","categoryName":"Nhẫn đá quý","contentVersion":0}', '$.variants[0].variantId', CAST((@bv+55) AS CHAR), '$.variants[0].proposalId', CAST((@bpp+55) AS CHAR), '$.variants[0].sku', CONCAT('SP', LPAD((@bp+8), 6, '0'), '-S', LPAD(6, 2, '0')), '$.variants[1].variantId', CAST((@bv+56) AS CHAR), '$.variants[1].proposalId', CAST((@bpp+56) AS CHAR), '$.variants[1].sku', CONCAT('SP', LPAD((@bp+8), 6, '0'), '-S', LPAD(7, 2, '0')), '$.variants[2].variantId', CAST((@bv+57) AS CHAR), '$.variants[2].proposalId', CAST((@bpp+57) AS CHAR), '$.variants[2].sku', CONCAT('SP', LPAD((@bp+8), 6, '0'), '-S', LPAD(8, 2, '0')), '$.variants[3].variantId', CAST((@bv+58) AS CHAR), '$.variants[3].proposalId', CAST((@bpp+58) AS CHAR), '$.variants[3].sku', CONCAT('SP', LPAD((@bp+8), 6, '0'), '-S', LPAD(9, 2, '0')), '$.variants[4].variantId', CAST((@bv+59) AS CHAR), '$.variants[4].proposalId', CAST((@bpp+59) AS CHAR), '$.variants[4].sku', CONCAT('SP', LPAD((@bp+8), 6, '0'), '-S', LPAD(10, 2, '0')), '$.variants[5].variantId', CAST((@bv+60) AS CHAR), '$.variants[5].proposalId', CAST((@bpp+60) AS CHAR), '$.variants[5].sku', CONCAT('SP', LPAD((@bp+8), 6, '0'), '-S', LPAD(11, 2, '0')), '$.variants[6].variantId', CAST((@bv+61) AS CHAR), '$.variants[6].proposalId', CAST((@bpp+61) AS CHAR), '$.variants[6].sku', CONCAT('SP', LPAD((@bp+8), 6, '0'), '-S', LPAD(12, 2, '0')), '$.variants[7].variantId', CAST((@bv+62) AS CHAR), '$.variants[7].proposalId', CAST((@bpp+62) AS CHAR), '$.variants[7].sku', CONCAT('SP', LPAD((@bp+8), 6, '0'), '-S', LPAD(13, 2, '0'))) FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_product_reviews` (`id`, `product_id`, `revision`, `content_version`, `state`, `effective`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `rejection_reason`, `snapshot`)
SELECT (@br+9), (@bp+9), 1, 0, 'APPROVED', 1, 'system:catalogue-seed', '2026-11-12 17:30:06.370295', 'system:catalogue-seed', '2026-11-12 17:30:06.419350', NULL, JSON_SET('{"name":"Nhẫn bạc cành lá citrine","images":[{"altText":"Nhẫn bạc cành lá đính citrine","mediaId":"84690e58-5f17-4572-b6c6-e1a87ab10dbc","position":0}],"variants":[{"sku":"SP000009-S06","size":6,"variantId":"63","proposalId":"63","proposedPrice":"560000","proposalRevision":1,"quantityAtSubmission":"4"},{"sku":"SP000009-S07","size":7,"variantId":"64","proposalId":"64","proposedPrice":"560000","proposalRevision":1,"quantityAtSubmission":"4"},{"sku":"SP000009-S08","size":8,"variantId":"65","proposalId":"65","proposedPrice":"560000","proposalRevision":1,"quantityAtSubmission":"4"},{"sku":"SP000009-S09","size":9,"variantId":"66","proposalId":"66","proposedPrice":"560000","proposalRevision":1,"quantityAtSubmission":"4"},{"sku":"SP000009-S10","size":10,"variantId":"67","proposalId":"67","proposedPrice":"560000","proposalRevision":1,"quantityAtSubmission":"4"},{"sku":"SP000009-S11","size":11,"variantId":"68","proposalId":"68","proposedPrice":"560000","proposalRevision":1,"quantityAtSubmission":"4"},{"sku":"SP000009-S12","size":12,"variantId":"69","proposalId":"69","proposedPrice":"560000","proposalRevision":1,"quantityAtSubmission":"4"}],"productId":"9","categoryId":"2","description":"Cành bạc nhỏ có lá ôm lấy viên citrine vàng hổ phách, đai nhẫn mảnh như nhánh cây.","categoryName":"Nhẫn đá quý","contentVersion":0}', '$.variants[0].variantId', CAST((@bv+63) AS CHAR), '$.variants[0].proposalId', CAST((@bpp+63) AS CHAR), '$.variants[0].sku', CONCAT('SP', LPAD((@bp+9), 6, '0'), '-S', LPAD(6, 2, '0')), '$.variants[1].variantId', CAST((@bv+64) AS CHAR), '$.variants[1].proposalId', CAST((@bpp+64) AS CHAR), '$.variants[1].sku', CONCAT('SP', LPAD((@bp+9), 6, '0'), '-S', LPAD(7, 2, '0')), '$.variants[2].variantId', CAST((@bv+65) AS CHAR), '$.variants[2].proposalId', CAST((@bpp+65) AS CHAR), '$.variants[2].sku', CONCAT('SP', LPAD((@bp+9), 6, '0'), '-S', LPAD(8, 2, '0')), '$.variants[3].variantId', CAST((@bv+66) AS CHAR), '$.variants[3].proposalId', CAST((@bpp+66) AS CHAR), '$.variants[3].sku', CONCAT('SP', LPAD((@bp+9), 6, '0'), '-S', LPAD(9, 2, '0')), '$.variants[4].variantId', CAST((@bv+67) AS CHAR), '$.variants[4].proposalId', CAST((@bpp+67) AS CHAR), '$.variants[4].sku', CONCAT('SP', LPAD((@bp+9), 6, '0'), '-S', LPAD(10, 2, '0')), '$.variants[5].variantId', CAST((@bv+68) AS CHAR), '$.variants[5].proposalId', CAST((@bpp+68) AS CHAR), '$.variants[5].sku', CONCAT('SP', LPAD((@bp+9), 6, '0'), '-S', LPAD(11, 2, '0')), '$.variants[6].variantId', CAST((@bv+69) AS CHAR), '$.variants[6].proposalId', CAST((@bpp+69) AS CHAR), '$.variants[6].sku', CONCAT('SP', LPAD((@bp+9), 6, '0'), '-S', LPAD(12, 2, '0'))) FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_product_reviews` (`id`, `product_id`, `revision`, `content_version`, `state`, `effective`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `rejection_reason`, `snapshot`)
SELECT (@br+10), (@bp+10), 1, 0, 'APPROVED', 1, 'system:catalogue-seed', '2026-11-12 17:30:08.794246', 'system:catalogue-seed', '2026-11-12 17:30:08.889963', NULL, JSON_SET('{"name":"Nhẫn bạc peridot oval","images":[{"altText":"Nhẫn bạc đính peridot oval","mediaId":"59039fdd-df4a-42cf-9990-3ca68d22b17b","position":0}],"variants":[{"sku":"SP000010-S07","size":7,"variantId":"70","proposalId":"70","proposedPrice":"550000","proposalRevision":1,"quantityAtSubmission":"4"},{"sku":"SP000010-S08","size":8,"variantId":"71","proposalId":"71","proposedPrice":"550000","proposalRevision":1,"quantityAtSubmission":"4"},{"sku":"SP000010-S09","size":9,"variantId":"72","proposalId":"72","proposedPrice":"550000","proposalRevision":1,"quantityAtSubmission":"4"},{"sku":"SP000010-S10","size":10,"variantId":"73","proposalId":"73","proposedPrice":"550000","proposalRevision":1,"quantityAtSubmission":"4"},{"sku":"SP000010-S11","size":11,"variantId":"74","proposalId":"74","proposedPrice":"550000","proposalRevision":1,"quantityAtSubmission":"4"},{"sku":"SP000010-S12","size":12,"variantId":"75","proposalId":"75","proposedPrice":"550000","proposalRevision":1,"quantityAtSubmission":"4"},{"sku":"SP000010-S13","size":13,"variantId":"76","proposalId":"76","proposedPrice":"550000","proposalRevision":1,"quantityAtSubmission":"4"},{"sku":"SP000010-S14","size":14,"variantId":"77","proposalId":"77","proposedPrice":"550000","proposalRevision":1,"quantityAtSubmission":"4"}],"productId":"10","categoryId":"2","description":"Viên peridot xanh ô liu mài oval trong ổ bạc, hai đầu đai nhẫn xoắn nhẹ ôm lấy ổ đá.","categoryName":"Nhẫn đá quý","contentVersion":0}', '$.variants[0].variantId', CAST((@bv+70) AS CHAR), '$.variants[0].proposalId', CAST((@bpp+70) AS CHAR), '$.variants[0].sku', CONCAT('SP', LPAD((@bp+10), 6, '0'), '-S', LPAD(7, 2, '0')), '$.variants[1].variantId', CAST((@bv+71) AS CHAR), '$.variants[1].proposalId', CAST((@bpp+71) AS CHAR), '$.variants[1].sku', CONCAT('SP', LPAD((@bp+10), 6, '0'), '-S', LPAD(8, 2, '0')), '$.variants[2].variantId', CAST((@bv+72) AS CHAR), '$.variants[2].proposalId', CAST((@bpp+72) AS CHAR), '$.variants[2].sku', CONCAT('SP', LPAD((@bp+10), 6, '0'), '-S', LPAD(9, 2, '0')), '$.variants[3].variantId', CAST((@bv+73) AS CHAR), '$.variants[3].proposalId', CAST((@bpp+73) AS CHAR), '$.variants[3].sku', CONCAT('SP', LPAD((@bp+10), 6, '0'), '-S', LPAD(10, 2, '0')), '$.variants[4].variantId', CAST((@bv+74) AS CHAR), '$.variants[4].proposalId', CAST((@bpp+74) AS CHAR), '$.variants[4].sku', CONCAT('SP', LPAD((@bp+10), 6, '0'), '-S', LPAD(11, 2, '0')), '$.variants[5].variantId', CAST((@bv+75) AS CHAR), '$.variants[5].proposalId', CAST((@bpp+75) AS CHAR), '$.variants[5].sku', CONCAT('SP', LPAD((@bp+10), 6, '0'), '-S', LPAD(12, 2, '0')), '$.variants[6].variantId', CAST((@bv+76) AS CHAR), '$.variants[6].proposalId', CAST((@bpp+76) AS CHAR), '$.variants[6].sku', CONCAT('SP', LPAD((@bp+10), 6, '0'), '-S', LPAD(13, 2, '0')), '$.variants[7].variantId', CAST((@bv+77) AS CHAR), '$.variants[7].proposalId', CAST((@bpp+77) AS CHAR), '$.variants[7].sku', CONCAT('SP', LPAD((@bp+10), 6, '0'), '-S', LPAD(14, 2, '0'))) FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_product_reviews` (`id`, `product_id`, `revision`, `content_version`, `state`, `effective`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `rejection_reason`, `snapshot`)
SELECT (@br+11), (@bp+11), 1, 0, 'APPROVED', 1, 'system:catalogue-seed', '2026-11-12 17:30:11.629455', 'system:catalogue-seed', '2026-11-12 17:30:11.666282', NULL, JSON_SET('{"name":"Nhẫn hoa văn vàng thạch anh tím","images":[{"altText":"Nhẫn mạ vàng hoa văn đính thạch anh tím","mediaId":"3e5c1f53-8309-4e24-b9be-3872cd8c03aa","position":0}],"variants":[{"sku":"SP000011-S06","size":6,"variantId":"78","proposalId":"78","proposedPrice":"690000","proposalRevision":1,"quantityAtSubmission":"3"},{"sku":"SP000011-S07","size":7,"variantId":"79","proposalId":"79","proposedPrice":"690000","proposalRevision":1,"quantityAtSubmission":"3"},{"sku":"SP000011-S08","size":8,"variantId":"80","proposalId":"80","proposedPrice":"690000","proposalRevision":1,"quantityAtSubmission":"3"},{"sku":"SP000011-S09","size":9,"variantId":"81","proposalId":"81","proposedPrice":"690000","proposalRevision":1,"quantityAtSubmission":"3"},{"sku":"SP000011-S10","size":10,"variantId":"82","proposalId":"82","proposedPrice":"690000","proposalRevision":1,"quantityAtSubmission":"3"},{"sku":"SP000011-S11","size":11,"variantId":"83","proposalId":"83","proposedPrice":"690000","proposalRevision":1,"quantityAtSubmission":"3"},{"sku":"SP000011-S12","size":12,"variantId":"84","proposalId":"84","proposedPrice":"690000","proposalRevision":1,"quantityAtSubmission":"3"}],"productId":"11","categoryId":"2","description":"Đai nhẫn mạ vàng chạm hoa lá cổ điển, đính viên thạch anh tím mài hình hạt thóc bằng bốn chấu.","categoryName":"Nhẫn đá quý","contentVersion":0}', '$.variants[0].variantId', CAST((@bv+78) AS CHAR), '$.variants[0].proposalId', CAST((@bpp+78) AS CHAR), '$.variants[0].sku', CONCAT('SP', LPAD((@bp+11), 6, '0'), '-S', LPAD(6, 2, '0')), '$.variants[1].variantId', CAST((@bv+79) AS CHAR), '$.variants[1].proposalId', CAST((@bpp+79) AS CHAR), '$.variants[1].sku', CONCAT('SP', LPAD((@bp+11), 6, '0'), '-S', LPAD(7, 2, '0')), '$.variants[2].variantId', CAST((@bv+80) AS CHAR), '$.variants[2].proposalId', CAST((@bpp+80) AS CHAR), '$.variants[2].sku', CONCAT('SP', LPAD((@bp+11), 6, '0'), '-S', LPAD(8, 2, '0')), '$.variants[3].variantId', CAST((@bv+81) AS CHAR), '$.variants[3].proposalId', CAST((@bpp+81) AS CHAR), '$.variants[3].sku', CONCAT('SP', LPAD((@bp+11), 6, '0'), '-S', LPAD(9, 2, '0')), '$.variants[4].variantId', CAST((@bv+82) AS CHAR), '$.variants[4].proposalId', CAST((@bpp+82) AS CHAR), '$.variants[4].sku', CONCAT('SP', LPAD((@bp+11), 6, '0'), '-S', LPAD(10, 2, '0')), '$.variants[5].variantId', CAST((@bv+83) AS CHAR), '$.variants[5].proposalId', CAST((@bpp+83) AS CHAR), '$.variants[5].sku', CONCAT('SP', LPAD((@bp+11), 6, '0'), '-S', LPAD(11, 2, '0')), '$.variants[6].variantId', CAST((@bv+84) AS CHAR), '$.variants[6].proposalId', CAST((@bpp+84) AS CHAR), '$.variants[6].sku', CONCAT('SP', LPAD((@bp+11), 6, '0'), '-S', LPAD(12, 2, '0'))) FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_product_reviews` (`id`, `product_id`, `revision`, `content_version`, `state`, `effective`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `rejection_reason`, `snapshot`)
SELECT (@br+12), (@bp+12), 1, 0, 'APPROVED', 1, 'system:catalogue-seed', '2026-11-12 17:30:13.651695', 'system:catalogue-seed', '2026-11-12 17:30:13.700700', NULL, JSON_SET('{"name":"Nhẫn bạc lá ngọc xanh","images":[{"altText":"Nhẫn bạc đá xanh ngọc điểm lá bạc","mediaId":"2331db2e-0239-4e78-8f29-ddba3a61596b","position":0}],"variants":[{"sku":"SP000012-S06","size":6,"variantId":"85","proposalId":"85","proposedPrice":"520000","proposalRevision":1,"quantityAtSubmission":"4"},{"sku":"SP000012-S07","size":7,"variantId":"86","proposalId":"86","proposedPrice":"520000","proposalRevision":1,"quantityAtSubmission":"4"},{"sku":"SP000012-S08","size":8,"variantId":"87","proposalId":"87","proposedPrice":"520000","proposalRevision":1,"quantityAtSubmission":"4"},{"sku":"SP000012-S09","size":9,"variantId":"88","proposalId":"88","proposedPrice":"520000","proposalRevision":1,"quantityAtSubmission":"4"},{"sku":"SP000012-S10","size":10,"variantId":"89","proposalId":"89","proposedPrice":"520000","proposalRevision":1,"quantityAtSubmission":"4"},{"sku":"SP000012-S11","size":11,"variantId":"90","proposalId":"90","proposedPrice":"520000","proposalRevision":1,"quantityAtSubmission":"4"},{"sku":"SP000012-S12","size":12,"variantId":"91","proposalId":"91","proposedPrice":"520000","proposalRevision":1,"quantityAtSubmission":"4"}],"productId":"12","categoryId":"2","description":"Viên chalcedony xanh ngọc mài cabochon, hai bên điểm lá bạc chạm gân, nhẹ nhàng và tươi mát.","categoryName":"Nhẫn đá quý","contentVersion":0}', '$.variants[0].variantId', CAST((@bv+85) AS CHAR), '$.variants[0].proposalId', CAST((@bpp+85) AS CHAR), '$.variants[0].sku', CONCAT('SP', LPAD((@bp+12), 6, '0'), '-S', LPAD(6, 2, '0')), '$.variants[1].variantId', CAST((@bv+86) AS CHAR), '$.variants[1].proposalId', CAST((@bpp+86) AS CHAR), '$.variants[1].sku', CONCAT('SP', LPAD((@bp+12), 6, '0'), '-S', LPAD(7, 2, '0')), '$.variants[2].variantId', CAST((@bv+87) AS CHAR), '$.variants[2].proposalId', CAST((@bpp+87) AS CHAR), '$.variants[2].sku', CONCAT('SP', LPAD((@bp+12), 6, '0'), '-S', LPAD(8, 2, '0')), '$.variants[3].variantId', CAST((@bv+88) AS CHAR), '$.variants[3].proposalId', CAST((@bpp+88) AS CHAR), '$.variants[3].sku', CONCAT('SP', LPAD((@bp+12), 6, '0'), '-S', LPAD(9, 2, '0')), '$.variants[4].variantId', CAST((@bv+89) AS CHAR), '$.variants[4].proposalId', CAST((@bpp+89) AS CHAR), '$.variants[4].sku', CONCAT('SP', LPAD((@bp+12), 6, '0'), '-S', LPAD(10, 2, '0')), '$.variants[5].variantId', CAST((@bv+90) AS CHAR), '$.variants[5].proposalId', CAST((@bpp+90) AS CHAR), '$.variants[5].sku', CONCAT('SP', LPAD((@bp+12), 6, '0'), '-S', LPAD(11, 2, '0')), '$.variants[6].variantId', CAST((@bv+91) AS CHAR), '$.variants[6].proposalId', CAST((@bpp+91) AS CHAR), '$.variants[6].sku', CONCAT('SP', LPAD((@bp+12), 6, '0'), '-S', LPAD(12, 2, '0'))) FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_product_reviews` (`id`, `product_id`, `revision`, `content_version`, `state`, `effective`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `rejection_reason`, `snapshot`)
SELECT (@br+13), (@bp+13), 1, 0, 'APPROVED', 1, 'system:catalogue-seed', '2026-11-12 17:30:18.527186', 'system:catalogue-seed', '2026-11-12 17:30:18.582163', NULL, JSON_SET('{"name":"Nhẫn bạc mã não cây chữ nhật","images":[{"altText":"Nhẫn bạc mã não cây mặt chữ nhật","mediaId":"da58b598-476a-42a5-88da-cb8595ed9452","position":0},{"altText":"Nhẫn bạc mã não cây mặt chữ nhật nhìn chéo","mediaId":"5056511b-6a97-436b-b8a3-75fb0ba5623c","position":1}],"variants":[{"sku":"SP000013-S08","size":8,"variantId":"92","proposalId":"92","proposedPrice":"750000","proposalRevision":1,"quantityAtSubmission":"2"},{"sku":"SP000013-S09","size":9,"variantId":"93","proposalId":"93","proposedPrice":"750000","proposalRevision":1,"quantityAtSubmission":"2"},{"sku":"SP000013-S10","size":10,"variantId":"94","proposalId":"94","proposedPrice":"750000","proposalRevision":1,"quantityAtSubmission":"2"},{"sku":"SP000013-S11","size":11,"variantId":"95","proposalId":"95","proposedPrice":"750000","proposalRevision":1,"quantityAtSubmission":"2"},{"sku":"SP000013-S12","size":12,"variantId":"96","proposalId":"96","proposedPrice":"750000","proposalRevision":1,"quantityAtSubmission":"2"},{"sku":"SP000013-S13","size":13,"variantId":"97","proposalId":"97","proposedPrice":"750000","proposalRevision":1,"quantityAtSubmission":"2"},{"sku":"SP000013-S14","size":14,"variantId":"98","proposalId":"98","proposedPrice":"750000","proposalRevision":1,"quantityAtSubmission":"2"},{"sku":"SP000013-S15","size":15,"variantId":"99","proposalId":"99","proposedPrice":"750000","proposalRevision":1,"quantityAtSubmission":"2"}],"productId":"13","categoryId":"3","description":"Mặt mã não cây cắt chữ nhật, vân đen như cành cây in trong đá trong; hai vai nhẫn chạm hoa văn nhỏ. Mỗi viên một hoa văn riêng.","categoryName":"Nhẫn mã não","contentVersion":0}', '$.variants[0].variantId', CAST((@bv+92) AS CHAR), '$.variants[0].proposalId', CAST((@bpp+92) AS CHAR), '$.variants[0].sku', CONCAT('SP', LPAD((@bp+13), 6, '0'), '-S', LPAD(8, 2, '0')), '$.variants[1].variantId', CAST((@bv+93) AS CHAR), '$.variants[1].proposalId', CAST((@bpp+93) AS CHAR), '$.variants[1].sku', CONCAT('SP', LPAD((@bp+13), 6, '0'), '-S', LPAD(9, 2, '0')), '$.variants[2].variantId', CAST((@bv+94) AS CHAR), '$.variants[2].proposalId', CAST((@bpp+94) AS CHAR), '$.variants[2].sku', CONCAT('SP', LPAD((@bp+13), 6, '0'), '-S', LPAD(10, 2, '0')), '$.variants[3].variantId', CAST((@bv+95) AS CHAR), '$.variants[3].proposalId', CAST((@bpp+95) AS CHAR), '$.variants[3].sku', CONCAT('SP', LPAD((@bp+13), 6, '0'), '-S', LPAD(11, 2, '0')), '$.variants[4].variantId', CAST((@bv+96) AS CHAR), '$.variants[4].proposalId', CAST((@bpp+96) AS CHAR), '$.variants[4].sku', CONCAT('SP', LPAD((@bp+13), 6, '0'), '-S', LPAD(12, 2, '0')), '$.variants[5].variantId', CAST((@bv+97) AS CHAR), '$.variants[5].proposalId', CAST((@bpp+97) AS CHAR), '$.variants[5].sku', CONCAT('SP', LPAD((@bp+13), 6, '0'), '-S', LPAD(13, 2, '0')), '$.variants[6].variantId', CAST((@bv+98) AS CHAR), '$.variants[6].proposalId', CAST((@bpp+98) AS CHAR), '$.variants[6].sku', CONCAT('SP', LPAD((@bp+13), 6, '0'), '-S', LPAD(14, 2, '0')), '$.variants[7].variantId', CAST((@bv+99) AS CHAR), '$.variants[7].proposalId', CAST((@bpp+99) AS CHAR), '$.variants[7].sku', CONCAT('SP', LPAD((@bp+13), 6, '0'), '-S', LPAD(15, 2, '0'))) FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_product_reviews` (`id`, `product_id`, `revision`, `content_version`, `state`, `effective`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `rejection_reason`, `snapshot`)
SELECT (@br+14), (@bp+14), 1, 0, 'APPROVED', 1, 'system:catalogue-seed', '2026-11-12 17:30:20.895548', 'system:catalogue-seed', '2026-11-12 17:30:20.929548', NULL, JSON_SET('{"name":"Nhẫn bạc mã não cây vuông","images":[{"altText":"Nhẫn bạc mã não cây mặt vuông","mediaId":"2b3bc822-f55d-493e-86f0-1e19263b3ad6","position":0}],"variants":[{"sku":"SP000014-S07","size":7,"variantId":"100","proposalId":"100","proposedPrice":"680000","proposalRevision":1,"quantityAtSubmission":"2"},{"sku":"SP000014-S08","size":8,"variantId":"101","proposalId":"101","proposedPrice":"680000","proposalRevision":1,"quantityAtSubmission":"2"},{"sku":"SP000014-S09","size":9,"variantId":"102","proposalId":"102","proposedPrice":"680000","proposalRevision":1,"quantityAtSubmission":"2"},{"sku":"SP000014-S10","size":10,"variantId":"103","proposalId":"103","proposedPrice":"680000","proposalRevision":1,"quantityAtSubmission":"2"},{"sku":"SP000014-S11","size":11,"variantId":"104","proposalId":"104","proposedPrice":"680000","proposalRevision":1,"quantityAtSubmission":"2"},{"sku":"SP000014-S12","size":12,"variantId":"105","proposalId":"105","proposedPrice":"680000","proposalRevision":1,"quantityAtSubmission":"2"},{"sku":"SP000014-S13","size":13,"variantId":"106","proposalId":"106","proposedPrice":"680000","proposalRevision":1,"quantityAtSubmission":"2"},{"sku":"SP000014-S14","size":14,"variantId":"107","proposalId":"107","proposedPrice":"680000","proposalRevision":1,"quantityAtSubmission":"2"}],"productId":"14","categoryId":"3","description":"Mặt mã não cây vuông bo góc, nền đá sáng với vân cành nâu nhạt, viền bạc mảnh.","categoryName":"Nhẫn mã não","contentVersion":0}', '$.variants[0].variantId', CAST((@bv+100) AS CHAR), '$.variants[0].proposalId', CAST((@bpp+100) AS CHAR), '$.variants[0].sku', CONCAT('SP', LPAD((@bp+14), 6, '0'), '-S', LPAD(7, 2, '0')), '$.variants[1].variantId', CAST((@bv+101) AS CHAR), '$.variants[1].proposalId', CAST((@bpp+101) AS CHAR), '$.variants[1].sku', CONCAT('SP', LPAD((@bp+14), 6, '0'), '-S', LPAD(8, 2, '0')), '$.variants[2].variantId', CAST((@bv+102) AS CHAR), '$.variants[2].proposalId', CAST((@bpp+102) AS CHAR), '$.variants[2].sku', CONCAT('SP', LPAD((@bp+14), 6, '0'), '-S', LPAD(9, 2, '0')), '$.variants[3].variantId', CAST((@bv+103) AS CHAR), '$.variants[3].proposalId', CAST((@bpp+103) AS CHAR), '$.variants[3].sku', CONCAT('SP', LPAD((@bp+14), 6, '0'), '-S', LPAD(10, 2, '0')), '$.variants[4].variantId', CAST((@bv+104) AS CHAR), '$.variants[4].proposalId', CAST((@bpp+104) AS CHAR), '$.variants[4].sku', CONCAT('SP', LPAD((@bp+14), 6, '0'), '-S', LPAD(11, 2, '0')), '$.variants[5].variantId', CAST((@bv+105) AS CHAR), '$.variants[5].proposalId', CAST((@bpp+105) AS CHAR), '$.variants[5].sku', CONCAT('SP', LPAD((@bp+14), 6, '0'), '-S', LPAD(12, 2, '0')), '$.variants[6].variantId', CAST((@bv+106) AS CHAR), '$.variants[6].proposalId', CAST((@bpp+106) AS CHAR), '$.variants[6].sku', CONCAT('SP', LPAD((@bp+14), 6, '0'), '-S', LPAD(13, 2, '0')), '$.variants[7].variantId', CAST((@bv+107) AS CHAR), '$.variants[7].proposalId', CAST((@bpp+107) AS CHAR), '$.variants[7].sku', CONCAT('SP', LPAD((@bp+14), 6, '0'), '-S', LPAD(14, 2, '0'))) FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_product_reviews` (`id`, `product_id`, `revision`, `content_version`, `state`, `effective`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `rejection_reason`, `snapshot`)
SELECT (@br+15), (@bp+15), 1, 0, 'APPROVED', 1, 'system:catalogue-seed', '2026-11-12 17:30:23.079618', 'system:catalogue-seed', '2026-11-12 17:30:23.109995', NULL, JSON_SET('{"name":"Nhẫn bạc mã não cây oval ngang","images":[{"altText":"Nhẫn bạc mã não cây oval đặt ngang","mediaId":"1e14017b-5e7a-426f-ad43-f43b59c97b8f","position":0}],"variants":[{"sku":"SP000015-S08","size":8,"variantId":"108","proposalId":"108","proposedPrice":"720000","proposalRevision":1,"quantityAtSubmission":"2"},{"sku":"SP000015-S09","size":9,"variantId":"109","proposalId":"109","proposedPrice":"720000","proposalRevision":1,"quantityAtSubmission":"2"},{"sku":"SP000015-S10","size":10,"variantId":"110","proposalId":"110","proposedPrice":"720000","proposalRevision":1,"quantityAtSubmission":"2"},{"sku":"SP000015-S11","size":11,"variantId":"111","proposalId":"111","proposedPrice":"720000","proposalRevision":1,"quantityAtSubmission":"2"},{"sku":"SP000015-S12","size":12,"variantId":"112","proposalId":"112","proposedPrice":"720000","proposalRevision":1,"quantityAtSubmission":"2"},{"sku":"SP000015-S13","size":13,"variantId":"113","proposalId":"113","proposedPrice":"720000","proposalRevision":1,"quantityAtSubmission":"2"},{"sku":"SP000015-S14","size":14,"variantId":"114","proposalId":"114","proposedPrice":"720000","proposalRevision":1,"quantityAtSubmission":"2"},{"sku":"SP000015-S15","size":15,"variantId":"115","proposalId":"115","proposedPrice":"720000","proposalRevision":1,"quantityAtSubmission":"2"}],"productId":"15","categoryId":"3","description":"Mặt mã não cây oval đặt ngang, vân đen loang như tranh thủy mặc trên nền đá xám trong.","categoryName":"Nhẫn mã não","contentVersion":0}', '$.variants[0].variantId', CAST((@bv+108) AS CHAR), '$.variants[0].proposalId', CAST((@bpp+108) AS CHAR), '$.variants[0].sku', CONCAT('SP', LPAD((@bp+15), 6, '0'), '-S', LPAD(8, 2, '0')), '$.variants[1].variantId', CAST((@bv+109) AS CHAR), '$.variants[1].proposalId', CAST((@bpp+109) AS CHAR), '$.variants[1].sku', CONCAT('SP', LPAD((@bp+15), 6, '0'), '-S', LPAD(9, 2, '0')), '$.variants[2].variantId', CAST((@bv+110) AS CHAR), '$.variants[2].proposalId', CAST((@bpp+110) AS CHAR), '$.variants[2].sku', CONCAT('SP', LPAD((@bp+15), 6, '0'), '-S', LPAD(10, 2, '0')), '$.variants[3].variantId', CAST((@bv+111) AS CHAR), '$.variants[3].proposalId', CAST((@bpp+111) AS CHAR), '$.variants[3].sku', CONCAT('SP', LPAD((@bp+15), 6, '0'), '-S', LPAD(11, 2, '0')), '$.variants[4].variantId', CAST((@bv+112) AS CHAR), '$.variants[4].proposalId', CAST((@bpp+112) AS CHAR), '$.variants[4].sku', CONCAT('SP', LPAD((@bp+15), 6, '0'), '-S', LPAD(12, 2, '0')), '$.variants[5].variantId', CAST((@bv+113) AS CHAR), '$.variants[5].proposalId', CAST((@bpp+113) AS CHAR), '$.variants[5].sku', CONCAT('SP', LPAD((@bp+15), 6, '0'), '-S', LPAD(13, 2, '0')), '$.variants[6].variantId', CAST((@bv+114) AS CHAR), '$.variants[6].proposalId', CAST((@bpp+114) AS CHAR), '$.variants[6].sku', CONCAT('SP', LPAD((@bp+15), 6, '0'), '-S', LPAD(14, 2, '0')), '$.variants[7].variantId', CAST((@bv+115) AS CHAR), '$.variants[7].proposalId', CAST((@bpp+115) AS CHAR), '$.variants[7].sku', CONCAT('SP', LPAD((@bp+15), 6, '0'), '-S', LPAD(15, 2, '0'))) FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_product_reviews` (`id`, `product_id`, `revision`, `content_version`, `state`, `effective`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `rejection_reason`, `snapshot`)
SELECT (@br+16), (@bp+16), 1, 0, 'APPROVED', 1, 'system:catalogue-seed', '2026-11-12 17:30:25.183008', 'system:catalogue-seed', '2026-11-12 17:30:25.215030', NULL, JSON_SET('{"name":"Nhẫn bạc mã não cây oval dọc","images":[{"altText":"Nhẫn bạc mã não cây oval đặt dọc","mediaId":"381ee35b-acbb-4705-9eea-83a3fead5bca","position":0}],"variants":[{"sku":"SP000016-S07","size":7,"variantId":"116","proposalId":"116","proposedPrice":"680000","proposalRevision":1,"quantityAtSubmission":"2"},{"sku":"SP000016-S08","size":8,"variantId":"117","proposalId":"117","proposedPrice":"680000","proposalRevision":1,"quantityAtSubmission":"2"},{"sku":"SP000016-S09","size":9,"variantId":"118","proposalId":"118","proposedPrice":"680000","proposalRevision":1,"quantityAtSubmission":"2"},{"sku":"SP000016-S10","size":10,"variantId":"119","proposalId":"119","proposedPrice":"680000","proposalRevision":1,"quantityAtSubmission":"2"},{"sku":"SP000016-S11","size":11,"variantId":"120","proposalId":"120","proposedPrice":"680000","proposalRevision":1,"quantityAtSubmission":"2"},{"sku":"SP000016-S12","size":12,"variantId":"121","proposalId":"121","proposedPrice":"680000","proposalRevision":1,"quantityAtSubmission":"2"},{"sku":"SP000016-S13","size":13,"variantId":"122","proposalId":"122","proposedPrice":"680000","proposalRevision":1,"quantityAtSubmission":"2"},{"sku":"SP000016-S14","size":14,"variantId":"123","proposalId":"123","proposedPrice":"680000","proposalRevision":1,"quantityAtSubmission":"2"}],"productId":"16","categoryId":"3","description":"Mặt mã não cây oval đặt dọc theo ngón tay, vân cành mảnh, làm ngón tay trông thon dài hơn.","categoryName":"Nhẫn mã não","contentVersion":0}', '$.variants[0].variantId', CAST((@bv+116) AS CHAR), '$.variants[0].proposalId', CAST((@bpp+116) AS CHAR), '$.variants[0].sku', CONCAT('SP', LPAD((@bp+16), 6, '0'), '-S', LPAD(7, 2, '0')), '$.variants[1].variantId', CAST((@bv+117) AS CHAR), '$.variants[1].proposalId', CAST((@bpp+117) AS CHAR), '$.variants[1].sku', CONCAT('SP', LPAD((@bp+16), 6, '0'), '-S', LPAD(8, 2, '0')), '$.variants[2].variantId', CAST((@bv+118) AS CHAR), '$.variants[2].proposalId', CAST((@bpp+118) AS CHAR), '$.variants[2].sku', CONCAT('SP', LPAD((@bp+16), 6, '0'), '-S', LPAD(9, 2, '0')), '$.variants[3].variantId', CAST((@bv+119) AS CHAR), '$.variants[3].proposalId', CAST((@bpp+119) AS CHAR), '$.variants[3].sku', CONCAT('SP', LPAD((@bp+16), 6, '0'), '-S', LPAD(10, 2, '0')), '$.variants[4].variantId', CAST((@bv+120) AS CHAR), '$.variants[4].proposalId', CAST((@bpp+120) AS CHAR), '$.variants[4].sku', CONCAT('SP', LPAD((@bp+16), 6, '0'), '-S', LPAD(11, 2, '0')), '$.variants[5].variantId', CAST((@bv+121) AS CHAR), '$.variants[5].proposalId', CAST((@bpp+121) AS CHAR), '$.variants[5].sku', CONCAT('SP', LPAD((@bp+16), 6, '0'), '-S', LPAD(12, 2, '0')), '$.variants[6].variantId', CAST((@bv+122) AS CHAR), '$.variants[6].proposalId', CAST((@bpp+122) AS CHAR), '$.variants[6].sku', CONCAT('SP', LPAD((@bp+16), 6, '0'), '-S', LPAD(13, 2, '0')), '$.variants[7].variantId', CAST((@bv+123) AS CHAR), '$.variants[7].proposalId', CAST((@bpp+123) AS CHAR), '$.variants[7].sku', CONCAT('SP', LPAD((@bp+16), 6, '0'), '-S', LPAD(14, 2, '0'))) FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_product_reviews` (`id`, `product_id`, `revision`, `content_version`, `state`, `effective`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `rejection_reason`, `snapshot`)
SELECT (@br+17), (@bp+17), 1, 0, 'APPROVED', 1, 'system:catalogue-seed', '2026-11-12 17:30:27.313676', 'system:catalogue-seed', '2026-11-12 17:30:27.346827', NULL, JSON_SET('{"name":"Nhẫn bạc mã não rêu trái tim","images":[{"altText":"Nhẫn bạc mã não rêu hình trái tim","mediaId":"ddc93f57-3e47-4778-aef3-ab9a6be038ae","position":0}],"variants":[{"sku":"SP000017-S06","size":6,"variantId":"124","proposalId":"124","proposedPrice":"620000","proposalRevision":1,"quantityAtSubmission":"3"},{"sku":"SP000017-S07","size":7,"variantId":"125","proposalId":"125","proposedPrice":"620000","proposalRevision":1,"quantityAtSubmission":"3"},{"sku":"SP000017-S08","size":8,"variantId":"126","proposalId":"126","proposedPrice":"620000","proposalRevision":1,"quantityAtSubmission":"3"},{"sku":"SP000017-S09","size":9,"variantId":"127","proposalId":"127","proposedPrice":"620000","proposalRevision":1,"quantityAtSubmission":"3"},{"sku":"SP000017-S10","size":10,"variantId":"128","proposalId":"128","proposedPrice":"620000","proposalRevision":1,"quantityAtSubmission":"3"},{"sku":"SP000017-S11","size":11,"variantId":"129","proposalId":"129","proposedPrice":"620000","proposalRevision":1,"quantityAtSubmission":"3"},{"sku":"SP000017-S12","size":12,"variantId":"130","proposalId":"130","proposedPrice":"620000","proposalRevision":1,"quantityAtSubmission":"3"},{"sku":"SP000017-S13","size":13,"variantId":"131","proposalId":"131","proposedPrice":"620000","proposalRevision":1,"quantityAtSubmission":"3"}],"productId":"17","categoryId":"3","description":"Mã não rêu mài trái tim, bên trong có những mảng xanh rêu như khu vườn thu nhỏ, ổ bạc ôm sát.","categoryName":"Nhẫn mã não","contentVersion":0}', '$.variants[0].variantId', CAST((@bv+124) AS CHAR), '$.variants[0].proposalId', CAST((@bpp+124) AS CHAR), '$.variants[0].sku', CONCAT('SP', LPAD((@bp+17), 6, '0'), '-S', LPAD(6, 2, '0')), '$.variants[1].variantId', CAST((@bv+125) AS CHAR), '$.variants[1].proposalId', CAST((@bpp+125) AS CHAR), '$.variants[1].sku', CONCAT('SP', LPAD((@bp+17), 6, '0'), '-S', LPAD(7, 2, '0')), '$.variants[2].variantId', CAST((@bv+126) AS CHAR), '$.variants[2].proposalId', CAST((@bpp+126) AS CHAR), '$.variants[2].sku', CONCAT('SP', LPAD((@bp+17), 6, '0'), '-S', LPAD(8, 2, '0')), '$.variants[3].variantId', CAST((@bv+127) AS CHAR), '$.variants[3].proposalId', CAST((@bpp+127) AS CHAR), '$.variants[3].sku', CONCAT('SP', LPAD((@bp+17), 6, '0'), '-S', LPAD(9, 2, '0')), '$.variants[4].variantId', CAST((@bv+128) AS CHAR), '$.variants[4].proposalId', CAST((@bpp+128) AS CHAR), '$.variants[4].sku', CONCAT('SP', LPAD((@bp+17), 6, '0'), '-S', LPAD(10, 2, '0')), '$.variants[5].variantId', CAST((@bv+129) AS CHAR), '$.variants[5].proposalId', CAST((@bpp+129) AS CHAR), '$.variants[5].sku', CONCAT('SP', LPAD((@bp+17), 6, '0'), '-S', LPAD(11, 2, '0')), '$.variants[6].variantId', CAST((@bv+130) AS CHAR), '$.variants[6].proposalId', CAST((@bpp+130) AS CHAR), '$.variants[6].sku', CONCAT('SP', LPAD((@bp+17), 6, '0'), '-S', LPAD(12, 2, '0')), '$.variants[7].variantId', CAST((@bv+131) AS CHAR), '$.variants[7].proposalId', CAST((@bpp+131) AS CHAR), '$.variants[7].sku', CONCAT('SP', LPAD((@bp+17), 6, '0'), '-S', LPAD(13, 2, '0'))) FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_product_reviews` (`id`, `product_id`, `revision`, `content_version`, `state`, `effective`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `rejection_reason`, `snapshot`)
SELECT (@br+18), (@bp+18), 1, 0, 'APPROVED', 1, 'system:catalogue-seed', '2026-11-12 17:30:29.329362', 'system:catalogue-seed', '2026-11-12 17:30:29.360400', NULL, JSON_SET('{"name":"Nhẫn bạc mã não rêu giọt nước","images":[{"altText":"Nhẫn bạc mã não rêu hình giọt nước","mediaId":"2f0167bc-480b-4693-bbbf-acf7b60c7ef4","position":0}],"variants":[{"sku":"SP000018-S07","size":7,"variantId":"132","proposalId":"132","proposedPrice":"650000","proposalRevision":1,"quantityAtSubmission":"3"},{"sku":"SP000018-S08","size":8,"variantId":"133","proposalId":"133","proposedPrice":"650000","proposalRevision":1,"quantityAtSubmission":"3"},{"sku":"SP000018-S09","size":9,"variantId":"134","proposalId":"134","proposedPrice":"650000","proposalRevision":1,"quantityAtSubmission":"3"},{"sku":"SP000018-S10","size":10,"variantId":"135","proposalId":"135","proposedPrice":"650000","proposalRevision":1,"quantityAtSubmission":"3"},{"sku":"SP000018-S11","size":11,"variantId":"136","proposalId":"136","proposedPrice":"650000","proposalRevision":1,"quantityAtSubmission":"3"},{"sku":"SP000018-S12","size":12,"variantId":"137","proposalId":"137","proposedPrice":"650000","proposalRevision":1,"quantityAtSubmission":"3"},{"sku":"SP000018-S13","size":13,"variantId":"138","proposalId":"138","proposedPrice":"650000","proposalRevision":1,"quantityAtSubmission":"3"},{"sku":"SP000018-S14","size":14,"variantId":"139","proposalId":"139","proposedPrice":"650000","proposalRevision":1,"quantityAtSubmission":"3"}],"productId":"18","categoryId":"3","description":"Viên mã não rêu lớn mài giọt nước, vân xanh đậm nhạt đan xen; đai bạc trơn để đá làm điểm nhấn.","categoryName":"Nhẫn mã não","contentVersion":0}', '$.variants[0].variantId', CAST((@bv+132) AS CHAR), '$.variants[0].proposalId', CAST((@bpp+132) AS CHAR), '$.variants[0].sku', CONCAT('SP', LPAD((@bp+18), 6, '0'), '-S', LPAD(7, 2, '0')), '$.variants[1].variantId', CAST((@bv+133) AS CHAR), '$.variants[1].proposalId', CAST((@bpp+133) AS CHAR), '$.variants[1].sku', CONCAT('SP', LPAD((@bp+18), 6, '0'), '-S', LPAD(8, 2, '0')), '$.variants[2].variantId', CAST((@bv+134) AS CHAR), '$.variants[2].proposalId', CAST((@bpp+134) AS CHAR), '$.variants[2].sku', CONCAT('SP', LPAD((@bp+18), 6, '0'), '-S', LPAD(9, 2, '0')), '$.variants[3].variantId', CAST((@bv+135) AS CHAR), '$.variants[3].proposalId', CAST((@bpp+135) AS CHAR), '$.variants[3].sku', CONCAT('SP', LPAD((@bp+18), 6, '0'), '-S', LPAD(10, 2, '0')), '$.variants[4].variantId', CAST((@bv+136) AS CHAR), '$.variants[4].proposalId', CAST((@bpp+136) AS CHAR), '$.variants[4].sku', CONCAT('SP', LPAD((@bp+18), 6, '0'), '-S', LPAD(11, 2, '0')), '$.variants[5].variantId', CAST((@bv+137) AS CHAR), '$.variants[5].proposalId', CAST((@bpp+137) AS CHAR), '$.variants[5].sku', CONCAT('SP', LPAD((@bp+18), 6, '0'), '-S', LPAD(12, 2, '0')), '$.variants[6].variantId', CAST((@bv+138) AS CHAR), '$.variants[6].proposalId', CAST((@bpp+138) AS CHAR), '$.variants[6].sku', CONCAT('SP', LPAD((@bp+18), 6, '0'), '-S', LPAD(13, 2, '0')), '$.variants[7].variantId', CAST((@bv+139) AS CHAR), '$.variants[7].proposalId', CAST((@bpp+139) AS CHAR), '$.variants[7].sku', CONCAT('SP', LPAD((@bp+18), 6, '0'), '-S', LPAD(14, 2, '0'))) FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_product_reviews` (`id`, `product_id`, `revision`, `content_version`, `state`, `effective`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `rejection_reason`, `snapshot`)
SELECT (@br+19), (@bp+19), 1, 0, 'APPROVED', 1, 'system:catalogue-seed', '2026-11-12 17:30:31.437050', 'system:catalogue-seed', '2026-11-12 17:30:31.474241', NULL, JSON_SET('{"name":"Nhẫn bạc ngọc xanh quấn thừng","images":[{"altText":"Nhẫn bạc ngọc xanh quấn dây bạc xoắn","mediaId":"a419904e-f035-4623-89d2-e179e51de87d","position":0}],"variants":[{"sku":"SP000019-S07","size":7,"variantId":"140","proposalId":"140","proposedPrice":"480000","proposalRevision":1,"quantityAtSubmission":"4"},{"sku":"SP000019-S08","size":8,"variantId":"141","proposalId":"141","proposedPrice":"480000","proposalRevision":1,"quantityAtSubmission":"4"},{"sku":"SP000019-S09","size":9,"variantId":"142","proposalId":"142","proposedPrice":"480000","proposalRevision":1,"quantityAtSubmission":"4"},{"sku":"SP000019-S10","size":10,"variantId":"143","proposalId":"143","proposedPrice":"480000","proposalRevision":1,"quantityAtSubmission":"4"},{"sku":"SP000019-S11","size":11,"variantId":"144","proposalId":"144","proposedPrice":"480000","proposalRevision":1,"quantityAtSubmission":"4"},{"sku":"SP000019-S12","size":12,"variantId":"145","proposalId":"145","proposedPrice":"480000","proposalRevision":1,"quantityAtSubmission":"4"},{"sku":"SP000019-S13","size":13,"variantId":"146","proposalId":"146","proposedPrice":"480000","proposalRevision":1,"quantityAtSubmission":"4"},{"sku":"SP000019-S14","size":14,"variantId":"147","proposalId":"147","proposedPrice":"480000","proposalRevision":1,"quantityAtSubmission":"4"}],"productId":"19","categoryId":"4","description":"Viên chalcedony xanh ngọc thuôn dài được giữ bằng sợi bạc xoắn thừng quấn quanh đai nhẫn bản dẹt.","categoryName":"Nhẫn đá thô","contentVersion":0}', '$.variants[0].variantId', CAST((@bv+140) AS CHAR), '$.variants[0].proposalId', CAST((@bpp+140) AS CHAR), '$.variants[0].sku', CONCAT('SP', LPAD((@bp+19), 6, '0'), '-S', LPAD(7, 2, '0')), '$.variants[1].variantId', CAST((@bv+141) AS CHAR), '$.variants[1].proposalId', CAST((@bpp+141) AS CHAR), '$.variants[1].sku', CONCAT('SP', LPAD((@bp+19), 6, '0'), '-S', LPAD(8, 2, '0')), '$.variants[2].variantId', CAST((@bv+142) AS CHAR), '$.variants[2].proposalId', CAST((@bpp+142) AS CHAR), '$.variants[2].sku', CONCAT('SP', LPAD((@bp+19), 6, '0'), '-S', LPAD(9, 2, '0')), '$.variants[3].variantId', CAST((@bv+143) AS CHAR), '$.variants[3].proposalId', CAST((@bpp+143) AS CHAR), '$.variants[3].sku', CONCAT('SP', LPAD((@bp+19), 6, '0'), '-S', LPAD(10, 2, '0')), '$.variants[4].variantId', CAST((@bv+144) AS CHAR), '$.variants[4].proposalId', CAST((@bpp+144) AS CHAR), '$.variants[4].sku', CONCAT('SP', LPAD((@bp+19), 6, '0'), '-S', LPAD(11, 2, '0')), '$.variants[5].variantId', CAST((@bv+145) AS CHAR), '$.variants[5].proposalId', CAST((@bpp+145) AS CHAR), '$.variants[5].sku', CONCAT('SP', LPAD((@bp+19), 6, '0'), '-S', LPAD(12, 2, '0')), '$.variants[6].variantId', CAST((@bv+146) AS CHAR), '$.variants[6].proposalId', CAST((@bpp+146) AS CHAR), '$.variants[6].sku', CONCAT('SP', LPAD((@bp+19), 6, '0'), '-S', LPAD(13, 2, '0')), '$.variants[7].variantId', CAST((@bv+147) AS CHAR), '$.variants[7].proposalId', CAST((@bpp+147) AS CHAR), '$.variants[7].sku', CONCAT('SP', LPAD((@bp+19), 6, '0'), '-S', LPAD(14, 2, '0'))) FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_product_reviews` (`id`, `product_id`, `revision`, `content_version`, `state`, `effective`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `rejection_reason`, `snapshot`)
SELECT (@br+20), (@bp+20), 1, 0, 'APPROVED', 1, 'system:catalogue-seed', '2026-11-12 17:30:35.318110', 'system:catalogue-seed', '2026-11-12 17:30:35.345660', NULL, JSON_SET('{"name":"Nhẫn bạc thạch anh thô","images":[{"altText":"Nhẫn bạc thạch anh thô trong hộp","mediaId":"ae57c551-032d-4f91-a808-2f2fed7721fd","position":0},{"altText":"Nhẫn bạc thạch anh thô nhìn nghiêng","mediaId":"d6282338-a736-4c17-acf8-b5e2b7d0715e","position":1}],"variants":[{"sku":"SP000020-S06","size":6,"variantId":"148","proposalId":"148","proposedPrice":"590000","proposalRevision":1,"quantityAtSubmission":"3"},{"sku":"SP000020-S07","size":7,"variantId":"149","proposalId":"149","proposedPrice":"590000","proposalRevision":1,"quantityAtSubmission":"3"},{"sku":"SP000020-S08","size":8,"variantId":"150","proposalId":"150","proposedPrice":"590000","proposalRevision":1,"quantityAtSubmission":"3"},{"sku":"SP000020-S09","size":9,"variantId":"151","proposalId":"151","proposedPrice":"590000","proposalRevision":1,"quantityAtSubmission":"3"},{"sku":"SP000020-S10","size":10,"variantId":"152","proposalId":"152","proposedPrice":"590000","proposalRevision":1,"quantityAtSubmission":"3"},{"sku":"SP000020-S11","size":11,"variantId":"153","proposalId":"153","proposedPrice":"590000","proposalRevision":1,"quantityAtSubmission":"3"},{"sku":"SP000020-S12","size":12,"variantId":"154","proposalId":"154","proposedPrice":"590000","proposalRevision":1,"quantityAtSubmission":"3"}],"productId":"20","categoryId":"4","description":"Viên thạch anh thô còn nguyên mặt tinh thể, trong lòng có ánh xanh tím, ôm bằng viền bạc gò tay.","categoryName":"Nhẫn đá thô","contentVersion":0}', '$.variants[0].variantId', CAST((@bv+148) AS CHAR), '$.variants[0].proposalId', CAST((@bpp+148) AS CHAR), '$.variants[0].sku', CONCAT('SP', LPAD((@bp+20), 6, '0'), '-S', LPAD(6, 2, '0')), '$.variants[1].variantId', CAST((@bv+149) AS CHAR), '$.variants[1].proposalId', CAST((@bpp+149) AS CHAR), '$.variants[1].sku', CONCAT('SP', LPAD((@bp+20), 6, '0'), '-S', LPAD(7, 2, '0')), '$.variants[2].variantId', CAST((@bv+150) AS CHAR), '$.variants[2].proposalId', CAST((@bpp+150) AS CHAR), '$.variants[2].sku', CONCAT('SP', LPAD((@bp+20), 6, '0'), '-S', LPAD(8, 2, '0')), '$.variants[3].variantId', CAST((@bv+151) AS CHAR), '$.variants[3].proposalId', CAST((@bpp+151) AS CHAR), '$.variants[3].sku', CONCAT('SP', LPAD((@bp+20), 6, '0'), '-S', LPAD(9, 2, '0')), '$.variants[4].variantId', CAST((@bv+152) AS CHAR), '$.variants[4].proposalId', CAST((@bpp+152) AS CHAR), '$.variants[4].sku', CONCAT('SP', LPAD((@bp+20), 6, '0'), '-S', LPAD(10, 2, '0')), '$.variants[5].variantId', CAST((@bv+153) AS CHAR), '$.variants[5].proposalId', CAST((@bpp+153) AS CHAR), '$.variants[5].sku', CONCAT('SP', LPAD((@bp+20), 6, '0'), '-S', LPAD(11, 2, '0')), '$.variants[6].variantId', CAST((@bv+154) AS CHAR), '$.variants[6].proposalId', CAST((@bpp+154) AS CHAR), '$.variants[6].sku', CONCAT('SP', LPAD((@bp+20), 6, '0'), '-S', LPAD(12, 2, '0'))) FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_product_reviews` (`id`, `product_id`, `revision`, `content_version`, `state`, `effective`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `rejection_reason`, `snapshot`)
SELECT (@br+21), (@bp+21), 1, 0, 'APPROVED', 1, 'system:catalogue-seed', '2026-11-12 17:30:37.572893', 'system:catalogue-seed', '2026-11-12 17:30:37.608883', NULL, JSON_SET('{"name":"Nhẫn bạc hở tourmaline xanh","images":[{"altText":"Nhẫn bạc hở hai thanh tourmaline xanh","mediaId":"0d5cf1c7-615f-4b52-99fd-2c5a2802261b","position":0}],"variants":[{"sku":"SP000021-S06","size":6,"variantId":"155","proposalId":"155","proposedPrice":"640000","proposalRevision":1,"quantityAtSubmission":"2"},{"sku":"SP000021-S07","size":7,"variantId":"156","proposalId":"156","proposedPrice":"640000","proposalRevision":1,"quantityAtSubmission":"2"},{"sku":"SP000021-S08","size":8,"variantId":"157","proposalId":"157","proposedPrice":"640000","proposalRevision":1,"quantityAtSubmission":"2"},{"sku":"SP000021-S09","size":9,"variantId":"158","proposalId":"158","proposedPrice":"640000","proposalRevision":1,"quantityAtSubmission":"2"},{"sku":"SP000021-S10","size":10,"variantId":"159","proposalId":"159","proposedPrice":"640000","proposalRevision":1,"quantityAtSubmission":"2"},{"sku":"SP000021-S11","size":11,"variantId":"160","proposalId":"160","proposedPrice":"640000","proposalRevision":1,"quantityAtSubmission":"2"},{"sku":"SP000021-S12","size":12,"variantId":"161","proposalId":"161","proposedPrice":"640000","proposalRevision":1,"quantityAtSubmission":"2"}],"productId":"21","categoryId":"4","description":"Nhẫn hở với hai thanh tourmaline xanh lá thô ở hai đầu, kẹp bằng khâu bạc; nới khép nhẹ cho vừa tay.","categoryName":"Nhẫn đá thô","contentVersion":0}', '$.variants[0].variantId', CAST((@bv+155) AS CHAR), '$.variants[0].proposalId', CAST((@bpp+155) AS CHAR), '$.variants[0].sku', CONCAT('SP', LPAD((@bp+21), 6, '0'), '-S', LPAD(6, 2, '0')), '$.variants[1].variantId', CAST((@bv+156) AS CHAR), '$.variants[1].proposalId', CAST((@bpp+156) AS CHAR), '$.variants[1].sku', CONCAT('SP', LPAD((@bp+21), 6, '0'), '-S', LPAD(7, 2, '0')), '$.variants[2].variantId', CAST((@bv+157) AS CHAR), '$.variants[2].proposalId', CAST((@bpp+157) AS CHAR), '$.variants[2].sku', CONCAT('SP', LPAD((@bp+21), 6, '0'), '-S', LPAD(8, 2, '0')), '$.variants[3].variantId', CAST((@bv+158) AS CHAR), '$.variants[3].proposalId', CAST((@bpp+158) AS CHAR), '$.variants[3].sku', CONCAT('SP', LPAD((@bp+21), 6, '0'), '-S', LPAD(9, 2, '0')), '$.variants[4].variantId', CAST((@bv+159) AS CHAR), '$.variants[4].proposalId', CAST((@bpp+159) AS CHAR), '$.variants[4].sku', CONCAT('SP', LPAD((@bp+21), 6, '0'), '-S', LPAD(10, 2, '0')), '$.variants[5].variantId', CAST((@bv+160) AS CHAR), '$.variants[5].proposalId', CAST((@bpp+160) AS CHAR), '$.variants[5].sku', CONCAT('SP', LPAD((@bp+21), 6, '0'), '-S', LPAD(11, 2, '0')), '$.variants[6].variantId', CAST((@bv+161) AS CHAR), '$.variants[6].proposalId', CAST((@bpp+161) AS CHAR), '$.variants[6].sku', CONCAT('SP', LPAD((@bp+21), 6, '0'), '-S', LPAD(12, 2, '0'))) FROM DUAL WHERE @run_catalogue;

-- Price proposals
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+1), (@bv+1), (@br+1), 1, 2, 'APPROVED', 1, 390000, 390000, 'VND', 0, 'c7756dab73a07b547ef4a4f752916f1c626ac4ba2aa3484ec20a084838a1d1d9', 'system:catalogue-seed', '2026-11-12 17:29:48.368265', 'system:catalogue-seed', '2026-11-12 17:29:48.515323', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+2), (@bv+2), (@br+1), 1, 2, 'APPROVED', 1, 390000, 390000, 'VND', 0, '350134b753f1f908e8739b6daea03dbfad7dac783d0751055f059a5eeea8fc25', 'system:catalogue-seed', '2026-11-12 17:29:48.368265', 'system:catalogue-seed', '2026-11-12 17:29:48.515323', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+3), (@bv+3), (@br+1), 1, 2, 'APPROVED', 1, 390000, 390000, 'VND', 0, 'fb9b4d0bf4ea3e56179aba00d3ae78c3c1b7ce496bbc94d413695086a5d057d4', 'system:catalogue-seed', '2026-11-12 17:29:48.368265', 'system:catalogue-seed', '2026-11-12 17:29:48.515323', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+4), (@bv+4), (@br+1), 1, 2, 'APPROVED', 1, 390000, 390000, 'VND', 0, '253c8dc2872a0231a50589588e38d3b8923f8c088942fa00285272c2218e3cbc', 'system:catalogue-seed', '2026-11-12 17:29:48.368265', 'system:catalogue-seed', '2026-11-12 17:29:48.515323', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+5), (@bv+5), (@br+1), 1, 2, 'APPROVED', 1, 390000, 390000, 'VND', 0, '0bfaf592f4e0909d96f54272727ec45eb6cac324d07d00bf80dea092b944102a', 'system:catalogue-seed', '2026-11-12 17:29:48.368265', 'system:catalogue-seed', '2026-11-12 17:29:48.515323', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+6), (@bv+6), (@br+1), 1, 2, 'APPROVED', 1, 390000, 390000, 'VND', 0, '3ee700d9a97ff35dbd2787edf62b07c976114f668c9711a13200ac5f8b621c92', 'system:catalogue-seed', '2026-11-12 17:29:48.368265', 'system:catalogue-seed', '2026-11-12 17:29:48.515323', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+7), (@bv+7), (@br+1), 1, 2, 'APPROVED', 1, 390000, 390000, 'VND', 0, '7885e66575f1f038bba48e8ccb9af398fe76d176e21952c5339fb8ee415f6de6', 'system:catalogue-seed', '2026-11-12 17:29:48.368265', 'system:catalogue-seed', '2026-11-12 17:29:48.515323', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+8), (@bv+8), (@br+1), 1, 2, 'APPROVED', 1, 390000, 390000, 'VND', 0, 'd24d4b35ef2210566dd1ba719347e832a8da77eda8fa787d855cf1af5304b3ea', 'system:catalogue-seed', '2026-11-12 17:29:48.368265', 'system:catalogue-seed', '2026-11-12 17:29:48.515323', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+9), (@bv+9), (@br+2), 1, 2, 'APPROVED', 1, 420000, 420000, 'VND', 0, '2048dd6d56de72c51641ace72d1d574311e8c0d2ab01785f3d19443c98e41825', 'system:catalogue-seed', '2026-11-12 17:29:51.004674', 'system:catalogue-seed', '2026-11-12 17:29:51.055328', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+10), (@bv+10), (@br+2), 1, 2, 'APPROVED', 1, 420000, 420000, 'VND', 0, '04b7c6185987a7604d13cbe2a1410c65bac378c8293d88e4d9b7d943759b2cf9', 'system:catalogue-seed', '2026-11-12 17:29:51.004674', 'system:catalogue-seed', '2026-11-12 17:29:51.055328', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+11), (@bv+11), (@br+2), 1, 2, 'APPROVED', 1, 420000, 420000, 'VND', 0, 'bd004c89a3762ef633c18d9b908c5c6caa6de71c73b6464918dde5ca23ee8eae', 'system:catalogue-seed', '2026-11-12 17:29:51.004674', 'system:catalogue-seed', '2026-11-12 17:29:51.055328', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+12), (@bv+12), (@br+2), 1, 2, 'APPROVED', 1, 420000, 420000, 'VND', 0, '692cd75ca0ef1643d248214fcc331ea283128e03d1600cece583b365960cc94a', 'system:catalogue-seed', '2026-11-12 17:29:51.004674', 'system:catalogue-seed', '2026-11-12 17:29:51.055328', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+13), (@bv+13), (@br+2), 1, 2, 'APPROVED', 1, 420000, 420000, 'VND', 0, '40e6fc5c22d97aba303d360c2e41168799142fb8394ba318ca3e0639cac33320', 'system:catalogue-seed', '2026-11-12 17:29:51.004674', 'system:catalogue-seed', '2026-11-12 17:29:51.055328', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+14), (@bv+14), (@br+2), 1, 2, 'APPROVED', 1, 420000, 420000, 'VND', 0, 'c583880cf556f53e4eefa393e7d9114586b196ecf1ba7fd5258eb9e76720375b', 'system:catalogue-seed', '2026-11-12 17:29:51.004674', 'system:catalogue-seed', '2026-11-12 17:29:51.055328', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+15), (@bv+15), (@br+2), 1, 2, 'APPROVED', 1, 420000, 420000, 'VND', 0, '62362cbf04c02a51841a98d78baddf1e2bf2d56d21837e6a14fcfcddaf4f27b5', 'system:catalogue-seed', '2026-11-12 17:29:51.004674', 'system:catalogue-seed', '2026-11-12 17:29:51.055328', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+16), (@bv+16), (@br+3), 1, 2, 'APPROVED', 1, 520000, 520000, 'VND', 0, '5025b9087376488ee8a7fcc0085faf2951863ca2af2d138d60220f05c6c7ff7b', 'system:catalogue-seed', '2026-11-12 17:29:53.155806', 'system:catalogue-seed', '2026-11-12 17:29:53.194797', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+17), (@bv+17), (@br+3), 1, 2, 'APPROVED', 1, 520000, 520000, 'VND', 0, 'ed2fba6cb99878791997dcf065301b46245ea1462e50eb760b2bbd95984e11fa', 'system:catalogue-seed', '2026-11-12 17:29:53.155806', 'system:catalogue-seed', '2026-11-12 17:29:53.194797', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+18), (@bv+18), (@br+3), 1, 2, 'APPROVED', 1, 520000, 520000, 'VND', 0, 'cbdbc326134e5d11f3efc954c96682faa5fa04fd8094ae5b6e00d396bd8e271e', 'system:catalogue-seed', '2026-11-12 17:29:53.155806', 'system:catalogue-seed', '2026-11-12 17:29:53.194797', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+19), (@bv+19), (@br+3), 1, 2, 'APPROVED', 1, 520000, 520000, 'VND', 0, '8577dc79c40de65b97ee93ef6c7506daf1eb660ad900fd6b661e37894df98f6f', 'system:catalogue-seed', '2026-11-12 17:29:53.155806', 'system:catalogue-seed', '2026-11-12 17:29:53.194797', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+20), (@bv+20), (@br+3), 1, 2, 'APPROVED', 1, 520000, 520000, 'VND', 0, '339fe17311a70c2490b5c70f3ae1db306e24c6b78a34e2676e14e9ff3299304f', 'system:catalogue-seed', '2026-11-12 17:29:53.155806', 'system:catalogue-seed', '2026-11-12 17:29:53.194797', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+21), (@bv+21), (@br+3), 1, 2, 'APPROVED', 1, 520000, 520000, 'VND', 0, 'f2d7ab52fb907bb841d5e6a545679ae9df8ee11e24868f59ae3e366595cc6e30', 'system:catalogue-seed', '2026-11-12 17:29:53.155806', 'system:catalogue-seed', '2026-11-12 17:29:53.194797', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+22), (@bv+22), (@br+3), 1, 2, 'APPROVED', 1, 520000, 520000, 'VND', 0, 'fe448289779c6e1092245ed01d3878f13bfe07130008759c34dc8be70613a242', 'system:catalogue-seed', '2026-11-12 17:29:53.155806', 'system:catalogue-seed', '2026-11-12 17:29:53.194797', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+23), (@bv+23), (@br+3), 1, 2, 'APPROVED', 1, 520000, 520000, 'VND', 0, 'f17ffd9f6276d3049214ff3113ce83d889f2433008081cf8799e237335ea5fe4', 'system:catalogue-seed', '2026-11-12 17:29:53.155806', 'system:catalogue-seed', '2026-11-12 17:29:53.194797', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+24), (@bv+24), (@br+4), 1, 2, 'APPROVED', 1, 560000, 560000, 'VND', 0, '577bb2cb76d06315373d3f2b3fbbf7d2e4c6fe1fcfdfe06bc07af7a54535a250', 'system:catalogue-seed', '2026-11-12 17:29:55.369288', 'system:catalogue-seed', '2026-11-12 17:29:55.405193', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+25), (@bv+25), (@br+4), 1, 2, 'APPROVED', 1, 560000, 560000, 'VND', 0, '1399248644e783f0cf01363df57b7240046e9dc3f6b79e7730a924813c6e45cf', 'system:catalogue-seed', '2026-11-12 17:29:55.369288', 'system:catalogue-seed', '2026-11-12 17:29:55.405193', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+26), (@bv+26), (@br+4), 1, 2, 'APPROVED', 1, 560000, 560000, 'VND', 0, '351a76ab651f428c70136525847a5a9d32a2c620265a9f405b3c7dbe329b2a85', 'system:catalogue-seed', '2026-11-12 17:29:55.369288', 'system:catalogue-seed', '2026-11-12 17:29:55.405193', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+27), (@bv+27), (@br+4), 1, 2, 'APPROVED', 1, 560000, 560000, 'VND', 0, '3a24128d35a19bfb87fd73b48008537adb1276ed0690fe63e7a4274936b0f4e3', 'system:catalogue-seed', '2026-11-12 17:29:55.369288', 'system:catalogue-seed', '2026-11-12 17:29:55.405193', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+28), (@bv+28), (@br+4), 1, 2, 'APPROVED', 1, 560000, 560000, 'VND', 0, '89057b8c41c2bcf58dc410188ed4bbe99a0b876f6ffb6fe824c720ff80b59f80', 'system:catalogue-seed', '2026-11-12 17:29:55.369288', 'system:catalogue-seed', '2026-11-12 17:29:55.405193', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+29), (@bv+29), (@br+4), 1, 2, 'APPROVED', 1, 560000, 560000, 'VND', 0, '28d00702f9966172e9210042b9fd5903cd8a34a5278161c52c6b50a0e28e8751', 'system:catalogue-seed', '2026-11-12 17:29:55.369288', 'system:catalogue-seed', '2026-11-12 17:29:55.405193', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+30), (@bv+30), (@br+4), 1, 2, 'APPROVED', 1, 560000, 560000, 'VND', 0, '24697d9c5dae67faddecfaf043fa6cb8d88c3bba835ec776e9467430ef93bdd6', 'system:catalogue-seed', '2026-11-12 17:29:55.369288', 'system:catalogue-seed', '2026-11-12 17:29:55.405193', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+31), (@bv+31), (@br+4), 1, 2, 'APPROVED', 1, 560000, 560000, 'VND', 0, 'bb5286714e814616f5dd513fdad38b28f5039ce3c13a825c081c5881e2eaa302', 'system:catalogue-seed', '2026-11-12 17:29:55.369288', 'system:catalogue-seed', '2026-11-12 17:29:55.405193', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+32), (@bv+32), (@br+5), 1, 2, 'APPROVED', 1, 540000, 540000, 'VND', 0, '55cec2dbdca86d8b7fdef05bf13d11b8b7c16ecd19f7791595e399f34785c6f3', 'system:catalogue-seed', '2026-11-12 17:29:57.477031', 'system:catalogue-seed', '2026-11-12 17:29:57.521565', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+33), (@bv+33), (@br+5), 1, 2, 'APPROVED', 1, 540000, 540000, 'VND', 0, 'a73a7f74770f8aea3ea1e71086d75372116272238432dac5a07b18ede62f1ff2', 'system:catalogue-seed', '2026-11-12 17:29:57.477031', 'system:catalogue-seed', '2026-11-12 17:29:57.521565', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+34), (@bv+34), (@br+5), 1, 2, 'APPROVED', 1, 540000, 540000, 'VND', 0, '264725c26b38bac2131166f9b195a265dd06c073d3d4fb1d3101de5826abfc76', 'system:catalogue-seed', '2026-11-12 17:29:57.477031', 'system:catalogue-seed', '2026-11-12 17:29:57.521565', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+35), (@bv+35), (@br+5), 1, 2, 'APPROVED', 1, 540000, 540000, 'VND', 0, '708e7bf96921c9b7f069a9973e6d614ea4e6c98679a047c5c3aa1b0e4b2b1519', 'system:catalogue-seed', '2026-11-12 17:29:57.477031', 'system:catalogue-seed', '2026-11-12 17:29:57.521565', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+36), (@bv+36), (@br+5), 1, 2, 'APPROVED', 1, 540000, 540000, 'VND', 0, '08b610ee5c494a87116e79afde4d920abd61a32634286dfefcd1a50abe85bbfe', 'system:catalogue-seed', '2026-11-12 17:29:57.477031', 'system:catalogue-seed', '2026-11-12 17:29:57.521565', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+37), (@bv+37), (@br+5), 1, 2, 'APPROVED', 1, 540000, 540000, 'VND', 0, 'e45770aaeee4eaec4651eb72c073fffbea97ba487280aab0f5b2294ed343b772', 'system:catalogue-seed', '2026-11-12 17:29:57.477031', 'system:catalogue-seed', '2026-11-12 17:29:57.521565', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+38), (@bv+38), (@br+5), 1, 2, 'APPROVED', 1, 540000, 540000, 'VND', 0, 'b3592891e38ad361aaa8bacf9c3cc569305587a65a145028693e96d98a88beee', 'system:catalogue-seed', '2026-11-12 17:29:57.477031', 'system:catalogue-seed', '2026-11-12 17:29:57.521565', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+39), (@bv+39), (@br+5), 1, 2, 'APPROVED', 1, 540000, 540000, 'VND', 0, 'a040b326000fb4a4db44e5df71cee01d29b615f36e05441f007c5372373ae68c', 'system:catalogue-seed', '2026-11-12 17:29:57.477031', 'system:catalogue-seed', '2026-11-12 17:29:57.521565', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+40), (@bv+40), (@br+6), 1, 2, 'APPROVED', 1, 520000, 520000, 'VND', 0, 'cb610931fbe6a6f0aa2d321b318f3535769be82f1383e867326ac1a1cbf19d9f', 'system:catalogue-seed', '2026-11-12 17:29:59.688717', 'system:catalogue-seed', '2026-11-12 17:29:59.750157', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+41), (@bv+41), (@br+6), 1, 2, 'APPROVED', 1, 520000, 520000, 'VND', 0, '97b1302953a006d78e3f96125bb700a7722a0212a8123127c3224783629f8724', 'system:catalogue-seed', '2026-11-12 17:29:59.688717', 'system:catalogue-seed', '2026-11-12 17:29:59.750157', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+42), (@bv+42), (@br+6), 1, 2, 'APPROVED', 1, 520000, 520000, 'VND', 0, 'fe75dde3968b712c92834dbdf29238dd35a5571eec1ef94bc3d5855f078cf6e6', 'system:catalogue-seed', '2026-11-12 17:29:59.688717', 'system:catalogue-seed', '2026-11-12 17:29:59.750157', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+43), (@bv+43), (@br+6), 1, 2, 'APPROVED', 1, 520000, 520000, 'VND', 0, '72367a49afd40c2e8213e0d5f5fcf93fe44288c8fefa1283801b5eadaaa7b50c', 'system:catalogue-seed', '2026-11-12 17:29:59.688717', 'system:catalogue-seed', '2026-11-12 17:29:59.750157', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+44), (@bv+44), (@br+6), 1, 2, 'APPROVED', 1, 520000, 520000, 'VND', 0, '0041076e36e3891cc250e1e94f2a86d2783fa15d18e67ca90875ce4982a0ae95', 'system:catalogue-seed', '2026-11-12 17:29:59.688717', 'system:catalogue-seed', '2026-11-12 17:29:59.750157', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+45), (@bv+45), (@br+6), 1, 2, 'APPROVED', 1, 520000, 520000, 'VND', 0, '84ac8b6135f07a74aa13fa91364d761a45a7b3f2521b430becb2d447a7397598', 'system:catalogue-seed', '2026-11-12 17:29:59.688717', 'system:catalogue-seed', '2026-11-12 17:29:59.750157', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+46), (@bv+46), (@br+6), 1, 2, 'APPROVED', 1, 520000, 520000, 'VND', 0, '11e2337b9d4b0be96070a8317cb316e0ac2be4ef1094a3550a507c1d5605fca3', 'system:catalogue-seed', '2026-11-12 17:29:59.688717', 'system:catalogue-seed', '2026-11-12 17:29:59.750157', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+47), (@bv+47), (@br+6), 1, 2, 'APPROVED', 1, 520000, 520000, 'VND', 0, '2ce504dae947df95ca590c8ecd790157e87ac13b93bf987d531d79ca95ef8050', 'system:catalogue-seed', '2026-11-12 17:29:59.688717', 'system:catalogue-seed', '2026-11-12 17:29:59.750157', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+48), (@bv+48), (@br+7), 1, 2, 'APPROVED', 1, 480000, 480000, 'VND', 0, 'b1c8494fe8c7103dd04e2cb7c380370c17fdf13d1c0fdf723690ca848ca6ffe0', 'system:catalogue-seed', '2026-11-12 17:30:01.982217', 'system:catalogue-seed', '2026-11-12 17:30:02.021682', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+49), (@bv+49), (@br+7), 1, 2, 'APPROVED', 1, 480000, 480000, 'VND', 0, 'e464ecdb735df85f433b0d2ed1cadab060d1cb8674ea6fc8b3de57294851b777', 'system:catalogue-seed', '2026-11-12 17:30:01.982217', 'system:catalogue-seed', '2026-11-12 17:30:02.021682', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+50), (@bv+50), (@br+7), 1, 2, 'APPROVED', 1, 480000, 480000, 'VND', 0, 'd182a567aff26263fd78cc6d23ef3e137d0285fab170c8f39a649c188f76795d', 'system:catalogue-seed', '2026-11-12 17:30:01.982217', 'system:catalogue-seed', '2026-11-12 17:30:02.021682', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+51), (@bv+51), (@br+7), 1, 2, 'APPROVED', 1, 480000, 480000, 'VND', 0, 'c41f9ecf74d5d1f82c5fdc3035d4d4efae65802d563e99bbe476a5272958fafd', 'system:catalogue-seed', '2026-11-12 17:30:01.982217', 'system:catalogue-seed', '2026-11-12 17:30:02.021682', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+52), (@bv+52), (@br+7), 1, 2, 'APPROVED', 1, 480000, 480000, 'VND', 0, '9a10fea3acb574dd786667afc5e23cf5e1907e5367f2b837e386cda253976ba7', 'system:catalogue-seed', '2026-11-12 17:30:01.982217', 'system:catalogue-seed', '2026-11-12 17:30:02.021682', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+53), (@bv+53), (@br+7), 1, 2, 'APPROVED', 1, 480000, 480000, 'VND', 0, '65b570d63b3d59978da9c1e900b024cee4a95ea272646b249898a41b12d74b99', 'system:catalogue-seed', '2026-11-12 17:30:01.982217', 'system:catalogue-seed', '2026-11-12 17:30:02.021682', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+54), (@bv+54), (@br+7), 1, 2, 'APPROVED', 1, 480000, 480000, 'VND', 0, '8fc3ceac999c1eb984e92b185782a655b95c6f2710500ad1a32ed17040b3b163', 'system:catalogue-seed', '2026-11-12 17:30:01.982217', 'system:catalogue-seed', '2026-11-12 17:30:02.021682', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+55), (@bv+55), (@br+8), 1, 2, 'APPROVED', 1, 590000, 590000, 'VND', 0, '34732d2e3dc1cd894b4f542dd988f8e7caa03fc876cbfd9d22ee87224598ddab', 'system:catalogue-seed', '2026-11-12 17:30:04.023113', 'system:catalogue-seed', '2026-11-12 17:30:04.084610', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+56), (@bv+56), (@br+8), 1, 2, 'APPROVED', 1, 590000, 590000, 'VND', 0, '3424e959026602ec9d34e40e827a8d1751679aa5f0728b692b17421f626b39e5', 'system:catalogue-seed', '2026-11-12 17:30:04.023113', 'system:catalogue-seed', '2026-11-12 17:30:04.084610', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+57), (@bv+57), (@br+8), 1, 2, 'APPROVED', 1, 590000, 590000, 'VND', 0, '93b66d88a1a94cd8ebb73b7d2a55cd2227ea908543dcee8eff3189bdc9eb4fe5', 'system:catalogue-seed', '2026-11-12 17:30:04.023113', 'system:catalogue-seed', '2026-11-12 17:30:04.084610', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+58), (@bv+58), (@br+8), 1, 2, 'APPROVED', 1, 590000, 590000, 'VND', 0, 'adae2b91d9d2d97347c17a2d35e93f7e7818b8ebc870410f155bec002f1f5064', 'system:catalogue-seed', '2026-11-12 17:30:04.023113', 'system:catalogue-seed', '2026-11-12 17:30:04.084610', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+59), (@bv+59), (@br+8), 1, 2, 'APPROVED', 1, 590000, 590000, 'VND', 0, '8ad5440a195f6b1e9a9948ed89d87537da160c441aa3fb1fbe8bd2fec9ce07a5', 'system:catalogue-seed', '2026-11-12 17:30:04.023113', 'system:catalogue-seed', '2026-11-12 17:30:04.084610', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+60), (@bv+60), (@br+8), 1, 2, 'APPROVED', 1, 590000, 590000, 'VND', 0, '21ae7885b636c34e18ff5d9466fd37667111247b5f03a260fdf7735af58a1bd0', 'system:catalogue-seed', '2026-11-12 17:30:04.023113', 'system:catalogue-seed', '2026-11-12 17:30:04.084610', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+61), (@bv+61), (@br+8), 1, 2, 'APPROVED', 1, 590000, 590000, 'VND', 0, '4deb3e8713fd9046cd0163f913a2b4099d8fc9d35cabfd3729e2f40d473211c3', 'system:catalogue-seed', '2026-11-12 17:30:04.023113', 'system:catalogue-seed', '2026-11-12 17:30:04.084610', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+62), (@bv+62), (@br+8), 1, 2, 'APPROVED', 1, 590000, 590000, 'VND', 0, '718245bbc7452cd3fb11c14e7ba4e74656e82d3dc2312cd3241da75d7615a036', 'system:catalogue-seed', '2026-11-12 17:30:04.023113', 'system:catalogue-seed', '2026-11-12 17:30:04.084610', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+63), (@bv+63), (@br+9), 1, 2, 'APPROVED', 1, 560000, 560000, 'VND', 0, 'f20ab97ec763e10c1b67e30af89718f48abb0a8b52d237e53c80c70a03a36546', 'system:catalogue-seed', '2026-11-12 17:30:06.370295', 'system:catalogue-seed', '2026-11-12 17:30:06.419350', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+64), (@bv+64), (@br+9), 1, 2, 'APPROVED', 1, 560000, 560000, 'VND', 0, 'd51bc73be8fcfe03b39495a145bce10df831c9b9fb3894a2dd568ef655c49367', 'system:catalogue-seed', '2026-11-12 17:30:06.370295', 'system:catalogue-seed', '2026-11-12 17:30:06.419350', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+65), (@bv+65), (@br+9), 1, 2, 'APPROVED', 1, 560000, 560000, 'VND', 0, '8453b531a2230606aceb18b29bb19c02b46dac337512b2a35eaa726466aa3c5a', 'system:catalogue-seed', '2026-11-12 17:30:06.370295', 'system:catalogue-seed', '2026-11-12 17:30:06.419350', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+66), (@bv+66), (@br+9), 1, 2, 'APPROVED', 1, 560000, 560000, 'VND', 0, 'b35a49c1bbd2bc7e9928e6542e90104cca5578112ef6a0b796c6c01bdccdb599', 'system:catalogue-seed', '2026-11-12 17:30:06.370295', 'system:catalogue-seed', '2026-11-12 17:30:06.419350', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+67), (@bv+67), (@br+9), 1, 2, 'APPROVED', 1, 560000, 560000, 'VND', 0, '78b4fed4796b419a8da85ee8865014c73ec17336ab9a35ab69c92830b738bab4', 'system:catalogue-seed', '2026-11-12 17:30:06.370295', 'system:catalogue-seed', '2026-11-12 17:30:06.419350', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+68), (@bv+68), (@br+9), 1, 2, 'APPROVED', 1, 560000, 560000, 'VND', 0, '6873d6d529cd4f853de1b42fc23730ea5295256f394d8ec3f255cdd3cc4a3055', 'system:catalogue-seed', '2026-11-12 17:30:06.370295', 'system:catalogue-seed', '2026-11-12 17:30:06.419350', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+69), (@bv+69), (@br+9), 1, 2, 'APPROVED', 1, 560000, 560000, 'VND', 0, '85ec87571b665cac99eb6d59b572240ce5e233540cff0a268ad12cbd23414869', 'system:catalogue-seed', '2026-11-12 17:30:06.370295', 'system:catalogue-seed', '2026-11-12 17:30:06.419350', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+70), (@bv+70), (@br+10), 1, 2, 'APPROVED', 1, 550000, 550000, 'VND', 0, '172d6bf80388bbacd234dbeace8445b98bed1c08cb0dc82d859a25bdf7966a2d', 'system:catalogue-seed', '2026-11-12 17:30:08.794246', 'system:catalogue-seed', '2026-11-12 17:30:08.889963', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+71), (@bv+71), (@br+10), 1, 2, 'APPROVED', 1, 550000, 550000, 'VND', 0, 'b64bb30e014cecc49ef886ec4175b294df6ec070cb1873f021cbd273dbf6024a', 'system:catalogue-seed', '2026-11-12 17:30:08.794246', 'system:catalogue-seed', '2026-11-12 17:30:08.889963', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+72), (@bv+72), (@br+10), 1, 2, 'APPROVED', 1, 550000, 550000, 'VND', 0, '0cf7214033bc03c388daaa6a6cda732bc31fc3848f43039370e4fa0c64e39bb4', 'system:catalogue-seed', '2026-11-12 17:30:08.794246', 'system:catalogue-seed', '2026-11-12 17:30:08.889963', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+73), (@bv+73), (@br+10), 1, 2, 'APPROVED', 1, 550000, 550000, 'VND', 0, '978825c18b113c787b1f73dc2efb813bd3d2c71703a55eabf9c4e5c5a545390c', 'system:catalogue-seed', '2026-11-12 17:30:08.794246', 'system:catalogue-seed', '2026-11-12 17:30:08.889963', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+74), (@bv+74), (@br+10), 1, 2, 'APPROVED', 1, 550000, 550000, 'VND', 0, '869121241682795b838941be95c79778777eb3cde96e13b2823c34e9e3ab16be', 'system:catalogue-seed', '2026-11-12 17:30:08.794246', 'system:catalogue-seed', '2026-11-12 17:30:08.889963', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+75), (@bv+75), (@br+10), 1, 2, 'APPROVED', 1, 550000, 550000, 'VND', 0, 'ca0f528d646df08c2249b587b024c5ab236ae1a053968f62e54d8dcd9c0468ee', 'system:catalogue-seed', '2026-11-12 17:30:08.794246', 'system:catalogue-seed', '2026-11-12 17:30:08.889963', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+76), (@bv+76), (@br+10), 1, 2, 'APPROVED', 1, 550000, 550000, 'VND', 0, '6612dac4bdf6c00a7262f572c72f8ab29a78ce6d2c29c92e997a4abf3b54ddd1', 'system:catalogue-seed', '2026-11-12 17:30:08.794246', 'system:catalogue-seed', '2026-11-12 17:30:08.889963', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+77), (@bv+77), (@br+10), 1, 2, 'APPROVED', 1, 550000, 550000, 'VND', 0, 'a80a40e1a9395738472e44cdb03ee0849dcea682bb5c2d06c923e26c84375c8c', 'system:catalogue-seed', '2026-11-12 17:30:08.794246', 'system:catalogue-seed', '2026-11-12 17:30:08.889963', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+78), (@bv+78), (@br+11), 1, 2, 'APPROVED', 1, 690000, 690000, 'VND', 0, 'ef1b49b86f3d2745a98b61f3334092419e0efd86d8a3f3e9fdb41b1000e53dd2', 'system:catalogue-seed', '2026-11-12 17:30:11.629455', 'system:catalogue-seed', '2026-11-12 17:30:11.666282', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+79), (@bv+79), (@br+11), 1, 2, 'APPROVED', 1, 690000, 690000, 'VND', 0, '8458a69f3efb754ac2fa4ecbc3ee181eb73ae48b4f47e03958ec08df1b0b84c5', 'system:catalogue-seed', '2026-11-12 17:30:11.629455', 'system:catalogue-seed', '2026-11-12 17:30:11.666282', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+80), (@bv+80), (@br+11), 1, 2, 'APPROVED', 1, 690000, 690000, 'VND', 0, '24371f4ae311500fce28863f703d6647ab089f6b7b2fc6c50dc0dc1d3816dd18', 'system:catalogue-seed', '2026-11-12 17:30:11.629455', 'system:catalogue-seed', '2026-11-12 17:30:11.666282', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+81), (@bv+81), (@br+11), 1, 2, 'APPROVED', 1, 690000, 690000, 'VND', 0, '563657b3b6dfd6dfb50c90b38bac4d86205a6828df07fe8f00538c78cfd3d5d5', 'system:catalogue-seed', '2026-11-12 17:30:11.629455', 'system:catalogue-seed', '2026-11-12 17:30:11.666282', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+82), (@bv+82), (@br+11), 1, 2, 'APPROVED', 1, 690000, 690000, 'VND', 0, 'f167e1b3e2c4158ddf52e90712d1e31af18f8b8ae6b736e2f639c425b0002c59', 'system:catalogue-seed', '2026-11-12 17:30:11.629455', 'system:catalogue-seed', '2026-11-12 17:30:11.666282', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+83), (@bv+83), (@br+11), 1, 2, 'APPROVED', 1, 690000, 690000, 'VND', 0, 'cee802c784ae78360776d1c83494c9204428c4de11b08a488b8f742799dd5896', 'system:catalogue-seed', '2026-11-12 17:30:11.629455', 'system:catalogue-seed', '2026-11-12 17:30:11.666282', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+84), (@bv+84), (@br+11), 1, 2, 'APPROVED', 1, 690000, 690000, 'VND', 0, 'd3ba19acaf18edaa09e5df2c3ae884ff6b7d84192ef18e2e4e9c53bf6c157783', 'system:catalogue-seed', '2026-11-12 17:30:11.629455', 'system:catalogue-seed', '2026-11-12 17:30:11.666282', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+85), (@bv+85), (@br+12), 1, 2, 'APPROVED', 1, 520000, 520000, 'VND', 0, 'e03f3ba9cf2941885a4a63f002f2ab55d644b54fd3d39aa5ed19cab0fb57df1c', 'system:catalogue-seed', '2026-11-12 17:30:13.651695', 'system:catalogue-seed', '2026-11-12 17:30:13.700700', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+86), (@bv+86), (@br+12), 1, 2, 'APPROVED', 1, 520000, 520000, 'VND', 0, 'e9c0b61bfa1de000d0ac3dd0c4e50ad30eccfdb489efe5ebbff92a2443108fa5', 'system:catalogue-seed', '2026-11-12 17:30:13.651695', 'system:catalogue-seed', '2026-11-12 17:30:13.700700', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+87), (@bv+87), (@br+12), 1, 2, 'APPROVED', 1, 520000, 520000, 'VND', 0, '3cdb4056867f03ac2c370a55ccbb4ff4d4c6155e1b0f81603528b072dc3d4d5d', 'system:catalogue-seed', '2026-11-12 17:30:13.651695', 'system:catalogue-seed', '2026-11-12 17:30:13.700700', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+88), (@bv+88), (@br+12), 1, 2, 'APPROVED', 1, 520000, 520000, 'VND', 0, '2a0310a83b3abf11b67bc5b89706a16d95f0ddf8018f4d4fa644c23b58b61ad2', 'system:catalogue-seed', '2026-11-12 17:30:13.651695', 'system:catalogue-seed', '2026-11-12 17:30:13.700700', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+89), (@bv+89), (@br+12), 1, 2, 'APPROVED', 1, 520000, 520000, 'VND', 0, '86cf1c79b5c1e1b087f271b0a72ff1ad81f6d64cf7cbae306103767270564eaf', 'system:catalogue-seed', '2026-11-12 17:30:13.651695', 'system:catalogue-seed', '2026-11-12 17:30:13.700700', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+90), (@bv+90), (@br+12), 1, 2, 'APPROVED', 1, 520000, 520000, 'VND', 0, '22d0b9b5be920e2f9831fdd3bc45d85403a955f4f9fb2877deabb565a5285e61', 'system:catalogue-seed', '2026-11-12 17:30:13.651695', 'system:catalogue-seed', '2026-11-12 17:30:13.700700', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+91), (@bv+91), (@br+12), 1, 2, 'APPROVED', 1, 520000, 520000, 'VND', 0, 'b4c27ca12ff151b6c38df9bebac0b5b861111369d5079f3ebbd09ef6d300cc24', 'system:catalogue-seed', '2026-11-12 17:30:13.651695', 'system:catalogue-seed', '2026-11-12 17:30:13.700700', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+92), (@bv+92), (@br+13), 1, 2, 'APPROVED', 1, 750000, 750000, 'VND', 0, '9ccc1545006a88ac935b48ba2b66dd915dc4215797ba60cddb9f42f759247e26', 'system:catalogue-seed', '2026-11-12 17:30:18.527186', 'system:catalogue-seed', '2026-11-12 17:30:18.582163', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+93), (@bv+93), (@br+13), 1, 2, 'APPROVED', 1, 750000, 750000, 'VND', 0, '43e4d7b5939f245c78578881f712d8fc4a3f1ab8c314505ba267047631c2e7cf', 'system:catalogue-seed', '2026-11-12 17:30:18.527186', 'system:catalogue-seed', '2026-11-12 17:30:18.582163', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+94), (@bv+94), (@br+13), 1, 2, 'APPROVED', 1, 750000, 750000, 'VND', 0, '257e945dd89f597d3d50421ce0a9c68531bb9089e6f83e65da91480d386f9d70', 'system:catalogue-seed', '2026-11-12 17:30:18.527186', 'system:catalogue-seed', '2026-11-12 17:30:18.582163', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+95), (@bv+95), (@br+13), 1, 2, 'APPROVED', 1, 750000, 750000, 'VND', 0, 'd8e2c2893e9702ea47f8f32f36184c7f4819dd6f090bcc9c2972c234ae018316', 'system:catalogue-seed', '2026-11-12 17:30:18.527186', 'system:catalogue-seed', '2026-11-12 17:30:18.582163', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+96), (@bv+96), (@br+13), 1, 2, 'APPROVED', 1, 750000, 750000, 'VND', 0, '3fb354009c82f9993a65e9777e526dc341322f4d9742c28ba443fff20cbade6f', 'system:catalogue-seed', '2026-11-12 17:30:18.527186', 'system:catalogue-seed', '2026-11-12 17:30:18.582163', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+97), (@bv+97), (@br+13), 1, 2, 'APPROVED', 1, 750000, 750000, 'VND', 0, '23fe9198a6ac5a7d8fc40759db6c31f07abcfa4d9461a50aec1e7a3f5e4b4bc5', 'system:catalogue-seed', '2026-11-12 17:30:18.527186', 'system:catalogue-seed', '2026-11-12 17:30:18.582163', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+98), (@bv+98), (@br+13), 1, 2, 'APPROVED', 1, 750000, 750000, 'VND', 0, '3a43c3ccef71f021f479667187a1e8e1c803ea2ca040d11e541a69ae8b1e2031', 'system:catalogue-seed', '2026-11-12 17:30:18.527186', 'system:catalogue-seed', '2026-11-12 17:30:18.582163', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+99), (@bv+99), (@br+13), 1, 2, 'APPROVED', 1, 750000, 750000, 'VND', 0, '4bf36e8d617c596d3b49724e32c402a7a3d5da1cd32f0296c04be7a46fcc2e5c', 'system:catalogue-seed', '2026-11-12 17:30:18.527186', 'system:catalogue-seed', '2026-11-12 17:30:18.582163', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+100), (@bv+100), (@br+14), 1, 2, 'APPROVED', 1, 680000, 680000, 'VND', 0, '3dfdfbadeeadb0648750dc4213da449762adc3399cb24a9fd078b4643d39b6ff', 'system:catalogue-seed', '2026-11-12 17:30:20.895548', 'system:catalogue-seed', '2026-11-12 17:30:20.929548', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+101), (@bv+101), (@br+14), 1, 2, 'APPROVED', 1, 680000, 680000, 'VND', 0, '08bef09dced07c165e618eb68da7746abfa7e02edd8bec864ad7199d0b09849f', 'system:catalogue-seed', '2026-11-12 17:30:20.895548', 'system:catalogue-seed', '2026-11-12 17:30:20.929548', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+102), (@bv+102), (@br+14), 1, 2, 'APPROVED', 1, 680000, 680000, 'VND', 0, '32da6b6fe6e19d563a2c5e2b45b9981abe56c01dddeef8f4d85694b133d8abbf', 'system:catalogue-seed', '2026-11-12 17:30:20.895548', 'system:catalogue-seed', '2026-11-12 17:30:20.929548', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+103), (@bv+103), (@br+14), 1, 2, 'APPROVED', 1, 680000, 680000, 'VND', 0, 'b4c314f6df063011e210d5e1bf6eef96b4bc086669669e4c216b19b5273c5757', 'system:catalogue-seed', '2026-11-12 17:30:20.895548', 'system:catalogue-seed', '2026-11-12 17:30:20.929548', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+104), (@bv+104), (@br+14), 1, 2, 'APPROVED', 1, 680000, 680000, 'VND', 0, 'f2c3a46dd28fd0d61dfb71ed350e2d5c1f42d25cf465db05cd21ff51b080e039', 'system:catalogue-seed', '2026-11-12 17:30:20.895548', 'system:catalogue-seed', '2026-11-12 17:30:20.929548', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+105), (@bv+105), (@br+14), 1, 2, 'APPROVED', 1, 680000, 680000, 'VND', 0, '2fadce6f3e1fcc94b8bfe5ce7f46cf90ecea516008c106dd7fb9cb403e379e6c', 'system:catalogue-seed', '2026-11-12 17:30:20.895548', 'system:catalogue-seed', '2026-11-12 17:30:20.929548', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+106), (@bv+106), (@br+14), 1, 2, 'APPROVED', 1, 680000, 680000, 'VND', 0, 'a137b54678615fb9a930ed91dae62b1ebb55b7aad59505c1977c20beebc946f1', 'system:catalogue-seed', '2026-11-12 17:30:20.895548', 'system:catalogue-seed', '2026-11-12 17:30:20.929548', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+107), (@bv+107), (@br+14), 1, 2, 'APPROVED', 1, 680000, 680000, 'VND', 0, '6b16e66d8078ac1f10ac5b6920ea1a735e6c41aaff9ba809f534b1061d1235a0', 'system:catalogue-seed', '2026-11-12 17:30:20.895548', 'system:catalogue-seed', '2026-11-12 17:30:20.929548', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+108), (@bv+108), (@br+15), 1, 2, 'APPROVED', 1, 720000, 720000, 'VND', 0, 'c5a5d97aed0bc7c55a224599d8dc787dfad01659d5077cdcbdb16b8e0009df30', 'system:catalogue-seed', '2026-11-12 17:30:23.079618', 'system:catalogue-seed', '2026-11-12 17:30:23.109995', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+109), (@bv+109), (@br+15), 1, 2, 'APPROVED', 1, 720000, 720000, 'VND', 0, 'f5c6d2d21c7092f154f6c6950e25f0e9c499ebf8ea7f6b50af94c70880002213', 'system:catalogue-seed', '2026-11-12 17:30:23.079618', 'system:catalogue-seed', '2026-11-12 17:30:23.109995', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+110), (@bv+110), (@br+15), 1, 2, 'APPROVED', 1, 720000, 720000, 'VND', 0, '6dcf9034b10e6733fec0babde26f6919666f72b62d3204bc980560a5a037c86a', 'system:catalogue-seed', '2026-11-12 17:30:23.079618', 'system:catalogue-seed', '2026-11-12 17:30:23.109995', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+111), (@bv+111), (@br+15), 1, 2, 'APPROVED', 1, 720000, 720000, 'VND', 0, '065fd23f4670956fec3f5232ba0489fce7ae0dcb1b80577a94edad439792f85d', 'system:catalogue-seed', '2026-11-12 17:30:23.079618', 'system:catalogue-seed', '2026-11-12 17:30:23.109995', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+112), (@bv+112), (@br+15), 1, 2, 'APPROVED', 1, 720000, 720000, 'VND', 0, '4c8fae696850d30d2479e91dcbc7badd546bc6654fe2ccbe2cd4d7ededbdbe8a', 'system:catalogue-seed', '2026-11-12 17:30:23.079618', 'system:catalogue-seed', '2026-11-12 17:30:23.109995', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+113), (@bv+113), (@br+15), 1, 2, 'APPROVED', 1, 720000, 720000, 'VND', 0, '4f60d926f6fad632297c8551bf8b4f4047ae94b957f3830cd4741bcfbff197f7', 'system:catalogue-seed', '2026-11-12 17:30:23.079618', 'system:catalogue-seed', '2026-11-12 17:30:23.109995', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+114), (@bv+114), (@br+15), 1, 2, 'APPROVED', 1, 720000, 720000, 'VND', 0, '59ffa3baae1c1050c57cc0c902effc5dece4afe308799a40314e6d4399b21367', 'system:catalogue-seed', '2026-11-12 17:30:23.079618', 'system:catalogue-seed', '2026-11-12 17:30:23.109995', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+115), (@bv+115), (@br+15), 1, 2, 'APPROVED', 1, 720000, 720000, 'VND', 0, '4425b325a683d8ea58352c798c5c4b1fefa6b7fd1e89519494738c1ae17578eb', 'system:catalogue-seed', '2026-11-12 17:30:23.079618', 'system:catalogue-seed', '2026-11-12 17:30:23.109995', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+116), (@bv+116), (@br+16), 1, 2, 'APPROVED', 1, 680000, 680000, 'VND', 0, '730943d243f522a80635a79e83ce71106847d4598d9aec6f725626f32ed23fef', 'system:catalogue-seed', '2026-11-12 17:30:25.183008', 'system:catalogue-seed', '2026-11-12 17:30:25.215030', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+117), (@bv+117), (@br+16), 1, 2, 'APPROVED', 1, 680000, 680000, 'VND', 0, 'dedd8901a2bbab09b4b00abfbed1fab40ca051ad346dddc93eb02b458535409b', 'system:catalogue-seed', '2026-11-12 17:30:25.183008', 'system:catalogue-seed', '2026-11-12 17:30:25.215030', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+118), (@bv+118), (@br+16), 1, 2, 'APPROVED', 1, 680000, 680000, 'VND', 0, 'b99e013728040af343f4cb4702117024a54ab6e35b9318421c11338f354e7594', 'system:catalogue-seed', '2026-11-12 17:30:25.183008', 'system:catalogue-seed', '2026-11-12 17:30:25.215030', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+119), (@bv+119), (@br+16), 1, 2, 'APPROVED', 1, 680000, 680000, 'VND', 0, '1b7f4ebf8ceb1b9b1aa847dc11d0ff17404c808dcfdc5ad2c57561a78363a2c9', 'system:catalogue-seed', '2026-11-12 17:30:25.183008', 'system:catalogue-seed', '2026-11-12 17:30:25.215030', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+120), (@bv+120), (@br+16), 1, 2, 'APPROVED', 1, 680000, 680000, 'VND', 0, '2c40a896941b47b78932b55c351a4c82bdc1a1b8f8ee308bf358ed30cd1da539', 'system:catalogue-seed', '2026-11-12 17:30:25.183008', 'system:catalogue-seed', '2026-11-12 17:30:25.215030', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+121), (@bv+121), (@br+16), 1, 2, 'APPROVED', 1, 680000, 680000, 'VND', 0, 'e100fa7e494ead8eb722dfc95ebc912823e6ec381bad8e352020fda77cd2588c', 'system:catalogue-seed', '2026-11-12 17:30:25.183008', 'system:catalogue-seed', '2026-11-12 17:30:25.215030', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+122), (@bv+122), (@br+16), 1, 2, 'APPROVED', 1, 680000, 680000, 'VND', 0, '5b18ab11da1c64d52b338c7ee4d29657e3a65d82453e4ca8c64dd167425ee420', 'system:catalogue-seed', '2026-11-12 17:30:25.183008', 'system:catalogue-seed', '2026-11-12 17:30:25.215030', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+123), (@bv+123), (@br+16), 1, 2, 'APPROVED', 1, 680000, 680000, 'VND', 0, '5a939ead28f0d9a22340ff74e3ab689c85cab9ebc46c91a10e7e5914b514c05d', 'system:catalogue-seed', '2026-11-12 17:30:25.183008', 'system:catalogue-seed', '2026-11-12 17:30:25.215030', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+124), (@bv+124), (@br+17), 1, 2, 'APPROVED', 1, 620000, 620000, 'VND', 0, 'f2fba4157f0e49825d18c7f7c04f483b2468681b5e8a9fe6a5676c3897e34847', 'system:catalogue-seed', '2026-11-12 17:30:27.313676', 'system:catalogue-seed', '2026-11-12 17:30:27.346827', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+125), (@bv+125), (@br+17), 1, 2, 'APPROVED', 1, 620000, 620000, 'VND', 0, '196830f518bfdc427823f761b01993848cd65a01a95c89595f1696f882c8bba3', 'system:catalogue-seed', '2026-11-12 17:30:27.313676', 'system:catalogue-seed', '2026-11-12 17:30:27.346827', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+126), (@bv+126), (@br+17), 1, 2, 'APPROVED', 1, 620000, 620000, 'VND', 0, '2b889923e4fa75d0e497f5307c9027aca4be43a38659ec8a594ce018b2e30dec', 'system:catalogue-seed', '2026-11-12 17:30:27.313676', 'system:catalogue-seed', '2026-11-12 17:30:27.346827', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+127), (@bv+127), (@br+17), 1, 2, 'APPROVED', 1, 620000, 620000, 'VND', 0, '517826cb07dc6247268e26cc7a344ca8778bacf8a482f9d18d50364bf3194b62', 'system:catalogue-seed', '2026-11-12 17:30:27.313676', 'system:catalogue-seed', '2026-11-12 17:30:27.346827', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+128), (@bv+128), (@br+17), 1, 2, 'APPROVED', 1, 620000, 620000, 'VND', 0, '641d7ebcaa4f4e70f5cde980c3460eb476c034a508edc8b949b820c7855b0fb0', 'system:catalogue-seed', '2026-11-12 17:30:27.313676', 'system:catalogue-seed', '2026-11-12 17:30:27.346827', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+129), (@bv+129), (@br+17), 1, 2, 'APPROVED', 1, 620000, 620000, 'VND', 0, '5433b3f9ecb07e62187f24c04afa42c50855eb82da734dfafe4b65478669cf52', 'system:catalogue-seed', '2026-11-12 17:30:27.313676', 'system:catalogue-seed', '2026-11-12 17:30:27.346827', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+130), (@bv+130), (@br+17), 1, 2, 'APPROVED', 1, 620000, 620000, 'VND', 0, 'f20a30c361b30ceac4ffb853ce4c24f19a9980bc93034dba9332ccb66944b70f', 'system:catalogue-seed', '2026-11-12 17:30:27.313676', 'system:catalogue-seed', '2026-11-12 17:30:27.346827', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+131), (@bv+131), (@br+17), 1, 2, 'APPROVED', 1, 620000, 620000, 'VND', 0, 'ea8653c7e6cb29894c4de94fd1796e7668a31c4ee69ba890d4a2f56dcf4ad32f', 'system:catalogue-seed', '2026-11-12 17:30:27.313676', 'system:catalogue-seed', '2026-11-12 17:30:27.346827', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+132), (@bv+132), (@br+18), 1, 2, 'APPROVED', 1, 650000, 650000, 'VND', 0, 'ce7858dc463b2435fad3284f6b0afe59a6315a06d1d36d2e346317e349bd2695', 'system:catalogue-seed', '2026-11-12 17:30:29.329362', 'system:catalogue-seed', '2026-11-12 17:30:29.360400', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+133), (@bv+133), (@br+18), 1, 2, 'APPROVED', 1, 650000, 650000, 'VND', 0, '187112b4d377159c54317a34729e72745135b46429a0808f75db136e37039270', 'system:catalogue-seed', '2026-11-12 17:30:29.329362', 'system:catalogue-seed', '2026-11-12 17:30:29.360400', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+134), (@bv+134), (@br+18), 1, 2, 'APPROVED', 1, 650000, 650000, 'VND', 0, 'b11654696a3c562694fa86027e712ae0d8bf412d3b650a4e4bcd831ecf820005', 'system:catalogue-seed', '2026-11-12 17:30:29.329362', 'system:catalogue-seed', '2026-11-12 17:30:29.360400', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+135), (@bv+135), (@br+18), 1, 2, 'APPROVED', 1, 650000, 650000, 'VND', 0, 'bd796bddf1d8538d61f178fd78b909c836dbce3ee61fb9c7cb6b11a7b3e4e94a', 'system:catalogue-seed', '2026-11-12 17:30:29.329362', 'system:catalogue-seed', '2026-11-12 17:30:29.360400', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+136), (@bv+136), (@br+18), 1, 2, 'APPROVED', 1, 650000, 650000, 'VND', 0, '8b81337a4ca398da24a5af1386f3c3243c9c90e175a4cec77a139919d0bbbf6c', 'system:catalogue-seed', '2026-11-12 17:30:29.329362', 'system:catalogue-seed', '2026-11-12 17:30:29.360400', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+137), (@bv+137), (@br+18), 1, 2, 'APPROVED', 1, 650000, 650000, 'VND', 0, 'b985ab2c0b050dbb55bb9cebdcda2e3d784e9abf2030358c11542f390ede9906', 'system:catalogue-seed', '2026-11-12 17:30:29.329362', 'system:catalogue-seed', '2026-11-12 17:30:29.360400', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+138), (@bv+138), (@br+18), 1, 2, 'APPROVED', 1, 650000, 650000, 'VND', 0, '327250535eb87c6256efe14037a5592c1f8813067c3f4474c34b8d2c212df323', 'system:catalogue-seed', '2026-11-12 17:30:29.329362', 'system:catalogue-seed', '2026-11-12 17:30:29.360400', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+139), (@bv+139), (@br+18), 1, 2, 'APPROVED', 1, 650000, 650000, 'VND', 0, '021e97637e7d3d6dae9aca1ec25dac07b84e51d539b3fd58c56ccf561f89cccc', 'system:catalogue-seed', '2026-11-12 17:30:29.329362', 'system:catalogue-seed', '2026-11-12 17:30:29.360400', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+140), (@bv+140), (@br+19), 1, 2, 'APPROVED', 1, 480000, 480000, 'VND', 0, '6f0206994220249e23eaca5e021e5ed7551ed91c213fc60e9af8988bd9147069', 'system:catalogue-seed', '2026-11-12 17:30:31.437050', 'system:catalogue-seed', '2026-11-12 17:30:31.474241', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+141), (@bv+141), (@br+19), 1, 2, 'APPROVED', 1, 480000, 480000, 'VND', 0, '67b1625e2fea10291cccb823d8c1d81b558dacf90efde217a34894d916e8dd94', 'system:catalogue-seed', '2026-11-12 17:30:31.437050', 'system:catalogue-seed', '2026-11-12 17:30:31.474241', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+142), (@bv+142), (@br+19), 1, 2, 'APPROVED', 1, 480000, 480000, 'VND', 0, '49568c04ce63613d8df4472cdb8dd7de766a03b97eef3b0a861da02e8f1d14da', 'system:catalogue-seed', '2026-11-12 17:30:31.437050', 'system:catalogue-seed', '2026-11-12 17:30:31.474241', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+143), (@bv+143), (@br+19), 1, 2, 'APPROVED', 1, 480000, 480000, 'VND', 0, 'd70d2d20c7e6fec216ba536cd7814cfb5fab8a66587a3f30be7025eab523007b', 'system:catalogue-seed', '2026-11-12 17:30:31.437050', 'system:catalogue-seed', '2026-11-12 17:30:31.474241', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+144), (@bv+144), (@br+19), 1, 2, 'APPROVED', 1, 480000, 480000, 'VND', 0, '1bedb4c20f501196760db1344248c325e7117815e3b2780d207cfc3e75ab0ce6', 'system:catalogue-seed', '2026-11-12 17:30:31.437050', 'system:catalogue-seed', '2026-11-12 17:30:31.474241', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+145), (@bv+145), (@br+19), 1, 2, 'APPROVED', 1, 480000, 480000, 'VND', 0, '64c0d329f5ccd7638e061c6d483dd8fb589b4370ba0756bb6bbe9563d3133449', 'system:catalogue-seed', '2026-11-12 17:30:31.437050', 'system:catalogue-seed', '2026-11-12 17:30:31.474241', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+146), (@bv+146), (@br+19), 1, 2, 'APPROVED', 1, 480000, 480000, 'VND', 0, 'f01b5e027eba44fdfb610e1fd2a7496a57280f3353cb1956e8be0b00dbf2c309', 'system:catalogue-seed', '2026-11-12 17:30:31.437050', 'system:catalogue-seed', '2026-11-12 17:30:31.474241', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+147), (@bv+147), (@br+19), 1, 2, 'APPROVED', 1, 480000, 480000, 'VND', 0, 'a9bc96233d94dbb2db774f4766f720d5ed375d167a041dc3ea95393474eefe42', 'system:catalogue-seed', '2026-11-12 17:30:31.437050', 'system:catalogue-seed', '2026-11-12 17:30:31.474241', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+148), (@bv+148), (@br+20), 1, 2, 'APPROVED', 1, 590000, 590000, 'VND', 0, '9728d03f1ea359ce57c65de7258e4b7a27a3082aa69f35cd4f90f3842a5adc8a', 'system:catalogue-seed', '2026-11-12 17:30:35.318110', 'system:catalogue-seed', '2026-11-12 17:30:35.345660', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+149), (@bv+149), (@br+20), 1, 2, 'APPROVED', 1, 590000, 590000, 'VND', 0, 'd88f8eee0a92d56c5af31ff3c1f2bb3f60b821e2bb334ee8f9c0f7063a0617a7', 'system:catalogue-seed', '2026-11-12 17:30:35.318110', 'system:catalogue-seed', '2026-11-12 17:30:35.345660', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+150), (@bv+150), (@br+20), 1, 2, 'APPROVED', 1, 590000, 590000, 'VND', 0, 'c1970a53103e25656254d407ec2c8e539f8669abe7f6f893a0f0c114ece6bcd9', 'system:catalogue-seed', '2026-11-12 17:30:35.318110', 'system:catalogue-seed', '2026-11-12 17:30:35.345660', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+151), (@bv+151), (@br+20), 1, 2, 'APPROVED', 1, 590000, 590000, 'VND', 0, 'cfdd70370b6dde72c0260c0ac90f3cec2e967ef2faa10769e51aafe47a9803b7', 'system:catalogue-seed', '2026-11-12 17:30:35.318110', 'system:catalogue-seed', '2026-11-12 17:30:35.345660', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+152), (@bv+152), (@br+20), 1, 2, 'APPROVED', 1, 590000, 590000, 'VND', 0, 'dcdb6822a02dd2897419d2161d6f34601699ae58828a2ad7b6162534a65436e2', 'system:catalogue-seed', '2026-11-12 17:30:35.318110', 'system:catalogue-seed', '2026-11-12 17:30:35.345660', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+153), (@bv+153), (@br+20), 1, 2, 'APPROVED', 1, 590000, 590000, 'VND', 0, '55326ecdf4c38ce5ebd9b3756615cd686d73a4f115713dbdc1f1f89e4355fff4', 'system:catalogue-seed', '2026-11-12 17:30:35.318110', 'system:catalogue-seed', '2026-11-12 17:30:35.345660', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+154), (@bv+154), (@br+20), 1, 2, 'APPROVED', 1, 590000, 590000, 'VND', 0, '2897327b3e16815dab0fc0b1a951ea39108de561be9984584edeac5dd35ac06b', 'system:catalogue-seed', '2026-11-12 17:30:35.318110', 'system:catalogue-seed', '2026-11-12 17:30:35.345660', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+155), (@bv+155), (@br+21), 1, 2, 'APPROVED', 1, 640000, 640000, 'VND', 0, 'f307638d85820672a94397436f6d4639d578f0ab2795675b7f26f0ed8182ca7d', 'system:catalogue-seed', '2026-11-12 17:30:37.572893', 'system:catalogue-seed', '2026-11-12 17:30:37.608883', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+156), (@bv+156), (@br+21), 1, 2, 'APPROVED', 1, 640000, 640000, 'VND', 0, '011af4b478627c70ca331099e3d5b1d77cb7f4abb03e991dd0e9f253480aedb6', 'system:catalogue-seed', '2026-11-12 17:30:37.572893', 'system:catalogue-seed', '2026-11-12 17:30:37.608883', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+157), (@bv+157), (@br+21), 1, 2, 'APPROVED', 1, 640000, 640000, 'VND', 0, 'e14b7108d5933f78cdf44787ce1d0b428edb84d3d3817c756bdfd93b1adbf06a', 'system:catalogue-seed', '2026-11-12 17:30:37.572893', 'system:catalogue-seed', '2026-11-12 17:30:37.608883', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+158), (@bv+158), (@br+21), 1, 2, 'APPROVED', 1, 640000, 640000, 'VND', 0, 'a5f2974064439463fce91a0438e377705cde3bd9e04aca06275d950472557ed0', 'system:catalogue-seed', '2026-11-12 17:30:37.572893', 'system:catalogue-seed', '2026-11-12 17:30:37.608883', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+159), (@bv+159), (@br+21), 1, 2, 'APPROVED', 1, 640000, 640000, 'VND', 0, 'b01d9fa2f966994913f2524d870b77ca89582ef058cc81cc8eeea502388da452', 'system:catalogue-seed', '2026-11-12 17:30:37.572893', 'system:catalogue-seed', '2026-11-12 17:30:37.608883', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+160), (@bv+160), (@br+21), 1, 2, 'APPROVED', 1, 640000, 640000, 'VND', 0, '512d13b1ba2234b98ac6c82606082aec61b871474bd85fab7e65e5fc56de8704', 'system:catalogue-seed', '2026-11-12 17:30:37.572893', 'system:catalogue-seed', '2026-11-12 17:30:37.608883', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+161), (@bv+161), (@br+21), 1, 2, 'APPROVED', 1, 640000, 640000, 'VND', 0, '9392effb76eaefd86a1e2a8286848e2d3a373a20824eb77affdfa46591cf6f9f', 'system:catalogue-seed', '2026-11-12 17:30:37.572893', 'system:catalogue-seed', '2026-11-12 17:30:37.608883', NULL, NULL FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+162), (@bv+161), NULL, 2, 2, 'APPROVED', 1, 700000, 700000, 'VND', 1, '873fe2afbdf8c641ffdc35a03164b2406c02d57b3077525fca5379b637ca107c', '5', '2026-11-12 17:40:58.094548', '5', '2026-11-12 17:41:05.835572', NULL, (@bpp+161) FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+163), (@bv+154), NULL, 2, 2, 'APPROVED', 1, 600000, 600000, 'VND', 1, '88ad7c29b1f81412c248d0f577b5b5038eafba7759a882cd170a207bb6edec17', '5', '2026-11-12 17:41:46.106062', '5', '2026-11-12 17:41:48.902505', NULL, (@bpp+154) FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+164), (@bv+152), NULL, 2, 2, 'APPROVED', 1, 650000, 650000, 'VND', 1, 'b6eada4ed03eed134daed37ffd9ac18d544a118dbcab569e421355814eaa4506', '5', '2026-11-12 17:43:31.312205', '5', '2026-11-12 17:43:38.583269', NULL, (@bpp+152) FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+165), (@bv+153), NULL, 2, 2, 'APPROVED', 1, 650000, 650000, 'VND', 1, '7123648c6ebd08c2fd1dc4e80cfc4d8b425c260c54b8146cd81bcc7b879e2709', '5', '2026-11-12 17:43:32.016372', '5', '2026-11-12 17:43:39.310125', NULL, (@bpp+153) FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+166), (@bv+154), NULL, 3, 2, 'APPROVED', 1, 650000, 650000, 'VND', 2, '88ad7c29b1f81412c248d0f577b5b5038eafba7759a882cd170a207bb6edec17', '5', '2026-11-12 17:43:32.702125', '5', '2026-11-12 17:43:40.162836', NULL, (@bpp+163) FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+167), (@bv+143), NULL, 2, 2, 'APPROVED', 1, 500000, 500000, 'VND', 1, 'a53037d253e52c9f2b7ba840fd0b17e3e14947746c3d4aa6baf49ecba784aac3', '5', '2026-11-12 17:44:18.199897', '5', '2026-11-12 17:44:27.789172', NULL, (@bpp+143) FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+168), (@bv+144), NULL, 2, 2, 'APPROVED', 1, 500000, 500000, 'VND', 1, '18d2f2ed568bf8db6d7e1602030a7bec3a74fc352d6e1b8b320311b301a4002f', '5', '2026-11-12 17:44:19.120819', '5', '2026-11-12 17:44:28.458391', NULL, (@bpp+144) FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+169), (@bv+145), NULL, 2, 2, 'APPROVED', 1, 500000, 500000, 'VND', 1, '07101000c5dedd9ef61b90a4d5d1b036233b428a5d54ce85ee6cd8dd12ee2ee7', '5', '2026-11-12 17:44:19.796438', '5', '2026-11-12 17:44:29.110448', NULL, (@bpp+145) FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+170), (@bv+146), NULL, 2, 2, 'APPROVED', 1, 500000, 500000, 'VND', 1, '61af82f8c06c1208da29c373025c8f6457f397be7c454782559d8b720b0aea50', '5', '2026-11-12 17:44:20.645796', '5', '2026-11-12 17:44:30.164343', NULL, (@bpp+146) FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+171), (@bv+147), NULL, 2, 2, 'APPROVED', 1, 500000, 500000, 'VND', 1, '8fd23c726d9be0fc57541904efe24cd319d6167dd1ef006e0ad8933cf2a3734f', '5', '2026-11-12 17:44:21.360830', '5', '2026-11-12 17:44:30.892869', NULL, (@bpp+147) FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+172), (@bv+67), NULL, 2, 2, 'APPROVED', 1, 650000, 650000, 'VND', 1, 'daf4e74dcfcd17c42741f8e51133ba49a8b7bc330ed18c4b596c348b33e30040', '5', '2026-11-12 17:52:04.734010', '5', '2026-11-12 17:52:16.506690', NULL, (@bpp+67) FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+173), (@bv+68), NULL, 2, 2, 'APPROVED', 1, 650000, 650000, 'VND', 1, '8732a494e726d23ef794e2f7ef6cab4c89c3b1d5925f484f7a69725e2e27ba30', '5', '2026-11-12 17:52:05.494013', '5', '2026-11-12 17:52:17.139475', NULL, (@bpp+68) FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_proposals` (`id`, `variant_id`, `review_id`, `revision`, `version`, `state`, `effective`, `proposed_amount`, `approved_amount`, `currency`, `base_applied_sequence`, `terms_digest`, `submitted_by`, `submitted_at`, `decided_by`, `decided_at`, `reason`, `supersedes_id`)
SELECT (@bpp+174), (@bv+69), NULL, 2, 2, 'APPROVED', 1, 650000, 650000, 'VND', 1, 'adc104a0646cef8c77ace553cbefdebbdd12245e3346dd29cabf86234ff57cc9', '5', '2026-11-12 17:52:07.612729', '5', '2026-11-12 17:52:17.774524', NULL, (@bpp+69) FROM DUAL WHERE @run_catalogue;

-- Applied prices
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+1), (@bv+1), (@bpp+1), 1, NULL, 390000, 'system:catalogue-seed', '2026-11-12 17:29:48.616400' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+2), (@bv+2), (@bpp+2), 1, NULL, 390000, 'system:catalogue-seed', '2026-11-12 17:29:48.672881' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+3), (@bv+3), (@bpp+3), 1, NULL, 390000, 'system:catalogue-seed', '2026-11-12 17:29:48.721337' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+4), (@bv+4), (@bpp+4), 1, NULL, 390000, 'system:catalogue-seed', '2026-11-12 17:29:48.775427' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+5), (@bv+5), (@bpp+5), 1, NULL, 390000, 'system:catalogue-seed', '2026-11-12 17:29:48.860053' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+6), (@bv+6), (@bpp+6), 1, NULL, 390000, 'system:catalogue-seed', '2026-11-12 17:29:48.912720' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+7), (@bv+7), (@bpp+7), 1, NULL, 390000, 'system:catalogue-seed', '2026-11-12 17:29:48.978900' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+8), (@bv+8), (@bpp+8), 1, NULL, 390000, 'system:catalogue-seed', '2026-11-12 17:29:49.039755' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+9), (@bv+9), (@bpp+9), 1, NULL, 420000, 'system:catalogue-seed', '2026-11-12 17:29:51.104417' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+10), (@bv+10), (@bpp+10), 1, NULL, 420000, 'system:catalogue-seed', '2026-11-12 17:29:51.137533' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+11), (@bv+11), (@bpp+11), 1, NULL, 420000, 'system:catalogue-seed', '2026-11-12 17:29:51.168079' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+12), (@bv+12), (@bpp+12), 1, NULL, 420000, 'system:catalogue-seed', '2026-11-12 17:29:51.195309' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+13), (@bv+13), (@bpp+13), 1, NULL, 420000, 'system:catalogue-seed', '2026-11-12 17:29:51.224766' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+14), (@bv+14), (@bpp+14), 1, NULL, 420000, 'system:catalogue-seed', '2026-11-12 17:29:51.254574' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+15), (@bv+15), (@bpp+15), 1, NULL, 420000, 'system:catalogue-seed', '2026-11-12 17:29:51.278551' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+16), (@bv+16), (@bpp+16), 1, NULL, 520000, 'system:catalogue-seed', '2026-11-12 17:29:53.232460' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+17), (@bv+17), (@bpp+17), 1, NULL, 520000, 'system:catalogue-seed', '2026-11-12 17:29:53.257292' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+18), (@bv+18), (@bpp+18), 1, NULL, 520000, 'system:catalogue-seed', '2026-11-12 17:29:53.289009' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+19), (@bv+19), (@bpp+19), 1, NULL, 520000, 'system:catalogue-seed', '2026-11-12 17:29:53.319601' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+20), (@bv+20), (@bpp+20), 1, NULL, 520000, 'system:catalogue-seed', '2026-11-12 17:29:53.344888' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+21), (@bv+21), (@bpp+21), 1, NULL, 520000, 'system:catalogue-seed', '2026-11-12 17:29:53.374893' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+22), (@bv+22), (@bpp+22), 1, NULL, 520000, 'system:catalogue-seed', '2026-11-12 17:29:53.400903' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+23), (@bv+23), (@bpp+23), 1, NULL, 520000, 'system:catalogue-seed', '2026-11-12 17:29:53.432407' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+24), (@bv+24), (@bpp+24), 1, NULL, 560000, 'system:catalogue-seed', '2026-11-12 17:29:55.436891' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+25), (@bv+25), (@bpp+25), 1, NULL, 560000, 'system:catalogue-seed', '2026-11-12 17:29:55.465998' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+26), (@bv+26), (@bpp+26), 1, NULL, 560000, 'system:catalogue-seed', '2026-11-12 17:29:55.494671' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+27), (@bv+27), (@bpp+27), 1, NULL, 560000, 'system:catalogue-seed', '2026-11-12 17:29:55.522030' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+28), (@bv+28), (@bpp+28), 1, NULL, 560000, 'system:catalogue-seed', '2026-11-12 17:29:55.547728' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+29), (@bv+29), (@bpp+29), 1, NULL, 560000, 'system:catalogue-seed', '2026-11-12 17:29:55.571637' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+30), (@bv+30), (@bpp+30), 1, NULL, 560000, 'system:catalogue-seed', '2026-11-12 17:29:55.595546' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+31), (@bv+31), (@bpp+31), 1, NULL, 560000, 'system:catalogue-seed', '2026-11-12 17:29:55.624541' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+32), (@bv+32), (@bpp+32), 1, NULL, 540000, 'system:catalogue-seed', '2026-11-12 17:29:57.556178' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+33), (@bv+33), (@bpp+33), 1, NULL, 540000, 'system:catalogue-seed', '2026-11-12 17:29:57.586336' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+34), (@bv+34), (@bpp+34), 1, NULL, 540000, 'system:catalogue-seed', '2026-11-12 17:29:57.604318' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+35), (@bv+35), (@bpp+35), 1, NULL, 540000, 'system:catalogue-seed', '2026-11-12 17:29:57.623638' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+36), (@bv+36), (@bpp+36), 1, NULL, 540000, 'system:catalogue-seed', '2026-11-12 17:29:57.648530' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+37), (@bv+37), (@bpp+37), 1, NULL, 540000, 'system:catalogue-seed', '2026-11-12 17:29:57.669755' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+38), (@bv+38), (@bpp+38), 1, NULL, 540000, 'system:catalogue-seed', '2026-11-12 17:29:57.688574' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+39), (@bv+39), (@bpp+39), 1, NULL, 540000, 'system:catalogue-seed', '2026-11-12 17:29:57.715198' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+40), (@bv+40), (@bpp+40), 1, NULL, 520000, 'system:catalogue-seed', '2026-11-12 17:29:59.792331' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+41), (@bv+41), (@bpp+41), 1, NULL, 520000, 'system:catalogue-seed', '2026-11-12 17:29:59.815733' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+42), (@bv+42), (@bpp+42), 1, NULL, 520000, 'system:catalogue-seed', '2026-11-12 17:29:59.835231' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+43), (@bv+43), (@bpp+43), 1, NULL, 520000, 'system:catalogue-seed', '2026-11-12 17:29:59.857348' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+44), (@bv+44), (@bpp+44), 1, NULL, 520000, 'system:catalogue-seed', '2026-11-12 17:29:59.874864' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+45), (@bv+45), (@bpp+45), 1, NULL, 520000, 'system:catalogue-seed', '2026-11-12 17:29:59.890825' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+46), (@bv+46), (@bpp+46), 1, NULL, 520000, 'system:catalogue-seed', '2026-11-12 17:29:59.912827' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+47), (@bv+47), (@bpp+47), 1, NULL, 520000, 'system:catalogue-seed', '2026-11-12 17:29:59.932909' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+48), (@bv+48), (@bpp+48), 1, NULL, 480000, 'system:catalogue-seed', '2026-11-12 17:30:02.063962' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+49), (@bv+49), (@bpp+49), 1, NULL, 480000, 'system:catalogue-seed', '2026-11-12 17:30:02.087961' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+50), (@bv+50), (@bpp+50), 1, NULL, 480000, 'system:catalogue-seed', '2026-11-12 17:30:02.115932' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+51), (@bv+51), (@bpp+51), 1, NULL, 480000, 'system:catalogue-seed', '2026-11-12 17:30:02.147609' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+52), (@bv+52), (@bpp+52), 1, NULL, 480000, 'system:catalogue-seed', '2026-11-12 17:30:02.174621' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+53), (@bv+53), (@bpp+53), 1, NULL, 480000, 'system:catalogue-seed', '2026-11-12 17:30:02.201064' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+54), (@bv+54), (@bpp+54), 1, NULL, 480000, 'system:catalogue-seed', '2026-11-12 17:30:02.219356' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+55), (@bv+55), (@bpp+55), 1, NULL, 590000, 'system:catalogue-seed', '2026-11-12 17:30:04.143289' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+56), (@bv+56), (@bpp+56), 1, NULL, 590000, 'system:catalogue-seed', '2026-11-12 17:30:04.181637' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+57), (@bv+57), (@bpp+57), 1, NULL, 590000, 'system:catalogue-seed', '2026-11-12 17:30:04.221588' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+58), (@bv+58), (@bpp+58), 1, NULL, 590000, 'system:catalogue-seed', '2026-11-12 17:30:04.268738' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+59), (@bv+59), (@bpp+59), 1, NULL, 590000, 'system:catalogue-seed', '2026-11-12 17:30:04.306661' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+60), (@bv+60), (@bpp+60), 1, NULL, 590000, 'system:catalogue-seed', '2026-11-12 17:30:04.342526' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+61), (@bv+61), (@bpp+61), 1, NULL, 590000, 'system:catalogue-seed', '2026-11-12 17:30:04.376726' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+62), (@bv+62), (@bpp+62), 1, NULL, 590000, 'system:catalogue-seed', '2026-11-12 17:30:04.414838' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+63), (@bv+63), (@bpp+63), 1, NULL, 560000, 'system:catalogue-seed', '2026-11-12 17:30:06.455817' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+64), (@bv+64), (@bpp+64), 1, NULL, 560000, 'system:catalogue-seed', '2026-11-12 17:30:06.488370' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+65), (@bv+65), (@bpp+65), 1, NULL, 560000, 'system:catalogue-seed', '2026-11-12 17:30:06.527453' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+66), (@bv+66), (@bpp+66), 1, NULL, 560000, 'system:catalogue-seed', '2026-11-12 17:30:06.571823' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+67), (@bv+67), (@bpp+67), 1, NULL, 560000, 'system:catalogue-seed', '2026-11-12 17:30:06.606921' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+68), (@bv+68), (@bpp+68), 1, NULL, 560000, 'system:catalogue-seed', '2026-11-12 17:30:06.636686' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+69), (@bv+69), (@bpp+69), 1, NULL, 560000, 'system:catalogue-seed', '2026-11-12 17:30:06.664200' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+70), (@bv+70), (@bpp+70), 1, NULL, 550000, 'system:catalogue-seed', '2026-11-12 17:30:08.981160' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+71), (@bv+71), (@bpp+71), 1, NULL, 550000, 'system:catalogue-seed', '2026-11-12 17:30:09.066219' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+72), (@bv+72), (@bpp+72), 1, NULL, 550000, 'system:catalogue-seed', '2026-11-12 17:30:09.118399' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+73), (@bv+73), (@bpp+73), 1, NULL, 550000, 'system:catalogue-seed', '2026-11-12 17:30:09.171802' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+74), (@bv+74), (@bpp+74), 1, NULL, 550000, 'system:catalogue-seed', '2026-11-12 17:30:09.216259' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+75), (@bv+75), (@bpp+75), 1, NULL, 550000, 'system:catalogue-seed', '2026-11-12 17:30:09.281575' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+76), (@bv+76), (@bpp+76), 1, NULL, 550000, 'system:catalogue-seed', '2026-11-12 17:30:09.386862' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+77), (@bv+77), (@bpp+77), 1, NULL, 550000, 'system:catalogue-seed', '2026-11-12 17:30:09.429171' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+78), (@bv+78), (@bpp+78), 1, NULL, 690000, 'system:catalogue-seed', '2026-11-12 17:30:11.698464' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+79), (@bv+79), (@bpp+79), 1, NULL, 690000, 'system:catalogue-seed', '2026-11-12 17:30:11.719465' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+80), (@bv+80), (@bpp+80), 1, NULL, 690000, 'system:catalogue-seed', '2026-11-12 17:30:11.744155' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+81), (@bv+81), (@bpp+81), 1, NULL, 690000, 'system:catalogue-seed', '2026-11-12 17:30:11.764461' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+82), (@bv+82), (@bpp+82), 1, NULL, 690000, 'system:catalogue-seed', '2026-11-12 17:30:11.785268' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+83), (@bv+83), (@bpp+83), 1, NULL, 690000, 'system:catalogue-seed', '2026-11-12 17:30:11.811274' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+84), (@bv+84), (@bpp+84), 1, NULL, 690000, 'system:catalogue-seed', '2026-11-12 17:30:11.829995' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+85), (@bv+85), (@bpp+85), 1, NULL, 520000, 'system:catalogue-seed', '2026-11-12 17:30:13.752349' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+86), (@bv+86), (@bpp+86), 1, NULL, 520000, 'system:catalogue-seed', '2026-11-12 17:30:13.788616' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+87), (@bv+87), (@bpp+87), 1, NULL, 520000, 'system:catalogue-seed', '2026-11-12 17:30:13.823325' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+88), (@bv+88), (@bpp+88), 1, NULL, 520000, 'system:catalogue-seed', '2026-11-12 17:30:13.861212' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+89), (@bv+89), (@bpp+89), 1, NULL, 520000, 'system:catalogue-seed', '2026-11-12 17:30:13.887436' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+90), (@bv+90), (@bpp+90), 1, NULL, 520000, 'system:catalogue-seed', '2026-11-12 17:30:13.910643' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+91), (@bv+91), (@bpp+91), 1, NULL, 520000, 'system:catalogue-seed', '2026-11-12 17:30:13.934441' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+92), (@bv+92), (@bpp+92), 1, NULL, 750000, 'system:catalogue-seed', '2026-11-12 17:30:18.629569' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+93), (@bv+93), (@bpp+93), 1, NULL, 750000, 'system:catalogue-seed', '2026-11-12 17:30:18.659888' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+94), (@bv+94), (@bpp+94), 1, NULL, 750000, 'system:catalogue-seed', '2026-11-12 17:30:18.686098' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+95), (@bv+95), (@bpp+95), 1, NULL, 750000, 'system:catalogue-seed', '2026-11-12 17:30:18.715183' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+96), (@bv+96), (@bpp+96), 1, NULL, 750000, 'system:catalogue-seed', '2026-11-12 17:30:18.743686' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+97), (@bv+97), (@bpp+97), 1, NULL, 750000, 'system:catalogue-seed', '2026-11-12 17:30:18.767964' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+98), (@bv+98), (@bpp+98), 1, NULL, 750000, 'system:catalogue-seed', '2026-11-12 17:30:18.794159' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+99), (@bv+99), (@bpp+99), 1, NULL, 750000, 'system:catalogue-seed', '2026-11-12 17:30:18.815176' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+100), (@bv+100), (@bpp+100), 1, NULL, 680000, 'system:catalogue-seed', '2026-11-12 17:30:20.964688' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+101), (@bv+101), (@bpp+101), 1, NULL, 680000, 'system:catalogue-seed', '2026-11-12 17:30:20.990057' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+102), (@bv+102), (@bpp+102), 1, NULL, 680000, 'system:catalogue-seed', '2026-11-12 17:30:21.009605' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+103), (@bv+103), (@bpp+103), 1, NULL, 680000, 'system:catalogue-seed', '2026-11-12 17:30:21.041469' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+104), (@bv+104), (@bpp+104), 1, NULL, 680000, 'system:catalogue-seed', '2026-11-12 17:30:21.061453' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+105), (@bv+105), (@bpp+105), 1, NULL, 680000, 'system:catalogue-seed', '2026-11-12 17:30:21.079962' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+106), (@bv+106), (@bpp+106), 1, NULL, 680000, 'system:catalogue-seed', '2026-11-12 17:30:21.107084' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+107), (@bv+107), (@bpp+107), 1, NULL, 680000, 'system:catalogue-seed', '2026-11-12 17:30:21.138859' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+108), (@bv+108), (@bpp+108), 1, NULL, 720000, 'system:catalogue-seed', '2026-11-12 17:30:23.139877' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+109), (@bv+109), (@bpp+109), 1, NULL, 720000, 'system:catalogue-seed', '2026-11-12 17:30:23.163223' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+110), (@bv+110), (@bpp+110), 1, NULL, 720000, 'system:catalogue-seed', '2026-11-12 17:30:23.180750' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+111), (@bv+111), (@bpp+111), 1, NULL, 720000, 'system:catalogue-seed', '2026-11-12 17:30:23.204905' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+112), (@bv+112), (@bpp+112), 1, NULL, 720000, 'system:catalogue-seed', '2026-11-12 17:30:23.226989' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+113), (@bv+113), (@bpp+113), 1, NULL, 720000, 'system:catalogue-seed', '2026-11-12 17:30:23.249281' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+114), (@bv+114), (@bpp+114), 1, NULL, 720000, 'system:catalogue-seed', '2026-11-12 17:30:23.274272' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+115), (@bv+115), (@bpp+115), 1, NULL, 720000, 'system:catalogue-seed', '2026-11-12 17:30:23.299396' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+116), (@bv+116), (@bpp+116), 1, NULL, 680000, 'system:catalogue-seed', '2026-11-12 17:30:25.245699' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+117), (@bv+117), (@bpp+117), 1, NULL, 680000, 'system:catalogue-seed', '2026-11-12 17:30:25.264715' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+118), (@bv+118), (@bpp+118), 1, NULL, 680000, 'system:catalogue-seed', '2026-11-12 17:30:25.283737' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+119), (@bv+119), (@bpp+119), 1, NULL, 680000, 'system:catalogue-seed', '2026-11-12 17:30:25.305733' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+120), (@bv+120), (@bpp+120), 1, NULL, 680000, 'system:catalogue-seed', '2026-11-12 17:30:25.328028' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+121), (@bv+121), (@bpp+121), 1, NULL, 680000, 'system:catalogue-seed', '2026-11-12 17:30:25.350326' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+122), (@bv+122), (@bpp+122), 1, NULL, 680000, 'system:catalogue-seed', '2026-11-12 17:30:25.371690' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+123), (@bv+123), (@bpp+123), 1, NULL, 680000, 'system:catalogue-seed', '2026-11-12 17:30:25.393290' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+124), (@bv+124), (@bpp+124), 1, NULL, 620000, 'system:catalogue-seed', '2026-11-12 17:30:27.378785' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+125), (@bv+125), (@bpp+125), 1, NULL, 620000, 'system:catalogue-seed', '2026-11-12 17:30:27.397797' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+126), (@bv+126), (@bpp+126), 1, NULL, 620000, 'system:catalogue-seed', '2026-11-12 17:30:27.420681' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+127), (@bv+127), (@bpp+127), 1, NULL, 620000, 'system:catalogue-seed', '2026-11-12 17:30:27.441768' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+128), (@bv+128), (@bpp+128), 1, NULL, 620000, 'system:catalogue-seed', '2026-11-12 17:30:27.461788' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+129), (@bv+129), (@bpp+129), 1, NULL, 620000, 'system:catalogue-seed', '2026-11-12 17:30:27.482982' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+130), (@bv+130), (@bpp+130), 1, NULL, 620000, 'system:catalogue-seed', '2026-11-12 17:30:27.505633' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+131), (@bv+131), (@bpp+131), 1, NULL, 620000, 'system:catalogue-seed', '2026-11-12 17:30:27.524985' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+132), (@bv+132), (@bpp+132), 1, NULL, 650000, 'system:catalogue-seed', '2026-11-12 17:30:29.392174' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+133), (@bv+133), (@bpp+133), 1, NULL, 650000, 'system:catalogue-seed', '2026-11-12 17:30:29.413678' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+134), (@bv+134), (@bpp+134), 1, NULL, 650000, 'system:catalogue-seed', '2026-11-12 17:30:29.437676' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+135), (@bv+135), (@bpp+135), 1, NULL, 650000, 'system:catalogue-seed', '2026-11-12 17:30:29.463444' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+136), (@bv+136), (@bpp+136), 1, NULL, 650000, 'system:catalogue-seed', '2026-11-12 17:30:29.490293' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+137), (@bv+137), (@bpp+137), 1, NULL, 650000, 'system:catalogue-seed', '2026-11-12 17:30:29.513239' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+138), (@bv+138), (@bpp+138), 1, NULL, 650000, 'system:catalogue-seed', '2026-11-12 17:30:29.544237' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+139), (@bv+139), (@bpp+139), 1, NULL, 650000, 'system:catalogue-seed', '2026-11-12 17:30:29.576798' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+140), (@bv+140), (@bpp+140), 1, NULL, 480000, 'system:catalogue-seed', '2026-11-12 17:30:31.506543' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+141), (@bv+141), (@bpp+141), 1, NULL, 480000, 'system:catalogue-seed', '2026-11-12 17:30:31.526119' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+142), (@bv+142), (@bpp+142), 1, NULL, 480000, 'system:catalogue-seed', '2026-11-12 17:30:31.544021' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+143), (@bv+143), (@bpp+143), 1, NULL, 480000, 'system:catalogue-seed', '2026-11-12 17:30:31.561107' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+144), (@bv+144), (@bpp+144), 1, NULL, 480000, 'system:catalogue-seed', '2026-11-12 17:30:31.578227' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+145), (@bv+145), (@bpp+145), 1, NULL, 480000, 'system:catalogue-seed', '2026-11-12 17:30:31.597995' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+146), (@bv+146), (@bpp+146), 1, NULL, 480000, 'system:catalogue-seed', '2026-11-12 17:30:31.619015' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+147), (@bv+147), (@bpp+147), 1, NULL, 480000, 'system:catalogue-seed', '2026-11-12 17:30:31.640247' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+148), (@bv+148), (@bpp+148), 1, NULL, 590000, 'system:catalogue-seed', '2026-11-12 17:30:35.374644' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+149), (@bv+149), (@bpp+149), 1, NULL, 590000, 'system:catalogue-seed', '2026-11-12 17:30:35.396826' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+150), (@bv+150), (@bpp+150), 1, NULL, 590000, 'system:catalogue-seed', '2026-11-12 17:30:35.420045' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+151), (@bv+151), (@bpp+151), 1, NULL, 590000, 'system:catalogue-seed', '2026-11-12 17:30:35.439067' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+152), (@bv+152), (@bpp+152), 1, NULL, 590000, 'system:catalogue-seed', '2026-11-12 17:30:35.458380' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+153), (@bv+153), (@bpp+153), 1, NULL, 590000, 'system:catalogue-seed', '2026-11-12 17:30:35.475413' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+154), (@bv+154), (@bpp+154), 1, NULL, 590000, 'system:catalogue-seed', '2026-11-12 17:30:35.492510' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+155), (@bv+155), (@bpp+155), 1, NULL, 640000, 'system:catalogue-seed', '2026-11-12 17:30:37.640966' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+156), (@bv+156), (@bpp+156), 1, NULL, 640000, 'system:catalogue-seed', '2026-11-12 17:30:37.659779' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+157), (@bv+157), (@bpp+157), 1, NULL, 640000, 'system:catalogue-seed', '2026-11-12 17:30:37.680657' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+158), (@bv+158), (@bpp+158), 1, NULL, 640000, 'system:catalogue-seed', '2026-11-12 17:30:37.701272' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+159), (@bv+159), (@bpp+159), 1, NULL, 640000, 'system:catalogue-seed', '2026-11-12 17:30:37.722806' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+160), (@bv+160), (@bpp+160), 1, NULL, 640000, 'system:catalogue-seed', '2026-11-12 17:30:37.741441' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+161), (@bv+161), (@bpp+161), 1, NULL, 640000, 'system:catalogue-seed', '2026-11-12 17:30:37.757933' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+162), (@bv+161), (@bpp+162), 2, 640000, 700000, '5', '2026-11-12 17:41:06.160659' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+163), (@bv+154), (@bpp+163), 2, 590000, 600000, '5', '2026-11-12 17:41:49.248187' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+164), (@bv+152), (@bpp+164), 2, 590000, 650000, '5', '2026-11-12 17:43:38.953424' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+165), (@bv+153), (@bpp+165), 2, 590000, 650000, '5', '2026-11-12 17:43:39.642004' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+166), (@bv+154), (@bpp+166), 3, 600000, 650000, '5', '2026-11-12 17:43:40.502455' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+167), (@bv+143), (@bpp+167), 2, 480000, 500000, '5', '2026-11-12 17:44:28.127819' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+168), (@bv+144), (@bpp+168), 2, 480000, 500000, '5', '2026-11-12 17:44:28.767125' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+169), (@bv+145), (@bpp+169), 2, 480000, 500000, '5', '2026-11-12 17:44:29.799194' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+170), (@bv+146), (@bpp+170), 2, 480000, 500000, '5', '2026-11-12 17:44:30.549431' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+171), (@bv+147), (@bpp+171), 2, 480000, 500000, '5', '2026-11-12 17:44:31.353586' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+172), (@bv+67), (@bpp+172), 2, 560000, 650000, '5', '2026-11-12 17:52:16.838744' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+173), (@bv+68), (@bpp+173), 2, 560000, 650000, '5', '2026-11-12 17:52:17.441164' FROM DUAL WHERE @run_catalogue;
INSERT INTO `catalogue_price_applications` (`id`, `variant_id`, `proposal_id`, `applied_sequence`, `old_amount`, `new_amount`, `actor_id`, `applied_at`)
SELECT (@ba+174), (@bv+69), (@bpp+174), 2, 560000, 650000, '5', '2026-11-12 17:52:18.082660' FROM DUAL WHERE @run_catalogue;

-- ===== Promotions =====
SET @run_promotions = @branches_ok AND NOT EXISTS (SELECT 1 FROM promotion_program WHERE code_key = 'MLCHAO10');
SET @bg  = (SELECT COALESCE(MAX(id), 0) FROM promotion_program);
SET @bgr = (SELECT COALESCE(MAX(id), 0) FROM promotion_revision);
SET @bt  = (SELECT COALESCE(MAX(id), 0) FROM promotion_revision_target);

INSERT INTO `promotion_program` (`id`, `kind`, `scope`, `code_original`, `code_key`, `status`, `current_revision_id`, `total_limit`, `member_limit`, `held_count`, `consumed_count`, `created_at`, `created_by`, `config_version`, `version`)
SELECT (@bg+1), 'VOUCHER', 'PRODUCT', 'MLCHAO10', 'MLCHAO10', 'ENABLED', (@bgr+1), 200, 1, 0, 0, '2026-11-12 17:47:18.319854', 3, 0, 1 FROM DUAL WHERE @run_promotions;
INSERT INTO `promotion_program` (`id`, `kind`, `scope`, `code_original`, `code_key`, `status`, `current_revision_id`, `total_limit`, `member_limit`, `held_count`, `consumed_count`, `created_at`, `created_by`, `config_version`, `version`)
SELECT (@bg+2), 'VOUCHER', 'PRODUCT', 'MLGIAM50K', 'MLGIAM50K', 'ENABLED', (@bgr+2), 100, 2, 0, 0, '2026-11-12 17:47:18.480228', 3, 0, 1 FROM DUAL WHERE @run_promotions;
INSERT INTO `promotion_program` (`id`, `kind`, `scope`, `code_original`, `code_key`, `status`, `current_revision_id`, `total_limit`, `member_limit`, `held_count`, `consumed_count`, `created_at`, `created_by`, `config_version`, `version`)
SELECT (@bg+3), 'VOUCHER', 'PRODUCT', 'MLMANAO15', 'MLMANAO15', 'ENABLED', (@bgr+3), 50, 1, 0, 0, '2026-11-12 17:47:18.538957', 3, 0, 1 FROM DUAL WHERE @run_promotions;
INSERT INTO `promotion_program` (`id`, `kind`, `scope`, `code_original`, `code_key`, `status`, `current_revision_id`, `total_limit`, `member_limit`, `held_count`, `consumed_count`, `created_at`, `created_by`, `config_version`, `version`)
SELECT (@bg+4), 'VOUCHER', 'PRODUCT', 'MLTAYHO80', 'MLTAYHO80', 'ENABLED', (@bgr+4), 30, 1, 0, 0, '2026-11-12 17:47:18.587154', 3, 0, 1 FROM DUAL WHERE @run_promotions;
INSERT INTO `promotion_program` (`id`, `kind`, `scope`, `code_original`, `code_key`, `status`, `current_revision_id`, `total_limit`, `member_limit`, `held_count`, `consumed_count`, `created_at`, `created_by`, `config_version`, `version`)
SELECT (@bg+5), 'VOUCHER', 'PRODUCT', 'MLHK12', 'MLHK12', 'ENABLED', (@bgr+5), 60, 1, 0, 0, '2026-11-12 17:47:18.627044', 3, 0, 1 FROM DUAL WHERE @run_promotions;
INSERT INTO `promotion_program` (`id`, `kind`, `scope`, `code_original`, `code_key`, `status`, `current_revision_id`, `total_limit`, `member_limit`, `held_count`, `consumed_count`, `created_at`, `created_by`, `config_version`, `version`)
SELECT (@bg+6), 'AUTO_DISCOUNT', 'PRODUCT', NULL, NULL, 'ENABLED', (@bgr+6), NULL, NULL, NULL, NULL, '2026-11-12 17:47:18.658400', 3, 0, 1 FROM DUAL WHERE @run_promotions;
INSERT INTO `promotion_program` (`id`, `kind`, `scope`, `code_original`, `code_key`, `status`, `current_revision_id`, `total_limit`, `member_limit`, `held_count`, `consumed_count`, `created_at`, `created_by`, `config_version`, `version`)
SELECT (@bg+7), 'AUTO_DISCOUNT', 'PRODUCT', NULL, NULL, 'ENABLED', (@bgr+7), NULL, NULL, NULL, NULL, '2026-11-12 17:47:18.681968', 3, 0, 1 FROM DUAL WHERE @run_promotions;
INSERT INTO `promotion_program` (`id`, `kind`, `scope`, `code_original`, `code_key`, `status`, `current_revision_id`, `total_limit`, `member_limit`, `held_count`, `consumed_count`, `created_at`, `created_by`, `config_version`, `version`)
SELECT (@bg+8), 'VOUCHER', 'WORKSHOP', 'MLWS100', 'MLWS100', 'ENABLED', (@bgr+8), 100, 1, 0, 0, '2026-11-12 17:47:18.704746', 3, 0, 1 FROM DUAL WHERE @run_promotions;
INSERT INTO `promotion_program` (`id`, `kind`, `scope`, `code_original`, `code_key`, `status`, `current_revision_id`, `total_limit`, `member_limit`, `held_count`, `consumed_count`, `created_at`, `created_by`, `config_version`, `version`)
SELECT (@bg+9), 'VOUCHER', 'WORKSHOP', 'MLWS15', 'MLWS15', 'ENABLED', (@bgr+9), 50, 1, 0, 0, '2026-11-12 17:47:18.722638', 3, 0, 1 FROM DUAL WHERE @run_promotions;
INSERT INTO `promotion_program` (`id`, `kind`, `scope`, `code_original`, `code_key`, `status`, `current_revision_id`, `total_limit`, `member_limit`, `held_count`, `consumed_count`, `created_at`, `created_by`, `config_version`, `version`)
SELECT (@bg+10), 'VOUCHER', 'WORKSHOP', 'MLWSHK50', 'MLWSHK50', 'ENABLED', (@bgr+10), 80, 2, 0, 0, '2026-11-12 17:47:18.750523', 3, 0, 1 FROM DUAL WHERE @run_promotions;
INSERT INTO `promotion_revision` (`id`, `program_id`, `revision_no`, `name`, `start_at`, `end_at`, `discount_kind`, `percent`, `fixed_amount`, `max_discount_amount`, `minimum_eligible_amount`, `allow_points`, `created_at`, `created_by`)
SELECT (@bgr+1), (@bg+1), 1, 'Chào thành viên mới – giảm 10%', '2026-11-12 17:47:00.000000', '2027-03-12 17:47:00.000000', 'PERCENT', 10, NULL, 60000, 300000, 1, '2026-11-12 17:47:18.319854', 3 FROM DUAL WHERE @run_promotions;
INSERT INTO `promotion_revision` (`id`, `program_id`, `revision_no`, `name`, `start_at`, `end_at`, `discount_kind`, `percent`, `fixed_amount`, `max_discount_amount`, `minimum_eligible_amount`, `allow_points`, `created_at`, `created_by`)
SELECT (@bgr+2), (@bg+2), 1, 'Giảm 50.000 ₫ cho đơn từ 500.000 ₫', '2026-11-12 17:47:00.000000', '2027-02-10 17:47:00.000000', 'FIXED', NULL, 50000, 50000, 500000, 1, '2026-11-12 17:47:18.480228', 3 FROM DUAL WHERE @run_promotions;
INSERT INTO `promotion_revision` (`id`, `program_id`, `revision_no`, `name`, `start_at`, `end_at`, `discount_kind`, `percent`, `fixed_amount`, `max_discount_amount`, `minimum_eligible_amount`, `allow_points`, `created_at`, `created_by`)
SELECT (@bgr+3), (@bg+3), 1, 'Mùa mã não – giảm 15%', '2026-11-12 17:47:00.000000', '2027-01-11 17:47:00.000000', 'PERCENT', 15, NULL, 120000, 0, 1, '2026-11-12 17:47:18.538957', 3 FROM DUAL WHERE @run_promotions;
INSERT INTO `promotion_revision` (`id`, `program_id`, `revision_no`, `name`, `start_at`, `end_at`, `discount_kind`, `percent`, `fixed_amount`, `max_discount_amount`, `minimum_eligible_amount`, `allow_points`, `created_at`, `created_by`)
SELECT (@bgr+4), (@bg+4), 1, 'Đá thô Tây Hồ – giảm 80.000 ₫', '2026-11-12 17:47:00.000000', '2026-12-27 17:47:00.000000', 'FIXED', NULL, 80000, 80000, 450000, 0, '2026-11-12 17:47:18.587154', 3 FROM DUAL WHERE @run_promotions;
INSERT INTO `promotion_revision` (`id`, `program_id`, `revision_no`, `name`, `start_at`, `end_at`, `discount_kind`, `percent`, `fixed_amount`, `max_discount_amount`, `minimum_eligible_amount`, `allow_points`, `created_at`, `created_by`)
SELECT (@bgr+5), (@bg+5), 1, 'Hoàn Kiếm – nhẫn đá quý giảm 12%', '2026-11-12 17:47:00.000000', '2027-01-11 17:47:00.000000', 'PERCENT', 12, NULL, 90000, 0, 1, '2026-11-12 17:47:18.627044', 3 FROM DUAL WHERE @run_promotions;
INSERT INTO `promotion_revision` (`id`, `program_id`, `revision_no`, `name`, `start_at`, `end_at`, `discount_kind`, `percent`, `fixed_amount`, `max_discount_amount`, `minimum_eligible_amount`, `allow_points`, `created_at`, `created_by`)
SELECT (@bgr+6), (@bg+6), 1, 'Tuần lễ nhẫn bạc trơn – giảm 10%', '2026-11-12 17:47:00.000000', '2026-12-12 17:47:00.000000', 'PERCENT', 10, NULL, 50000, 0, 1, '2026-11-12 17:47:18.658400', 3 FROM DUAL WHERE @run_promotions;
INSERT INTO `promotion_revision` (`id`, `program_id`, `revision_no`, `name`, `start_at`, `end_at`, `discount_kind`, `percent`, `fixed_amount`, `max_discount_amount`, `minimum_eligible_amount`, `allow_points`, `created_at`, `created_by`)
SELECT (@bgr+7), (@bg+7), 1, 'Giảm ngay 30.000 ₫ nhẫn citrine và peridot', '2026-11-12 17:47:00.000000', '2026-12-27 17:47:00.000000', 'FIXED', NULL, 30000, 30000, 500000, 1, '2026-11-12 17:47:18.681968', 3 FROM DUAL WHERE @run_promotions;
INSERT INTO `promotion_revision` (`id`, `program_id`, `revision_no`, `name`, `start_at`, `end_at`, `discount_kind`, `percent`, `fixed_amount`, `max_discount_amount`, `minimum_eligible_amount`, `allow_points`, `created_at`, `created_by`)
SELECT (@bgr+8), (@bg+8), 1, 'Workshop – giảm 100.000 ₫', '2026-11-12 17:47:00.000000', '2027-02-10 17:47:00.000000', 'FIXED', NULL, 100000, 100000, 0, 1, '2026-11-12 17:47:18.704746', 3 FROM DUAL WHERE @run_promotions;
INSERT INTO `promotion_revision` (`id`, `program_id`, `revision_no`, `name`, `start_at`, `end_at`, `discount_kind`, `percent`, `fixed_amount`, `max_discount_amount`, `minimum_eligible_amount`, `allow_points`, `created_at`, `created_by`)
SELECT (@bgr+9), (@bg+9), 1, 'Rủ bạn đi workshop – giảm 15%', '2026-11-12 17:47:00.000000', '2027-01-11 17:47:00.000000', 'PERCENT', 15, NULL, 200000, 1000000, 1, '2026-11-12 17:47:18.722638', 3 FROM DUAL WHERE @run_promotions;
INSERT INTO `promotion_revision` (`id`, `program_id`, `revision_no`, `name`, `start_at`, `end_at`, `discount_kind`, `percent`, `fixed_amount`, `max_discount_amount`, `minimum_eligible_amount`, `allow_points`, `created_at`, `created_by`)
SELECT (@bgr+10), (@bg+10), 1, 'Workshop Hoàn Kiếm – giảm 50.000 ₫', '2026-11-12 17:47:00.000000', '2027-01-11 17:47:00.000000', 'FIXED', NULL, 50000, 50000, 0, 0, '2026-11-12 17:47:18.750523', 3 FROM DUAL WHERE @run_promotions;
INSERT INTO `promotion_revision_target` (`id`, `revision_id`, `dimension`, `target_key`)
SELECT (@bt+1), (@bgr+1), 'PRODUCT', (SELECT CAST(MAX(id) AS CHAR) FROM catalogue_products WHERE name = 'Nhẫn bạc vân búa bản nhọn' AND deleted_at IS NULL) FROM DUAL WHERE @run_promotions;
INSERT INTO `promotion_revision_target` (`id`, `revision_id`, `dimension`, `target_key`)
SELECT (@bt+2), (@bgr+1), 'PRODUCT', (SELECT CAST(MAX(id) AS CHAR) FROM catalogue_products WHERE name = 'Nhẫn bạc mèo xoắn' AND deleted_at IS NULL) FROM DUAL WHERE @run_promotions;
INSERT INTO `promotion_revision_target` (`id`, `revision_id`, `dimension`, `target_key`)
SELECT (@bt+3), (@bgr+1), 'PRODUCT', (SELECT CAST(MAX(id) AS CHAR) FROM catalogue_products WHERE name = 'Nhẫn bạc trăng khuyết thạch anh tím' AND deleted_at IS NULL) FROM DUAL WHERE @run_promotions;
INSERT INTO `promotion_revision_target` (`id`, `revision_id`, `dimension`, `target_key`)
SELECT (@bt+4), (@bgr+1), 'PRODUCT', (SELECT CAST(MAX(id) AS CHAR) FROM catalogue_products WHERE name = 'Nhẫn bạc sóng nước peridot' AND deleted_at IS NULL) FROM DUAL WHERE @run_promotions;
INSERT INTO `promotion_revision_target` (`id`, `revision_id`, `dimension`, `target_key`)
SELECT (@bt+5), (@bgr+1), 'PRODUCT', (SELECT CAST(MAX(id) AS CHAR) FROM catalogue_products WHERE name = 'Nhẫn bạc mắt garnet' AND deleted_at IS NULL) FROM DUAL WHERE @run_promotions;
INSERT INTO `promotion_revision_target` (`id`, `revision_id`, `dimension`, `target_key`)
SELECT (@bt+6), (@bgr+1), 'PRODUCT', (SELECT CAST(MAX(id) AS CHAR) FROM catalogue_products WHERE name = 'Nhẫn bạc xoáy citrine' AND deleted_at IS NULL) FROM DUAL WHERE @run_promotions;
INSERT INTO `promotion_revision_target` (`id`, `revision_id`, `dimension`, `target_key`)
SELECT (@bt+7), (@bgr+1), 'PRODUCT', (SELECT CAST(MAX(id) AS CHAR) FROM catalogue_products WHERE name = 'Nhẫn bạc garnet tròn' AND deleted_at IS NULL) FROM DUAL WHERE @run_promotions;
INSERT INTO `promotion_revision_target` (`id`, `revision_id`, `dimension`, `target_key`)
SELECT (@bt+8), (@bgr+1), 'PRODUCT', (SELECT CAST(MAX(id) AS CHAR) FROM catalogue_products WHERE name = 'Nhẫn bạc hổ phách' AND deleted_at IS NULL) FROM DUAL WHERE @run_promotions;
INSERT INTO `promotion_revision_target` (`id`, `revision_id`, `dimension`, `target_key`)
SELECT (@bt+9), (@bgr+1), 'PRODUCT', (SELECT CAST(MAX(id) AS CHAR) FROM catalogue_products WHERE name = 'Nhẫn bạc cành lá citrine' AND deleted_at IS NULL) FROM DUAL WHERE @run_promotions;
INSERT INTO `promotion_revision_target` (`id`, `revision_id`, `dimension`, `target_key`)
SELECT (@bt+10), (@bgr+1), 'PRODUCT', (SELECT CAST(MAX(id) AS CHAR) FROM catalogue_products WHERE name = 'Nhẫn bạc peridot oval' AND deleted_at IS NULL) FROM DUAL WHERE @run_promotions;
INSERT INTO `promotion_revision_target` (`id`, `revision_id`, `dimension`, `target_key`)
SELECT (@bt+11), (@bgr+1), 'PRODUCT', (SELECT CAST(MAX(id) AS CHAR) FROM catalogue_products WHERE name = 'Nhẫn hoa văn vàng thạch anh tím' AND deleted_at IS NULL) FROM DUAL WHERE @run_promotions;
INSERT INTO `promotion_revision_target` (`id`, `revision_id`, `dimension`, `target_key`)
SELECT (@bt+12), (@bgr+1), 'PRODUCT', (SELECT CAST(MAX(id) AS CHAR) FROM catalogue_products WHERE name = 'Nhẫn bạc lá ngọc xanh' AND deleted_at IS NULL) FROM DUAL WHERE @run_promotions;
INSERT INTO `promotion_revision_target` (`id`, `revision_id`, `dimension`, `target_key`)
SELECT (@bt+13), (@bgr+1), 'PRODUCT', (SELECT CAST(MAX(id) AS CHAR) FROM catalogue_products WHERE name = 'Nhẫn bạc mã não cây chữ nhật' AND deleted_at IS NULL) FROM DUAL WHERE @run_promotions;
INSERT INTO `promotion_revision_target` (`id`, `revision_id`, `dimension`, `target_key`)
SELECT (@bt+14), (@bgr+1), 'PRODUCT', (SELECT CAST(MAX(id) AS CHAR) FROM catalogue_products WHERE name = 'Nhẫn bạc mã não cây vuông' AND deleted_at IS NULL) FROM DUAL WHERE @run_promotions;
INSERT INTO `promotion_revision_target` (`id`, `revision_id`, `dimension`, `target_key`)
SELECT (@bt+15), (@bgr+1), 'PRODUCT', (SELECT CAST(MAX(id) AS CHAR) FROM catalogue_products WHERE name = 'Nhẫn bạc mã não cây oval ngang' AND deleted_at IS NULL) FROM DUAL WHERE @run_promotions;
INSERT INTO `promotion_revision_target` (`id`, `revision_id`, `dimension`, `target_key`)
SELECT (@bt+16), (@bgr+1), 'PRODUCT', (SELECT CAST(MAX(id) AS CHAR) FROM catalogue_products WHERE name = 'Nhẫn bạc mã não cây oval dọc' AND deleted_at IS NULL) FROM DUAL WHERE @run_promotions;
INSERT INTO `promotion_revision_target` (`id`, `revision_id`, `dimension`, `target_key`)
SELECT (@bt+17), (@bgr+1), 'PRODUCT', (SELECT CAST(MAX(id) AS CHAR) FROM catalogue_products WHERE name = 'Nhẫn bạc mã não rêu trái tim' AND deleted_at IS NULL) FROM DUAL WHERE @run_promotions;
INSERT INTO `promotion_revision_target` (`id`, `revision_id`, `dimension`, `target_key`)
SELECT (@bt+18), (@bgr+1), 'PRODUCT', (SELECT CAST(MAX(id) AS CHAR) FROM catalogue_products WHERE name = 'Nhẫn bạc mã não rêu giọt nước' AND deleted_at IS NULL) FROM DUAL WHERE @run_promotions;
INSERT INTO `promotion_revision_target` (`id`, `revision_id`, `dimension`, `target_key`)
SELECT (@bt+19), (@bgr+1), 'PRODUCT', (SELECT CAST(MAX(id) AS CHAR) FROM catalogue_products WHERE name = 'Nhẫn bạc ngọc xanh quấn thừng' AND deleted_at IS NULL) FROM DUAL WHERE @run_promotions;
INSERT INTO `promotion_revision_target` (`id`, `revision_id`, `dimension`, `target_key`)
SELECT (@bt+20), (@bgr+1), 'PRODUCT', (SELECT CAST(MAX(id) AS CHAR) FROM catalogue_products WHERE name = 'Nhẫn bạc thạch anh thô' AND deleted_at IS NULL) FROM DUAL WHERE @run_promotions;
INSERT INTO `promotion_revision_target` (`id`, `revision_id`, `dimension`, `target_key`)
SELECT (@bt+21), (@bgr+1), 'PRODUCT', (SELECT CAST(MAX(id) AS CHAR) FROM catalogue_products WHERE name = 'Nhẫn bạc hở tourmaline xanh' AND deleted_at IS NULL) FROM DUAL WHERE @run_promotions;
INSERT INTO `promotion_revision_target` (`id`, `revision_id`, `dimension`, `target_key`)
SELECT (@bt+22), (@bgr+2), 'PRODUCT', (SELECT CAST(MAX(id) AS CHAR) FROM catalogue_products WHERE name = 'Nhẫn bạc vân búa bản nhọn' AND deleted_at IS NULL) FROM DUAL WHERE @run_promotions;
INSERT INTO `promotion_revision_target` (`id`, `revision_id`, `dimension`, `target_key`)
SELECT (@bt+23), (@bgr+2), 'PRODUCT', (SELECT CAST(MAX(id) AS CHAR) FROM catalogue_products WHERE name = 'Nhẫn bạc mèo xoắn' AND deleted_at IS NULL) FROM DUAL WHERE @run_promotions;
INSERT INTO `promotion_revision_target` (`id`, `revision_id`, `dimension`, `target_key`)
SELECT (@bt+24), (@bgr+2), 'PRODUCT', (SELECT CAST(MAX(id) AS CHAR) FROM catalogue_products WHERE name = 'Nhẫn bạc trăng khuyết thạch anh tím' AND deleted_at IS NULL) FROM DUAL WHERE @run_promotions;
INSERT INTO `promotion_revision_target` (`id`, `revision_id`, `dimension`, `target_key`)
SELECT (@bt+25), (@bgr+2), 'PRODUCT', (SELECT CAST(MAX(id) AS CHAR) FROM catalogue_products WHERE name = 'Nhẫn bạc sóng nước peridot' AND deleted_at IS NULL) FROM DUAL WHERE @run_promotions;
INSERT INTO `promotion_revision_target` (`id`, `revision_id`, `dimension`, `target_key`)
SELECT (@bt+26), (@bgr+2), 'PRODUCT', (SELECT CAST(MAX(id) AS CHAR) FROM catalogue_products WHERE name = 'Nhẫn bạc mắt garnet' AND deleted_at IS NULL) FROM DUAL WHERE @run_promotions;
INSERT INTO `promotion_revision_target` (`id`, `revision_id`, `dimension`, `target_key`)
SELECT (@bt+27), (@bgr+2), 'PRODUCT', (SELECT CAST(MAX(id) AS CHAR) FROM catalogue_products WHERE name = 'Nhẫn bạc xoáy citrine' AND deleted_at IS NULL) FROM DUAL WHERE @run_promotions;
INSERT INTO `promotion_revision_target` (`id`, `revision_id`, `dimension`, `target_key`)
SELECT (@bt+28), (@bgr+2), 'PRODUCT', (SELECT CAST(MAX(id) AS CHAR) FROM catalogue_products WHERE name = 'Nhẫn bạc garnet tròn' AND deleted_at IS NULL) FROM DUAL WHERE @run_promotions;
INSERT INTO `promotion_revision_target` (`id`, `revision_id`, `dimension`, `target_key`)
SELECT (@bt+29), (@bgr+2), 'PRODUCT', (SELECT CAST(MAX(id) AS CHAR) FROM catalogue_products WHERE name = 'Nhẫn bạc hổ phách' AND deleted_at IS NULL) FROM DUAL WHERE @run_promotions;
INSERT INTO `promotion_revision_target` (`id`, `revision_id`, `dimension`, `target_key`)
SELECT (@bt+30), (@bgr+2), 'PRODUCT', (SELECT CAST(MAX(id) AS CHAR) FROM catalogue_products WHERE name = 'Nhẫn bạc cành lá citrine' AND deleted_at IS NULL) FROM DUAL WHERE @run_promotions;
INSERT INTO `promotion_revision_target` (`id`, `revision_id`, `dimension`, `target_key`)
SELECT (@bt+31), (@bgr+2), 'PRODUCT', (SELECT CAST(MAX(id) AS CHAR) FROM catalogue_products WHERE name = 'Nhẫn bạc peridot oval' AND deleted_at IS NULL) FROM DUAL WHERE @run_promotions;
INSERT INTO `promotion_revision_target` (`id`, `revision_id`, `dimension`, `target_key`)
SELECT (@bt+32), (@bgr+2), 'PRODUCT', (SELECT CAST(MAX(id) AS CHAR) FROM catalogue_products WHERE name = 'Nhẫn hoa văn vàng thạch anh tím' AND deleted_at IS NULL) FROM DUAL WHERE @run_promotions;
INSERT INTO `promotion_revision_target` (`id`, `revision_id`, `dimension`, `target_key`)
SELECT (@bt+33), (@bgr+2), 'PRODUCT', (SELECT CAST(MAX(id) AS CHAR) FROM catalogue_products WHERE name = 'Nhẫn bạc lá ngọc xanh' AND deleted_at IS NULL) FROM DUAL WHERE @run_promotions;
INSERT INTO `promotion_revision_target` (`id`, `revision_id`, `dimension`, `target_key`)
SELECT (@bt+34), (@bgr+2), 'PRODUCT', (SELECT CAST(MAX(id) AS CHAR) FROM catalogue_products WHERE name = 'Nhẫn bạc mã não cây chữ nhật' AND deleted_at IS NULL) FROM DUAL WHERE @run_promotions;
INSERT INTO `promotion_revision_target` (`id`, `revision_id`, `dimension`, `target_key`)
SELECT (@bt+35), (@bgr+2), 'PRODUCT', (SELECT CAST(MAX(id) AS CHAR) FROM catalogue_products WHERE name = 'Nhẫn bạc mã não cây vuông' AND deleted_at IS NULL) FROM DUAL WHERE @run_promotions;
INSERT INTO `promotion_revision_target` (`id`, `revision_id`, `dimension`, `target_key`)
SELECT (@bt+36), (@bgr+2), 'PRODUCT', (SELECT CAST(MAX(id) AS CHAR) FROM catalogue_products WHERE name = 'Nhẫn bạc mã não cây oval ngang' AND deleted_at IS NULL) FROM DUAL WHERE @run_promotions;
INSERT INTO `promotion_revision_target` (`id`, `revision_id`, `dimension`, `target_key`)
SELECT (@bt+37), (@bgr+2), 'PRODUCT', (SELECT CAST(MAX(id) AS CHAR) FROM catalogue_products WHERE name = 'Nhẫn bạc mã não cây oval dọc' AND deleted_at IS NULL) FROM DUAL WHERE @run_promotions;
INSERT INTO `promotion_revision_target` (`id`, `revision_id`, `dimension`, `target_key`)
SELECT (@bt+38), (@bgr+2), 'PRODUCT', (SELECT CAST(MAX(id) AS CHAR) FROM catalogue_products WHERE name = 'Nhẫn bạc mã não rêu trái tim' AND deleted_at IS NULL) FROM DUAL WHERE @run_promotions;
INSERT INTO `promotion_revision_target` (`id`, `revision_id`, `dimension`, `target_key`)
SELECT (@bt+39), (@bgr+2), 'PRODUCT', (SELECT CAST(MAX(id) AS CHAR) FROM catalogue_products WHERE name = 'Nhẫn bạc mã não rêu giọt nước' AND deleted_at IS NULL) FROM DUAL WHERE @run_promotions;
INSERT INTO `promotion_revision_target` (`id`, `revision_id`, `dimension`, `target_key`)
SELECT (@bt+40), (@bgr+2), 'PRODUCT', (SELECT CAST(MAX(id) AS CHAR) FROM catalogue_products WHERE name = 'Nhẫn bạc ngọc xanh quấn thừng' AND deleted_at IS NULL) FROM DUAL WHERE @run_promotions;
INSERT INTO `promotion_revision_target` (`id`, `revision_id`, `dimension`, `target_key`)
SELECT (@bt+41), (@bgr+2), 'PRODUCT', (SELECT CAST(MAX(id) AS CHAR) FROM catalogue_products WHERE name = 'Nhẫn bạc thạch anh thô' AND deleted_at IS NULL) FROM DUAL WHERE @run_promotions;
INSERT INTO `promotion_revision_target` (`id`, `revision_id`, `dimension`, `target_key`)
SELECT (@bt+42), (@bgr+2), 'PRODUCT', (SELECT CAST(MAX(id) AS CHAR) FROM catalogue_products WHERE name = 'Nhẫn bạc hở tourmaline xanh' AND deleted_at IS NULL) FROM DUAL WHERE @run_promotions;
INSERT INTO `promotion_revision_target` (`id`, `revision_id`, `dimension`, `target_key`)
SELECT (@bt+43), (@bgr+3), 'PRODUCT', (SELECT CAST(MAX(id) AS CHAR) FROM catalogue_products WHERE name = 'Nhẫn bạc mã não cây chữ nhật' AND deleted_at IS NULL) FROM DUAL WHERE @run_promotions;
INSERT INTO `promotion_revision_target` (`id`, `revision_id`, `dimension`, `target_key`)
SELECT (@bt+44), (@bgr+3), 'PRODUCT', (SELECT CAST(MAX(id) AS CHAR) FROM catalogue_products WHERE name = 'Nhẫn bạc mã não cây vuông' AND deleted_at IS NULL) FROM DUAL WHERE @run_promotions;
INSERT INTO `promotion_revision_target` (`id`, `revision_id`, `dimension`, `target_key`)
SELECT (@bt+45), (@bgr+3), 'PRODUCT', (SELECT CAST(MAX(id) AS CHAR) FROM catalogue_products WHERE name = 'Nhẫn bạc mã não cây oval ngang' AND deleted_at IS NULL) FROM DUAL WHERE @run_promotions;
INSERT INTO `promotion_revision_target` (`id`, `revision_id`, `dimension`, `target_key`)
SELECT (@bt+46), (@bgr+3), 'PRODUCT', (SELECT CAST(MAX(id) AS CHAR) FROM catalogue_products WHERE name = 'Nhẫn bạc mã não cây oval dọc' AND deleted_at IS NULL) FROM DUAL WHERE @run_promotions;
INSERT INTO `promotion_revision_target` (`id`, `revision_id`, `dimension`, `target_key`)
SELECT (@bt+47), (@bgr+3), 'PRODUCT', (SELECT CAST(MAX(id) AS CHAR) FROM catalogue_products WHERE name = 'Nhẫn bạc mã não rêu trái tim' AND deleted_at IS NULL) FROM DUAL WHERE @run_promotions;
INSERT INTO `promotion_revision_target` (`id`, `revision_id`, `dimension`, `target_key`)
SELECT (@bt+48), (@bgr+3), 'PRODUCT', (SELECT CAST(MAX(id) AS CHAR) FROM catalogue_products WHERE name = 'Nhẫn bạc mã não rêu giọt nước' AND deleted_at IS NULL) FROM DUAL WHERE @run_promotions;
INSERT INTO `promotion_revision_target` (`id`, `revision_id`, `dimension`, `target_key`)
SELECT (@bt+49), (@bgr+4), 'PRODUCT', (SELECT CAST(MAX(id) AS CHAR) FROM catalogue_products WHERE name = 'Nhẫn bạc ngọc xanh quấn thừng' AND deleted_at IS NULL) FROM DUAL WHERE @run_promotions;
INSERT INTO `promotion_revision_target` (`id`, `revision_id`, `dimension`, `target_key`)
SELECT (@bt+50), (@bgr+4), 'PRODUCT', (SELECT CAST(MAX(id) AS CHAR) FROM catalogue_products WHERE name = 'Nhẫn bạc thạch anh thô' AND deleted_at IS NULL) FROM DUAL WHERE @run_promotions;
INSERT INTO `promotion_revision_target` (`id`, `revision_id`, `dimension`, `target_key`)
SELECT (@bt+51), (@bgr+4), 'PRODUCT', (SELECT CAST(MAX(id) AS CHAR) FROM catalogue_products WHERE name = 'Nhẫn bạc hở tourmaline xanh' AND deleted_at IS NULL) FROM DUAL WHERE @run_promotions;
INSERT INTO `promotion_revision_target` (`id`, `revision_id`, `dimension`, `target_key`)
SELECT (@bt+52), (@bgr+4), 'BRANCH', '2' FROM DUAL WHERE @run_promotions;
INSERT INTO `promotion_revision_target` (`id`, `revision_id`, `dimension`, `target_key`)
SELECT (@bt+53), (@bgr+5), 'PRODUCT', (SELECT CAST(MAX(id) AS CHAR) FROM catalogue_products WHERE name = 'Nhẫn bạc trăng khuyết thạch anh tím' AND deleted_at IS NULL) FROM DUAL WHERE @run_promotions;
INSERT INTO `promotion_revision_target` (`id`, `revision_id`, `dimension`, `target_key`)
SELECT (@bt+54), (@bgr+5), 'PRODUCT', (SELECT CAST(MAX(id) AS CHAR) FROM catalogue_products WHERE name = 'Nhẫn bạc sóng nước peridot' AND deleted_at IS NULL) FROM DUAL WHERE @run_promotions;
INSERT INTO `promotion_revision_target` (`id`, `revision_id`, `dimension`, `target_key`)
SELECT (@bt+55), (@bgr+5), 'PRODUCT', (SELECT CAST(MAX(id) AS CHAR) FROM catalogue_products WHERE name = 'Nhẫn bạc mắt garnet' AND deleted_at IS NULL) FROM DUAL WHERE @run_promotions;
INSERT INTO `promotion_revision_target` (`id`, `revision_id`, `dimension`, `target_key`)
SELECT (@bt+56), (@bgr+5), 'PRODUCT', (SELECT CAST(MAX(id) AS CHAR) FROM catalogue_products WHERE name = 'Nhẫn bạc xoáy citrine' AND deleted_at IS NULL) FROM DUAL WHERE @run_promotions;
INSERT INTO `promotion_revision_target` (`id`, `revision_id`, `dimension`, `target_key`)
SELECT (@bt+57), (@bgr+5), 'PRODUCT', (SELECT CAST(MAX(id) AS CHAR) FROM catalogue_products WHERE name = 'Nhẫn bạc garnet tròn' AND deleted_at IS NULL) FROM DUAL WHERE @run_promotions;
INSERT INTO `promotion_revision_target` (`id`, `revision_id`, `dimension`, `target_key`)
SELECT (@bt+58), (@bgr+5), 'PRODUCT', (SELECT CAST(MAX(id) AS CHAR) FROM catalogue_products WHERE name = 'Nhẫn bạc hổ phách' AND deleted_at IS NULL) FROM DUAL WHERE @run_promotions;
INSERT INTO `promotion_revision_target` (`id`, `revision_id`, `dimension`, `target_key`)
SELECT (@bt+59), (@bgr+5), 'PRODUCT', (SELECT CAST(MAX(id) AS CHAR) FROM catalogue_products WHERE name = 'Nhẫn bạc cành lá citrine' AND deleted_at IS NULL) FROM DUAL WHERE @run_promotions;
INSERT INTO `promotion_revision_target` (`id`, `revision_id`, `dimension`, `target_key`)
SELECT (@bt+60), (@bgr+5), 'PRODUCT', (SELECT CAST(MAX(id) AS CHAR) FROM catalogue_products WHERE name = 'Nhẫn bạc peridot oval' AND deleted_at IS NULL) FROM DUAL WHERE @run_promotions;
INSERT INTO `promotion_revision_target` (`id`, `revision_id`, `dimension`, `target_key`)
SELECT (@bt+61), (@bgr+5), 'PRODUCT', (SELECT CAST(MAX(id) AS CHAR) FROM catalogue_products WHERE name = 'Nhẫn hoa văn vàng thạch anh tím' AND deleted_at IS NULL) FROM DUAL WHERE @run_promotions;
INSERT INTO `promotion_revision_target` (`id`, `revision_id`, `dimension`, `target_key`)
SELECT (@bt+62), (@bgr+5), 'PRODUCT', (SELECT CAST(MAX(id) AS CHAR) FROM catalogue_products WHERE name = 'Nhẫn bạc lá ngọc xanh' AND deleted_at IS NULL) FROM DUAL WHERE @run_promotions;
INSERT INTO `promotion_revision_target` (`id`, `revision_id`, `dimension`, `target_key`)
SELECT (@bt+63), (@bgr+5), 'BRANCH', '1' FROM DUAL WHERE @run_promotions;
INSERT INTO `promotion_revision_target` (`id`, `revision_id`, `dimension`, `target_key`)
SELECT (@bt+64), (@bgr+6), 'PRODUCT', (SELECT CAST(MAX(id) AS CHAR) FROM catalogue_products WHERE name = 'Nhẫn bạc vân búa bản nhọn' AND deleted_at IS NULL) FROM DUAL WHERE @run_promotions;
INSERT INTO `promotion_revision_target` (`id`, `revision_id`, `dimension`, `target_key`)
SELECT (@bt+65), (@bgr+6), 'PRODUCT', (SELECT CAST(MAX(id) AS CHAR) FROM catalogue_products WHERE name = 'Nhẫn bạc mèo xoắn' AND deleted_at IS NULL) FROM DUAL WHERE @run_promotions;
INSERT INTO `promotion_revision_target` (`id`, `revision_id`, `dimension`, `target_key`)
SELECT (@bt+66), (@bgr+7), 'PRODUCT', (SELECT CAST(MAX(id) AS CHAR) FROM catalogue_products WHERE name = 'Nhẫn bạc xoáy citrine' AND deleted_at IS NULL) FROM DUAL WHERE @run_promotions;
INSERT INTO `promotion_revision_target` (`id`, `revision_id`, `dimension`, `target_key`)
SELECT (@bt+67), (@bgr+7), 'PRODUCT', (SELECT CAST(MAX(id) AS CHAR) FROM catalogue_products WHERE name = 'Nhẫn bạc cành lá citrine' AND deleted_at IS NULL) FROM DUAL WHERE @run_promotions;
INSERT INTO `promotion_revision_target` (`id`, `revision_id`, `dimension`, `target_key`)
SELECT (@bt+68), (@bgr+7), 'PRODUCT', (SELECT CAST(MAX(id) AS CHAR) FROM catalogue_products WHERE name = 'Nhẫn bạc sóng nước peridot' AND deleted_at IS NULL) FROM DUAL WHERE @run_promotions;
INSERT INTO `promotion_revision_target` (`id`, `revision_id`, `dimension`, `target_key`)
SELECT (@bt+69), (@bgr+7), 'PRODUCT', (SELECT CAST(MAX(id) AS CHAR) FROM catalogue_products WHERE name = 'Nhẫn bạc peridot oval' AND deleted_at IS NULL) FROM DUAL WHERE @run_promotions;
INSERT INTO `promotion_revision_target` (`id`, `revision_id`, `dimension`, `target_key`)
SELECT (@bt+70), (@bgr+10), 'BRANCH', '1' FROM DUAL WHERE @run_promotions;

-- ===== Workshop automatic discount: Workshop cuối năm – giảm ngay 10% =====
-- Targets every workshop package PUBLISHED now (FR-044: packages only). Own guard, so it is added even where
-- the promotions above already exist; skipped when no package is published yet (run again after publishing one).
SET @run_ws0 = @branches_ok
  AND NOT EXISTS (SELECT 1 FROM promotion_program p JOIN promotion_revision r ON r.id = p.current_revision_id
                  WHERE p.kind = 'AUTO_DISCOUNT' AND r.name = 'Workshop cuối năm – giảm ngay 10%')
  AND EXISTS (SELECT 1 FROM workshop_packages WHERE state = 'PUBLISHED');
SET @wg  = (SELECT COALESCE(MAX(id), 0) + 1 FROM promotion_program);
SET @wr  = (SELECT COALESCE(MAX(id), 0) + 1 FROM promotion_revision);
SET @ws_start0 = DATE_ADD(DATE_FORMAT(UTC_TIMESTAMP(), '%Y-%m-%d %H:%i:00'), INTERVAL 0 DAY);
SET @ws_creator = COALESCE((SELECT id FROM accounts WHERE role = 'MANAGER' AND status = 'ACTIVE' ORDER BY id LIMIT 1), 0);
INSERT INTO promotion_program (id, kind, scope, code_original, code_key, status, current_revision_id, total_limit,
  member_limit, held_count, consumed_count, created_at, created_by, config_version, version)
SELECT @wg, 'AUTO_DISCOUNT', 'WORKSHOP', NULL, NULL, 'ENABLED', @wr, NULL, NULL, NULL, NULL, UTC_TIMESTAMP(6), @ws_creator, 0, 1
FROM DUAL WHERE @run_ws0;
INSERT INTO promotion_revision (id, program_id, revision_no, name, start_at, end_at, discount_kind, percent, fixed_amount,
  max_discount_amount, minimum_eligible_amount, allow_points, created_at, created_by)
SELECT @wr, @wg, 1, 'Workshop cuối năm – giảm ngay 10%', @ws_start0, DATE_ADD(@ws_start0, INTERVAL 45 DAY), 'PERCENT',
  10, NULL, 150000,
  0, 1, UTC_TIMESTAMP(6), @ws_creator
FROM DUAL WHERE @run_ws0;
INSERT INTO promotion_revision_target (revision_id, dimension, target_key)
SELECT @wr, 'PACKAGE', CAST(id AS CHAR) FROM workshop_packages WHERE state = 'PUBLISHED' AND @run_ws0 ORDER BY id;

COMMIT;


-- ===========================================================================
-- 8. MEMBER VOUCHER ASSIGNMENTS & LOYALTY POINTS (FE-06)
-- ===========================================================================

-- Save all enabled vouchers to every Member account
INSERT INTO promotion_member_program (program_id, member_id, saved_at, held_count, consumed_count, version)
SELECT p.id, a.id, @now, 0, 0, 0
FROM promotion_program p CROSS JOIN accounts a
WHERE a.role = 'MEMBER' AND p.kind = 'VOUCHER' AND p.status = 'ENABLED'
ON DUPLICATE KEY UPDATE saved_at = COALESCE(saved_at, @now);

-- Member Loyalty Wallet & 100,000 Points Grant
SELECT current_policy_id INTO @policy_id FROM loyalty_registry WHERE id = 1;

INSERT INTO loyalty_wallet (member_id, balance_points, held_points, version)
SELECT a.id, 0, 0, 0 FROM accounts a WHERE a.role = 'MEMBER'
ON DUPLICATE KEY UPDATE member_id = member_id;

INSERT INTO loyalty_accepted_benefit (source_kind, source_ref, member_id, member_origin_eligible, accepted_at,
                                      original_deadline, source_revision, accepted_terms_digest, policy_id,
                                      program_id, revision_id, gross_amount, program_discount, requested_points,
                                      point_discount, earn_base, post_promotion_amount, points_state, voucher_state,
                                      earn_state, earned_points, settled_at, source_outcome_version, version)
SELECT 'RETAIL', CONCAT('seed-points-member-', a.id), a.id, 1, @now, @now, 0, 'seed-demo-digest', @policy_id,
       NULL, NULL, 0, 0, 0, 0, 0, NULL, 'NONE', 'NONE', 'GRANTED', 100000, @now, 0, 0
FROM accounts a
WHERE a.role = 'MEMBER'
  AND NOT EXISTS (
      SELECT 1 FROM loyalty_accepted_benefit b
      WHERE b.source_kind = 'RETAIL' AND b.source_ref = CONCAT('seed-points-member-', a.id)
  );

INSERT INTO loyalty_point_ledger (member_id, accepted_benefit_id, entry_kind, delta_balance, delta_held,
                                  balance_after, held_after, occurred_at, original_debit_id)
SELECT b.member_id, b.id, 'EARN', 100000, 0, 100000, 0, @now, NULL
FROM loyalty_accepted_benefit b
JOIN accounts a ON a.id = b.member_id AND a.role = 'MEMBER'
WHERE b.source_kind = 'RETAIL' AND b.source_ref LIKE 'seed-points-member-%'
  AND NOT EXISTS (
      SELECT 1 FROM loyalty_point_ledger l
      WHERE l.accepted_benefit_id = b.id AND l.entry_kind = 'EARN'
  );

UPDATE loyalty_wallet w
JOIN accounts a ON a.id = w.member_id AND a.role = 'MEMBER'
SET w.balance_points = 100000, w.version = w.version + 1;

SET FOREIGN_KEY_CHECKS = @old_fk;
