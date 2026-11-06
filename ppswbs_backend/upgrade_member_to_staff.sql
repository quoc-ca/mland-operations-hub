-- Chuyển một tài khoản MEMBER → STAFF và gán vào chi nhánh #1.
-- Cách dùng:
--   Bước 1: xem danh sách MEMBER ACTIVE (copy id anh muốn dùng):
--     SELECT id, login_email, role, status FROM accounts WHERE role = 'MEMBER' AND status = 'ACTIVE' ORDER BY id LIMIT 10;
--   Bước 2: thay <YOUR_ACCOUNT_ID> bên dưới bằng id vừa copy, rồi chạy toàn bộ file.
-- Kết quả: tài khoản đó được đổi thành STAFF (ACTIVE) và có 1 assignment Active vào location_id = 1.
--
-- Lưu ý: auth của app đọc role/assignment từ bảng này (qua AccountFacade/AccountRepository),
-- không có cache role theo firebase_uid khác, nhưng cân nhắc restart backend nếu đang chạy
-- để chắc chắn không có gì đang giữ snapshot cũ.

START TRANSACTION;

-- Kiểm tra tài khoản (phải là MEMBER + ACTIVE)
SELECT id, login_email, role, status, version
FROM accounts
WHERE id = <YOUR_ACCOUNT_ID>
FOR UPDATE;

UPDATE accounts
SET role = 'STAFF',
    version = version + 1
WHERE id = <YOUR_ACCOUNT_ID>
  AND role = 'MEMBER'
  AND status = 'ACTIVE';

-- Tạo assignment chi nhánh #1 nếu chưa có (active)
INSERT INTO account_location_assignments (account_id, location_id, assigned_at, active)
SELECT <YOUR_ACCOUNT_ID>, 1, NOW(6), 1
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1
    FROM account_location_assignments
    WHERE account_id = <YOUR_ACCOUNT_ID>
      AND location_id = 1
      AND active = 1
);

SELECT a.id, a.login_email, a.role, a.status, a.version,
       GROUP_CONCAT(CASE WHEN ala.active = 1 THEN ala.location_id END ORDER BY ala.location_id) AS active_locations
FROM accounts a
LEFT JOIN account_location_assignments ala ON ala.account_id = a.id
WHERE a.id = <YOUR_ACCOUNT_ID>
GROUP BY a.id, a.login_email, a.role, a.status, a.version;

COMMIT;

-- Nếu UPDATE ở trên ảnh hưởng 0 hàng → tài khoản không phải MEMBER ACTIVE (có thể đã là STAFF/MANAGER).
-- Khi đăng nhập lại, app sẽ nhận role STAFF và quyền tại chi nhánh #1.
-- Vào http://localhost:5173/staff/retail-shipments để quản lý vận đơn.
