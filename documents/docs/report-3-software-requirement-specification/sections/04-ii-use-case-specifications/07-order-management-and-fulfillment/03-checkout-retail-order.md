### 7.2 Checkout and Pay for Retail Order

| Use Case ID | UC-54 | Use Case Name | Checkout and Pay for Retail Order |
| --- | --- | --- | --- |
| Created By |  | Created Date |  |
| Primary Actors | Member | Secondary Actors | VNPay Payment Gateway |

#### Description

As a Member, I want my ticked cart lines revalidated, quoted, and paid in full through VNPay so that one order per branch holds the agreed terms and verified payment completes exactly one sale.

#### Trigger

A Member selects Mua hàng with at least one cart line ticked, or Thanh toán on an order in Chờ thanh toán.

#### Preconditions

1. The Member is signed in, verified, and active.
2. The cart has at least one ticked line, and each line belongs to an active processing branch.
3. Approved catalogue, quantity, price, deadline, and VNPay contracts are available.
4. A requested voucher or points benefit needs a complete approved policy. A missing policy does not block an order without benefits.

#### Postconditions

1. One grouped checkout holds one order per branch, with line, price, discount, and payable snapshots frozen.
2. Verified payment accepted before the 10-minute hold ends marks the order paid and consumes held quantities and benefits once.
3. Without timely verified payment, held quantities, voucher, and points are released once; late success goes to Manager reconciliation.
4. A rejected checkout leaves no partial order, hold, or benefit. Invalid lines are removed and reported.
5. Duplicate payment evidence repeats no effect and never undoes an accepted sale.

#### Normal Flow

1. Member opens checkout from the ticked cart lines.
2. System verifies the Member's authority and groups the lines by processing branch.
3. Member chooses, for each branch, store pickup or GHTK delivery with an address, VNPay payment, and an optional voucher and points.
4. System revalidates each line: public eligibility, variant, approved price, and available quantity.
5. System calculates the subtotal, best automatic discount, at most one voucher, then points, plus the GHTK fee, in whole VND.
6. System shows the lines, discounts, and payable total.
7. Member presses Đặt hàng.
8. System rechecks everything, then accepts one order per branch and all holds together (MSG79).
9. System records safe evidence and opens the stored VNPay link, valid until the 10-minute hold ends.
10. Member completes payment on VNPay and returns to Purchase Order History.
11. VNPay sends signed IPN evidence to the System.
12. System verifies signature, reference, frozen amount, currency, status, and active hold bindings.
13. System accepts payment before the hold ends and settles held quantities and benefits once.
14. System removes unchanged purchased cart lines and shows the paid order for Staff preparation (MSG81).

#### Alternative Flows

1a. Member selects Mua Ngay → System opens checkout with that one line and the best eligible voucher preselected.

1b. Member presses Thanh toán on an order in Chờ thanh toán → System reopens the same stored VNPay link without a second transaction or new deadlines; resume at step 10.

3a. Member chooses store pickup and cash at the counter → System holds for 72 hours and, instead of step 9, opens Purchase Order History at Chờ nhận tại cửa hàng with where and until when to pay.

3b. Member chooses no benefit → System quotes without voucher or points.

8a. Total is 0 VND after discounts → System accepts the order as paid, without VNPay.

8b. Member resubmits an identical request → System returns the existing order, attempt, link, and deadlines.

10a. Member leaves VNPay without paying → System opens Chờ thanh toán with a countdown; Member may press Thanh toán or Hủy đơn, which releases held items back to the cart.

12a. Catalogue price changes or the item is unpublished after a valid hold → System uses the original frozen quote and deadlines without repricing.

#### Exception Flows

2a. Member is unauthorized, the cart is empty, or the branch is missing or inactive → System rejects before any order or hold and reveals no other Member's data (MSG01).

2b. Chosen lines are no longer in the cart → System stops and asks the Member to reselect (MSG78).

3c. GHTK is chosen without an address, or GHTK does not deliver there → System asks for another address or store pickup (MSG77).

3d. Voucher cannot be used → System lists it as not eligible with its reason (MSG48). A voucher that excludes points shows 0 points (MSG49).

4a. A line is invalid or unavailable → System removes and reports it, creates no partial order or hold, and the Member resubmits from step 1.

