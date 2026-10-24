# Product Management — Implementation Prerequisites

**Date created**: 2026-10-24
**Date updated**: 2026-10-24
**Author**: Đặng Thị Khương
**Last updated by**: Đặng Thị Khương

**Task**: speckit-plan, công việc tài liệu cho FE-02.  
**Outcome**: Đã cập nhật các câu trả lời ngày 2026-10-24. Phân biệt quyết định còn cần chốt với dependency chưa triển khai; dependency chưa có code không tự ngăn việc lập plan/thứ tự task. Tác vụ vẫn chỉ sửa tài liệu, không production/test code, migration hoặc deployment.

## Decision and dependency status

| ID | Evidence and missing decision | Impact | Owner and closing condition |
| --- | --- | --- | --- |
| G-01 | **ĐÃ CHỐT HƯỚNG TÍCH HỢP/PHIÊN FE-02**: dùng chung xác thực theo [001](../001-account-management/spec.md), bearer cho mọi REST API (kể cả frontend React), phiên nội bộ cố định 8 giờ tính từ auth_time, revocation/trạng thái và quyền nội bộ trên từng action. MFA/recovery thuộc identity/FE-01. | Không còn câu hỏi xác nhận hướng A cho FE-02. Còn **dependency thiết kế MFA chung** và **triển khai facade/session/quyền** trước enable quản trị. | Identity owner hoàn thiện phương thức MFA, enrollment/recovery và contract chứng minh đạt yêu cầu đặc quyền; TOTP chưa được chọn. Giữ luồng Member, không suy quyền từ cookie 001; identity owner xác nhận cách tính mốc 8 giờ cho bearer. Xem D-02a; không hỏi lại cơ chế dùng chung, phiên 8 giờ hoặc role mapping. |
| G-02 | readyringsales chưa có retail hold source/AvailabilityGuardPort adapter. Quy tắc và contract giữ hàng đã được mô tả. | **Dependency triển khai/kiểm chứng**, không phải lựa chọn nghiệp vụ còn thiếu. | Ghi dependency trong thứ tự task FE-08/002; nghiệm thu quantity/buyability/concurrency cần adapter thật trên MySQL. Không dùng zero-hold fallback hoặc fixture-only làm integration evidence. |
| G-03 | **ĐÃ CHỐT PROVIDER: Cloudinary**, theo câu trả lời ngày 2026-10-24. | Không còn quyết định chọn provider; adapter/SDK/cấu hình/kiểm chứng là **dependency triển khai**. | D-06 và MediaStoragePort mô tả server-side signed upload, authenticated assets và stream cùng origin. Cấu hình cloud_name/api_key/api_secret qua môi trường khi triển khai; không cần gửi secret vào chat. |
| G-04 | **ĐÃ CHỐT POLICY**: ảnh chưa gắn 24h, ảnh gỡ không tham chiếu 30 ngày, giữ ảnh lịch sử theo owning record; audit nghiệp vụ 5 năm theo Report 3, kết quả thao tác tối thiểu 5 năm. | Không còn blocker quyết định retention; cleanup/registry/audit/replay là task triển khai và kiểm chứng. | Thiết kế đã cập nhật cleanup chống tranh chấp attach/delete, không hard-delete product hoặc ảnh còn tham chiếu; security-authentication log vẫn thuộc identity owner. |

Người dùng đã đồng ý hướng A, gồm dùng chung cơ chế xác thực và phiên nội bộ cố định 8 giờ. Các câu hỏi quyết định thuộc FE-02 đã được trả lời; phương thức MFA/recovery của FE-01 vẫn chưa được chọn và phải hoàn thiện trước enable quản trị. G-01 đóng về hướng tích hợp/phiên, không đóng dependency thiết kế/triển khai identity. G-03/G-04 đã đóng về quyết định. Không dùng việc chuyển ownership để coi thiết kế MFA hoặc integration đã hoàn thành.

## Decisions confirmed and technical design for review

- A-001 đã đồng ý: tên danh mục 1–200, mô tả tùy chọn tối đa 5.000, unique toàn hệ thống kể cả inactive; không hỏi lại.
- A-002 đã đồng ý: q tối đa 200, 20 bản ghi/trang, newest theo createdAt.
- D-07: miền biểu diễn DECIMAL(19,0), decimal-string JSON, Unicode code-point length; không phải trần giá kinh doanh được phê chuẩn.
- Convention đã đồng ý: 5 MB = 5.000.000 bytes, reject reason bắt buộc tối đa 2.000 ký tự, saleEnabled=true lúc tạo size nhưng product chưa public, danh mục mới active=true. Cách đếm code point và giới hạn multipart overhead vẫn là chi tiết kỹ thuật đề xuất, không phải câu hỏi nghiệp vụ mở lại.
- Trạng thái: spec/plan/tasks phải review đúng phạm vi và qua speckit-analyze trước implementation; tasks chưa được tạo bởi bước plan.

## Work completed and safe next step

Đã khảo sát nguồn, soạn plan, research, data model, provider-neutral API/web/facade contracts và validation guide. Các quyết định người dùng đã chốt được giữ nguyên, gồm Manager tự duyệt; không áp cấm tự review từ DB working document sang FE-02.

Đã tạo [tasks.md](tasks.md) ngày 2026-10-24 theo yêu cầu speckit-tasks: 87 task chưa thực hiện, chia theo 8 user story, có dependency FE-01/FE-08/Cloudinary, ánh xạ đủ 38 FR/27 operationIds/11 bảng. Static check format/links/traceability đạt; chưa có runtime evidence hoặc kết quả speckit-analyze.

Bước kế tiếp là review tasks và speckit-analyze khi được giao. Task generation không đóng dependency thiết kế MFA/capability/session hoặc adapter FE-08/Cloudinary. Không hỏi lại hướng A/provider; chưa bắt đầu code từ việc được giao hoàn thiện tài liệu. Không tạo task giả lập dependency để nhận integration pass và không tự hoàn tác research/spec hiện có.
