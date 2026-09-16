# Tổng quan dự án MOH

> **Snapshot Git-only — kiểm tra ngày 17/09/2026.** File này giúp nắm nhanh tình trạng dự án; nguồn chi tiết vẫn là report và tracker được liên kết bên dưới. Không dùng snapshot này để thay thế requirement, quyết định nhóm hoặc lịch sử thay đổi trong Git.

## Nhìn nhanh

| Nội dung | Trạng thái hiện tại |
| --- | --- |
| Baseline V1 | Đang validation. Group decision ngày 17/09 đã chốt 8 actor, cọc workshop 50%, Member retail và bàn giao vận chuyển bên thứ ba; policy chi tiết còn cần xác nhận. |
| Mục tiêu V1 | Hỗ trợ workshop làm nhẫn, order nhẫn custom do shop làm hộ và Member mua nhẫn có sẵn. |
| Phạm vi sản phẩm | Chỉ nhẫn; không phải ERP, CAD/3D hay web shop tổng quát. |
| Tiến độ | Chưa có mốc, ngân sách, phân công hay % hoàn thành được phê duyệt. |
| Mã nguồn ứng dụng | Chưa có mã nguồn runtime/application của MOH trong repository. |

## Scope và tính năng V1

1. **Booking workshop nhóm:** Guest và Member chọn cơ sở/ca. Mỗi package tạo invoice và yêu cầu cọc online 50% qua Payment Gateway; chỉ giao dịch được gateway xác nhận mới confirm booking. Lịch vận hành đề xuất gồm ba ca: 09:30–12:00, 13:00–15:30, 16:00–18:30; capacity theo cơ sở, huỷ/no-show và walk-in vẫn `Open/TBD`.
2. **Catalogue và cấu hình nhẫn:** chọn mẫu nhẫn đã xác nhận hoặc component được shop cho phép; staff có thể tư vấn trực tiếp.
3. **Tiếp nhận ảnh tham khảo:** chỉ gửi ảnh sang AI Vision API khi khách đã consent; AI trả candidate feature và quality, không tự thiết kế.
4. **Feasibility triage và audit override:** custom ngoài catalogue được auto-accept, staff-review hoặc auto-reject theo rule được phê duyệt; override phải có lý do và ngữ cảnh audit.
5. **Invoice, cọc và giá cuối:** invoice lưu cọc 50%; staff chỉ thêm phí phát sinh khi có lý do, actor, thời điểm và customer consent. Số dư cuối là 50% chưa thanh toán cộng phí phát sinh, được settlement cash/bank tại shop.
6. **Fulfilment và continuation:** khách tự làm tại workshop hoặc tạo order để shop làm; staff có thể ghi nhận custody và tạo booking tiếp tục cho sản phẩm làm dở.
7. **Check-in và settlement:** staff xác nhận số người thực sự tham gia làm sản phẩm, ghi phụ thu nếu có, và ghi nhận settlement cash/bank sau collection.
8. **Google Reviews research dashboard:** candidate cho owner/manager xem review theo cơ sở; không tự gán review cho nhân viên hoặc tự tính lương/thưởng.
9. **Member retail nhẫn có sẵn:** chỉ Member mua; full payment online được xác nhận trước khi reserve một đơn vị nhẫn, sau đó chọn pickup hoặc bàn giao cho đơn vị vận chuyển bên thứ ba.
10. **Quản trị kỹ thuật:** Admin Technical quản lý cấu hình, integration, monitoring và technical audit; không sửa invoice, payment, order hay dữ liệu nghiệp vụ.

## Actors và hành trình chính

| Actor | Tương tác với MOH |
| --- | --- |
| Guest | Booking không cần tài khoản, trả cọc, cung cấp design/reference có consent và nhận invoice/tracking. Không có retail checkout hoặc lịch sử Member. |
| Member | Có mọi khả năng booking của Guest; xem lịch sử tham gia, lịch sử giao dịch, workshop/event sắp tới và mua nhẫn có sẵn. Loyalty chưa triển khai. |
| Staff/Consultant | Quản lý session, tư vấn, review/override, adjustment có audit/consent, fulfilment, cash/bank settlement, delivery handoff, continuation booking và custody record. |
| Owner/Manager | Quản lý catalogue/component price, feasibility rule; xem audit/override và review dashboard research khi đủ điều kiện. |
| Admin Technical | Cấu hình kỹ thuật, integration, monitoring và technical audit; không có quyền thay đổi business record. |
| AI Vision API *(TBD)* | Nhận ảnh đã consent, trả quality và candidate feature. |
| Email Sender *(TBD)* | Gửi notification booking, review và order. |
| Payment Gateway *(TBD)* | Xác nhận cọc workshop hoặc full payment retail; redirect không tự xác nhận thanh toán. |

`Guest/Member booking → invoice + cọc 50% được gateway xác nhận → design/consult → feasibility → estimate → fulfilment → settlement cash/bank.`

`Member retail → full payment được gateway xác nhận → reserve một nhẫn có sẵn → pickup ready hoặc handed to third-party carrier.`

Khách có thể làm tiếp ở ca sau khi staff tạo continuation booking và ghi nhận custody cho work-in-progress item. Khi check-in, staff xác nhận số người thực sự cùng làm sản phẩm; phụ thu, nếu áp dụng, là 100.000 VND cho mỗi người thêm tham gia và chỉ ghi sau xác nhận.

