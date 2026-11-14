---
id: E2E-007-007
feature: 007-notification-management
test_level: e2e
test_case_type: N
source_ref: spec.md#FR-022
target_url: http://localhost:5173/admin/notifications/requests
actor_role: Manager
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
  - http://localhost:8080/v3/api-docs
test_data:
  fixture: seed-request-with-attempts
  account_ref: E2E_MGR_001
  isolation_key: E2E-007-007-20261109
cleanup:
  required: false
  method: none
---

# E2E-007-007: Inspect diagnostic details, attempt logs, and audit records in drawer

**Ngày tạo**: 2026-11-09  
**Ngày cập nhật**: 2026-11-09  
**Người phụ trách**: Senior QA Engineer  
**Người cập nhật cuối**: Senior QA Engineer  

## 1. Tiền điều kiện (Preconditions)

- [ ] Đăng nhập với tài khoản `MANAGER`.
- [ ] Tồn tại request gửi email có kèm các bản ghi attempt trong DB.

## 2. Các bước kiểm thử trên trình duyệt (Browser Action Steps)

### Step 1 — Mở danh sách và bấm Xem chi tiết request đầu tiên
```browser
goto http://localhost:5173/admin/notifications/requests
click getByRole('button', { name: 'Xem chi tiết' })
wait getByRole('heading', { name: /Chi tiết yêu cầu gửi thư/i })
```

## 3. Kết quả kỳ vọng (Expected Assertions)

```browser
find "Chi tiết yêu cầu gửi thư"
# Expected: Drawer heading visible
find "Lịch sử các lần gửi thư (Attempts)"
# Expected: Attempts section rendered
find "Lịch sử thao tác & kiểm toán (Audit Trail)"
# Expected: Audit section rendered
console --errors
# Expected: No errors
```

## 4. Bằng chứng kiểm thử (Evidence)

- Đường dẫn ảnh chụp màn hình: `screenshots/E2E-007-007--diagnostic-drawer.jpg`
- Lệnh lưu bằng chứng: `screenshot --full --path "ppswbs-spec/features/007-notification-management/e2e/screenshots/E2E-007-007--diagnostic-drawer.jpg"`

## 5. Kết quả thực thi (Execution History)

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-14 04:40:13
- Executed by: Senior QA & System Verification Engineer
- Browser tool: browsermcp
- Failure step: None
- Actual result: Drawer chi tiết yêu cầu #1001 mở trơn tru. Hiển thị đầy đủ thông tin chẩn đoán, masked recipient, nhật ký lần gửi (Attempts) và nhật ký kiểm toán (Audit Trail).
- Evidence screenshot: screenshots/E2E-007-007--diagnostic-drawer.jpg
- Bug reference: NONE
<!-- EXECUTION:END -->

