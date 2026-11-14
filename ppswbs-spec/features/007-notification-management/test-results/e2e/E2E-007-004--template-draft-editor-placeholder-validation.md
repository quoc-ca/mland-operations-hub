---
id: E2E-007-004
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
  isolation_key: E2E-007-004-20261109
cleanup:
  required: true
  method: delete-draft-version
---

# E2E-007-004: Real-time placeholder validation and draft saving in template editor

**Ngày tạo**: 2026-11-09  
**Ngày cập nhật**: 2026-11-09  
**Người phụ trách**: Senior QA Engineer  
**Người cập nhật cuối**: Senior QA Engineer  

## 1. Tiền điều kiện (Preconditions)

- [ ] Đăng nhập với tài khoản `MANAGER`.
- [ ] Mở trang chi tiết template `BOOKING_CONFIRMED`.

## 2. Các bước kiểm thử trên trình duyệt (Browser Action Steps)

### Step 1 — Mở modal Tạo bản nháp mới
```browser
goto http://localhost:5173/admin/notifications/templates/BOOKING_CONFIRMED
click getByRole('button', { name: 'Tạo bản nháp mới' })
wait getByRole('heading', { name: 'Tạo bản nháp mới (Xác nhận đặt lịch Workshop)' })
```

### Step 2 — Nhập biến không hợp lệ để kiểm tra bộ lọc client
```browser
fill getByLabel('Tiêu đề email') "Xác nhận với biến {{invalidField123}}"
wait getByText('Biến không hợp lệ sẽ bị máy chủ từ chối')
```

### Step 3 — Khắc phục bằng biến hợp lệ và lưu bản nháp
```browser
fill getByLabel('Tiêu đề email') "Xác nhận đặt lịch #{{bookingCode}}"
fill getByLabel('Nội dung HTML') "<p>Chào {{customerName}}, đơn đặt #{{bookingCode}} thành công.</p>"
fill getByLabel('Nội dung Plain Text') "Chào {{customerName}}, đơn đặt #{{bookingCode}} thành công."
click getByRole('button', { name: 'Lưu bản nháp' })
wait getByText('Bản nháp')
```

## 3. Kết quả kỳ vọng (Expected Assertions)

```browser
find "Bản nháp"
# Expected: Draft badge visible in versions table
requests --filter "/api/v1/notification-templates/BOOKING_CONFIRMED/versions"
# Expected: POST 201
console --errors
# Expected: No uncaught errors
```

## 4. Bằng chứng kiểm thử (Evidence)

- Đường dẫn ảnh chụp màn hình: `screenshots/E2E-007-004--draft-created.jpg`
- Lệnh lưu bằng chứng: `screenshot --full --path "ppswbs-spec/features/007-notification-management/e2e/screenshots/E2E-007-004--draft-created.jpg"`

## 5. Kết quả thực thi (Execution History)

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-14 04:36:51
- Executed by: Senior QA & System Verification Engineer
- Browser tool: browsermcp
- Failure step: None
- Actual result: Modal soạn thảo bản nháp mới hoạt động chính xác. Lưu bản nháp với placeholder hợp lệ thành công; thông báo "Đã tạo bản nháp mới v2" hiển thị và badge "Bản nháp" xuất hiện trong bảng lịch sử phiên bản.
- Evidence screenshot: screenshots/E2E-007-004--draft-created.jpg
- Bug reference: NONE
<!-- EXECUTION:END -->

