# Tổng quan dự án

> **Snapshot Git-only — kiểm tra ngày 18/09/2026.** File này giúp nắm nhanh tình trạng dự án; nguồn chi tiết vẫn là report và tracker được liên kết bên dưới. Không dùng snapshot này để thay thế requirement, quyết định nhóm hoặc lịch sử thay đổi trong Git.

| Thông tin dự án | Giá trị |
| --- | --- |
| Nhóm | SEP490_G22 |
| Tên tiếng Anh | Personalized Product Sales and Workshop Booking System |

## Nhìn nhanh

| Nội dung | Trạng thái hiện tại |
| --- | --- |
| Baseline V1 | Đang validation. Group decisions 17/09 và 18/09 đã chốt 8 actor, payment boundary, session/booking rules, catalogue/AI direction, Google Review scope có điều kiện và Member retail; dữ liệu cấu hình cùng gateway contract còn mở. |
| Mục tiêu V1 | Hỗ trợ workshop làm nhẫn, order nhẫn custom do shop làm hộ và Member mua nhẫn có sẵn. |
| Phạm vi sản phẩm | Chỉ nhẫn; không phải ERP, CAD/3D hay web shop tổng quát. |
| Tiến độ | Chưa có mốc, ngân sách, phân công hay % hoàn thành được phê duyệt. |
| Mã nguồn ứng dụng | Chưa có mã nguồn runtime/application của dự án trong repository. |

## Scope và tính năng V1

1. **Booking workshop nhóm:** Guest và Member chọn cơ sở/ca. Mỗi package tạo invoice và yêu cầu cọc online 50% qua Payment Gateway; chỉ giao dịch được gateway xác nhận mới confirm booking. Có ba ca 09:30–12:00, 13:00–15:30, 16:00–18:30; capacity cấu hình theo cơ sở/ca. Đổi lịch cần trước 24 giờ và ca còn chỗ; no-show mất booking/cọc trừ ngoại lệ Staff/Owner có audit.
2. **Catalogue và cấu hình nhẫn:** chọn mẫu nhẫn đã xác nhận hoặc component do Owner xác nhận cùng giá, độ khó và hard constraint; Staff có thể tư vấn trực tiếp.
3. **Tiếp nhận ảnh tham khảo:** chỉ gửi ảnh sang AI Vision API khi khách đã consent; AI trả candidate feature và quality, không tự thiết kế, chốt giá hay chốt khả thi. Ảnh request được duyệt giữ tới khi hoàn tất order; request bị từ chối/rút consent bị xoá.
4. **Feasibility triage và audit override:** custom ngoài catalogue được route simple/auto-accept, medium/Staff review, advanced/Owner review hoặc impossible/auto-reject theo rule Owner cấu hình; override phải có lý do và ngữ cảnh audit.
5. **Invoice, cọc và giá cuối:** invoice lưu cọc 50%; staff chỉ thêm phí phát sinh khi có lý do, actor, thời điểm và customer consent. Số dư cuối là 50% chưa thanh toán cộng phí phát sinh, được settlement cash/bank tại shop.
6. **Fulfilment và continuation:** khách tự làm tại workshop hoặc tạo order để shop làm; Staff chỉ tạo booking tiếp tục khi khách yêu cầu và ca còn chỗ, đồng thời ghi nhận custody/release có audit cho sản phẩm làm dở.
7. **Check-in và settlement:** Staff xác nhận số người thực sự tham gia làm sản phẩm. Phụ thu là 100.000 VND cho mỗi người thêm chưa booking nhưng có tham gia làm; điều chỉnh cần customer consent, còn miễn/đổi phí do Owner duyệt có audit. Staff ghi nhận settlement cash/bank sau collection.
8. **Google Reviews visibility:** website cung cấp Google review link/QR cho khách tự đánh giá. Sau khi đạt access/governance prerequisite, Owner có thể manual import một chiều Google → hệ thống để hiển thị cho Owner và công khai trên website; không tự gán review cho nhân viên hoặc tự tính lương/thưởng.
9. **Member retail nhẫn có sẵn:** chỉ Member mua; full payment online được xác nhận trước khi reserve một đơn vị nhẫn. Reservation không tự hết hạn trong V1; khách chọn pickup hoặc bàn giao cho đơn vị vận chuyển bên thứ ba, với bằng chứng handoff tối thiểu.
10. **Quản trị kỹ thuật:** Admin Technical quản lý cấu hình, integration, monitoring và technical audit; không sửa invoice, payment, order hay dữ liệu nghiệp vụ.

