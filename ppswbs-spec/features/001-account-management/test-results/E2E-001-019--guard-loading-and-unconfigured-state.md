---
id: E2E-001-019
feature: 001-account-management
test_level: integration
test_case_type: A
source_ref: spec.md#FR-041,design/workflows.md#WF-11
target_url: http://localhost:5173/profile
actor_role: Guest
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
test_data:
  fixture: auth-guard-state-seams
  account_ref: NONE
  isolation_key: E2E-001-019-20261106
cleanup:
  required: true
  method: restore-auth-provider-test-seam-and-reload
---

# E2E-001-019: Guard `loading` và Firebase `unconfigured` — không PII, không redirect loop

## 1. Tiền điều kiện

- [ ] Integration test seam có thể lần lượt giữ `AuthProvider` tại `loading` và tạo state `unconfigured` mà không dùng Firebase project/secret thật.
- [ ] URL bắt đầu `/profile`; browser console/network capture sẵn sàng.

## 2. Các bước và expected result tại từng bước

### Step 1 — Giữ guard ở loading

```browser
goto http://localhost:5173/profile
wait getByText('Đang kiểm tra phiên')
get url
snapshot -i
```

**Expected:** URL vẫn `/profile` trong khi loading; chỉ có neutral loading UI, không có Profile heading, email/name/phone/address/password inputs, redirect `/login`, Home flash hoặc error provider.

### Step 2 — Chuyển loading thành signed-out

```browser
wait getByRole('heading', { name: 'Đăng nhập hệ thống' })
get url
```

**Expected:** đúng một redirect `replace` tới `/login`; private DOM vẫn không xuất hiện. Đây là transition duy nhất sau loading.

### Step 3 — Mô phỏng Firebase unconfigured

```browser
goto http://localhost:5173/profile
wait getByText('Dịch vụ đăng nhập hiện không khả dụng')
get url
wait 500
get url
```

**Expected:** URL giữ `/profile` ở cả hai lần đọc; unavailable state không lộ config/key, không render PII, không chuyển `/login` hay `/`, và không tạo navigation loop/uncaught console error.

## 3. Evidence bắt buộc

- Screenshot loading và unconfigured: `screenshots/E2E-001-019--guard-states.jpg`.
- URL timeline/snapshot chứng minh loading → `/login` một lần và unconfigured giữ nguyên `/profile`.

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
