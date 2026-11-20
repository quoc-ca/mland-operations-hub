---
id: E2E-001-025
feature: 001-account-management
test_level: e2e
test_case_type: B
source_ref: spec.md#FR-011, spec.md#FR-012, tasks.md#T086
target_url: http://localhost:5173/admin/accounts/new
actor_role: Admin
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
  - http://localhost:8080/v3/api-docs
test_data:
  fixture: admin-carrier-provisioning
  account_ref: ADMIN_001
  isolation_key: E2E-001-025-20261120
cleanup:
  required: false
  method: none-ephemeral-test-account
---

# E2E-001-025: Quản trị cấp tài khoản CARRIER — Form guard, mật khẩu chỉ-ghi, loại trừ cơ sở và kích hoạt trực tiếp ACTIVE

**Ngày tạo**: 2026-11-20  
**Ngày cập nhật**: 2026-11-20  
**Người phụ trách**: Phan Sơn (Senior QA Engineer)  
**Người cập nhật cuối**: Phan Sơn  

## 1. Tiền điều kiện (Preconditions)

- [x] Môi trường Frontend Vite đang hoạt động tại `http://localhost:5173`.
- [x] Môi trường Backend Spring Boot đang hoạt động tại `http://localhost:8080`.
- [x] Công cụ `browsermcp` đã được nạp và kích hoạt trong phiên làm việc.
- [x] Người dùng đã đăng nhập với vai trò `ADMIN` và có phiên truy cập hợp lệ vào module Quản trị tài khoản.
- [x] Email mồi để thử nghiệm trùng lặp: `admin@mland.vn` (đã tồn tại trong hệ thống).
- [x] Dữ liệu tạo mới chưa từng tồn tại: `carrier.e2e.test@ghtk.vn`.

## 2. Phân tích rủi ro đối nghịch (Adversarial Risk Analysis)

- **Rò rỉ phân công cơ sở (Location Scope Leakage / FR-012):** Đối tác vận chuyển (GHTK) hoạt động trên phạm vi toàn quốc, không gắn với bất kỳ Workshop Location nào. Khi chọn role `CARRIER`, giao diện phải ẩn hoàn toàn vùng chọn cơ sở (Radio/Checkbox) và payload gửi lên server tuyệt đối không chứa mảng `locationIds`.
- **Rò rỉ mật khẩu khởi tạo (Write-only Password Exposure / FR-011):** Mật khẩu khởi tạo tài khoản CARRIER mang tính chất chỉ-ghi (write-only). Form phải xóa sạch input mật khẩu khỏi React state memory ngay khi gửi submit; giao diện thành công không được lưu cache, không render lại mật khẩu dưới bất kỳ hình thức nào, và không lộ trong DOM hay URL.
- **Loại trừ luồng thư mời thiết lập (Invitation Exclusion / FR-011):** Khác với Staff/Manager ở trạng thái `PENDING_SETUP` cần gửi email mời thiết lập mật khẩu, CARRIER được kích hoạt ngay ở trạng thái `ACTIVE`. Checkbox "Gửi thông báo hướng dẫn thiết lập tài khoản" phải ẩn đi khi chọn CARRIER.
- **Phòng thủ validation phía client & server (Form Boundary Validation):**
  - Mật khẩu < 6 ký tự: Bị chặn ngay tại client với thông báo "Mật khẩu phải chứa ít nhất 6 ký tự."
  - Email sai định dạng: Bị chặn tại client với thông báo "Địa chỉ email không đúng định dạng."
  - Số điện thoại thiếu hoặc ngoài dải 7-30 ký tự: Bị chặn với lỗi trường tương ứng.
  - Email đã thuộc tài khoản Mland/Firebase khác: Server trả về 409 Conflict (`INVALID_STATE`), frontend hiển thị alert lỗi an toàn, không làm crash form và không làm mất dữ liệu đã nhập (họ tên, SĐT).

## 3. Các bước kiểm thử trên trình duyệt (Browser Action Steps)

### Step 1 — Truy cập trang tạo tài khoản nội bộ với vai trò Admin
```browser
goto http://localhost:5173/admin/accounts/new
wait getByRole('heading', { name: 'Cấp tài khoản nhân sự & đối tác' })
```

### Step 2 — Chuyển vai trò sang "Đối tác vận chuyển (GHTK)" và xác minh UI phản ứng
```browser
select getByLabel('Vai trò nội bộ') "Đối tác vận chuyển (GHTK)"
# Chờ giao diện cập nhật trạng thái động cho CARRIER
wait getByText('Email đăng nhập (bất biến)')
wait getByText('Mật khẩu khởi tạo')
```

