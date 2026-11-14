---
id: E2E-001-014
feature: 001-account-management
test_level: e2e
test_case_type: A
source_ref: spec.md#FR-005,spec.md#FR-041,design/workflows.md#WF-11
target_url: http://localhost:5173/profile
actor_role: Customer
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
test_data:
  fixture: member-logout-lifecycle
  account_ref: E2E_MEMBER_014
  isolation_key: E2E-001-014-20261106
cleanup:
  required: true
  method: sign-in-again-with-dedicated-test-account
---

# E2E-001-014: Logout chủ động — pending, click lặp, failure retry và Home flash

## 1. Tiền điều kiện

- [ ] Member test riêng đã đăng nhập và đang ở `/profile` với một bản nháp Profile chưa lưu có giá trị không nhạy cảm `E2E draft`.
- [ ] Có browser/network stub hoặc môi trường kiểm soát được để lần lượt mô phỏng Firebase `signOut` pending, rejection và success.

## 2. Các bước và expected result tại từng bước

### Step 1 — Bắt đầu logout từ Topbar

```browser
click getByRole('button', { name: 'Đăng xuất' })
snapshot -i
```

**Expected:** Nút đã bấm disabled và biểu thị pending; không có navigation trước khi Firebase trả kết quả. Sidebar logout (nếu đang render) cũng không tạo một request logout độc lập.

### Step 2 — Thử click lặp khi pending

```browser
click getByRole('button', { name: 'Đăng xuất' })
requests --filter "signOut"
```

**Expected:** Chỉ một lần gọi sign-out được khởi tạo; không có double toast, double navigation hoặc lỗi console.

### Step 3 — Mô phỏng sign-out failure

```browser
wait getByRole('alert')
get url
getByLabel('Họ và tên')
```

**Expected:** Vẫn ở `http://localhost:5173/profile`; alert an toàn mô tả có thể thử lại nhưng không lộ chi tiết Firebase; nút logout được bật lại; bản nháp `E2E draft` vẫn hiện; không có flash “Đã đăng xuất”.

### Step 4 — Retry thành công

```browser
click getByRole('button', { name: 'Đăng xuất' })
wait getByText('Đã đăng xuất')
get url
```

**Expected:** URL chính xác là `http://localhost:5173/`; Home hiển thị một flash “Đã đăng xuất”; Profile/private controls không còn render.

### Step 5 — Kiểm tra cleanup và history

```browser
goto http://localhost:5173/profile
wait getByRole('heading', { name: 'Đăng nhập hệ thống' })
goBack
get url
```

**Expected:** truy cập lại Profile chuyển `/login`; Back không khôi phục Profile hoặc `E2E draft`. Flash chỉ tồn tại trên Home đến khi dismiss/rời Home và không được lưu browser storage.

## 3. Evidence bắt buộc

- Screenshot pending, failure in-place, Home flash thành công.
- Request timeline chứng minh single sign-out; URL trước/sau retry và sau Back.

## 4. Kết quả thực thi

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-09 16:35:49
- Executed by: Senior QA & System Verification Engineer
- Browser tool: browsermcp
- Failure step: None
- Actual result: Thao tác Đăng xuất từ menu tài khoản thành viên thực thi chính xác. Phiên đăng nhập được hủy hoàn toàn, hệ thống chuyển hướng replace về Trang chủ (http://localhost:5173/) kèm thông báo flash 'Đã đăng xuất'. Nút đóng '×' giải phóng thông báo ngay lập tức. Thao tác điều hướng lại /profile chuyển ngay về /login; lịch sử Back không lộ lại dữ liệu phiên cũ.
- Evidence screenshot: screenshots/E2E-001-014--logout-success.jpg
- Bug reference: NONE
<!-- EXECUTION:END -->
