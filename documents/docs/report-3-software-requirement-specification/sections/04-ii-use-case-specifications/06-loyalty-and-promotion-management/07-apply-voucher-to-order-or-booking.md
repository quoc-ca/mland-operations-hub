### 6.7 Apply Voucher to Order or Booking

| Use Case ID | UC-48 | Use Case Name | Apply Voucher to Order or Booking |
| --- | --- | --- | --- |
| Created By |  | Created Date |  |
| Primary Actors | Member, Guest | Secondary Actors | Order Management and Fulfillment, Workshop Management |

#### Description

As a Member or Guest, I want the best automatic discount and an optional Member voucher applied to my order or booking so that I see the exact discount before confirming.

#### Trigger

A Member or Guest proceeds to retail checkout or direct website workshop booking.

#### Preconditions

1. The Member is signed in and active, or a Guest is booking a workshop directly on the website.
2. The actor is at retail checkout or direct website workshop booking.

#### Postconditions

1. Each order or booking records the best automatic discount and at most one voucher separately.
2. The voucher use is finalized once after verified payment or valid 0 VND acceptance, or released on failure.
3. The retail preparation branch does not change the recorded discount.

#### Normal Flow

**Apply Promotion to Retail Order**

1. System calculates eligible line amounts excluding shipping.
2. System applies the largest applicable automatic discount proportionally across eligible lines.
3. Member selects a suggested, saved, or entered voucher code.
4. System validates the voucher's scope, validity, status, minimum amount, limits, and save state.
5. System calculates the voucher discount on eligible lines after the automatic discount.
6. Member optionally applies points under UC-44 and confirms the order.
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

#### Alternative Flows

Retail 1a. Line in scope cannot be bought → System excludes it while keeping other eligible lines.

Retail 3a. Member chooses no voucher → System keeps the automatic discount only.

Retail 8a. Valid 0 VND order is accepted → System finalizes the voucher use once on acceptance.

Retail 8b. Checkout splits into branch orders → System splits discounts on acceptance and finalizes on first discounted branch payment.

Workshop 4a. Fee after discounts is 0 VND → System confirms without payment gateway and finalizes benefits.

#### Exception Flows

1a. Actor uses another Member's transaction reference → System rejects the request (MSG01).

Retail 4a. Voucher is invalid, expired, ineligible, or unsaved → System explains why and keeps the automatic discount (MSG48).

Retail 7a. Price, limits, or points changed before acceptance → System recalculates and asks for reconfirmation (MSG51).

Retail 8a. Payment is not verified in time → System releases the held use under UC-50.

Workshop 2a. Guest requests a voucher or points → System rejects them and allows booking with the automatic discount.

Workshop 3a. Voucher is invalid, expired, ineligible, or unsaved → System explains why and keeps the automatic discount (MSG48).

Workshop 4a. Fee after discounts is negative → System rejects the booking, holds nothing, and allows adjustment (MSG52).

Workshop 6a. Price, limits, or points changed before acceptance → System recalculates and asks for reconfirmation (MSG51).

Workshop 7a. Payment is not verified in time → System releases the held use under UC-50.

#### Business Rules

BR-48-01, BR-48-02, BR-48-03, BR-48-04, BR-48-05, BR-48-06

#### Non-Functional Requirements

—

#### Priority

—

#### Messages

MSG01, MSG48, MSG51, MSG52

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-48-01 | A retail order or a workshop booking uses the best applicable automatic discount, if any, plus at most one voucher chosen by the Member. When several automatic discounts apply, the largest discount is used, and the earliest created one wins a tie; the automatic discount cannot be declined. A voucher that must be saved can be used only by a Member who saved it. |
| BR-48-02 | The automatic discount is calculated first on the purchasable lines in its scope and divided between them; the voucher is then calculated on its purchasable lines in scope (and, for a branch-limited voucher, only lines whose cart branch is in scope) after their share of the automatic discount, excluding shipping; points come last. A fixed discount applies once per order and a percentage is rounded down, never above the cap or the eligible amount. A voucher that adds no discount after the automatic discount is refused. |
| BR-48-03 | The workshop scope is checked per ticket: the ticket's package must be in scope and, for a voucher, every session of the ticket must match the voucher's branch, date and session scope. Only tickets in scope are discounted; the voucher's minimum fee and discount use their total price after the automatic discount, and the cap applies once per booking. |
| BR-48-04 | A workshop fee after discounts of 0 VND is valid and only a negative fee is rejected. This feature returns each ticket's share of the automatic discount and of the voucher discount; Workshop Management calculates the amount collected at booking and the remaining amount, and this feature does not calculate or store the deposit. |
| BR-48-05 | The use is held on acceptance and finalized once after verified payment, or on acceptance of a valid 0 VND order or booking; for a checkout split into branch orders, when the first branch order with a share of the discount is paid. Browser messages alone never finalize it. |
| BR-48-06 | A workshop automatic discount applies to every direct website booking, including a Guest's; a Guest's discount is recorded without a Member, with no points or voucher and no points earned. Workshop vouchers and points are for Members only. |
