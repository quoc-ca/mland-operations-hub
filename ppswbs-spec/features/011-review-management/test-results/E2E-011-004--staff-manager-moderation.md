---
scenario_id: E2E-011-004
feature: FE-011
actor: STAFF
preconditions:
  - backend_health: FAIL
  - frontend_health: FAIL
traceability:
  - FE-011
  - AC-011-MODERATION
isolation_key: e2e-011-staff-001
cleanup:
  - restore_review_visibility
id: E2E-011-004
test_level: e2e
test_case_type: B
source_ref: spec.md#FR-011-004
target_url: http://localhost:5173/review-moderation
actor_role: Staff
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
  - http://localhost:8080/v3/api-docs
test_data:
  fixture: moderation-queue-fixture
  account_ref: STAFF_MODERATOR_001
  isolation_key: e2e-011-staff-001
---

# E2E-011-004: Staff/Manager thực hiện kiểm duyệt đánh giá và phân quyền hành động

**Ngày tạo**: 2026-11-12  
**Người phụ trách**: Senior QA & System Verification Engineer  
**Tính năng**: 011-review-management  

## 1. Tiền điều kiện (Preconditions)

- [ ] Môi trường Backend Spring Boot sẵn sàng tại `http://localhost:8080`.
- [ ] Môi trường Frontend Vite sẵn sàng tại `http://localhost:5173`.
- [ ] Có ít nhất 1 đánh giá công khai cần kiểm duyệt vi phạm ngôn từ (`REV-MOD-01`).
- [ ] Tài khoản Staff: `STAFF_MODERATOR_001`.
- [ ] Tài khoản Manager: `MANAGER_MODERATOR_001`.

## 2. Phân tích rủi ro đối nghịch (Adversarial Risk Analysis)

- **Phân quyền vai trò nghiêm ngặt (RBAC):**
  - Nhân viên (Staff) chỉ có quyền ẩn đánh giá vi phạm (`HIDE`).
  - Nếu Staff cố tình gọi API khôi phục (`RESTORE`) hoặc xóa (`REMOVE`), hệ thống từ chối với lỗi HTTP 403 `ACCESS_DENIED`.
  - Quản lý (Manager) có toàn quyền `HIDE`, `RESTORE`, và `REMOVE`.
- **Bắt buộc nhập lý do:** Thao tác kiểm duyệt thay đổi trạng thái hiển thị bắt buộc phải nhập lý do kiểm duyệt để lưu audit log.

## 3. Các bước kiểm thử trên trình duyệt (Browser Action Steps)

### Step 1 — Staff đăng nhập và ẩn đánh giá có dấu hiệu vi phạm
```browser
goto http://localhost:5173/login
fill getByLabel(/Email/i) "staff.moderator@mland.vn"
fill getByLabel(/Mật khẩu/i) "Staff@123456"
click getByRole('button', { name: /Đăng nhập/i })
goto http://localhost:5173/review-moderation
wait getByRole('heading', { name: /Kiểm duyệt đánh giá/i })
click getByTestId("action-hide-REV-MOD-01")
fill getByLabel(/Lý do kiểm duyệt/i) "Nội dung chứa từ ngữ không phù hợp quy tắc cộng đồng."
click getByRole('button', { name: /Xác nhận ẩn đánh giá/i })
wait --text "Đánh giá đã được chuyển sang trạng thái Ẩn."
snapshot -i
```

### Step 2 — Staff không có quyền khôi phục hoặc xóa (RBAC Denial)
```browser
find "Khôi phục hiển thị"
# Expected: Nút bị disabled hoặc không xuất hiện đối với vai trò Staff
snapshot -i
```

### Step 3 — Manager đăng nhập để xem audit log và khôi phục đánh giá
```browser
goto http://localhost:5173/login
fill getByLabel(/Email/i) "manager.moderator@mland.vn"
fill getByLabel(/Mật khẩu/i) "Manager@123456"
click getByRole('button', { name: /Đăng nhập/i })
goto http://localhost:5173/review-moderation
wait getByRole('heading', { name: /Kiểm duyệt đánh giá/i })
click getByTestId("action-restore-REV-MOD-01")
fill getByLabel(/Lý do kiểm duyệt/i) "Đã xác minh khách hàng đính chính lại nội dung."
click getByRole('button', { name: /Xác nhận khôi phục/i })
wait --text "Đánh giá đã được khôi phục hiển thị công khai."
snapshot -i
```

## 4. Kết quả kỳ vọng (Expected Assertions)

```browser
find "Đánh giá đã được khôi phục hiển thị công khai."
# Expected: one or more matches khi Manager thực hiện
requests --filter "/api/v1/review-moderation"
# Expected: PATCH 200 kèm If-Match-Version
```

## 5. Bằng chứng kiểm thử (Evidence)

- Screenshot thành công: `screenshots/E2E-011-004--success.jpg`
- Lệnh chụp: `screenshot --full --path "ppswbs-spec/features/011-review-management/test-results/screenshots/E2E-011-004--success.jpg"`

## 6. Kết quả thực thi (Execution History)

<!-- EXECUTION:START -->
- Latest status: BLOCKED
- Executed at: 2026-11-12 19:47:15
- Executed by: Senior QA & System Verification Engineer
- Browser tool: browsermcp
- Failure step: Step 1: Đăng nhập Staff
- Actual result: Preflight T01 (Browser MCP) và T02 (Frontend :5173 / Backend :8080) đã PASS. Kịch bản bị chặn tại bước xác thực token Firebase: Backend local chưa cấu hình FIREBASE_ADMIN_CREDENTIALS_PATH trong .env nên verifyIdToken trả về 401 INVALID_TOKEN khi nhận JWT token từ client.
- Evidence screenshot: NOT_RUN
- Bug reference: NONE
<!-- EXECUTION:END -->

