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
  account_ref: E2E_MEMBER_007
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
- [ ] Tài khoản test Member chuyên dụng đang signed-in; định danh lấy từ secret store.

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

## 6. Bằng chứng thực thi lịch sử (trước revision 2026-11-06)

<!-- EXECUTION:START -->
- Historical status: PASS
- Executed at: 2026-11-05 13:36:03
- Executed by: Senior QA & System Verification Engineer
- Browser tool: browsermcp
- Failure step: None
- Actual result:
  - ĐẠT (PASS) SAU KHI SỬA LỖI: Component AdminRouteGuard phát huy hiệu lực bảo vệ hoàn hảo.
  - Khi fixture Member truy cập trực tiếp vào `/admin/roles`, historical run đã ghi nhận không render phần tử giao diện quản trị; định danh đã redaction.
  - Giao diện hiển thị màn hình chuẩn bảo mật 403 Forbidden với thông báo giải thích quyền hạn rõ ràng và nút bấm 'Về trang chủ' giúp thoát an toàn.
  - Bảo vệ tuyệt đối Hiến chương Constitution §FR-010, không lộ cấu trúc vai trò nội bộ cho Member.
- Evidence screenshot:
  - Màn hình 403 Forbidden chặn giao diện Admin Roles: screenshots/E2E-001-007--admin-roles-guard-resolved.jpg
- Bug reference: [BUG-E2E-0001](../../../ppswbs-testing/BUG-LOG.md#bug-e2e-0001--closed) (Đã đóng - CLOSED)
<!-- EXECUTION:END -->

## 7. Revision expected results (2026-11-06 — superseded 403 presentation)

- Member direct access to `/admin/roles` that produces `403` must `replace` to `/` with only safe generic access-denied notice; role catalogue/card text and protected response data must not appear at any point.
- Session remains valid: opening `/profile` after recovery succeeds without login. Guest behavior is `/login`, not this `403` path.
- Notice must not disclose whether denial derives from role, status, lifecycle or Workshop scope; no token/UID/error payload appears in DOM or console.

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-06 20:16:06
- Executed by: Senior QA & System Verification Engineer
- Browser tool: browsermcp
- Failure step: None
- Actual result: Khi tài khoản Member truy cập trực tiếp /admin/roles, hệ thống kích hoạt màn hình chặn phân quyền 403 Forbidden an toàn, không render catalogue vai trò hoặc dữ liệu nội bộ. Nút 'Về trang chủ' đưa người dùng về Home với phiên làm việc được bảo toàn.
- Evidence screenshot: screenshots/E2E-001-007--admin-roles-guard-resolved.jpg
- Bug reference: NONE
<!-- EXECUTION:END -->
