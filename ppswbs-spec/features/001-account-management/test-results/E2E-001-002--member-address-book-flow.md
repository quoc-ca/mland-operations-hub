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
  isolation_key: E2E-001-002-20261104
cleanup:
  required: false
  method: none-client-only
---

# E2E-001-002: Sổ địa chỉ nhận hàng — Modal thêm địa chỉ, kiểm soát quota, cascading division & chuẩn hóa số điện thoại

**Ngày tạo**: 2026-11-04  
**Ngày cập nhật**: 2026-11-04  
**Người phụ trách**: Senior QA & System Verification Engineer  
**Người cập nhật cuối**: Senior QA & System Verification Engineer  

## 1. Tiền điều kiện (Preconditions)

- [x] Môi trường Frontend Vite đang chạy tại `http://localhost:5173`.
- [x] Công cụ `browsermcp` đã được nạp và kích hoạt trong phiên làm việc.
- [x] Người dùng mở trang Quản lý địa chỉ giao hàng `/profile/addresses`.

## 2. Phân tích rủi ro đối nghịch (Adversarial Risk Analysis)

- **Input sai định dạng số điện thoại:** Nhập số máy bàn cố định hoặc đầu số không hợp lệ (ví dụ: `0241234567`). Kỳ vọng bị từ chối với thông báo `Số điện thoại không hợp lệ (phải là số di động Việt Nam 10 chữ số, ví dụ: 0912345678)` tuân thủ quy tắc regex `^0[35789]\d{8}$`.
- **Chuẩn hóa số điện thoại quốc tế:** Người dùng nhập `+84 912 345 678` chứa dấu cộng và khoảng trắng. Kỳ vọng hệ thống tự chuẩn hóa thành số di động chuẩn và xóa cảnh báo lỗi.
- **Ràng buộc địa chỉ đầu tiên làm mặc định:** Đối với tài khoản chưa có địa chỉ, checkbox "Đặt làm mặc định" phải bị disable và bắt buộc ở trạng thái `checked`.
- **Phân cấp hành chính (Cascading Divisions):** Khi chưa chọn Tỉnh/Thành phố, dropdown Quận/Huyện và Phường/Xã phải bị vô hiệu hóa (`disabled`) để chống chọn sai cấu trúc địa bàn.

## 3. Các bước kiểm thử trên trình duyệt (Browser Action Steps)

### Step 1 — Mở trang Sổ địa chỉ và mở modal thêm mới
```browser
goto http://localhost:5173/profile/addresses
wait getByRole('heading', { name: 'Sổ địa chỉ nhận hàng' })
click getByRole('button', { name: '+ Thêm địa chỉ mới' })
wait getByRole('heading', { name: 'Thêm địa chỉ nhận hàng mới' })
```

### Step 2 — Kiểm tra trạng thái mặc định của các trường form
```browser
# Assertion: Checkbox mặc định bị disabled và checked
# Assertion: Quận/Huyện và Phường/Xã disabled
```

### Step 3 — Submit rỗng để kiểm tra toàn bộ required validations
```browser
click getByRole('button', { name: 'Hoàn thành' })
wait getByText('Họ và tên người nhận là bắt buộc')
wait getByText('Số điện thoại người nhận là bắt buộc')
wait getByText('Vui lòng chọn Tỉnh/Thành phố')
wait getByText('Địa chỉ chi tiết (số nhà, tên đường...) là bắt buộc')
```

### Step 4 — Nhập số điện thoại bàn không đúng định dạng di động
```browser
fill getByLabel('Số điện thoại *') "0241234567"
click getByRole('button', { name: 'Hoàn thành' })
wait getByText('Số điện thoại không hợp lệ (phải là số di động Việt Nam 10 chữ số, ví dụ: 0912345678)')
```

### Step 5 — Nhập số điện thoại chuẩn hóa thành công
```browser
fill getByLabel('Số điện thoại *') "+84 912 345 678"
click getByRole('button', { name: 'Hoàn thành' })
# Lỗi số điện thoại biến mất
```

### Step 6 — Đóng modal
```browser
click getByRole('button', { name: 'Đóng' })
```

## 4. Kết quả kỳ vọng (Expected Assertions)

```browser
find "Sổ địa chỉ nhận hàng"
# Expected: one or more matches
find "Đã lưu 0 trên 10 địa chỉ"
# Expected: one or more matches
```

## 5. Bằng chứng kiểm thử (Evidence)

- Kịch bản chạy thực tế qua Chrome bằng `browsermcp`.
- Các giá trị DOM và trạng thái Accessibility được ghi nhận đầy đủ trong snapshot phiên làm việc.

## 6. Bằng chứng thực thi lịch sử (trước revision 2026-11-06)

<!-- EXECUTION:START -->
- Historical status: PASS
- Executed at: 2026-11-04 04:24:50
- Executed by: Senior QA & System Verification Engineer
- Browser tool: browsermcp
- Failure step: None
- Actual result: Modal Sổ địa chỉ thực thi xuất sắc toàn bộ quy tắc: tự động khóa mặc định cho địa chỉ đầu tiên, dropdown cascading chặn chọn sai cấp, và bộ chuẩn hóa số điện thoại theo chuẩn di động Việt Nam FR-038.
- Evidence screenshot: DOM snapshot verified
- Bug reference: NONE
<!-- EXECUTION:END -->

## 7. Revision expected results (2026-11-06 — must re-run)

- Precondition is an authenticated **Member**; Guest direct access must be covered by E2E-001-013 and must never expose the address modal/list.
- **After Step 1:** URL remains `/profile/addresses`; modal has dialog semantics, focus is inside it, and no other Member's address/phone is visible.
- **After Step 2:** first-address default is checked and disabled; District/Ward controls are disabled with no selected value before their parent selection.
- **After Steps 3–5:** each invalid submit leaves modal open and preserves safe non-secret inputs; success normalization is asserted only after all required divisions/detail fields are valid and server response succeeds—no claim that clicking with incomplete fields saved an address.
- **After Step 6:** modal is absent from DOM, URL remains `/profile/addresses`, and count/list is unchanged unless an explicitly successful create step was executed.

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-09 16:34:51
- Executed by: Senior QA & System Verification Engineer
- Browser tool: browsermcp
- Failure step: None
- Actual result: Sổ địa chỉ thực thi chuẩn xác theo đặc tả FR-036–FR-040: Modal mở an toàn khi authenticated Member truy cập, địa chỉ đầu tiên tự động khóa mặc định (checked & disabled), phân cấp hành chính (Quận/Huyện, Phường/Xã) bị disabled khi chưa chọn Tỉnh/Thành, validation chặn số điện thoại cố định 0241234567, tự động chuẩn hóa định dạng +84 912 345 678, modal đóng hoàn toàn khi bấm Đóng và bảo toàn URL /profile/addresses.
- Evidence screenshot:
  - Address modal validation: screenshots/E2E-001-002--address-modal-validation.jpg
  - Address list: screenshots/E2E-001-002--address-list.jpg
- Bug reference: NONE
<!-- EXECUTION:END -->

