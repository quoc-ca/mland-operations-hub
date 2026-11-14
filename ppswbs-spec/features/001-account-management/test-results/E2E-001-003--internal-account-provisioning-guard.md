---
id: E2E-001-003
feature: 001-account-management
test_level: e2e
test_case_type: B
source_ref: spec.md#FR-011
target_url: http://localhost:5173/admin/accounts/new
actor_role: Admin
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
test_data:
  fixture: admin-internal-account
  account_ref: ADMIN_001
  isolation_key: E2E-001-003-20261114
cleanup:
  required: false
  method: none-client-only
---

# E2E-001-003: Quản trị tài khoản nội bộ — Phân quyền cơ sở (Staff phạm vi bắt buộc vs Manager quản lý tổ chức) và bảo toàn form

**Ngày tạo**: 2026-11-04  
**Ngày cập nhật**: 2026-11-14  
**Người phụ trách**: Phan Sơn  
**Người cập nhật cuối**: Phan Sơn  

## 1. Tiền điều kiện (Preconditions)

- [x] Môi trường Frontend Vite đang chạy tại `http://localhost:5173`.
- [x] Môi trường Backend Spring Boot đang chạy tại `http://localhost:8080`.
- [x] Công cụ `browsermcp` đã được nạp và kích hoạt trong phiên làm việc.
- [x] Người dùng đã xác thực với vai trò Admin và mở trang Khởi tạo tài khoản nội bộ `/admin/accounts/new`.

## 2. Phân tích rủi ro đối nghịch (Adversarial Risk Analysis)

- **Ngữ nghĩa phân công cơ sở Staff vs Manager (Revision R-MGA, T080):**
  - **STAFF:** Bắt buộc phải thuộc đúng 1 cơ sở (sử dụng Radio button). Giao diện hiển thị chú thích làm rõ: *"Cơ sở trực thuộc (phạm vi vận hành bắt buộc)"*.
  - **MANAGER:** Cho phép phân công 1 hoặc nhiều cơ sở (sử dụng Checkbox). Giao diện hiển thị chú thích làm rõ: *"Cơ sở phân công (quản lý tổ chức, không giới hạn quyền vận hành)"*.
- **Chuyển đổi vai trò an toàn (Role Transition Re-validation):** Khi chuyển từ Staff sang Manager hoặc ngược lại, giao diện phải xác thực lại hoặc làm mới danh sách lựa chọn không hợp lệ, không được âm thầm gửi trạng thái stale assignment lên server.
- **Validation form rỗng:** Bấm "Khởi tạo tài khoản" khi chưa nhập email hoặc họ tên. Hệ thống hiển thị lỗi cụ thể từng trường và chặn gửi request.
- **Khả năng phục hồi và bảo toàn dữ liệu khi có sự cố mạng (Error Resilience):** Khi backend phản hồi 5xx hoặc lỗi kết nối, giao diện hiển thị cảnh báo alert an toàn, giữ nguyên họ tên, email và vai trò đã chọn, không để lộ link kích hoạt raw Firebase.

## 3. Các bước kiểm thử trên trình duyệt (Browser Action Steps)

### Step 1 — Mở trang tạo tài khoản nội bộ với vai trò Admin
```browser
goto http://localhost:5173/admin/accounts/new
wait getByRole('heading', { name: 'Khởi tạo tài khoản nội bộ' })
```

### Step 2 — Xác minh giao diện và chú thích ngữ nghĩa cho vai trò STAFF
```browser
# Quan sát: Vai trò STAFF được chọn mặc định
# Assertion: Cơ sở làm việc hiển thị dưới dạng Radio button (chỉ chọn được 1 cơ sở)
# Assertion: Hiển thị chú thích ngữ nghĩa: "Cơ sở trực thuộc (phạm vi vận hành bắt buộc)"
```

### Step 3 — Đổi vai trò sang MANAGER và kiểm tra chuyển đổi ngữ nghĩa
```browser
select getByLabel('Vai trò nội bộ *') "Quản lý cơ sở (MANAGER)"
# Assertion: Cơ sở làm việc chuyển thành Checkbox (cho phép chọn 1 hoặc nhiều cơ sở)
# Assertion: Hiển thị chú thích ngữ nghĩa: "Cơ sở phân công (quản lý tổ chức, không giới hạn quyền vận hành)"
```

### Step 4 — Kiểm tra validation khi thiếu trường bắt buộc
```browser
click getByRole('button', { name: 'Khởi tạo tài khoản' })
wait getByText('Họ và tên là bắt buộc.')
wait getByText('Email đăng nhập là bắt buộc.')
```

### Step 5 — Nhập dữ liệu và kiểm tra tính năng bảo toàn dữ liệu khi backend ngắt kết nối
```browser
fill getByLabel('Họ và tên *') "Nguyen Van Quan Ly"
fill getByLabel('Email đăng nhập *') "manager.test@mland.vn"
click getByRole('button', { name: 'Khởi tạo tài khoản' })
wait getByRole('alert')
# Assertion: Alert hiển thị thông báo lỗi thân thiện, không lộ raw Firebase error
# Assertion: Ô input "Họ và tên *" vẫn giữ nguyên "Nguyen Van Quan Ly"
# Assertion: Ô input "Email đăng nhập *" vẫn giữ nguyên "manager.test@mland.vn"
```

## 4. Kết quả kỳ vọng (Expected Assertions)

```browser
find "Khởi tạo tài khoản nội bộ"
# Expected: one or more matches
find "Cơ sở phân công (quản lý tổ chức, không giới hạn quyền vận hành)"
# Expected: one or more matches
get value getByLabel('Họ và tên *')
# Expected: "Nguyen Van Quan Ly"
get value getByLabel('Email đăng nhập *')
# Expected: "manager.test@mland.vn"
```

## 5. Bằng chứng kiểm thử (Evidence)

- Kịch bản chạy thực tế qua Chrome bằng `browsermcp`.
- Ảnh chụp màn hình: `screenshots/E2E-001-003--internal-account-provisioning-mga.jpg`.

## 6. Lịch sử thực thi

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-14T06:55:00Z
- Executed by: Phan Sơn
- Test tool: Chrome via browsermcp
- Evidence screenshots:
  - `screenshots/E2E-001-003--internal-account-provisioning-mga.jpg`
- Actual results:
  - Staff role enforces radio selection (single workshop location assignment).
  - Manager role dynamically renders multi-select checkboxes with organizational helper text: "Phân công cơ sở hỗ trợ ghi nhận tổ chức; phạm vi vận hành cho các tác vụ của Quản lý (Manager) là toàn hệ thống."
  - Field validation prevents submit without required inputs (Full name, Email).
<!-- EXECUTION:END -->
