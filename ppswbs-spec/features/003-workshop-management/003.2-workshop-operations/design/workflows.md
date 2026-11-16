# Workflow Design: 003.2 — Gói workshop bán theo location (Phase 18)

**Date created**: 2026-11-17
**Date updated**: 2026-11-17
**Author**: Hồ Hoàng Quốc
**Last updated by**: Hồ Hoàng Quốc

**Trạng thái**: `DRAFT/PROPOSED`, chưa có `approval.md`. Phạm vi: chỉ các luồng mới hoặc bị đổi bởi spec FR-037–FR-046.
Tên class/method là đề xuất theo [architecture.md](./architecture.md). Hợp đồng HTTP nằm ở
[OpenAPI](../contracts/workshop-operations.openapi.yaml), dữ liệu ở [data-model.md](../data-model.md).

## Workflow index

| ID | Workflow | Trigger and actor | Source requirements | Architecture data-flow link | Status |
| --- | --- | --- | --- | --- | --- |
| WF-01 | Manager đọc ma trận | Manager mở trang ma trận | FR-038 | DF-01 | proposed |
| WF-02 | Manager bật/tắt một ô | Manager tick/bỏ tick | FR-038, FR-040 | DF-02 | proposed |
| WF-03 | Khách xem location bán gói | Khách mở trang gói | FR-039 | DF-03/04 | proposed |
| WF-04 | Khách xem session của gói tại location | Khách chọn location | FR-039, FR-044 | DF-03/04 | proposed |
| WF-05 | Đặt chỗ (reserve) kiểm lại | 003.1 gọi reserve | FR-039, FR-040, FR-043 | DF-05 | proposed |
| WF-06 | Liệt kê buổi đổi vé | Khách/Staff/Manager mở đổi vé | FR-042, FR-043, FR-044 | DF-06 | proposed |
| WF-07 | Xác nhận đổi vé sang location khác | Khách/Staff/Manager xác nhận | FR-041, FR-042, FR-045 | DF-07 | proposed |
| WF-08 | Buổi thay thế khi Mland hủy | 003.3 hỏi ứng viên | FR-046 | DF-08 | proposed |
| WF-09 | Migration seed ma trận | Triển khai migration một lần | FR-037 | — | proposed |

Trigger độc lập khác nhau được tách thành workflow riêng, kể cả đọc và ghi.

## WF-01: Manager đọc ma trận

- Trigger và actor: Manager mở `/operations/package-availability`.
- Điều kiện và quyền: Manager đang hoạt động (toàn cục). Staff, Admin và vai khác bị từ chối 403.
- Luồng dữ liệu: DF-01.

1. `WorkshopPackageAvailabilityController.getMatrix` nhận yêu cầu, xác thực token bearer.
2. Controller xác định quyền Manager qua `WorkshopForbiddenException` hiện có nếu không đạt.
3. `WorkshopPackageAvailabilityService.readMatrix` đọc mọi gói, mọi location và mọi dòng availability.
4. Ô không có dòng được trả là INACTIVE, `version` 0.
5. Trang hiển thị bảng: hàng location, cột gói, ô tick = ACTIVE.

### Failure, recovery, and integrity

1. Không phải Manager: 403, không lộ dữ liệu. Token thiếu hoặc sai: 401. Account facade không trả được vai: 503, đóng an toàn.
2. Đọc thuần, không retry hay concurrency riêng.
3. Hậu điều kiện: không đổi dữ liệu. Kiểm chứng: contract test 403 cho Staff và Admin.

## WF-02: Manager bật/tắt một ô

- Trigger và actor: Manager tick hoặc bỏ tick một ô.
- Điều kiện và quyền: như WF-01; gói và location tồn tại; header `Idempotency-Key` bắt buộc.
- Luồng dữ liệu: DF-02.

1. `PackageAvailabilityMatrix.onToggle` gọi `packageAvailabilityApi.setAvailability` kèm `version` của ô.
2. `WorkshopPackageAvailabilityController.putAvailability` kiểm quyền Manager và áp idempotency theo `OperationsRestIdempotencyService`.
3. `WorkshopPackageAvailabilityService.setAvailability` đọc dòng của cặp (nếu có).
4. Nếu trạng thái mới bằng trạng thái hiện tại: trả 200 với ô hiện tại, không ghi dòng, không ghi audit.
5. Nếu khác: một giao dịch ghi hoặc cập nhật dòng (tăng `version`) và ghi một `OperationsAuditEvent` `WORKSHOP_PACKAGE_AVAILABILITY_CHANGED` (actor, gói, location, từ, thành, thời điểm; `session_id` rỗng).
6. Trả ô kết quả; trang cập nhật ô đó.

