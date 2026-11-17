# Architecture Design: 003.2 — Gói workshop bán theo location (Phase 18)

**Date created**: 2026-11-17
**Date updated**: 2026-11-17
**Author**: Hồ Hoàng Quốc
**Last updated by**: Hồ Hoàng Quốc

**Trạng thái pack**: `DRAFT/PROPOSED`. Chưa có `approval.md` (revision, phạm vi, ngày, tên Tech Lead), nên chưa
được coi là sẵn sàng triển khai. Mọi node `proposed` bên dưới chưa phải baseline.

Phạm vi file này: chỉ thay đổi "Package availability per location" (spec FR-037–FR-046, SC-009–SC-012, US6). Phần
kiến trúc còn lại của 003.2 nằm ở `plan.md`, `data-model.md` và các contract; không lặp lại ở đây.

## Scope and source map

| Yêu cầu / kịch bản | Nguồn có thẩm quyền | Phản hồi kiến trúc | Task | Kiểm chứng |
| --- | --- | --- | --- | --- |
| FR-037 trạng thái ACTIVE/INACTIVE theo cặp gói × location | `spec.md` FR-037; `data-model.md` "WorkshopPackageLocationAvailability" | Entity + repository + service "sellable" một nơi | Chưa có (bước `$speckit-tasks`) | Unit service; MySQL IT migration seed |
| FR-038 ma trận Manager, audit, chỉ Manager | `spec.md` FR-038; OpenAPI `/workshop-package-availability`, `PUT .../availability` | Controller riêng + service + audit | Chưa có | Contract test 403 Staff/Admin; no-op không audit |
| FR-039 khám phá và reserve | `spec.md` FR-039; OpenAPI `/workshop-packages/{id}/sessions`, `/locations`; facade contract | Sửa controller khám phá + `WorkshopPackageReservationPolicy` | Chưa có | Contract test 409; reserve atomic |
| FR-040 đơn cũ, hold còn hiệu lực không đổi | `spec.md` FR-040 | Không thêm bước hủy/di chuyển nào; toggle không đọc booking | Chưa có | Test: toggle khi còn booking và hold |
| FR-041/042 đổi vé khác location | `spec.md` FR-041, FR-042; facade contract `replaceTicketSessions` | `TicketReplacementRules` + `TicketSessionReplacementService` kiểm cặp ACTIVE và một location | Chưa có | Test Wax Casting hai buổi |
| FR-043 session độc lập theo location | `spec.md` FR-043 | Không đổi mô hình session; kiểm quy tắc chỉ trên session của một vé | Chưa có | Test hai session cùng giờ khác location |
| FR-044 tên location | `spec.md` FR-044; OpenAPI `locationName` | DTO session, khám phá, tùy chọn đổi vé | Chưa có | Contract test; UI test |
| FR-045 hệ quả đổi location | `spec.md` FR-045 | Phần 003.2: quyền Staff theo location mới; phần folio/add-on thuộc 003.3 | Chưa có | Test 003.3 (xem Open decisions) |
| FR-046 Mland thay buổi mọi location | `spec.md` FR-046; facade contract hàng "Find equivalent replacement sessions" | `EquivalentReplacementSessionService` bỏ lọc cùng chi nhánh | Chưa có | Test danh sách ứng viên |

## Implementation tree and ownership

