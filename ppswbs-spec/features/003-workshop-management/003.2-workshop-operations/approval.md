# Implementation Design Approval: 003.2 — Gói workshop bán theo location (Phase 18)

**Date created**: 2026-11-17
**Date updated**: 2026-11-17
**Author**: Hồ Hoàng Quốc
**Last updated by**: Hồ Hoàng Quốc

## Approval status

**Status**: PARTIALLY APPROVED

Chỉ phần backend và contract của 003.2 cho "Package availability per location" được duyệt để triển khai. Các phần
loại trừ bên dưới vẫn bị chặn.

## Approved revision and scope

- Design revision: working tree trên commit `7ac6cbb5` (nhánh `quoc`), các thay đổi chưa commit. Nội dung được duyệt
  nhận diện bằng mã băm SHA-256 (12 ký tự đầu) của từng file ngày 2026-11-17:
  - `003.2-workshop-operations/spec.md` — `27d3460a47d6` (FR-037–FR-046, SC-009–SC-012, US6)
  - `003.2-workshop-operations/plan.md` — `8b00182e3594` (mục "Package availability per location (Phase 18)"; đồng bộ FR-046 và trạng thái duyệt, cùng ngày)
  - `003.2-workshop-operations/data-model.md` — `c9519efcf919` (thêm tên bảng `workshop_package_location_availability`, cùng ngày)
  - `003.2-workshop-operations/contracts/workshop-operations.openapi.yaml` — `86b1db5f8226`
  - `contracts/workshop-interfeature-facades.md` — `c062dbb1e6fe`
  - `003.2-workshop-operations/design/architecture.md` — `81f00ed9643e`
  - `003.2-workshop-operations/design/workflows.md` — `b482c1c2cb0c`
  - `003.2-workshop-operations/design/ui-flow.md` — `3f6e7689fd4d` (chỉ phần dữ liệu và quyền; bố cục chưa duyệt)
  Sửa các file này sau ngày duyệt thì phần bị ảnh hưởng phải được đánh giá lại; mã băm đổi là dấu hiệu.
- Approved implementation scope: backend `workshopoperations` — bảng và migration, `WorkshopPackageAvailabilityService`,
  hai route ma trận, kiểm cặp ACTIVE trong khám phá, reserve và kiểm bộ session thay thế, `locationName`, lý do từ chối
  `PACKAGE_NOT_AVAILABLE_AT_LOCATION`, `activeLocationsForPackage`, và `findEquivalentReplacementSessions` theo FR-046. Workflow
  WF-01–WF-09. Test theo mục "Verification plan" của `plan.md`.
- Approval date: 2026-11-17
- Tech Lead: Hồ Hoàng Quốc

## Decisions approved

| Decision | Approved value | Evidence/source | Affected artifact revision |
| --- | --- | --- | --- |
| Ma trận | Hàng = location, cột = gói, tick = ACTIVE; chỉ Manager xem và sửa; Staff, Admin 403 | Chỉ thị trong phiên 2026-11-17; spec FR-038 | spec, OpenAPI |
| Trạng thái ban đầu | Hoàn Kiếm 5 gói; Tây Hồ Brass, Silver 925, Wax Casting; Đà Nẵng trống; ô mới mặc định INACTIVE | Chỉ thị trong phiên; spec FR-037; `V161__add_data.sql` | spec, data-model |
| Tắt ô | Không bị chặn bởi đơn đang có; đơn, vé, hold còn hiệu lực giữ nguyên | Chỉ thị trong phiên; spec FR-040 | spec |
| Khám phá ở ô INACTIVE | `409 PACKAGE_NOT_AVAILABLE_AT_LOCATION` (mặc định, hiếm gặp) | Chỉ thị trong phiên; OpenAPI | OpenAPI |
| OTA | Không chặn đơn OTA; ma trận không tự cập nhật listing OTA | Chỉ thị trong phiên; spec FR-039 | spec |
| Đổi vé khác location | Cho phép khi gói ACTIVE ở đó; Wax Casting đổi cả hai buổi cùng location; Staff trong location của mình và được gán vào buổi của vé; khác location KHÔNG cần Manager, sau khi đổi Staff location cũ mất quyền, có audit (sửa 2026-11-17, Hồ Hoàng Quốc; trước đó ghi "khác location cần Manager") | Chỉ thị trong phiên 2026-11-17; spec FR-042 | spec, facade contract |
| Mland thay buổi | Gợi ý mọi location còn bán gói; vé hai buổi ở location của buổi còn lại | Chỉ thị trong phiên; spec FR-046 | spec, facade contract |
| Tiền và location của vé | Tiền đã thu giữ chi nhánh nơi thu; khoản mới theo chi nhánh mới; location của vé đọc từ buổi hiện tại ở Operations | Chỉ thị trong phiên; spec FR-045 | spec, plan |
| Audit | `WORKSHOP_PACKAGE_AVAILABILITY_CHANGED`, không ghi khi không đổi | Chỉ thị trong phiên; plan | plan, data-model |

