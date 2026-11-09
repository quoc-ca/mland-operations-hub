---
id: E2E-007-008
feature: 007-notification-management
test_level: e2e
test_case_type: N
source_ref: spec.md#FR-015
target_url: http://localhost:5173/admin/notifications/requests
actor_role: Manager
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
  - http://localhost:8080/v3/api-docs
test_data:
  fixture: seed-sent-request
  account_ref: E2E_MGR_001
  isolation_key: E2E-007-008-20261109
cleanup:
  required: false
  method: none
---

# E2E-007-008: Operator resends SENT request with mandatory audited reason

**Ngày tạo**: 2026-11-09  
**Ngày cập nhật**: 2026-11-09  
**Người phụ trách**: Senior QA Engineer  
**Người cập nhật cuối**: Senior QA Engineer  

## 1. Tiền điều kiện (Preconditions)

- [ ] Đăng nhập với tài khoản `MANAGER`.
- [ ] Tồn tại một request có trạng thái `SENT` trong DB.

## 2. Các bước kiểm thử trên trình duyệt (Browser Action Steps)

### Step 1 — Mở Drawer chi tiết của request SENT
```browser
goto http://localhost:5173/admin/notifications/requests
click getByRole('button', { name: 'Xem chi tiết' })
wait getByRole('button', { name: 'Gửi lại thư' })
```

### Step 2 — Bấm mở modal Gửi lại thư
```browser
click getByRole('button', { name: 'Gửi lại thư' })
wait getByRole('heading', { name: /Gửi lại email thông báo/i })
```

### Step 3 — Nhập lý do gửi lại và xác nhận
```browser
fill getByLabel(/Lý do gửi lại/i) "Khách hàng thông báo chưa nhận được email xác nhận trong hộp thư đến"
click getByRole('button', { name: 'Xác nhận gửi lại' })
wait 1000
```

## 3. Kết quả kỳ vọng (Expected Assertions)

```browser
requests --filter "/resend"
# Expected: POST 201 with newRequestId
find "Yêu cầu gửi lại đã được xếp hàng"
# Expected: Success notification message
```

## 4. Bằng chứng kiểm thử (Evidence)

- Đường dẫn ảnh chụp màn hình: `screenshots/E2E-007-008--resend-success.jpg`
- Lệnh lưu bằng chứng: `screenshot --full --path "ppswbs-spec/features/007-notification-management/e2e/screenshots/E2E-007-008--resend-success.jpg"`

## 5. Kết quả thực thi (Execution History)

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