5a. Requested benefit is unavailable, incomplete, or unapproved → System rejects the quote and shows the reason; Cập nhật lại drops the benefit and shows the new total (MSG51).

5b. Total is negative or not whole VND → System rejects the quote and asks the Member to correct the discount or selection. No rounding.

6a. Price or quote changed → Member must review the new quote and resubmit (MSG51).

8c. Last unit or benefit is contested → System rejects the whole bundle, with no partial order or variant substitution.

8d. Retry changes the input → System rejects the retry.

8e. Save outcome is uncertain → System keeps the established facts for recovery and creates no new order, attempt, or deadline (MSG04).

9a. VNPay page fails to open or the provider response is uncertain → System keeps the order and original hold and lets the Member press Thanh toán again (MSG80).

10b. Browser return claims success without verified evidence → System shows informational status only and does not mark the order paid.

11a. No valid IPN when the 10-minute hold ends → System checks an opened payment with signed QueryDR about every 5 minutes for 60 minutes (MSG82).

12b. Invalid signature, reference, amount, currency, status, or unknown payment → System rejects settlement, keeps the actual outcome, and allows no manual paid override.

12c. Duplicate, reordered, or concurrent IPN or QueryDR evidence → System keeps the accepted sale and records a no-change result.

13a. First acceptance comes at or after the hold ends → System queues a payment exception for Manager reconciliation without reviving the hold or refunding.

13b. No timely verified success → System expires the unpaid order and releases held quantities, voucher, and points once.

14a. Settlement, read, or optional email failure → System preserves established evidence and completed sales and keeps later cart edits.

#### Business Rules

BR-54-01, BR-54-02, BR-54-03, BR-54-04, BR-54-05, BR-54-06, BR-54-07, BR-54-08, BR-54-09, BR-54-10, BR-54-11, BR-54-12, BR-54-13, BR-54-14

#### Non-Functional Requirements

—

#### Priority

—

#### Messages

MSG01, MSG04, MSG48, MSG49, MSG51, MSG77, MSG78, MSG79, MSG80, MSG81, MSG82

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-54-01 | Each ticked line belongs to an active processing branch; the accepted order of each branch freezes that branch and its pickup or GHTK choice. |
| BR-54-02 | Checkout covers the ticked lines; invalid or unavailable lines are removed and reported without a partial order, hold, or variant substitution. |
| BR-54-03 | Freeze product, variant, options, quantity, prices, branch, policy versions, discounts, and non-negative whole-VND totals; later changes never rewrite accepted terms. |
| BR-54-04 | Checkout without benefits is allowed; requested benefits need complete approved policies. The best automatic discount always applies; at most one voucher stacks on what it left, then points on the remainder, with no rounding or silent benefit removal. A voucher that adds nothing, is not saved when it must be, or does not cover the lines or branch is refused with its reason. |
| BR-54-05 | Reserve exact-variant quantities and chosen benefits together or none; unpaid holds neither deduct sold quantity nor debit points or consume voucher quota. |
| BR-54-06 | Accepted checkout creates one order per branch and one hold bundle; a VNPay hold lasts 10 minutes and a cash-at-the-counter hold 72 hours, with no extension on retry. |
| BR-54-07 | A corrected cart or changed quote needs review and resubmission; conflicting edits and changed-input retries are rejected; later cart changes are preserved during paid cleanup. |
| BR-54-08 | Only a matching valid signed successful VNPay IPN or eligible signed QueryDR confirms payment; browser returns and human assertions never do. |
| BR-54-09 | Verify the frozen full positive whole-VND amount, currency, reference, and original variant and benefit bindings; never reprice or reassign accepted terms. |
| BR-54-10 | Use the single stored VNPay link of the checkout, valid until the original 10-minute hold ends; reopening it creates no second transaction. |
| BR-54-11 | Accept payment strictly before the original hold ends; first acceptance at or after that time is an exception regardless of earlier provider timestamps. |
| BR-54-12 | Payment consumes held quantities and benefits once; duplicates, reordering, recovery, and expiry races cannot repeat effects or both settle and release the same resources. |
| BR-54-13 | Release unpaid holds at expiry; late evidence cannot revive holds or fulfil automatically. Unknown outcomes keep the original hold; verified failure releases it. |
| BR-54-14 | Record each attempt and its actual outcome under the shared audit policy. |
