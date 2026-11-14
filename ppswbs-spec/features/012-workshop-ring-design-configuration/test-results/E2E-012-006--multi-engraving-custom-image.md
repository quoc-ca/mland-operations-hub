---
scenario_id: E2E-012-006
feature: FE-012
actor: GUEST_OR_MEMBER
preconditions:
  - backend_health: PASS
  - frontend_health: PASS
traceability:
  - FE-012
  - AC-012-MULTI-ENGRAVING
  - AC-012-CUSTOM-IMAGE-CROPPING
  - AC-012-TEXTURED-SURFACE-WARNING
isolation_key: e2e-012-engraving-custom-image
cleanup:
  - delete_draft
id: E2E-012-006
test_level: e2e
test_case_type: N
source_ref: spec.md#3.2.8
target_url: http://localhost:5173/workshops/PKG-COUPLE-SILVER/design/configure?mode=BLANK
actor_role: Guest / Member
browser_tool: browsermcp
health_urls:
  - http://localhost:5173
  - http://localhost:8080/api/v1/workshop-design-options
test_data:
  package_ref: PKG-COUPLE-SILVER
  shape_ref: SHP-BALL-PEEN
  material_ref: MAT-SILVER-925
  standard_engraving_fee: 80000
  custom_image_price: 100000
---

# E2E-012-006: Khắc nhiều vị trí tính phí 1 lần, Upload ảnh khắc riêng (100k) & Cảnh báo bề mặt gồ ghề

**Ngày tạo**: 2026-11-14  
**Người phụ trách**: Ngô Tiến Đạt (Senior QA & System Verification Engineer)  
**Tính năng**: 012-workshop-ring-design-configuration  

## 1. Tiền điều kiện (Preconditions)

- [x] Môi trường Backend Spring Boot sẵn sàng tại `http://localhost:8080`.
- [x] Môi trường Frontend Vite sẵn sàng tại `http://localhost:5173`.
- [x] Gói workshop `PKG-COUPLE-SILVER` trả về cấu hình khắc laser hỗ trợ cả `INNER_BAND`, `OUTER_BAND`, phí khắc tiêu chuẩn 80.000 ₫ và phí khắc ảnh tùy chọn 100.000 ₫.

## 2. Phân tích rủi ro đối nghịch (Adversarial Risk Analysis)

1. **Phí khắc tiêu chuẩn chỉ thu 1 lần duy nhất**:
   - Khi khách hàng bật khắc tiêu chuẩn (+80.000 ₫) và thêm 2 hoặc nhiều vị trí khắc (cả trong và ngoài), hệ thống chỉ tính phí khắc tiêu chuẩn đúng 1 lần (+80.000 ₫), không nhân đôi phí.
2. **Cảnh báo xung đột kết cấu bề mặt gồ ghề (Surface Texture Collision Warning)**:
   - Khi có bất kỳ mục khắc nào (chữ hoặc ảnh) chọn vị trí `OUTER_BAND` trên các dáng nhẫn có kết cấu bề mặt gồ ghề (`SHP-BALL-PEEN`, `SHP-HAMMERED`, `SHP-SINGLE-TWIST`, `SHP-TWISTED`, `SHP-WAVE`), giao diện và backend rules phải hiển thị cảnh báo kỹ thuật rõ ràng để khách hàng biết hình khắc mặt ngoài có thể không sắc nét hoặc bị biến dạng.
3. **Upload ảnh khắc laser & Khung chọn 160px x 160px**:
   - Tải file ảnh lên phải được chuyển đổi tức thì thành ảnh đơn sắc trắng đen (Monochrome / B&W thresholding).
   - Khung chọn cắt phải bị giới hạn đúng kích thước 160px x 160px, hỗ trợ kéo thả trực tiếp để chọn góc khắc.
   - Mỗi ảnh upload được cộng thêm đúng 100.000 ₫ vào chi phí ước tính.

## 3. Các bước kiểm thử trên trình duyệt (Browser Action Steps)

### Step 1 — Mở trang cấu hình nhẫn và chọn dáng Búa bi (`SHP-BALL-PEEN`)
```browser
goto http://localhost:5173/workshops/PKG-COUPLE-SILVER/design/configure?mode=BLANK
click getByTestId("shape-card-SHP-BALL-PEEN")
wait 500
```