## Các rule đã chốt — 18/09/2026

- Ba ca mỗi ngày; capacity được cấu hình riêng theo cơ sở/ca, không hard-code chung.
- Đổi lịch trước tối thiểu 24 giờ và cần ca còn chỗ; no-show chỉ có ngoại lệ khi Staff/Owner lưu audit.
- Phụ thu 100.000 VND chỉ tính cho người thêm thực sự làm sản phẩm; Staff xác nhận, khách consent adjustment và Owner duyệt miễn/đổi phí.
- Continuation do Staff tạo theo yêu cầu khách và capacity; custody/release cần evidence, booking code/QR và Staff check.
- Configurator chỉ dùng catalogue Owner xác nhận; AI chỉ phân loại candidate feature; feasibility routing có audit và không cam kết accuracy.
- Google Review bắt đầu bằng link/QR; import một chiều chỉ do Owner kích hoạt sau khi đủ access và governance.
- Ready-ring reservation không tự hết hạn; delivery kết thúc tại handoff có carrier, reference, Staff và timestamp.

## Payment Gateway — chưa chốt

Payment Gateway vẫn là blocker nghiệp vụ/kỹ thuật. Chưa có quyết định về provider, webhook/IPN contract, xác minh chữ ký/trạng thái, đối soát, retry, duplicate-event handling hay refund. Browser redirect chỉ là trải nghiệm điều hướng và **không** được coi là thanh toán thành công; không được triển khai hoặc chọn provider trước khi nhóm chốt các mục này.

## Actors và hành trình chính

| Actor | Tương tác với hệ thống |
| --- | --- |
| Guest | Booking không cần tài khoản, trả cọc, cung cấp design/reference có consent và nhận invoice/tracking. Không có retail checkout hoặc lịch sử Member. |
| Member | Có mọi khả năng booking của Guest; xem lịch sử tham gia, lịch sử giao dịch, workshop/event sắp tới và mua nhẫn có sẵn. Loyalty chưa triển khai. |
| Staff/Consultant | Quản lý session, tư vấn, review/override, adjustment có audit/consent, fulfilment, cash/bank settlement, delivery handoff, continuation booking và custody record. |
| Owner/Manager | Quản lý catalogue/component price, feasibility rule; xem audit/override, manual Google Review import và review visibility khi đủ điều kiện. |
| Admin Technical | Cấu hình kỹ thuật, integration, monitoring và technical audit; không có quyền thay đổi business record. |
| AI Vision API *(TBD)* | Nhận ảnh đã consent, trả quality và candidate feature. |
| Email Sender *(TBD)* | Gửi notification booking, review và order. |
| Payment Gateway *(chưa chốt)* | Sẽ xác nhận cọc workshop hoặc full payment retail; redirect không tự xác nhận thanh toán. Provider và toàn bộ webhook/IPN/reconciliation/refund contract vẫn `Open`. |

`Guest/Member booking → invoice + cọc 50% được gateway xác nhận → design/consult → feasibility → estimate → fulfilment → settlement cash/bank.`

`Member retail → full payment được gateway xác nhận → reserve một nhẫn có sẵn → pickup ready hoặc handed to third-party carrier.`

Khách có thể làm tiếp ở ca sau khi Staff tạo continuation booking theo yêu cầu và ghi nhận custody/release cho work-in-progress item. Khi check-in, Staff xác nhận số người thực sự cùng làm sản phẩm; phụ thu là 100.000 VND cho mỗi người thêm chưa booking có tham gia làm, chỉ ghi sau Staff attestation và customer consent.

Context chi tiết: [Project Context Diagram](docs/report-3-software-requirement-specification/assets/diagrams/context.puml).

## Ngoài scope V1

- Staff scheduling, HR, payroll và quản trị nhân sự; review không tự động tính lương/thưởng.
- Inventory đầy đủ, retail catalogue rộng, trang sức ngoài nhẫn và detailed production scheduling.
- POS, card-data storage, accounting/tax/bank reconciliation; policy cancellation/refund chưa chốt.
- Carrier integration, live shipment tracking, delivery failure, return và shipping refund sau khi staff đã bàn giao cho carrier.
- CAD/3D editor, tự tạo design từ ảnh, hoặc cam kết AI chính xác/tự động hoá tuyệt đối.

