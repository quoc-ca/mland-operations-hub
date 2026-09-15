# Tổng quan dự án MOH

> **Snapshot Git-only — kiểm tra ngày 15/09/2026.** File này giúp nắm nhanh tình trạng dự án; nguồn chi tiết vẫn là report và tracker được liên kết bên dưới. Không dùng snapshot này để thay thế requirement, quyết định nhóm hoặc lịch sử thay đổi trong Git.

## Nhìn nhanh

| Nội dung | Trạng thái hiện tại |
| --- | --- |
| Baseline | V1 đang được xác thực từ ngày 15/09/2026. |
| Mục tiêu V1 | Hỗ trợ hành trình workshop làm nhẫn và order nhẫn custom do shop làm hộ. |
| Phạm vi sản phẩm | Chỉ nhẫn; không phải ERP hay web shop tổng quát. |
| Tiến độ | Chưa có mốc, ngân sách, phân công hay % hoàn thành được phê duyệt. |
| Mã nguồn ứng dụng | Chưa có mã nguồn runtime/application của MOH trong repository. |

## Scope và tính năng V1

1. **Booking workshop nhóm:** khách truy cập không cần tài khoản, chọn session nhẫn có capacity cố định, nhận email xác nhận và mã tra cứu.
2. **Catalogue và cấu hình nhẫn:** chọn mẫu nhẫn đã xác nhận hoặc component được shop cho phép; staff có thể tư vấn trực tiếp.
3. **Tiếp nhận ảnh tham khảo:** chỉ gửi ảnh sang AI Vision API khi khách đã consent; AI trả candidate feature và quality, không tự thiết kế.
4. **Feasibility triage và audit override:** custom ngoài catalogue được auto-accept, staff-review hoặc auto-reject theo rule được phê duyệt; override phải có lý do và ngữ cảnh audit.
5. **Estimate và consent giá cuối:** estimate có sau khi design feasible/review xong; thay đổi final invoice phải được khách chấp thuận.
6. **Fulfilment nhẫn:** khách tự làm tại workshop hoặc tạo order để shop làm; order shop-made đi qua trạng thái đơn giản tới pickup.
7. **Ghi nhận payment khi nhận hàng:** staff ghi nhận cash hoặc bank transfer sau collection; không xử lý card data.

## Actors và hành trình chính

| Actor | Tương tác với MOH |
| --- | --- |
| Customer (guest, VI/EN) | Booking, thiết kế/tư vấn, consent ảnh, nhận estimate và kết quả, tra cứu bằng tracking code, consent giá cuối. |
| Shop staff / consultant | Quản lý session, tư vấn, review/override, cập nhật fulfilment, lập invoice cuối và ghi nhận payment thủ công. |
| Owner / manager | Quản lý catalogue/component price, feasibility rule; xem audit và override. |
| AI Vision API *(TBD)* | Nhận ảnh đã consent, trả quality và candidate feature. |
| Email delivery service *(TBD)* | Gửi notification booking, review và order. |

Luồng chính: **booking → thiết kế hoặc tư vấn → feasibility → estimate → tự làm tại workshop hoặc shop-made order → pickup/collection → ghi nhận payment**.

Context chi tiết: [MOH Context Diagram](docs/report-1-vision-scope/assets/diagrams/moh-context.mmd).

## Ngoài scope V1

- Staff scheduling, HR, payroll và quản trị nhân sự.
- Inventory đầy đủ, retail catalogue rộng, trang sức ngoài nhẫn và detailed production scheduling.
- Payment gateway, POS, card data, accounting, tax và bank reconciliation.
- CAD/3D editor, tự tạo design từ ảnh, hoặc cam kết AI chính xác/tự động hoá tuyệt đối.

## Tình trạng tài liệu

| Nguồn | Trạng thái |
| --- | --- |
| [Vision & Scope](docs/report-1-vision-scope/front-matter.md) | Baseline V1 hiện hành; scope, feature và context đã được xác định ở mức cần validation. |
| [Project Plan](docs/report-2.0-project-plan/front-matter.md) | Có working baseline; milestone, budget, delivery role, governance và approval process vẫn chưa được duyệt. |
| [SRS](docs/report-3.0-srs/front-matter.md) | Có phạm vi requirement ban đầu; cần chi tiết hóa sau khi stakeholder xác nhận rule. |
| [FDS](docs/report-3.2-fds/front-matter.md) và [Screen Design](docs/report-3.2-screen-design-spec/front-matter.md) | Cần chốt journey, field, validation, screen inventory và wireframe. |
| [TDS](docs/report-4-tds/front-matter.md) | Chưa có architecture, hosting, data, auth, integration hay security decision được phê duyệt. |
| [Test Plan](docs/report-5.0-test-plan/front-matter.md) | Chưa có environment, acceptance owner, quality threshold hay test result được phê duyệt. |

## Tình trạng mã nguồn và vận hành tài liệu

- Repository hiện có **tooling Python cho tài liệu**, gồm generate DOCX, Git history/GitHub Issue snapshot, Google Workspace synchronization và unit tests; đây không phải mã nguồn sản phẩm MOH.
- GitHub Actions validate và publish source tài liệu/tracker lên Google Workspace **chỉ từ nhánh `develop`**. Đồng bộ Google Workspace có kết quả theo từng target để target lỗi không chặn target khác.
- Các report/tracker Git-first là nguồn chỉnh sửa; Google Docs/Sheets là bề mặt publish/review. Không đưa credentials, Drive ID hay dữ liệu khách vào snapshot.

## Việc cần làm để chốt baseline

| Nhóm cần xác nhận | Tình trạng | Nguồn theo dõi |
| --- | --- | --- |
| Session duration, capacity, cancellation và no-show | Open | [QA Q-001](trackers/report-2.1-project-tracking/QA.csv), [Open Issue OI-001](trackers/report-3.1-rtw/8-OpenIssues.csv) |
| Component taxonomy, catalogue, price và hard constraint | Open | [QA Q-002](trackers/report-2.1-project-tracking/QA.csv), [Open Issue OI-002](trackers/report-3.1-rtw/8-OpenIssues.csv) |
| Feasibility evidence, rule governance, threshold và evaluation | Open; research prototype under validation | [QA Q-003](trackers/report-2.1-project-tracking/QA.csv), [Risk R-001](trackers/report-2.1-project-tracking/Risks.csv), [Open Issue OI-003](trackers/report-3.1-rtw/8-OpenIssues.csv) |
| Image consent/retention, review coverage, email và guest code | Open | [QA Q-004](trackers/report-2.1-project-tracking/QA.csv), [Risk R-002](trackers/report-2.1-project-tracking/Risks.csv), [Open Issue OI-004](trackers/report-3.1-rtw/8-OpenIssues.csv) |
| Final-invoice consent và cash/bank-transfer payment record | Open | [Open Issue OI-005](trackers/report-3.1-rtw/8-OpenIssues.csv) |

**Thứ tự tiếp theo:** thu evidence và chốt các mục `Open/TBD` → baseline requirement và trace trong RTW → chi tiết FDS/UI/TDS → lập kế hoạch implementation và test scenario.

## Quy ước cập nhật snapshot

Snapshot chỉ đúng tại ngày kiểm tra ghi ở đầu file. Khi có quyết định nhóm đã được duyệt, thay đổi tài liệu, hoặc mã nguồn ứng dụng thực sự được thêm vào repository, cập nhật mục liên quan và liên kết lại nguồn canonical. Không chuyển một assumption, risk hoặc câu hỏi mở thành requirement đã xác nhận chỉ bằng việc cập nhật file này.