```text
ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/
  workshopoperations/                                        [existing — plan.md "Target Module Structure"]
    controller/
      WorkshopPackageController.java                         [existing — sửa: /sessions trả 409, /locations lọc ACTIVE]
      WorkshopPackageAvailabilityController.java             [proposed — plan.md Phase 18]
      WorkshopSessionController.java                         [existing — thêm locationName vào response]
    dto/
      PackageAvailabilityDtos.java                           [proposed — OpenAPI schemas PackageAvailability*]
    service/
      WorkshopPackageAvailabilityService.java                [proposed — plan.md Phase 18, quyết định 1–2]
      WorkshopPackageReservationPolicy.java                  [existing — sửa: kiểm cặp ACTIVE]
      TicketReplacementRules.java                            [existing — không đổi (kiểm tra đã xác minh: không chứa quy tắc location)]
      TicketSessionReplacementService.java                   [existing — không đổi trực tiếp; gọi WorkshopPackageReservationPolicy.isValidTicketReplacement]
      TicketChangeOptionsService.java                        [existing — sửa: bỏ ghim location cũ trong candidatesFor, lọc theo location đang bán trước khi giới hạn pool]
      EquivalentReplacementSessionService.java               [existing — sửa: FR-046]
      WorkshopPackageService.java                            [existing — mẫu recordAudit để theo]
    entity/
      WorkshopPackageLocationAvailability.java               [proposed — data-model.md]
    dao/
      WorkshopPackageLocationAvailabilityRepository.java     [proposed]
      OperationsAuditEventRepository.java                    [existing]
    facade/
      WorkshopOperationsFacade.java                          [existing — thêm activeLocationsForPackage]
      WorkshopOperationsFacadeImpl.java                      [existing]
      RejectionReason.java                                   [existing — thêm PACKAGE_NOT_AVAILABLE_AT_LOCATION]
  workshopsettlement/                                        [existing — chỉ là node biên, sửa thuộc 003.3]
    service/TicketChangeOptionsApiService.java               [existing — 003.3]
    service/TicketChangeService.java                         [existing — 003.3]
    facade/WorkshopOperationsSettlementAdapter.java          [existing — 003.3]
  workshopbooking/facade/WorkshopOperationsAdapter.java      [existing — node biên 003.1: ánh xạ lý do mới]
ppswbs_backend/src/main/resources/db/migration/
  V<next free after V177>__workshop_package_location_availability.sql   [proposed — chọn số khi triển khai]

ppswbs_frontend/src/
  workshop-operations/
    pages/PackageAvailabilityMatrixPage.jsx                  [proposed]
    components/PackageAvailabilityMatrix.jsx                 [proposed]
    api/packageAvailabilityApi.js                            [proposed — theo mẫu createWorkshopPackageApi trong packageApi.js]
    routes.jsx                                               [existing — thêm /operations/package-availability]
  workshops/                                                 [existing — node biên 003.1: WorkshopDetailPage, WorkshopBookingPage, GroupTicketList]
  shared/ui/TicketSessionPicker.jsx                          [existing — node biên 003.3: hiện tên location]
```

Hướng phụ thuộc: controller → service → repository/facade; 003.1 và 003.3 chỉ gọi `WorkshopOperationsFacade`
(`activeLocationsForPackage`, `replaceTicketSessions`, `findEquivalentReplacementSessions`, đặt chỗ). Không module nào
đọc bảng availability trực tiếp. Không thêm phụ thuộc module mới và không có vòng. `ApplicationModules.verify()` phải
tiếp tục đạt. Mọi thứ mới nằm trong `workshopoperations`; thay đổi trong `workshopsettlement`/`workshopbooking` là việc
của feature đó.

## Production-unit blueprint

