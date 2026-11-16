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

## Exclusions and remaining blockers

- **Chưa duyệt, vẫn bị chặn:**
  - Giao diện trang ma trận và chữ hiển thị (chưa có Figma). Chỉ quyền và dữ liệu được duyệt.
  - Thay đổi của 003.1: đổi `getTicketFolioContext` để suy location từ buổi hiện tại của vé; xử lý lý do từ chối mới ở giỏ; hiển thị location.
  - Thay đổi của 003.3: bỏ ghim location trong tùy chọn đổi vé đã tính ở 003.3, yêu cầu Manager khi khác location, áp hệ quả FR-045.
  - Bước "đẩy cập nhật lên OTA" của 003.4 (ngoài phạm vi).
  Mỗi phần cần task, thiết kế và duyệt trong feature của nó.
- **Chưa kiểm:** số phiên bản migration (chọn sau khi fetch `origin/develop`; hiện mới nhất V177) và tên gói trong DB thật (đã khớp `V161__add_data.sql`, chưa truy vấn DB).
- **Task:** Phase 18 (T144–T162) đã có trong `tasks.md`. T154 (bỏ ghim chi nhánh trong `TicketChangeOptionsService`, WF-06 bước 3) thuộc phạm vi 003.2 được duyệt; phần "bỏ ghim" bên 003.3 là phần loại trừ ở trên.
- Bản duyệt này chỉ đúng với các mã băm nêu trên.
