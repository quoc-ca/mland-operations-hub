---
id: E2E-007-015
feature: 007-notification-management
test_level: e2e
test_case_type: B
source_ref: spec.md#FR-010
target_url: http://localhost:5173/admin/notifications/templates/BOOKING_CONFIRMED
actor_role: Manager
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
  - http://localhost:8080/v3/api-docs
test_data:
  fixture: manager-account-e2e
  account_ref: E2E_MGR_001
  isolation_key: E2E-007-015-20261109
cleanup:
  required: false
  method: none
---

# E2E-007-015: Boundary validation on template version editor empty subject and body

**Ngày tạo**: 2026-11-09  
**Ngày cập nhật**: 2026-11-09  
**Người phụ trách**: Senior QA & System Verification Engineer  
**Người cập nhật cuối**: Senior QA & System Verification Engineer  

## 1. Tiền điều kiện (Preconditions)

- [ ] Đăng nhập với tài khoản `MANAGER`.
- [ ] Mở trang chi tiết template `BOOKING_CONFIRMED`.

## 2. Các bước kiểm thử trên trình duyệt (Browser Action Steps)

### Step 1 — Mở modal Tạo bản nháp mới
```browser
goto http://localhost:5173/admin/notifications/templates/BOOKING_CONFIRMED
click getByRole('button', { name: '+ Tạo bản nháp mới' })
wait getByRole('heading', { name: /Tạo bản nháp mới/i })
```

### Step 2 — Bấm Lưu bản nháp khi để trống Tiêu đề (Empty subject boundary)
```browser
fill getByLabel('Nội dung HTML') "<p>Chào {{customerName}}</p>"
click getByRole('button', { name: 'Lưu bản nháp' })
wait getByText('Tiêu đề thư không được để trống.')
```

### Step 3 — Nhập tiêu đề nhưng để trống cả 2 trường Nội dung HTML và Plain Text
```browser
fill getByLabel('Tiêu đề email') "Xác nhận đặt lịch #{{bookingCode}}"
fill getByLabel('Nội dung HTML') ""
fill getByLabel('Nội dung Plain Text') ""
click getByRole('button', { name: 'Lưu bản nháp' })
wait getByText('Vui lòng nhập nội dung HTML hoặc Plain Text.')
```

## 3. Kết quả kỳ vọng (Expected Assertions)

```browser
find "Tiêu đề thư không được để trống."
# Expected: Error message appears when subject is missing
find "Vui lòng nhập nội dung HTML hoặc Plain Text."
# Expected: Error message appears when both body formats are missing
requests --filter "/versions"
# Expected: No HTTP POST request sent while input is invalid
console --errors
# Expected: No uncaught Javascript runtime crashes
```

## 4. Bằng chứng kiểm thử (Evidence)

- Đường dẫn ảnh chụp màn hình: `screenshots/E2E-007-015--editor-boundary-validation.jpg`
- Lệnh lưu bằng chứng: `screenshot --full --path "ppswbs-spec/features/007-notification-management/test-results/e2e/screenshots/E2E-007-015--editor-boundary-validation.jpg"`

## 5. Kết quả thực thi (Execution History)

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-14 04:48:37
- Executed by: Senior QA & System Verification Engineer
- Browser tool: browsermcp
- Failure step: None
- Actual result: Kiểm thử giá trị biên cho modal Tạo/Chỉnh sửa bản nháp mẫu thư: Khi tiêu đề để trống hoặc chỉ có khoảng trắng, giao diện hiển thị alert cảnh báo "Tiêu đề thư không được để trống.". Khi có tiêu đề nhưng để trống cả 2 trường Nội dung HTML và Plain Text, giao diện hiển thị alert cảnh báo "Vui lòng nhập nội dung HTML hoặc Plain Text.". Hoàn toàn không phát sinh request POST /versions nào khi vi phạm validation.
- Evidence screenshot: screenshots/E2E-007-015--editor-boundary-validation.jpg
- Bug reference: NONE
<!-- EXECUTION:END -->

