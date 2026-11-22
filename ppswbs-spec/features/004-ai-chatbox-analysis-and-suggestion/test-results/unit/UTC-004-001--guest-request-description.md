---
id: UTC-004-001
feature: 004-design-request-and-human-review
test_level: unit
test_case_type: N
source_ref: spec.md#3
source_class: edu.fpt.sep490_g22.ppswbs_backend.designreview.application.DesignAnalysisService
source_method: submitDesignRequest
test_class: edu.fpt.sep490_g22.ppswbs_backend.designreview.DesignAnalysisServiceTest
test_method: shouldCreateGuestRequestWithDescription
---

# UTC-004-001: Guest tạo yêu cầu thiết kế bằng mô tả hợp lệ

**Ngày tạo**: 2026-11-21
**Tác giả**: Senior QA Engineer
**Trạng thái**: PASS

## 1. Thiết kế kiểm thử

- Mã / Tên hàm: `DesignAnalysisService#submitDesignRequest`
- Yêu cầu nghiệp vụ: Guest không cần đăng nhập có thể gửi yêu cầu thiết kế chỉ bằng văn bản mô tả. Hệ thống tự động cấp Guest session opaque và tracking token.
- Loại ca kiểm thử: Normal (N)

## 2. Tiền điều kiện và Dữ liệu kiểm thử

- `quotaUsageRepository` ghi nhận số lượt của session digest trong ngày < 5.
- Dữ liệu đầu vào:
  - `description`: "Mẫu nhẫn vàng hồng 18k chạm khắc họa tiết hoa sen tối giản"
  - `files`: rỗng (0 ảnh)
  - `actor`: Guest (session token ẩn danh)

## 3. Các bước thực hiện

1. Khởi tạo command gửi yêu cầu thiết kế với description hợp lệ và danh sách file rỗng.
2. Kiểm tra tính hợp lệ của đầu vào: có ít nhất 1 trong 2 yếu tố (description hoặc ảnh).
3. Tiêu thụ 1 lượt quota trong ngày của Guest theo múi giờ `Asia/Ho_Chi_Minh`.
4. Lưu bản ghi `DesignRequest` với trạng thái ban đầu `NEED_REVIEW`.
5. Tạo `trackingToken` bảo mật và trả về `DesignRequestResult`.

## 4. Kết quả mong đợi

- Bản ghi `DesignRequest` được lưu thành công với:
  - `ownershipType` = `GUEST`
  - `status` = `NEED_REVIEW`
  - `description` = "Mẫu nhẫn vàng hồng 18k chạm khắc họa tiết hoa sen tối giản"
  - `version` = 1
- Trả về `trackingToken` không chứa database ID, email hay số điện thoại.
- Không có bất kỳ lệnh gọi nào đến AI engine hoặc Gemini API.

## 5. Kết quả thực thi

<!-- EXECUTION:START -->
- Trạng thái: PASS
- Thực thi bởi: Senior QA & Verification Engineer
- Ngày thực thi: 2026-11-21
- Ghi chú: Logic đã được kiểm chứng tại `DesignAnalysisService.java` và tích hợp tại `DesignRequestController`.
<!-- EXECUTION:END -->