### Failure, recovery, and integrity

1. Gói hoặc location không tồn tại: 404. Phụ thuộc quyền không dùng được: 503.
2. `version` cũ: 409 `OPTIMISTIC_LOCK_CONFLICT`; trang tải lại ô và hiện trạng thái mới. Cùng `Idempotency-Key` và cùng nội dung: trả kết quả cũ; khác nội dung: 409 `IDEMPOTENCY_CONFLICT`.
3. Lệnh KHÔNG bị chặn bởi booking, vé, hold đang có (spec FR-040) và KHÔNG hủy hoặc di chuyển gì. Audit và dòng nằm trong một giao dịch: hoặc cả hai hoặc không gì. Kiểm chứng: test tắt khi còn booking và hold; test không audit khi cùng trạng thái.

## WF-03: Khách xem location bán gói

- Trigger và actor: Khách (Guest/Member) mở trang gói; không cần đăng nhập.
- Điều kiện: gói `PUBLISHED`.
- Luồng dữ liệu: DF-03/04.

1. `WorkshopPackageController` route `/workshop-packages/{packageId}/locations` nhận yêu cầu.
2. Controller gọi `WorkshopPackageAvailabilityService.activeLocationIds(packageId)` (cặp ACTIVE và location đang hoạt động).
3. Trả danh sách location kèm tên.
4. Trang chỉ cho khách chọn trong danh sách đó.

### Failure, recovery, and integrity

1. Gói không `PUBLISHED` hoặc không tồn tại: 404 như hiện tại. Danh sách rỗng là kết quả hợp lệ: trang báo "chưa bán ở chi nhánh nào".
2. Đọc thuần; không retry riêng. Kiểm chứng: danh sách không chứa Tây Hồ cho Silver Clay 5g.

## WF-04: Khách xem session của gói tại một location

- Trigger và actor: Khách chọn location trên trang gói.
- Luồng dữ liệu: DF-03/04.

1. `WorkshopPackageController` route `/workshop-packages/{packageId}/sessions?locationId=` nhận yêu cầu.
2. Controller hỏi `WorkshopPackageAvailabilityService.isSellable(packageId, locationId)`.
3. Nếu bán được: trả session tương lai, `PUBLISHED`, chọn được, kèm `locationName`.
4. Nếu cặp INACTIVE: 409 `PACKAGE_NOT_AVAILABLE_AT_LOCATION` (đề xuất, xem Open decision 1 ở architecture).

### Failure, recovery, and integrity

1. 400 thiếu `locationId`; 404 gói không bán công khai. Khi gặp 409 trang quay về danh sách location (WF-03).
2. Dữ liệu là bản chụp để chọn; `remainingCapacity` không bảo đảm đặt được. Quyết định cuối ở WF-05.

## WF-05: Đặt chỗ (reserve) kiểm lại

- Trigger và actor: 003.1 gọi lệnh reserve qua `WorkshopOperationsFacade` (đơn lẻ hoặc cả giỏ).
- Điều kiện: lệnh mang `packageReference` và các `sessionReference` đã chọn.
- Luồng dữ liệu: DF-05.

1. `WorkshopPackageReservationPolicy` kiểm gói còn `PUBLISHED`; nếu không trả `PACKAGE_NOT_PUBLISHED` (luôn thắng).
2. Với mọi session được chọn, policy hỏi `WorkshopPackageAvailabilityService.isSellable` theo location của session.
3. Có một session không bán được: cả lệnh bị từ chối với `PACKAGE_NOT_AVAILABLE_AT_LOCATION`, không giữ chỗ nào.
4. Qua kiểm: tiếp tục kiểm số người, số buổi, một location, rồi `CapacityHoldService` giữ chỗ nguyên khối như hiện tại.

### Failure, recovery, and integrity

1. 003.1 ánh xạ lý do mới thành thông báo giỏ; khách chọn lại. Lý do khác (`INSUFFICIENT_CAPACITY`, `PACKAGE_SELECTION_INVALID`) giữ nguyên.
2. Race giữa bật/tắt và reserve: không khóa thêm. Reserve nào đọc ACTIVE rồi commit là một lần bán trước khi tắt (hợp lệ theo FR-040); reserve bắt đầu sau khi tắt bị từ chối. Không có kết quả nửa vời vì giữ chỗ nguyên khối trong một giao dịch.
3. Hold đã tạo trước khi tắt vẫn commit được đến lúc hết hạn (FR-040): bước commit hold không kiểm lại availability. Kiểm chứng: test race và test hold sống.

## WF-06: Liệt kê buổi đổi vé

