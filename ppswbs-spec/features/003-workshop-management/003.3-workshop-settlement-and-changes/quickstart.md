# Quickstart Validation: Workshop Settlement and Changes

> **OTA đã bị loại 2026-11-25** (chưa có tài khoản/sandbox đối tác): feature 003.4 và mọi hành vi OTA không còn hiệu lực. Phần còn nhắc OTA/003.4 bên dưới (Clarifications, câu "superseded", task đã đóng) chỉ là lịch sử. Xem [quyết định loại OTA](../ota-removal-2026-11-25.md).

**Date created**: 2026-10-24
**Date updated**: 2026-11-16
**Author**: Hồ Hoàng Quốc
**Last updated by**: Hồ Hoàng Quốc

> **Historical (2026-11-15): this guide validates the booking-wide model only.**
> Current validation is per ticket: spec User Stories 5–10, tasks T103 and T123,
> and `contracts/workshop-settlement-ticket.openapi.yaml`. The booking-wide
> routes named below were removed from the code. Do not use this guide to
> accept current behavior.

This is a non-production validation guide. It does not authorize production
credentials, provider calls, schema changes or deployment.

## Preconditions

- Use a test VNPay merchant configuration satisfying the shared
  [VNPay configuration preflight](../contracts/vnpay-configuration-preflight.md)
  and synthetic/non-production booking records only.
- Prepare one confirmed one-session booking and one confirmed Wax Casting booking
  with frozen post-promotion amount, accepted deposit and remaining amount.
- Provide approved 003.2 facade outcomes for settlement readiness, complete
  replacement, verified-refund consequence and cancellation handoff.
- Use active test Member, Guest contact proof, Staff in and outside the booking
  location, and active Managers with different location assignments. Manager
  actions are global; do not guess an FE-001 role code.
- Use the routes and safe schemas in
  [`contracts/workshop-settlement.openapi.yaml`](./contracts/workshop-settlement.openapi.yaml).

## Validation Scenarios

1. **One folio per package**: after deposit confirmation, verify exactly one
   OPEN folio exists for the one-session offer and Wax Casting, with the
   verified deposit as its first collection; replaying `MlandBookingConfirmedEvent`
   or `POST /final-bill` returns the same folio. Verify no
   session/ticket/participant bill exists and frozen financial values do not
   change. Finalization is still allowed only after the final required session
   is attendance-finalized.
2. **Customer-safe reads and Staff settlement charges**: verify an owning Member
   and a Guest with booking code plus exactly one stored email/phone can read
   their bill and case, but cannot create add-on or variable charge lines.
   Verify in-scope Staff/Manager records customer-consented eligible catalogue
   add-ons with a server-derived price (including priced laser engraving for a direct Mland booking), rejects arbitrary price and stale/duplicate
   input, appends a direct add-on only to the OPEN direct folio, rejects a
   direct add-on on a `FINALIZED` folio without mutation. Verify variable-charge reason/audit behavior.
3. **Finalization and client retries**: verify in-scope Staff finalizes only a
   zero-balance bill. Verify Manager cannot override an outstanding amount and
   package output remains withheld until signed matching payment evidence
   settles it. Replay every client mutation with the same `Idempotency-Key`
   and confirm its original safe result; reuse the key with changed input and
   confirm `409 IDEMPOTENCY_CONFLICT` without a second effect.
4. **VNPay evidence**: send a valid signed `GET`
   shared `paymentintegration` `/api/v1/integrations/payments/vnpay/events`
   test callback with matching
   reference/amount/currency, then replay it. Confirm JSON `RspCode`/`Message`,
   exactly-once settlement, and no effect from invalid signature, mismatch, late
   event, browser Return URL or manual counter record. Exercise outbound QueryDR
   reconciliation only with approved test configuration.
5. **No credit and output handover**: attempt finalization as Staff and Manager
   while an outstanding balance remains; both must receive a conflict and no
   state change. Confirm signed matching VNPay evidence settles the full balance
   before finalization/output handover becomes available. There is no deferred
   payment case, reminder, or payment-link reissue route.
6. **Customer cancellation**: submit one Member or Guest whole-booking
   cancellation case. Verify an eligible future booking is handed to Operations,
   the case resolves with `DIRECT_DEPOSIT_FORFEITED`, and the direct deposit is
   retained. Ineligible or Operations-rejected requests remain `REJECTED`; no
   VNPay refund/QueryDR request or refund allocation is created. 
7. **Replacement and Mland remedy**: submit a complete target session set and
   reason for a Manager change. Verify Operations applies all capacity/entitlement
   changes or none and QR persists. For Mland cancellation, require the same
   `cancellationHandoffReference` for replacement and `UNAVAILABLE`/`DECLINED`
   remedy before refund.
