### 6.2 Apply Promotions and Points

| Use Case ID | UC-44 | Use Case Name | Apply Promotions and Points |
| --- | --- | --- | --- |
| Created By |  | Created Date |  |
| Primary Actors | Member, Guest | Secondary Actors | Order Management and Fulfillment, Workshop Management |

#### Description

As a Member or Guest, I want the best automatic discount, an optional voucher and my loyalty points applied to my order or booking so that I see the exact discount before confirming.

#### Trigger

A Member or Guest proceeds to retail checkout or direct website workshop booking.

#### Preconditions

1. The Member is signed in and active, or a Guest is booking a workshop directly on the website.
2. The actor is at retail checkout or direct website workshop booking.
3. A confirmed loyalty point policy exists.

#### Postconditions

1. Each order or booking records the best automatic discount, at most one voucher, and the points used separately.
2. Voucher use and points are held on acceptance and finalized once after verified payment or valid 0 VND acceptance.
3. Points are earned, and held or used benefits are released or refunded, automatically from the confirmed order or booking outcome.
4. The accepted transaction keeps its recorded point policy and discount even if the policy changes later.
5. The retail preparation branch does not change the recorded discount.

#### Normal Flow

**Apply Promotion to Retail Order**

1. System calculates eligible line amounts excluding shipping.
2. System applies the largest applicable automatic discount proportionally across eligible lines.
3. Member selects a suggested, saved, or entered voucher code.
4. System validates the voucher's scope, validity, status, minimum amount, limits, and save state.
5. System calculates the voucher discount on eligible lines after the automatic discount.
6. Member optionally redeems points and confirms the order.
7. System rechecks all conditions and holds one voucher use on order acceptance.
8. System finalizes the voucher use once after verified full payment.

**Apply Promotion to Workshop Booking**

1. System selects the largest applicable workshop automatic discount for the booking's packages.
2. Member selects a suggested, saved, or entered workshop voucher.
3. System validates each ticket against the voucher's scope, booking period, minimum fee, and limits.
4. System subtracts automatic discount, voucher discount, and allowed points to compute the fee after discounts.
5. System returns the fee after discounts and per-ticket discount shares for deposit calculation.
6. Member or Guest confirms the booking, and System holds any voucher use and records the policy.
7. System finalizes the discount and voucher use once after verified deposit payment.

**Redeem Loyalty Points**

1. System displays available points and the maximum allowed points for the order or booking.
2. Member enters a whole number of points within the allowed range.
3. System calculates the point discount and displays item amount, discounts, shipping, and total.
4. Member confirms the order or booking.
5. System rechecks price, conditions, available points, and limits, then holds the points on acceptance.
6. System deducts held points once after payment verification and records a used entry.

#### Alternative Flows

R1a. Line in scope cannot be bought → System excludes it while keeping other eligible lines.

R3a. Member chooses no voucher → System keeps the automatic discount only.

R8a. Valid 0 VND order is accepted → System finalizes the voucher use once on acceptance.

R8b. Checkout splits into branch orders → System splits discounts on acceptance and finalizes on first discounted branch payment.

W4a. Fee after discounts is 0 VND → System confirms without payment gateway and finalizes benefits.

L2a. Member chooses not to use points → System holds and deducts no points.

L6a. Transaction is a valid 0 VND order or booking → System deducts held points once on acceptance.

L6b. Checkout splits into branch orders → System divides points on acceptance and deducts each share on branch payment.

L6c. Booking has multiple workshop tickets → System divides points across tickets on acceptance and deducts once on deposit verification.

#### Exception Flows

A1a. Actor uses another Member's transaction reference → System rejects the request (MSG01).

R4a. Voucher is invalid, expired, ineligible, or unsaved → System explains why and keeps the automatic discount (MSG48).

R7a. Price, limits, or points changed before acceptance → System recalculates and asks for reconfirmation (MSG51).

R8c. Payment is not verified in time → System releases the held voucher use and points (BR-44-16).

W2a. Guest requests a voucher or points → System rejects them and allows booking with the automatic discount.

W3a. Voucher is invalid, expired, ineligible, or unsaved → System explains why and keeps the automatic discount (MSG48).

W4b. Fee after discounts is negative → System rejects the booking, holds nothing, and allows adjustment (MSG52).

W6a. Price, limits, or points changed before acceptance → System recalculates and asks for reconfirmation (MSG51).

W7a. Payment is not verified in time → System releases the held voucher use and points (BR-44-16).

L1a. Selected voucher disallows points → System disables point use and explains why (MSG49).

L2b. Entered points are below minimum or above maximum → System rejects the input and shows the allowed range (MSG50).

L5a. Another transaction holds the same points → System rejects the hold and returns the updated limit for reconfirmation.

L6d. Browser reports payment success without verified result → System does not deduct points.

#### Business Rules

BR-44-01, BR-44-02, BR-44-03, BR-44-04, BR-44-05, BR-44-06, BR-44-07, BR-44-08, BR-44-09, BR-44-10, BR-44-11, BR-44-12, BR-44-13, BR-44-14, BR-44-15, BR-44-16, BR-44-17, BR-44-18, BR-44-19, BR-44-20, BR-44-21

