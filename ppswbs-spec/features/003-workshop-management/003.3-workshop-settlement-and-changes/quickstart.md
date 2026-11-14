# Quickstart Validation: Workshop Settlement and Changes

**Date created**: 2026-10-24
**Date updated**: 2026-11-05
**Author**: Hồ Hoàng Quốc
**Last updated by**: Hồ Hoàng Quốc

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
   direct add-on on a `FINALIZED` folio without mutation, and routes every OTA
   shop add-on to a separate direct-Mland bill (free OTA laser engraving
   creates no bill). Verify variable-charge reason/audit behavior.
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
   VNPay refund/QueryDR request or refund allocation is created. OTA refunds are
   tested separately through 003.4 evidence reconciliation.
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
16. **OTA shop add-on bill (D12/D16)**: for an OTA booking verify laser
   engraving is recorded at zero charge with no bill, and a paid shop add-on
   from an accepted 003.4 quote creates one separate Mland bill paid only by
   verified `SHOP_ADDON` evidence, with no change to OTA payment, payout,
   commission or refund facts.

## Evidence to Retain

Retain contract-validation output, facade/module-boundary evidence, safe
correlation references for positive/denied/idempotent outcomes, redacted audit
references, redacted notification-handoff/deduplication evidence, provider test
evidence, and data-bearing upgrade/recovery evidence.
Do not retain raw contact proof, Magic Link/QR values, Firebase tokens, VNPay raw
payloads, signatures or merchant secrets.