## UI approval — trang ma trận gói × chi nhánh (added 2026-11-17)

- **Phạm vi:** chỉ giao diện và chữ hiển thị của `PackageAvailabilityMatrixPage` / `PackageAvailabilityMatrix` (route `/operations/package-availability`). Không gồm 003.1/003.3.
- **Nguồn thiết kế:** Figma https://www.figma.com/design/lNOLBLlL9DEeyU5EvXJnPd, frame 01–11 (node ID trong `design/ui-flow.md`, mục "Figma của trang ma trận").
- **Duyệt bởi:** Tech Lead Hồ Hoàng Quốc, chỉ thị trong phiên ngày 2026-11-17 ("tôi duyệt") sau khi nhận danh sách quyết định thiết kế tự chọn.
- **Quyết định riêng:** hàng Đà Nẵng (chi nhánh chưa hoạt động) vẫn hiện và ô vẫn tick được, không vô hiệu hóa. Tech Lead chốt trong phiên 2026-11-17, khớp Figma hiện tại; backend không được chặn ghi ô của chi nhánh chưa hoạt động.
- **Mã băm sau khi ghi nhận (SHA-256, 12 ký tự đầu, 2026-11-17):** `design/ui-flow.md` — `a04f74328e8b` (thay `3f6e7689fd4d`, mục UI đã đổi); `tasks.md` — `e0bd51a950c1` (dòng B1; sau khi sinh task FE T163–T168 ngày 2026-11-17: xem dòng "Task frontend" bên dưới). Mã băm khác của bảng "Approved revision" không đổi.
- **Task frontend (added 2026-11-17, `$speckit-tasks`):** T163–T168 trong `tasks.md` mục "Frontend: Manager matrix page"; mục B1 đã gỡ. Mã băm `tasks.md` sau khi thêm: `9728bd2f6b64` (thay `e0bd51a950c1`; chỉ thêm task FE, bảng B1 và dòng phụ thuộc, không đổi T144–T162). Giả định cần Tech Lead xác nhận: breakpoint thẻ mobile 768 px (Figma chỉ có khung 375 px) và ô chỉ đổi sau khi server xác nhận (kiểm khung 05). Phần 003.1/003.3 vẫn bị chặn (B2, B3).

## Exclusions and remaining blockers

