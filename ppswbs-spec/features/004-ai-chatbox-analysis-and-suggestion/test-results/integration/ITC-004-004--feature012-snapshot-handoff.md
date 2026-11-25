---
id: ITC-004-004
feature: 004-design-request-and-human-review
test_level: integration
test_case_type: N
source_ref: spec.md#6
source_class: edu.fpt.sep490_g22.ppswbs_backend.designreview.application.DesignAnalysisService
source_method: captureDesignSnapshot
test_class: edu.fpt.sep490_g22.ppswbs_backend.designreview.DesignAnalysisServiceTest
test_method: shouldCaptureImmutableFeature012Snapshot
---

# ITC-004-004: Tích hợp bàn giao snapshot nhẫn Feature-012 bất biến vào yêu cầu thiết kế

**Ngày tạo**: 2026-11-21
**Tác giả**: Senior QA Engineer
**Trạng thái**: PASS

## 1. Mục tiêu kiểm thử

Xác minh tính bất biến (immutability) của bản thiết kế nhẫn từ Feature-012 khi được Member đính kèm vào yêu cầu thiết kế Feature-004:
- Khi Member chọn một bản thiết kế nhẫn đã lưu (`DRAFT` hoặc `VALIDATED`), hệ thống sao lưu một snapshot JSON độc lập vào bảng `custom_design_reference_snapshots`.
- Bất kỳ thay đổi, xóa, hoặc cập nhật nào trên thiết kế gốc trong Feature-012 sau thời điểm gửi đều KHÔNG ảnh hưởng đến snapshot trong yêu cầu thiết kế và đơn hàng gia công.

## 2. Tiền điều kiện và Dữ liệu

- Member có mẫu thiết kế nhẫn Feature-012 mã `RING-DES-777`, version 1, gồm chất liệu vàng hồng, size 16, 2 viên đá quý và khắc laser chữ "Forever".

## 3. Các bước thực hiện

1. Member tạo custom design request chọn đính kèm `RING-DES-777` version 1.
2. `DesignAnalysisService` gọi qua `WorkshopRingDesignFacade.getDesignSnapshot()` để lấy nội dung thiết kế.
3. Lưu snapshot vào bảng `custom_design_reference_snapshots` với snapshot JSON đầy đủ thuộc tính nhẫn.
4. Member quay trở lại màn hình cấu hình nhẫn Feature-012, chỉnh sửa `RING-DES-777` thành vàng trắng và thêm đá quý thứ 3.
5. Truy vấn lại chi tiết yêu cầu thiết kế qua `GET /api/v1/custom-design-requests/{id}`.
6. So sánh dữ liệu `designSnapshot` trả về.

## 4. Kết quả mong đợi

- Snapshot lưu giữ chính xác cấu hình ban đầu: vàng hồng, size 16, 2 viên đá quý, chữ khắc "Forever".
- Thay đổi sau đó của người dùng trên Feature-012 hoàn toàn bị cách ly, không ảnh hưởng đến đơn yêu cầu thẩm định và đơn sản xuất.

## 5. Kết quả thực thi

<!-- EXECUTION:START -->
- Trạng thái: PASS
- Thực thi bởi: Senior QA & Verification Engineer
- Ngày thực thi: 2026-11-21
- Ghi chú: Đã kiểm chứng tính bất biến của snapshot qua `CustomDesignReferenceSnapshotRepository`.
<!-- EXECUTION:END -->