## Tình trạng tài liệu

| Nguồn | Trạng thái |
| --- | --- |
| [Report 1](docs/report-1-project-introduction/front-matter.md) | Active SEP490 introduction and scope baseline. |
| [Report 2](docs/report-2-project-management-plan/front-matter.md) | Planning baseline; milestone, budget, roles, and approval remain TBD. |
| [Report 3](docs/report-3-software-requirement-specification/front-matter.md) | Active requirements, business flows, permission boundary, and committed PUML source. |
| [Report 4](docs/report-4-software-design-specification/front-matter.md) | Architecture and implementation design remain TBD until an application baseline is approved. |
| [Report 5](docs/report-5.0-test-documentation/front-matter.md) | Test planning only; no result, environment, schedule, or sign-off is claimed. |

## Tình trạng mã nguồn và vận hành tài liệu

- Repository có **tooling Python cho tài liệu**, gồm generate DOCX, Git history/GitHub Issue snapshot, Google Workspace synchronization và unit tests; đây không phải mã nguồn sản phẩm.
- GitHub Actions validate và publish source tài liệu/tracker lên Google Workspace **chỉ từ nhánh `develop`**. Đồng bộ có kết quả theo từng target để target lỗi không chặn target khác.
- Các report/tracker Git-first là nguồn chỉnh sửa; Google Docs/Sheets là bề mặt publish/review. Không đưa credentials, Drive ID hay dữ liệu khách vào snapshot.

## Việc cần làm để chốt baseline

| Nhóm cần xác nhận | Tình trạng | Nguồn theo dõi |
| --- | --- | --- |
| Booking, invoice & payment rules | Capacity từng cơ sở/ca, manual refund/force majeure, consent wording, final settlement và toàn bộ gateway contract. | [Q&A](trackers/project-tracking/Q&A.csv), [Issues](trackers/project-tracking/Issues.csv) |
| Component taxonomy, catalogue, price và hard constraint | Partially answered; Owner cần cung cấp/duyệt dữ liệu thực tế. | [Q&A](trackers/project-tracking/Q&A.csv), [Issues](trackers/project-tracking/Issues.csv) |
| Feasibility evidence, rule governance, threshold và evaluation | Partially answered; research prototype vẫn under validation. | [Q&A](trackers/project-tracking/Q&A.csv), [Issues](trackers/project-tracking/Issues.csv) |
| Image consent/retention, review coverage, email và guest code | Partially answered; consent wording, provider retention và coverage còn mở. | [Q&A](trackers/project-tracking/Q&A.csv), [Issues](trackers/project-tracking/Issues.csv) |
| Member retail & delivery handoff | Partially answered; pickup/manual exception, recipient-data retention và failed-handoff handling còn mở. | [Issues](trackers/project-tracking/Issues.csv) |
| Technical administration & Payment Gateway | Payment provider, webhook/IPN, reconciliation, retry, duplicate events và refund vẫn `Open` do tranh chấp nhóm. | [Issues](trackers/project-tracking/Issues.csv), [Q&A](trackers/project-tracking/Q&A.csv) |
| Google Reviews access, privacy, staff attribution và approval | Partially answered; verified access và governance là điều kiện trước import. | [Q&A](trackers/project-tracking/Q&A.csv), [Issues](trackers/project-tracking/Issues.csv) |

**Thứ tự tiếp theo:** thu evidence và chốt các mục `Open/TBD` → baseline requirement và trace trong RTW → chi tiết FDS/UI/TDS → lập kế hoạch implementation và test scenario.

## Quy ước cập nhật snapshot

Snapshot chỉ đúng tại ngày kiểm tra ghi ở đầu file. Khi có quyết định nhóm đã được duyệt, thay đổi tài liệu, hoặc mã nguồn ứng dụng thực sự được thêm vào repository, cập nhật mục liên quan và liên kết lại nguồn canonical. Không chuyển một assumption, risk hoặc câu hỏi mở thành requirement đã xác nhận chỉ bằng việc cập nhật file này.
