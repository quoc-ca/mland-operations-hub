# Specification Quality Checklist: Loyalty & Promotion Management

**Date created**: 2026-10-24
**Date updated**: 2026-10-24
**Author**: Đặng Thị Khương
**Last updated by**: Đặng Thị Khương

**Purpose**: Kiểm tra chất lượng và độ đầy đủ của spec trước review/lập plan.  
**Feature**: [spec.md](../spec.md)  
**Review Ownership**: Bước speckit-specify rà chất lượng tài liệu; không phải phê duyệt nghiệp vụ mới hoặc bằng chứng chạy sản phẩm.  
**Marker Semantics**: `[x]` nghĩa đã kiểm tra tiêu chí chất lượng; không có nghĩa đã triển khai hoặc chạy nghiệm thu.

## Content Quality

- [x] Không lựa chọn ngôn ngữ, framework, giao thức, schema hoặc cách triển khai.
- [x] Tập trung giá trị khách quay lại hai dịch vụ và nhu cầu quản lí của Manager.
- [x] Viết cho stakeholder nghiệp vụ, giải thích rõ điểm, phần giữ và quyền lợi đã ghi nhận.
- [x] Hoàn thành các phần bắt buộc theo thứ tự spec-template đã resolve.

## Requirement Completeness

- [x] Không còn marker yêu cầu làm rõ hoặc placeholder template.
- [x] Yêu cầu kiểm chứng được; thông số, cơ sở tính, làm tròn, quyền và vòng đời rõ ràng.
- [x] Tiêu chí thành công đo được bằng tập nghiệm thu xác định.
- [x] Tiêu chí thành công độc lập công nghệ, không tuyên bố kết quả chưa chạy.
- [x] Tất cả FR có kịch bản chấp nhận trong bảng truy vết.
- [x] Có biên tiền/điểm, giỏ hỗn hợp, đồng thời, gửi lặp, mất phản hồi và hủy.
- [x] Phạm vi voucher/điểm và những phần loại trừ rõ; không khôi phục hạng thành viên.
- [x] Nguồn, giả định, dependency và giới hạn của booking spec hiện có được nêu rõ.

## Feature Readiness

- [x] Tất cả FR có tiêu chí chấp nhận rõ và không mâu thuẫn quyết định đã chốt.
- [x] Các hành trình chính có ưu tiên, giá trị và cách kiểm tra độc lập.
- [x] SC bao phủ tính đúng tiền/điểm, thông tin khách thấy, quyền, giữ/chốt/hoàn và thay đổi chính sách.
- [x] Spec chỉ mô tả nghiệp vụ; không tự chuyển sang code hoặc nhận đã sẵn sàng triển khai.

## Acceptance Traceability

Ký hiệu `USn.m` là kịch bản m của User Story n. Khoảng số bao gồm cả hai đầu; bảng này là truy vết yêu cầu, không phải kết quả chạy test.

