---
id: E2E-001-011
feature: 001-account-management
test_level: e2e
test_case_type: B
source_ref: spec.md#FR-006
target_url: http://localhost:5173/profile
actor_role: Customer
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
  - http://localhost:8080
test_data:
  fixture: member-email-change-request
  account_ref: E2E_MEMBER_011
  target_new_email: <target-email-fixture-from-secret-store>
  isolation_key: E2E-001-011-20261105
cleanup:
  required: false
  method: none-client-only
---

# E2E-001-011: Yêu cầu Thay đổi Địa chỉ Email Đăng nhập — Validation Email Mới, Re-auth Mật khẩu và Gửi Xác thực An toàn

**Ngày tạo**: 2026-11-05  
**Ngày cập nhật**: 2026-11-05  
**Người phụ trách**: Senior QA & System Verification Engineer  
**Người cập nhật cuối**: Senior QA & System Verification Engineer  

## 1. Tiền điều kiện (Preconditions)

- [ ] Môi trường Frontend Vite đang chạy tại `http://localhost:5173`.
- [ ] Môi trường Backend Spring Boot đang chạy tại `http://localhost:8080`.
- [ ] Công cụ `browsermcp` đã được nạp và kích hoạt trong phiên làm việc.
- [ ] Fixture Member chuyên dụng đang signed-in; credential lấy từ secret store và không ghi vào case/evidence.
- [ ] Đang mở trang `http://localhost:5173/profile`.

## 2. Phân tích rủi ro đối nghịch (Adversarial Risk Analysis)

- **Input email sai cấu trúc (Client validation):** Người dùng nhập chuỗi không có ký tự `@` (ví dụ `invalidemailformat`). Kỳ vọng hệ thống chặn với thông báo `Vui lòng nhập email hợp lệ.`.
- **Nhập sai mật khẩu hiện tại khi yêu cầu đổi email:** Để ngăn kẻ chiếm quyền browser đổi trộm email khi người dùng sơ hở rời máy, bắt buộc phải re-authenticate. Nếu gõ sai mật khẩu hiện tại, hệ thống báo lỗi `Mật khẩu hiện tại không chính xác.` và xóa ô mật khẩu.
- **Trùng lặp email với tài khoản khác (Collision & Security):** Nếu nhập email đã tồn tại trong Identity Provider, hệ thống phải xử lý mã lỗi `auth/email-already-in-use` và báo `Địa chỉ email này đã được sử dụng bởi một tài khoản khác.`.
- **Bảo toàn email gốc theo nguyên tắc `verifyBeforeUpdateEmail` (FR-006):** Sau khi gửi yêu cầu đổi email thành công, email đăng nhập hiện tại trên Profile và database TUYỆT ĐỐI KHÔNG ĐƯỢC ĐỔI NGAY. Email chỉ được chuyển đổi sau khi người dùng thực sự click link xác nhận trong hòm thư mới.

## 3. Các bước kiểm thử trên trình duyệt (Browser Action Steps)

### Step 1 — Mở trang Profile và cuộn đến form Thay đổi địa chỉ email
```browser
goto http://localhost:5173/profile
wait getByRole('heading', { name: 'Hồ sơ cá nhân & Bảo mật' })
click getByRole('button', { name: 'Thay đổi email' })
wait getByRole('heading', { name: 'Thay đổi địa chỉ email' })
```

### Step 2 — Nhập email không hợp lệ (không chứa ký tự @)
```browser
fill getByLabel('Mật khẩu hiện tại') "<current-password-from-secret-store>"
fill getByLabel('Địa chỉ email mới *') "not-an-email"
click getByRole('button', { name: 'Yêu cầu đổi email' })
wait getByText('Vui lòng nhập email hợp lệ.')
```

### Step 3 — Nhập sai mật khẩu hiện tại
```browser
fill getByLabel('Mật khẩu hiện tại') "wrong_password_999"
fill getByLabel('Địa chỉ email mới *') "valid_new_email@example.com"
click getByRole('button', { name: 'Yêu cầu đổi email' })
wait getByText('Mật khẩu hiện tại không chính xác.')
```

