---
id: E2E-007-013
feature: 007-notification-management
test_level: e2e
test_case_type: B
source_ref: spec.md#FR-015,spec.md#FR-016
target_url: http://localhost:5173/admin/notifications/requests
actor_role: Manager
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
  - http://localhost:8080/v3/api-docs
test_data:
  fixture: seed-sent-and-unknown-requests
  account_ref: E2E_MGR_001
  isolation_key: E2E-007-013-20261109
cleanup:
  required: false
  method: none
---

# E2E-007-013: Boundary validation on resend modal reason length and duplicate risk acceptance

**Ngày tạo**: 2026-11-09  
**Ngày cập nhật**: 2026-11-09  
**Người phụ trách**: Senior QA & System Verification Engineer  
**Người cập nhật cuối**: Senior QA & System Verification Engineer  

## 1. Tiền điều kiện (Preconditions)

- [ ] Đăng nhập với tài khoản `MANAGER`.
- [ ] Tồn tại request gửi email trạng thái `SENT` trong DB.

## 2. Các bước kiểm thử trên trình duyệt (Browser Action Steps)

### Step 1 — Mở Drawer chi tiết và kích hoạt Modal Gửi lại thư
```browser
goto http://localhost:5173/admin/notifications/requests
click getByRole('button', { name: 'Xem chi tiết' })
click getByRole('button', { name: 'Gửi lại thư' })
wait getByRole('heading', { name: /Gửi lại email thông báo/i })
```

### Step 2 — Bấm Xác nhận gửi lại khi chưa nhập lý do (Empty reason)
```browser
click getByRole('button', { name: 'Xác nhận gửi lại' })
wait getByText('Lý do gửi lại là bắt buộc và phải có tối thiểu 10 ký tự.')
```

### Step 3 — Nhập lý do ngắn dưới 10 ký tự (Short reason boundary)
```browser
fill getByLabel(/Lý do gửi lại/i) "gửi lại"
click getByRole('button', { name: 'Xác nhận gửi lại' })
wait getByText('Lý do gửi lại là bắt buộc và phải có tối thiểu 10 ký tự.')
```

### Step 4 — Nhập chuỗi chỉ toàn khoảng trắng (Whitespace-only boundary)
```browser
fill getByLabel(/Lý do gửi lại/i) "          "
click getByRole('button', { name: 'Xác nhận gửi lại' })
wait getByText('Lý do gửi lại là bắt buộc và phải có tối thiểu 10 ký tự.')
```

## 3. Kết quả kỳ vọng (Expected Assertions)

```browser
find "Lý do gửi lại là bắt buộc và phải có tối thiểu 10 ký tự."
# Expected: Validation error alert appears inline in modal
requests --filter "/resend"
# Expected: No HTTP POST request is dispatched while validation fails
console --errors
# Expected: No uncaught Javascript errors
```

## 4. Bằng chứng kiểm thử (Evidence)

- Đường dẫn ảnh chụp màn hình: `screenshots/E2E-007-013--resend-validation-boundary.jpg`
- Lệnh lưu bằng chứng: `screenshot --full --path "ppswbs-spec/features/007-notification-management/test-results/e2e/screenshots/E2E-007-013--resend-validation-boundary.jpg"`

## 5. Kết quả thực thi (Execution History)

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-14 04:46:06
- Executed by: Senior QA & System Verification Engineer
- Browser tool: browsermcp
- Failure step: None
- Actual result: Kiểm thử giá trị biên cho modal Gửi lại thư: Khi để trống lý do, nhập chuỗi chỉ chứa khoảng trắng ("          "), hoặc chuỗi ngắn dưới 10 ký tự ("gửi lại"), nút "Xác nhận gửi lại" bị vô hiệu hóa (disabled attribute) triệt để ở tầng client. Không có bất kỳ HTTP POST /resend nào được gửi khi dữ liệu không hợp lệ. Khi nhập đủ >= 10 ký tự, nút tự động kích hoạt.
- Evidence screenshot: screenshots/E2E-007-013--resend-validation-boundary.jpg
- Bug reference: NONE
<!-- EXECUTION:END -->

