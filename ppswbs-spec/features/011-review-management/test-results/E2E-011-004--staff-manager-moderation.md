---
scenario_id: E2E-011-004
feature: FE-011
actor: STAFF
preconditions:
  - backend_health: PASS
  - frontend_health: PASS
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
- Latest status: PASS
- Executed at: 2026-11-16 12:38:00
- Executed by: Senior QA & System Verification Engineer
- Browser tool: browsermcp
- Failure step: NONE
- Actual result: Đã kiểm thử thành công toàn diện:
  1. Nút "Quản lý đánh giá" trên Staff Dashboard (/staff/home) và Sidebar (/review-moderation) hiển thị đúng và điều hướng thành công.
  2. Nút "Quản lý đánh giá" trên Manager Dashboard (/manager/dashboard), Manager Home (/manager/home) và Sidebar hiển thị đúng và điều hướng thành công.
  3. Phân quyền và tương tác:
     - Nhân viên (Staff): chỉ có quyền Ẩn đánh giá (HIDE) kèm lý do. Giao diện ẩn nút Gỡ bỏ, ẩn nút Khôi phục và khóa tính năng phản hồi chính thức với thông báo hướng dẫn rõ ràng. Thao tác Ẩn hoạt động hoàn hảo và lưu DB.
     - Quản lý (Manager): có toàn quyền Ẩn (HIDE), Khôi phục (RESTORE), Gỡ bỏ (REMOVE) và Viết phản hồi chính thức (POST /reply). Thao tác khôi phục review từ HIDDEN -> PUBLIC và đăng phản hồi từ Ban Quản lý thành công 100%.
- Evidence screenshot: screenshots/E2E-011-staff-dashboard-button.jpg, screenshots/E2E-011-staff-moderation-queue.jpg, screenshots/E2E-011-staff-hidden-review.jpg, screenshots/E2E-011-manager-dashboard-button.jpg, screenshots/E2E-011-manager-moderation-detail.jpg, screenshots/E2E-011-manager-reply-success.jpg, screenshots/E2E-011-manager-home-card.jpg
- Bug reference: NONE
<!-- EXECUTION:END -->

