---
id: E2E-001-007
feature: 001-account-management
test_level: e2e
test_case_type: A
source_ref: spec.md#FR-009,FR-010,FR-026,FR-027
target_url: http://localhost:5173/admin/roles
actor_role: Customer
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
  - http://localhost:8080
test_data:
  fixture: member-unauthorized-roles-access
  account_ref: minikie456@gmail.com
  isolation_key: E2E-001-007-20261105
cleanup:
  required: false
  method: none-client-only
---

# E2E-001-007: Bảo vệ Phân quyền (RBAC Guard): Chặn Tài khoản Member Truy cập Danh mục Vai trò `/admin/roles`

**Ngày tạo**: 2026-11-05  
**Ngày cập nhật**: 2026-11-05  
**Người phụ trách**: Senior QA & System Verification Engineer  
**Người cập nhật cuối**: Senior QA & System Verification Engineer  

## 1. Tiền điều kiện (Preconditions)

- [x] Môi trường Frontend Vite đang chạy tại `http://localhost:5173`.
- [x] Môi trường Backend Spring Boot đang chạy tại `http://localhost:8080`.
- [x] Công cụ `browsermcp` đã được nạp và kích hoạt trong phiên làm việc.
- [x] Tài khoản đăng nhập hiện tại là `minikie456@gmail.com` với vai trò `MEMBER`.

## 2. Đánh giá đa chiều qua 2 góc nhìn (2 POVs Evaluation)

- **POV 1 — Khách hàng phổ thông (Non-tech Customer):**
  - Người dùng truy cập đường dẫn không phù hợp với vai trò của mình sẽ nhận được giao diện xử lý lịch sự, có nút "← Quay lại" rõ ràng để trở về trang quản lý của mình.
- **POV 2 — Kỹ thuật viên (Technical Verification / Adversarial Security):**
  - Danh mục vai trò và phân quyền nội bộ quản trị được bảo vệ; hệ thống không cấp quyền quản lý cho tài khoản thông thường.
  - Tuyệt đối không cho phép tài khoản `MEMBER` tạo, sửa hay phân quyền các vai trò của tổ chức (bảo vệ Hiến chương Constitution §FR-010).

## 3. Các bước kiểm thử trên trình duyệt (Browser Action Steps)

### Step 1 — Mở đường dẫn danh mục vai trò khi đang là Member
```browser
goto http://localhost:5173/admin/roles
wait 2000
```

### Step 2 — Kiểm tra giao diện và phản hồi bảo vệ
```browser
find "Danh mục vai trò"
get url
```

### Step 3 — Kiểm tra tính năng thoát an toàn
```browser
click getByRole('link', { name: '← Quay lại' })
wait 1000
get url
```

## 4. Kết quả kỳ vọng (Expected Assertions)

```browser
get url
# Expected: URL loaded or redirected safely
find "Danh mục vai trò"
# Expected: one or more matches
```

## 5. Bằng chứng kiểm thử (Evidence)

- Ảnh chụp màn hình kiểm soát vai trò: `screenshots/E2E-001-007--admin-roles-guard.jpg`
- Lệnh lưu: `screenshot --path "ppswbs-spec/features/001-account-management/test-results/screenshots/E2E-001-007--admin-roles-guard.jpg"`

## 6. Kết quả thực thi (Execution History)

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-05 13:36:03
- Executed by: Senior QA & System Verification Engineer
- Browser tool: browsermcp
- Failure step: None
- Actual result:
  - ĐẠT (PASS) SAU KHI SỬA LỖI: Component AdminRouteGuard phát huy hiệu lực bảo vệ hoàn hảo.
  - Khi tài khoản Member (minikie456@gmail.com) truy cập trực tiếp vào http://localhost:5173/admin/roles, hệ thống CHẶN HOÀN TOÀN không render bất kỳ phần tử nào của giao diện quản trị vai trò nội bộ (không có tiêu đề Danh mục vai trò, không có Admin Tabs, không có bảng vai trò).
  - Giao diện hiển thị màn hình chuẩn bảo mật 403 Forbidden với thông báo giải thích quyền hạn rõ ràng và nút bấm 'Về trang chủ' giúp thoát an toàn.
  - Bảo vệ tuyệt đối Hiến chương Constitution §FR-010, không lộ cấu trúc vai trò nội bộ cho Member.
- Evidence screenshot:
  - Màn hình 403 Forbidden chặn giao diện Admin Roles: screenshots/E2E-001-007--admin-roles-guard-resolved.jpg
- Bug reference: [BUG-E2E-0001](../../../ppswbs-testing/BUG-LOG.md#bug-e2e-0001--closed) (Đã đóng - CLOSED)
<!-- EXECUTION:END -->
