### 8.3 Checkout Retail Order

#### Primary Actors

Member.

#### Secondary Actors

VNPay Payment Gateway.

#### Description

As a Member, I want my whole cart revalidated and quoted at approved prices so that one complete order holds the exact variants and agreed purchase terms without a partial sale (UC45).

#### Preconditions

1. The platform is available.
2. The actor is authenticated with verified identity, has an active account, and holds current authority for the requested action.
3. The Member owns a nonempty cart and can select an active processing branch before a new order/hold.
4. Approved catalogue/quantity and initiation/deadline contracts are available. Requested benefits require complete approved applicable policies; missing benefit policies do not block an otherwise-valid no-benefit order. Timing/attempt rules are confirmed D06-a–f; commercial benefit values remain dependency/TBD D-09.

#### Postconditions

- Successful checkout retains one complete order, frozen processing branch/line/discount/payable snapshots and its whole-cart hold with original deadline evidence.
- Rejected checkout creates no partial accepted order/hold/benefit bundle; invalid/unavailable lines are removed and reported.
- Checkout does not itself assert paid, deduct sold quantities or authorize preparation; UC46 requires verified payment.

#### Normal Flow

**Checkout Retail Order**

1. The Member opens checkout from the entire own cart.
2. The system verifies current account/ownership authority and retrieves all cart lines.
3. The Member selects an active processing branch and chooses no benefits or available approved voucher/points benefits.
4. The system revalidates every line for current public eligibility, exact configured variant, applied approved price and logically available quantity.
5. The system validates selected branch/benefit eligibility and calculates approved variant subtotal, then at most one voucher, then approved points on the remainder; each step must yield whole VND without invented rounding.
6. The system displays current line/options/quantity/prices, branch, discounts and positive payable total for acceptance; any changed-term review follows confirmed A-01.
7. The Member submits acceptance of the complete quote.
8. The system rechecks authority, limits, latest eligibility/quantity and approved terms, then accepts one complete order, whole-cart quantity/benefit holds and the first payment attempt together or none. It freezes policy versions/results and T0, with link expiry T0 + 10 minutes and hold expiry T0 + 15 minutes.
9. The system records safe evidence, displays the accepted reference/quote/deadlines and continues to UC46 for full VNPay payment.

#### Alternative Flows

**Steps 2 and 8 — Unauthorized, empty or missing/inactive branch**

Reject before a new order/hold; reveal no other Member's cart/order. Require an active selected branch and safe correction. Branch inactivity blocks new checkout selection, but accepted orders retain the original branch and deadlines. Existing payment/holds continue under the original rules; paid orders may be completed by currently assigned Staff or explicitly action/branch-delegated Manager. Inability to physically hand over requires operational follow-up, not automatic relocation/cancellation/refund. (D05-a).

**Steps 4 and 8 — Any invalid/unavailable line, or changed price/quote**

Remove/report invalid or unavailable lines and create no partial order/hold. Existing accepted holds remain protected separately. Confirmed behavior (A-01): require review and a fresh explicit submission for the corrected cart/changed quote before ordering survivors; resume at Step 1.

**Step 5 — Requested benefit unavailable, incomplete or unapproved**

Reject the quote and require explicit Member reselection at Step 3. Do not silently remove a benefit, invent rates/caps or change to a full-price order. A newly selected otherwise-valid no-benefit quote may proceed (C5/D-09).

**Steps 5 and 8 — Zero/negative or invalid whole-VND total**

Reject and require discount/selection correction before creating a new order/hold. Do not floor/round, remove benefits silently, create a zero-payment VNPay attempt or bypass the provider to mark paid.

**Step 8 — Last-unit or benefit-reservation contention**

Accept the entire bundle only if exact-variant quantities and approved benefit resources remain available. Otherwise reject safely without a partial accepted bundle or cross-variant substitution.

**Step 8 — Duplicate submission, changed input or uncertain/save outcome**

Accepted identical checkout retries must not create another order/hold or new deadlines. V1 permits one payment attempt per order. Identical accepted checkout retries return the established order/attempt/link/deadlines; reject changed-input reuse. No replacement attempt/link is created for an order with an active hold. A new checkout revalidates all terms and creates a new order/attempt/reservations; retired evidence never rebinds. T0 is Mland acceptance of whole-cart checkout, creating the order, all quantity/benefit holds and the first payment attempt together. The existing VNPay link expires at T0 + 10 minutes and the hold at T0 + 15 minutes. UC46 opens that original link; opening/retrying never resets either clock. On failure preserve established evidence, report the actual outcome and provide safe recovery without inventing success.

#### Business Rules

BR-45-01, BR-45-02, BR-45-03, BR-45-04, BR-45-05, BR-45-06, BR-45-07, BR-45-08

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-45-01 | Member ownership/current authority and an active Member-selected processing branch are required before a new order/hold. Freeze that branch with the quote; later pickup/GHTK changes do not move it (D-01; C1/D-05). |
| BR-45-02 | Checkout covers the entire cart. Revalidate all lines and remove/report invalid or unavailable ones without a partial order; exact variants cannot substitute for each other. |
| BR-45-03 | Freeze product name, variant/options, quantities, unit prices/currency/amounts, subtotal, branch, discounts/approved benefit terms and positive whole-VND payable total. Later catalogue/policy changes do not rewrite accepted snapshots. |
| BR-45-04 | Permit otherwise-valid no-benefit checkout. Requested voucher/points require complete approved applicable policy/branch terms; reject unavailable benefits for explicit reselection. Calculate approved variant subtotal, then at most one approved voucher, then approved points on the remainder. Every calculation must yield whole VND under approved policy, without invented rounding. Freeze policy versions/results with the order; reserve quantity/voucher/points together or none, consume once on accepted payment, release once on unpaid failure/expiry. If policy permits earning, its trigger is verified paid once per order, not fulfilment. D-09 Dependency/TBD: commercial voucher values, eligibility/caps and point conversion, minimum spend, earning rates and credit lifetime require complete policy-owner approval. Order defines no numeric defaults or policy/ledger administration. Missing terms disable only the related benefit; otherwise-valid no-benefit checkout remains enabled. |
| BR-45-05 | Reserve exact quantities subject to remaining-unsold minus active holds, together with applicable benefits or none. Unpaid holds do not deduct sale quantities, debit points or consume voucher quota. |
| BR-45-06 | T0 is Mland acceptance of whole-cart checkout, creating the order, all quantity/benefit holds and the first payment attempt together. The existing VNPay link expires at T0 + 10 minutes and the hold at T0 + 15 minutes. UC46 opens that original link; opening/retrying never resets either clock. V1 permits one payment attempt per order. Identical accepted checkout retries return the established order/attempt/link/deadlines; reject changed-input reuse. No replacement attempt/link is created for an order with an active hold. A new checkout revalidates all terms and creates a new order/attempt/reservations; retired evidence never rebinds. |
| BR-45-07 | Confirmed behavior: require review and explicit resubmission for corrected cart/changed quote (A-01), reject stale conflicts (A-02), reuse only identical accepted commands (A-03). After paid, remove only purchased cart lines unchanged by the Member since checkout. Preserve any subsequently edited or added line entirely; never subtract purchased quantity from a later edit. Frozen orders remain independent of cart edits. (A-07). |
| BR-45-08 | Retain safe normally append-only actor/source, target, action/time, actual SUCCESS/REJECTED/FAILED outcome and reason, with permitted references/context. Rejected or failed attempts are not success; exclude credentials, raw tokens and avoidable personal data. Business audit follows five-year retention without extending recipient-data retention. |
