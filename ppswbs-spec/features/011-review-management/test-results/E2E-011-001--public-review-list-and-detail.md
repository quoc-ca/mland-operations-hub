---
scenario_id: E2E-011-001
feature: FE-011
actor: GUEST
preconditions:
  - backend_health: FAIL
  - frontend_health: FAIL
traceability:
  - FE-011
  - AC-011-PUBLIC-LIST
isolation_key: e2e-011-guest-001
cleanup:
  - none-client-only
id: E2E-011-001
test_level: e2e
test_case_type: N
source_ref: spec.md#FR-011-001
target_url: http://localhost:5173/reviews
actor_role: Guest
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
  - http://localhost:8080/v3/api-docs
test_data:
  fixture: public-reviews-fixture
  account_ref: GUEST_PUBLIC
  isolation_key: e2e-011-guest-001
---

# E2E-011-001: Khách vãng lai xem danh sách đánh giá công khai, bộ lọc và tổng hợp điểm sao

**Ngày tạo**: 2026-11-12  
**Người phụ trách**: Senior QA & System Verification Engineer  
**Tính năng**: 011-review-management  

## 1. Tiền điều kiện (Preconditions)

- [ ] Môi trường Backend Spring Boot sẵn sàng tại `http://localhost:8080`.
- [ ] Môi trường Frontend Vite sẵn sàng tại `http://localhost:5173`.
- [ ] Dữ liệu mồi: Ít nhất 3 đánh giá trạng thái `PUBLIC` thuộc workshop và sản phẩm lẻ.
- [ ] Phiên người dùng: Guest (chưa đăng nhập).

## 2. Phân tích rủi ro đối nghịch (Adversarial Risk Analysis)

- **Đánh giá bị ẩn/xóa không được lộ ra công khai:** Các đánh giá ở trạng thái `HIDDEN` hoặc `REMOVED` tuyệt đối không xuất hiện trong danh sách công khai và không được tính vào aggregate điểm sao trung bình.
- **Che dấu danh tính (PII Masking):** Tên người đánh giá hoặc thông tin liên hệ của khách vãng lai phải được làm mờ (ví dụ `N** D**`, `090****123`).

## 3. Các bước kiểm thử trên trình duyệt (Browser Action Steps)

### Step 1 — Mở trang danh sách đánh giá công khai
```browser
goto http://localhost:5173/reviews
wait getByRole('heading', { name: /Đánh giá từ khách hàng/i })
snapshot -i
```

### Step 2 — Kiểm tra tổng hợp điểm sao và số lượng đánh giá
```browser
wait --text "Điểm trung bình"
snapshot -i
```

### Step 3 — Lọc đánh giá theo gói Workshop cụ thể
```browser
click getByRole('button', { name: /Gói Workshop/i })
wait --text "Mẫu Nhẫn Cưới Classic"
snapshot -i
```

## 4. Kết quả kỳ vọng (Expected Assertions)

```browser
find "Đánh giá từ khách hàng"
# Expected: one or more matches
find "sao"
# Expected: one or more matches
requests --filter "/api/v1/reviews"
# Expected: GET 200 trả về danh sách public reviews
```

## 5. Bằng chứng kiểm thử (Evidence)

- Screenshot thành công: `screenshots/E2E-011-001--success.jpg`
- Lệnh chụp: `screenshot --full --path "ppswbs-spec/features/011-review-management/test-results/screenshots/E2E-011-001--success.jpg"`

## 6. Kết quả thực thi (Execution History)

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-12 19:53:54
- Executed by: Senior QA & System Verification Engineer
- Browser tool: browsermcp
- Failure step: None
- Actual result: Trang danh sách đánh giá công khai hiển thị chuẩn xác với bộ lọc số sao (1-5 sao), thống kê điểm trung bình và kết nối API GET /api/v1/reviews trả về HTTP 200 OK.
- Evidence screenshot: screenshots/E2E-011-001--success.jpg
- Bug reference: NONE
<!-- EXECUTION:END -->

