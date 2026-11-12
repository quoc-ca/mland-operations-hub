---
scenario_id: E2E-004-002
feature: FE-004
actor: MEMBER
preconditions:
  - backend_health: FAIL
  - frontend_health: FAIL
traceability:
  - FE-004
  - AC-004-DESIGN-REQUEST
isolation_key: e2e-004-member-001
cleanup:
  - delete_created_design_request
id: E2E-004-002
test_level: e2e
test_case_type: B
source_ref: spec.md#FR-004-002
target_url: http://localhost:5173/design-requests/new
actor_role: Member
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
  - http://localhost:8080/v3/api-docs
test_data:
  fixture: member-design-request-fixture
  account_ref: MEMBER_001
  isolation_key: e2e-004-member-001
---

# E2E-004-002: Thành viên tạo yêu cầu phân tích thiết kế độc lập kèm kiểm thử các điều kiện biên

**Ngày tạo**: 2026-11-12  
**Người phụ trách**: Senior QA & System Verification Engineer  
**Tính năng**: 004-ai-chatbox-analysis-and-suggestion  

## 1. Tiền điều kiện (Preconditions)

- [ ] Môi trường Backend Spring Boot sẵn sàng tại `http://localhost:8080`.
- [ ] Môi trường Frontend Vite sẵn sàng tại `http://localhost:5173`.
- [ ] Đã đăng nhập với tài khoản Member hợp lệ (`MEMBER_001`).
- [ ] Fixture ảnh hợp lệ: `ring-sample.png` (< 10 MB, định dạng PNG/JPEG).
- [ ] Fixture ảnh quá khổ: `oversized-sample.jpg` (> 10 MB).

## 2. Phân tích rủi ro đối nghịch & Các trường hợp bắt buộc (Adversarial Analysis)

1. **Member thiếu consent bị từ chối:** Nếu người dùng tải ảnh lên nhưng không tích chọn checkbox cam kết thỏa thuận xử lý ảnh (`INDEPENDENT_DESIGN_IMAGE_ANALYSIS v1.0`), nút submit bị vô hiệu hóa hoặc hệ thống từ chối với thông báo lỗi rõ ràng.
2. **Ảnh vượt 10 MB bị từ chối:** Client kiểm tra kích thước file ngay khi chọn. Nếu file vượt quá 10 MB, hiển thị thông báo "Kích thước hình ảnh vượt quá giới hạn 10 MB."
3. **Upload ảnh thứ tư trong 5 phút trả 429:** Cơ chế rate-limit nguyên tử giới hạn tối đa 3 lần upload ảnh trong 5 phút. Lần upload thứ 4 sẽ nhận phản hồi HTTP 429 `IMAGE_RATE_LIMITED`.
4. **Retry giữ nguyên dữ liệu form và Idempotency-Key:** Khi xảy ra lỗi kết nối hoặc 503, bấm nút "Thử lại" giữ nguyên thông tin mô tả, ảnh đã chọn và giữ nguyên `Idempotency-Key` ban đầu để tránh tạo trùng bản ghi.
5. **Hiển thị an toàn (Safe Projections):** Màn hình chi tiết `/design-requests/:id` chỉ hiển thị các thuộc tính trích xuất (style, chất liệu, đá quý, độ khó, chất lượng ảnh), tuyệt đối **không hiển thị raw provider response** từ Gemini/Google Cloud Vision.

## 3. Các bước kiểm thử trên trình duyệt (Browser Action Steps)

### Step 1 — Đăng nhập tài khoản Member và mở Design Workspace
```browser
goto http://localhost:5173/login
fill getByLabel(/Email/i) "member.test@mland.vn"
fill getByLabel(/Mật khẩu/i) "Member@123456"
click getByRole('button', { name: /Đăng nhập/i })
wait --text "Trang chủ"
goto http://localhost:5173/design-requests/new
wait getByRole('heading', { name: /Gửi yêu cầu phân tích thiết kế/i })
snapshot -i
```

### Step 2 — Kiểm tra rào chắn ảnh quá 10 MB (Negative Boundary)
```browser
uploadFile getByLabel(/Tải lên hình ảnh mẫu/i) "fixtures/oversized-sample.jpg"
wait --text "Kích thước hình ảnh vượt quá giới hạn 10 MB."
snapshot -i
```

### Step 3 — Chọn ảnh hợp lệ nhưng bỏ trống Consent (Consent Gate)
```browser
uploadFile getByLabel(/Tải lên hình ảnh mẫu/i) "fixtures/ring-sample.png"
fill getByLabel(/Mô tả ý tưởng/i) "Nhẫn đôi bạc đính đá Moissanite phong cách tối giản."
click getByRole('button', { name: /Gửi yêu cầu thiết kế/i })
wait --text "Vui lòng xác nhận sự đồng thuận phân tích hình ảnh để tiếp tục."
snapshot -i
```

### Step 4 — Tích chọn Consent và Submit thành công
```browser
check getByLabel(/Tôi đồng ý cho phép phân tích hình ảnh này/i)
click getByRole('button', { name: /Gửi yêu cầu thiết kế/i })
wait --text "Đang phân tích thiết kế..."
wait --text "Hoàn tất phân tích"
snapshot -i
```

### Step 5 — Kiểm tra hiển thị an toàn và Candidate Features
```browser
wait getByRole('heading', { name: /Thông số trích xuất/i })
snapshot -i
```

### Step 6 — Kiểm tra Rate Limit (Upload ảnh lần thứ tư liên tiếp)
```browser
goto http://localhost:5173/design-requests/new
fill getByLabel(/Mô tả ý tưởng/i) "Yêu cầu thứ 4 kiểm tra rate limit"
uploadFile getByLabel(/Tải lên hình ảnh mẫu/i) "fixtures/ring-sample.png"
check getByLabel(/Tôi đồng ý cho phép phân tích hình ảnh này/i)
click getByRole('button', { name: /Gửi yêu cầu thiết kế/i })
wait --text "Bạn đã vượt quá giới hạn tải ảnh (3 lần/5 phút). Vui lòng thử lại sau ít phút."
snapshot -i
```

## 4. Kết quả kỳ vọng (Expected Assertions)

```browser
find "Thông số trích xuất"
# Expected: one or more matches
find "Chất lượng hình ảnh: ĐẠT"
# Expected: one or more matches
console --errors
# Expected: no unexpected runtime errors
requests --filter "/api/v1/design-requests"
# Expected: POST 202 hoặc 200 kèm Idempotency-Key
```

## 5. Bằng chứng kiểm thử (Evidence)

- Screenshot thành công: `screenshots/E2E-004-002--success.jpg`
- Lệnh chụp: `screenshot --full --path "ppswbs-spec/features/004-ai-chatbox-analysis-and-suggestion/test-results/screenshots/E2E-004-002--success.jpg"`

## 6. Kết quả thực thi (Execution History)

<!-- EXECUTION:START -->
- Latest status: BLOCKED
- Executed at: 2026-11-12 19:47:15
- Executed by: Senior QA & System Verification Engineer
- Browser tool: browsermcp
- Failure step: Step 1: Đăng nhập tài khoản Member
- Actual result: Preflight T01 (Browser MCP) và T02 (Frontend :5173 / Backend :8080) đã PASS. Tuy nhiên kịch bản bị chặn tại bước xác thực token Firebase: Backend local chưa cấu hình FIREBASE_ADMIN_CREDENTIALS_PATH trong .env nên verifyIdToken trả về 401 INVALID_TOKEN khi nhận JWT token từ client.
- Evidence screenshot: NOT_RUN
- Bug reference: NONE
<!-- EXECUTION:END -->

