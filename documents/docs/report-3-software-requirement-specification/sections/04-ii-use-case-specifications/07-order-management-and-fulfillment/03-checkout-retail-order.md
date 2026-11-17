### 7.3 Checkout Retail Order

#### Primary Actors

Member.

#### Secondary Actors

VNPay Payment Gateway.

#### Description

As a Member, I want my whole cart revalidated and quoted at approved prices so that one complete order holds the exact variants and agreed purchase terms without a partial sale.

#### Preconditions

1. The platform is available.
2. The actor is authenticated with verified identity, has an active account, and holds current authority for the requested action.
3. The Member owns a nonempty cart and can select an active processing branch before a new order/hold.
4. Approved catalogue/quantity and initiation/deadline contracts are available. Requested benefits require complete approved applicable policies; missing benefit policies do not block an otherwise-valid no-benefit order. Commercial benefit values require complete policy-owner approval.

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
6. The system displays current line/options/quantity/prices, branch, discounts and positive payable total for acceptance; changed terms require explicit Member review and resubmission.
7. The Member submits acceptance of the complete quote.
8. The system rechecks authority, limits, latest eligibility/quantity and approved terms, then accepts one complete order, whole-cart quantity/benefit holds and the first payment attempt together or none. It freezes policy versions/results and the checkout acceptance time; the payment link expires 10 minutes later and the hold 15 minutes later.
9. The system records safe evidence, displays the accepted reference/quote/deadlines and continues to UC46 for full VNPay payment.

#### Alternative Flows

**Steps 2 and 8 — Unauthorized, empty or missing/inactive branch**

Reject before a new order/hold; reveal no other Member's cart/order. Require an active selected branch and safe correction. An inactive branch cannot receive new checkout, but accepted orders retain their branch and original deadlines. Currently authorized assigned Staff/delegated Manager may complete paid orders; physical handover problems require operational follow-up without automatic transfer, cancellation or refund.

**Steps 4 and 8 — Any invalid/unavailable line, or changed price/quote**

Remove/report invalid or unavailable lines and create no partial order/hold. Existing accepted holds remain protected separately. require review and a fresh explicit submission for the corrected cart/changed quote before ordering survivors; resume at Step 1.

**Step 5 — Requested benefit unavailable, incomplete or unapproved**

Reject the quote and require explicit Member reselection at Step 3. Do not silently remove a benefit, invent rates/caps or change to a full-price order. A newly selected otherwise-valid no-benefit quote may proceed.

**Steps 5 and 8 — Zero/negative or invalid whole-VND total**

Reject and require discount/selection correction before creating a new order/hold. Do not floor/round, remove benefits silently, create a zero-payment VNPay attempt or bypass the provider to mark paid.

**Step 8 — Last-unit or benefit-reservation contention**

Accept the entire bundle only if exact-variant quantities and approved benefit resources remain available. Otherwise reject safely without a partial accepted bundle or cross-variant substitution.

**Step 8 — Duplicate submission, changed input or uncertain/save outcome**

An identical retry returns the existing order, attempt, link and deadlines; changed-input reuse is rejected. Uncertain results retain established facts for recovery without another order/attempt or new deadlines. A new checkout requires full revalidation.

#### Business Rules

BR-45-01, BR-45-02, BR-45-03, BR-45-04, BR-45-05, BR-45-06, BR-45-07, BR-45-08

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-45-01 | The active owning Member selects an active processing branch before checkout; the accepted order freezes that branch for all later fulfilment choices. |
| BR-45-02 | Checkout covers the whole cart; invalid/unavailable lines are removed and reported without a partial order/hold or variant substitution. |
| BR-45-03 | Freeze product/variant/options, quantity, prices/currency, branch, policy versions, discounts and positive whole-VND totals; later changes never rewrite accepted terms. |
| BR-45-04 | Allow no-benefit checkout; requested benefits require complete approved policies. Apply at most one voucher, then points on the remainder, without rounding or silent benefit removal. |
| BR-45-05 | Reserve exact-variant quantities and selected benefits together or none; unpaid holds neither deduct sold quantity nor debit points or consume voucher quota. |
| BR-45-06 | Accepted checkout creates one order, hold bundle and payment attempt; link expires 10 minutes and hold 15 minutes afterward, without retry extensions. |
| BR-45-07 | Require review/resubmission after corrected cart/quote; reject conflicting edits or changed-input retry reuse, and preserve later cart changes during paid cleanup. |
| BR-45-08 | Record each attempt and its actual outcome under the shared audit policy. |