- **Chưa duyệt, vẫn bị chặn:**
  - ~~Giao diện trang ma trận và chữ hiển thị (chưa có Figma)~~ — đã duyệt 2026-11-17, xem mục "UI approval" ở trên.
  - Thay đổi của 003.1: đổi `getTicketFolioContext` để suy location từ buổi hiện tại của vé; xử lý lý do từ chối mới ở giỏ; hiển thị location.
  - Thay đổi của 003.3: bỏ ghim location trong tùy chọn đổi vé đã tính ở 003.3, không cần Manager khi khác location (sửa 2026-11-17), áp hệ quả FR-045. *(003.3 được duyệt ngày 2026-11-17 bởi Hồ Hoàng Quốc, xem header spec 003.3 "Approved addition".)*
  - Bước "đẩy cập nhật lên OTA" của 003.4 (ngoài phạm vi).
  Mỗi phần cần task, thiết kế và duyệt trong feature của nó.
- **Chưa kiểm:** số phiên bản migration (chọn sau khi fetch `origin/develop`; hiện mới nhất V177) và tên gói trong DB thật (đã khớp `V161__add_data.sql`, chưa truy vấn DB).
- **Task:** Phase 18 (T144–T162) đã có trong `tasks.md`. T154 (bỏ ghim chi nhánh trong `TicketChangeOptionsService`, WF-06 bước 3) thuộc phạm vi 003.2 được duyệt; phần "bỏ ghim" bên 003.3 là phần loại trừ ở trên.
- Bản duyệt này chỉ đúng với các mã băm nêu trên.

## Audit review for Manager (US7, added 2026-11-17) — chưa duyệt

- **Trạng thái:** DRAFT/PROPOSED. Chủ feature Hồ Hoàng Quốc đã trả lời các câu hỏi clarify ngày 2026-11-17 (nguồn Operations + Settlement dạng hai tab, metadata qua whitelist, actor = id + tên, các mặc định, lý do settlement hiện đầy đủ, chỉ Manager toàn cục), ghi ở `spec.md` mục Clarifications "Session 2026-11-17 audit review". Đây là quyết định của chủ feature về nghiệp vụ, **không phải** phê duyệt thiết kế của Tech Lead.
- **Chưa có:** bản duyệt của Tech Lead (revision, phạm vi, ngày, tên), `design/architecture.md` và `workflows.md` cho US7 (T169), Figma/UI được duyệt cho trang audit (B6), đồng ý của chủ 003.3 cho facade đọc (B5).
- **Rủi ro đã ghi:** `reason_reference` của settlement là text tự do tối đa 500 ký tự, có thể chứa thông tin cá nhân; chủ feature chọn hiện đầy đủ cho Manager toàn cục, không ghi log.
- Phase 19 (T169–T174) chưa được phép triển khai cho đến khi các mục trên có bằng chứng.

## UI approval — trang audit workshop (added 2026-11-17)

- **Duyệt bởi:** Hồ Hoàng Quốc (chủ Feature 003, Tech Lead theo mục "UI approval" trang ma trận), chỉ thị trực tiếp trong phiên ngày 2026-11-17 sau khi xem mockup trong Browser pane.
- **Nội dung được duyệt:** `design/audit-review-mockup.html` (SHA-256 12 ký tự: `930ab62b658b`), 7 khung: tab Vận hành, tab Quyết toán, đang tải, không có kết quả, lỗi tải, lỗi lọc, điện thoại 375 px, cùng ghi chú thiết kế ở cuối file. Đây là **mockup HTML**, không phải Figma; chủ feature chấp nhận thay Figma cho trang này.
- **Phạm vi:** chỉ giao diện và chữ hiển thị của trang `/operations/audit`. **Không** gồm: duyệt thiết kế backend (T169), facade 003.3 (B5), hành vi Staff/Admin gõ URL (theo FE-DESIGN-0006).
- **Hệ quả:** B6 gỡ; T173–T174 hết bị chặn vì UI, nhưng T174 vẫn phụ thuộc T170–T171 (API).

## Design approval — audit workshop (US7, backend và contract; added 2026-11-17)

