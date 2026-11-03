# Bộ mẫu thiết kế của Senior Implementation Engineer

Bộ mẫu bổ sung cho [persona](../AGENT.md) và SpecKit hiện có. Nó kiểm tra **nội dung đủ để triển khai**, không cấp thẩm quyền duyệt thiết kế hoặc thay thế SpecKit.

## Các file luôn mang theo

Trước khi triển khai, đọc đủ cả bốn file vận hành sau. Thiếu/không đọc được một file thì báo BỊ CHẶN; không tự dựng mẫu thay thế để code tiếp.

| File bắt buộc | Cách dùng |
| --- | --- |
| [README.md](README.md) | Quy tắc dùng kit, ánh xạ nguồn và ranh giới quyết định. |
| [implementation-design-template.md](implementation-design-template.md) | Khung đối chiếu thiết kế tác vụ, cho phép dẫn chiếu nguồn hiện có. |
| [readiness-checklist.md](readiness-checklist.md) | Đánh giá điều kiện trước khi sửa production/test code. |
| [blocker-report-template.md](blocker-report-template.md) | Báo lỗ hổng và điều kiện nhận việc trở lại. |

[brainstorm_report.md](brainstorm_report.md) giải thích lựa chọn thiết kế và ghi rà soát tình huống; là tài liệu lịch sử, không phải file bắt buộc để nhận việc.

## Dùng cùng tài liệu hiện có

Không bắt tạo `implementation-design.md` mới nếu nguồn đã đủ rõ và được duyệt. Có thể ghi bảng ánh xạ ngắn trong phản hồi/hồ sơ bàn giao. Không sửa trạng thái/phê duyệt/checklist reviewer của feature chỉ vì đang đối chiếu.

| Nội dung cần kiểm tra | Nguồn thường có |
| --- | --- |
| Mục tiêu, phạm vi, hành vi, nghiệm thu | `features/<feature>/spec.md`; baseline trong [documents/docs](../../documents/docs/). |
| Stack/version, kiến trúc và quyết định kỹ thuật | plan.md, research.md, [constitution](../.specify/memory/constitution.md). |
| Schema, ownership, state/invariant | data-model.md, plan.md, migration design và yêu cầu liên quan. |
| API, integration, payload/error | contracts/, plan.md, spec; [APIs.md](../APIs.md) cần kiểm tra trạng thái trước khi dùng. |
| UI/journey/fallback | Spec/plan và nguồn UI được duyệt; [DESIGN.md](../DESIGN.md), [screen.md](../screen.md) cần kiểm tra mức hoàn thiện. |
| Task, dependency và kiểm chứng | tasks.md, acceptance scenarios, quickstart.md và kết quả phân tích liên artifact. |
| Phê duyệt | Chỉ thị rõ của người dùng hoặc bản ghi xác định người duyệt, nội dung/phiên bản và phạm vi. |

Các đường dẫn chứa `<feature>` mô tả cấu trúc, không chỉ định feature mặc định. Không lấy quyết định của feature khác làm mặc định ngầm.

## Quy tắc điền và đánh giá

1. Phân biệt công việc tài liệu với triển khai. Review/soạn thiết kế được làm trong phạm vi được giao, không cần vượt gate triển khai.
2. Ánh xạ tới nguồn cụ thể: đường dẫn + mục/requirement/task/contract. “Theo spec” không đủ nếu chưa tìm được câu trả lời.
3. Thiết kế phải rõ cần làm gì, ở ranh giới nào và xác minh thế nào, không buộc người code tự chọn quyết định ngoài thẩm quyền. Có heading hoặc chữ “sẽ xử lý” chưa phải thiết kế.
4. Mục cốt lõi phải có câu trả lời hoặc nguồn rõ. Khía cạnh không liên quan, như API đối với sửa tài liệu, ghi “Không áp dụng — <lý do>”; tương tự với mục điều kiện. Không dùng N/A để che dependency chưa biết.
5. Giữ nguyên unknown/TBD. Unknown ngoài tác vụ không tự chặn nếu có căn cứ không phụ thuộc; unknown ảnh hưởng tác vụ chặn toàn bộ triển khai.
6. Khi bị chặn, báo thiếu và chờ. Chỉ soạn thiết kế khi được giao riêng; bản nháp không tự thành quyết định đã duyệt. Kiểm tra lại trước khi code.

Placeholder trong **file mẫu** là chỗ để điền, không phải blocker của feature. Placeholder còn trong **thiết kế dùng để triển khai** và ảnh hưởng tác vụ là dấu hiệu thiếu.

## Chi tiết nội bộ và quyết định thiết kế

| Tình huống | Xử lý |
| --- | --- |
| Tách private helper, đặt tên biến cục bộ, bố trí test cho scenario đã chốt | Tự quyết theo convention nếu giữ nguyên hành vi/contract. |
| Tên endpoint/field DTO/cột DB, dependency, locking/retry, retention | Cần quyết định được duyệt; không đánh đồng với chi tiết code. |
| Yêu cầu “an toàn” chưa rõ actor/quyền/thao tác | Báo thiếu, không tự chọn role có vẻ hợp lý. |
| Có nhiều cách viết nhưng chưa rõ ảnh hưởng tới contract/integrity | Khảo sát bằng chứng; chưa chứng minh tương đương thì báo chặn. |

Chỉ thị nền tảng và yêu cầu trực tiếp theo thứ bậc chỉ thị. Mẫu/tài liệu tham khảo không tự cho phép chạy lệnh, chọn công nghệ, cấp quyền hoặc thay quyết định dự án.