#### Non-Functional Requirements

—

#### Priority

—

#### Messages

MSG01, MSG48, MSG49, MSG50, MSG51, MSG52

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-44-01 | Points are a non-cash discount chosen by the Member; the system never deducts points automatically. |
| BR-44-02 | Points used are whole numbers from the minimum per use up to `min(available points, floor(cap × amount after program discount / point value))`. |
| BR-44-03 | Points are calculated on the amount after both the automatic discount and the voucher. They can be combined with an automatic discount, and with a voucher only when that voucher allows points; an order or booking with a voucher that does not allow points cannot use points. |
| BR-44-04 | Points are held when the order or booking is accepted and deducted once after verified payment of the retail order (or of each branch order, for its share) or the workshop deposit, or on acceptance of a valid 0 VND order or booking; the remaining workshop payment does not deduct points again. |
| BR-44-05 | Points earned by a transaction cannot discount that same transaction. |
| BR-44-06 | A retail order or a workshop booking uses the best applicable automatic discount, if any, plus at most one voucher chosen by the Member. When several automatic discounts apply, the largest discount is used, and the earliest created one wins a tie; the automatic discount cannot be declined. A voucher that must be saved can be used only by a Member who saved it. |
| BR-44-07 | The automatic discount is calculated first on the purchasable lines in its scope and divided between them; the voucher is then calculated on its purchasable lines in scope (and, for a branch-limited voucher, only lines whose cart branch is in scope) after their share of the automatic discount, excluding shipping; points come last. A fixed discount applies once per order and a percentage is rounded down, never above the cap or the eligible amount. A voucher that adds no discount after the automatic discount is refused. |
| BR-44-08 | The workshop scope is checked per ticket: the ticket's package must be in scope and, for a voucher, every session of the ticket must match the voucher's branch, date and session scope. Only tickets in scope are discounted; the voucher's minimum fee and discount use their total price after the automatic discount, and the cap applies once per booking. |
| BR-44-09 | A workshop fee after discounts of 0 VND is valid and only a negative fee is rejected. This feature returns each ticket's share of the automatic discount and of the voucher discount; Workshop Management calculates the amount collected at booking and the remaining amount, and this feature does not calculate or store the deposit. |
| BR-44-10 | The voucher use is held on acceptance and finalized once after verified payment, or on acceptance of a valid 0 VND order or booking; for a checkout split into branch orders, when the first branch order with a share of the discount is paid. Browser messages alone never finalize it. |
| BR-44-11 | A workshop automatic discount applies to every direct website booking, including a Guest's; a Guest's discount is recorded without a Member, with no points or voucher and no points earned. Workshop vouchers and points are for Members only. |
| BR-44-12 | Earning points is an automatic result of the order or booking, not a Member action. Points are earned only for Member transactions: a retail order after verified full payment, or a workshop booking that is both completed and fully paid; a browser success message alone earns nothing. |
| BR-44-13 | The earning base is the amount actually paid after discounts and points, excluding shipping, divided by the recorded earning rate; points are rounded down per transaction and a workshop earns once on the fee actually paid. |
| BR-44-14 | Each eligible transaction has exactly one earning result, calculated with the policy recorded at acceptance; retries never add points twice, and a missing recorded policy is never replaced by the current one. |
| BR-44-15 | For a multi-ticket booking or a checkout split into branch orders, points are rounded down once for the whole group and divided by the largest remainder method; each ticket or branch order earns only its own share, once, and a ticket or branch order that does not complete earns nothing. |
| BR-44-16 | Releasing and refunding benefits are automatic results of the confirmed order or booking outcome. Expiry, or cancellation of a retail order before payment, without accepted payment releases held points and voucher uses exactly once, without recording them as used. A released or refunded use of a voucher that must be saved returns to the Member's saved voucher. |
| BR-44-17 | A confirmed valid workshop cancellation before completion, or a cancellation by Mland, refunds exactly the points deducted, linked to the original deduction, and, only if the voucher has not expired when the cancellation is decided, one voucher use to the Member and the program; held benefits are always released. It does not convert points to money or extend or reactivate vouchers. |
| BR-44-18 | Release and refund results are tracked per benefit so that retries complete only the missing part and never return a benefit twice. |
| BR-44-19 | No-shows, unconfirmed or invalid cancellations, completed workshops and returned retail orders return no benefits; the outcome is decided only by the confirmed result from the owning feature. |
| BR-44-20 | For a split retail checkout or a multi-ticket booking, points are released or refunded per branch order or per ticket. The voucher use returns once: for a checkout, when every branch order with a share of the discount ends unpaid; for a booking, when any ticket is cancelled by Mland or every ticket ends in a valid or Mland cancellation, under the validity condition of BR-44-17. |
| BR-44-21 | Voucher suggestions at checkout and booking never list a voucher that must be saved but has not been saved by the Member; a code typed by hand is matched after trimming spaces and ignoring case. |
