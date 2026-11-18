---
scenario_id: E2E-011-006
feature: FE-011
actor: MEMBER
preconditions:
  - backend_health: PASS
  - frontend_health: PASS
traceability:
  - FE-011
  - AC-011-OWNERSHIP-AUTHORIZATION
isolation_key: e2e-011-ownership-001
cleanup:
  - none
id: E2E-011-006
test_level: e2e
test_case_type: B
source_ref: spec.md#FR-011-003
target_url: http://localhost:5173/reviews
actor_role: Member
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
  - http://localhost:8080/v3/api-docs
test_data:
  fixture: member-review-fixture
  account_ref: member.test@mland.vn
  isolation_key: e2e-011-ownership-001
---

# E2E-011-006: Kiểm soát quyền chỉnh sửa đánh giá - Chặn truy cập và chỉnh sửa đánh giá của người dùng khác

**Ngày tạo**: 2026-11-18  
**Người phụ trách**: Senior QA & System Verification Engineer  
**Tính năng**: 011-review-management  
**Trạng thái**: PASS 100%  

## 1. Tiền điều kiện (Preconditions)

- [x] Môi trường Backend Spring Boot sẵn sàng tại `http://localhost:8080`.
- [x] Môi trường Frontend Vite sẵn sàng tại `http://localhost:5173`.
- [x] Đã đăng nhập tài khoản Member: `member.test@mland.vn`.
- [x] Tồn tại đánh giá của chính Member này (`cc6446de-4851-4c95-8b81-7965213da398`).
- [x] Tồn tại đánh giá của người dùng khác (`rev-101-5star`, chủ sở hữu `Nguyễn V***`).

## 2. Phân tích rủi ro an ninh & đối nghịch (Adversarial Security Analysis - IDOR Protection)

- **IDOR Protection trên giao diện chi tiết (`/reviews/:id`)**: Chỉ chủ sở hữu bài đánh giá (`canEdit = true`) mới nhìn thấy nút "Chỉnh sửa đánh giá của bạn". Đối với đánh giá của người dùng khác, nút này hoàn toàn bị ẩn để tránh nhầm lẫn hoặc kích thích can thiệp.
- **IDOR Protection trên đường dẫn trực tiếp (`/reviews/:id/edit`)**: Người dùng cố tình nhập URL trực tiếp hoặc can thiệp ID của người khác phải bị chặn hiển thị biểu mẫu chỉnh sửa. Hệ thống phải render thông báo từ chối truy cập: *"Bạn không có quyền chỉnh sửa đánh giá này. Chỉ chủ sở hữu đánh giá mới có thể chỉnh sửa."*, không hiển thị bất kỳ trường nhập liệu nào của form.
- **Backend Authorization Protection**: Khi gửi `PATCH /api/v1/reviews/{id}`, backend xác thực `actor.accountId().equals(review.getOwnerReference())` và ném HTTP 403 `REVIEW_EDIT_FORBIDDEN` nếu không đúng chủ sở hữu.

## 3. Các bước kiểm thử trên trình duyệt (Browser Action Steps)

### Step 1 — Kiểm tra đánh giá của người dùng khác (Ẩn nút chỉnh sửa)
```browser
goto http://localhost:5173/reviews/rev-101-5star
wait getByRole('heading', { name: 'Chi tiết đánh giá' })
snapshot -i
# Quan sát: Chỉ có nút "Quay lại", không xuất hiện nút "Chỉnh sửa đánh giá của bạn"
```

### Step 2 — Tấn công đổi URL trực tiếp sang trang chỉnh sửa của người dùng khác (`/edit`)
```browser
goto http://localhost:5173/reviews/rev-101-5star/edit
wait getByRole('heading', { name: 'Chỉnh sửa đánh giá' })
snapshot -i
# Quan sát: Form chỉnh sửa bị chặn hoàn toàn, hiển thị:
# "Bạn không có quyền chỉnh sửa đánh giá này. Chỉ chủ sở hữu đánh giá mới có thể chỉnh sửa."
# Đi kèm nút "Quay lại" và "Xem chi tiết"
```

### Step 3 — Kiểm tra đánh giá của chính mình (Hiện nút chỉnh sửa và cho phép cập nhật)
```browser
goto http://localhost:5173/reviews/cc6446de-4851-4c95-8b81-7965213da398
wait getByRole('heading', { name: 'Chi tiết đánh giá' })
click getByRole('link', { name: 'Chỉnh sửa đánh giá của bạn' })
wait getByRole('heading', { name: 'Chỉnh sửa đánh giá' })
# Form mở ra bình thường với dữ liệu đánh giá cá nhân
fill getByPlaceholder('Viết đánh giá tối đa 2.000 ký tự…') "Cập nhật E2E: Sau 2 tuần sử dụng nhẫn vẫn sáng bóng, thợ kim hoàn tư vấn rất chu đáo và nhiệt tình!"
click getByRole('button', { name: 'Lưu thay đổi' })
wait getByRole('heading', { name: 'Chi tiết đánh giá' })
```

## 4. Kết quả nghiệm thu thực tế (Actual Verification Results)

- **Trình duyệt Chrome (Live Browser MCP)**: PASS 100%.
- **Chặn hiển thị nút sửa**: Trang `rev-101-5star` không có liên kết edit.
- **Chặn truy cập trực tiếp bằng URL**: Trang `rev-101-5star/edit` bị chặn hoàn toàn, render cảnh báo từ chối truy cập và không lộ dữ liệu form sửa.
- **Chỉnh sửa đánh giá của chính mình**: Thành công cập nhật và lưu thay đổi vào cơ sở dữ liệu.

## 5. Bằng chứng kiểm thử (Evidence Screenshots)

- Chặn truy cập trực tiếp URL sửa của người khác:  
  ![Chặn trực tiếp URL](screenshots/E2E-011-006--forbidden-edit-url-tampering.jpg)
- Không có nút chỉnh sửa trên đánh giá người khác:  
  ![Ẩn nút chỉnh sửa](screenshots/E2E-011-006--other-user-no-edit-btn.jpg)
- Chủ sở hữu mở và sửa thành công:  
  ![Chủ sở hữu chỉnh sửa](screenshots/E2E-011-006--owner-edit-allowed.jpg)
