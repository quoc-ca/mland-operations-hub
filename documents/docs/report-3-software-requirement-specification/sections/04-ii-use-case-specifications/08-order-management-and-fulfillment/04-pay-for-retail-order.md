### 8.4 Pay for Retail Order

#### Primary Actors

Member.

#### Secondary Actors

VNPay Payment Gateway.


#### Description

As a Member, I want to pay the full frozen retail amount through VNPay so that matching verified evidence completes exactly one sale while expiry or late evidence receives safe handling (UC46).

#### Preconditions

1. The platform is available and a complete retail order, original quote and attributable hold/attempt exist.
2. Human initiation/result access requires verified active Member ownership. A signed provider callback does not require a live Member browser session or grant a human business role.
3. The applicable VNPay and attempt/deadline contracts are approved for operation; confirmed D06-a–f governs timing, one-attempt retry, failure/release and scheduled recovery; merchant-specific controls require planning verification.

#### Postconditions

- An eligible verified payment accepted strictly before original hold expiry makes the order paid and consumes exact held quantities/applicable benefits once.
- Without timely accepted success, unpaid holds expire/release at the original boundary. First success accepted at/after expiry is a recorded operational exception, not automatic payment eligibility or fulfilment.
- Duplicate evidence repeats no settlement effects and does not undo an already accepted sale; frozen terms/history and minimized evidence remain intact.

#### Normal Flow

**Pay for Retail Order**

1. The owning Member opens the accepted order's payment action.
2. The system checks current human access and retrieves its frozen full payable amount/currency/reference, original hold/deadlines and eligible attempt.
3. The system opens the original VNPay link created with accepted checkout, using its one frozen full-amount attempt and T0 deadlines: link T0 + 10 minutes, hold T0 + 15 minutes. It creates no second attempt or fresh clock.
4. The Member completes the external payment and may return to the order result page.
5. VNPay sends successful signed IPN evidence, the normal confirmation channel.
6. The system verifies signature, exact payment/order reference, frozen full amount/currency, successful status and current original attempt/hold/benefit bindings.
7. The system accepts payment only if verification and payment acceptance finish strictly before original hold expiry with an eligible active hold, ensuring that sale settlement and expiry release cannot both apply.
8. The system records one paid sale, consumes its held variant quantities and settles applicable bound benefits once, with safe evidence and preserved original terms.
9. The system shows the owning Member the recorded verified result, removes only purchased unchanged cart lines while preserving subsequent edits/additions, permits UC51 method choice and uses the existing optional-email workflow at approved milestones (A-07/A-08).

#### Alternative Flows

**Steps 1 and 2 — Wrong owner, inactive/revoked account or ineligible human action**

Deny initiation/private result access without exposing another order or creating a new attempt. This does not turn a human role into payment-confirmation authority.

**Step 3 — Initiation failure or uncertain provider response**

Report a safe failure/pending result, preserve attributable original facts and do not create false success/new deadlines. Timeout, connection loss or unknown result preserves pending facts and the original eligible hold. Only an authenticated, matched definitive terminal failure under the approved VNPay contract ends the attempt and releases the whole unpaid reservation bundle; non-success alone is not definitive. Later success after release is an exception, not hold revival. Already accepted paid sales cannot be undone by reordered failure evidence. V1 permits one payment attempt per order. Identical accepted checkout retries return the established order/attempt/link/deadlines; reject changed-input reuse. No replacement attempt/link is created for an order with an active hold. A new checkout revalidates all terms and creates a new order/attempt/reservations; retired evidence never rebinds. (A-04/D-06).

**Step 4 — Browser return or Member claim reports success**

Display only an informational return and the recorded order state. Without verified accepted evidence, do not mark paid, deduct quantity or start preparation; continue evidence processing/recovery as eligible.

**Step 6 — Invalid signature/reference/amount/currency/status or unknown payment**

Reject as settlement authority and safely retain actual processing outcome. Do not repair mismatches, assert paid or allow manual Staff/Admin paid overrides.

**Steps 5 to 7 — Missing valid IPN while hold remains active**

Eligible signed QueryDR may recover matching successful evidence using the same validation, original bindings, strict acceptance cutoff and once-only settlement rules. Without a valid IPN recording a final outcome, and only while the original attempt/hold remains eligible, schedule signed QueryDR at T0 + 10, 12 and 14 minutes; stop after a final outcome or hold expiry. Verify successful transaction status, not merely successful API processing, with all original bindings and the strict acceptance cutoff. Merchant rate/terminal-status controls must be verified in planning before operation. Browser return alone never qualifies.

**Steps 6 to 8 — Duplicate, reordered or concurrent IPN/QueryDR evidence**

Preserve an already accepted sale and record a duplicate/no-change processing result without another stock/point/quota/paid/fulfilment effect. Conflicting or retired/replaced generation evidence cannot settle a replacement quote.

**Step 7 — First payment acceptance exactly at/after expiry, or hold already released**

Treat as a minimized payment exception for manual shop follow-up even if VNPay's payment timestamp or Mland's receipt was earlier. Never revive the hold, automatically mark an unpaid order paid, fulfil, settle a replacement order or add cancellation/refund/reconciliation.

**Step 7 — No timely accepted success at original minute 15**

