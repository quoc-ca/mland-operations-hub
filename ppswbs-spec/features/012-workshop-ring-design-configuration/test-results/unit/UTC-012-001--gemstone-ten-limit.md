---
id: UTC-012-001
feature: 012-workshop-ring-design-configuration
test_level: unit
test_case_type: B
source_ref: spec.md#4A.1
source_class: edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.domain.RingDesignValidator
source_method: validateGemstoneCount
test_class: edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.RingDesignValidatorTest
test_method: shouldEnforceGemstoneLimitOfTen
---

# UTC-012-001: Giới hạn tối đa 10 viên đá quý trên cấu hình nhẫn (Viên thứ 11 bị chặn)

**Ngày tạo**: 2026-11-21
**Tác giả**: Senior QA Engineer
**Trạng thái**: PASS

## 1. Thiết kế kiểm thử

- Mã / Tên hàm: `RingDesignValidator#validateGemstoneCount`
- Yêu cầu nghiệp vụ: Tổng số viên đá quý trên 1 mẫu nhẫn tối đa là 10 viên. Khi đạt 10 viên, nút thêm đá và nút tăng số lượng bị vô hiệu hóa. Nếu client cố tình gửi payload chứa 11 viên đá, backend phải từ chối với lỗi `GEMSTONE_TOTAL_LIMIT_EXCEEDED` (HTTP 422).
- Loại ca kiểm thử: Boundary (B)

## 2. Tiền điều kiện và Dữ liệu kiểm thử

- Mẫu thiết kế nhẫn đang có 10 viên đá (ví dụ: 1 đá chủ kim cương 5.0mm + 9 đá phụ 1.5mm).
- Thử thêm tiếp 1 viên đá phụ vào cấu hình.

## 3. Các bước thực hiện và Kỳ vọng

1. Đưa tổng số lượng đá quý lên 10 viên.
2. Kiểm tra cờ giao diện: thông báo đạt giới hạn 10 viên đá hiển thị; các nút thêm đá bị disable.
3. Gửi payload lưu thiết kế với 11 viên đá lên backend.
4. Backend ném lỗi `RingDesignValidationException("GEMSTONE_TOTAL_LIMIT_EXCEEDED")`.

## 4. Kết quả thực thi

<!-- EXECUTION:START -->
- Trạng thái: PASS
- Thực thi bởi: Senior QA & Verification Engineer
- Ngày thực thi: 2026-11-21
- Ghi chú: Đã kiểm tra logic cả ở Frontend `designDomain.js` và Backend validator `ManagerDesignRuleAdapter`.
<!-- EXECUTION:END -->
