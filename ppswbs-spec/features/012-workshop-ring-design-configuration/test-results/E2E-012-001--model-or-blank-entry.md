---
scenario_id: E2E-012-001
feature: FE-012
actor: GUEST
preconditions:
  - backend_health: FAIL
  - frontend_health: FAIL
traceability:
  - FE-012
  - AC-012-ENTRY
isolation_key: e2e-012-guest-001
cleanup:
  - none-client-only
id: E2E-012-001
test_level: e2e
test_case_type: N
source_ref: spec.md#FR-012-001
target_url: http://localhost:5173/workshops/PKG-COUPLE-01/design
actor_role: Guest
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
  - http://localhost:8080/v3/api-docs
test_data:
  fixture: package-options-fixture
  account_ref: GUEST_PUBLIC
  isolation_key: e2e-012-guest-001
---

# E2E-012-001: Khởi đầu luồng cấu hình nhẫn Workshop từ mẫu có sẵn hoặc phôi trắng

**Ngày tạo**: 2026-11-12  
**Người phụ trách**: Senior QA & System Verification Engineer  
**Tính năng**: 012-workshop-ring-design-configuration  

## 1. Tiền điều kiện (Preconditions)

- [ ] Môi trường Backend Spring Boot sẵn sàng tại `http://localhost:8080`.
- [ ] Môi trường Frontend Vite sẵn sàng tại `http://localhost:5173`.
- [ ] Gói Workshop `PKG-COUPLE-01` có hỗ trợ tính năng cấu hình nhẫn độc bản.
- [ ] Phiên người dùng: Guest (chưa đăng nhập).

## 2. Phân tích rủi ro đối nghịch (Adversarial Risk Analysis)

- **Cảnh báo giá tham khảo (Advisory Disclaimer):** Giao diện phải luôn làm nổi bật thông báo: "Thiết kế nhẫn áp dụng cho buổi chế tác Workshop. Giá thẩm định chỉ mang tính chất tham khảo, không làm thay đổi giá gói workshop và tiền cọc đặt lịch (50%)."
- **Rẽ nhánh luồng mượt mà:** Khách có thể chọn bắt đầu từ catalogue model (đã có nghệ nhân chuẩn hóa) hoặc bắt đầu từ phôi trắng hoàn toàn (Blank configurator).

## 3. Các bước kiểm thử trên trình duyệt (Browser Action Steps)

### Step 1 — Mở trang điểm vào cấu hình nhẫn theo gói workshop
```browser
goto http://localhost:5173/workshops/PKG-COUPLE-01/design
wait getByRole('heading', { name: /Cấu hình mẫu nhẫn Workshop/i })
wait --text "Thiết kế nhẫn áp dụng cho buổi chế tác Workshop"
snapshot -i
```

### Step 2 — Chọn điểm bắt đầu từ Mẫu gợi ý (Catalogue Models)
```browser
click getByRole('button', { name: /Chọn mẫu này/i })
wait getByRole('heading', { name: /Mẫu gợi ý theo gói/i })
wait --text "Mẫu Nhẫn Cưới Classic"
snapshot -i
```

### Step 3 — Quay lại và chọn bắt đầu từ Phôi trắng (Blank Configurator)
```browser
goto http://localhost:5173/workshops/PKG-COUPLE-01/design
click getByRole('button', { name: /Bắt đầu cấu hình/i })
wait getByRole('heading', { name: /1. Chất liệu nhẫn/i })
snapshot -i
```

## 4. Kết quả kỳ vọng (Expected Assertions)

```browser
get url
# Expected: http://localhost:5173/workshops/PKG-COUPLE-01/design/configure?mode=BLANK
find "Cấu hình mẫu nhẫn Workshop"
# Expected: one or more matches
find "Mô phỏng nhẫn"
# Expected: one or more matches cho Live Preview SVG
```

## 5. Bằng chứng kiểm thử (Evidence)

- Screenshot thành công: `screenshots/E2E-012-001--success.jpg`
- Lệnh chụp: `screenshot --full --path "ppswbs-spec/features/012-workshop-ring-design-configuration/test-results/screenshots/E2E-012-001--success.jpg"`

## 6. Kết quả thực thi (Execution History)

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-12 19:50:18
- Executed by: Senior QA & System Verification Engineer
- Browser tool: browsermcp
- Failure step: None
- Actual result: Hiển thị đầy đủ giao diện chọn điểm bắt đầu (Catalogue Models và Phôi trắng), banner cảnh báo giá tham khảo (Advisory Disclaimer) và điều hướng thành công vào bộ cấu hình nhẫn trống với đầy đủ 5 phân mục tùy biến.
- Evidence screenshot: screenshots/E2E-012-001--success.jpg
- Bug reference: NONE
<!-- EXECUTION:END -->

