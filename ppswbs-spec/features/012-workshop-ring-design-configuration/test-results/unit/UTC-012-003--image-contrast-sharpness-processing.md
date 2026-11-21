---
id: UTC-012-003
feature: 012-workshop-ring-design-configuration
test_level: unit
test_case_type: N
source_ref: spec.md#4A.3
source_class: edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.dto.CustomEngravingImageDto
source_method: getProcessingParameters
test_class: edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.EngravingImageTest
test_method: shouldPersistContrastAndSharpnessSettings
---

# UTC-012-003: Xử lý tiền xử lý ảnh khắc laser (Độ tương phản và Độ sắc nét)

**Ngày tạo**: 2026-11-21
**Tác giả**: Senior QA Engineer
**Trạng thái**: PASS

## 1. Thiết kế kiểm thử

- Mã / Tên hàm: Tiền xử lý ảnh khắc laser
- Yêu cầu nghiệp vụ: Hình ảnh khắc laser sau khi upload được chuyển sang đen trắng, đóng khung 160px x 160px, hỗ trợ thanh trượt điều chỉnh Độ tương phản (Contrast) và Độ sắc nét (Sharpness). Khi lưu thiết kế, các tham số này phải được lưu đầy đủ để khôi phục chính xác khi xem lại.
- Loại ca kiểm thử: Normal (N)

## 2. Tiền điều kiện và Dữ liệu kiểm thử

- File ảnh khắc chữ ký đen trắng.
- Thanh trượt:
  - Contrast: giá trị `1.2` (mặc định 1.0)
  - Sharpness: giá trị `1.5` (mặc định 1.0)
  - Crop box: `{x: 20, y: 30, width: 160, height: 160}`

## 3. Các bước thực hiện và Kỳ vọng

1. Tải ảnh lên mục khắc laser, chọn vùng đóng khung 160px x 160px.
2. Kéo thanh trượt điều chỉnh Contrast = 1.2, Sharpness = 1.5.
3. Nhấn lưu thiết kế.
4. Mở lại mẫu thiết kế đã lưu:
   - Preview khôi phục chính xác hình ảnh đã qua xử lý.
   - Các giá trị contrast, sharpness và crop box được nạp lại đầy đủ, không bị reset về mặc định.

## 4. Kết quả thực thi

<!-- EXECUTION:START -->
- Trạng thái: PASS
- Thực thi bởi: Senior QA & Verification Engineer
- Ngày thực thi: 2026-11-21
- Ghi chú: Đã kiểm tra cơ chế filter grayscale, contrast và khôi phục trong `CustomEngravingImageDto`.
<!-- EXECUTION:END -->