| Path | Type / status | Responsibility | Class / export | Draft public / use-case methods | Allowed dependencies | Source |
| --- | --- | --- | --- | --- | --- | --- |
| `workshopoperations/entity/` | Entity — proposed | Một dòng mỗi cặp gói × location; thiếu dòng nghĩa là INACTIVE | `WorkshopPackageLocationAvailability` | (thuộc tính theo data-model; không có method nghiệp vụ) | Không | `data-model.md` |
| `workshopoperations/dao/` | Repository — proposed | Truy cập dòng availability, khóa lạc quan | `WorkshopPackageLocationAvailabilityRepository` | tìm theo cặp; liệt kê theo gói; liệt kê tất cả | Entity | `data-model.md` |
| `workshopoperations/service/` | Service — proposed | Nguồn duy nhất cho "gói bán được ở location nào" và lệnh bật/tắt | `WorkshopPackageAvailabilityService` | `isSellable(packageId, locationId)`; `activeLocationIds(packageId)`; `readMatrix(actor)`; `setAvailability(actor, packageId, locationId, state, version, correlationReference)` | Repository gói và location (existing), audit repository, quyền Manager (existing) | `plan.md` Phase 18 |
| `workshopoperations/controller/` | Controller — proposed | Vận chuyển HTTP cho ma trận, chỉ Manager | `WorkshopPackageAvailabilityController` | `getMatrix`; `putAvailability` | Service, `OperationsRestIdempotencyService` (existing) | OpenAPI |
| `workshopoperations/dto/` | DTO — proposed | Ma trận, ô, yêu cầu ghi | `PackageAvailabilityDtos` | (record; không method) | Không | OpenAPI |
| `WorkshopPackageController` | Controller — existing | Khám phá công khai | (giữ tên) | `/sessions` trả 409 khi cặp INACTIVE; `/locations` chỉ trả location bán được | `WorkshopPackageAvailabilityService` | OpenAPI |
| `WorkshopPackageReservationPolicy` | Service — existing | Quyết định reserve và kiểm bộ session thay thế | (giữ tên) | đường reserve đơn và giỏ: thêm kiểm cặp ACTIVE cho mọi session, sau kiểm `PUBLISHED`; `isValidTicketReplacement`: thêm kiểm cặp ACTIVE (kiểm "một location" khi `sameLocationRequired` đã có sẵn, dòng ~214) | `WorkshopPackageAvailabilityService` | `spec.md` FR-039, FR-041, FR-042 (đã xác minh trong code) |
| `TicketSessionReplacementService` | Service — existing | Thực hiện `replaceTicketSessions` | (giữ tên) | không đổi mã; kế thừa kiểm cặp ACTIVE qua policy; từ chối cả lệnh hoặc không gì cả | Policy | facade contract |
| `TicketChangeOptionsService` | Service — existing | Ứng viên đổi vé | (giữ tên) | `candidatesFor` hiện ghim vào location của buổi cũ khi `sameLocation`; đổi thành lọc theo `activeLocationIds`. Lọc theo availability TRƯỚC giới hạn pool (`PAIR_POOL_LIMIT` 40) để không cắt mất location khác. Ghép cặp hai buổi vẫn do `isValidTicketReplacement` bảo đảm cùng location | Service availability | `spec.md` FR-042, FR-044 |
| `EquivalentReplacementSessionService` | Service — existing | Buổi thay thế khi Mland hủy | (giữ tên) | bỏ lọc cùng chi nhánh; trả `locationId`, `locationName` | Service availability | `spec.md` FR-046 |
| `WorkshopOperationsFacade` / `Impl` | Facade — existing | Biên công khai | (giữ tên) | thêm `activeLocationsForPackage(packageReference)` | Service availability | facade contract |
| `RejectionReason` | Enum — existing | Lý do từ chối dùng chung | (giữ tên) | thêm `PACKAGE_NOT_AVAILABLE_AT_LOCATION` | Không | facade contract |
| `PackageAvailabilityMatrixPage` | Page — proposed | Trang Manager hiển thị và sửa ma trận | `PackageAvailabilityMatrixPage` | props: không (lấy dữ liệu qua API client) | `PackageAvailabilityMatrix`, `packageAvailabilityApi` | `ui-flow.md` |
| `PackageAvailabilityMatrix` | Component — proposed | Bảng hàng = location, cột = gói, ô tick | `PackageAvailabilityMatrix` | props: dữ liệu ma trận, `onToggle`, trạng thái đang lưu theo ô | Không | `ui-flow.md` |
| `packageAvailabilityApi` | API client — proposed | Gọi hai route ma trận | `createPackageAvailabilityApi` | `readMatrix()`; `setAvailability(packageId, locationId, state, version)` | HTTP client dùng chung (existing) | OpenAPI |

## Libraries and integrations

