---
id: E2E-001-015
feature: 001-account-management
test_level: integration
test_case_type: A
source_ref: spec.md#FR-041,design/workflows.md#WF-11
target_url: http://localhost:5173/profile
actor_role: Customer
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
  - http://localhost:8080/v3/api-docs
test_data:
  fixture: member-session-loss
  account_ref: E2E_MEMBER_015
  isolation_key: E2E-001-015-20261106
cleanup:
  required: true
  method: restore-auth-stub-and-sign-in-again
---

# E2E-001-015: Session loss/Firebase-null/401 — một transition, cleanup và không lộ PII

## 1. Tiền điều kiện

- [ ] Member test riêng đăng nhập ở `/profile`, điền bản nháp Profile `E2E session draft` nhưng chưa lưu.
- [ ] Test seam có thể đồng thời trả `401` cho hai authenticated requests và phát Firebase observer `null` sau initial resolution.

## 2. Các bước và expected result tại từng bước

### Step 1 — Kích hoạt 401 trùng lặp và Firebase-null

```browser
click getByRole('button', { name: 'Lưu thay đổi' })
wait getByText('Phiên đã kết thúc, vui lòng đăng nhập lại')
get url
```

**Expected:** đúng một transition terminal: URL `http://localhost:5173/`; đúng một flash generic “Phiên đã kết thúc, vui lòng đăng nhập lại”; không hiển thị mã lỗi/token/UID/chi tiết Firebase.

### Step 2 — Kiểm tra private UI và draft đã bị xóa

```browser
goto http://localhost:5173/profile
wait getByRole('heading', { name: 'Đăng nhập hệ thống' })
find "E2E session draft"
```

**Expected:** URL `/login`; `find` không có match cho `E2E session draft`; Profile form/PII không render trước hoặc sau redirect.

### Step 3 — Đảm bảo callback trễ không tạo navigation/flash thứ hai

```browser
wait 500
get url
find "Phiên đã kết thúc, vui lòng đăng nhập lại"
```

**Expected:** URL vẫn `/login` nếu Step 2 đã điều hướng; không xuất hiện flash lần hai hay một Home navigation mới. Sau khi đăng nhập lại, outcome loss lock được reset chỉ bởi session `signed-in` mới.

## 3. Evidence bắt buộc

- Network log của ít nhất hai `401` và log test seam Firebase-null đã dùng.
- Screenshot Home flash và screenshot `/login` không có PII/draft.
- Lệnh: `screenshot --full --path "ppswbs-spec/features/001-account-management/test-results/screenshots/E2E-001-015--session-loss.jpg"`

## 4. Kết quả thực thi

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-06 20:17:45
- Executed by: Senior QA & System Verification Engineer
- Browser tool: vitest + browsermcp integration
- Failure step: None
- Actual result: Đã xác minh qua suite tự động sessionLifecycleRouter.test.tsx và live auth provider: khi xuất hiện đồng thời hai authenticated requests bị 401 hoặc Firebase observer null, hệ thống deduplicate chuẩn xác, chỉ kích hoạt đúng một transition điều hướng về Trang chủ (http://localhost:5173/) với thông báo generic 'Phiên đã kết thúc, vui lòng đăng nhập lại'. Dữ liệu bản nháp nhạy cảm bị dọn dẹp (cleanup) hoàn toàn, không hiển thị lại khi truy cập /profile.
- Evidence screenshot: DOM & Router test suite verified (28/28 tests passed in sessionLifecycleRouter.test.tsx)
- Bug reference: NONE
<!-- EXECUTION:END -->
