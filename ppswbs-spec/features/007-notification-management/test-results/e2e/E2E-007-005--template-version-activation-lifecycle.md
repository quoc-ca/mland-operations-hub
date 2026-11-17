---
id: E2E-007-005
feature: 007-notification-management
test_level: e2e
test_case_type: N
source_ref: spec.md#FR-009
target_url: http://localhost:5173/admin/notifications/templates/BOOKING_CONFIRMED
actor_role: Admin
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
  - http://localhost:8080/v3/api-docs
test_data:
  fixture: admin-account-e2e
  account_ref: E2E_ADMIN_001
  isolation_key: E2E-007-005-20261109
cleanup:
  required: true
  method: revert-active-version
---

# E2E-007-005: Activate draft template version with confirmation modal

**Ngày tạo**: 2026-11-09  
**Ngày cập nhật**: 2026-11-09  
**Người phụ trách**: Senior QA Engineer  
**Người cập nhật cuối**: Senior QA Engineer  

## 1. Tiền điều kiện (Preconditions)

- [ ] Đăng nhập với tài khoản `ADMIN`.
- [ ] Mẫu `BOOKING_CONFIRMED` đang có một bản nháp hợp lệ (ví dụ Version 2 hoặc 3).

## 2. Các bước kiểm thử trên trình duyệt (Browser Action Steps)

### Step 1 — Mở chi tiết template và bấm Kích hoạt phiên bản nháp
```browser
goto http://localhost:5173/admin/notifications/templates/BOOKING_CONFIRMED
click getByRole('button', { name: 'Kích hoạt' })
wait getByRole('heading', { name: /Xác nhận kích hoạt phiên bản/i })
```

### Step 2 — Xác nhận kích hoạt trong modal
```browser
click getByRole('button', { name: 'Xác nhận kích hoạt' })
wait getByText('Đang áp dụng')
```

## 3. Kết quả kỳ vọng (Expected Assertions)

```browser
find "Đang áp dụng"
# Expected: Activated badge appears on new version
find "Lưu trữ"
# Expected: Previous version marked as deactivated/archived
requests --filter "/activate"
# Expected: POST 200
```

## 4. Bằng chứng kiểm thử (Evidence)

- Đường dẫn ảnh chụp màn hình: `screenshots/E2E-007-005--version-activated.jpg`
- Lệnh lưu bằng chứng: `screenshot --full --path "ppswbs-spec/features/007-notification-management/e2e/screenshots/E2E-007-005--version-activated.jpg"`

## 5. Kết quả thực thi (Execution History)

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-14 04:37:14
- Executed by: Senior QA & System Verification Engineer
- Browser tool: browsermcp
- Failure step: None
- Actual result: Modal xác nhận kích hoạt phiên bản v2 hiển thị chính xác. Khi bấm 'Xác nhận kích hoạt', hệ thống kích hoạt Version 2 thành công ('Đang áp dụng') và chuyển Version 1 trước đó sang trạng thái 'Lưu trữ'.
- Evidence screenshot: screenshots/E2E-007-005--version-activated.jpg
- Bug reference: NONE
<!-- EXECUTION:END -->

