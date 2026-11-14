---
id: E2E-007-012
feature: 007-notification-management
test_level: e2e
test_case_type: A
source_ref: spec.md#FR-001,spec.md#FR-002
target_url: http://localhost:5173/admin/notifications/templates/AUTH_EMAIL_VERIFICATION
actor_role: Manager
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
  - http://localhost:8080/v3/api-docs
test_data:
  fixture: manager-account-e2e
  account_ref: E2E_MGR_001
  isolation_key: E2E-007-012-20261109
cleanup:
  required: false
  method: none
---

# E2E-007-012: External template read-only protection against version draft creation

**Ngày tạo**: 2026-11-09  
**Ngày cập nhật**: 2026-11-09  
**Người phụ trách**: Senior QA & System Verification Engineer  
**Người cập nhật cuối**: Senior QA & System Verification Engineer  

## 1. Tiền điều kiện (Preconditions)

- [ ] Backend Spring Boot đang chạy tại `http://localhost:8080`.
- [ ] Frontend Vite đang chạy tại `http://localhost:5173`.
- [ ] Đăng nhập với tài khoản `MANAGER`.
- [ ] Template `AUTH_EMAIL_VERIFICATION` có trạng thái vòng đời `EXTERNAL` trong database.

## 2. Các bước kiểm thử trên trình duyệt (Browser Action Steps)

### Step 1 — Mở chi tiết mẫu thông báo EXTERNAL (AUTH_EMAIL_VERIFICATION)
```browser
goto http://localhost:5173/admin/notifications/templates/AUTH_EMAIL_VERIFICATION
wait getByRole('heading', { name: /Xác minh Email/i })
```

### Step 2 — Bấm nút Tạo bản nháp mới
```browser
click getByRole('button', { name: '+ Tạo bản nháp mới' })
wait getByRole('heading', { name: /Tạo bản nháp mới/i })
```

### Step 3 — Điền nội dung bản nháp và thực hiện Lưu bản nháp
```browser
fill getByLabel('Tiêu đề email') "[Mland] Xác minh tài khoản của bạn {{firebaseVerificationUrl}}"
fill getByLabel('Nội dung HTML') "<p>Nhấn vào <a href='{{firebaseVerificationUrl}}'>đây</a> để xác thực.</p>"
fill getByLabel('Nội dung Plain Text') "Nhấn link sau để xác minh: {{firebaseVerificationUrl}}"
click getByRole('button', { name: 'Lưu bản nháp' })
wait 1500
```

## 3. Kết quả kỳ vọng (Expected Assertions)

```browser
find "External templates are managed by an external provider and cannot have version drafts"
# Expected: Error alert rendered showing backend rejection
requests --filter "/api/v1/notification-templates/AUTH_EMAIL_VERIFICATION/versions"
# Expected: POST 400 with code CANNOT_EDIT_EXTERNAL_TEMPLATE
console --errors
# Expected: No uncaught Javascript crashes
```

## 4. Bằng chứng kiểm thử (Evidence)

- Đường dẫn ảnh chụp màn hình: `screenshots/E2E-007-012--external-template-rejected.jpg`
- Lệnh lưu bằng chứng: `screenshot --full --path "ppswbs-spec/features/007-notification-management/test-results/e2e/screenshots/E2E-007-012--external-template-rejected.jpg"`

## 5. Kết quả thực thi (Execution History)

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-14 04:45:20
- Executed by: Senior QA & System Verification Engineer
- Browser tool: browsermcp
- Failure step: None
- Actual result: Trình duyệt mở mẫu thông báo EXTERNAL (AUTH_EMAIL_VERIFICATION), điền thông tin và cố gắng bấm "Lưu bản nháp". Hệ thống backend từ chối yêu cầu với HTTP 400 (mã CANNOT_EDIT_EXTERNAL_TEMPLATE). Giao diện modal hiển thị chính xác cảnh báo lỗi màu đỏ: "External templates are managed by an external provider and cannot have version drafts.", bảo vệ toàn vẹn mẫu quản lý bởi bên thứ ba (Firebase Auth).
- Evidence screenshot: screenshots/E2E-007-012--external-template-rejected.jpg
- Bug reference: NONE
<!-- EXECUTION:END -->

