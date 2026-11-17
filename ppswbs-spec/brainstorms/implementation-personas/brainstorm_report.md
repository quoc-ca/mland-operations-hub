# Brainstorm — Metadata SpecKit và persona triển khai

**Date created**: 2026-10-24
**Date updated**: 2026-10-24
**Author**: Sơn
**Last updated by**: Sơn

## 1. Vấn đề thực sự cần giải quyết

Nhóm cần truy vết thành viên chịu trách nhiệm tạo/sửa tài liệu thiết kế và ngăn
agent triển khai lấp chỗ thiếu bằng quyết định tự suy diễn. Hai giải pháp được
đưa ra là thêm metadata vào template và tạo persona triển khai có báo cáo design
blocker. Thành công được đánh giá bằng tính nhất quán của luồng ghi tài liệu và
cách trả thiếu sót về giai đoạn thiết kế, không bằng số lượng file hướng dẫn.

AGENT là hợp đồng hành vi khi được nạp, không phải cơ chế kỹ thuật bảo đảm tuyệt
đối agent không suy diễn. Metadata có tên người cũng không tự chứng minh người
đó duyệt nội dung. Cần nguồn, gate và bằng chứng song song với các field.

## 2. Hiện trạng và bằng chứng

- Có 5 [template SpecKit](../../.specify/templates/): spec, plan, tasks, checklist,
  constitution; các nhãn Created/Date chưa đồng nhất, tasks chưa có khối tác giả.
- `specify` có checklist requirements nhúng; `checklist` có fallback không phụ
  thuộc template. Chỉ sửa template không bao phủ đầy đủ luồng sinh.
- `clarify` bảo vệ metadata khi đổi marker, `converge` chỉ được append và không
  viết khi converged; phải giữ các invariant này với ngoại lệ update metadata hẹp.
- Backend Java 21/Spring Boot/Modulith/JPA/Flyway/MySQL tồn tại; chưa có persona code.
  Frontend chưa có thư mục riêng trước đợt này; baseline React + Vite đã chốt trong
  [constitution](../../.specify/memory/constitution.md).
- Persona spec đã tách nghiên cứu/thiết kế khỏi code. Việc tạo AGENT code không
  giao quyền sửa thiết kế hay tự khởi tạo ứng dụng.

Bằng chứng cho phạm vi là chỉ thị và các lựa chọn rõ của người dùng trong phiên
2026-10-24; không suy thêm người phê chuẩn hoặc dữ liệu hiệu quả thực tế của agent.

## 3. Giả định được phản biện

| Giả định | Rủi ro nếu giữ nguyên | Quyết định/kiểm chứng |
| --- | --- | --- |
| Sửa template là đủ | Checklist nhúng và companion Markdown thiếu field | Sửa cả luồng sinh/cập nhật, kiểm tra vị trí policy và fallback. |
| Author là agent viết file | Trái cách nhóm muốn quy trách nhiệm | Author/Last updated by là thành viên được cung cấp; tên xác nhận cho báo cáo này là Sơn. |
| Cấm mọi quyết định code | Dev phải dừng cả ở helper/tên biến | Cho phép chi tiết nội bộ tương đương; behavior/contract/integrity vẫn phải được chốt. |
| Mọi test fail đều do design | Log sai nguyên nhân, đẩy việc sửa code về người thiết kế | Phân biệt bug implementation, lỗi môi trường và design gap có bằng chứng. |
| Ghi đè log là gọn nhất | Mất diễn biến và căn cứ các lần bị chặn | Append bản ghi/diễn biến, giữ lịch sử và nguồn gỡ chặn. |
| Thiếu tên phải chặn cả error report | Agent không báo được blocker khẩn cấp | ERROR-LOG là báo cáo vận hành với reporter thực tế, được miễn gate danh tính SpecKit. |

## 4. Các cách tiếp cận đã đánh giá

| Cách nhìn vấn đề / phương án | Ưu điểm | Giới hạn | Kết luận |
| --- | --- | --- | --- |
| Chỉ là định dạng: sửa 5 template | Ít file, dễ review | Không bao phủ mẫu nhúng, fallback và quy tắc cập nhật | Không chọn. |
| Là hợp đồng workflow: template + policy chung + persona | Bao phủ nơi ghi, ít lặp chính sách, không thêm script | Vẫn cần agent nạp và tuân thủ, cần kiểm tra nguồn/diff | Được chọn. |
| Là bài toán cưỡng chế tự động: thêm script/hook điền và kiểm soát | Có thể xác thực máy ở các điểm được tích hợp | Tăng phạm vi, vẫn không tự biết danh tính người chịu trách nhiệm hoặc chứng minh design hoàn chỉnh | Chưa cần trong đợt này. |

Tác động: người thiết kế nhận blocker cụ thể; dev có quyền chi tiết nội bộ rõ;
người review phân biệt trách nhiệm metadata với approval; vận hành giữ bằng chứng
thay đổi đang dở và không bị agent reset dữ liệu/deploy để né thiếu thiết kế.

## 5. Thiết kế đã được người dùng chốt

- Bốn field đúng tên: Date created, Date updated, Author, Last updated by. Ngày
  YYYY-MM-DD theo Asia/Bangkok; tạo mới dùng cùng ngày và cùng thành viên.
