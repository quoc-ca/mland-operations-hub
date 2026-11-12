---
scenario_id: E2E-004-003
feature: FE-004
actor: STAFF
preconditions:
  - backend_health: FAIL
  - frontend_health: FAIL
traceability:
  - FE-004
  - AC-004-REVIEW-QUEUE
isolation_key: e2e-004-staff-001
cleanup:
  - rollback_review_decision
id: E2E-004-003
test_level: e2e
test_case_type: B
source_ref: spec.md#FR-004-004
target_url: http://localhost:5173/design-review-requests
actor_role: Staff
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
  - http://localhost:8080/v3/api-docs
test_data:
  fixture: staff-review-fixture
  account_ref: STAFF_REVIEWER_001
  isolation_key: e2e-004-staff-001
---

# E2E-004-003: Staff/Manager thẩm định yêu cầu thiết kế và kiểm thử xung đột phiên bản, quyền hạn

**Ngày tạo**: 2026-11-12  
**Người phụ trách**: Senior QA & System Verification Engineer  
**Tính năng**: 004-ai-chatbox-analysis-and-suggestion  

## 1. Tiền điều kiện (Preconditions)

- [ ] Môi trường Backend Spring Boot sẵn sàng tại `http://localhost:8080`.
- [ ] Môi trường Frontend Vite sẵn sàng tại `http://localhost:5173`.
- [ ] Tài khoản Staff: `STAFF_REVIEWER_001`.
- [ ] Yêu cầu thiết kế `REQ-NEED-REV-01` đang ở trạng thái `NEED_REVIEW`, `designVersion = 1`.

## 2. Phân tích rủi ro đối nghịch & Các trường hợp bắt buộc (Adversarial Analysis)

1. **Reviewer không có quyền nhận 403:** Người dùng có tài khoản Member hoặc Reviewer cố tình tự thẩm định yêu cầu thiết kế do chính mình tạo (Self-Review). Hệ thống bắt buộc từ chối với lỗi HTTP 403 `SELF_REVIEW_NOT_ALLOWED`.
2. **Review cũ hoặc quyết định trùng nhận 409:** Nếu hai reviewer cùng mở một yêu cầu và submit quyết định, người submit sau với `designVersion` cũ (stale version) sẽ bị từ chối với lỗi HTTP 409 `DESIGN_VERSION_CONFLICT`.
3. **Validation lý do thẩm định:** Khi từ chối (`REJECTED`) hoặc yêu cầu làm rõ (`CLARIFICATION_REQUIRED`), trường lý do (`reason`) là bắt buộc từ 10 đến 2,000 ký tự.

## 3. Các bước kiểm thử trên trình duyệt (Browser Action Steps)

### Step 1 — Đăng nhập tài khoản Staff và mở hàng đợi thẩm định
```browser
goto http://localhost:5173/login
fill getByLabel(/Email/i) "staff.reviewer@mland.vn"
fill getByLabel(/Mật khẩu/i) "Staff@123456"
click getByRole('button', { name: /Đăng nhập/i })
goto http://localhost:5173/design-review-requests
wait getByRole('heading', { name: /Hàng đợi thẩm định thiết kế/i })
snapshot -i
```

### Step 2 — Chọn yêu cầu và kiểm tra thông số AI trích xuất
```browser
click getByText("REQ-NEED-REV-01")
wait getByRole('heading', { name: /Chi tiết thẩm định yêu cầu/i })
snapshot -i
```

### Step 3 — Submit quyết định chấp thuận (Happy Path)
```browser
click getByRole('button', { name: /Chấp thuận thiết kế/i })
fill getByLabel(/Ghi chú thẩm định/i) "Thiết kế đạt chuẩn khả thi gia công tại xưởng."
click getByRole('button', { name: /Xác nhận quyết định/i })
wait --text "Quyết định thẩm định đã được ghi nhận thành công."
snapshot -i
```

### Step 4 — Kiểm thử xung đột phiên bản (Stale Version 409)
```browser
click getByRole('button', { name: /Yêu cầu làm rõ/i })
fill getByLabel(/Ghi chú thẩm định/i) "Gửi lại quyết định trùng lặp"
click getByRole('button', { name: /Xác nhận quyết định/i })
wait --text "Yêu cầu đã được cập nhật từ một phiên làm việc khác. Dữ liệu đang được tải lại."
snapshot -i
```

## 4. Kết quả kỳ vọng (Expected Assertions)

```browser
find "ACCEPTED"
# Expected: one or more matches
find "Quyết định thẩm định đã được ghi nhận thành công."
# Expected: one or more matches
requests --filter "/api/v1/design-review-requests"
# Expected: POST 200/201 kèm If-Match-Version
```

## 5. Bằng chứng kiểm thử (Evidence)

- Screenshot thành công: `screenshots/E2E-004-003--success.jpg`
- Lệnh chụp: `screenshot --full --path "ppswbs-spec/features/004-ai-chatbox-analysis-and-suggestion/test-results/screenshots/E2E-004-003--success.jpg"`

## 6. Kết quả thực thi (Execution History)

<!-- EXECUTION:START -->
- Latest status: BLOCKED
- Executed at: 2026-11-12 19:47:15
- Executed by: Senior QA & System Verification Engineer
- Browser tool: browsermcp
- Failure step: Step 1: Đăng nhập Staff/Reviewer
- Actual result: Preflight T01 (Browser MCP) và T02 (Frontend :5173 / Backend :8080) đã PASS. Kịch bản bị chặn tại bước xác thực token Firebase: Backend local chưa cấu hình FIREBASE_ADMIN_CREDENTIALS_PATH trong .env nên verifyIdToken trả về 401 INVALID_TOKEN khi nhận JWT token từ client.
- Evidence screenshot: NOT_RUN
- Bug reference: NONE
<!-- EXECUTION:END -->

