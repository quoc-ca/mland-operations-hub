---
id: E2E-007-010
feature: 007-notification-management
test_level: e2e
test_case_type: A
source_ref: spec.md#FR-016
target_url: http://localhost:5173/admin/notifications/requests
actor_role: Admin
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
  - http://localhost:8080/v3/api-docs
test_data:
  fixture: seed-unknown-outcome-request
  account_ref: E2E_ADMIN_001
  isolation_key: E2E-007-010-20261109
cleanup:
  required: false
  method: none
---

# E2E-007-010: Adversarial role and risk confirmation gate for UNKNOWN_OUTCOME resend

**Ngày tạo**: 2026-11-09  
**Ngày cập nhật**: 2026-11-09  
**Người phụ trách**: Senior QA Engineer  
**Người cập nhật cuối**: Senior QA Engineer  

## 1. Tiền điều kiện (Preconditions)

- [ ] Tồn tại request ID 1003 có trạng thái `UNKNOWN_OUTCOME` trong DB.
- [ ] Phiên 1 kiểm tra với tài khoản `MANAGER`.
- [ ] Phiên 2 kiểm tra với tài khoản `ADMIN`.

## 2. Các bước kiểm thử trên trình duyệt (Browser Action Steps)

### Step 1 (Phản biện Manager) — Manager mở modal gửi lại trên UNKNOWN_OUTCOME
```browser
goto http://localhost:5173/admin/notifications/requests
click getByRole('button', { name: 'Xem chi tiết #1003' })
click getByRole('button', { name: 'Gửi lại thư' })
wait getByText('Từ chối quyền: Chỉ Quản trị viên (ADMIN) mới có quyền gửi lại')
```

### Step 2 (Admin Override) — Đăng nhập tài khoản Admin và kiểm tra checkbox rủi ro
```browser
goto http://localhost:5173/admin/notifications/requests
click getByRole('button', { name: 'Xem chi tiết #1003' })
click getByRole('button', { name: 'Gửi lại thư' })
wait getByText(/Tôi đã xác nhận với dịch vụ mail và chấp nhận rủi ro/i)
```

### Step 3 — Nhập lý do và đánh dấu checkbox chấp nhận rủi ro
```browser
fill getByLabel(/Lý do gửi lại/i) "Admin đã kiểm tra log GreenMail và xác nhận cần gửi lại"
click getByRole('checkbox')
click getByRole('button', { name: 'Xác nhận gửi lại' })
wait 1000
```

## 3. Kết quả kỳ vọng (Expected Assertions)

```browser
find "Tôi đã xác nhận với dịch vụ mail và chấp nhận rủi ro khách hàng có thể nhận thư trùng"
# Expected: Risk checkbox visible only for Admin
requests --filter "/resend"
# Expected: Body contains duplicateRiskAccepted=true and status is 201
```

## 4. Bằng chứng kiểm thử (Evidence)

- Đường dẫn ảnh chụp màn hình: `screenshots/E2E-007-010--unknown-outcome-gate.jpg`
- Lệnh lưu bằng chứng: `screenshot --full --path "ppswbs-spec/features/007-notification-management/e2e/screenshots/E2E-007-010--unknown-outcome-gate.jpg"`

## 5. Kết quả thực thi (Execution History)

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-14 04:42:54
- Executed by: Senior QA & System Verification Engineer
- Browser tool: browsermcp
- Failure step: None
- Actual result: Cổng phản biện đối kháng kiểm tra đúng hai tầng: Tài khoản vai trò MANAGER bị chặn hoàn toàn không cho phép gửi lại thư UNKNOWN_OUTCOME ("Từ chối quyền: Chỉ Quản trị viên (ADMIN) mới có quyền gửi lại"). Tài khoản ADMIN được hiển thị checkbox xác nhận chấp nhận rủi ro nhận thư trùng; sau khi tích chọn và nhập lý do, hệ thống cho phép tạo yêu cầu gửi lại (#1007) và ghi vết kiểm toán đầy đủ.
- Evidence screenshot: screenshots/E2E-007-010--unknown-outcome-resend.jpg
- Bug reference: NONE
<!-- EXECUTION:END -->

