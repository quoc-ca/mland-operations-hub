# Specification Quality Checklist: Product Management

**Purpose**: Rà chất lượng và độ đầy đủ của spec trước review/lập plan.  
**Created**: 2026-10-23  
**Feature**: [spec.md](../spec.md)  
**Review Ownership**: Checklist chất lượng do bước speckit-specify rà soát; không phải phê duyệt nghiệp vụ mới hoặc bằng chứng triển khai.  
**Marker Semantics**: `[x]` nghĩa tiêu chí chất lượng tài liệu đã được kiểm tra, không có nghĩa code đã hoàn thành hoặc kịch bản đã chạy trên sản phẩm.

## Content Quality

- [x] Không lựa chọn ngôn ngữ, framework, giao thức, schema hoặc chi tiết triển khai trong yêu cầu nghiệp vụ.
- [x] Tập trung giá trị của Staff/Manager và thông tin khách cần trước mua hàng.
- [x] Viết cho stakeholder nghiệp vụ; định nghĩa size, SKU, hồ sơ duyệt, giá áp dụng và khả năng mua.
- [x] Hoàn thành các phần bắt buộc của spec-template theo đúng thứ tự.

## Requirement Completeness

- [x] Không còn marker yêu cầu làm rõ hoặc placeholder của template.
- [x] FR có thể kiểm chứng; A-001/A-002, Cloudinary và hướng A/phiên nội bộ 8 giờ đã được người dùng chốt ngày 2026-10-24; phương thức MFA chung còn là dependency FE-01, không tự coi đã được chọn.
- [x] SC-001–SC-007 có số lượng/tỷ lệ và tập nghiệm thu xác định.
- [x] Tiêu chí thành công không phụ thuộc công nghệ hoặc kết quả kiểm thử chưa có.
- [x] Có kịch bản chấp nhận cho tất cả 38 FR; xem ánh xạ dưới đây.
- [x] Có biên dữ liệu, ảnh, SKU, hồ sơ cũ, thử lại, giữ chỗ và thao tác đồng thời.
- [x] Ranh giới FE-02/FE-04/FE-08, không khắc, không thêm/đổi size và xóa mềm không khôi phục được nêu rõ.
- [x] Có nguồn, giả định, phụ thuộc và giới hạn trước triển khai; không tự quyết mapping vai trò, provider, retention hoặc payment.

## Feature Readiness

- [x] Mỗi FR có ít nhất một kịch bản nghiệm thu rõ trong US1–US8.
- [x] User story có mức ưu tiên, giá trị, cách kiểm tra độc lập và đường đi chính/lỗi.
- [x] SC bao phủ luồng chính, quyền, dữ liệu, trạng thái, giá, khả dụng và bảo toàn giao dịch.
- [x] Không biến spec thành plan/code hoặc tuyên bố đã sẵn sàng triển khai.

## Acceptance Traceability

Ký hiệu `USn.m` là kịch bản số m trong User Story n; các khoảng FR bao gồm cả hai đầu.

| Requirements | Acceptance scenarios | Evidence reviewed |
| --- | --- | --- |
| FR-001, FR-002 | US1.8–US1.10; US2.3, US2.6; US7.1; US8.1 | Xác thực dùng chung/phiên 8 giờ, quyền quản trị, gate đặc quyền, Admin cấp quyền, duyệt chỉ Manager, tự duyệt, tra cứu công khai. |
| FR-003 | US1.5, US1.7 | Danh sách/chi tiết, tìm mã/tên/SKU, phân biệt dữ liệu giá. |
| FR-004–FR-007 | US1.1–US1.4, US1.6; US4.4; Edge Cases | Giới hạn dữ liệu, size/SKU, nháp và tính toàn vẹn; mã trên 6 chữ số. |
| FR-008 | US4.5, US4.6, US4.9; Edge Cases | Ảnh, dung lượng, alt text, thứ tự, lỗi lưu và cleanup 24h/30 ngày có bảo vệ lịch sử. |
| FR-009–FR-011 | US2.1–US2.7 | Gửi/duyệt/từ chối, phiên bản, hủy và lỗi. |
| FR-012 | US4.1; US2.5 | Sửa trực tiếp sau lần công khai đầu, tách với duyệt lại hồ sơ mới. |
| FR-013–FR-016 | US5.1–US5.5; US3.1, US3.2, US3.6 | Giá đề xuất/duyệt/áp dụng, AI không bắt buộc, giữ giá lịch sử. |
| FR-017, FR-018 | US3.3–US3.6; US6.3 | Điều kiện công khai, số lượng 0, ẩn và công khai lại. |
| FR-019, FR-020 | US4.2, US4.3; US8.3–US8.5 | Lượng chung, giữ hợp lệ, khả năng mua từng size/sản phẩm. |
| FR-021 | US6.1, US6.2 | Ngừng bán/mở bán lại không đổi số lượng hay cờ công khai. |
| FR-022–FR-025 | US6.4–US6.7; US4.7; US5.4 | Xóa mềm, đồng thời, thử lại và bảo toàn giao dịch. |
| FR-026–FR-028 | US1.6; US2.2, US2.6, US2.7; US3.1, US3.6; US4.3, US4.6; US5.2, US5.6; US6.5; US7.1; US8.9 | Dữ liệu mới nhất, audit/receipt 5 năm, phiên bản/kết quả, lỗi an toàn và không lưu/trả kết quả một phần. |
| FR-029, FR-030 | US7.1–US7.5 | Nhập liệu danh mục, ảnh hưởng hoạt động và giữ lịch sử. |
| FR-031–FR-036 | US8.1–US8.9, US8.11 | Tập kết quả công khai, lọc cùng size, giá/sắp xếp và mất ảnh. |
| FR-037 | US8.10; US6.6 | Chuyển FE-08, không tạo giao dịch từ việc tra cứu. |
| FR-038 | US4.8 | Hành trình cốt lõi hoạt động không cần tăng cường trình duyệt. |

## Notes

- Vòng review 1 đã bổ sung kịch bản riêng về quyền quản trị, phân trang, biên nhập danh mục, độ dài từ khóa và hành trình không cần tăng cường trình duyệt để làm rõ ánh xạ nghiệm thu. Vòng review 2 kiểm tra lại toàn bộ 38 FR và các quyết định hội thoại.
- A-001/A-002, role codes, hướng A/phiên nội bộ 8 giờ, Cloudinary, retention và các mặc định đã được người dùng đồng ý ngày 2026-10-24 và đồng bộ vào FE-02 research. Không hỏi lại các quyết định này.
- MFA/recovery cụ thể vẫn là dependency thiết kế identity/FE-01; không sửa luồng Member hoặc tự chọn phương thức từ FE-02. Adapter FE-08/identity/Cloudinary là dependency triển khai; miền số/cách đếm ký tự là thiết kế kỹ thuật. Không dùng checklist chất lượng để nhận dependency đã chạy hoặc code đã được phê duyệt.
- Không có hooks.before_specify hoặc hooks.after_specify để thực thi vì dự án không có `.specify/extensions.yml`; không tạo/switch nhánh và không commit/push.
- Checklist này xác nhận chất lượng bản nháp để review/lập plan; không phải kết quả chạy nghiệm thu, phê duyệt plan hoặc cho phép triển khai.