| Layer | Library / version | Status | Purpose in this feature | Why it is needed | Approval/source |
| --- | --- | --- | --- | --- | --- |
| BE | Spring Boot 3.4.x, Spring Data JPA, Spring Modulith, Flyway, MySQL | existing | Entity, repository, migration, kiểm module | Đã là baseline | `plan.md` Technical Context |
| FE | React + Vite, Vitest | existing | Trang ma trận và test | Đã là baseline | `ppswbs_frontend/AGENT.md` |
| Cả hai | Thư viện mới | không có | — | Không cần thư viện mới | — |

## Data flows

Quy ước: `error:` là nhánh lỗi chính. Mỗi luồng liên kết workflow trong `workflows.md`.

**DF-01 (WF-01) Đọc ma trận**
```text
PackageAvailabilityMatrixPage → packageAvailabilityApi.readMatrix → WorkshopPackageAvailabilityController.getMatrix
→ WorkshopPackageAvailabilityService.readMatrix → WorkshopPackageLocationAvailabilityRepository + gói/location repository → MatrixResponse
error: không phải Manager → 403 → trang hiện từ chối quyền
```

**DF-02 (WF-02) Bật/tắt một ô**
```text
PackageAvailabilityMatrix.onToggle → packageAvailabilityApi.setAvailability → WorkshopPackageAvailabilityController.putAvailability
→ WorkshopPackageAvailabilityService.setAvailability → Repository (khóa lạc quan) → OperationsAuditEvent (khi đổi thật) → ô kết quả
error: version cũ → 409 OPTIMISTIC_LOCK_CONFLICT → ma trận tải lại ô; không phải Manager → 403
```

**DF-03/04 (WF-03, WF-04) Khám phá của khách**
```text
WorkshopDetailPage/WorkshopBookingPage → WorkshopPackageController (/locations, /sessions)
→ WorkshopPackageAvailabilityService.activeLocationIds / isSellable → danh sách
error: cặp INACTIVE → 409 PACKAGE_NOT_AVAILABLE_AT_LOCATION → trang báo "gói không bán tại chi nhánh này"
```

**DF-05 (WF-05) Reserve**
```text
003.1 WorkshopOperationsAdapter → WorkshopOperationsFacade.reserveBookingCapacity / reserveGroupCapacity → WorkshopPackageReservationPolicy
→ WorkshopPackageAvailabilityService.isSellable (từng session) → CapacityHoldService → outcome
error: REJECTED(PACKAGE_NOT_AVAILABLE_AT_LOCATION) → 003.1 ánh xạ → giỏ hiện lý do, không giữ chỗ nào
```

**DF-06 (WF-06) Tùy chọn đổi vé**
```text
TicketSessionPicker/GroupTicketList → 003.3 TicketChangeController → TicketChangeOptionsApiService
→ WorkshopOperationsSettlementAdapter → WorkshopOperationsFacade.listTicketChangeOptions → TicketChangeOptionsService
→ WorkshopPackageAvailabilityService.activeLocationIds → options kèm locationName
```

**DF-07 (WF-07) Xác nhận đổi vé khác location**
```text
TicketSessionPicker → TicketChangeController → TicketChangeService (kiểm Manager nếu khác location, 003.3)
→ WorkshopOperationsSettlementAdapter → WorkshopOperationsFacade.replaceTicketSessions → TicketSessionReplacementService
→ TicketReplacementRules → WorkshopPackageAvailabilityService.isSellable → outcome → 003.3 áp hệ quả FR-045
error: REJECTED(PACKAGE_NOT_AVAILABLE_AT_LOCATION | INVALID_REPLACEMENT | …) → chọn lại buổi
```

**DF-08 (WF-08) Mland thay buổi**
```text
003.3 → WorkshopOperationsFacade.findEquivalentReplacementSessions → EquivalentReplacementSessionService
→ WorkshopPackageAvailabilityService.activeLocationIds → candidates (kèm locationName) → 003.3 lưu offer
```

## Open decisions and dependencies

