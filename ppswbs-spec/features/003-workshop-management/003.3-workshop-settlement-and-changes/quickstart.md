# Quickstart Validation: Workshop Settlement and Changes

**Date created**: 2026-10-24
**Date updated**: 2026-10-31
**Author**: Hồ Hoàng Quốc
**Last updated by**: Hồ Hoàng Quốc

This is a non-production validation guide. It does not authorize production
credentials, provider calls, schema changes or deployment.

## Preconditions

- Use a test VNPay merchant configuration satisfying the shared
  [VNPay configuration preflight](../contracts/vnpay-configuration-preflight.md)
  and synthetic/non-production booking records only.
- Prepare one confirmed Basic Ring booking and one confirmed Silverclay booking
  with frozen post-promotion amount, accepted deposit and remaining amount.
- Provide approved 003.2 facade outcomes for settlement readiness, complete
  replacement, verified-refund consequence and cancellation handoff.
- Use active test Member, Guest contact proof, Staff in and outside the booking
  location, and active Managers with different location assignments. Manager
  actions are global; do not guess an FE-001 role code.
- Use the routes and safe schemas in
  [`contracts/workshop-settlement.openapi.yaml`](./contracts/workshop-settlement.openapi.yaml).

## Validation Scenarios

1. **One bill per package**: once the final required session is attendance-
   finalized, create/read one final bill for Basic Ring and Silverclay. Verify no
   session/ticket/participant bill exists and frozen financial values do not
   change.
2. **Customer-safe reads and charges**: verify an owning Member and a Guest with
   booking code plus exactly one stored email/phone can read their bill and case.
   Each may add only an eligible fixed catalogue add-on before finalization; reject
   arbitrary price, invalid proof, Magic Link write access and post-finalization
   add-on selection. Verify Staff/Manager variable-charge reason/audit behavior.
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
   proves no retained booking/customer data.

## Evidence to Retain

Retain contract-validation output, facade/module-boundary evidence, safe
correlation references for positive/denied/idempotent outcomes, redacted audit
references, redacted notification-handoff/deduplication evidence, provider test
evidence, and data-bearing upgrade/recovery evidence.
Do not retain raw contact proof, Magic Link/QR values, Firebase tokens, VNPay raw
payloads, signatures or merchant secrets.
