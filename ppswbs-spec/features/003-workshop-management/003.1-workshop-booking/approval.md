# Implementation Design Approval: 003.1 — Gói bán theo location (Phase 18)

**Date created**: 2026-11-17
**Date updated**: 2026-11-19
**Author**: Hồ Hoàng Quốc
**Last updated by**: Hồ Hoàng Quốc

## Approval status

**Status**: PARTIALLY APPROVED

Chỉ phần 003.1 "Package availability per location" (tác động của 003.2 Phase 18) được duyệt. Phần còn lại của 003.1
giữ trạng thái duyệt riêng (spec.md ghi "Approved 2026-11-15", không thay đổi bởi file này).

## Approved revision and scope

- Design revision: working tree trên commit `803e83bc` (nhánh `claude/pkg-location-003-1-tasks-050dab`), các thay đổi
  đã commit ở `6a53a31c`. Nội dung được duyệt nhận diện bằng SHA-256 (12 ký tự đầu) của từng file ngày 2026-11-17:
  - `spec.md` — `cf59b26ae737` (mục "Package availability per location — effect on 003.1": FR-053–FR-057, SC-009, "Owner answers" Q1–Q7)
  - `plan.md` — `eee3213843f6` (mục "Package availability per location (Phase 18)")
  - `data-model.md` — `c242ea36c2a0` (ghi chú `booking_group_line_sessions.location_reference`)
  - `tasks.md` — `8d0de5130c7a` (Phase 18, T154–T164)
  Sửa các file này sau ngày duyệt thì phần bị ảnh hưởng phải được đánh giá lại; mã băm đổi là dấu hiệu. Bản đầu (T154–T163) ở commit `6a53a31c`; T164 (thêm 2026-11-17 theo yêu cầu của Tech Lead, cập nhật hash spec.md và tasks.md) nằm trong commit ghi ở lịch sử git của file này.
- Re-recorded 2026-11-17 after implementation (hashes of the LF-normalised files; the approved text above is kept as history). `plan.md` and `data-model.md` are unchanged. `spec.md` — `ed3be1e88d87`: T163 added `PACKAGE_NOT_AVAILABLE_AT_LOCATION` to the code list of "Documentation sync 2026-11-16" (FR-055 approved by Hồ Hoàng Quốc in session 2026-11-17); the FR-053–FR-057 text is not changed by that edit. `tasks.md` — `3e6a1d2169a4`: T154–T164 ticked with evidence, no scope change.
- Approved scope: T154–T164 — `getTicketFolioContext` đọc buổi hiện tại qua Operations; quyền Staff cấp nhóm theo location hiện tại; giữ bản
  sao booking-time cho danh sách thanh toán lúc đặt; ánh xạ `PACKAGE_NOT_AVAILABLE_AT_LOCATION` ở giỏ; frontend chọn chi nhánh từ
  `GET /workshop-packages/{packageId}/locations`, xử lý 409 của `…/sessions`, hiện `locationName`; cập nhật mô tả 409 trong
  `contracts/workshop-booking.openapi.yaml`; `listBookingTimePayments` trả location tại lúc thanh toán (T164).
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

## Owner decision 2026-11-17 — header notice (FR-058) and interface notes

Hồ Hoàng Quốc (owner of Feature 003) decided in the session of 2026-11-17: (1) FR-058, the pending-payment notice in the site header, with tasks T165–T166; (2) the section "Owner decisions 2026-11-17 — interface behaviour" of `spec.md`, which records the interface changes already made that day (no new business rule). Hashes of the LF-normalised files after the edit: `spec.md` — `26b822bf55c9`; `tasks.md` — `5d3d890f3cca`. `plan.md`, `data-model.md` and the contracts are unchanged (FR-058 uses the existing `GET /members/me/workshop-booking-groups`).

Revision after the owner's review of the first screen (same day): the notice moved from the header's tool row to a slim line under the header card (the tool row wrapped and broke the header), the link carries `resume=1`, and the header and hold-panel clocks tick at the same rate. Hashes above are those of the revised files.

## Owner decision 2026-11-19 — failed payment attempt after a VNPay cancel (FR-032, FR-048)

Hồ Hoàng Quốc (owner of Feature 003) approved in the session of 2026-11-19, in answer to the report listing the decisions: (1) an attempt becomes `FAILED` only from a signature- and amount-verified IPN with `vnp_ResponseCode` in {09, 10, 11, 12, 13, 24, 51, 65, 79} or `vnp_TransactionStatus=02` (list checked against VNPay's sandbox documentation 2026-11-18; 01, 07, 75, 99 and unknown codes keep `PENDING`); (2) one live attempt per group under the group row lock, pay-again after `FAILED` with a new `merchantReference`; (3) pending-hold release blocked only by a live attempt, FR-017j/FR-017k aligned (F2, F3); (4) a verified success on a `FAILED` attempt follows the group state: still `PENDING_PAYMENT` confirms, `CANCELLED`/expired goes to FR-040, `CONFIRMED` goes to FR-013a (F4); (5) no QueryDR in the pay-again path. Scope: `spec.md` FR-032, FR-048, FR-010f, FR-017j, FR-017k, US8 scenario 5; `plan.md`; `data-model.md`; the OpenAPI `pending-hold-release` text; the ingress OpenAPI note and the facade contract note; `tasks.md` Phase 19 (T167–T173; T167 done). The facade command `RecordDefinitePaymentFailureCommand(merchantReference, providerResponseCode, providerTransactionStatus, eventReference)` → `PaymentFailureOutcome` was approved by Hồ Hoàng Quốc in the same session ("dùng đề xuất này") and recorded in `../contracts/workshop-interfeature-facades.md`. No code is written or approved by this entry; the approval is a chat directive ("duyệt"), not a content hash.
