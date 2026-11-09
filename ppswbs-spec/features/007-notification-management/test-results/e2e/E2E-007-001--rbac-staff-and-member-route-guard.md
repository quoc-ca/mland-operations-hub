---
id: E2E-007-001
feature: 007-notification-management
test_level: e2e
test_case_type: A
source_ref: spec.md#FR-027
target_url: http://localhost:5173/admin/notifications/templates
actor_role: Staff
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
  - http://localhost:8080/v3/api-docs
test_data:
  fixture: staff-account-e2e
  account_ref: E2E_STAFF_001
  isolation_key: E2E-007-001-20261109
cleanup:
  required: false
  method: none
---

# E2E-007-001: Staff role access denial on notification management admin routes

**Ngày tạo**: 2026-11-09  
**Ngày cập nhật**: 2026-11-09  
**Người phụ trách**: Senior QA Engineer  
**Người cập nhật cuối**: Senior QA Engineer  

## 1. Tiền điều kiện (Preconditions)

- [ ] Backend Spring Boot đang chạy tại `http://localhost:8080`.
- [ ] Frontend Vite đang chạy tại `http://localhost:5173`.
- [ ] Tài khoản test chuyên dụng có vai trò `STAFF` (`E2E_STAFF_001`).
- [ ] Trình duyệt đã đăng nhập với phiên hợp lệ của `STAFF`.

## 2. Các bước kiểm thử trên trình duyệt (Browser Action Steps)

### Step 1 — Điều hướng trực tiếp tới trang Quản lý mẫu thông báo
```browser
goto http://localhost:5173/admin/notifications/templates
wait getByRole('alert')
```

### Step 2 — Điều hướng trực tiếp tới trang Nhật ký gửi thư
```browser
goto http://localhost:5173/admin/notifications/requests
wait getByRole('alert')
```

## 3. Kết quả kỳ vọng (Expected Assertions)

```browser
get url
# Expected: Contains /admin/notifications/templates or /admin/notifications/requests
find "Truy cập bị từ chối"
# Expected: One or more matches
find "Yêu cầu quyền Quản trị viên (Admin) hoặc Quản lý (Manager)"
# Expected: Exactly one match
console --errors
# Expected: No unhandled Javascript runtime crashes
```

## 4. Bằng chứng kiểm thử (Evidence)

- Đường dẫn ảnh chụp màn hình: `screenshots/E2E-007-001--access-denied.jpg`
- Lệnh lưu bằng chứng: `screenshot --full --path "ppswbs-spec/features/007-notification-management/e2e/screenshots/E2E-007-001--access-denied.jpg"`

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