### Step 3 — Kiểm tra cô lập trường: Ẩn phân công cơ sở và ẩn checkbox thư mời
```browser
# Khẳng định: Vùng chọn cơ sở làm việc và checkbox thư mời không còn trong DOM
snapshot -i
```

### Step 4 — Kiểm tra validation form biên (Negative / Boundary Validation)
```browser
# 4.1 Bấm Submit khi form hoàn toàn rỗng
click getByRole('button', { name: 'Khởi tạo tài khoản' })
wait getByText('Họ và tên là bắt buộc.')
wait getByText('Email đăng nhập là bắt buộc đối với Đối tác vận chuyển.')
wait getByText('Mật khẩu khởi tạo là bắt buộc đối với Đối tác vận chuyển.')
wait getByText('Số điện thoại là bắt buộc đối với Đối tác vận chuyển.')

# 4.2 Nhập mật khẩu yếu (< 6 ký tự) và email sai cú pháp
fill getByLabel(/email đăng nhập/i) "email-khong-hop-le"
fill getByLabel(/mật khẩu khởi tạo/i) "12345"
fill getByLabel(/tên hiển thị đối tác/i) "GHTK Hà Nội Test"
fill getByLabel(/số điện thoại liên hệ/i) "0912345678"
click getByRole('button', { name: 'Khởi tạo tài khoản' })
wait getByText('Địa chỉ email không đúng định dạng.')
wait getByText('Mật khẩu phải chứa ít nhất 6 ký tự.')
```

### Step 5 — Kiểm tra xung đột email trùng lặp (Duplicate Email Conflict)
```browser
fill getByLabel(/email đăng nhập/i) "admin@mland.vn"
fill getByLabel(/mật khẩu khởi tạo/i) "ValidPass123@"
click getByRole('button', { name: 'Khởi tạo tài khoản' })
wait getByRole('alert')
# Hệ thống hiển thị alert báo lỗi từ chối tài khoản trùng lặp
```

### Step 6 — Khởi tạo thành công tài khoản CARRIER hợp lệ (Happy Path)
```browser
fill getByLabel(/email đăng nhập/i) "carrier.e2e.test@ghtk.vn"
fill getByLabel(/mật khẩu khởi tạo/i) "GhtkPass@2026"
fill getByLabel(/tên hiển thị đối tác/i) "Công ty CP Giao Hàng Tiết Kiệm"
fill getByLabel(/số điện thoại liên hệ/i) "0909999888"
click getByRole('button', { name: 'Khởi tạo tài khoản' })
wait getByRole('heading', { name: 'Khởi tạo thành công!' })
```

### Step 7 — Xác minh trạng thái ACTIVE, ghi chú write-only và nút điều hướng
```browser
wait getByText('ACTIVE (Đang hoạt động)')
wait getByText('carrier.e2e.test@ghtk.vn')
wait getByText(/Mật khẩu khởi tạo mang tính chất chỉ-ghi \(write-only\)/i)
wait getByText(/bàn giao thông tin đăng nhập trực tiếp/i)
```

## 4. Kết quả kỳ vọng (Expected Assertions)

```browser
find "Khởi tạo thành công!"
# Expected: 1 match
find "ACTIVE"
# Expected: 1 or more matches
find "carrier.e2e.test@ghtk.vn"
# Expected: 1 match
find "Cơ sở làm việc"
# Expected: 0 matches (không được hiển thị trên card kết quả hoặc form CARRIER)
console --errors
# Expected: no errors
requests --filter "/api/v1/internal-accounts"
# Expected: POST 200 OK, response JSON chứa status: "ACTIVE", role: "CARRIER", không chứa field password
```

## 5. Bằng chứng kiểm thử (Evidence)

- File ảnh chụp màn hình sau khi tạo thành công: `ppswbs-spec/features/001-account-management/test-results/screenshots/E2E-001-025--carrier-provisioning-success.jpg`
- Lệnh chụp bằng chứng: `screenshot --full --path "ppswbs-spec/features/001-account-management/test-results/screenshots/E2E-001-025--carrier-provisioning-success.jpg"`

## 6. Lịch sử thực thi (Execution History)

<!-- EXECUTION:START -->
- Latest status: NOT_RUN
- Executed at: NOT_RUN
- Executed by: NOT_RUN
- Browser tool: browsermcp
- Failure step: NOT_RUN
- Actual result: NOT_RUN
- Evidence screenshot: NOT_RUN
- Bug reference: NONE
<!-- EXECUTION:END -->
