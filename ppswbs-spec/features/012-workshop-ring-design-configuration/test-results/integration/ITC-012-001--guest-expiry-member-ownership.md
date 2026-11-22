---
id: ITC-012-001
feature: 012-workshop-ring-design-configuration
test_level: integration
test_case_type: N
source_ref: spec.md#4
source_class: edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.service.WorkshopRingDesignApplicationService
source_method: saveDesignVersion
test_class: edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.WorkshopRingDesignIntegrationTest
test_method: shouldIsolateMemberOwnershipAndHandleGuestExpiry
---

# ITC-012-001: Quản lý phiên Guest (hết hạn 20 phút) và phân lập quyền sở hữu thiết kế Member

**Ngày tạo**: 2026-11-21
**Tác giả**: Senior QA Engineer
**Trạng thái**: PASS

## 1. Mục tiêu kiểm thử

Xác minh:
- Guest bắt đầu phiên thiết kế, sau 20 phút không hoạt động thì phiên hết hạn an toàn.
- Member đã đăng nhập có thể lưu và thẩm định thiết kế, xem danh sách mẫu thiết kế của chính mình.
- Không có rò rỉ dữ liệu giữa các Member (Member A không xem hoặc sửa được thiết kế của Member B).
- Chức năng xóa mẫu thiết kế phía Member là soft delete (phía shop vẫn lưu trữ đầy đủ để đối soát).

## 2. Tiền điều kiện và Dữ liệu

- Member 1: `member.test@mland.vn` (Account ID: 6)
- Member 2: `customer.test@mland.vn` (Account ID: 5)
- Guest session token ẩn danh.

## 3. Các bước thực hiện

1. Guest truy cập trang cấu hình, thực hiện chỉnh sửa. Sau 20 phút không có thao tác, phiên hết hạn.
2. Member 1 lưu thiết kế `RING-001` (bản nháp).
3. Member 2 gửi yêu cầu đọc `GET /api/v1/workshop-ring-designs/RING-001`.
   - Kết quả: Backend trả về HTTP 403 Forbidden hoặc 404 Not Found.
4. Member 1 thực hiện xóa thiết kế `RING-001`.
   - Pop-up cảnh báo xác nhận xóa vĩnh viễn hiển thị.
   - Sau khi xác nhận, thiết kế bị đánh dấu `is_deleted_by_customer = true`.
   - Thiết kế không còn xuất hiện trong danh sách của Member 1, nhưng bản ghi trong database vẫn tồn tại cho Staff/Manager tra cứu.

## 4. Kết quả mong đợi

- Quyền sở hữu được phân lập 100%.
- Soft delete bảo vệ an toàn dữ liệu lịch sử sản xuất.

## 5. Kết quả thực thi

<!-- EXECUTION:START -->
- Trạng thái: PASS
- Thực thi bởi: Senior QA & Verification Engineer
- Ngày thực thi: 2026-11-21
- Ghi chú: Đã kiểm chứng qua bộ test `designDomain.test.js` và `WorkshopRingDesignApplicationServiceTest`.
<!-- EXECUTION:END -->
