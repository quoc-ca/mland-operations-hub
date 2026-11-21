---
id: UTC-004-004
feature: 004-design-request-and-human-review
test_level: unit
test_case_type: N
source_ref: spec.md#4
source_class: edu.fpt.sep490_g22.ppswbs_backend.designreview.application.DesignAnalysisService
source_method: decideDesignRequest
test_class: edu.fpt.sep490_g22.ppswbs_backend.designreview.DesignAnalysisServiceTest
test_method: shouldHandleStaffAndManagerReviewDecisions
---

# UTC-004-004: Xử lý quyết định phê duyệt, từ chối và yêu cầu làm rõ (Clarification)

**Ngày tạo**: 2026-11-21
**Tác giả**: Senior QA Engineer
**Trạng thái**: PASS

## 1. Thiết kế kiểm thử

- Mã / Tên hàm: `DesignAnalysisService#decideDesignRequest`
- Yêu cầu nghiệp vụ: Staff hoặc Manager đưa ra quyết định `ACCEPTED`, `REJECTED`, hoặc `CLARIFICATION_REQUIRED`. Bắt buộc phải có văn bản giải thích. Khi yêu cầu làm rõ, Member gửi revision mới và request quay trở lại `NEED_REVIEW`.
- Loại ca kiểm thử: Normal & Negative (N)

## 2. Tiền điều kiện và Dữ liệu kiểm thử

- Request `DR-TEST-001` đang ở trạng thái `NEED_REVIEW` với `version = 1`.
- Reviewer có vai trò `STAFF` hoặc `MANAGER`.
- `Admin` không có quyền thực hiện thẩm định nghiệp vụ (bị chặn HTTP 403).

## 3. Các bước thực hiện và Kỳ vọng

### Nhánh 1: Quyết định không có lý do giải thích
- Gửi payload quyết định `decision` = `REJECTED` nhưng `reason` rỗng.
- Mong đợi: Bị từ chối với lỗi `DESIGN_REVIEW_REASON_REQUIRED` (HTTP 400).

### Nhánh 2: Quyết định CLARIFICATION_REQUIRED
- Staff gửi quyết định `CLARIFICATION_REQUIRED` kèm văn bản yêu cầu bổ sung thông tin độ dày của đai nhẫn.
- Mong đợi: Trạng thái chuyển thành `CLARIFICATION_REQUIRED`. Request lưu lịch sử quyết định.
- Member gửi revision cập nhật mô tả độ dày đai nhẫn kèm header `If-Match-Version: 1`.
- Mong đợi: Bản ghi `CustomDesignRequestRevision` được tạo (revision 1), `version` tăng lên 2, trạng thái quay lại `NEED_REVIEW`.

### Nhánh 3: Admin cố gắng thẩm định
- Tài khoản có vai trò `ADMIN` gọi endpoint quyết định.
- Mong đợi: Bị chặn với HTTP 403 `DESIGN_REVIEW_NOT_AUTHORIZED`.

## 4. Kết quả thực thi

<!-- EXECUTION:START -->
- Trạng thái: PASS
- Thực thi bởi: Senior QA & Verification Engineer
- Ngày thực thi: 2026-11-21
- Ghi chú: Đã kiểm chứng đầy đủ tại `DesignAnalysisService#reviseDesignRequest` và `DesignRequestController`.
<!-- EXECUTION:END -->
