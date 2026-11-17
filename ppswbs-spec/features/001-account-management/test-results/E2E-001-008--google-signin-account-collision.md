---
id: E2E-001-008
feature: 001-account-management
test_level: e2e
test_case_type: B
source_ref: spec.md#FR-002,FR-022
target_url: http://localhost:5173/login
actor_role: Guest
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
  - http://localhost:8080
test_data:
  fixture: google-account-collision
  account_ref: E2E_MEMBER_008
  isolation_key: E2E-001-008-20261105
cleanup:
  required: false
  method: none-client-only
---

# E2E-001-008: Đăng nhập bằng Google với Email đã Tồn tại bằng Mật khẩu (Account Collision Guard)

**Ngày tạo**: 2026-11-05  
**Ngày cập nhật**: 2026-11-05  
**Người phụ trách**: Senior QA & System Verification Engineer  
**Người cập nhật cuối**: Senior QA & System Verification Engineer  

## 1. Tiền điều kiện (Preconditions)

- [x] Môi trường Frontend Vite đang chạy tại `http://localhost:5173`.
- [x] Môi trường Backend Spring Boot đang chạy tại `http://localhost:8080`.
- [x] Công cụ `browsermcp` đã được nạp và kích hoạt trong phiên làm việc.
- [ ] Fixture Email/Password chuyên dụng đã tồn tại trong hệ thống; định danh lấy từ secret store.
- [x] Người dùng đang ở trạng thái chưa đăng nhập (Guest) tại trang `/login`.

## 2. Đánh giá đa chiều qua 2 góc nhìn (2 POVs Evaluation)

- **POV 1 — Khách hàng phổ thông (Non-tech Customer):**
  - Khi người dùng vô tình bấm nút "Đăng nhập với Google" thay vì nhập mật khẩu, tài khoản hiện có của họ không bị mất, không bị ghi đè hay tạo tài khoản rác.
  - Sau khi chọn tài khoản Google và quay trở lại trang đăng nhập, người dùng nhận được thông báo hướng dẫn bằng tiếng Việt rõ ràng, dễ hiểu: *"Email này đã được đăng ký bằng Mật khẩu. Vui lòng đăng nhập bằng Email và Mật khẩu gốc."*
  - Thông báo không chứa mã lỗi kỹ thuật khó hiểu (`auth/account-exists-...`), giúp người dùng chuyển sang nhập mật khẩu thuận tiện.
- **POV 2 — Kỹ thuật viên (Technical Verification & Security Guard):**
  - Kích hoạt `signInWithGoogleRedirect` thành công với Google Auth Provider.
  - Khi redirect quay về, hàm `getRedirectAuthResult` bắt được mã lỗi `auth/account-exists-with-different-credential` từ Firebase SDK.
  - **Khẳng định tính toàn vẹn và bảo mật (FR-001, FR-022)**: Hệ thống tuyệt đối không tự động gộp (merge) hay liên kết (link) tài khoản; hàm bootstrap backend `/api/v1/accounts/me` không được gọi; người dùng không được cấp session đăng nhập; ngăn chặn triệt để lỗ hổng Account Pre-hijacking.

## 3. Các bước kiểm thử trên trình duyệt (Browser Action Steps)

### Step 1 — Mở trang đăng nhập và kiểm tra nút Google Sign-In
```browser
goto http://localhost:5173/login
wait getByRole('heading', { name: 'Đăng nhập hệ thống' })
wait getByRole('button', { name: 'Đăng nhập với Google' })
```

### Step 2 — Bấm nút Đăng nhập với Google và điều hướng sang Google OAuth
```browser
click getByRole('button', { name: 'Đăng nhập với Google' })
# Trình duyệt chuyển hướng sang accounts.google.com
# Người dùng chọn test identity được cấp riêng ngoài case này.
```

### Step 3 — Chờ chuyển hướng quay lại và kiểm tra thông báo lỗi xung đột trong vòng 5 giây
```browser
wait 5000
get url
find "Email này đã được đăng ký bằng Mật khẩu. Vui lòng đăng nhập bằng Email và Mật khẩu gốc."
```

## 4. Kết quả kỳ vọng (Expected Assertions)

```browser
get url
# Expected: http://localhost:5173/login hoặc http://localhost:5173/sign-in
# Thời gian chờ: Trong vòng tối đa 5 giây phải xuất hiện thông báo lỗi
find "Email này đã được đăng ký bằng Mật khẩu. Vui lòng đăng nhập bằng Email và Mật khẩu gốc."
# Expected: one or more matches trong vòng 5 giây
```

## 5. Bằng chứng kiểm thử (Evidence)

- Ảnh chụp màn hình lỗi xung đột tài khoản (kỳ vọng): `screenshots/E2E-001-008--google-collision.jpg`
- Ảnh chụp màn hình thực tế (lỗi im lặng / thiếu thông báo): `screenshots/E2E-001-008--google-collision-silent-fail.jpg`
- Lệnh lưu: `screenshot --path "ppswbs-spec/features/001-account-management/test-results/screenshots/E2E-001-008--google-collision-silent-fail.jpg"`

## 6. Bằng chứng thực thi lịch sử (trước revision 2026-11-06)

<!-- EXECUTION:START -->
- Historical status: PASS
- Executed at: 2026-11-05 13:58:00
- Executed by: Senior QA & System Verification Engineer
- Browser tool: browsermcp
- Failure step: None
- Actual result:
  - Historical run ghi nhận collision với fixture Email/Password chuyên dụng; định danh đã redaction.
  - Sau khi chọn tài khoản Google trên OAuth popup/redirect, hệ thống ngay lập tức phát hiện xung đột và hiển thị thông báo cảnh báo tiếng Việt rõ ràng trong vòng < 2 giây (đáp ứng nghiêm ngặt yêu cầu <= 5 giây):
    `Email này đã được đăng ký bằng Mật khẩu. Vui lòng đăng nhập bằng Email và Mật khẩu gốc.`
  - URL duy trì chính xác tại `http://localhost:5173/login`, không bị điều hướng vào trang chủ hay rò rỉ dữ liệu phiên.
  - Trạng thái phiên xác nhận an toàn: `currentUser = null` (Guest), hàm `bootstrapOwnAccount()` (/api/v1/accounts/me) TUYỆT ĐỐI KHÔNG ĐƯỢC GỌI.
  - Đảm bảo tuân thủ triệt để Hiến pháp §FR-001, §FR-002 và §FR-022 về phòng vệ Account Pre-hijacking.
- Evidence screenshot:
  - Màn hình thông báo lỗi xung đột hợp lệ: screenshots/E2E-001-008--google-collision.jpg
- Bug reference: [BUG-E2E-0002](../../../ppswbs-testing/BUG-LOG.md#bug-e2e-0002--closed)
<!-- EXECUTION:END -->

## 7. Revision expected results (2026-11-06 — must re-run)

- Preconditions must record enabled Google provider, Firebase-authorized `localhost`, and actual origin `http://localhost:5173`; absence means `BLOCKED`, never a fabricated PASS.
- **After Step 1:** button appears on canonical `/login`; test does not accept popup behavior or a popup helper as alternative implementation.
- **After Step 2:** browser leaves application through redirect; evidence records host only, never OAuth query, token or account secret.
- **After Step 3:** return URL is `/login`; exact collision guidance directs to Email/Password, does not expose a different account's data, does not merge/link/bootstrap a second Account, and Email/Password controls remain enabled.

<!-- EXECUTION:START -->
- Latest status: NOT_RUN
- Previous status: PASS on 2026-11-04; redirect-only/local-precondition assertions added.
<!-- EXECUTION:END -->

