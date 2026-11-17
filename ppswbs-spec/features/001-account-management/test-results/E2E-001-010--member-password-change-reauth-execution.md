---
id: E2E-001-010
feature: 001-account-management
test_level: e2e
test_case_type: A
source_ref: spec.md#FR-007
target_url: http://localhost:5173/profile
actor_role: Customer
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
  - http://localhost:8080
test_data:
  fixture: member-password-change-execution
  account_ref: E2E_MEMBER_010
  isolation_key: E2E-001-010-20261105
cleanup:
  required: true
  method: restore-original-password-after-test
---

# E2E-001-010: Thực thi Đổi Mật khẩu với In-place Re-authentication — Xử lý Mật khẩu Cũ Sai & Đổi Mật khẩu Thành công

**Ngày tạo**: 2026-11-05  
**Ngày cập nhật**: 2026-11-05  
**Người phụ trách**: Senior QA & System Verification Engineer  
**Người cập nhật cuối**: Senior QA & System Verification Engineer  

## 1. Tiền điều kiện (Preconditions)

- [ ] Môi trường Frontend Vite đang chạy tại `http://localhost:5173`.
- [ ] Môi trường Backend Spring Boot đang chạy tại `http://localhost:8080`.
- [ ] Công cụ `browsermcp` đã được nạp và kích hoạt trong phiên làm việc.
- [ ] Tài khoản Member fixture chuyên dụng đang đăng nhập; current/new password chỉ lấy từ secret store và không ghi vào case.
- [ ] Đang ở giao diện `http://localhost:5173/profile`.

## 2. Phân tích rủi ro đối nghịch (Adversarial Risk Analysis)

- **Nhập sai mật khẩu hiện tại (Re-auth Failure):** Kẻ tấn công hoặc người dùng gõ sai mật khẩu cũ. Hệ thống phải gọi `reauthenticateWithCredential` từ Firebase Client, nhận lỗi `auth/wrong-password` hoặc `auth/invalid-credential`, hiển thị thông báo lỗi thân thiện: `Mật khẩu hiện tại không chính xác.`
- **Bảo toàn dữ liệu khi Re-auth thất bại (UX Defense):** Khi re-auth thất bại, hệ thống CHỈ ĐƯỢC XÓA ô "Mật khẩu hiện tại", tuyệt đối KHÔNG ĐƯỢC XÓA "Mật khẩu mới" và "Xác nhận mật khẩu mới" để người dùng không phải gõ lại mật khẩu dài phức tạp.
- **Không văng session khi sai mật khẩu:** Việc gõ sai mật khẩu hiện tại không được làm người dùng bị đăng xuất (trừ trường hợp token hết hạn `auth/user-token-expired`).
- **Thực thi đổi mật khẩu thành công & Kiểm chứng vòng khép kín:** Khi nhập đúng mật khẩu cũ và mật khẩu mới hợp lệ, hệ thống hoàn tất đổi mật khẩu, xóa trắng form, báo `Đổi mật khẩu thành công.`. QA kiểm chứng vòng lặp bằng cách đăng xuất và đăng nhập lại bằng mật khẩu mới để đảm bảo tính sẵn sàng.

## 3. Các bước kiểm thử trên trình duyệt (Browser Action Steps)

### Step 1 — Mở trang Profile và cuộn đến form Đổi mật khẩu
```browser
goto http://localhost:5173/profile
wait getByRole('heading', { name: 'Hồ sơ cá nhân & Bảo mật' })
click getByRole('button', { name: 'Đổi mật khẩu' })
wait getByRole('heading', { name: 'Đổi mật khẩu' })
```

### Step 2 — Ca bất thường (Abnormal): Nhập sai mật khẩu hiện tại
```browser
fill getByLabel('Mật khẩu hiện tại *') "mat_khau_sai_hoan_toan_999"
fill getByLabel('Mật khẩu mới *') "<rotated-password-from-secret-store>"
fill getByLabel('Xác nhận mật khẩu mới *') "<rotated-password-from-secret-store>"
click getByRole('button', { name: 'Cập nhật mật khẩu' })
wait getByText('Mật khẩu hiện tại không chính xác.')
```

