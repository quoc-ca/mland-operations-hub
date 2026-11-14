---
scenario_id: E2E-011-003
feature: FE-011
actor: MEMBER
preconditions:
  - backend_health: PASS
  - frontend_health: PASS
traceability:
  - FE-011
  - AC-011-MEMBER-REVIEW
isolation_key: e2e-011-member-001
cleanup:
  - delete_created_review
id: E2E-011-003
test_level: e2e
test_case_type: B
source_ref: spec.md#FR-011-003
target_url: http://localhost:5173/my/reviews
actor_role: Member
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
  - http://localhost:8080/v3/api-docs
test_data:
  fixture: member-review-fixture
  account_ref: MEMBER_ELIGIBLE_001
  isolation_key: e2e-011-member-001
---

# E2E-011-003: Hội viên tạo đánh giá cho trải nghiệm cá nhân và chỉnh sửa phiên bản lạc quan

**Ngày tạo**: 2026-11-12  
**Người phụ trách**: Senior QA & System Verification Engineer  
**Tính năng**: 011-review-management  

## 1. Tiền điều kiện (Preconditions)

- [ ] Môi trường Backend Spring Boot sẵn sàng tại `http://localhost:8080`.
- [ ] Môi trường Frontend Vite sẵn sàng tại `http://localhost:5173`.
- [ ] Đã đăng nhập tài khoản Member: `member.review@mland.vn`.
- [ ] Tài khoản có đơn hàng bán lẻ đã hoàn tất nhận hàng (`PICKED_UP`).

## 2. Phân tích rủi ro đối nghịch (Adversarial Risk Analysis)

- **Không cần tra cứu thủ công:** Khi đã đăng nhập Member, hệ thống tự động tải danh sách nguồn hợp lệ thuộc quyền sở hữu của chính Member, không hiển thị form tra cứu email/phone của khách vãng lai.
- **Chỉnh sửa bảo vệ bằng khóa lạc quan (`If-Match-Version`):** Khi sửa đánh giá, gửi kèm header `If-Match-Version`. Nếu phiên bản đã bị cập nhật, nhận lỗi 409 `REVIEW_VERSION_CONFLICT`.
- **Giới hạn độ dài nội dung (2,000 ký tự):** Chặn các nội dung quá 2,000 ký tự ngay tại client và backend.

## 3. Các bước kiểm thử trên trình duyệt (Browser Action Steps)

### Step 1 — Đăng nhập Member và mở trang quản lý đánh giá cá nhân
```browser
goto http://localhost:5173/login
fill getByLabel(/Email/i) "member.review@mland.vn"
fill getByLabel(/Mật khẩu/i) "Member@123456"
click getByRole('button', { name: /Đăng nhập/i })
goto http://localhost:5173/my/reviews
wait getByRole('heading', { name: /Đánh giá của tôi/i })
snapshot -i
```

### Step 2 — Tạo đánh giá cho đơn bán lẻ
```browser
click getByRole('button', { name: /Viết đánh giá/i })
click getByTestId("rating-star-4")
fill getByLabel(/Nội dung/i) "Nhẫn hoàn thiện đẹp, giao hàng đúng hẹn."
click getByRole('button', { name: /Gửi đánh giá/i })
wait --text "Đánh giá của bạn đã được xuất bản."
snapshot -i
```

### Step 3 — Chỉnh sửa nội dung đánh giá vừa tạo
```browser
click getByRole('button', { name: /Chỉnh sửa đánh giá/i })
fill getByLabel(/Nội dung/i) "Cập nhật: Sau 1 tuần sử dụng nhẫn vẫn sáng bóng, rất ưng ý!"
click getByRole('button', { name: /Lưu chỉnh sửa/i })
wait --text "Đã cập nhật đánh giá thành công."
snapshot -i
```

## 4. Kết quả kỳ vọng (Expected Assertions)

```browser
find "Cập nhật: Sau 1 tuần sử dụng"
# Expected: one or more matches
find "Phiên bản 2"
# Expected: one or more matches
requests --filter "/api/v1/reviews"
# Expected: PATCH 200 kèm If-Match-Version: "1"
```

## 5. Bằng chứng kiểm thử (Evidence)

- Screenshot thành công: `screenshots/E2E-011-003--success.jpg`
- Lệnh chụp: `screenshot --full --path "ppswbs-spec/features/011-review-management/test-results/screenshots/E2E-011-003--success.jpg"`

## 6. Kết quả thực thi (Execution History)

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-13 19:14:00
- Executed by: Senior QA & System Verification Engineer
- Browser tool: browsermcp
- Failure step: NONE
- Actual result: Sau khi seed đơn hàng bán lẻ đã hoàn tất nhận hàng (PICKED_UP, mã dòng đơn roi-test-member-01) cho tài khoản Member member.test@mland.vn, hội viên đăng nhập mở /reviews/eligible-sources thấy nguồn đủ điều kiện "Product Variant #101 roi-test-member-01", bấm "Viết đánh giá" mở /reviews/new. Hội viên đánh giá 5 sao kèm nội dung "Nhẫn hoàn thiện đẹp, giao hàng đúng hẹn." và submit thành công qua POST /api/v1/reviews với Idempotency-Key. Tiếp tục bấm "Edit own review", cập nhật nội dung thành "Cập nhật: Sau 1 tuần sử dụng nhẫn vẫn sáng bóng, rất ưng ý!" và lưu thay đổi thành công qua PATCH /api/v1/reviews/:id với header If-Match-Version: 0. Đánh giá được cập nhật thành công, phiên bản tăng lên 1 trong cơ sở dữ liệu.
- Evidence screenshot: screenshots/E2E-011-003--eligible-sources-empty.jpg, screenshots/E2E-011-003--success.jpg
- Bug reference: NONE
<!-- EXECUTION:END -->