8. **Policy and authorization**: verify any active Manager reads/revises
   refund-policy history. A future 0..100 integral revision affects only later
   confirmed bookings and uses the stated floor-VND rule; customer views reveal
   only their own retained snapshot/outcome. Verify inactive/out-of-scope actor
   denial has no side effect.
9. **Notification isolation**: for a verified final payment/refund, replay the
   post-commit handoff event and verify there is no second delivery request.
   Simulate an unavailable or uncertain notification provider; final-bill,
   payment, refund, case and Booking `FULLY_PAID` outcomes must remain unchanged.
   Verify no event or delivery/audit log retains a recipient, payment-action-link
   value, raw gateway evidence or ticket/QR material.
10. **Data-bearing rollout**: on an upgrade copy with retained booking/customer
   data, execute the append-only preservation migration plan and retain
   upgrade/recovery evidence. A reset is allowed only after dev/test preflight
   proves no retained booking/customer data. Verify the D5 backfill creates
   exactly one OPEN folio per existing confirmed direct booking and is
   idempotent with the `MlandBookingConfirmedEvent` consumer.
11. **Full no-show closure (D2/D3/D13)**: finalize every required session with
   all tickets unchecked; inside the 24-hour correction window verify no
   `BookingAttendanceFinalizedEvent` and the folio stays `OPEN`. After the
   window closes, verify one event, `BookingAttendanceOutcomeQuery` returns
   terminal `NO_SHOW`, the folio becomes `CLOSED` at the verified deposit, no
   refund/QueryDR is requested, Booking becomes `NO_SHOW`, and a Member
   booking emits `WORKSHOP_NO_SHOW_FINAL`. Replay the event and verify no
   second effect.
12. **Partial attendance (D2/D6)**: check in some but not all tickets; verify
   the outcome is `PARTIAL`, the folio stays `OPEN` for normal checkout of the
   full package price plus add-ons, and after `FINALIZED` with zero balance
   Booking becomes `COMPLETED` and a Member booking earns points
   (`WORKSHOP_COMPLETED_AND_FULLY_PAID`).
13. **Customer cancellation closure (P3/D13)**: for an eligible cancellation,
   verify the folio becomes `CLOSED` at the verified deposit in the same
   transaction that resolves the case and Booking becomes `CANCELLED`.
14. **Mland remedy closure (D11/D13/D14/D15)**: for an `UNAVAILABLE`/`DECLINED`
   remedy, verify Booking becomes `REFUND_PENDING` when the first allocation
   is dispatched, the folio stays `OPEN` (rejecting add-ons) while any
   allocation is pending/unknown, and after every allocation is verified by
   signed QueryDR the folio becomes `CLOSED` with collected and refunded
   amounts, Booking becomes `REFUNDED` and `CANCELLED`, and a Member booking
   emits `VALID_WORKSHOP_CANCELLATION`.
15. **Post-FINALIZED correction (D7–D10)**: on a `FINALIZED` folio verify Staff,
   Member and Guest cannot open a correction; an active Manager with a reason
   opens an overcharge correction that is allocated oldest verified payment
   first, never exceeds unrefunded collections, completes only on signed
   QueryDR, and changes neither folio lines/state nor Booking status nor
   Loyalty earn. Verify no undercharge collection route exists.
16. *(Removed 2026-11-25: OTA shop add-on bill.)*

## Per-ticket validation (current)

This section is the current acceptance path (added 2026-11-16, R29); the scenarios above stay historical. Validate per ticket with: spec User Stories 5-13 (per-ticket settlement, bank refunds, cancellation and change, refund account, reconciliation, cash correction); task T103 (per-ticket end-to-end verification) and T138 (SC-015 accessibility and browser verification of every screen, after T164); and the reconciliation scenario: online booking-time VNPay 6,000,000 VND (IPN success) + counter collection 1,200,000 VND = 7,200,000 VND confirmed total in the FR-056 list, with an unverified booking-time row shown as `PENDING_VERIFICATION` and left out of the confirmed total. Contracts: `contracts/workshop-settlement-ticket.openapi.yaml`.

## Vận hành hiện hành 003.3

*(Added 2026-11-16, fix A14; completed by task T137 on 2026-11-16. The scenarios above are historical; this section is the current operations runbook for the per-ticket model.)*

### Thay khóa mã hóa số tài khoản hoàn tiền (`WORKSHOP_REFUND_ACCOUNT_KEY`, FR-040, T137)

