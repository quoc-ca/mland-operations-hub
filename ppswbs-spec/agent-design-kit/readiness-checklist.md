# Checklist sẵn sàng triển khai

Dùng cùng [persona](../AGENT.md), [hướng dẫn](README.md), [mẫu thiết kế](implementation-design-template.md). Đây là đánh giá **điều kiện nhận việc**, không phải checklist reviewer của feature hoặc bằng chứng code hoàn tất. Không tick file mẫu này hay sửa marker reviewer để vượt gate.

Ghi trong phản hồi/hồ sơ tác vụ: ĐẠT, KHÔNG ĐẠT hoặc KHÔNG ÁP DỤNG — <lý do>, kèm nguồn cụ thể cho từng tiêu chí. Không bắt tạo file đánh giá mới. Các tiêu chí về phạm vi, bộ mẫu, thiết kế được duyệt, tính nhất quán và truy vết không được bỏ qua. Chỉ loại khía cạnh thực sự không liên quan bằng lý do.

## Tiêu chí

| ID | Tiêu chí đạt |
| --- | --- |
| R01 | Phân biệt triển khai với công việc tài liệu; có yêu cầu, đầu ra, phạm vi rõ. Review/soạn tài liệu được giao không bị chặn chỉ vì chưa có thiết kế code. |
| R02 | Đọc đủ bốn file vận hành bắt buộc trong README; không lấy kit tự dựng hoặc sự tồn tại folder làm bằng chứng. |
| R03 | Đúng nguồn hiện hành, constitution, ngoại lệ; nguồn có thẩm quyền nhất quán. Không lấy prototype/code làm quyết định đã duyệt. |
| R04 | Spec, plan, task list được duyệt đúng phạm vi với bằng chứng xác định nội dung/phiên bản và người có thẩm quyền. File có sẵn/checkbox/code chạy không tự chứng minh duyệt. |
| R05 | Các mục mẫu có nội dung hoặc ánh xạ nguồn cụ thể, không điền hình thức kiểu “xử lý phù hợp”; không cần sao chép thiết kế hoặc mô tả từng biến. |
| R06 | Phạm vi/acceptance criteria quan sát và kiểm chứng được; yêu cầu → thiết kế → task → scenario/check truy vết được. |
| R07 | Stack/version, kiến trúc, ownership và dependency liên quan đã chốt, tương thích baseline. Dependency sẵn sàng hoặc có thứ tự thực hiện rõ trong thiết kế. |
| R08 | Luồng chính, lỗi/phục hồi đủ để code mà không tự quyết hành vi quan sát được hoặc kết quả nghiệp vụ. |
| R09 | Contract, validation/error, compatibility rõ khi có interface/API/consumer bị tác động. |
| R10 | Actor, auth, quyền trên tài nguyên, đường từ chối, audit và dữ liệu nhạy cảm rõ khi liên quan. |
| R11 | Ownership/schema, invariant, trạng thái/chuyển trạng thái, transaction/partial failure rõ khi tác động dữ liệu. |
| R12 | Migration, baseline DB, bảo toàn/upgrade/recovery rõ khi đổi schema/dữ liệu; quyền code không tự cho phép reset. |
| R13 | Concurrency, retry/idempotency, duplicate/late/out-of-order event có cơ chế và kết quả đã chốt khi liên quan. |
| R14 | Boundary/provider, xác thực kết quả, lỗi/phục hồi tích hợp rõ khi có hệ thống ngoài. |
| R15 | UI, trạng thái phản hồi, fallback/baseline chất lượng liên quan rõ; ngoại lệ JavaScript chỉ đúng phạm vi ratify. |
| R16 | Vận hành/config/deploy, giới hạn quyền, rollout/rollback hoặc recovery, quan sát lỗi được chốt khi liên quan. |
| R17 | Spec/plan/tasks/contracts nhất quán; feature đã qua speckit-analyze, không còn vấn đề ảnh hưởng tác vụ chưa giải quyết. |
| R18 | Không còn unknown/TBD/placeholder/quyết định chưa duyệt ảnh hưởng tác vụ. Phần chưa chốt ngoài phạm vi có căn cứ không phụ thuộc, không bị tự điền giá trị. |
| R19 | Kế hoạch kiểm chứng có check/lệnh hoặc manual check thích hợp, môi trường, kết quả mong đợi; bằng chứng bắt buộc không bị tự miễn. |
| R20 | Phần giao tự quyết chỉ là chi tiết nội bộ giữ nguyên hành vi/contract/giới hạn. Không bắt duyệt tên biến cục bộ nhưng không coi field public/locking là nội bộ. |

## Kết luận và hiệu lực

- SẴN SÀNG TRIỂN KHAI: mọi tiêu chí áp dụng đạt với bằng chứng, không thiếu quyết định bắt buộc. Đánh giá này không thay phê duyệt hoặc tự cấp quyền code/deploy.
- BỊ CHẶN: một tiêu chí bắt buộc không đạt cũng dừng toàn tác vụ triển khai, gồm production/test code, scaffold, migration, cấu hình triển khai. Trả [báo cáo thiếu](blocker-report-template.md); không tự làm phần độc lập.
- CÔNG VIỆC TÀI LIỆU: review/nghiên cứu/soạn tài liệu theo yêu cầu; phần đề xuất chưa duyệt được ghi rõ, không suy ra quyền code. Chuyển sang triển khai phải đánh giá lại đầy đủ.

Phát hiện thiết kế đổi, mâu thuẫn mới, scope creep hoặc quyết định còn thiếu giữa lúc code làm kết luận cũ hết hiệu lực với tác vụ đang triển khai: dừng, báo hiện trạng. Chỉ đánh giá tiếp phần độc lập sau khi người dùng tách/giao lại phạm vi, hoặc đánh giá lại toàn tác vụ sau khi thiết kế được bổ sung/duyệt đúng phần ảnh hưởng.