Context chi tiết: [MOH Context Diagram](docs/report-1-vision-scope/assets/diagrams/moh-context.md).

## Ngoài scope V1

- Staff scheduling, HR, payroll và quản trị nhân sự; review không tự động tính lương/thưởng.
- Inventory đầy đủ, retail catalogue rộng, trang sức ngoài nhẫn và detailed production scheduling.
- POS, card-data storage, accounting/tax/bank reconciliation; policy cancellation/refund chưa chốt.
- Carrier integration, live shipment tracking, delivery failure, return và shipping refund sau khi staff đã bàn giao cho carrier.
- CAD/3D editor, tự tạo design từ ảnh, hoặc cam kết AI chính xác/tự động hoá tuyệt đối.

## Tình trạng tài liệu

| Nguồn | Trạng thái |
| --- | --- |
| [Vision & Scope](docs/report-1-vision-scope/front-matter.md) | Baseline V1 hiện hành, đã phản ánh group decision 17/09 ở mức cần validation. |
| [Project Plan](docs/report-2.0-project-plan/front-matter.md) | Có working baseline; milestone, budget, delivery role, governance và approval process vẫn chưa được duyệt. |
| [SRS](docs/report-3.0-srs/front-matter.md) | Có phạm vi requirement ban đầu; cần chi tiết hóa rule, dữ liệu và authorization đã mở. |
| [FDS](docs/report-3.2-fds/front-matter.md) và [Screen Design](docs/report-3.2-screen-design-spec/front-matter.md) | Cần chốt journey, field, validation, screen inventory và wireframe. |
| [TDS](docs/report-4-tds/front-matter.md) | Chưa có architecture, hosting, data, auth, integration hay security decision được phê duyệt. |
| [Test Plan](docs/report-5.0-test-plan/front-matter.md) | Acceptance scenarios đã có baseline; environment, owner, quality threshold và test result chưa được duyệt. |

## Tình trạng mã nguồn và vận hành tài liệu

- Repository có **tooling Python cho tài liệu**, gồm generate DOCX, Git history/GitHub Issue snapshot, Google Workspace synchronization và unit tests; đây không phải mã nguồn sản phẩm MOH.
- GitHub Actions validate và publish source tài liệu/tracker lên Google Workspace **chỉ từ nhánh `develop`**. Đồng bộ có kết quả theo từng target để target lỗi không chặn target khác.
- Các report/tracker Git-first là nguồn chỉnh sửa; Google Docs/Sheets là bề mặt publish/review. Không đưa credentials, Drive ID hay dữ liệu khách vào snapshot.

## Việc cần làm để chốt baseline

| Nhóm cần xác nhận | Tình trạng | Nguồn theo dõi |
| --- | --- | --- |
| Booking, invoice & payment rules | Capacity từng cơ sở, cancellation/refund, consent wording, gateway webhook/reconciliation và final settlement. | [QA](trackers/report-2.1-project-tracking/QA.csv), [Open Issues](trackers/report-3.1-rtw/8-OpenIssues.csv) |
| Component taxonomy, catalogue, price và hard constraint | Open | [QA](trackers/report-2.1-project-tracking/QA.csv), [Open Issues](trackers/report-3.1-rtw/8-OpenIssues.csv) |
| Feasibility evidence, rule governance, threshold và evaluation | Open; research prototype under validation | [QA](trackers/report-2.1-project-tracking/QA.csv), [Risks](trackers/report-2.1-project-tracking/Risks.csv) |
| Image consent/retention, review coverage, email và guest code | Open | [QA](trackers/report-2.1-project-tracking/QA.csv), [Risks](trackers/report-2.1-project-tracking/Risks.csv) |
| Member retail & delivery handoff | Package/retail catalogue, reservation expiry, pickup readiness, handoff evidence và delivery-data retention. | [Open Issues](trackers/report-3.1-rtw/8-OpenIssues.csv), [Risks](trackers/report-2.1-project-tracking/Risks.csv) |
| Technical administration | Provider selection, Admin Technical authorization, monitoring và technical-audit boundaries. | [Open Issues](trackers/report-3.1-rtw/8-OpenIssues.csv), [QA](trackers/report-2.1-project-tracking/QA.csv) |
| Google Reviews access, privacy, staff attribution và approval | Research-gated / Open | [QA](trackers/report-2.1-project-tracking/QA.csv), [Open Issues](trackers/report-3.1-rtw/8-OpenIssues.csv), [Risks](trackers/report-2.1-project-tracking/Risks.csv) |

**Thứ tự tiếp theo:** thu evidence và chốt các mục `Open/TBD` → baseline requirement và trace trong RTW → chi tiết FDS/UI/TDS → lập kế hoạch implementation và test scenario.

## Quy ước cập nhật snapshot

Snapshot chỉ đúng tại ngày kiểm tra ghi ở đầu file. Khi có quyết định nhóm đã được duyệt, thay đổi tài liệu, hoặc mã nguồn ứng dụng thực sự được thêm vào repository, cập nhật mục liên quan và liên kết lại nguồn canonical. Không chuyển một assumption, risk hoặc câu hỏi mở thành requirement đã xác nhận chỉ bằng việc cập nhật file này.
