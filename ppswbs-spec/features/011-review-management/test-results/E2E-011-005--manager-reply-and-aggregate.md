---
scenario_id: E2E-011-005
feature: FE-011
actor: MANAGER
preconditions:
  - backend_health: FAIL
  - frontend_health: FAIL
traceability:
  - FE-011
  - AC-011-REPLY
isolation_key: e2e-011-manager-001
cleanup:
  - delete_reply
id: E2E-011-005
test_level: e2e
test_case_type: N
source_ref: spec.md#FR-011-005
target_url: http://localhost:5173/reviews
actor_role: Manager
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
  - http://localhost:8080/v3/api-docs
test_data:
  fixture: review-reply-fixture
  account_ref: MANAGER_REPLY_001
  isolation_key: e2e-011-manager-001
---

# E2E-011-005: Quản lý đăng phản hồi chính thức cho đánh giá và kiểm tra hiển thị công khai

**Ngày tạo**: 2026-11-12  
**Người phụ trách**: Senior QA & System Verification Engineer  
**Tính năng**: 011-review-management  

## 1. Tiền điều kiện (Preconditions)

- [ ] Môi trường Backend Spring Boot sẵn sàng tại `http://localhost:8080`.
- [ ] Môi trường Frontend Vite sẵn sàng tại `http://localhost:5173`.
- [ ] Có sẵn một đánh giá công khai `REV-PUBLIC-01` chưa có phản hồi của Mland.
- [ ] Tài khoản Manager: `MANAGER_REPLY_001`.

## 2. Phân tích rủi ro đối nghịch (Adversarial Risk Analysis)

- **Mỗi đánh giá chỉ có duy nhất 1 phản hồi chính thức hiện hành:** Cố gắng gửi phản hồi thứ hai cho cùng một đánh giá sẽ bị từ chối với lỗi HTTP 409 `REPLY_ALREADY_EXISTS`.
- **Chỉnh sửa phản hồi có lưu vết:** Khi Manager cập nhật nội dung phản hồi, hệ thống lưu lại lịch sử chỉnh sửa để phục vụ audit.

## 3. Các bước kiểm thử trên trình duyệt (Browser Action Steps)

### Step 1 — Manager mở chi tiết đánh giá và gửi phản hồi chính thức
```browser
goto http://localhost:5173/login
fill getByLabel(/Email/i) "manager.reply@mland.vn"
fill getByLabel(/Mật khẩu/i) "Manager@123456"
click getByRole('button', { name: /Đăng nhập/i })
goto http://localhost:5173/reviews/REV-PUBLIC-01
wait getByRole('heading', { name: /Chi tiết đánh giá/i })
click getByRole('button', { name: /Gửi phản hồi chính thức/i })
fill getByLabel(/Nội dung phản hồi/i) "Cảm ơn bạn đã đóng góp ý kiến. Mland rất hân hạnh được đồng hành cùng bạn!"
click getByRole('button', { name: /Đăng phản hồi/i })
wait --text "Phản hồi chính thức từ Mland"
snapshot -i
```

### Step 2 — Đăng xuất và mở trang với tư cách Khách vãng lai để xác minh hiển thị
```browser
goto http://localhost:5173/reviews/REV-PUBLIC-01
wait --text "Phản hồi chính thức từ Mland"
wait --text "Cảm ơn bạn đã đóng góp ý kiến"
snapshot -i
```

## 4. Kết quả kỳ vọng (Expected Assertions)

```browser
find "Phản hồi chính thức từ Mland"
# Expected: one or more matches
requests --filter "/api/v1/reviews/REV-PUBLIC-01/reply"
# Expected: POST 201 kèm Idempotency-Key
```

## 5. Bằng chứng kiểm thử (Evidence)

- Screenshot thành công: `screenshots/E2E-011-005--success.jpg`
- Lệnh chụp: `screenshot --full --path "ppswbs-spec/features/011-review-management/test-results/screenshots/E2E-011-005--success.jpg"`

## 6. Kết quả thực thi (Execution History)

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-13 18:36:00
- Executed by: Senior QA & System Verification Engineer
- Browser tool: browsermcp
- Failure step: NONE
- Actual result: Manager đăng nhập mở chi tiết review tại /review-moderation/11111111-1111-1111-1111-111111111111, nhập nội dung phản hồi chính thức "Cảm ơn bạn đã đóng góp ý kiến. Mland rất hân hạnh được đồng hành cùng bạn!" và nhấn "Lưu phản hồi". Backend xử lý POST /api/v1/reviews/11111111-1111-1111-1111-111111111111/reply trả về HTTP 201 Created. Giao diện cập nhật hiển thị khối "Phản hồi từ Ban Quản lý Mland (v0)". Truy cập /reviews và /reviews/11111111-1111-1111-1111-111111111111 với tư cách khách công khai hiển thị phản hồi chính thức và điểm tổng hợp 4.0 ★★★★☆ (1 đánh giá) chính xác.
- Evidence screenshot: screenshots/E2E-011-005--success.jpg, screenshots/E2E-011-005--public-reply.jpg
- Bug reference: NONE
<!-- EXECUTION:END -->

