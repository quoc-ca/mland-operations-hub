---
id: E2E-007-003
feature: 007-notification-management
test_level: e2e
test_case_type: N
source_ref: spec.md#FR-009
target_url: http://localhost:5173/admin/notifications/templates
actor_role: Manager
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
  - http://localhost:8080/v3/api-docs
test_data:
  fixture: manager-account-e2e
  account_ref: E2E_MGR_001
  isolation_key: E2E-007-003-20261109
cleanup:
  required: false
  method: none
---

# E2E-007-003: Template registry overview and sanitized fixture preview

**Ngày tạo**: 2026-11-09  
**Ngày cập nhật**: 2026-11-09  
**Người phụ trách**: Senior QA Engineer  
**Người cập nhật cuối**: Senior QA Engineer  

## 1. Tiền điều kiện (Preconditions)

- [ ] Đăng nhập với tài khoản `MANAGER`.
- [ ] Backend đã nạp dữ liệu mẫu template `BOOKING_CONFIRMED` và `RETAIL_ORDER_CONFIRMED`.

## 2. Các bước kiểm thử trên trình duyệt (Browser Action Steps)

### Step 1 — Mở trang Danh sách mẫu thông báo
```browser
goto http://localhost:5173/admin/notifications/templates
wait getByRole('heading', { name: 'Quản lý mẫu thông báo email' })
```

### Step 2 — Mở chi tiết mẫu BOOKING_CONFIRMED
```browser
click getByRole('link', { name: 'Chi tiết BOOKING_CONFIRMED' })
wait getByRole('heading', { name: 'Xác nhận đặt lịch Workshop' })
```

### Step 3 — Kích hoạt modal Xem trước mẫu
```browser
click getByRole('button', { name: 'Xem trước' })
wait getByRole('heading', { name: 'Xem trước mẫu thông báo email' })
```

### Step 4 — Chuyển sang xem nội dung dạng văn bản thuần (Plain Text)
```browser
click getByRole('button', { name: 'Nội dung Plain Text' })
wait getByText('Dữ liệu mẫu an toàn')
```

## 3. Kết quả kỳ vọng (Expected Assertions)

```browser
find "Quản lý mẫu thông báo email"
# Expected: Exactly one match
find "BOOKING_CONFIRMED"
# Expected: One or more matches
find "Dữ liệu mẫu an toàn - Không sử dụng dữ liệu khách hàng thật"
# Expected: Exactly one match
find "Xác nhận đặt lịch"
# Expected: Rendered preview content visible
```

## 4. Bằng chứng kiểm thử (Evidence)

- Đường dẫn ảnh chụp màn hình: `screenshots/E2E-007-003--template-preview-safe.jpg`
- Lệnh lưu bằng chứng: `screenshot --full --path "ppswbs-spec/features/007-notification-management/e2e/screenshots/E2E-007-003--template-preview-safe.jpg"`

## 5. Kết quả thực thi (Execution History)

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-14 04:36:28
- Executed by: Senior QA & System Verification Engineer
- Browser tool: browsermcp
- Failure step: None
- Actual result: Trang danh sách mẫu thông báo hiển thị đầy đủ các template hệ thống. Modal Xem trước mẫu thư hiển thị chính xác tiêu đề, nội dung rendered với dữ liệu fixture an toàn (BK-SAMPLE-2026), có cảnh báo "Dữ liệu mẫu an toàn - Không sử dụng dữ liệu khách hàng thật" và hỗ trợ chuyển đổi giữa HTML/Plain Text.
- Evidence screenshot: screenshots/E2E-007-003--template-preview-safe.jpg
- Bug reference: NONE
<!-- EXECUTION:END -->