1. Quyết định thay khóa (khóa bị lộ hoặc luân chuyển định kỳ). Hệ thống không xoay khóa trong V1: mọi số tài khoản đang được mã hóa bằng khóa cũ sẽ bị xóa.
2. Thông báo trước cho Manager: các yêu cầu hoàn tiền đang giữ số tài khoản sẽ quay về `PENDING_ACCOUNT`, người nhận phải nhập lại số tài khoản và Manager phải duyệt lại.
3. Đổi giá trị biến môi trường `WORKSHOP_REFUND_ACCOUNT_KEY` trong cấu hình triển khai (không ghi vào tài liệu, log hay repo) và khởi động lại dịch vụ.
4. Một Admin gọi `POST /api/v1/workshop-refund-account-key-replacements` với `reason` và header `Idempotency-Key` (Manager và Staff nhận `403`).
5. Kết quả mong đợi, mỗi yêu cầu một giao dịch: bản mã bị xóa nhưng giữ mask, trạng thái `PENDING_ACCOUNT`, link cũ bị thu hồi, link mới gửi cho người nhận, phê duyệt cũ của Manager vô hiệu, một bản ghi audit mỗi yêu cầu, không số tài khoản nào được trả về hay ghi log.
6. Gọi lại cùng route là an toàn: chỉ tác động các yêu cầu còn giữ bản mã (chạy lại không đổi gì).
7. Theo dõi: người nhận nhập lại số tài khoản qua link mới; Manager duyệt lại sau khi có tài khoản mới.

Cột lưu bản mã là `settlement_refund_requests.recipient_account_encrypted` (V122, nullable): route đặt cột này về `NULL` và giữ `recipient_account_masked`, nên **không cần migration**. Phản hồi `200` chỉ có số lượng: `requestsReset`, `linksSent` và `failed` (yêu cầu không xử lý được vẫn giữ bản mã; gọi lại với `Idempotency-Key` mới để thử lại). Mỗi yêu cầu có hai bản ghi audit: `REFUND_ACCOUNT_KEY_REPLACED` (kèm lý do) và `REFUND_ACCOUNT_LINK_RESENT`. Kiểm chứng bằng `RefundAccountMysqlIT`, `BankRefundServiceTest` và `RefundAccountKeyReplacementControllerTest`.

## Changes across locations (Phase 14, FR-058–FR-063, T192; added 2026-11-17)

Validate with a package sold (`ACTIVE`) at two locations A and B, and a two-session package with `sameLocationRequired`.

1. **Options (FR-058, FR-063):** `GET /workshop-tickets/{code}/change-options` lists sessions at A and B, each with `locationName`, seats left and changes left (UI: `<Location> – <date time> · còn N chỗ · còn M lượt đổi`); a session with no seat or no quota is not listed; a location where the pair is `INACTIVE` is never listed.
2. **Move without a Manager (FR-059):** an online buyer, or Staff in scope and assigned, changes a ticket from A to B: applied at once (200), one counted change, audit action `TICKET_LOCATION_CHANGED` with `from=<A id>;to=<B id>`. Staff of A are then refused (`403`), assigned Staff of B allowed.
3. **Two-session package:** both sessions move together to B; moving one alone is `409 INVALID_REPLACEMENT` (003.2 rule, passed on unchanged).
4. **Pair switched off (FR-059 d):** a target whose pair is `INACTIVE` is `409 PACKAGE_NOT_AVAILABLE_AT_LOCATION` at `POST /changes` and when an older queued request is approved; the request stays `PENDING`, no allowance is used.
5. **Window boundary (FR-062, SC-023):** a change must land by 23:59 of the 7th calendar day (`Asia/Ho_Chi_Minh`) after the replaced session's end date; a target at 00:00 of day 8 is `409 LATE_ARRIVAL_WINDOW_EXPIRED` (never queued, a Manager cannot lift it). A session ending 09:00 and one ending 23:00 the same day share the limit. Mland replacements are exempt.
6. **Money (FR-060, SC-022):** a collection made before the move keeps branch A (`branch_id` immutable); one made after takes B; each branch's cash close counts its own cash. A Manager reads `GET /workshop-tickets/{code}/folio/previous-location-add-ons` (Staff/Member `403`; empty almost always).
7. **Mland offer (FR-061):** the offer lists candidates at several locations with `locationName`; the buyer's pick at B moves the ticket with no Manager and no counted change; if B was switched off before the answer the answer is `409 REPLACEMENT_SESSION_UNAVAILABLE` (003.2 rejects the replacement at commit; 003.3 does not re-query) and the offer stays open.

Evidence of the current run: `quickstart-verification-evidence.md` (T193 entry). Acceptance criteria SC-021–SC-023 are handed to `ppswbs-testing/` for detailed scenarios (`$e2e-test-specify`, `$unit-test-specify`); none are written here.

## Evidence to Retain

Retain contract-validation output, facade/module-boundary evidence, safe
correlation references for positive/denied/idempotent outcomes, redacted audit
references, redacted notification-handoff/deduplication evidence, provider test
evidence, and data-bearing upgrade/recovery evidence.
Do not retain raw contact proof, Magic Link/QR values, Firebase tokens, VNPay raw
payloads, signatures or merchant secrets.