- **Duyệt bởi:** Hồ Hoàng Quốc (chủ Feature 003, Tech Lead theo mục "UI approval" trang ma trận), chỉ thị trực tiếp trong phiên 2026-11-17: "tôi đồng ý duyệt" cho T169 (`$feature-design` và duyệt thiết kế) và B5 (đồng ý facade đọc của 003.3, do cùng người sở hữu 003.3).
- **Revision được duyệt (SHA-256 12 ký tự):** `design/architecture.md` `0b8d241837af` (mục "Audit review for Manager"), `design/workflows.md` `7ca219877d71` (WF-10, WF-11), `contracts/workshop-operations.openapi.yaml` `181ec60e0656` (`/workshop-audit/*`), `spec.md` `a09020e54812` (US7, FR-047–FR-055, SC-013), `plan.md` `f06518248099` (Phase 19).
- **Phạm vi:** route đọc audit vận hành và quyết toán, `AuditReviewService`, whitelist metadata, con trỏ phân trang, facade đọc `SettlementAuditReadFacade` trong 003.3, hiển thị lý do settlement đầy đủ cho Manager. Task T170–T174.
- **Ghi nhận:** (1) `actor_reference` của settlement là `account:<id>` hoặc `SYSTEM` (xác minh trong code), contract đã thêm `actorReference`; (2) cửa sổ mặc định 7 ngày khi không có `from` là đề xuất kèm theo bản duyệt này; (3) nếu sau này đổi nội dung các file trên đáng kể thì cần duyệt lại phần bị ảnh hưởng.
- **Chưa kiểm:** `$speckit-analyze` chưa chạy; chưa có code hay test.

## Design approval — audit workshop: sửa sau $speckit-analyze (added 2026-11-17)

- **Lý do:** $speckit-analyze phát hiện F1 (CRITICAL): route quyết toán đặt trong Operations buộc `workshopoperations` gọi `workshopsettlement`, trong khi `workshopsettlement` đã phụ thuộc `workshopoperations :: facade` (11 file; `package-info.java`), tạo vòng module và fail `ApplicationModules.verify()`. Cũng sửa F2–F8.
- **Thay đổi (theo chỉ thị "Sửa theo đề xuất" của Hồ Hoàng Quốc, 2026-11-17):** route `/workshop-audit/settlement-records`, service và truy vấn nằm trong module `workshopsettlement` (T173), không có facade `SettlementAuditReadFacade`; Operations chỉ giữ `operations-events`; frontend gọi hai route độc lập. 400 dùng `VALIDATION_ERROR` có sẵn (không thêm mã); từ chối Quyết toán ghi `DENIED_<reason>` theo 003.3; cửa sổ mặc định 7 ngày vào FR-049; giá trị metadata trả dạng chuỗi; `assignedAccountId` vào whitelist; T174 (trước là T180) thêm kiểm tay FR-055 và SC-013.
- **Revision mới (SHA-256 12 ký tự, thay các mã cũ ở mục "Design approval — audit workshop"):** `design/architecture.md` `f86a4739fad9`, `design/workflows.md` `ac7f75f1fd9c`, `contracts/workshop-operations.openapi.yaml` `35d094a90684`, `spec.md` `7f59f3401125`, `plan.md` `5d27ecc6ee3c`.
- **Phạm vi và UI:** như mục trước; mockup UI (`930ab62b658b`) không đổi vì giao diện vẫn hai tab gọi hai route.
- **Chưa kiểm:** chạy lại `$speckit-analyze` sau sửa; chưa có code hay test.

- **Cập nhật 2026-11-17 (gộp task):** theo chỉ thị của Hồ Hoàng Quốc, Phase 19 gộp từ T169–T180 còn T169–T174 (T170 backend Operations, T171 route Quyết toán trong 003.3, T172 kiểm backend, T173 frontend, T174 kiểm frontend). Chỉ đổi `tasks.md` và số task được nhắc trong `plan.md`; mã băm `plan.md` mới: `d239901ebd9d` (thay `5d27ecc6ee3c`). Không đổi nội dung thiết kế, contract hay mockup.
