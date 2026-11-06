---
id: E2E-001-009
feature: 001-account-management
test_level: e2e
test_case_type: B
source_ref: spec.md#FR-005
target_url: http://localhost:5173/profile
actor_role: Customer
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
  - http://localhost:8080
test_data:
  fixture: member-profile-update
  account_ref: E2E_MEMBER_009
  isolation_key: E2E-001-009-20261105
cleanup:
  required: true
  method: revert-profile-data-via-ui
---

# E2E-001-009: Chỉnh sửa Hồ sơ Thành viên — Ràng buộc Họ tên, Số điện thoại, Khôi phục Form và Lưu Thay đổi Thành công

**Ngày tạo**: 2026-11-05  
**Ngày cập nhật**: 2026-11-05  
**Người phụ trách**: Senior QA & System Verification Engineer  
**Người cập nhật cuối**: Senior QA & System Verification Engineer  

## 1. Tiền điều kiện (Preconditions)

- [ ] Môi trường Frontend Vite đang chạy tại `http://localhost:5173`.
- [ ] Môi trường Backend Spring Boot đang chạy tại `http://localhost:8080`.
- [ ] Công cụ `browsermcp` đã được nạp và kích hoạt trong phiên làm việc.
- [ ] Phiên đăng nhập hợp lệ của fixture Member chuyên dụng đang hoạt động tại `http://localhost:5173/profile`.
- [ ] Giá trị name/email baseline được lấy từ fixture an toàn, không ghi PII thật trong case/evidence.

## 2. Phân tích rủi ro đối nghịch (Adversarial Risk Analysis)

- **Xóa trắng Họ và tên (Required validation):** Người dùng xóa rỗng hoặc chỉ gõ khoảng trắng vào ô Họ và tên rồi submit. Kỳ vọng hệ thống chặn ngay tại client, hiển thị lỗi `Họ và tên là bắt buộc`, không gửi request `PATCH /api/v1/accounts/me` vô ích.
- **Ràng buộc độ dài & ký tự đặc biệt:** Họ và tên không được vượt quá 100 ký tự và không chứa ký tự điều khiển ISO. Input SĐT phải từ 7 đến 30 ký tự nếu có nhập. Nhập `12345` phải bị chặn với thông báo `Số điện thoại phải từ 7 đến 30 ký tự`.
- **Bảo toàn dữ liệu với tính năng Khôi phục (Reset Form):** Sau khi người dùng gõ thay đổi dở dang, bấm nút "Khôi phục" phải hoàn nguyên toàn bộ giá trị về snapshot gốc nạp từ server và xóa sạch mọi thông báo lỗi inline.
- **Tính toàn vẹn sau khi Lưu thành công:** Sau khi submit thành công, hệ thống phải cập nhật ngay lập tức phần hiển thị Profile Overview trên đầu trang mà không cần F5 reload lại toàn bộ trang web.
- **Bảo vệ email đăng nhập:** Ô nhập Địa chỉ đăng nhập bị `disabled` (read-only) với con trỏ `cursor: not-allowed`, không cho phép can thiệp bằng cách sửa DOM.

## 3. Các bước kiểm thử trên trình duyệt (Browser Action Steps)

### Step 1 — Mở trang Profile và cuộn tới section Chỉnh sửa thông tin hồ sơ
```browser
goto http://localhost:5173/profile
wait getByRole('heading', { name: 'Hồ sơ cá nhân & Bảo mật' })
click getByRole('button', { name: 'Chỉnh sửa hồ sơ' })
wait getByRole('heading', { name: 'Chỉnh sửa thông tin hồ sơ' })
```

### Step 2 — Kiểm tra ô Email bị vô hiệu hóa
```browser
# Assertion: input #profile-email bị disabled
```

### Step 3 — Xóa trắng Họ và tên và submit để kiểm tra Required validation
```browser
fill getByLabel('Họ và tên *') ""
click getByRole('button', { name: 'Lưu thay đổi' })
wait getByText('Họ và tên là bắt buộc')
```

### Step 4 — Nhập Số điện thoại sai độ dài biên (< 7 ký tự)
```browser
fill getByLabel('Họ và tên *') "Phan Sơn QA"
fill getByLabel('Số điện thoại') "12345"
click getByRole('button', { name: 'Lưu thay đổi' })
wait getByText('Số điện thoại phải từ 7 đến 30 ký tự')
```

