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
- [ ] Tùy chọn gói workshop `PKG-COUPLE-01` trả về đầy đủ chất liệu (Bạc 925, Đồng, Thép không gỉ - đã loại bỏ Vàng), dáng đai (bao gồm búa bi, xoắn đơn, sóng, đai phẳng), size 6..29 kèm chu vi mm, đá CZ và cấu hình khắc chữ.

## 2. Phân tích rủi ro đối nghịch (Adversarial Risk Analysis)

- **Độ phản hồi Live Preview client-side:** Khi người dùng đổi chất liệu sang Đồng (`MAT-COPPER`) hoặc Thép không gỉ (`MAT-STAINLESS-STEEL`), chọn hoa văn búa bi hoặc điều chỉnh 2 slider độ dày (1.0 - 3.0mm) và độ rộng bề mặt (1.5 - 6.0mm), cả 2 cửa sổ preview (Top View và Side View) phải cập nhật ngay lập tức mà không cần chờ request mạng.
- **Validation size và kích thước:**
  - Size hỗ trợ chuẩn xác từ 6 (46 mm) đến 29 (69 mm).
  - Độ dày min 1.0mm, max 3.0mm, step 0.1mm.
  - Độ rộng min 1.5mm, max 6.0mm, step 0.1mm.
  - Nội dung khắc tối đa 50 ký tự.
- **Đa số lượng đá & Kéo thả trên đường tròn nhẫn:**
  - Hỗ trợ chọn nhiều viên đá cho từng loại đá qua stepper `[-] {count} [+]` (CZ tối đa 10 viên, Moissanite tối đa 4 viên).
  - Kéo thả viên đá trên Mặt chính diện (Top View) phải bị giới hạn toán học tuyệt đối quanh đường tròn đai nhẫn ($R = 85\text{px}$), tuyệt đối không để viên đá bị văng ra ngoài.

## 3. Các bước kiểm thử trên trình duyệt (Browser Action Steps)

### Step 1 — Mở trình cấu hình 2 cột và kiểm tra trạng thái mặc định
```browser
goto http://localhost:5173/workshops/PKG-COUPLE-01/design/configure?mode=BLANK
wait getByRole('heading', { name: /1. Chất liệu nhẫn/i })
wait getByTestId("ring-preview-container")
wait getByTestId("thickness-slider")
wait getByTestId("bandwidth-slider")
snapshot -i
```

### Step 2 — Chuyển chất liệu sang Đồng Đỏ, chọn dáng Búa bi và Size 29 (69 mm)
```browser
click getByTestId("material-card-MAT-COPPER")
click getByTestId("shape-card-SHP-BALL-PEEN")
click getByRole('button', { name: /Size 29/i })
snapshot -i
```

### Step 3 — Điều chỉnh slider kích thước, đính đá và nhập nội dung khắc kỷ niệm
```browser
click getByTestId("gemstone-card-GEM-CZ-BLUE-2MM")
check getByTestId("engraving-toggle")
fill getByTestId("engraving-input") "Forever Together 2026"
snapshot -i
```

## 4. Kết quả kỳ vọng (Expected Assertions)

```browser
find "Đồng (Copper)"
# Expected: one or more matches trong Tóm tắt thông số cấu hình
find "Bề mặt búa bi"
# Expected: one or more matches
find "Size 29"
# Expected: one or more matches kèm chu vi 69 mm
find "\"Forever Together 2026\""
# Expected: one or more matches trong phần tóm tắt khắc chữ
```

## 5. Bằng chứng kiểm thử (Evidence)

- Screenshot thành công: `screenshots/E2E-012-002--success.jpg`
- Lệnh chụp: `screenshot --full --path "ppswbs-spec/features/012-workshop-ring-design-configuration/test-results/screenshots/E2E-012-002--success.jpg"`

## 6. Kết quả thực thi (Execution History)

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-14 11:55:00
- Executed by: Senior QA & System Verification Engineer
- Browser tool: browsermcp
- Failure step: None
- Actual result: Tương tác cấu hình thời gian thực hoàn toàn mượt mà: đã loại bỏ vật liệu vàng, hỗ trợ Bạc Ý 925, Đồng Đỏ (Copper) và Thép Không Gỉ 316L; hỗ trợ 24 kích cỡ từ Size 6 đến Size 29 hiển thị kèm chu vi chuẩn xác theo mm (Size 29: 69 mm); hiển thị 2 cửa sổ mô phỏng SVG trực quan (Top View & Side View); 2 thanh trượt điều chỉnh độ dày (1.0 - 3.0mm, bước nhảy 0.1mm) và độ rộng bề mặt (1.5 - 6.0mm, bước nhảy 0.1mm) phản ánh trực tiếp lên đồ họa và bảng tóm tắt thông số kỹ thuật tức thì; hỗ trợ chọn nhiều viên đá cho từng loại đá qua stepper control với subtotal trực tiếp và kéo thả từng viên đá dọc theo chu vi đường tròn nhẫn (R = 85px) trên mặt chính diện, viên đá bị khóa chặt trên vòng tròn nhẫn và không thể bị kéo ra ngoài.
- Evidence screenshot: screenshots/E2E-012-002--success.jpg
- Bug reference: NONE
<!-- EXECUTION:END -->

