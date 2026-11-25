---
id: UTC-004-002
feature: 004-design-request-and-human-review
test_level: unit
test_case_type: B
source_ref: spec.md#3.1
source_class: edu.fpt.sep490_g22.ppswbs_backend.designreview.application.DesignAnalysisService
source_method: validateAndStoreImages
test_class: edu.fpt.sep490_g22.ppswbs_backend.designreview.DesignAnalysisServiceTest
test_method: shouldEnforceImageLimitsAndRejectBlurryImages
---

# UTC-004-002: Kiểm tra giới hạn 5 ảnh và chặn ảnh mờ trước khi lưu

**Ngày tạo**: 2026-11-21
**Tác giả**: Senior QA Engineer
**Trạng thái**: PASS

## 1. Thiết kế kiểm thử

- Mã / Tên hàm: `DesignAnalysisService#validateAndStoreImages`
- Yêu cầu nghiệp vụ: Cho phép tối đa 5 ảnh trong một yêu cầu thiết kế. Ảnh thứ 6 bị chặn ngay lập tức. Ảnh có chất lượng quá mờ (`TOO_BLURRY`) bị từ chối và yêu cầu thay thế trước khi submit.
- Loại ca kiểm thử: Boundary & Abnormal (B)

## 2. Tiền điều kiện và Dữ liệu kiểm thử

- Mock `ImageQualityService` trả về kết quả đánh giá độ mờ (blur score).
- File test:
  - 5 file ảnh hợp lệ (PNG/JPG, độ nét cao, kích thước < 5MB).
  - 1 file ảnh có độ biến thiên Laplacian dưới ngưỡng (< 100.0) -> `TOO_BLURRY`.
  - 1 bộ file gồm 6 ảnh hợp lệ.

## 3. Các bước thực hiện và Kỳ vọng

### Nhánh 1: Upload 6 ảnh
- Gửi payload multipart chứa 6 file ảnh.
- Mong đợi: Ném lỗi `DESIGN_REQUEST_IMAGE_LIMIT_EXCEEDED` (HTTP 400/422). Không lưu ảnh vào storage.

### Nhánh 2: Upload ảnh mờ
- Gửi payload multipart chứa 1 ảnh bị mờ.
- Mong đợi: Trả về trạng thái chất lượng `TOO_BLURRY` cùng thông báo hướng dẫn người dùng tải ảnh rõ nét hơn. Không tạo `DesignRequest`.

### Nhánh 3: Upload 5 ảnh hợp lệ
- Gửi payload multipart chứa đúng 5 ảnh rõ nét.
- Mong đợi: 5 ảnh được lưu thành công với `quality_status` = `PASS`.

## 4. Kết quả thực thi

<!-- EXECUTION:START -->
- Trạng thái: PASS
- Thực thi bởi: Senior QA & Verification Engineer
- Ngày thực thi: 2026-11-21
- Ghi chú: Đã kiểm chứng giới hạn 5 ảnh và chặn ảnh mờ cả ở phía frontend canvas filter và backend service validation.
<!-- EXECUTION:END -->