| # | Nội dung | Nguồn / phương án | Tác động | Người quyết | Chặn bàn giao? |
| --- | --- | --- | --- | --- | --- |
| 1 | Khám phá trả `409 PACKAGE_NOT_AVAILABLE_AT_LOCATION` khi cặp INACTIVE | Giữ 409 làm mặc định (2026-11-17). Chỉ gặp khi trang cũ hoặc gõ tay, vì chi nhánh inactive đã bị ẩn khỏi danh sách chọn; thay thế 200 rỗng vẫn được nếu owner đổi ý | Frontend 003.1 xử lý 409 bằng cách quay về chọn chi nhánh | Chủ feature 003 (đã nhận, chưa phản đối) | Không |
| 2 | ĐÃ CHỐT 2026-11-17 (chủ 003.1/003.3): location của vé đọc từ buổi hiện tại của vé trong Operations (cách A), không lưu bản sao | Xác minh trong code: `getTicketFolioContext` (`WorkshopBookingFacadeImpl`) lấy location từ buổi đầu của DÒNG giỏ (`BookingGroupLineSession.location_reference`, `updatable=false`), chép lúc đặt, dùng chung mọi vé của dòng và không cập nhật khi đổi buổi. Quyền Staff, folio và `branch_id` của thu tiền (003.3) đọc giá trị này | 003.1 đổi `getTicketFolioContext` để suy `locationReference` (và `sessionReferences`) từ buổi hiện tại của vé qua Operations; 003.3 không đổi cách đọc | — | Không cho thiết kế; task thuộc 003.1 |
| 3 | ĐÃ CHỐT 2026-11-17 (chủ 003.3): tiền đã thu giữ chi nhánh nơi thu (`settlement_folio_collections.branch_id` đã lưu sẵn); khoản thu, phí, hóa đơn sau khi đổi tính cho chi nhánh mới; add-on cũ giữ nguyên và liệt kê cho Manager | spec FR-045 | 003.3 áp dụng theo hợp đồng cũ; cần task kiểm chứng sau quyết định 2 | — | Không |
| 4 | ĐÃ XÁC MINH 2026-11-17: availability không đụng OTA. Bản chụp OTA (`WorkshopSessionOtaSnapshot`, có `location_reference`) theo từng session; thiết kế này không đổi session, sức chứa hay trạng thái bán | Code `workshopoperations` + `otaconnectivity` | Không | Chủ 003.4 là chủ feature 003 | Không. Bước "đẩy cập nhật lên OTA" là việc riêng của 003.4 |
| 5 | Số phiên bản migration | Chọn sau khi fetch `origin/develop` (hiện mới nhất V177) | Triển khai | Triển khai | Không |
| 7 | ĐÃ CHỐT 2026-11-17 (chủ feature 003): đặt chỗ OTA (`acceptOtaBooking`) KHÔNG bị chặn khi cặp INACTIVE; ma trận không tự cập nhật listing OTA. Cập nhật lên OTA là một bước riêng, tường minh của 003.4, ngoài phạm vi feature này | spec FR-039 | `acceptOtaBooking` không đổi; không thêm kiểm availability vào đường OTA | — | Không. Việc theo dõi: chủ 003.4 quyết có làm bước "đẩy cập nhật lên OTA" hay không |
| 6 | Chưa có `approval.md` | `ppswbs-spec/AGENT.md` §5 | Toàn bộ | Tech Lead | Có |

## Audit review for Manager (US7, added 2026-11-17, status: proposed → approval in `approval.md`)

Spec FR-047–FR-055; plan Phase 19. Không thêm bảng hay migration. BE = `workshopoperations/` trừ khi ghi khác.

