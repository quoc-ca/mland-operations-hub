---
id: E2E-007-002
feature: 007-notification-management
test_level: e2e
test_case_type: A
source_ref: spec.md#FR-027
target_url: http://localhost:5173/admin/notifications/requests
actor_role: Guest
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
test_data:
  fixture: none-guest
  account_ref: GUEST
  isolation_key: E2E-007-002-20261109
cleanup:
  required: false
  method: none
---

# E2E-007-002: Unauthenticated guest redirected to login when accessing notification routes

**Ngày tạo**: 2026-11-09  
**Ngày cập nhật**: 2026-11-09  
**Người phụ trách**: Senior QA Engineer  
**Người cập nhật cuối**: Senior QA Engineer  

## 1. Tiền điều kiện (Preconditions)

- [ ] Frontend Vite đang chạy tại `http://localhost:5173`.
- [ ] Trình duyệt ở trạng thái Guest (chưa đăng nhập, không có session token).

## 2. Các bước kiểm thử trên trình duyệt (Browser Action Steps)

### Step 1 — Điều hướng tới trang Nhật ký gửi thư khi chưa đăng nhập
```browser
goto http://localhost:5173/admin/notifications/requests
wait getByRole('heading', { name: 'Đăng nhập' })
```

## 3. Kết quả kỳ vọng (Expected Assertions)

```browser
get url
# Expected: http://localhost:5173/login?redirect=%2Fadmin%2Fnotifications%2Frequests
find "Đăng nhập"
# Expected: Exactly one match for login form
console --errors
# Expected: No uncaught crashes
```

## 4. Bằng chứng kiểm thử (Evidence)

- Đường dẫn ảnh chụp màn hình: `screenshots/E2E-007-002--redirect-to-login.jpg`
- Lệnh lưu bằng chứng: `screenshot --full --path "ppswbs-spec/features/007-notification-management/e2e/screenshots/E2E-007-002--redirect-to-login.jpg"`

## 5. Kết quả thực thi (Execution History)

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-09 17:06:09
- Executed by: Senior QA & System Verification Engineer
- Browser tool: browsermcp
- Failure step: None
- Actual result: Trình duyệt chuyển hướng chính xác về /login?redirect=%2Fadmin%2Fnotifications%2Frequests khi truy cập route thông báo ở trạng thái Guest; form đăng nhập hiển thị đầy đủ, không gây crash ứng dụng.
- Evidence screenshot: screenshots/E2E-007-002--redirect-to-login.jpg
- Bug reference: NONE
<!-- EXECUTION:END -->

