### 7.4 Pay for Retail Order

#### Primary Actors

Member.

#### Secondary Actors

VNPay Payment Gateway.

#### Description

As a Member, I want to pay the full frozen retail amount through VNPay so that matching verified evidence completes exactly one sale while expiry or late evidence receives safe handling.

#### Preconditions

1. The platform is available and a complete retail order, original quote and attributable hold/attempt exist.
2. Human initiation/result access requires verified active Member ownership. A signed provider callback does not require a live Member browser session or grant a human business role.
3. The applicable VNPay and attempt/deadline contracts are approved for operation; merchant-specific signature, terminal-status and recovery controls must be verified before operation.

#### Postconditions

- An eligible verified payment accepted strictly before original hold expiry makes the order paid and consumes exact held quantities/applicable benefits once.
- Without timely accepted success, unpaid holds expire/release at the original boundary. First success accepted at/after expiry is a recorded operational exception, not automatic payment eligibility or fulfilment.
- Duplicate evidence repeats no settlement effects and does not undo an already accepted sale; frozen terms/history and minimized evidence remain intact.

#### Normal Flow

**Pay for Retail Order**

1. The owning Member is sent to VNPay right after Đặt hàng, or presses Thanh toán on an order in Chờ thanh toán.
2. The system checks current human access and retrieves its frozen full payable amount/currency/reference, original hold/deadlines and eligible attempt.
3. The system opens the VNPay link stored for the checkout and its frozen full-amount attempt. The link lasts until the hold expires 10 minutes after checkout acceptance; pressing Thanh toán again reopens the same link and creates no second VNPay transaction or new deadlines.
4. The Member completes the external payment and returns to Purchase Order History.
5. VNPay sends successful signed IPN evidence, the normal confirmation channel.
6. The system verifies signature, exact payment/order reference, frozen full amount/currency, successful status and current original attempt/hold/benefit bindings.
7. The system accepts payment only if verification and payment acceptance finish strictly before original hold expiry with an eligible active hold, ensuring that sale settlement and expiry release cannot both apply.
8. The system records one paid sale, consumes its held variant quantities and settles applicable bound benefits once, with safe evidence and preserved original terms.
9. The system shows the owning Member the recorded verified result in Purchase Order History at the tab of that order (Chờ lấy hàng for GHTK, Chờ nhận tại cửa hàng for pickup, Tất cả when the checkout mixes both), removes only purchased unchanged cart lines while preserving subsequent edits/additions, lets Staff start preparation and uses the existing optional-email workflow at approved milestones.

#### Alternative Flows

**Steps 1 and 2 — Wrong owner, inactive/revoked account or ineligible human action**

Deny initiation/private result access without exposing another order or creating a new attempt. This does not turn a human role into payment-confirmation authority.

**Step 3 — Initiation failure or uncertain provider response**

Display the recorded pending/error state and retain the original eligible hold. Timeout or an unknown result creates no second attempt or new deadlines; the Member may reopen the existing link while valid.

**Step 4 — Member leaves VNPay without paying**

Purchase Order History opens Chờ thanh toán with a countdown to the hold deadline. The Member may press Thanh toán to reopen the same link, or Hủy đơn, which releases the held quantities, voucher and points and returns the rings to the cart. A payment that still arrives after the cancellation goes to the Manager reconciliation queue.

**Step 4 — Browser return or Member claim reports success**

Display only an informational return and the recorded order state. Without verified accepted evidence, do not mark paid, deduct quantity or start preparation; continue evidence processing/recovery as eligible.

**Step 6 — Invalid signature/reference/amount/currency/status or unknown payment**

Reject as settlement authority and safely retain actual processing outcome. Do not repair mismatches, assert paid or allow manual Staff/Admin paid overrides.

**Steps 5 to 7 — No valid IPN recorded**

When the 10-minute hold ends, signed QueryDR checks an opened but unconfirmed payment about every 5 minutes for 60 minutes. A verified failure or expiry releases the order; a verified success goes to the Manager reconciliation queue; a still unresolved payment is also queued. API success alone is insufficient.

**Steps 6 to 8 — Duplicate, reordered or concurrent IPN/QueryDR evidence**

Preserve an already accepted sale and record a duplicate/no-change processing result without another stock/point/quota/paid/fulfilment effect. Conflicting or retired/replaced generation evidence cannot settle a replacement quote.

**Step 7 — First payment acceptance exactly at/after expiry, or hold already released**

Treat as a minimized payment exception for manual shop follow-up even if VNPay's payment timestamp or Mland's receipt was earlier. Place it in the Manager reconciliation queue. Never revive the hold, automatically mark an unpaid order paid, fulfil, settle a replacement order or refund automatically.

**Step 7 — No timely accepted success when the hold ends**

For a checkout with no payment started, or with a verified failure or expiry, expire the unpaid order and release the held quantities, voucher and points without sale deduction or compensating refund credit. Verified settlement and release remain mutually exclusive.

**Steps 6 to 8 — Catalogue price change or unpublish after a valid hold**

Use the original frozen quote and eligibility under unchanged original deadlines. Do not reprice the held order; expired holds and stale carts receive no revival/protection.

**Steps 8 and 9 — Settlement/state failure, result read failure or notice failure**

Preserve established evidence and show safe pending/retry guidance without partial paid success or repeated settlement. Optional email failure never undoes a sale; recorded order status remains available. Paid cart cleanup preserves all subsequent Member edits/additions.

**Steps 6 and 7 — Verified definitive terminal failure before paid acceptance**

A matched, verified definitive terminal failure ends the attempt; the unpaid quantity and benefit reservations are released once when the hold ends. Nonterminal/unknown outcomes do not qualify. Later success after release becomes an exception; reordered failure never undoes an accepted sale. A new checkout creates a new order/attempt after revalidation.

> **Open difference (checked 2026-11-25):** Feature-008 requires an unpaid VNPay hold to expire at its 10-minute deadline. The current code keeps an opened payment without verified failure evidence in QueryDR recovery and then queues it for the Manager.

#### Business Rules

BR-55-01, BR-55-02, BR-55-03, BR-55-04, BR-55-05, BR-55-06, BR-55-07, BR-55-08, BR-55-09

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-55-01 | Only a matching valid signed successful VNPay IPN or eligible signed QueryDR confirms payment; browser returns and human assertions never do. |
| BR-55-02 | Verify the frozen full positive whole-VND amount, currency, reference and original variant/benefit bindings; never reprice or reassign accepted terms. |
| BR-55-03 | Use the single stored VNPay link of the checkout, valid until the original 10-minute hold expires; reopening it creates no second transaction. Eligible QueryDR recovery checks an opened but unconfirmed payment before the hold is released. |
| BR-55-04 | Complete verification and payment acceptance strictly before original hold expiry; first acceptance at/after expiry is an exception regardless of earlier receipt/provider timestamps. |
| BR-55-05 | Payment consumes held quantities/benefits once; duplicates, reordering, recovery and expiry races cannot repeat effects or both settle and release the same resources. |
| BR-55-06 | Release unpaid holds at expiry; late or retired/replaced evidence cannot revive holds, settle replacements or automatically fulfil. |
| BR-55-07 | Catalogue changes/unpublish preserve accepted valid held terms; duplicate evidence never undoes an accepted sale. |
| BR-55-08 | Unknown outcomes retain the original eligible hold; verified definitive failure releases it. Paid cart cleanup and optional notices follow shared policies without reversing sales. |
| BR-55-09 | Record each attempt and its actual outcome under the shared audit policy. |