| Path | Type / status | Responsibility | Class / export | Draft public / use-case methods | Allowed dependencies | Source |
| --- | --- | --- | --- | --- | --- | --- |
| `controller/` | Controller — proposed | HTTP cho route audit vận hành, chỉ Manager | `AuditReviewController` | `getOperationsEvents` | `AuditReviewService` | OpenAPI `/workshop-audit/operations-events` |
| `dto/` | DTO — proposed | Dòng, trang, actor | `AuditReviewDtos` | (record) | Không | OpenAPI |
| `service/` | Service — proposed | Guard Manager, kiểm bộ lọc, đọc, whitelist, ghép tên actor | `AuditReviewService` | `readOperationsEvents(auth, filter, cursor, limit)` | `WorkshopOperationsAuthorizationService` (existing), `OperationsDeniedAttemptRecorder` (existing), `OperationsAuditEventRepository` (existing), `AuditMetadataWhitelist`, `AuditCursorCodec`, `AccountFacade` (existing) | plan Phase 19 |
| `service/` | Service — proposed | Lọc khóa metadata được phép, parse JSON dung sai | `AuditMetadataWhitelist` | `filter(rawJson)` | Không | FR-051 |
| `service/` | Service — proposed | Mã hóa/giải mã con trỏ id | `AuditCursorCodec` | `encode(id)`; `decode(token)` | Không | FR-050 |
| `dao/OperationsAuditEventRepository` | Repository — existing | Thêm một truy vấn lọc theo con trỏ, không SQL thô | (giữ tên) | `findPage(filter, beforeId, limit+1)` | Entity | data-model |
| `workshopsettlement/controller/` | Controller — proposed (module 003.3, chủ đã đồng ý, B5) | HTTP cho route audit quyết toán, chỉ Manager | `SettlementAuditReviewController` | `getSettlementRecords` | `SettlementAuditReviewService` | OpenAPI `/workshop-audit/settlement-records` |
| `workshopsettlement/service/` | Service — proposed (003.3) | Guard Manager, lọc, tách `account:<id>`, ghép tên actor | `SettlementAuditReviewService` | `readRecords(auth, filter, cursor, limit)` | `WorkshopSettlementAuthorizationService` (existing), `SettlementBusinessAuditRepository` (existing, thêm truy vấn), `AccountFacade` | FR-052 |
| `ppswbs_frontend/src/workshop-operations/api/` | Client — proposed | Gọi hai route | `auditReviewApi.js` | `readOperationsEvents`; `readSettlementRecords` | http client hiện có | OpenAPI |
| `…/pages/` | Page — proposed | Trang hai tab, bộ lọc, bảng, tải thêm, trạng thái | `AuditReviewPage` | (component) | api, `OperationsSidebar` | mockup đã duyệt |

**DF-10 (WF-10) Đọc audit vận hành**
```text
AuditReviewPage → auditReviewApi.readOperationsEvents → AuditReviewController.getOperationsEvents → AuditReviewService.readOperationsEvents
  → WorkshopOperationsAuthorizationService.requireGlobalManager (error: 403 + OperationsDeniedAttemptRecorder DENIED)
  → OperationsAuditEventRepository.findPage → AuditMetadataWhitelist.filter → AccountFacade.findAccountFullName (mỗi actor khác nhau một lần) → AuditReviewDtos
```
**DF-11 (WF-11) Đọc audit quyết toán**
```text
AuditReviewPage → auditReviewApi.readSettlementRecords → SettlementAuditReviewController.getSettlementRecords → SettlementAuditReviewService.readRecords
  → WorkshopSettlementAuthorizationService (error: 403, ghi `DENIED_<reason>` theo cách 003.3 đang làm) → SettlementBusinessAuditRepository (truy vấn con trỏ) → AccountFacade.findAccountFullName → dto settlement
```
Hướng phụ thuộc: **không thêm phụ thuộc giữa hai module**. `workshopsettlement` đã phụ thuộc `workshopoperations :: facade` nên route quyết toán nằm trong 003.3; Operations không gọi 003.3 (gọi sẽ tạo vòng và fail `ApplicationModules.verify()`, phát hiện F1 của analyze 2026-11-17). `actor_reference` của settlement có dạng `account:<id>` hoặc `SYSTEM` (xác minh trong code 2026-11-17: `AbsorbedUnderchargeService`, `BankRefundService`, `CashDayCloseService`, `CancellationHandoffCompletionService`): `account:<id>` được tách ra thành id để lấy tên; giá trị khác hiện nguyên văn.
