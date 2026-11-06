---
id: E2E-001-013
feature: 001-account-management
test_level: e2e
test_case_type: A
source_ref: spec.md#FR-003,spec.md#FR-041,design/workflows.md#WF-11
target_url: http://localhost:5173/profile
actor_role: Guest
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
test_data:
  fixture: guest-route-guard
  account_ref: NONE
  isolation_key: E2E-001-013-20261106
cleanup:
  required: false
  method: none-client-only
---

# E2E-001-013: Guard route private của Guest, canonical login và replace history

**Ngày tạo**: 2026-11-06
**Ngày cập nhật**: 2026-11-06
**Người phụ trách**: Senior QA & System Verification Engineer

## 1. Tiền điều kiện

- [ ] Frontend chạy tại `http://localhost:5173`; trình duyệt không có Firebase session.
- [ ] Có fixture Member riêng cho bước đăng nhập; secret chỉ lấy từ secret store.
- [ ] Browser MCP có thể quan sát URL, DOM và thao tác Back.

## 2. Rủi ro cần phủ

- Guest thấy PII/private form trong lúc guard chưa resolve.
- Alias `/sign-in` giữ URL cũ, hoặc Back quay lại trang private sau redirect.
- Login dùng `returnTo` thay vì luôn về Home.

## 3. Các bước và expected result tại từng bước

### Step 1 — Guest mở thẳng Profile

```browser
goto http://localhost:5173/profile
wait getByRole('heading', { name: 'Đăng nhập hệ thống' })
get url
```

**Expected:** URL chính xác là `http://localhost:5173/login`; không có heading `Hồ sơ cá nhân`, field họ tên/email, sổ địa chỉ, hoặc bất kỳ giá trị Profile nào trong DOM.

### Step 2 — Kiểm tra alias đăng nhập

```browser
goto http://localhost:5173/sign-in
wait getByRole('heading', { name: 'Đăng nhập hệ thống' })
get url
```

**Expected:** URL chính xác là `http://localhost:5173/login`; `/sign-in` không được giữ làm URL cuối.

### Step 3 — Đăng nhập hợp lệ từ guard

```browser
fill getByLabel('Địa chỉ email') "<member-email-from-secret-store>"
fill getByLabel('Mật khẩu') "<member-password-from-secret-store>"
click getByRole('button', { name: 'Đăng nhập' })
wait getByRole('main')
get url
```

**Expected:** URL chính xác là `http://localhost:5173/`; Home hiển thị nội dung công khai/đã đăng nhập theo UI hiện hành, không tự quay về `/profile`.

### Step 4 — Kiểm tra replace history

```browser
goBack
get url
```

**Expected:** History Back không hiển thị `/profile` private trước login. Nếu browser không còn history trong app, kết quả vẫn không được là `/profile`; ghi URL quan sát được vào evidence.

## 4. Evidence bắt buộc

- Screenshot sau Step 1: URL `/login`, không có PII Profile.
- Screenshot sau Step 3: URL `/`.
- DOM snapshot/URL sau Step 4.

## 5. Kết quả thực thi

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
