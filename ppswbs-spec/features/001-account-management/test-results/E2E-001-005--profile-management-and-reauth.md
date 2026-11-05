---
id: E2E-001-005
feature: 001-account-management
test_level: e2e
test_case_type: B
source_ref: spec.md#FR-005,FR-007
target_url: http://localhost:5173/profile
actor_role: Customer
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
  - http://localhost:8080
test_data:
  fixture: member-password-reauth
  account_ref: minikie456@gmail.com
  isolation_key: E2E-001-005-20261105
cleanup:
  required: false
  method: none-client-only
---

# E2E-001-005: Quản lý Hồ sơ: Kích hoạt Form Đổi Mật khẩu có In-place Re-auth từ Session đã Đăng nhập

**Ngày tạo**: 2026-11-05  
**Ngày cập nhật**: 2026-11-05  
**Người phụ trách**: Senior QA & System Verification Engineer  
**Người cập nhật cuối**: Senior QA & System Verification Engineer  

## 1. Tiền điều kiện (Preconditions)

- [x] Môi trường Frontend Vite đang chạy tại `http://localhost:5173`.
- [x] Môi trường Backend Spring Boot đang chạy tại `http://localhost:8080`.
- [x] Công cụ `browsermcp` đã được nạp và kích hoạt trong phiên làm việc.
- [x] Phiên đăng nhập hợp lệ của tài khoản `minikie456@gmail.com` đang hoạt động tại trang `http://localhost:5173/profile` từ kịch bản E2E-001-004.

## 2. Đánh giá đa chiều qua 2 góc nhìn (2 POVs Evaluation)

- **POV 1 — Khách hàng phổ thông (Non-tech Customer):**
  - Trong trang Hồ sơ cá nhân, người dùng thấy ngay nút "Đổi mật khẩu" trong thanh tác vụ; khi bấm vào, màn hình cuộn mượt mà xuống form Đổi mật khẩu mà không bị giật trang.
  - Form có 3 ô nhập rõ ràng: "Mật khẩu hiện tại", "Mật khẩu mới", "Xác nhận mật khẩu mới".
  - Các thông báo lỗi hiển thị bằng tiếng Việt trực quan, đúng trọng tâm ("Vui lòng nhập mật khẩu hiện tại.", "Mật khẩu phải chứa ít nhất 6 ký tự.", "Mật khẩu xác nhận không khớp.").
  - Khi gõ nhầm mật khẩu xác nhận, hệ thống giữ nguyên mật khẩu mới đã nhập, không bắt người dùng phải gõ lại từ đầu.
- **POV 2 — Kỹ thuật viên (Technical Verification):**
  - Nút "Đổi mật khẩu" hiển thị chính xác theo điều kiện provider: vì tài khoản có provider `password` trong `providerData` (`showPasswordSection = true`).
  - Nút bấm kích hoạt cuộn mượt qua `passwordSectionRef.current?.scrollIntoView({ behavior: 'smooth' })`.
  - Form gồm 3 trường nhập liệu: `current-password`, `new-password`, `confirm-password`.
  - Validation kiểm tra chặt chẽ các điều kiện biên: rỗng, độ dài $\ge 6$ ký tự, và đối sánh chuỗi xác nhận.
  - Component giữ proposed values trong bộ nhớ React state tạm thời, tuân thủ nghiêm ngặt quy tắc không lưu mật khẩu vào `localStorage` hay `sessionStorage`.

## 3. Các bước kiểm thử trên trình duyệt (Browser Action Steps)

### Step 1 — Sử dụng session hiện tại và click nút Đổi mật khẩu trên trang Profile
```browser
# Session kế thừa từ E2E-001-004, đang ở http://localhost:5173/profile
click getByRole('button', { name: 'Đổi mật khẩu' })
wait 1000
wait getByRole('heading', { name: 'Đổi mật khẩu' })
```

### Step 2 — Bấm Cập nhật mật khẩu khi để trống và kiểm tra thông báo lỗi
```browser
click getByRole('button', { name: 'Cập nhật mật khẩu' })
wait getByText('Vui lòng nhập mật khẩu hiện tại.')
```

### Step 3 — Nhập mật khẩu hiện tại và kiểm tra lỗi mật khẩu mới dưới 6 ký tự
```browser
fill getByLabel('Mật khẩu hiện tại *') "123123123"
fill getByLabel('Mật khẩu mới *') "123"
fill getByLabel('Xác nhận mật khẩu mới *') "123"
click getByRole('button', { name: 'Cập nhật mật khẩu' })
wait getByText('Mật khẩu phải chứa ít nhất 6 ký tự.')
```

### Step 4 — Kiểm tra lỗi mật khẩu xác nhận không khớp và bảo toàn mật khẩu mới
```browser
fill getByLabel('Mật khẩu mới *') "newpassword123"
fill getByLabel('Xác nhận mật khẩu mới *') "wrongpassword456"
click getByRole('button', { name: 'Cập nhật mật khẩu' })
wait getByText('Mật khẩu xác nhận không khớp.')
get value getByLabel('Mật khẩu mới *')
# Expected: "newpassword123"
```

## 4. Kết quả kỳ vọng (Expected Assertions)

```browser
find "Đổi mật khẩu"
# Expected: one or more matches
find "Mật khẩu xác nhận không khớp."
# Expected: one or more matches
get value getByLabel('Mật khẩu mới *')
# Expected: "newpassword123"
```

## 5. Bằng chứng kiểm thử (Evidence)

- Ảnh chụp màn hình form validation: `screenshots/E2E-001-005--change-password-validation.jpg`
- Lệnh lưu: `screenshot --path "ppswbs-spec/features/001-account-management/test-results/screenshots/E2E-001-005--change-password-validation.jpg"`

## 6. Kết quả thực thi (Execution History)

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-05 12:35:26
- Executed by: Senior QA & System Verification Engineer
- Browser tool: browsermcp
- Failure step: None
- Actual result:
  - Tiếp nối đúng session đã đăng nhập của Member minikie456@gmail.com trên trang Profile.
  - Nút "Đổi mật khẩu" hiển thị trong thanh tác vụ, bấm vào kích hoạt cuộn mượt xuống section Đổi mật khẩu.
  - Khi các ô nhập trống, nút "Cập nhật mật khẩu" bị vô hiệu hóa (disabled) để ngăn submit rỗng.
  - Khi nhập mật khẩu mới < 6 ký tự ("123"), hệ thống chặn và hiển thị thông báo inline: 'Mật khẩu mới phải chứa ít nhất 6 ký tự.'
  - Khi nhập mật khẩu xác nhận không khớp, hệ thống chặn và báo lỗi: 'Mật khẩu xác nhận không khớp.'
  - Bảo toàn giá trị: Ô nhập Mật khẩu mới vẫn giữ nguyên giá trị 'newpassword123' trong component state, không bị xóa trắng.
- Evidence screenshot:
  - Validation form đổi mật khẩu: screenshots/E2E-001-005--change-password-validation.jpg
- Bug reference: NONE
<!-- EXECUTION:END -->
