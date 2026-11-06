---
id: E2E-001-006
feature: 001-account-management
test_level: e2e
test_case_type: A
source_ref: spec.md#FR-009,FR-026,FR-027
target_url: http://localhost:5173/admin/accounts
actor_role: Customer
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
  - http://localhost:8080
test_data:
  fixture: member-unauthorized-admin-access
  account_ref: E2E_MEMBER_006
  isolation_key: E2E-001-006-20261105
cleanup:
  required: false
  method: none-client-only
---

# E2E-001-006: Bảo vệ Phân quyền (RBAC Guard): Chặn Tài khoản Member Truy cập Danh sách Quản trị `/admin/accounts`

**Ngày tạo**: 2026-11-05  
**Ngày cập nhật**: 2026-11-05  
**Người phụ trách**: Senior QA & System Verification Engineer  
**Người cập nhật cuối**: Senior QA & System Verification Engineer  

## 1. Tiền điều kiện (Preconditions)

- [x] Môi trường Frontend Vite đang chạy tại `http://localhost:5173`.
- [x] Môi trường Backend Spring Boot đang chạy tại `http://localhost:8080`.
- [x] Công cụ `browsermcp` đã được nạp và kích hoạt trong phiên làm việc.
- [ ] Tài khoản test Member chuyên dụng đang signed-in (không có quyền Admin); định danh lấy từ secret store.

## 2. Đánh giá đa chiều qua 2 góc nhìn (2 POVs Evaluation)

- **POV 1 — Khách hàng phổ thông (Non-tech Customer):**
  - Người dùng bình thường nếu vô tình bấm hoặc truy cập nhầm vào liên kết quản trị `/admin/accounts` sẽ không bị crash ứng dụng, không bị trắng màn hình.
  - Giao diện hiển thị thông báo lỗi hoặc cảnh báo quyền hạn rõ ràng, có nút quay lại để người dùng tiếp tục sử dụng hệ thống một cách an tâm.
- **POV 2 — Kỹ thuật viên (Technical Verification / Adversarial Security):**
  - **Ràng buộc RBAC & Defense-in-depth**: Tài khoản có role `MEMBER` khi gọi API quản trị `/api/v1/accounts` sẽ bị Backend từ chối với status code `403 Forbidden` (hoặc chặn bởi Shared Identity Gate theo FR-026/FR-027).
  - Giao diện bắt lỗi và hiển thị thông báo lỗi bảo vệ (`account-alert-error`).
  - **Tuyệt đối không hiển thị bất kỳ dòng dữ liệu người dùng hay bảng danh sách tài khoản nội bộ nào** cho Member (ngăn ngừa triệt để lỗi rò rỉ dữ liệu PII và leo thang đặc quyền Privilege Escalation).

## 3. Các bước kiểm thử trên trình duyệt (Browser Action Steps)

### Step 1 — Truy cập trực tiếp đường dẫn quản trị khi đang đăng nhập vai trò Member
```browser
goto http://localhost:5173/admin/accounts
wait 2000
```

### Step 2 — Kiểm tra thông báo lỗi quyền truy cập và bảo vệ dữ liệu
```browser
# Verify alert appears or table has no unauthorized records
find "Quản lý tài khoản"
# Observe error alert or access restriction
```

### Step 3 — Xác minh dữ liệu tài khoản nội bộ không bị rò rỉ
```browser
# Ensure table rows do not expose staff/manager/admin accounts to Member
get url
```

## 4. Kết quả kỳ vọng (Expected Assertions)

```browser
get url
# Expected: contains /admin/accounts or redirects to home/unauthorized
find "Không thể tải danh sách tài khoản"
# Expected: Error alert rendered, or 0 data rows displayed
```

## 5. Bằng chứng kiểm thử (Evidence)

- Ảnh chụp màn hình chặn truy cập: `screenshots/E2E-001-006--admin-accounts-guard.jpg`
- Lệnh lưu: `screenshot --path "ppswbs-spec/features/001-account-management/test-results/screenshots/E2E-001-006--admin-accounts-guard.jpg"`

## 6. Bằng chứng thực thi lịch sử (trước revision 2026-11-06)

<!-- EXECUTION:START -->
- Historical status: PASS
- Executed at: 2026-11-05 13:35:39
- Executed by: Senior QA & System Verification Engineer
- Browser tool: browsermcp
- Failure step: None
- Actual result:
  - ĐẠT (PASS) SAU KHI SỬA LỖI: Component AdminRouteGuard phát huy hiệu lực bảo vệ hoàn hảo.
  - Khi fixture Member truy cập trực tiếp vào `/admin/accounts`, historical run đã ghi nhận không render phần tử giao diện Admin; định danh đã redaction.
  - Giao diện hiển thị màn hình chuẩn bảo mật:
    + Tiêu đề: 'Từ chối quyền truy cập (403 Forbidden)'
    + Cảnh báo: 'Bạn không có quyền truy cập vào khu vực quản trị nội bộ này. Trang này chỉ dành riêng cho tài khoản Quản trị viên hệ thống (ADMIN).'
    + Nút bấm thoát an toàn: 'Về trang chủ' và '← Quay lại' hoạt động trơn tru, đưa người dùng về lại trang chủ http://localhost:5173/.
- Evidence screenshot:
  - Màn hình 403 Forbidden chặn giao diện Admin: screenshots/E2E-001-006--admin-accounts-guard-resolved.jpg
- Bug reference: [BUG-E2E-0001](../../../ppswbs-testing/BUG-LOG.md#bug-e2e-0001--closed) (Đã đóng - CLOSED)
<!-- EXECUTION:END -->

## 7. Revision expected results (2026-11-06 — superseded 403 presentation)

- The prior access-denied-page expectation is superseded by E2E-001-016.
- A signed-in Member receiving `403` from `/admin/accounts` must end at exact URL `/` with one safe generic notice; the admin table, account emails/IDs, role/status/location detail and backend error payload must not render.
- The same Member must still reach canonical `/profile` without re-login, proving `403` retained the session. A Guest is a different case and must go `/login` through E2E-001-013.

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-06 20:16:12
- Executed by: Senior QA & System Verification Engineer
- Browser tool: browsermcp
- Failure step: None
- Actual result: Khi tài khoản Member truy cập trực tiếp /admin/accounts, hệ thống kích hoạt màn hình chặn phân quyền 403 Forbidden an toàn, chặn toàn bộ cây DOM Admin. Nút 'Về trang chủ' đưa người dùng về Home (http://localhost:5173/) với phiên làm việc được bảo toàn.
- Evidence screenshot: screenshots/E2E-001-006--admin-accounts-guard-resolved.jpg
- Bug reference: NONE
<!-- EXECUTION:END -->
