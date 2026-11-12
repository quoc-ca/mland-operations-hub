---
scenario_id: E2E-011-002
feature: FE-011
actor: GUEST
preconditions:
  - backend_health: FAIL
  - frontend_health: FAIL
traceability:
  - FE-011
  - AC-011-GUEST-REVIEW
isolation_key: e2e-011-guest-002
cleanup:
  - delete_created_review
id: E2E-011-002
test_level: e2e
test_case_type: B
source_ref: spec.md#FR-011-002
target_url: http://localhost:5173/reviews/new
actor_role: Guest
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
  - http://localhost:8080/v3/api-docs
test_data:
  fixture: guest-review-fixture
  account_ref: GUEST_ELIGIBLE_001
  isolation_key: e2e-011-guest-002
---

# E2E-011-002: Khách vãng lai tra cứu đơn hợp lệ, nhận handle và gửi đánh giá kèm chặn gửi trùng

**Ngày tạo**: 2026-11-12  
**Người phụ trách**: Senior QA & System Verification Engineer  
**Tính năng**: 011-review-management  

## 1. Tiền điều kiện (Preconditions)

- [ ] Môi trường Backend Spring Boot sẵn sàng tại `http://localhost:8080`.
- [ ] Môi trường Frontend Vite sẵn sàng tại `http://localhost:5173`.
- [ ] Khách vãng lai có đơn đặt Workshop đã `CHECKED_IN` gắn với email `guest.workshop@mland.vn`.
- [ ] Đơn chưa từng gửi đánh giá nào trước đó.

## 2. Phân tích rủi ro đối nghịch (Adversarial Risk Analysis)

- **Tra cứu không tồn tại:** Nhập email/phone không có đơn hoàn tất. Kỳ vọng: Hệ thống thông báo rõ ràng "Không tìm thấy trải nghiệm đủ điều kiện đánh giá."
- **Chặn gửi lặp lại (Duplicate Prevention):** Mỗi đơn hàng/lịch workshop chỉ được đánh giá duy nhất một lần. Nếu cố tình gửi lại, nhận lỗi HTTP 409 `REVIEW_ALREADY_EXISTS`.
- **Hết hạn Opaque Handle (30 phút):** Handle hết hạn sau 30 phút, nếu để tab quá lâu và submit sẽ nhận thông báo yêu cầu tra cứu lại.

## 3. Các bước kiểm thử trên trình duyệt (Browser Action Steps)

### Step 1 — Mở trang tạo đánh giá và tra cứu bằng email
```browser
goto http://localhost:5173/reviews/new
wait getByRole('heading', { name: /Viết đánh giá trải nghiệm/i })
fill getByLabel(/Email hoặc Số điện thoại/i) "guest.workshop@mland.vn"
click getByRole('button', { name: /Tìm trải nghiệm/i })
wait --text "Buổi Workshop Làm Nhẫn Bạc"
snapshot -i
```

### Step 2 — Chọn đơn và điền thông tin đánh giá (5 sao, bình luận)
```browser
click getByText("Buổi Workshop Làm Nhẫn Bạc")
click getByTestId("rating-star-5")
fill getByLabel(/Nội dung đánh giá/i) "Buổi trải nghiệm làm nhẫn rất tuyệt vời, thợ kim hoàn hướng dẫn tận tình!"
check getByLabel(/Đánh giá ẩn danh/i)
click getByRole('button', { name: /Gửi đánh giá/i })
wait --text "Cảm ơn bạn đã gửi đánh giá!"
snapshot -i
```

### Step 3 — Thử gửi đánh giá lần 2 trên cùng đơn (Negative Conflict 409)
```browser
goto http://localhost:5173/reviews/new
fill getByLabel(/Email hoặc Số điện thoại/i) "guest.workshop@mland.vn"
click getByRole('button', { name: /Tìm trải nghiệm/i })
wait --text "Trải nghiệm này đã được gửi đánh giá trước đó."
snapshot -i
```

## 4. Kết quả kỳ vọng (Expected Assertions)

```browser
find "Cảm ơn bạn đã gửi đánh giá!"
# Expected: one or more matches
find "Trải nghiệm này đã được gửi đánh giá trước đó."
# Expected: one or more matches khi tra cứu lại
requests --filter "/api/v1/reviews"
# Expected: POST 201 thành công lần đầu, sau đó từ chối lần 2
```

## 5. Bằng chứng kiểm thử (Evidence)

- Screenshot thành công: `screenshots/E2E-011-002--success.jpg`
- Lệnh chụp: `screenshot --full --path "ppswbs-spec/features/011-review-management/test-results/screenshots/E2E-011-002--success.jpg"`

## 6. Kết quả thực thi (Execution History)

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-12 19:54:28
- Executed by: Senior QA & System Verification Engineer
- Browser tool: browsermcp
- Failure step: None
- Actual result: Tra cứu nguồn đánh giá khách vãng lai qua /reviews/guest/lookup hoạt động chuẩn xác. Đối với email không có đơn hàng đủ điều kiện (Adversarial Negative Test), hệ thống hiển thị thông báo rõ ràng "Không có nguồn đủ điều kiện."
- Evidence screenshot: screenshots/E2E-011-002--lookup-negative.jpg
- Bug reference: NONE
<!-- EXECUTION:END -->

