# UI Flow Design: 003.2 Workshop Operations

**Date created**: 2026-11-02
**Date updated**: 2026-11-27
**Author**: Hồ Hoàng Quốc
**Last updated by**: Hồ Hoàng Quốc

## Scope and sources

- Business rules: `spec.md` (packages, locations, slots, sessions, capacity, attendance, check-in, cancellation handoff).
- Transport contract: `contracts/workshop-operations.openapi.yaml`.
- Current implementation reference: `ppswbs_frontend/src/workshop-operations/`.
- This pack describes internal operational UI; it does not grant authorization to the client.

## Actors, routes, and authorization

| Actor | Route | Access rule |
| --- | --- | --- |
| Manager | `/operations/sessions` | Global Feature 003 scope; can manage packages, locations, slots, sessions, assignments, attendance and handoff. |
| Staff | `/operations/sessions` | Only sessions and bookings within the Staff location scope. |
| Guest/Member | Public package/session discovery | Read-only data exposed by the public discovery contract; no operational actions. |

## Screen and interaction flows

| Screen / flow | Workflow/API reference | Required states and recovery |
| --- | --- | --- |
| Package and lifecycle management | `/workshop-packages`, `/workshop-packages/{packageId}`, lifecycle transitions | Loading; empty list; validation; lifecycle conflict; preserve draft fields after server rejection. |
| Location/slot management | `/workshop-locations`, `/workshop-locations/{locationId}/slots`, slot patch | Manager global access; Staff cannot administer catalog; reject inactive location or overlapping slot; show conflict without silently rewriting times. |
| Session calendar/list | `/workshop-sessions`, bulk create, session GET/PATCH | Filter by date and location; state tabs with counts, order and one colour per state on the session board (FR-056, 2026-11-27: running, upcoming, ended, cancelled and draft; From defaults to today, 7 days back on the Ended tab); show capacity and source revision; stale update returns conflict and reload action. |
| Session state actions (backfilled 2026-11-12, 0a21bcb, c0f550d) | `/workshop-sessions/{sessionId}/state-transitions`, `/attendance-finalizations` | Buttons for transitions the lifecycle does not allow from the current state are greyed out. There is no `ATTENDANCE_FINALIZED` state button; "Finalize attendance" uses the dedicated action and is enabled only while the session is `IN_PROGRESS`. |
| Staff assignment | `/workshop-sessions/{sessionId}/staff-assignments` | Manager assigns/removes; Staff sees only allowed scope; unauthorized actions remain disabled but backend response is shown if attempted. |
| Roster and booking lookup | `/workshop-sessions/{sessionId}/booking-lookups/{bookingCode}` | Informational roster only; do not infer check-in from presence in the list. |
| Staff *(2026-11-06, 2026-11-15)* | session list, roster, check-in | Only sessions in the Staff location scope **and actively assigned**; unassigned sessions are `403`. A session with no Staff shows a "missing Staff" warning to Managers from `checkInOpensAt`; revoking the last assignment after that needs explicit confirmation (spec Q3). Check-in is also open on `SALES_PAUSED` sessions (Q2). |
| Check-in and override | `/check-ins`, `/check-in-overrides` | Require participant/ticket proof; duplicate or out-of-window check-in is a typed error; no offline or automatic check-in. |
| Ticket resend | `/ticket-resends` | Show idempotency/rate-limit result; do not expose recipient PII or provider payload. |
| Attendance finalization | `/attendance-finalizations` | Show a confirmation summary and immutable evidence reference; after all sessions are finalized, attendance is locked and no ordinary edit is offered. |
| Attendance correction/reversal | `/attendance-corrections`, `/check-in-reversals` | Only the explicitly authorized correction path is shown; require reason/evidence; never silently mutate a finalized result. |
| Cancellation handoff | `/cancellation-requests` | Create Operations handoff; show `PENDING/RESOLVED/CANCELED` outcome; no direct payment/refund action from Operations. |

## Invariants captured for regeneration

