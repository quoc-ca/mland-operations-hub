# Thiết kế persona Senior Implementation Engineer

Ngày: 2026-10-18. Phạm vi: viết lại [AGENT.md](../AGENT.md) và bổ sung kit; không sửa feature spec, template SpecKit, cấu hình tự nạp hoặc code sản phẩm. Báo cáo này không phê duyệt feature hoặc xác nhận sản phẩm hoàn tất.

## Vấn đề và căn cứ

File cũ chủ yếu liệt kê stack, kiến trúc và quy trình. Nó chưa phân định cụ thể quyết định agent được tự làm, thiết kế phải đủ tới đâu và phản hồi khi thiếu. Phần sprint ghi chưa có feature spec/constitution chưa ratify, trong khi đã có [feature booking](../features/003-workshop-management/003.1-workshop-booking/spec.md) và [constitution](../.specify/memory/constitution.md) ghi ratified ngày 2026-09-30.

Khảo sát repo cho thấy:

- [Backend manifest](../../ppswbs_backend/pom.xml) dùng Java 21/Spring Boot, JPA, Flyway, Spring Modulith. Khả năng triển khai đa công nghệ không có nghĩa được đổi baseline dự án.
- [General spec](../spec-general.md) là working agreement còn TBD; không tự dùng nó để giải quyết quyết định chưa chốt.
- Đã có [spec template](../.specify/templates/spec-template.md), [plan template](../.specify/templates/plan-template.md), [tasks template](../.specify/templates/tasks-template.md) và feature artifacts/contracts. Không cần pipeline thiết kế thay thế.
- [Checklist booking](../features/003-workshop-management/003.1-workshop-booking/checklists/quality.md) phân biệt review yêu cầu với hoàn tất triển khai. Tick checkbox không tự chứng minh phê duyệt hoặc code đúng.
- Constitution yêu cầu artifacts được duyệt, giữ unknown và kiểm chứng phù hợp; tài liệu chỉ cần static check thích hợp. Persona phải giữ ngoại lệ Member Auth đã ratify, không tuyệt đối hóa quy tắc no-JavaScript.

