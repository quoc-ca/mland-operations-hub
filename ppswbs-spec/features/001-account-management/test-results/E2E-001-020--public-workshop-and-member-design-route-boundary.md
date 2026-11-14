---
id: E2E-001-020
feature: 001-account-management
test_level: e2e
test_case_type: B
source_ref: spec.md#FR-041,spec-general.md#Shared-browser-session-and-navigation-lifecycle
target_url: http://localhost:5173/workshops
actor_role: Guest
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
test_data:
  fixture: public-vs-member-route-boundary
  account_ref: NONE
  published_package_id: <published-workshop-fixture-id>
  safe_booking_code: <non-sensitive-booking-fixture-code>
  isolation_key: E2E-001-020-20261106
cleanup:
  required: false
  method: none-client-only
---

# E2E-001-020: Route boundary — Workshop public, Design Request Member-only

## 1. Tiền điều kiện

- [ ] Guest browser session sạch.
- [ ] Có published Workshop fixture; không dùng booking/contact data thật.

## 2. Các bước và expected result tại từng bước

### Step 1 — Guest mở public Workshop list và booking route

```browser
goto http://localhost:5173/workshops
wait getByRole('main')
get url
goto http://localhost:5173/workshops/<published-package-id>/book
wait getByRole('main')
get url
```

**Expected:** cả hai URL Workshop vẫn nằm trên origin route đã yêu cầu, không bị chuyển `/login`; trang hiển thị public workshop/booking UI, không đòi Profile/address/Member history chỉ để xem/mở booking.

### Step 2 — Guest mở booking lookup/access/payment-return public routes

```browser
goto http://localhost:5173/workshop-booking/lookup
wait getByRole('main')
goto http://localhost:5173/workshop-booking/access
wait getByRole('main')
goto http://localhost:5173/workshop-booking/<safe-booking-code>/payment-return
wait getByRole('main')
```

**Expected:** không redirect `/login` chỉ vì Guest. Lookup/access/payment-return tự áp dụng contact-proof/Magic-Link/booking-code validation của Feature-003.1; case này không coi một booking hợp lệ là điều kiện cần.

### Step 3 — Guest mở Design Request mới

```browser
goto http://localhost:5173/design-requests/new
wait getByRole('heading', { name: 'Đăng nhập hệ thống' })
get url
```

**Expected:** exact URL `http://localhost:5173/login` với `replace`; workspace upload/form, existing request detail, and Member data are never rendered.

## 3. Evidence bắt buộc

- Screenshot Workshop booking public entry and `/login` after Design Request entry: `screenshots/E2E-001-020--route-boundary.jpg`.
- URL evidence for each Workshop public row and for `/design-requests/new`; do not capture contact proof, Magic Link query or booking PII.

## 4. Kết quả thực thi

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-09 16:32:12
- Executed by: Senior QA & System Verification Engineer
- Browser tool: browsermcp
- Failure step: None
- Actual result: Các route công khai của Workshop (/workshops, /workshop-booking/lookup, /workshop-booking/access) giữ nguyên trạng thái cho Guest truy cập tự do, không bị ép chuyển hướng đăng nhập. Ngược lại, route tạo Custom Design Request (/design-requests/new) được bảo vệ bằng Route Guard, lập tức chuyển hướng replace về /login.
- Evidence screenshot:
  - Workshop public: screenshots/E2E-001-020--workshops-public.jpg
  - Design request guard /login: screenshots/E2E-001-020--route-boundary.jpg
- Bug reference: NONE
<!-- EXECUTION:END -->
