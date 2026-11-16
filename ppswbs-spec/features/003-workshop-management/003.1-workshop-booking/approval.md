# Implementation Design Approval: 003.1 — Gói bán theo location (Phase 18)

**Date created**: 2026-11-17
**Date updated**: 2026-11-17
**Author**: Hồ Hoàng Quốc
**Last updated by**: Hồ Hoàng Quốc

## Approval status

**Status**: PARTIALLY APPROVED

Chỉ phần 003.1 "Package availability per location" (tác động của 003.2 Phase 18) được duyệt. Phần còn lại của 003.1
giữ trạng thái duyệt riêng (spec.md ghi "Approved 2026-11-15", không thay đổi bởi file này).

## Approved revision and scope

- Design revision: working tree trên commit `803e83bc` (nhánh `claude/pkg-location-003-1-tasks-050dab`), các thay đổi
  chưa commit. Nội dung được duyệt nhận diện bằng SHA-256 (12 ký tự đầu) của từng file ngày 2026-11-17:
  - `spec.md` — `6e69e7e70063` (mục "Package availability per location — effect on 003.1": FR-053–FR-057, SC-009, "Owner answers" Q1–Q7)
  - `plan.md` — `db222c192c65` (mục "Package availability per location (Phase 18)")
  - `data-model.md` — `c242ea36c2a0` (ghi chú `booking_group_line_sessions.location_reference`)
  - `tasks.md` — `a7bb53924cb8` (Phase 18, T154–T163)
  Sửa các file này sau ngày duyệt thì phần bị ảnh hưởng phải được đánh giá lại; mã băm đổi là dấu hiệu. Sau khi commit, ghi hash commit vào đây.
- Approved scope: T154–T163 — `getTicketFolioContext` đọc buổi hiện tại qua Operations; quyền Staff cấp nhóm theo location hiện tại; giữ bản
  sao booking-time cho danh sách thanh toán lúc đặt; ánh xạ `PACKAGE_NOT_AVAILABLE_AT_LOCATION` ở giỏ; frontend chọn chi nhánh từ
  `GET /workshop-packages/{packageId}/locations`, xử lý 409 của `…/sessions`, hiện `locationName`; cập nhật mô tả 409 trong
  `contracts/workshop-booking.openapi.yaml`.
- Phụ thuộc, không thuộc phê duyệt này: 003.2 Phase 18 (lý do từ chối, `locationName`, `locations` chỉ ACTIVE, 409 của sessions,
  `replaceTicketSessions` sang location khác) phải có trước T155, T158–T162; phạm vi triển khai của 003.2 xem `003.2-workshop-operations/approval.md`.
- Approval date: 2026-11-17
- Tech Lead: Hồ Hoàng Quốc

## Decisions approved

| Decision | Approved value | Evidence/source | Affected artifact |
| --- | --- | --- | --- |
| Location của vé | Đọc từ buổi hiện tại ở Operations, không lưu bản sao thứ hai | Chỉ thị trong phiên 2026-11-17; spec FR-053; 003.2 FR-045 | spec, plan |
| Q1 | Chưa có vé ở Operations: dùng bản sao dòng giỏ | Chỉ thị "đồng ý" trong phiên 2026-11-17 | spec FR-053 |
| Q2 | Vé không còn ACTIVE: dùng bản sao dòng giỏ | như trên | spec FR-053 |
| Q3 | Operations không trả lời: fail closed, 503 `DEPENDENCY_UNAVAILABLE`, không rơi về bản sao | như trên | spec FR-053 |
| Q4 | Scope Staff cấp nhóm theo location hiện tại của buổi được gán | như trên | spec FR-054, T156 |
| Q5 | Danh sách thanh toán lúc đặt giữ chi nhánh lúc đặt | như trên | spec FR-054, T157 |
| Q6 | Báo giá không từ chối sớm; đặt chỗ là nơi quyết định | như trên | spec FR-055 |
| Q7 | Câu chữ 409, thông báo giỏ, danh sách chi nhánh rỗng theo bản nháp trong spec | như trên | spec FR-055, FR-056, T160, T162 |

## Excluded

- Màn hình ma trận Manager và tên location ở lựa chọn đổi vé 003.3 (003.2, 003.3).
- Cập nhật listing OTA (003.4).
- Mọi việc cần quyết định mới ngoài Q1–Q7.