- Trigger và actor: khách, Staff hoặc Manager mở đổi vé (003.3).
- Điều kiện: vé hợp lệ; quyền Staff theo location trong phạm vi và có gán buổi (quy tắc hiện có).
- Luồng dữ liệu: DF-06.

1. `TicketChangeController` nhận yêu cầu; `TicketChangeOptionsApiService` kiểm quyền.
2. `WorkshopOperationsSettlementAdapter` gọi `WorkshopOperationsFacade` (`ListTicketChangeOptionsQuery`).
3. `TicketChangeOptionsService.candidatesFor` không còn ghim vào location của buổi cũ; nó lấy ứng viên ở mọi location trả về từ `WorkshopPackageAvailabilityService.activeLocationIds`, lọc TRƯỚC giới hạn pool, bỏ session hết chỗ hoặc hết lượt đổi (đã có), tính quota theo từng session đích. Với vé hai buổi, cặp chỉ được liệt kê khi `isValidTicketReplacement` (cùng location, đủ ngày xưởng đúc) đạt.
4. Mỗi ứng viên có `locationName`; ứng viên khác location đánh dấu cần Manager nếu người gọi là Staff.
5. Trang hiển thị "Chi nhánh – ngày giờ – còn N chỗ – còn M lượt đổi".

### Failure, recovery, and integrity

1. Không có ứng viên: danh sách rỗng và thông điệp như hiện tại. Quyền không đủ: 403 `SESSION_ASSIGNMENT_REQUIRED` hoặc `LOCATION_SCOPE_DENIED`.
2. Đọc thuần. Hai session cùng giờ khác location không coi là trùng (FR-043).

## WF-07: Xác nhận đổi vé sang location khác

- Trigger và actor: khách (trong quy tắc 003.3), Staff hoặc Manager xác nhận buổi mới.
- Điều kiện: buổi mới thuộc ứng viên WF-06; nếu khác location thì người gọi là Manager (003.3 kiểm trước), trừ Mland tự đổi.
- Luồng dữ liệu: DF-07.

1. `TicketChangeService` (003.3) kiểm quyền và yêu cầu Manager khi location đích khác location hiện tại của vé.
2. Qua `WorkshopOperationsSettlementAdapter`, gọi `WorkshopOperationsFacade.replaceTicketSessions`.
3. `WorkshopPackageReservationPolicy.isValidTicketReplacement` kiểm: mọi session đích của vé ở một location nếu `sameLocationRequired` (đã có); cặp gói × location đích ACTIVE (mới). `TicketReplacementRules` kiểm đích còn mở bán, còn chỗ và quota (đã có).
4. `TicketSessionReplacementService` thực hiện thay toàn bộ hoặc không gì, giữ QR và lịch sử điểm danh (FR-034).
5. Trả `ACCEPTED`; 003.3 áp hệ quả FR-045: location của vé chuyển sang location mới, folio tính cho location mới, add-on đã bán ở location cũ giữ nguyên và liệt kê cho Manager. Staff location mới có quyền theo gán buổi mới; Staff location cũ không còn quyền với vé.

### Failure, recovery, and integrity

1. `REJECTED(PACKAGE_NOT_AVAILABLE_AT_LOCATION)` khi cặp vừa bị tắt (kể cả yêu cầu Manager đã duyệt trước đó); `INVALID_REPLACEMENT` khi Wax Casting chỉ đổi một buổi sang location khác; `INSUFFICIENT_CAPACITY`, `RESCHEDULE_QUOTA_EXHAUSTED` như cũ. Không thay đổi nào được áp.
2. Idempotent theo `operationReference` (quy tắc facade chung). Nếu bước 5 phía 003.3 thất bại sau bước 4, cách phục hồi và giao dịch bao quanh là thiết kế của 003.3 và đang là Open decision 2 và 3 ở architecture.
3. Kiểm chứng: test Wax Casting hai buổi; test vé một buổi sang Tây Hồ bởi Manager; Staff bị từ chối khi khác location.

## WF-08: Buổi thay thế khi Mland hủy

- Trigger và actor: 003.3 hỏi ứng viên sau khi một session bị Mland hủy.
- Luồng dữ liệu: DF-08.

1. `WorkshopOperationsFacade.findEquivalentReplacementSessions` gọi `EquivalentReplacementSessionService`.
2. Service lấy mọi session tương lai, `PUBLISHED`, còn chỗ và quota của cùng gói ở mọi location ACTIVE (FR-046), giữ thứ tự gói và khoảng cách. Buổi còn lại của vé hai buổi giữ nguyên nên ứng viên của vé Wax Casting vẫn ở location của buổi đó (FR-046); chỉ vé một buổi mới có ứng viên khác location.
3. Trả danh sách theo giờ bắt đầu, mỗi ứng viên có `locationId` và `locationName`.
4. 003.3 lưu offer, khách chọn một buổi; 003.3 lọc lại theo chỗ khi khách trả lời, rồi gọi WF-07 (type MLAND) mà không cần Manager.

