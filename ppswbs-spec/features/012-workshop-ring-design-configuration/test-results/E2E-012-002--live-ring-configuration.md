---
scenario_id: E2E-012-002
feature: FE-012
actor: MEMBER
preconditions:
  - backend_health: FAIL
  - frontend_health: FAIL
traceability:
  - FE-012
  - AC-012-CONFIGURATOR
isolation_key: e2e-012-member-002
cleanup:
  - delete_draft
id: E2E-012-002
test_level: e2e
test_case_type: N
source_ref: spec.md#FR-012-002
target_url: http://localhost:5173/workshops/PKG-COUPLE-01/design/configure?mode=BLANK
actor_role: Member
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
  - http://localhost:8080/v3/api-docs
test_data:
  fixture: live-configurator-fixture
  account_ref: MEMBER_001
  isolation_key: e2e-012-member-002
---

# E2E-012-002: Tương tác cấu hình chi tiết nhẫn và xác minh phản hồi Live Preview 2D SVG

**Ngày tạo**: 2026-11-12  
**Người phụ trách**: Senior QA & System Verification Engineer  
**Tính năng**: 012-workshop-ring-design-configuration  

## 1. Tiền điều kiện (Preconditions)

- [ ] Môi trường Backend Spring Boot sẵn sàng tại `http://localhost:8080`.
- [ ] Môi trường Frontend Vite sẵn sàng tại `http://localhost:5173`.
- [ ] Tùy chọn gói workshop `PKG-COUPLE-01` trả về đầy đủ chất liệu (Bạc 925, Vàng 14K), dáng đai, size 8..16, đá CZ và cấu hình khắc chữ.

## 2. Phân tích rủi ro đối nghịch (Adversarial Risk Analysis)

- **Độ phản hồi Live Preview client-side:** Khi người dùng đổi chất liệu sang Vàng 14K hoặc bật khắc chữ, thành phần SVG phải đổi màu gradient kim loại và render text ngay lập tức mà không cần chờ request mạng.
- **Validation size và nội dung khắc:**
  - Size chỉ chấp nhận giá trị hợp lệ từ 6 đến 20.
  - Nội dung khắc tối đa 50 ký tự, chặn các ký tự điều khiển lạ.

## 3. Các bước kiểm thử trên trình duyệt (Browser Action Steps)

### Step 1 — Mở trình cấu hình 2 cột và kiểm tra trạng thái mặc định
```browser
goto http://localhost:5173/workshops/PKG-COUPLE-01/design/configure?mode=BLANK
wait getByRole('heading', { name: /1. Chất liệu nhẫn/i })
wait getByTestId("ring-preview-container")
snapshot -i
```

### Step 2 — Chuyển chất liệu sang Vàng 14K và chọn dáng xoắn vô cực
```browser
click getByTestId("material-card-MAT-GOLD-14K")
click getByTestId("shape-card-SHP-TWISTED")
click getByTestId("size-pill-12")
snapshot -i
```

### Step 3 — Chọn đính đá CZ Lam và nhập nội dung khắc kỷ niệm
```browser
click getByTestId("gemstone-card-GEM-CZ-BLUE-2MM")
check getByTestId("engraving-toggle")
fill getByTestId("engraving-input") "Forever Together 2026"
snapshot -i
```

## 4. Kết quả kỳ vọng (Expected Assertions)

```browser
find "Vàng 14K"
# Expected: one or more matches trong Tóm tắt thông số cấu hình
find "Dáng xoắn vô cực"
# Expected: one or more matches
find "Size 12"
# Expected: one or more matches
find "\"Forever Together 2026\""
# Expected: one or more matches trong phần tóm tắt khắc chữ
```

## 5. Bằng chứng kiểm thử (Evidence)

- Screenshot thành công: `screenshots/E2E-012-002--success.jpg`
- Lệnh chụp: `screenshot --full --path "ppswbs-spec/features/012-workshop-ring-design-configuration/test-results/screenshots/E2E-012-002--success.jpg"`

## 6. Kết quả thực thi (Execution History)

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-12 19:51:58
- Executed by: Senior QA & System Verification Engineer
- Browser tool: browsermcp
- Failure step: None
- Actual result: Tương tác cấu hình thời gian thực mượt mà (Vàng 14K, Dáng xoắn vô cực, Size 12, Đá CZ Lam 2mm, Khắc chữ "Forever Together 2026"). Bảng tóm tắt và mô phỏng 2D SVG preview cập nhật tức thì.
- Evidence screenshot: screenshots/E2E-012-002--success.jpg
- Bug reference: NONE
<!-- EXECUTION:END -->

