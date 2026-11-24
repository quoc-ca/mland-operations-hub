### 7.3 Checkout Retail Order

#### Primary Actors

Member.

#### Secondary Actors

VNPay Payment Gateway.

#### Description

As a Member, I want the cart lines I ticked revalidated and quoted at approved prices so that one complete order per branch holds the exact variants and agreed purchase terms without a partial sale.

#### Preconditions

1. The platform is available.
2. The actor is authenticated with verified identity, has an active account, and holds current authority for the requested action.
3. The Member owns a nonempty cart, has ticked at least one line, and each line belongs to an active processing branch.
4. Approved catalogue/quantity and initiation/deadline contracts are available. Requested benefits require complete approved applicable policies; missing benefit policies do not block an otherwise-valid no-benefit order. Commercial benefit values require complete policy-owner approval.

#### Postconditions

- Successful checkout retains one grouped checkout with one complete order per branch, frozen processing branch/line/fulfilment/discount/payable snapshots and the hold of the ticked lines with original deadline evidence.
- Rejected checkout creates no partial accepted order/hold/benefit bundle; invalid/unavailable lines are removed and reported.
- Checkout does not itself assert paid, deduct sold quantities or authorize preparation; UC46 requires verified payment.

#### Normal Flow

**Checkout Retail Order**

1. The Member opens checkout from the ticked lines of the own cart, or from Mua Ngay for one line.
2. The system verifies current account/ownership authority and retrieves the selected lines grouped by processing branch.
3. For each branch the Member chooses store pickup or GHTK delivery with a recipient address, chooses VNPay or, for store pickup, cash at the counter, and chooses no benefits or available approved voucher/points benefits.
4. The system revalidates every line for current public eligibility, exact configured variant, applied approved price and logically available quantity.
5. The system validates selected branch/benefit eligibility and calculates approved variant subtotal, then the best automatic product discount, then at most one voucher on what the automatic discount left of its covered lines, then approved points on the remainder, plus the GHTK delivery fee quoted by GHTK; the automatic and voucher discounts are shown as separate lines (product discount, voucher discount, points discount, savings) and each line's share goes to its branch order and each step must yield whole VND without invented rounding.
6. The system displays current line/options/quantity/prices, branch, discounts and positive payable total for acceptance; changed terms require explicit Member review and resubmission.
7. The Member submits acceptance of the complete quote by pressing Đặt hàng.
8. The system rechecks authority, limits, latest eligibility/quantity and approved terms, then accepts one grouped checkout with one order per branch and the quantity/benefit holds together or none. It freezes policy versions/results and the checkout acceptance time; a VNPay hold lasts 10 minutes and a cash-at-the-counter hold 72 hours.
9. The system records safe evidence. For VNPay it opens the VNPay payment page straight away (UC46); for cash at the counter it opens Purchase Order History at Chờ nhận tại cửa hàng with where and until when to pay.

#### Alternative Flows

**Steps 2 and 8 — Unauthorized, empty or missing/inactive branch**

Reject before a new order/hold; reveal no other Member's cart/order. Require an active selected branch and safe correction. An inactive branch cannot receive new checkout, but accepted orders retain their branch and original deadlines. Currently authorized assigned Staff/delegated Manager may complete paid orders; physical handover problems require operational follow-up without automatic transfer, cancellation or refund.

**Steps 4 and 8 — Any invalid/unavailable line, or changed price/quote**

Remove/report invalid or unavailable lines and create no partial order/hold. Existing accepted holds remain protected separately. require review and a fresh explicit submission for the corrected cart/changed quote before ordering survivors; resume at Step 1.

**Step 5 — Requested benefit unavailable, incomplete or unapproved**

Reject the quote and require explicit Member reselection at Step 3. When the voucher can no longer be used (for example its member limit is used up or held) or the points changed, show the specific reason above Đặt hàng with Cập nhật lại, which drops the refused benefit and shows the new total for review. Do not silently remove a benefit, invent rates/caps or change to a full-price order. A newly selected otherwise-valid no-benefit quote may proceed.

**Step 3 — A voucher cannot be used for this order**

The voucher picker lists the voucher under Chưa đủ điều kiện cho đơn này with its reason instead of one general message: its Kho Voucher state (not started yet, paused, expired, no product covered any more, no uses left, or the Member's uses used up); the amount still to spend to reach its minimum; or the reason returned by Loyalty and Promotion Management (not saved in Kho Voucher, the automatic discount already covers its lines, no item or size covered, another branch, a workshop voucher, or code not found). When the chosen voucher cannot be combined with points, the points line shows 0 points and "Voucher {code} không dùng chung với điểm. Bỏ voucher để dùng điểm."

**Steps 5 and 8 — Zero/negative or invalid whole-VND total**

A zero total after discounts is accepted as a paid order without VNPay. Reject a negative or non-whole-VND total and require discount/selection correction before creating a new order/hold. Do not floor/round, remove benefits silently or create a zero-payment VNPay attempt.

**Step 8 — Last-unit or benefit-reservation contention**

Accept the entire bundle only if exact-variant quantities and approved benefit resources remain available. Otherwise reject safely without a partial accepted bundle or cross-variant substitution.

**Step 8 — Duplicate submission, changed input or uncertain/save outcome**

An identical retry returns the existing order, attempt, link and deadlines; changed-input reuse is rejected. Uncertain results retain established facts for recovery without another order/attempt or new deadlines. A new checkout requires full revalidation.

#### Business Rules

BR-45-01, BR-45-02, BR-45-03, BR-45-04, BR-45-05, BR-45-06, BR-45-07, BR-45-08

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-45-01 | Each ticked line belongs to an active processing branch; the accepted order of each branch freezes that branch and its pickup or GHTK choice. |
| BR-45-02 | Checkout covers the ticked lines; invalid/unavailable lines are removed and reported without a partial order/hold or variant substitution. |
| BR-45-03 | Freeze product/variant/options, quantity, prices/currency, branch, policy versions, discounts and positive whole-VND totals; later changes never rewrite accepted terms. |
| BR-45-04 | Allow no-benefit checkout; requested benefits require complete approved policies. The best automatic product discount always applies; at most one voucher stacks on what it left, then points on the remainder, without rounding or silent benefit removal. A voucher that gives nothing more, is not saved when it must be, or does not cover the lines or branch is refused with its reason. |
| BR-45-05 | Reserve exact-variant quantities and selected benefits together or none; unpaid holds neither deduct sold quantity nor debit points or consume voucher quota. |
| BR-45-06 | Accepted checkout creates one order per branch and one hold bundle; a VNPay hold lasts 10 minutes and a cash-at-the-counter hold 72 hours, without retry extensions. |
| BR-45-07 | Require review/resubmission after corrected cart/quote; reject conflicting edits or changed-input retry reuse, and preserve later cart changes during paid cleanup. |
| BR-45-08 | Record each attempt and its actual outcome under the shared audit policy. |