### Step 2 — Bật khắc tiêu chuẩn, nhập nội dung khắc và chọn Mặt ngoài nhẫn
```browser
check getByTestId("engraving-toggle")
fill getByTestId("engraving-input") "MLAND 2026♥"
click getByTestId("placement-outer")
wait 500
```
*Xác minh*: Banner cảnh báo `surface-texture-warning` xuất hiện:
> "⚠️ Lưu ý: Đai nhẫn đang chọn có kết cấu bề mặt gồ ghề (đập búa / búa bi / xoắn / lượn sóng)..."

### Step 3 — Thêm vị trí khắc thứ 2 ở Mặt trong nhẫn
```browser
click getByTestId("add-engraving-item-btn")
fill getByTestId("engraving-input-1") "Forever & Always"
click getByTestId("placement-inner-1")
wait 300
```
*Xác minh*: Bảng tóm tắt chỉ tính phí khắc tiêu chuẩn 1 lần (+80.000 đ) cho cả 2 nội dung khắc.

### Step 4 — Upload file ảnh tùy chọn, di chuyển khung chọn 160px x 160px
```browser
upload getByTestId("upload-engraving-image-input") "emblem.png"
fill getByLabel("X:") "40"
fill getByLabel("Y:") "50"
wait 500
```
*Xác minh*:
- Hiển thị khung 160x160 với tọa độ X: 40px, Y: 50px.
- Hiển thị ô xem trước khắc laser (Trắng - Đen 160px x 160px).
- Bảng tóm tắt ghi nhận: `Ảnh khắc laser: 1 ảnh (+100.000 đ)`.

### Step 5 — Nhấn Lưu & Thẩm định, kiểm tra trang kết quả thẩm định
```browser
click getByTestId("save-and-evaluate-btn")
wait 2500
```

## 4. Bằng chứng kiểm thử (Evidence)

- **Ảnh chụp màn hình trang cấu hình**: `screenshots/E2E-012-006--configurator-engraving.jpg`
- **Ảnh chụp màn hình trang thẩm định**: `screenshots/E2E-012-006--evaluation-multi-engraving.jpg`

## 5. Kết quả thực thi (Execution History)

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-14 13:01:45
- Executed by: Ngô Tiến Đạt (Senior QA & System Verification Engineer)
- Browser tool: browsermcp
- Failure step: None
- Actual result:
  1. Trả tiền khắc 1 lần (+80.000 ₫) và khắc được nhiều nội dung cả mặt trong (`Forever & Always`) và mặt ngoài (`MLAND 2026♥`).
  2. Cảnh báo bề mặt gồ ghề hiển thị nổi bật trên cả trang cấu hình và trang thẩm định:
     *"Lưu ý: Mặt ngoài đai nhẫn có thiết kế kết cấu bề mặt gồ ghề (đập búa / búa bi / xoắn / lượn sóng). Hình khắc ở mặt ngoài có thể không đạt độ sắc nét hoặc bị biến dạng so với mong muốn."*
  3. Upload ảnh khắc thành công, chuyển đổi trắng đen tương phản cao (monochrome thresholding) chuẩn laser, khung chọn 160px x 160px di chuyển chính xác và hiển thị ô xem trước trực quan.
  4. Chi phí thẩm định được tính toán chuẩn xác tuyệt đối theo toán học:
     - Giá phôi bạc cơ sở: 250.000 ₫
     - Phụ phí khối lượng vượt mức (0.50g @ 120k/g): +60.000 ₫
     - Dáng nhẫn búa bi (`SHP-BALL-PEEN`): +90.000 ₫
     - Phí khắc tiêu chuẩn 1 lần: +80.000 ₫
     - 1 ảnh khắc riêng: +100.000 ₫
     - **Tổng chi phí ước tính**: **580.000 ₫** (khớp hoàn toàn giữa Frontend và Backend).
- Evidence screenshots:
  - screenshots/E2E-012-006--configurator-engraving.jpg
  - screenshots/E2E-012-006--evaluation-multi-engraving.jpg
- Bug reference: NONE
<!-- EXECUTION:END -->