Expire the unpaid order/hold and release unpaid quantity/eligible benefit reservations without sale deduction, point debit or compensating refund credit. Earlier evidence still awaiting completed verification does not extend eligibility. At the original expiry boundary reservations cease contributing to logical held availability. Expiry-state processing completes within 60 seconds in normal operating conditions; that tolerance grants no payment/hold extension. Verified settlement and release remain mutually exclusive. (D06-e).

**Steps 6 to 8 — Catalogue price change or unpublish after a valid hold**

Use the original frozen quote and eligibility under unchanged original deadlines. Do not reprice the held order; expired holds and stale carts receive no revival/protection.

**Steps 8 and 9 — Settlement/state failure, result read failure or notice failure**

Do not report a paid order with only partial settlement or repeat a previously accepted effect. Preserve established evidence and show safe pending/retry guidance. Notification failure never undoes a verified sale; In-application recorded status is primary. Optional email uses the existing workflow and approved usable contact for paid, ready_for_pickup and completed pickup/carrier handoff; email is not mandatory. Send once and retry at most twice, after 1 and 5 minutes from initial send. Failure never changes a sale/order; messages obey minimization and recipient-data retirement. After paid, remove only purchased cart lines unchanged by the Member since checkout. Preserve any subsequently edited or added line entirely; never subtract purchased quantity from a later edit. Frozen orders remain independent of cart edits. (confirmed A-07/A-08).

**Steps 6 and 7 — Verified definitive terminal failure before paid acceptance**

Timeout, connection loss or unknown result preserves pending facts and the original eligible hold. Only an authenticated, matched definitive terminal failure under the approved VNPay contract ends the attempt and releases the whole unpaid reservation bundle; non-success alone is not definitive. Later success after release is an exception, not hold revival. Already accepted paid sales cannot be undone by reordered failure evidence. Release all unpaid quantity/benefit reservations once; new checkout creates a new order/attempt with revalidated terms, not a replacement bound to this order.

#### Business Rules

BR-46-01, BR-46-02, BR-46-03, BR-46-04, BR-46-05, BR-46-06, BR-46-07, BR-46-08, BR-46-09

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-46-01 | Only matching valid signed successful VNPay IPN or eligible signed QueryDR confirms payment. Browser returns, Member assertions and human paid flags are not authority. |
| BR-46-02 | Payment uses the exact frozen full positive whole-VND quote/reference/currency; discounts and variant/benefit bindings cannot be reassigned or repriced after acceptance. |
| BR-46-03 | T0 is Mland acceptance of whole-cart checkout, creating the order, all quantity/benefit holds and the first payment attempt together. The existing VNPay link expires at T0 + 10 minutes and the hold at T0 + 15 minutes. UC46 opens that original link; opening/retrying never resets either clock. V1 permits one payment attempt per order. Identical accepted checkout retries return the established order/attempt/link/deadlines; reject changed-input reuse. No replacement attempt/link is created for an order with an active hold. A new checkout revalidates all terms and creates a new order/attempt/reservations; retired evidence never rebinds. Without a valid IPN recording a final outcome, and only while the original attempt/hold remains eligible, schedule signed QueryDR at T0 + 10, 12 and 14 minutes; stop after a final outcome or hold expiry. Verify successful transaction status, not merely successful API processing, with all original bindings and the strict acceptance cutoff. Merchant rate/terminal-status controls must be verified in planning before operation. |
| BR-46-04 | Confirmed C2/D-06: verification and payment acceptance must finish strictly before original hold expiry. First acceptance at/after the boundary is an exception regardless of earlier provider/receipt timestamps; no grace or hold revival. |
| BR-46-05 | Settle the paid sale, held quantities and applicable point/quota reservations once, or preserve an uncertain/failed outcome without reporting partial paid success. Duplicates/reordering and concurrent recovery/expiry must not repeat or both consume/release resources. |
| BR-46-06 | Release unpaid resources at original expiry without sale deduction/debit; retired/released/replaced attempts/generations do not settle replacements. Late evidence is manual operational exception only, outside cancellation/refund/accounting/reconciliation. |
| BR-46-07 | Catalogue price/unpublish changes do not invalidate earlier accepted valid held terms within their original deadlines. Already accepted sales survive later duplicate evidence. |
| BR-46-08 | Confirmed behavior: Timeout, connection loss or unknown result preserves pending facts and the original eligible hold. Only an authenticated, matched definitive terminal failure under the approved VNPay contract ends the attempt and releases the whole unpaid reservation bundle; non-success alone is not definitive. Later success after release is an exception, not hold revival. Already accepted paid sales cannot be undone by reordered failure evidence. After paid, remove only purchased cart lines unchanged by the Member since checkout. Preserve any subsequently edited or added line entirely; never subtract purchased quantity from a later edit. Frozen orders remain independent of cart edits. In-application recorded status is primary. Optional email uses the existing workflow and approved usable contact for paid, ready_for_pickup and completed pickup/carrier handoff; email is not mandatory. Send once and retry at most twice, after 1 and 5 minutes from initial send. Failure never changes a sale/order; messages obey minimization and recipient-data retirement. (confirmed A-04/A-07/A-08). |
| BR-46-09 | Retain safe normally append-only actor/source, target, action/time, actual SUCCESS/REJECTED/FAILED outcome and reason, with permitted references/context. Rejected or failed attempts are not success; exclude credentials, raw tokens and avoidable personal data. Business audit follows five-year retention without extending recipient-data retention. |
