---
id: E2E-001-007
feature: 001-account-management
test_level: e2e
test_case_type: B
source_ref: spec.md#FR-009,FR-010,FR-011,tasks.md#T039,tasks.md#T080
target_url: http://localhost:5173/admin/roles
actor_role: Admin
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
  - http://localhost:8080
test_data:
  fixture: admin-role-catalogue-verification
  account_ref: ADMIN_001
  isolation_key: E2E-001-007-20261114
cleanup:
  required: false
  method: none-client-only
---

# E2E-001-007: Danh mục vai trò cố định — Bảo vệ RBAC Guard và xác minh mô tả thẩm quyền Manager toàn cục vs Staff cơ sở

**Ngày tạo**: 2026-11-05  
**Ngày cập nhật**: 2026-11-14  
**Người phụ trách**: Phan Sơn  
**Người cập nhật cuối**: Phan Sơn  

## 1. Tiền điều kiện (Preconditions)

- [x] Môi trường Frontend Vite đang chạy tại `http://localhost:5173`.
- [x] Môi trường Backend Spring Boot đang chạy tại `http://localhost:8080`.
- [x] Công cụ `browsermcp` đã được nạp và kích hoạt trong phiên làm việc.
- [x] Người dùng thực hiện 2 luồng: (1) Tài khoản Member thử truy cập trái phép; (2) Tài khoản Admin truy cập hợp lệ để kiểm tra nội dung danh mục vai trò.

## 2. Phân tích rủi ro đối nghịch (Adversarial Risk Analysis)

- **Bảo vệ RBAC Guard (FR-010):** Tuyệt đối không cho phép tài khoản `MEMBER` xem hoặc thao tác cấu trúc vai trò nội bộ. Khi Member truy cập `/admin/roles`, hệ thống phải chặn lại, redirect về `/` với thông báo từ chối truy cập an toàn (403), giữ nguyên session đăng nhập.
- **Tính bất biến của danh mục vai trò cố định (FR-009):** Danh mục vai trò là read-only, cố định đúng 4 vai trò (`MEMBER`, `STAFF`, `MANAGER`, `ADMIN`). Không có nút thêm/sửa/xóa vai trò hoặc cấp quyền động (RBAC động).
- **Mô tả thẩm quyền theo Revision R-MGA (T039, T080):**
  - **MANAGER:** Mô tả phải thể hiện rõ: *Quản lý có thẩm quyền vận hành toàn cục đối với các chức năng phân quyền cho Manager; phân công cơ sở là thông tin tổ chức, không thu hẹp quyền vận hành*.
  - **STAFF:** Mô tả thể hiện rõ: *Nhân viên vận hành trong phạm vi cơ sở trực thuộc bắt buộc*.
  - **ADMIN:** Mô tả thể hiện rõ: *Quản trị viên kỹ thuật, tài khoản và tham số vận hành*.

## 3. Các bước kiểm thử trên trình duyệt (Browser Action Steps)

### Phần A — Kiểm tra bảo vệ RBAC Guard khi Member truy cập
```browser
# Đăng nhập với tài khoản Member
goto http://localhost:5173/admin/roles
wait 1000
# Assertion: Hệ thống không hiển thị nội dung danh mục vai trò
# Assertion: URL được chuyển hướng an toàn về trang chủ "/" hoặc hiển thị thông báo 403 không có quyền
```

### Phần B — Đăng nhập tài khoản Admin và xác minh nội dung danh mục vai trò
```browser
goto http://localhost:5173/admin/roles
wait getByRole('heading', { name: 'Danh mục vai trò' })
```

### Step 2 — Kiểm tra 4 vai trò cố định và tính chất Read-Only
```browser
# Quan sát danh sách 4 vai trò: MEMBER, STAFF, MANAGER, ADMIN
# Assertion: Không tồn tại các nút "Thêm vai trò", "Chỉnh sửa quyền" hoặc form tạo mới
```

### Step 3 — Xác minh mô tả thẩm quyền của MANAGER và STAFF theo Revision R-MGA
```browser
# Assertion: Thẻ vai trò MANAGER hiển thị mô tả quyền vận hành toàn cục và phân công cơ sở mang tính tổ chức
# Assertion: Thẻ vai trò STAFF hiển thị mô tả phạm vi giới hạn theo cơ sở trực thuộc bắt buộc
# Assertion: Thẻ vai trò ADMIN thể hiện rõ vai trò quản trị kỹ thuật/vận hành, không thay thế nghiệp vụ Manager/Staff
```

## 4. Kết quả kỳ vọng (Expected Assertions)

```browser
find "Danh mục vai trò"
# Expected: one or more matches
find "thẩm quyền vận hành toàn cục"
# Expected: one or more matches (trong mô tả Manager)
find "phạm vi cơ sở trực thuộc bắt buộc"
# Expected: one or more matches (trong mô tả Staff)
find "Thêm vai trò"
# Expected: zero matches (bảo đảm tính cố định, không dynamic RBAC)
```

## 5. Bằng chứng kiểm thử (Evidence)

- Kịch bản chạy thực tế qua Chrome bằng `browsermcp`.
- Ảnh chụp màn hình: `screenshots/E2E-001-007--admin-role-catalogue-mga.jpg`.

## 6. Lịch sử thực thi

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-14T07:23:30Z
- Executed by: Phan Sơn
- Test tool: Chrome via browsermcp
- Defect verification: [BUG-E2E-0005](../../../../ppswbs-testing/BUG-LOG.md#bug-e2e-0005--closed) (CLOSED & VERIFIED)
- Actual results:
  - RBAC Guard and API endpoint `GET /api/v1/roles` returned 200 OK with 4 system roles.
  - Table renders all 4 system roles (`MEMBER`, `STAFF`, `MANAGER`, `ADMIN`) along with their scope descriptions (`Khách hàng thành viên / Member`, `Nhân viên cơ sở / Staff`, `Quản lý cơ sở / Manager`, `Quản trị viên / Admin`).
  - Read-only integrity verified: No buttons exist to add, edit, or delete system roles.
  - Role Governance Rules clearly state Staff 1-location restriction vs Manager global operational authority.
<!-- EXECUTION:END -->
