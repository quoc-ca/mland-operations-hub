---
id: E2E-001-002
feature: 001-account-management
test_level: e2e
test_case_type: B
source_ref: spec.md#FR-036
target_url: http://localhost:5173/profile/addresses
actor_role: Customer
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
test_data:
  fixture: member-address-validation
  account_ref: MEMBER_001
  isolation_key: E2E-001-002-20261114
cleanup:
  required: false
  method: none-client-only
---

# E2E-001-002: Sổ địa chỉ nhận hàng — Modal 2 cấp hành chính (Tỉnh -> Xã), nhập liệu tuần tự Shopee-style, quota & chuẩn hóa số điện thoại

**Ngày tạo**: 2026-11-04  
**Ngày cập nhật**: 2026-11-14  
**Người phụ trách**: Phan Sơn  
**Người cập nhật cuối**: Phan Sơn  

## 1. Tiền điều kiện (Preconditions)

- [x] Môi trường Frontend Vite đang chạy tại `http://localhost:5173`.
- [x] Môi trường Backend Spring Boot đang chạy tại `http://localhost:8080`.
- [x] Công cụ `browsermcp` đã được nạp và kích hoạt trong phiên làm việc.
- [x] Người dùng đã xác thực với vai trò Member và mở trang Quản lý địa chỉ giao hàng `/profile/addresses`.

## 2. Phân tích rủi ro đối nghịch (Adversarial Risk Analysis)

- **Mô hình địa giới 2 cấp chuẩn hóa (FR-036, FR-037):** Thay thế mô hình 4 cấp cũ bằng 2 cấp: Tỉnh/Thành phố -> Phường/Xã/Khu vực đặc thù -> Địa chỉ chi tiết. Không còn trường Quận/Huyện hay Thôn/Ấp.
- **Biểu mẫu tuần tự (Shopee-style Progressive Form - FR-037):** Biểu mẫu mở ra ở trạng thái trống; các trường Người nhận -> Số điện thoại -> Tỉnh/Thành -> Phường/Xã -> Địa chỉ chi tiết được kích hoạt theo thứ tự tuần tự. Khi thay đổi Tỉnh/Thành phố, hệ thống bắt buộc tự động xóa dữ liệu Phường/Xã và Địa chỉ chi tiết đã chọn trước đó. Khi thay đổi Phường/Xã, tự động xóa Địa chỉ chi tiết.
- **Ràng buộc địa chỉ đầu tiên làm mặc định (FR-039):** Đối với tài khoản chưa có địa chỉ, checkbox "Đặt làm mặc định" phải bị vô hiệu hóa (`disabled`) và bắt buộc ở trạng thái `checked`.
- **Chuẩn hóa số điện thoại di động Việt Nam (FR-040):** 
  - Chặn số cố định (ví dụ: `0241234567`) và các đầu số không hợp lệ; yêu cầu khớp regex `^0[35789]\d{8}$`.
  - Tự động chuẩn hóa chuỗi quốc tế `+84 912 345 678` thành `0912345678`, loại bỏ khoảng trắng và dấu gạch nối.
- **Quota tối đa 10 địa chỉ (FR-036):** Giao diện hiển thị rõ số lượng địa chỉ đã lưu (ví dụ: "Đã lưu 0/10 địa chỉ").

## 3. Các bước kiểm thử trên trình duyệt (Browser Action Steps)

### Step 1 — Mở trang Sổ địa chỉ và mở modal thêm mới
```browser
goto http://localhost:5173/profile/addresses
wait getByRole('heading', { name: 'Sổ địa chỉ nhận hàng' })
click getByRole('button', { name: '+ Thêm địa chỉ mới' })
wait getByRole('dialog')
wait getByRole('heading', { name: 'Thêm địa chỉ nhận hàng mới' })
```

### Step 2 — Kiểm tra trạng thái khởi tạo và thứ tự kích hoạt trường
```browser
# Assertion: Checkbox "Đặt làm địa chỉ mặc định" bị disabled và checked (địa chỉ đầu tiên)
# Assertion: Dropdown Phường/Xã/Khu vực đặc thù bị disabled khi chưa chọn Tỉnh/Thành phố
# Assertion: Ô nhập "Địa chỉ chi tiết" bị disabled khi chưa chọn Phường/Xã
# Assertion: Nút "Hoàn thành" / "Lưu địa chỉ" bị disabled hoặc submit báo lỗi required
```

### Step 3 — Nhập thông tin người nhận và kiểm tra validation số điện thoại
```browser
fill getByLabel('Họ và tên người nhận *') "Nguyen Van Test"
fill getByLabel('Số điện thoại *') "0241234567"
click getByRole('button', { name: 'Hoàn thành' })
wait getByText('Số điện thoại không hợp lệ (phải là số di động Việt Nam 10 chữ số, ví dụ: 0912345678)')
```

### Step 4 — Nhập số điện thoại chuẩn hóa thành công
```browser
fill getByLabel('Số điện thoại *') "+84 912 345 678"
# Lỗi cảnh báo số điện thoại biến mất sau khi blur/re-validate
```

### Step 5 — Thao tác chọn cascading 2 cấp và kiểm tra reset trường con
```browser
# Chọn Tỉnh/Thành phố
select getByLabel('Tỉnh/Thành phố *') "Thành phố Hà Nội"
# Quan sát: Dropdown Phường/Xã được kích hoạt và tải danh sách xã thuộc Hà Nội
select getByLabel('Phường/Xã/Khu vực đặc thù *') "Phường Hàng Trống"
# Quan sát: Ô nhập "Địa chỉ chi tiết" được kích hoạt
fill getByLabel('Địa chỉ chi tiết *') "Số 12 phố Tràng Thi"

# Đổi lại Tỉnh/Thành phố sang Tỉnh khác
select getByLabel('Tỉnh/Thành phố *') "Thành phố Hồ Chí Minh"
# Assertion: Phường/Xã bị reset về rỗng
# Assertion: Địa chỉ chi tiết bị reset về rỗng
```

### Step 6 — Đóng modal và kiểm tra an toàn URL
```browser
click getByRole('button', { name: 'Đóng' })
wait --text "Sổ địa chỉ nhận hàng"
```

## 4. Kết quả kỳ vọng (Expected Assertions)

```browser
find "Sổ địa chỉ nhận hàng"
# Expected: one or more matches
find "Đã lưu 0 trên 10 địa chỉ"
# Expected: one or more matches
get url
# Expected: http://localhost:5173/profile/addresses
```

## 5. Bằng chứng kiểm thử (Evidence)

- Kịch bản chạy thực tế qua Chrome bằng `browsermcp`.
- Ảnh chụp màn hình: `screenshots/E2E-001-002--address-modal-2level.jpg`.

## 6. Lịch sử thực thi

<!-- EXECUTION:START -->
- Latest status: NOT_RUN
- Note: Cập nhật đặc tả ngày 2026-11-14 theo mô hình địa giới 2 cấp (Tỉnh -> Xã) và biểu mẫu tuần tự Shopee-style; kịch bản cần được chạy lại khi giao diện hoàn tất kết nối API danh mục mới.
- Previous execution: PASS (2026-11-09 trên mô hình 4 cấp cũ).
<!-- EXECUTION:END -->
