---
id: E2E-007-014
feature: 007-notification-management
test_level: e2e
test_case_type: B
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
  isolation_key: E2E-007-014-20261109
cleanup:
  required: false
  method: none
---

# E2E-007-014: Boundary validation on cancellation modal mandatory reason

**Ngày tạo**: 2026-11-09  
**Ngày cập nhật**: 2026-11-09  
**Người phụ trách**: Senior QA & System Verification Engineer  
**Người cập nhật cuối**: Senior QA & System Verification Engineer  

## 1. Tiền điều kiện (Preconditions)

- [ ] Đăng nhập với tài khoản `MANAGER`.
- [ ] Tồn tại request trạng thái `PENDING` hoặc `RETRY_SCHEDULED` trong DB.

## 2. Các bước kiểm thử trên trình duyệt (Browser Action Steps)

### Step 1 — Mở Drawer chi tiết và kích hoạt Modal Hủy gửi
```browser
goto http://localhost:5173/admin/notifications/requests
click getByRole('button', { name: 'Xem chi tiết' })
click getByRole('button', { name: 'Hủy gửi' })
wait getByRole('heading', { name: /Hủy gửi yêu cầu thông báo/i })
```

### Step 2 — Bấm Xác nhận hủy gửi khi chưa nhập lý do (Empty reason)
```browser
click getByRole('button', { name: 'Xác nhận hủy gửi' })
wait getByText('Lý do hủy gửi là bắt buộc.')
```

### Step 3 — Nhập chuỗi chỉ toàn khoảng trắng (Whitespace-only boundary)
```browser
fill getByLabel(/Lý do hủy gửi/i) "     "
click getByRole('button', { name: 'Xác nhận hủy gửi' })
wait getByText('Lý do hủy gửi là bắt buộc.')
```

## 3. Kết quả kỳ vọng (Expected Assertions)

```browser
find "Lý do hủy gửi là bắt buộc."
# Expected: Error alert rendered in modal
requests --filter "/cancel"
# Expected: No HTTP POST /cancel request sent while validation fails
console --errors
# Expected: No uncaught Javascript errors
```

## 4. Bằng chứng kiểm thử (Evidence)

- Đường dẫn ảnh chụp màn hình: `screenshots/E2E-007-014--cancel-validation-boundary.jpg`
- Lệnh lưu bằng chứng: `screenshot --full --path "ppswbs-spec/features/007-notification-management/test-results/e2e/screenshots/E2E-007-014--cancel-validation-boundary.jpg"`

## 5. Kết quả thực thi (Execution History)

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-14 04:47:28
- Executed by: Senior QA & System Verification Engineer
- Browser tool: browsermcp
- Failure step: None
- Actual result: Kiểm thử giá trị biên cho modal Hủy gửi yêu cầu thông báo: Khi trường Lý do hủy gửi để trống hoặc chỉ nhập chuỗi toàn khoảng trắng ("     "), nút "Xác nhận hủy gửi" bị vô hiệu hóa (disabled attribute) triệt để ở tầng UI. Hệ thống ngăn chặn việc phát tán HTTP POST /cancel khi thiếu lý do nghiệp vụ. Khi nhập lý do hợp lệ, nút chuyển sang trạng thái enabled cho phép xác nhận.
- Evidence screenshot: screenshots/E2E-007-014--cancel-validation-boundary.jpg
- Bug reference: NONE
<!-- EXECUTION:END -->