### Step 5 — Thao tác nút Khôi phục để hoàn nguyên dữ liệu
```browser
click getByRole('button', { name: 'Khôi phục' })
# Giá trị Họ và tên trở về dữ liệu gốc, thông báo lỗi biến mất
```

### Step 6 — Nhập dữ liệu mới hợp lệ và Lưu thay đổi
```browser
fill getByLabel('Họ và tên *') "Phan Nguyễn Trường Sơn Verified"
fill getByLabel('Số điện thoại') "0987654321"
click getByRole('button', { name: 'Lưu thay đổi' })
wait getByText('Thông tin hồ sơ đã được cập nhật thành công')
```

### Step 7 — Xác minh thông tin được đồng bộ lên Header Profile Overview
```browser
# Assertion: Heading Overview hiển thị tên mới "Phan Nguyễn Trường Sơn Verified"
# Assertion: Chi tiết Số điện thoại hiển thị "0987654321"
```

## 4. Kết quả kỳ vọng (Expected Assertions)

```browser
find "Thông tin hồ sơ đã được cập nhật thành công"
# Expected: one or more matches
find "Phan Nguyễn Trường Sơn Verified"
# Expected: one or more matches
requests --filter "/api/v1/accounts/me"
# Expected: PATCH /api/v1/accounts/me status 200
console --errors
# Expected: no uncaught errors
```

## 5. Bằng chứng kiểm thử (Evidence)

- Ảnh chụp màn hình cập nhật thành công: `screenshots/E2E-001-009--profile-update-success.jpg`
- Lệnh lưu bằng chứng: `screenshot --full --path "ppswbs-spec/features/001-account-management/test-results/screenshots/E2E-001-009--profile-update-success.jpg"`

## 6. Bằng chứng thực thi lịch sử (trước revision 2026-11-06)

<!-- EXECUTION:START -->
- Historical status: PASS
- Executed at: 2026-11-05 17:20:36
- Executed by: Senior QA & System Verification Engineer
- Browser tool: browsermcp
- Failure step: None
- Actual result:
  - Form validation hoạt động hoàn hảo: Xóa rỗng Họ và tên chặn ngay tại client với lỗi 'Họ và tên là bắt buộc'; Nhập SĐT < 7 ký tự ('12345') chặn với lỗi 'Số điện thoại phải từ 7 đến 30 ký tự'.
  - Nút 'Khôi phục' (Reset form) hoạt động chính xác: Hoàn nguyên Họ tên & SĐT về đúng snapshot ban đầu và làm sạch toàn bộ lỗi inline.
  - Lưu cập nhật hồ sơ thành công: Nhập Họ tên và SĐT mới, bấm 'Lưu thay đổi' gọi API PATCH /api/v1/accounts/me thành công, xuất hiện thông báo 'Thông tin hồ sơ đã được cập nhật thành công'. Thẻ Profile Overview được đồng bộ dữ liệu mới tức thì.
  - Đã thực hiện cleanup hoàn nguyên dữ liệu họ tên và số điện thoại về ban đầu an toàn.
- Evidence screenshot:
  - Cập nhật thành công: screenshots/E2E-001-009--profile-update-success.jpg
- Bug reference: NONE
<!-- EXECUTION:END -->

## 7. Revision expected results (2026-11-06 — must re-run)

- **Steps 1–2:** page is canonical `/profile`; Email is display-only/disabled and no current identity email may be changed through profile save.
- **Steps 3–4:** validation keeps URL `/profile`, leaves non-secret safe fields for correction, sends no save request, and does not silently trim/control-character-correct invalid name without a visible result.
- **Step 5:** Restore returns fields to last server-confirmed values, not a prior unsaved state from another session; it cannot restore a draft after logout/session loss.
- **Steps 6–7:** success UI appears only after server response; overview and edit form show the returned saved name/phone. A `401` instead discards draft and follows E2E-001-015; a `403` retains session/safe Home notice.

<!-- EXECUTION:START -->
- Latest status: NOT_RUN
- Previous status: PASS on 2026-11-04; route/error assertions added.
<!-- EXECUTION:END -->