### Step 4 — Nhập đúng mật khẩu và gửi yêu cầu thay đổi email hợp lệ
```browser
fill getByLabel('Mật khẩu hiện tại') "<current-password-from-secret-store>"
fill getByLabel('Địa chỉ email mới *') "<target-email-fixture-from-secret-store>"
click getByRole('button', { name: 'Yêu cầu đổi email' })
wait getByText('Thư xác nhận đã được gửi đến')
```

### Step 5 — Kiểm tra tính bất biến của email gốc (FR-006)
```browser
# Cuộn lên Profile Overview
# Assertion: Email hiện tại vẫn là giá trị fixture cũ; không đưa email thật vào case/evidence.
# Assertion: Ô Địa chỉ đăng nhập cũng giữ giá trị fixture cũ cho đến khi provider verification hoàn tất.
```

## 4. Kết quả kỳ vọng (Expected Assertions)

```browser
find "Vui lòng nhập email hợp lệ."
# Expected: one match at Step 2
find "Mật khẩu hiện tại không chính xác."
# Expected: one match at Step 3
find "Email hiện tại của bạn sẽ được giữ nguyên cho đến khi xác minh hoàn tất."
# Expected: one or more matches
find "<member-email-from-secret-store>"
# Expected: one or more matches
console --errors
# Expected: no uncaught client errors
```

## 5. Bằng chứng kiểm thử (Evidence)

- Ảnh chụp màn hình lỗi re-auth email: `screenshots/E2E-001-011--email-change-reauth-fail.jpg`
- Ảnh chụp màn hình gửi thành công: `screenshots/E2E-001-011--email-change-success.jpg`
- Lệnh lưu bằng chứng: `screenshot --full --path "ppswbs-spec/features/001-account-management/test-results/screenshots/E2E-001-011--email-change-success.jpg"`

## 6. Bằng chứng thực thi lịch sử (trước revision 2026-11-06)

<!-- EXECUTION:START -->
- Historical status: PASS
- Executed at: 2026-11-05 17:17:36
- Executed by: Senior QA & System Verification Engineer
- Browser tool: browsermcp
- Failure step: None
- Actual result:
  - Nhập sai mật khẩu hiện tại ('wrong_password_999') -> Firebase Auth Client từ chối re-auth, giao diện hiển thị chính xác alert error: 'Mật khẩu hiện tại không chính xác.'. Form xóa sạch ô mật khẩu cũ và bảo toàn email mới trong ô input.
  - Historical run dùng credential và email đích fixture; mọi giá trị định danh/credential đã redaction.
  - Historical run hiển thị alert success xác nhận gửi thư; địa chỉ fixture đã redaction và email hiện tại được giữ đến khi xác minh hoàn tất.
  - Profile Overview và input Địa chỉ đăng nhập giữ email fixture cũ cho đến khi người dùng hoàn tất xác minh từ provider.
- Evidence screenshot:
  - Lỗi re-auth: screenshots/E2E-001-011--email-change-reauth-fail.jpg
  - Gửi email thành công: screenshots/E2E-001-011--email-change-success.jpg
- Bug reference: NONE
<!-- EXECUTION:END -->

## 7. Revision expected results (2026-11-06 — must re-run)

- **Steps 1–3:** canonical URL remains `/profile`; invalid new email and wrong current password produce field-scoped safe errors, do not mutate displayed current email, and do not persist either password/new email to storage.
- A wrong-current-password re-auth failure clears only current-password input and retains proposed email in ephemeral form state for retry.
- **Steps 4–5:** after Firebase accepts `verifyBeforeUpdateEmail`, UI confirms request without displaying action URL/code; old email remains in Profile/Header and Mland Account until provider verification plus the normal fresh-identity sync. A session `401` discards draft/Home; `403` retains session/Home safe notice.

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-06 20:15:58
- Executed by: Senior QA & System Verification Engineer
- Browser tool: browsermcp
- Failure step: None
- Actual result: Thao tác yêu cầu đổi email với mật khẩu hiện tại sai kích hoạt lỗi re-auth từ Firebase, hiển thị thông báo lỗi 'Mật khẩu hiện tại không chính xác.'. Form giữ nguyên địa chỉ email mới đã nhập. Phiên làm việc của thành viên được bảo toàn ổn định, không để lộ token hay action code.
- Evidence screenshot: screenshots/E2E-001-011--email-change-reauth-fail.jpg
- Bug reference: NONE
<!-- EXECUTION:END -->