1. Manager scope is global across Feature 003; Staff scope is location-based.
2. Session identifiers are opaque UUID references; the UI never substitutes numeric database IDs.
3. No automatic no-show, no offline check-in, and no implicit check-in from roster display.
4. Capacity changes are server-validated and must not reduce below already committed/checked-in participants.
5. Attendance is locked after the finalized boundary; a correction requires the dedicated audited flow.
6. Operations cancellation handoff does not call VNPay refund and does not settle money.

## Session, data handling, and accessibility

- Clear stale session/list data after logout or authorization loss.
- Keep server revision/source version with editable rows to prevent stale overwrites.
- Do not put contact PII, ticket secrets, or credentials in query strings or logs.
- Calendar, table, and check-in controls must be keyboard navigable, labelled, and usable at the approved responsive breakpoints.

## Package availability per location (proposed 2026-11-17, spec FR-037–FR-046)

Phạm vi: màn hình mới của Manager và các thay đổi hiển thị ở 003.1/003.3. Trạng thái: trang ma trận của Manager đã có
Figma và được Tech Lead duyệt ngày 2026-11-17 (xem "Figma của trang ma trận" bên dưới); các thay đổi hiển thị ở 003.1/003.3
vẫn `proposed`, chưa có Figma. Liên kết: [architecture.md](./architecture.md), [workflows.md](./workflows.md).

### Figma của trang ma trận (duyệt 2026-11-17)

Figma: https://www.figma.com/design/lNOLBLlL9DEeyU5EvXJnPd (file "Mland 003.2 Package availability matrix", trang
"003.2 Ma trận gói × chi nhánh"). Tech Lead Hồ Hoàng Quốc duyệt trong phiên ngày 2026-11-17, kèm các quyết định tự chọn
của thiết kế liệt kê ở frame 11 và bên dưới.

| Frame | Node | Trạng thái |
| --- | --- | --- |
| 01 Mặc định (đã tải) | `2:2` | Dữ liệu mẫu: Hoàn Kiếm 5 gói, Tây Hồ Brass/Silver 925/Wax Casting, Đà Nẵng trống |
| 02 Đang tải | `2:106` | Khung bảng skeleton, `aria-busy` |
| 03 Rỗng | `2:191` | Không có gói hoặc chi nhánh; nút "Đến Gói workshop", "Tải lại" |
| 04 Đang lưu từng ô | `2:227` | Ô vô hiệu kèm spinner và chữ "Đang lưu" |
| 05 Lưu thành công | `2:339` | Thông báo xanh ngắn, tự ẩn sau vài giây |
| 06 Lỗi 403 | `2:449` | Trang từ chối quyền, sidebar không có mục ma trận |
| 07 Lỗi 409 version | `2:476` | Ô tải lại giá trị server, viền cam, nút "Tải lại ma trận" |
| 08 Lỗi khác | `2:586` | Ô giữ giá trị cũ, viền đỏ, banner lỗi |
| 09, 10 Mobile 375 | `3:2`, `3:86` | Thẻ theo chi nhánh, mỗi thẻ 5 checkbox; mặc định và lỗi khác |
| 11 Ghi chú accessibility và quyết định | `3:175` | Nhãn ô, bàn phím, thông báo trạng thái, tương phản |

Quyết định thiết kế đã duyệt: lưu ngay từng ô (không nút "Lưu" chung, không hỏi xác nhận khi tắt, không hiện số đơn đang
chạy); hàng Đà Nẵng chưa hoạt động vẫn hiện, ô trống và vẫn tick được (Tech Lead chốt riêng
ngày 2026-11-17: không vô hiệu hóa ô của chi nhánh chưa hoạt động); mobile dùng thẻ theo chi nhánh thay vì cuộn ngang; ô đang lưu dùng `aria-disabled`; màu nhấn navy #002967 vì thư
viện Figma không có design system Mland (token lấy từ `workshop-operations.css`).