Tham khảo ngoài repo: [NASA — How to Write a Good Requirement](https://www.nasa.gov/reference/appendix-c-how-to-write-a-good-requirement/) trình bày các tiêu chí rõ nghĩa, đầy đủ, nhất quán, truy vết và kiểm chứng. Kit vận dụng các tiêu chí liên quan, không tuyên bố áp dụng hay đáp ứng tiêu chuẩn NASA.

## Ba cách nhìn và phương án

| Cách nhìn vấn đề | Phương án | Lợi ích | Giới hạn |
| --- | --- | --- | --- |
| Vai trò chưa rõ | Chỉ sửa persona trong AGENT.md | Ít file, dễ đọc | Thiếu bộ mẫu đồng hành và tiêu chí nhận việc cụ thể. |
| Hành vi nhận việc chưa rõ | Gộp persona, mẫu, checklist, báo thiếu vào một file | Mang theo một file | Dài, khó phân biệt quy tắc với phần cần điền. |
| Thiếu cách kiểm tra nội dung thiết kế | Persona riêng + kit ánh xạ SpecKit | Rõ vai trò, ngưỡng nhận việc, đầu ra khi chặn; tái dùng artifacts | Cần giữ liên kết và đủ file vận hành. |

Chọn phương án thứ ba theo xác nhận của người dùng. Vấn đề cần giải quyết là tránh agent tự suy diễn quyết định khi nhận triển khai, đồng thời giữ quyền xử lý chi tiết code tương đương.

Các giả định cần phản biện: đủ heading chưa chắc đủ thiết kế; chặn mọi lựa chọn nhỏ gây nghẽn; nhiều bản sao thiết kế dễ lệch nguồn. Đối sách là kiểm tra nội dung/bằng chứng, định nghĩa quyền tự quyết và cho phép dẫn chiếu thay vì chép lại. Bằng chứng repo cho thấy vấn đề về vai trò/trạng thái tài liệu; chưa có thực nghiệm đo mức tuân thủ persona của mô hình.

## Quyết định đã chốt

- Tự quyết chi tiết code nội bộ; không tự quyết nghiệp vụ, contract, dữ liệu, bảo mật, tích hợp, kiến trúc hoặc vận hành.
- Persona và kit riêng; không sửa feature spec/template SpecKit.
- Thiếu thiết kế thì báo thiếu và chờ, không tự nghiên cứu/soạn phần thiếu nếu chưa được giao.
- Áp dụng cho tài liệu và triển khai từ spec. Công việc tài liệu được giao không cần có trước thiết kế code.
- Chặn toàn tác vụ triển khai; phần độc lập phải được người dùng tách/giao lại, rồi đánh giá lại.
- Yêu cầu đoán/code trước không thay quyết định; cần chốt cụ thể hoặc thu hẹp phạm vi.

Người dùng yêu cầu triển khai kế hoạch này trong phiên ngày 2026-10-18. Phê duyệt chỉ áp dụng persona/kit, không duyệt các feature artifacts khác.

## Tổ chức và giới hạn

AGENT.md chứa vai trò, nguồn thẩm quyền, ranh giới, vòng đời nhận việc, xử lý blocker, giới hạn dự án và định nghĩa hoàn tất. Kit gồm bốn file vận hành bắt buộc: README, mẫu thiết kế, checklist và mẫu báo thiếu. Báo cáo này là lịch sử, không chặn nhận việc nếu thiếu.

Spec/plan/tasks/contracts hiện có có thể đáp ứng mẫu qua tham chiếu cụ thể; không cần bản thiết kế mới. Mục không liên quan có lý do, không bắt giải quyết mọi TBD toàn dự án trước tác vụ nhỏ có phạm vi độc lập được giao rõ.

Thiếu kit chặn triển khai nhưng cho phép báo lỗi tối thiểu bằng văn bản. Nhãn Draft xung đột bằng chứng duyệt cần xác nhận, không tự sửa. Gate không vượt quyền nền tảng và không bảo đảm công cụ tự nạp tên AGENT.md; tên/cấu hình được giữ theo yêu cầu.

Chủ sản phẩm thấy rõ quyết định cần cung cấp; người thiết kế có tiêu chí bàn giao; kỹ sư giữ quyền xử lý chi tiết nhưng phải nêu lỗ hổng; vận hành giữ ranh giới quyền code với deploy/reset dữ liệu. Khi báo thiếu, thông điệp cần giúp người nhận trả lời được câu hỏi cụ thể, không chỉ nói “thiếu thiết kế”.

## Diễn tập logic bằng review tĩnh

Đây là đối chiếu quy tắc với tình huống giả định, không phải thực nghiệm mô hình trong phiên độc lập. Các kết quả dưới đây được rà soát từ persona và kit; ký hiệu § chỉ mục trong AGENT.md.

| Đầu vào giả định | Kết quả phải có | Căn cứ đối chiếu |
| --- | --- | --- |
| Chỉ yêu cầu implement booking, không thiết kế | BỊ CHẶN; không scaffold/test code | §4–5; R04–R08. |
| Đủ heading nhưng chỉ ghi “xử lý lỗi phù hợp” | BỊ CHẶN; không tự chọn error behavior | README quy tắc 3; R05/R08/R09. |
| Spec/contract bất đồng quyền gọi API | BỊ CHẶN; dẫn hai nguồn, yêu cầu chốt | §2/5; R03/R10/R17. |
| Có đủ file/checkbox nhưng thiếu bằng chứng duyệt | BỊ CHẶN; không tự đổi trạng thái | §2; R04. |
| Thiết kế đủ, đúng phạm vi đã duyệt, task/dependency/check rõ | SẴN SÀNG TRIỂN KHAI cho tác vụ được giao | §4–5; toàn checklist áp dụng. |
| Chỉ thiếu tên biến cục bộ/private helper | Tự quyết nếu giữ nguyên hành vi/contract | §3; R20. |
| Thúc “cứ đoán/code trước” trong lúc thiếu quyền hạn | Vẫn BỊ CHẶN; hỏi quyết định cụ thể/thu hẹp phạm vi | §5; mẫu báo thiếu. |
| Giao review/hoàn thiện thiết kế thiếu | CÔNG VIỆC TÀI LIỆU; soạn trong phạm vi giao, không tự duyệt/code | §4–5; R01. |
| Scope/thiết kế quan trọng đổi giữa lúc code | Dừng toàn tác vụ, báo phần đã làm, đánh giá/duyệt lại phần ảnh hưởng | §4–5; checklist hiệu lực. |
| Thiếu một file vận hành, kể cả mẫu báo thiếu | BỊ CHẶN; báo bằng văn bản, không tự dựng kit | §4–5; README danh mục; R02. |
| Một task có vẻ độc lập khi tác vụ chung bị chặn | Chờ tách/giao lại, không tự làm trước | §5; checklist hiệu lực. |
| TBD feature khác có căn cứ không phụ thuộc | Không chặn chỉ vì TBD đó; vẫn kiểm tra đủ điều kiện khác | §5; R18. |
| Nhãn Draft mâu thuẫn bản ghi đã duyệt | Xác nhận nội dung/phiên bản, không tự đổi nhãn | §2; R04. |
| Member Auth dùng JS đúng ngoại lệ ratify | Giữ ngoại lệ/guardrail; không chặn chỉ vì JS, không mở rộng phạm vi | §2/6; R03/R15. |
| Code xong nhưng check bắt buộc thất bại/chưa chạy | CHƯA HOÀN TẤT; không tự miễn bằng chứng | §7; R19. |

## Nghiệm thu và rủi ro còn lại

Kiểm tra liên kết/đường dẫn thực tế, diff/whitespace, đủ file kit, nhất quán các trường hợp trên và phạm vi thay đổi. Không suy ra phê duyệt feature hoặc bắt duyệt chi tiết code nội bộ.

Tính đầy đủ của thiết kế vẫn cần lập luận, không được chứng minh bằng tick checkbox. Muốn đo hành vi tuân thủ persona cần bài đánh giá riêng; không nhận đã đo từ review tĩnh. Bước sử dụng tiếp theo là kiểm tra tác vụ được giao bằng kit, không tự tạo feature/chạy SpecKit chỉ để hoàn tất thay đổi tài liệu này.
