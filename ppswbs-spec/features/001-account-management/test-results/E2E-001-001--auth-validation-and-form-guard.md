---
id: E2E-001-001
feature: 001-account-management
test_level: e2e
test_case_type: B
source_ref: spec.md#FR-001
target_url: http://localhost:5173/login
actor_role: Guest
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
test_data:
  fixture: guest-auth-validation
  account_ref: GUEST_001
  isolation_key: E2E-001-001-20261104
cleanup:
  required: false
  method: none-client-only
---

# E2E-001-001: Xác minh tính đối nghịch và validation form Đăng nhập / Đăng ký / Quên mật khẩu

**Ngày tạo**: 2026-11-04  
**Ngày cập nhật**: 2026-11-04  
**Người phụ trách**: Senior QA & System Verification Engineer  
**Người cập nhật cuối**: Senior QA & System Verification Engineer  

## 1. Tiền điều kiện (Preconditions)

- [x] Môi trường Frontend Vite đang chạy tại `http://localhost:5173`.
- [x] Công cụ `browsermcp` đã được nạp và kích hoạt trong phiên làm việc.
- [x] Phiên đăng nhập: Guest (chưa đăng nhập).

## 2. Phân tích rủi ro đối nghịch (Adversarial Risk Analysis)

- **Input rỗng / submit liên tục:** Người dùng click nút submit khi chưa nhập liệu hoặc nhập khoảng trắng. Kỳ vọng chặn ngay tại client, highlight trường invalid và hiển thị thông báo lỗi cụ thể bằng tiếng Việt, không gửi request vô ích.
- **Mật khẩu không khớp:** Người dùng nhập mật khẩu xác nhận không trùng khớp. Kỳ vọng báo lỗi `Mật khẩu xác nhận không khớp` và giữ nguyên email/mật khẩu trong state.
- **Độ dài mật khẩu biên (< 6 ký tự):** Nhập mật khẩu 3 ký tự `123`. Kỳ vọng chặn với lỗi `Mật khẩu phải chứa ít nhất 6 ký tự.`
- **Bảo vệ quyền riêng tư form quên mật khẩu:** Form khôi phục mật khẩu không để lộ tài khoản có tồn tại hay không, chặn khi email rỗng.

## 3. Các bước kiểm thử trên trình duyệt (Browser Action Steps)

### Step 1 — Mở trang đăng nhập và kiểm tra validation rỗng
```browser
goto http://localhost:5173/login
wait getByRole('heading', { name: 'Đăng nhập hệ thống' })
click getByRole('button', { name: 'Đăng nhập' })
wait getByText('Vui lòng nhập địa chỉ email.')
```

### Step 2 — Chuyển sang form Đăng ký và kiểm tra lỗi mật khẩu không khớp
```browser
goto http://localhost:5173/register
wait getByRole('heading', { name: 'Đăng ký tài khoản' })
fill getByLabel('Địa chỉ email *') "tester@example.com"
fill getByLabel('Mật khẩu *') "password123"
fill getByLabel('Xác nhận mật khẩu *') "password456"
click getByRole('button', { name: 'Đăng ký' })
wait getByText('Mật khẩu xác nhận không khớp.')
```

### Step 3 — Kiểm tra điều kiện biên mật khẩu dưới 6 ký tự
```browser
fill getByLabel('Mật khẩu *') "123"
fill getByLabel('Xác nhận mật khẩu *') "123"
click getByRole('button', { name: 'Đăng ký' })
wait getByText('Mật khẩu phải chứa ít nhất 6 ký tự.')
```

### Step 4 — Kiểm tra form quên mật khẩu
```browser
goto http://localhost:5173/forgot-password
wait getByRole('heading', { name: 'Khôi phục mật khẩu' })
click getByRole('button', { name: 'Gửi liên kết đặt lại' })
wait getByText('Vui lòng nhập địa chỉ email.')
```

## 4. Kết quả kỳ vọng (Expected Assertions)

```browser
get url
# Expected: http://localhost:5173/forgot-password
find "Khôi phục mật khẩu"
# Expected: one or more matches
find "Vui lòng nhập địa chỉ email."
# Expected: one or more matches
```

## 5. Bằng chứng kiểm thử (Evidence)

- Đường dẫn ảnh chụp màn hình: `screenshots/login-page.jpg`
- Lệnh lưu bằng chứng: `screenshot --path "ppswbs-spec/features/001-account-management/test-results/screenshots/login-page.jpg"`

## 6. Bằng chứng thực thi lịch sử (trước revision 2026-11-06)

<!-- EXECUTION:START -->
- Historical status: PASS
- Executed at: 2026-11-04 04:24:00
- Executed by: Senior QA & System Verification Engineer
- Browser tool: browsermcp
- Failure step: None
- Actual result: Form validation hoạt động hoàn toàn chính xác theo đặc tả; bắt đúng lỗi rỗng, mật khẩu không khớp, độ dài < 6 ký tự và hiển thị tiếng Việt thân thiện.
- Evidence screenshot: screenshots/login-page.jpg
- Bug reference: NONE
<!-- EXECUTION:END -->

## 7. Revision expected results (2026-11-06 — must re-run)

Historical PASS does not cover these assertions; status is superseded until rerun.

- **After Step 1:** URL remains exactly `/login`; the email-required message is adjacent to/associated with the Email control, focus stays in the form, no Home/Profile content appears, and no authentication request is sent.
- **After Step 2–3:** URL remains exactly `/register`; only the relevant validation messages appear; the registration action does not navigate or create a Firebase/Account request while client validation fails.
- **After Step 4:** URL remains exactly `/forgot-password`; response text is generic and does not reveal whether the typed email exists; no reset/action code is rendered or written to the URL.

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-09 16:31:20
- Executed by: Senior QA & System Verification Engineer
- Browser tool: browsermcp
- Failure step: None
- Actual result: Form validation trên /login, /register, /forgot-password được tái kiểm thử và hoạt động chuẩn xác theo đặc tả đối nghịch: bắt đúng lỗi rỗng, mật khẩu không khớp, mật khẩu < 6 ký tự; URL giữ nguyên không chuyển hướng; không gửi request trái phép khi client validation thất bại.
- Evidence screenshot:
  - Login validation: screenshots/E2E-001-001--login-validation.jpg
  - Register mismatch: screenshots/E2E-001-001--register-mismatch.jpg
  - Register length: screenshots/E2E-001-001--register-length.jpg
  - Forgot validation: screenshots/E2E-001-001--forgot-validation.jpg
- Bug reference: NONE
<!-- EXECUTION:END -->