| Screen / flow | Entry and actor | Workflow ID | Architecture component/hook/API-client link | API/contract reference | Loading / empty / validation / success / error / partial states | Recovery |
| --- | --- | --- | --- | --- | --- | --- |
| Ma trận gói × location (mới) | Manager vào `/operations/package-availability` | WF-01, WF-02 | `PackageAvailabilityMatrixPage`, `PackageAvailabilityMatrix`, `packageAvailabilityApi` | `GET /workshop-package-availability`, `PUT /workshop-packages/{packageId}/locations/{locationId}/availability` | Loading: khung bảng. Empty: không có gói hoặc location, hiện thông báo. Mỗi ô đang lưu bị vô hiệu hóa riêng; thành công: ô đổi ngay và có thông báo ngắn; lỗi 403: trang từ chối quyền; lỗi 409 version: tải lại ô; lỗi khác: giữ trạng thái cũ của ô và hiện lỗi. Hàng location không hoạt động (Đà Nẵng) vẫn hiện, ô để trống. | Tải lại ma trận; ô lỗi trở về giá trị server |
| Chọn location khi đặt chỗ (003.1, đổi) | Khách trên trang gói | WF-03, WF-04 | `WorkshopDetailPage`, `WorkshopBookingPage` (existing, `ppswbs_frontend/src/workshops`) | `GET /workshop-packages/{packageId}/locations`, `.../sessions?locationId=` | Danh sách chỉ gồm location bán gói; rỗng: "Chưa bán ở chi nhánh nào"; 409: quay về chọn location kèm thông báo | Chọn location khác |
| Danh sách buổi đặt chỗ và đổi vé (003.1/003.3, đổi) | Khách, Staff, Manager | WF-04, WF-06 | `TicketSessionPicker`, `GroupTicketList` (existing) | OpenAPI `locationName`; 003.3 `ChangeOption` | Mỗi dòng: "Chi nhánh – ngày giờ – còn N chỗ – còn M lượt đổi"; dòng khác location không cần Manager (sửa 2026-11-17, Hồ Hoàng Quốc) | Chọn buổi khác |
| Xác nhận đổi vé sang location khác (003.3, đổi) | Khách, Staff, Manager | WF-07 | `TicketSessionPicker` + trang đổi vé của 003.3 | facade `replaceTicketSessions` qua 003.3 | Lỗi `PACKAGE_NOT_AVAILABLE_AT_LOCATION` hiện rõ "gói vừa ngừng bán ở chi nhánh đó"; Wax Casting báo phải đổi cả hai buổi cùng chi nhánh | Chọn lại buổi |

Quyền và phiên: chỉ Manager thấy mục menu ma trận; Staff và Admin không thấy và bị 403 nếu vào thẳng URL. Khi mất phiên
hoặc mất quyền, xóa dữ liệu ma trận trong bộ nhớ và về trang đăng nhập (cùng quy tắc "clear stale session data" ở trên).
Khả năng truy cập: ô tick là checkbox có nhãn "<tên gói> tại <tên location>", dùng được bằng bàn phím, trạng thái lưu
được thông báo cho trình đọc màn hình.

**Open decisions (UI):** bố cục và chữ của trang ma trận đã chốt theo Figma ở trên; không hiện số đơn đang chạy khi
Manager tắt một ô (spec không yêu cầu, Tech Lead duyệt theo Figma). Bố cục 003.1/003.3 vẫn chờ Figma riêng.

## Open decisions and dependencies

- Figma của trang ma trận đã có node ID (xem trên). **DRAFT** còn lại: Figma cho phần hiển thị 003.1/003.3 và chữ chính thức của các banner lỗi vận hành khác.
- **Traceability gap**: map each screen to its existing `tasks.md` task and UI verification case before regeneration.

## Trang audit workshop (US7, duyệt 2026-11-17)

Mockup `design/audit-review-mockup.html` (SHA-256 12 ký tự `930ab62b658b`) được Hồ Hoàng Quốc duyệt ngày 2026-11-17 thay cho Figma; chi tiết trong `approval.md`. Route `/operations/audit`, chỉ Manager, hai tab Vận hành và Quyết toán, bộ lọc riêng từng tab, tải thêm bằng con trỏ, các trạng thái tải/rỗng/lỗi/lỗi lọc, dạng thẻ dưới 768 px.