### Failure, recovery, and integrity

1. Danh sách rỗng: hoàn 100% như hiện tại. Cặp bị tắt giữa lúc offer và lúc khách chọn: bước lọc lại bỏ ứng viên đó; nếu hết, rơi về hoàn tiền.
2. Đọc thuần, không giữ chỗ.

## WF-09: Migration seed ma trận

- Trigger và actor: triển khai Flyway một lần (không có actor người dùng).
- Điều kiện: bảng gói, location đã tồn tại; migration nối tiếp, không sửa migration cũ.

1. Tạo bảng availability với khóa duy nhất (gói, location).
2. Chèn dòng ACTIVE theo tên gói và mã location: Hoàn Kiếm (`HN-HOANKIEM`) × năm gói; Tây Hồ (`HN-TAYHO`) × `Signature Workshop - Brass`, `Signature Workshop - Silver 925`, `Wax Casting Workshop`.
3. Không chèn dòng cho Silver Clay 5g/7g ở Tây Hồ và không cho Đà Nẵng (`DN-HAICHAU`).

### Failure, recovery, and integrity

1. Tên gói hoặc mã location không khớp thì dòng đó không được chèn và phải bị phát hiện bằng kiểm tra sau migration (đếm dòng: 8). Không âm thầm bỏ qua.
2. Phục hồi: bỏ bảng; chỉ mất ma trận. Kiểm chứng: MySQL IT đếm 5 + 3 dòng ACTIVE, 0 dòng cho Đà Nẵng.

## WF-10: Manager đọc audit vận hành (US7, added 2026-11-17)

- Trigger và actor: Manager mở tab Vận hành, đổi bộ lọc hoặc bấm Tải thêm.
- Luồng dữ liệu: DF-10.

1. Controller nhận `Authorization`, bộ lọc, `cursor`, `limit`; service gọi `requireGlobalManager`.
2. Kiểm bộ lọc: `from` sau `to`, `limit` ngoài 1–100, con trỏ hỏng → 400. Không có `from` thì dùng 7 ngày gần nhất.
3. Repository lấy `limit + 1` dòng có `id` nhỏ hơn con trỏ, `id` giảm dần; dòng thứ `limit + 1` chỉ để biết còn trang sau (`nextCursor`).
4. Whitelist lọc metadata (JSON hỏng → rỗng); tên actor lấy một lần cho mỗi actor khác nhau; trả trang. Không ghi audit khi đọc thành công.

### Failure, recovery, and integrity

1. Không phải Manager đang hoạt động: 403, Operations ghi một dòng `DENIED` (giao dịch riêng); không trả dữ liệu.
2. Tra tên actor lỗi hoặc không có: trả id, không làm hỏng trang.
3. Lỗi DB: 503 an toàn có mã tương quan; giao diện giữ bộ lọc, có Thử lại.
4. Đồng thời: dòng mới ghi trong lúc duyệt có `id` lớn hơn con trỏ nên không làm trùng hoặc sót dòng trang đã tải.

## WF-11: Manager đọc audit quyết toán (US7, added 2026-11-17)

- Trigger và actor: Manager mở tab Quyết toán, lọc hoặc Tải thêm.
- Luồng dữ liệu: DF-11. Thực hiện trong module 003.3.

1. `SettlementAuditReviewController` nhận bộ lọc riêng (mã hành động, actor, loại và mã đối tượng, thời gian); `SettlementAuditReviewService` kiểm Manager như WF-10 bước 1–2 qua `WorkshopSettlementAuthorizationService`.
2. Repository settlement lấy `limit + 1` dòng theo `id` giảm dần; bộ lọc actor khớp `actor_reference = account:<id>`.
3. Service tách `account:<id>` để lấy tên; `SYSTEM` và giá trị khác hiện nguyên văn; trả trang gồm lý do đầy đủ.

### Failure, recovery, and integrity

1. Không phải Manager: 403 và ghi `DENIED_<reason>` theo cách 003.3 đang làm (không phải dòng `DENIED` của Operations); bộ lọc hỏng: 400 `VALIDATION_ERROR`.
2. Lỗi DB: 503 an toàn; không trả một nửa trang; tab Vận hành không bị ảnh hưởng.
3. Lý do (tối đa 500 ký tự) chỉ có trong phản hồi cho Manager; không đưa vào log, thông báo lỗi hay `toString`.
4. Hành động mới của 003.3 xuất hiện tự động vì không có danh sách action cố định.
