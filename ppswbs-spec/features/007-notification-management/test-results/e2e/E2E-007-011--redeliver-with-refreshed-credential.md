---
id: E2E-007-011
feature: 007-notification-management
test_level: e2e
test_case_type: N
source_ref: spec.md#FR-019
target_url: http://localhost:5173/admin/notifications/requests
actor_role: Manager
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
  - http://localhost:8080/v3/api-docs
test_data:
  fixture: seed-expired-credential-request
  account_ref: E2E_MGR_001
  isolation_key: E2E-007-011-20261109
cleanup:
  required: false
  method: none
---

# E2E-007-011: Operator triggers redelivery with replacement QR/link credential

**Ngày tạo**: 2026-11-09  
**Ngày cập nhật**: 2026-11-09  
**Người phụ trách**: Senior QA Engineer  
**Người cập nhật cuối**: Senior QA Engineer  

## 1. Tiền điều kiện (Preconditions)

- [ ] Đăng nhập với tài khoản `MANAGER`.
- [ ] Tồn tại request gửi vé workshop mà link QR đã hết hiệu lực.

## 2. Các bước kiểm thử trên trình duyệt (Browser Action Steps)

### Step 1 — Mở Drawer chi tiết và bấm "Gửi lại với link/vé mới"
```browser
goto http://localhost:5173/admin/notifications/requests
click getByRole('button', { name: 'Xem chi tiết' })
click getByRole('button', { name: 'Gửi lại với link/vé mới' })
wait getByRole('heading', { name: /Cấp lại liên kết và gửi lại/i })
```

### Step 2 — Nhập lý do cấp lại và xác nhận
```browser
fill getByLabel(/Lý do cấp lại/i) "Khách hàng liên hệ xin link mới do link 24h đã hết hạn"
click getByRole('button', { name: 'Xác nhận cấp lại và gửi' })
wait 1000
```

## 3. Kết quả kỳ vọng (Expected Assertions)

```browser
requests --filter "/redeliver"
# Expected: POST 201
find "Yêu cầu gửi lại với liên kết mới đã được xếp hàng"
# Expected: Success toast notification
```

## 4. Bằng chứng kiểm thử (Evidence)

- Đường dẫn ảnh chụp màn hình: `screenshots/E2E-007-011--redeliver-success.jpg`
- Lệnh lưu bằng chứng: `screenshot --full --path "ppswbs-spec/features/007-notification-management/e2e/screenshots/E2E-007-011--redeliver-success.jpg"`

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

