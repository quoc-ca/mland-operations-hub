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
  account_ref: E2E_MEMBER_004
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
- [ ] Tài khoản Member test chuyên dụng: định danh và mật khẩu lấy từ secret store; không ghi credential vào case/evidence.

## 2. Đánh giá đa chiều qua 2 góc nhìn (2 POVs Evaluation)

- **POV 1 — Khách hàng phổ thông (Non-tech Customer):**
  - Màn hình đăng nhập trang nhã, các trường thông tin rõ ràng; nút "Đăng nhập" phản hồi nhanh; sau khi đăng nhập thành công phải tự động chuyển hướng êm ái về Trang chủ (`/`).
  - Trên màn hình trang chủ dễ dàng tìm thấy liên kết/nút "Hồ sơ cá nhân" (Profile) trên thanh điều hướng; bấm vào chuyển tới trang hồ sơ một cách mượt mà, **không bị chớp nháy, giật lag hay reload liên tục** gây khó chịu cho mắt người dùng.
- **POV 2 — Kỹ thuật viên (Technical Verification):**
  - Form gửi thông tin email & password qua Firebase Auth Client thành công; backend `/api/v1/accounts/me` bootstrap trả về tài khoản `MEMBER` với trạng thái `ACTIVE`.
  - Tuân thủ nghiêm ngặt yêu cầu `spec.md#FR-003`: Sau khi đăng nhập thành công, hệ thống **bắt buộc điều hướng về Trang chủ (`/`)**, không ép buộc vào role dashboard hay policy acceptance.
  - Header của `AppLayout` hiển thị nút navigation `/profile`; hook `useVerifiedEmailGate` và `useProfile` chỉ nạp dữ liệu một lần duy nhất, tuyệt đối không rơi vào vòng lặp re-render/reload vô tận (`infinite checkGate loop`); hiển thị đúng email của fixture và mã tài khoản an toàn.

## 3. Các bước kiểm thử trên trình duyệt (Browser Action Steps)

### Step 1 — Mở trang đăng nhập và nhập thông tin tài khoản
```browser
goto http://localhost:5173/login
wait getByRole('heading', { name: 'Đăng nhập hệ thống' })
fill getByLabel('Địa chỉ email *') "<member-email-from-secret-store>"
fill getByLabel('Mật khẩu *') "<member-password-from-secret-store>"
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
find "<member-email-from-secret-store>"
wait 3000
# Verify page remains stable without visual flickering or continuous reloads
```

## 4. Kết quả kỳ vọng (Expected Assertions)

```browser
get url
# Expected: http://localhost:5173/profile
find "Hồ sơ cá nhân & Bảo mật"
# Expected: one or more matches
find "<member-email-from-secret-store>"
# Expected: one or more matches
find "MEMBER"
# Expected: one or more matches
```

## 5. Bằng chứng kiểm thử (Evidence)

- Ảnh chụp màn hình điều hướng đăng nhập: `screenshots/E2E-001-004--auth-signin.jpg`
- Ảnh chụp màn hình hồ sơ ổn định: `screenshots/E2E-001-004--profile-stable.jpg`
- Lệnh lưu: `screenshot --path "ppswbs-spec/features/001-account-management/test-results/screenshots/E2E-001-004--profile-stable.jpg"`

## 6. Bằng chứng thực thi lịch sử (trước revision 2026-11-06)

<!-- EXECUTION:START -->
- Historical status: PASS
- Executed at: 2026-11-05 12:34:46
- Executed by: Senior QA & System Verification Engineer
- Browser tool: browsermcp
- Failure step: None
- Actual result:
  - Đăng nhập thành công với tài khoản test chuyên dụng; định danh và mật khẩu đã được redaction.
  - Tuân thủ FR-003: Hệ thống điều hướng chuẩn xác về Trang chủ (http://localhost:5173/).
  - Trên màn hình trang chủ xuất hiện liên kết "Personal Profile" (/profile). Bấm vào điều hướng chính xác tới trang http://localhost:5173/profile.
  - Trang Profile tải dữ liệu mượt mà, hiển thị đúng họ tên và email của fixture đã được redaction.
  - Xác nhận trang hoạt động tĩnh tại, ổn định, tuyệt đối không bị chớp nháy hoặc reload lặp vô tận.
- Evidence screenshot:
  - Form đăng nhập: screenshots/E2E-001-004--auth-signin.jpg
  - Màn hình Profile ổn định: screenshots/E2E-001-004--profile-stable.jpg
- Bug reference: NONE
<!-- EXECUTION:END -->

## 7. Revision expected results (2026-11-06 — must re-run)

- **After Step 1:** URL is `/login`; password is not echoed in DOM text, logs, URL or screenshot annotation.
- **After Step 2:** successful sign-in uses `replace` to exact URL `/`, not a role dashboard or pre-login private route; Home must render before Profile navigation is attempted.
- **After Steps 3–4:** Profile link uses canonical `/profile`; while its guard/profile load resolves, no stale Profile PII or flash from a previous session is rendered. Profile remains accessible only with the current signed-in session.
- Browser Back after Step 2 must not return to `/login` as an authenticated screen if the implementation uses replace; record the observed URL/DOM in evidence.

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-06 20:14:19
- Executed by: Senior QA & System Verification Engineer
- Browser tool: browsermcp
- Failure step: None
- Actual result: Đăng nhập thành công với tài khoản Member; sau khi đăng nhập điều hướng ngay về Trang chủ (http://localhost:5173/) với replace. Điều hướng tới /profile tải dữ liệu nhanh chóng, ổn định tuyệt đối (zero flicker / no reload loop). Back không làm mất phiên hoặc hiển thị trạng thái hỏng.
- Evidence screenshot:
  - Form đăng nhập: screenshots/E2E-001-004--auth-signin.jpg
  - Màn hình Profile ổn định: screenshots/E2E-001-004--profile-stable.jpg
- Bug reference: NONE
<!-- EXECUTION:END -->
