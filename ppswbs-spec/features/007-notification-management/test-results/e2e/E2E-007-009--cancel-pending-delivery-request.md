---
id: E2E-007-009
feature: 007-notification-management
test_level: e2e
test_case_type: N
source_ref: spec.md#FR-017
target_url: http://localhost:5173/admin/notifications/requests
actor_role: Manager
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
  - http://localhost:8080/v3/api-docs
test_data:
  fixture: seed-pending-request
  account_ref: E2E_MGR_001
  isolation_key: E2E-007-009-20261109
cleanup:
  required: false
  method: none
---

# E2E-007-009: Cancel pending delivery request with mandatory cancellation reason

**Ngày tạo**: 2026-11-09  
**Ngày cập nhật**: 2026-11-09  
**Người phụ trách**: Senior QA Engineer  
**Người cập nhật cuối**: Senior QA Engineer  

## 1. Tiền điều kiện (Preconditions)

- [ ] Đăng nhập với tài khoản `MANAGER`.
- [ ] Tồn tại một request có trạng thái `PENDING` hoặc `RETRY_SCHEDULED`.

## 2. Các bước kiểm thử trên trình duyệt (Browser Action Steps)

### Step 1 — Mở Drawer chi tiết của request PENDING
```browser
goto http://localhost:5173/admin/notifications/requests
click getByRole('button', { name: 'Xem chi tiết' })
wait getByRole('button', { name: 'Hủy gửi' })
```

### Step 2 — Bấm mở modal Hủy gửi
```browser
click getByRole('button', { name: 'Hủy gửi' })
wait getByRole('heading', { name: /Hủy gửi yêu cầu thông báo/i })
```

### Step 3 — Nhập lý do hủy và xác nhận
```browser
fill getByLabel(/Lý do hủy gửi/i) "Khách hàng đổi ý hủy đơn đặt lịch ngay sau khi xác nhận"
click getByRole('button', { name: 'Xác nhận hủy gửi' })
wait getByText('CANCELLED')
```

## 3. Kết quả kỳ vọng (Expected Assertions)

```browser
find "CANCELLED"
# Expected: State badge changed to CANCELLED (slate gray)
requests --filter "/cancel"
# Expected: POST 200
```

## 4. Bằng chứng kiểm thử (Evidence)

- Đường dẫn ảnh chụp màn hình: `screenshots/E2E-007-009--cancel-success.jpg`
- Lệnh lưu bằng chứng: `screenshot --full --path "ppswbs-spec/features/007-notification-management/e2e/screenshots/E2E-007-009--cancel-success.jpg"`

## 5. Kết quả thực thi (Execution History)

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-14 04:41:30
- Executed by: Senior QA & System Verification Engineer
- Browser tool: browsermcp
- Failure step: None
- Actual result: Nút 'Hủy gửi' chỉ bật đối với request ở trạng thái PENDING hoặc RETRY_SCHEDULED. Sau khi nhập lý do hủy, request #1006 chuyển ngay sang trạng thái terminal 'CANCELLED', hiển thị badge màu xám và ghi nhận nhật ký kiểm toán.
- Evidence screenshot: screenshots/E2E-007-009--cancel-pending-delivery-request.jpg
- Bug reference: NONE
<!-- EXECUTION:END -->

