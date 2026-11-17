# Mẫu thiết kế triển khai: [Tên tác vụ / feature]

Đây là mẫu trống, **không phải thiết kế đã duyệt**. Có thể ánh xạ từng mục sang nguồn hiện có thay vì tạo bản sao. Đọc [hướng dẫn](README.md) trước khi dùng; không tự điền quyết định ngoài thẩm quyền để làm mẫu có vẻ hoàn tất.

## 1. Đầu ra, phạm vi và nguồn

- Loại công việc: [triển khai / review / nghiên cứu / soạn tài liệu].
- Mục tiêu và kết quả quan sát được: [ai cần gì, xác định hoàn thành thế nào].
- Trong/ngoài phạm vi: [hành vi, vùng tác động và phần loại trừ].
- Feature/thiết kế sở hữu: [đường dẫn, mục và phiên bản/mốc nội dung].
- Baseline/constitution, ngoại lệ liên quan: [nguồn và điều kiện áp dụng].

| Yêu cầu / scenario | Nguồn cụ thể | Thiết kế đáp ứng | Task và dependency | Cách kiểm chứng |
| --- | --- | --- | --- | --- |
| [ID hoặc mô tả] | [file + mục] | [nội dung hoặc dẫn chiếu] | [task + thứ tự] | [check/scenario + kết quả mong đợi] |

## 2. Quyết định và bằng chứng duyệt

Ghi quyết định kế thừa và quyết định mới liên quan. Thiếu lựa chọn quan trọng thì ghi chưa chốt; không tự chọn mặc định. Không bắt duyệt lại quyết định đã có bằng chứng hợp lệ và còn đúng phạm vi.

| Quyết định | Giá trị / phương án đã chốt | Nguồn và bằng chứng duyệt | Người có thẩm quyền | Phạm vi / phiên bản |
| --- | --- | --- | --- | --- |
| [ví dụ: stack hoặc quyền thao tác] | [đã chốt hoặc chưa chốt] | [chỉ thị/bản ghi cụ thể] | [người duyệt; chưa rõ thì ghi rõ] | [nội dung được duyệt] |

- Unknown/mâu thuẫn còn lại: [nội dung, ảnh hưởng, người cần chốt].
- Unknown ngoài tác vụ: [nguồn và căn cứ không ảnh hưởng/dependency; hoặc không có].
- Tính nhất quán spec/plan/tasks/contracts và phân tích speckit-analyze: [kết quả, nguồn và vấn đề chưa giải quyết].

## 3. Thành phần và luồng xử lý

- Stack/version, môi trường, dependency được phép: [baseline kế thừa và phần feature đã chốt].
- Module/thành phần sở hữu, ranh giới truy cập, vùng được sửa: [trách nhiệm, đầu vào/đầu ra, dependency].
- Luồng chính: [trigger → kiểm tra → xử lý → kết quả; thứ tự và trách nhiệm].
- Nhánh thất bại/phục hồi: [điểm lỗi, kết quả với người dùng/dữ liệu, hành động tiếp theo].
- Chi tiết nội bộ được tự quyết: [giới hạn giữ nguyên hành vi/contract; không cần liệt kê mọi biến/hàm].

## 4. Contract, quyền hạn và tích hợp

Mỗi khía cạnh cần nội dung, nguồn cụ thể hoặc lý do không áp dụng.

- API/interface: [input/output, field/validation, error/status, compatibility, consumer bị ảnh hưởng].
- Actor/authentication/authorization: [ai được làm gì trên tài nguyên nào; đường từ chối].
- Tích hợp: [provider/boundary đã chọn, bằng chứng tin cậy, timeout/failure/retry/idempotency liên quan].
- Dữ liệu nhạy cảm: [thu thập/lưu/log/che/xóa thế nào, nguồn retention đã chốt].

## 5. Dữ liệu, trạng thái và tính toàn vẹn

- Ownership/schema/ràng buộc: [dữ liệu, chủ sở hữu, unique/null/invariant liên quan].
- Vòng đời: [trạng thái, trigger/actor, điều kiện chuyển và chuyển bị cấm; hoặc lý do không có state].
- Transaction/partial failure: [ranh giới, rollback/phục hồi, tác dụng phụ, audit].

### Khi có thay đổi schema/dữ liệu

[Migration có version, baseline DB, bảo toàn/upgrade dữ liệu, recovery và quyền theo môi trường. Không tự chọn reset. Nếu không áp dụng, ghi lý do.]

### Khi có cạnh tranh, callback hoặc tác vụ lặp

[Cơ chế concurrency đã chốt, tranh chấp, duplicate/out-of-order/late event, idempotency và kết quả mong đợi. Nếu không áp dụng, ghi lý do.]

### Khi có UI/journey

[Interaction, validation/empty/loading/error/success liên quan, progressive enhancement/fallback và baseline accessibility/browser đã chốt. Nếu không áp dụng, ghi lý do.]

### Khi có ảnh hưởng deploy/vận hành

[Môi trường, cấu hình/secret qua kênh được phép, rollout/rollback hoặc recovery, quan sát lỗi, người chịu trách nhiệm. Quyền code không phải quyền deploy. Nếu không áp dụng, ghi lý do.]

## 6. Task, nghiệm thu và bằng chứng

- Task list được duyệt: [nguồn, phạm vi và dependency; không tạo task nghiệp vụ ngầm].
- Acceptance scenario: [thành công, lỗi, từ chối quyền và edge case liên quan; nguồn].
- Kiểm chứng: [check/lệnh, môi trường, đầu vào, kết quả mong đợi, bằng chứng cần giữ].
- Check không khả dụng: [lý do, manual check nếu có; thiếu bằng chứng bắt buộc thì không nhận hoàn tất].
- Điều kiện dừng/nhận lại: [quyết định còn thiếu, thay đổi nào cần đánh giá/duyệt lại].

Đánh giá bằng [checklist](readiness-checklist.md), không tự phê duyệt thiết kế. Khi thiếu, trả [báo cáo bị chặn](blocker-report-template.md) và chờ.
