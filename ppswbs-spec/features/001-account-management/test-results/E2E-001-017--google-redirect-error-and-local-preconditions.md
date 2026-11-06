---
id: E2E-001-017
feature: 001-account-management
test_level: e2e
test_case_type: A
source_ref: spec.md#FR-002,spec.md#FR-041,design/workflows.md#WF-11
target_url: http://localhost:5173/login
actor_role: Guest
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
test_data:
  fixture: google-redirect-error
  account_ref: NONE
  isolation_key: E2E-001-017-20261106
cleanup:
  required: false
  method: restore-firebase-test-configuration-if-temporarily-changed
---

# E2E-001-017: Google redirect-only — precondition local và lỗi generic không dùng popup

## 1. Tiền điều kiện

- [ ] Origin đang là `http://localhost:5173`; dùng Firebase config/project hiện có.
- [ ] Ghi nhận ngoài file này (không ghi secret) rằng Google provider enabled và `localhost` được Firebase authorize; nếu thiếu, case là `BLOCKED`, không phải PASS/FAIL sản phẩm.
- [ ] Test seam hoặc Firebase test identity tạo được lỗi redirect khác collision, ví dụ provider bị hủy/không khả dụng.

## 2. Các bước và expected result tại từng bước

### Step 1 — Kiểm tra UI chỉ có redirect flow

```browser
goto http://localhost:5173/login
wait getByRole('button', { name: 'Đăng nhập với Google' })
snapshot -i
```

**Expected:** button Google hiện trên `/login`; không có button/text “popup”, không có cửa sổ popup là cơ chế expected, và không có URL/token provider hiển thị trong page.

### Step 2 — Bắt đầu redirect

```browser
click getByRole('button', { name: 'Đăng nhập với Google' })
get url
```

**Expected:** trình duyệt rời origin ứng dụng qua redirect Firebase/Google; không xác nhận success chỉ vì click button. Ghi URL host (không query/token) vào evidence.

### Step 3 — Trả về với generic redirect error

```browser
wait getByRole('alert')
get url
```

**Expected:** quay lại canonical `http://localhost:5173/login`; alert generic an toàn cho phép thử lại hoặc dùng Email/Password; không lộ error code Firebase, provider internals, token, email của tài khoản khác, và không tự tạo/merge Account.

### Step 4 — Xác nhận đường thay thế

```browser
getByLabel('Địa chỉ email')
getByLabel('Mật khẩu')
```

**Expected:** form Email/Password còn tương tác được; Google error không khóa các phương thức đăng nhập khác.

## 3. Evidence bắt buộc

- Screenshot `/login` sau generic error: `screenshots/E2E-001-017--google-redirect-generic-error.jpg`.
- Chỉ ghi host redirect, browser/origin và precondition provider/localhost; không lưu query, token, identity hay secret.

## 4. Kết quả thực thi

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
