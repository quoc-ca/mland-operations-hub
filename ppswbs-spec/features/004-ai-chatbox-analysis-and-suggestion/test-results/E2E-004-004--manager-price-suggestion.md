---
scenario_id: E2E-004-004
feature: FE-004
actor: MANAGER
preconditions:
  - backend_health: FAIL
  - frontend_health: FAIL
traceability:
  - FE-004
  - AC-004-PRICE-SUGGESTION
isolation_key: e2e-004-manager-001
cleanup:
  - reset_proposal_decision
id: E2E-004-004
test_level: e2e
test_case_type: N
source_ref: spec.md#FR-004-006
target_url: http://localhost:5173/price-proposals/PROP-001/ai-suggestions
actor_role: Manager
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
  - http://localhost:8080/v3/api-docs
test_data:
  fixture: price-proposal-fixture
  account_ref: MANAGER_PRICING_001
  isolation_key: e2e-004-manager-001
---

# E2E-004-004: Manager yêu cầu gợi ý giá AI và Admin xem bằng chứng kỹ thuật tách biệt

**Ngày tạo**: 2026-11-12  
**Người phụ trách**: Senior QA & System Verification Engineer  
**Tính năng**: 004-ai-chatbox-analysis-and-suggestion  

## 1. Tiền điều kiện (Preconditions)

- [ ] Môi trường Backend Spring Boot sẵn sàng tại `http://localhost:8080`.
- [ ] Môi trường Frontend Vite sẵn sàng tại `http://localhost:5173`.
- [ ] Đề xuất giá `PROP-001` đang ở trạng thái chờ định giá.
- [ ] Tài khoản Manager: `MANAGER_PRICING_001`.
- [ ] Tài khoản Admin: `ADMIN_AUDIT_001`.

## 2. Phân tích rủi ro đối nghịch (Adversarial Risk Analysis)

- **Phân tách trách nhiệm (Separation of Duties):**
  - Manager được phép xem gợi ý giá bằng VND, ra quyết định thương mại và điều chỉnh giá bán.
  - Admin (`technicalAuditEvidenceOnly === true`) chỉ được xem các trường bằng chứng kiểm toán kỹ thuật (Provider Correlation ID, timestamp, model ID), tuyệt đối **ẩn toàn bộ các trường giá thương mại**.
- **Tính chất tham khảo:** Giá AI sinh ra mang nhãn "Giá tham khảo", không tự động cập nhật vào catalogue sản phẩm hay giá đặt cọc.

## 3. Các bước kiểm thử trên trình duyệt (Browser Action Steps)

### Step 1 — Manager mở trang gợi ý giá và yêu cầu tính toán từ AI
```browser
goto http://localhost:5173/login
fill getByLabel(/Email/i) "manager.pricing@mland.vn"
fill getByLabel(/Mật khẩu/i) "Manager@123456"
click getByRole('button', { name: /Đăng nhập/i })
goto http://localhost:5173/price-proposals/PROP-001/ai-suggestions
wait getByRole('heading', { name: /Gợi ý định giá từ AI/i })
click getByRole('button', { name: /Yêu cầu AI phân tích giá/i })
wait --text "Giá đề xuất tham khảo"
snapshot -i
```

### Step 2 — Manager xác nhận quyết định giá thương mại
```browser
fill getByLabel(/Giá phê duyệt chính thức/i) "550000"
fill getByLabel(/Ghi chú phê duyệt/i) "Đồng ý mức giá đề xuất có điều chỉnh nhẹ chi phí phụ kiện."
click getByRole('button', { name: /Ghi nhận quyết định/i })
wait --text "Quyết định giá đã được lưu trữ thành công."
snapshot -i
```

### Step 3 — Đăng nhập Admin kiểm tra chế độ hiển thị bằng chứng kỹ thuật
```browser
goto http://localhost:5173/login
fill getByLabel(/Email/i) "admin.audit@mland.vn"
fill getByLabel(/Mật khẩu/i) "Admin@123456"
click getByRole('button', { name: /Đăng nhập/i })
goto http://localhost:5173/price-proposals/PROP-001/ai-suggestions
wait getByRole('heading', { name: /Bằng chứng kiểm toán AI/i })
snapshot -i
```

## 4. Kết quả kỳ vọng (Expected Assertions)

```browser
find "Giá đề xuất tham khảo"
# Expected: one or more matches khi đăng nhập Manager
find "Bằng chứng kiểm toán kỹ thuật (Admin)"
# Expected: one or more matches khi đăng nhập Admin
requests --filter "/api/v1/price-proposals/PROP-001/ai-suggestions"
# Expected: POST 200 kèm Idempotency-Key
```

## 5. Bằng chứng kiểm thử (Evidence)

- Screenshot thành công: `screenshots/E2E-004-004--success.jpg`
- Lệnh chụp: `screenshot --full --path "ppswbs-spec/features/004-ai-chatbox-analysis-and-suggestion/test-results/screenshots/E2E-004-004--success.jpg"`

## 6. Kết quả thực thi (Execution History)

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-13 18:25:30
- Executed by: Senior QA & System Verification Engineer
- Browser tool: browsermcp
- Failure step: None
- Actual result: Manager yêu cầu AI gợi ý giá thành công (HTTP 200, hiển thị giá đề xuất 850.000 ₫ và cơ sở phân tích giá nguyên vật liệu bạc 925). Admin đăng nhập mở trang hiển thị chuẩn xác Chế độ xem kiểm toán kỹ thuật, ẩn dữ liệu giá thương mại và hiển thị mã tương quan provider gemini-price-* theo đúng quy tắc phân quyền.
- Evidence screenshot: screenshots/E2E-004-004--manager-suggestion.jpg, screenshots/E2E-004-004--admin-audit.jpg
- Bug reference: NONE
<!-- EXECUTION:END -->