| Requirements | Acceptance scenarios | Nội dung đã đối chiếu |
| --- | --- | --- |
| FR-001 | US1.6; US3.3, US3.6; US4.10; US6.4; US8.3 | Quyền Manager (Admin bị từ chối), dữ liệu Member và xem công khai. |
| FR-002 | US1.1–US1.3, US1.7; US5.8; US6.3, US6.5 | Hai phạm vi, ngày lễ, không hạng và giới hạn website. |
| FR-003–FR-005 | US1.1–US1.3 | Đủ điều kiện tạo ba loại chương trình. |
| FR-006 | US1.4; US2.2; US3.4 | Mã chuẩn hóa, duy nhất, không tái dùng/đổi mã hoặc phạm vi. |
| FR-007 | US1.5, US1.6, US1.8; US2.7 | Dữ liệu/quyền, lỗi lưu và không báo thành công sai. |
| FR-008 | US2.1 | Danh sách/chi tiết, lọc, lượt và tiền giảm. |
| FR-009 | US2.2, US2.3, US2.6 | Sửa/tạm dừng, giới hạn theo lượt hiện tại. |
| FR-010 | US2.4, US2.5; US8.4 | Bảo toàn cam kết, thời hạn và kiểm tra giao dịch mới. |
| FR-011–FR-013 | US3.1–US3.6; US4.8; US5.6 | Thông tin, kho, nhập trực tiếp và lỗi. |
| FR-014, FR-015 | US4.1–US4.3 | Phần đủ điều kiện, trần/làm tròn, cố định một lần và chọn giảm tự động. |
| FR-016 | US4.4–US4.6 | Hiển thị khoản tiền, khả dụng/tối đa và khách chọn điểm. |
| FR-017, FR-018 | US4.7–US4.9; US7.9 | Xác nhận lại, giữ cùng đơn và chốt đơn trả đủ/0đ. |
| FR-019–FR-022 | US5.1–US5.4, US5.6, US5.7 | Điều kiện booking, tính cọc sau giảm, chốt cọc, từ chối miễn phí. |
| FR-023 | US6.3, US6.5 | Điểm dùng chung, không hết hạn/hạng hoặc dùng trước khi cấp. |
| FR-024, FR-025 | US4.4–US4.6; US5.2, US5.3; US8.1 | Bộ thông số đầu, trần/tối thiểu và cơ sở tính hai dịch vụ. |
| FR-026, FR-027 | US8.1–US8.5; US6.7 | Quyền/cấu hình hợp lệ, chính sách giao dịch cũ/mới. |
| FR-028–FR-030 | US5.5; US6.1, US6.2, US6.6, US6.7 | Đủ điều kiện, thực trả, làm tròn và cấp một lần. |
| FR-031 | US6.4, US6.8 | My Loyalty, số dư/giữ/khả dụng, lịch sử và tải lại. |
| FR-032, FR-033 | US4.9; US5.4; US7.1, US7.2, US7.6, US7.9 | Giữ/chốt một lần, không vượt hạn mức và giải phóng. |
| FR-034–FR-036 | US7.3–US7.6 | Hủy xác nhận, trả lượng gốc/từng quyền lợi và không trả trùng. |
| FR-037, FR-038 | US7.7–US7.9; US6.6 | Không đến/hủy sai, ngoài phạm vi và kết quả chưa rõ. |
| FR-039 | US8.6; US3.6; US6.8 | Hành trình cốt lõi hoàn thành từ giao diện web tách riêng qua API và lỗi an toàn. |
| FR-040 | US8.7 | Xác thực dùng chung, phiên theo contract FE-01/001, quyền/trạng thái và thu hồi. |
| FR-041 | US6.9 | Lưu tối thiểu 5 năm, bảo vệ tham chiếu, số dư và bằng chứng chống trùng. |

## Notes

- Nguồn nghiệp vụ là [FE-06 research](../../../research.md#fe-06) và các xác nhận của người dùng; không dùng report/screen cũ để đổi phạm vi. Bộ thông số điểm và quyết định 1–10 đã chốt ngày 2026-10-24.
- Spec hiện có 8 user stories, 41 FR, 66 kịch bản chấp nhận và 8 SC (sau hai xác nhận khi lập plan và clarification ngày 2026-10-24). Thông số voucher/cọc trong ví dụ là dữ liệu kiểm thử, không phải chính sách mới.
- Review vòng 1 đã bổ sung tra mã có khoảng trắng/khác hoa thường tại US3.4 và miền dữ liệu hợp lệ tại US1.5/FR-007. Review vòng 2 (mốc lúc đó, trước khi thêm FR-040/FR-041) đối chiếu đủ 39 FR với 62 kịch bản và kiểm tra các quyết định 1–10, công thức tiền/điểm, phạm vi đã loại bỏ.
- Cập nhật khi lập plan: thêm FR-040/FR-041 và US8.7/US6.9 theo câu trả lời trực tiếp của người dùng ngày 2026-10-24; không suy rộng chính sách retention/xác thực của feature khác mà chưa có xác nhận.
- Static verification: thứ tự phần template, số FR/SC/user story, đánh số và tham chiếu kịch bản, liên kết nguồn và feature pointer đều hợp lệ; không có placeholder yêu cầu làm rõ. Băm research.md giữ nguyên sau tác vụ; kiểm tra whitespace và đường dẫn SpecKit được thực hiện trước bàn giao. Không chạy test sản phẩm vì chỉ tạo tài liệu.
- Dependency D-004: spec booking 003 hiện hoãn hậu xử lí; cần FE-03 cung cấp kết quả hủy/hoàn thành/trả đủ đã xác nhận trước bật các hành trình tương ứng. Không nhận dependency đã có code hoặc đã được spec 003 bao phủ.
- Hooks.before_specify và hooks.after_specify không có để chạy vì không tồn tại `.specify/extensions.yml` tại dự án SpecKit hoặc gốc workspace. Không tạo/chuyển nhánh, không commit/push.
- Đây là checklist chất lượng spec, được tạo trước plan/tasks. Plan, tasks, data model và contracts đã có sau đó; chưa có nghiệm thu trên sản phẩm cho FE-06.
