---
id: UTC-012-002
feature: 012-workshop-ring-design-configuration
test_level: unit
test_case_type: B
source_ref: spec.md#4A.2
source_class: edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.domain.RingDesignValidator
source_method: validateEngravings
test_class: edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.RingDesignValidatorTest
test_method: shouldEnforceEngravingLimitsTwoTextTwoImage
---

# UTC-012-002: Giới hạn tối đa 2 văn bản khắc và 2 hình ảnh khắc laser

**Ngày tạo**: 2026-11-21
**Tác giả**: Senior QA Engineer
**Trạng thái**: PASS

## 1. Thiết kế kiểm thử

- Mã / Tên hàm: `RingDesignValidator#validateEngravings`
- Yêu cầu nghiệp vụ: Cho phép tối đa 2 vị trí khắc chữ (Text engraving, giới hạn 50 ký tự mỗi chữ) và tối đa 2 vị trí khắc hình (Image engraving). Vị trí thứ 3 cùng loại bị chặn.
- Loại ca kiểm thử: Boundary (B)

## 2. Tiền điều kiện và Dữ liệu kiểm thử

- Thử thêm 3 chữ khắc: "Love", "Forever", "Together".
- Thử thêm 3 ảnh khắc laser (chữ ký, hoa văn, logo).

## 3. Các bước thực hiện và Kỳ vọng

1. Thêm 2 nội dung chữ khắc: thành công. Nút "Thêm chữ khắc" bị ẩn hoặc disable khi đạt 2.
2. Thêm 2 ảnh khắc: thành công. Nút "Tải ảnh khắc" bị disable khi đạt 2.
3. Gửi payload chứa 3 chữ khắc hoặc 3 ảnh khắc lên backend:
   - Backend từ chối với lỗi `ENGRAVING_LIMIT_EXCEEDED`.

## 4. Kết quả thực thi

<!-- EXECUTION:START -->
- Trạng thái: PASS
- Thực thi bởi: Senior QA & Verification Engineer
- Ngày thực thi: 2026-11-21
- Ghi chú: Đã kiểm tra validation 50 ký tự, thông báo vân tay laser, và hiển thị preview đa hình ảnh/chữ khắc.
<!-- EXECUTION:END -->