- Khi sửa giữ provenance tạo, chỉ cập nhật hai field update khi có thay đổi thực.
  No-op giữ byte-for-byte; không backfill lịch sử hay suy người từ Git/OS/agent.
- Nếu chưa có thành viên chịu trách nhiệm, hỏi trước khi ghi artifact liên quan;
  tái sử dụng danh tính đã cung cấp đúng phạm vi. Không ảnh hưởng quy trình documents/.
- [Metadata policy](../../.specify/metadata-policy.md) áp dụng cho Markdown SpecKit,
  gồm companion documents; không chèn field Markdown vào OpenAPI/JSON/schema.
- [Backend AGENT](../../../ppswbs_backend/AGENT.md) và
  [frontend AGENT](../../../ppswbs_frontend/AGENT.md) đều là persona senior implement
  theo thiết kế, có gate chi tiết và cơ chế dừng toàn tác vụ.
- Chỗ thiếu/mâu thuẫn design buộc dừng code/test/scaffold/migration, kể cả việc
  song song; giữ hiện trạng và ghi ERROR-LOG.md cạnh AGENT tương ứng. Không tự
  sửa spec, tạo workaround hoặc dùng hook có mutation để tiếp tục.
- Log có ID, timestamp có offset, reporter, feature/task, nguồn design, gap,
  scenario/bằng chứng, ảnh hưởng, hiện trạng thay đổi/check và điều kiện tiếp tục.
  Append lịch sử; chỉ nhận lại sau yêu cầu tiếp tục, design được bổ sung/phê duyệt
  và gate được kiểm tra lại. Ghi log thất bại vẫn phải dừng.

## 6. Phạm vi triển khai và giới hạn

Đợt này sửa 5 template và 8 skill ghi tài liệu: specify, plan, tasks, checklist,
clarify, constitution, converge, implement. Thêm policy chung và hai AGENT;
frontend chỉ chứa AGENT. Giữ analyze read-only, reviewer-owned checklists và
append-only task history; không đổi mã ứng dụng, schema/migration, spec chi tiết,
design kit, cấu hình publishing hoặc cơ chế tự động nạp AGENT.

Không tạo ERROR-LOG rỗng hoặc bịa blocker. Với artifact legacy thiếu provenance,
không tự migrate khi workflow chỉ được đổi marker/append; báo thiếu metadata và
tách việc hiệu chỉnh provenance theo quyền được giao.

## 7. Kiểm chứng và tiêu chí thành công

Đã chạy resolver thật cho cả 5 template với `-Json`: đều trả đúng tên/nội dung
và đủ bốn field. Kiểm tra tĩnh xác nhận metadata nằm sau H1, nội dung template
ngoài metadata giữ nguyên, YAML frontmatter của tasks và 8 skill được bảo toàn;
không còn Created/Date chung trong các template và mẫu nhúng đã sửa.

Đã kiểm tra link của hai persona, mỗi persona có 10 tiêu chí trước code;
frontend chỉ có AGENT và không có ERROR-LOG thực được tạo.

Review thủ công các scenario trên nội dung hướng dẫn:

| Scenario | Kết quả quy định |
| --- | --- |
| Thiếu design trước code | Gate không đạt, dừng, ghi log trước scaffold/test. |
| Phát hiện gap giữa lúc implement | Dừng toàn tác vụ, bảo toàn thay đổi đang dở. |
| Bug code khi design rõ | Sửa implementation theo design, không tự quy lỗi design. |
| Blocker lặp hoặc blocker mới | Giữ log cũ, append diễn biến/ID phù hợp. |
| Ghi log thất bại | Vẫn dừng, báo lỗi ghi và không nhận đã tạo file. |
| Có design bổ sung | Kiểm tra yêu cầu tiếp tục, phê duyệt và gate; append căn cứ gỡ chặn. |
| Không có thay đổi tài liệu | Không refresh metadata hoặc rewrite file. |
| Chưa biết thành viên phụ trách | Hỏi trước khi ghi artifact; operational error report được miễn. |

Đây là review tĩnh hợp đồng hướng dẫn, không phải forward-test hành vi của một
agent khác hoặc bằng chứng ứng dụng chạy được. Không chạy test ứng dụng vì không
đổi ứng dụng và frontend chưa được khởi tạo.

Validator `skill-creator/quick_validate.py` không chấp nhận khóa `compatibility`
đã có trong cả 8 skill SpecKit. Giữ nguyên frontmatter này; YAML parse thành công
và so sánh với HEAD xác nhận không thay đổi. Không tuyên bố validator đó đã pass
hoặc xóa khóa có sẵn chỉ để làm xanh check.

## 8. Bàn giao và bước tiếp theo

Thông điệp cho nhóm: persona code sẽ triển khai phần thiết kế đã rõ; nếu phải
đoán behavior/contract, agent dừng và trả blocker có bằng chứng về người thiết kế.
Metadata ghi thành viên chịu trách nhiệm, không thay approval hoặc lịch sử Git.

Review diff và nạp persona phù hợp khi giao task tiếp theo. Khởi tạo React/Vite,
đồng bộ feature design cũ hoặc bổ sung provenance legacy là các nhiệm vụ riêng;
không phát sinh từ việc có AGENT hoặc template mới. Đợt này không tự commit/push.
