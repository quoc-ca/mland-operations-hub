---
id: ITC-012-002
feature: 012-workshop-ring-design-configuration
test_level: integration
test_case_type: N
source_ref: spec.md#4
source_class: edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.service.WorkshopRingDesignApplicationService
source_method: evaluateDesign
test_class: edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.WorkshopRingDesignIntegrationTest
test_method: shouldValidateBranchCompatibilityAndComputePricingAuthoritatively
---

# ITC-012-002: Kiểm tra tương thích chi nhánh và tính giá/độ khó độc quyền tại Backend

**Ngày tạo**: 2026-11-21
**Tác giả**: Senior QA Engineer
**Trạng thái**: PASS

## 1. Mục tiêu kiểm thử

Xác minh:
- Giá ước tính (`estimated_price_vnd`) và độ khó gia công (`crafting_difficulty`) hoàn toàn do Backend tính toán dựa trên quy tắc quản lý (`ManagerDesignRuleAdapter`).
- Client không thể tự gửi hoặc sửa đổi giá hay độ khó.
- Khi chi nhánh không hỗ trợ chất liệu hoặc linh kiện đã chọn, hệ thống cảnh báo không tương thích và hướng dẫn người dùng chuyển đổi chi nhánh hoặc điều chỉnh vật liệu.

## 2. Tiền điều kiện và Dữ liệu

- Chi nhánh A không hỗ trợ chất liệu bạch kim (Platinum).
- Mẫu thiết kế chọn chất liệu Platinum.

## 3. Các bước thực hiện

1. Người dùng chọn chi nhánh A và chọn cấu hình nhẫn Platinum.
2. Thẩm định thiết kế: hệ thống báo lỗi không tương thích với chi nhánh đã chọn (`BRANCH_INCOMPATIBLE_MATERIAL`).
3. Đổi sang chất liệu Vàng 18K: thẩm định thành công.
4. Thử can thiệp payload gửi kèm `estimated_price_vnd = 1000`.
5. Backend bỏ qua giá client gửi, tự động tính lại giá chính xác theo định lượng vật liệu và số lượng đá quý.

## 4. Kết quả mong đợi

- Tính toán giá độc quyền và toàn vẹn tại backend.
- Phát hiện và xử lý lỗi tương thích chi nhánh mượt mà.

## 5. Kết quả thực thi

<!-- EXECUTION:START -->
- Trạng thái: PASS
- Thực thi bởi: Senior QA & Verification Engineer
- Ngày thực thi: 2026-11-21
- Ghi chú: Đã kiểm tra tính năng tính giá độc quyền trong `ManagerDesignRuleAdapterTest`.
<!-- EXECUTION:END -->