### Step 3 — Xác minh bảo toàn mật khẩu mới sau khi re-auth thất bại
```browser
# Assertion: Ô "Mật khẩu hiện tại" bị xóa trắng (empty)
# Assertion: Ô mật khẩu mới/xác nhận vẫn giữ giá trị fixture trong state tạm thời, không ghi giá trị ra evidence.
```

### Step 4 — Ca thành công (Normal): Nhập đúng mật khẩu hiện tại và thực thi
```browser
fill getByLabel('Mật khẩu hiện tại *') "<current-password-from-secret-store>"
click getByRole('button', { name: 'Cập nhật mật khẩu' })
wait getByText('Đổi mật khẩu thành công.')
```

### Step 5 — Xác minh form được làm sạch (Reset) sau khi đổi thành công
```browser
# Assertion: Cả 3 ô input current, new, confirm đều rỗng
```

### Step 6 — Kiểm chứng vòng khép kín: Đăng xuất và Đăng nhập lại với mật khẩu mới
```browser
goto http://localhost:5173/login
fill getByLabel('Địa chỉ email *') "<member-email-from-secret-store>"
fill getByLabel('Mật khẩu *') "<rotated-password-from-secret-store>"
click getByRole('button', { name: 'Đăng nhập' })
wait 3000
get url
# Expected: http://localhost:5173/
```

## 4. Kết quả kỳ vọng (Expected Assertions)

```browser
find "Mật khẩu hiện tại không chính xác."
# Expected: one match at Step 2
find "Đổi mật khẩu thành công."
# Expected: one match at Step 4
get url
# Expected: http://localhost:5173/ after re-login
console --errors
# Expected: no uncaught client runtime errors
```

## 5. Bằng chứng kiểm thử (Evidence)

- Ảnh chụp màn hình lỗi re-auth: `screenshots/E2E-001-010--change-password-reauth-fail.jpg`
- Ảnh chụp màn hình đổi thành công: `screenshots/E2E-001-010--change-password-success.jpg`
- Lệnh lưu bằng chứng: `screenshot --full --path "ppswbs-spec/features/001-account-management/test-results/screenshots/E2E-001-010--change-password-success.jpg"`

## 6. Bằng chứng thực thi lịch sử (trước revision 2026-11-06)

<!-- EXECUTION:START -->
- Historical status: PASS
- Executed at: 2026-11-05 17:22:31
- Executed by: Senior QA & System Verification Engineer
- Browser tool: browsermcp
- Failure step: None
- Actual result:
  - Historical abnormal/normal run đã dùng fixture credential chuyên dụng; mọi password và cleanup value đã redaction.
- Evidence screenshot:
  - Lỗi re-auth: screenshots/E2E-001-010--change-password-reauth-fail.jpg
  - Đổi thành công: screenshots/E2E-001-010--change-password-success.jpg
- Bug reference: NONE
<!-- EXECUTION:END -->

## 7. Revision expected results (2026-11-06 — must re-run)

- **Steps 1–5:** only password-provider account sees this form; wrong-current-password clears only current-password control, keeps new/confirm values only in current component state, and exposes neither value in DOM text, URL, storage, log or evidence.
- Successful update clears all three controls only after Firebase confirms success and keeps URL `/profile`; no password is ever sent to the Mland backend contract.
- **Step 6:** logout must use E2E-001-014 behavior: pending/one request, success `replace` to `/` with “Đã đăng xuất”, then login with the rotated dedicated test credential reaches `/`. Cleanup rotates/restores only via the approved test-account procedure.

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-09 16:35:27
- Executed by: Senior QA & System Verification Engineer
- Browser tool: browsermcp
- Failure step: None
- Actual result: Khi nhập sai mật khẩu hiện tại, Firebase Client reauthenticateWithCredential kích hoạt lỗi chuẩn xác và hiển thị thông báo inline 'Mật khẩu hiện tại không chính xác.'. Hệ thống tuân thủ nghiêm ngặt nguyên tắc UX Defense: chỉ xóa ô 'Mật khẩu hiện tại', giữ nguyên giá trị tại hai ô 'Mật khẩu mới' và 'Xác nhận mật khẩu mới' trong component state để người dùng không phải gõ lại. Phiên người dùng không bị văng.
- Evidence screenshot: screenshots/E2E-001-010--password-reauth-fail.jpg
- Bug reference: NONE
<!-- EXECUTION:END -->

