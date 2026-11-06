---
id: E2E-001-016
feature: 001-account-management
test_level: integration
test_case_type: A
source_ref: spec.md#FR-041,design/workflows.md#WF-06,design/workflows.md#WF-11
target_url: http://localhost:5173/admin/accounts
actor_role: Customer
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
  - http://localhost:8080/v3/api-docs
test_data:
  fixture: member-forbidden-route
  account_ref: E2E_MEMBER_016
  isolation_key: E2E-001-016-20261106
cleanup:
  required: true
  method: sign-out-dedicated-test-account
---

# E2E-001-016: 403 — Home recovery an toàn nhưng không mất session

## 1. Tiền điều kiện

- [ ] Member test riêng đang signed-in; backend/stub trả `403` cho request admin hợp lệ về credential.

## 2. Các bước và expected result tại từng bước

### Step 1 — Member mở direct admin route

```browser
goto http://localhost:5173/admin/accounts
wait getByRole('main')
get url
```

**Expected:** URL chính xác là `http://localhost:5173/`; không render table admin, email, Account ID hay lifecycle của tài khoản nội bộ.

### Step 2 — Kiểm tra safe notice

```browser
find "Không có quyền truy cập"
console --errors
```

**Expected:** có một notice an toàn có thể hiểu là không được phép truy cập; không chứa `MEMBER`, `ADMIN`, `SUSPENDED`, Workshop Location, HTTP payload hoặc correlation/token detail; console không có uncaught error.

### Step 3 — Chứng minh session còn hiệu lực

```browser
click getByRole('link', { name: 'Hồ sơ' })
wait getByRole('heading', { name: 'Hồ sơ cá nhân' })
get url
```

**Expected:** URL `/profile` (hoặc canonical Profile URL đã khai báo); không bị đưa `/login`; tên/profile của chính Member chỉ hiển thị sau khi vào route được phép.

## 3. Evidence bắt buộc

- Screenshot Home notice và Profile sau Step 3: `screenshots/E2E-001-016--forbidden-retained-session.jpg`.
- DOM snapshot chứng minh không có admin table/role/lifecycle data và URL của Step 1, Step 3.

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
