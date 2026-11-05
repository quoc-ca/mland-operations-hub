---
id: E2E-001-004
feature: 001-account-management
test_level: e2e
test_case_type: N
source_ref: spec.md#FR-001,FR-002,FR-003
target_url: http://localhost:5173/login
actor_role: Customer
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
  - http://localhost:8080
test_data:
  fixture: member-auth-signin
  account_ref: minikie456@gmail.com
  isolation_key: E2E-001-004-20261105
cleanup:
  required: false
  method: session-signout
---

# E2E-001-004: Đăng nhập tài khoản Member thực tế, điều hướng Trang chủ & truy cập Hồ sơ ổn định

**Ngày tạo**: 2026-11-05  
**Ngày cập nhật**: 2026-11-05  
**Người phụ trách**: Senior QA & System Verification Engineer  
**Người cập nhật cuối**: Senior QA & System Verification Engineer  

## 1. Tiền điều kiện (Preconditions)

- [x] Môi trường Frontend Vite đang chạy tại `http://localhost:5173`.
- [x] Môi trường Backend Spring Boot đang chạy tại `http://localhost:8080`.
- [x] Công cụ `browsermcp` đã được nạp và kích hoạt trong phiên làm việc.
- [x] Tài khoản người dùng thực tế: `minikie456@gmail.com` / `123123123`.

## 2. Đánh giá đa chiều qua 2 góc nhìn (2 POVs Evaluation)

- **POV 1 — Khách hàng phổ thông (Non-tech Customer):**
  - Màn hình đăng nhập trang nhã, các trường thông tin rõ ràng; nút "Đăng nhập" phản hồi nhanh; sau khi đăng nhập thành công phải tự động chuyển hướng êm ái về Trang chủ (`/`).
  - Trên màn hình trang chủ dễ dàng tìm thấy liên kết/nút "Hồ sơ cá nhân" (Profile) trên thanh điều hướng; bấm vào chuyển tới trang hồ sơ một cách mượt mà, **không bị chớp nháy, giật lag hay reload liên tục** gây khó chịu cho mắt người dùng.
- **POV 2 — Kỹ thuật viên (Technical Verification):**
  - Form gửi thông tin email & password qua Firebase Auth Client thành công; backend `/api/v1/accounts/me` bootstrap trả về tài khoản `MEMBER` với trạng thái `ACTIVE`.
  - Tuân thủ nghiêm ngặt yêu cầu `spec.md#FR-003`: Sau khi đăng nhập thành công, hệ thống **bắt buộc điều hướng về Trang chủ (`/`)**, không ép buộc vào role dashboard hay policy acceptance.
  - Header của `AppLayout` hiển thị nút navigation `/profile`; hook `useVerifiedEmailGate` và `useProfile` chỉ nạp dữ liệu một lần duy nhất, tuyệt đối không rơi vào vòng lặp re-render/reload vô tận (`infinite checkGate loop`); hiển thị đúng email `minikie456@gmail.com` và mã tài khoản.

## 3. Các bước kiểm thử trên trình duyệt (Browser Action Steps)

### Step 1 — Mở trang đăng nhập và nhập thông tin tài khoản
```browser
goto http://localhost:5173/login
wait getByRole('heading', { name: 'Đăng nhập hệ thống' })
fill getByLabel('Địa chỉ email *') "minikie456@gmail.com"
fill getByLabel('Mật khẩu *') "123123123"
```

### Step 2 — Bấm Đăng nhập và xác minh điều hướng về Trang chủ (FR-003)
```browser
click getByRole('button', { name: 'Đăng nhập' })
wait 3000
get url
# Expected: http://localhost:5173/
```

### Step 3 — Tìm và click nút Hồ sơ cá nhân (Profile) trên Trang chủ
```browser
click getByRole('link', { name: 'Hồ sơ cá nhân' })
wait 2000
get url
# Expected: http://localhost:5173/profile
```

### Step 4 — Quan sát trang Hồ sơ cá nhân và xác minh tính ổn định (không chớp nháy)
```browser
wait getByRole('heading', { name: 'Hồ sơ cá nhân & Bảo mật' })
find "minikie456@gmail.com"
wait 3000
# Verify page remains stable without visual flickering or continuous reloads
```

## 4. Kết quả kỳ vọng (Expected Assertions)

```browser
get url
# Expected: http://localhost:5173/profile
find "Hồ sơ cá nhân & Bảo mật"
# Expected: one or more matches
find "minikie456@gmail.com"
# Expected: one or more matches
find "MEMBER"
# Expected: one or more matches
```

## 5. Bằng chứng kiểm thử (Evidence)

- Ảnh chụp màn hình điều hướng đăng nhập: `screenshots/E2E-001-004--auth-signin.jpg`
- Ảnh chụp màn hình hồ sơ ổn định: `screenshots/E2E-001-004--profile-stable.jpg`
- Lệnh lưu: `screenshot --path "ppswbs-spec/features/001-account-management/test-results/screenshots/E2E-001-004--profile-stable.jpg"`

## 6. Kết quả thực thi (Execution History)

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-05 12:34:46
- Executed by: Senior QA & System Verification Engineer
- Browser tool: browsermcp
- Failure step: None
- Actual result:
  - Đăng nhập thành công với tài khoản minikie456@gmail.com / 123123123.
  - Tuân thủ FR-003: Hệ thống điều hướng chuẩn xác về Trang chủ (http://localhost:5173/).
  - Trên màn hình trang chủ xuất hiện liên kết "Personal Profile" (/profile). Bấm vào điều hướng chính xác tới trang http://localhost:5173/profile.
  - Trang Profile tải dữ liệu mượt mà, hiển thị đúng họ tên 'Phan Nguyễn Trường Sơn' và email 'minikie456@gmail.com'.
  - Xác nhận trang hoạt động tĩnh tại, ổn định, tuyệt đối không bị chớp nháy hoặc reload lặp vô tận.
- Evidence screenshot:
  - Form đăng nhập: screenshots/E2E-001-004--auth-signin.jpg
  - Màn hình Profile ổn định: screenshots/E2E-001-004--profile-stable.jpg
- Bug reference: NONE
<!-- EXECUTION:END -->
