### 6.7 Apply Voucher to Order or Booking

#### Primary Actors

Member; Guest (workshop automatic discount only).

#### Secondary Actors

Order Management and Fulfillment (retail checkout and payment); Workshop Management (booking and deposit payment).

#### Description

This use case applies the best automatic discount, if any, to a retail order or a direct website workshop booking and allows a Member to add at most one voucher on top of it, calculated on the amount after the automatic discount, and see the exact discount before confirming. A Guest booking a workshop directly on the website receives the workshop automatic discount but cannot use vouchers or points. The discount is held on acceptance and finalized once after verified payment, or when a valid 0 VND order or booking is accepted.

This use case includes the following sub-flows:

- Apply Promotion to Retail Order
- Apply Promotion to Workshop Booking

#### Preconditions

1. The Member is signed in and the account is active, or, for a workshop automatic discount, the actor is a Guest booking directly on the website.
2. The actor is at retail checkout, or is booking a workshop directly on the website.

#### Normal Flow

**Apply Promotion to Retail Order**

1. The system calculates the eligible amount from the cart lines in the program's scope that can be bought, before any discount or points and excluding shipping. For a voucher limited to branches, a line is eligible only when the branch recorded on the cart line is in the voucher's branch scope; the branch that later prepares the order does not count.
2. If several automatic discounts apply, the system selects the one with the largest discount; on a tie, the earliest created one. The automatic discount is always applied and is divided between the lines in its scope in proportion to their amounts, with the remaining VND given by the largest remainder.
3. The Member may add one voucher: a public voucher suggested at checkout, a voucher from My vouchers, or a code entered directly. The code is trimmed and matched ignoring case. A voucher that must be saved is listed and usable only after the Member has saved it. Choosing no voucher keeps the automatic discount.
4. The system checks the voucher's scope, time, status, minimum amount and limits, and, for a voucher that must be saved, that the Member has saved it.
5. The system calculates the voucher discount on the amount of the lines in the voucher's scope after their share of the automatic discount: a fixed amount once per order, or a percentage rounded down to the VND, never above the maximum discount or that amount. The minimum amount is checked against the same amount. The voucher discount is divided between those lines in the same way. For example, with lines A of 600,000 VND and B of 400,000 VND in a 10% automatic discount, the automatic discount is 100,000 VND (A 60,000, B 40,000); a 20% voucher with a 100,000 VND cap and a 300,000 VND minimum that covers only B is calculated on 360,000 VND and gives 72,000 VND, for a total program discount of 172,000 VND. With a 10% voucher limited to branch A, a cart with one 1,000,000 VND line at branch A and one at branch B gets 100,000 VND off from the voucher, for the line at branch A only.
6. The Member may add points under UC-44 Redeem Loyalty Points, then confirms the order.
7. The system rechecks all conditions and holds one voucher use together with the accepted order.
8. When the full payment is verified, or a valid 0 VND order is accepted, the system finalizes the use once. When Order Management splits the checkout into branch orders that are paid separately, the discount is divided between the branch orders on acceptance; the voucher use is finalized when the first branch order that has a share of the discount is paid.

**Apply Promotion to Workshop Booking**

1. If several workshop automatic discounts cover the packages of the booking, the system selects the one with the largest discount; on a tie, the earliest created one. A workshop automatic discount is matched by package only and applies to every customer booking directly on the website, including a Guest.
2. A Member may add one workshop voucher: a suggested public voucher, a voucher from My vouchers, or a code entered directly. The automatic discount is kept. A voucher that must be saved is usable only after the Member has saved it.
3. For a voucher, the system checks each ticket in the booking: the ticket is in scope when its package is in the voucher's package scope (if any) and every session of the ticket matches the voucher's branch, participation date and session scope (if any). It also checks the booking period, the minimum package fee and the limits. For an automatic discount, a ticket is in scope when its package is in the discount's package scope.
4. The system subtracts the automatic discount from the tickets in its scope, then calculates the voucher on the price of the tickets in the voucher's scope after their share of the automatic discount, then subtracts points if allowed, to get the fee after discounts. Tickets outside a program's scope are not discounted by it. The minimum package fee is checked against the voucher's tickets after the automatic discount, and the maximum discount applies once per booking, not per participant.
5. The system returns the fee after discounts, which tickets are in scope, and each ticket's share of the automatic discount and of the voucher discount separately. Workshop Management calculates the amount collected at booking and the remaining amount, and shows the original fee, each discount, the fee after discounts, the deposit and the remaining amount. For example, a 1,000,000 VND fee with a 100,000 VND voucher and 200 points gives a fee after discounts of 880,000 VND. The amount is always calculated by this quote, not taken from the price displayed on the package page.
6. The actor confirms the booking; the system holds the voucher use, if any, and records the discount and the policy in effect. For a Guest, the system records the automatic discount without a Member, with no points and no voucher.
7. When the deposit is verified, or Workshop Management confirms a 0 VND booking without the payment gateway, the system finalizes the discount and the voucher use once. The remaining payment does not apply the promotion again.

#### Alternative Flows

**Retail Step 4 / Workshop Step 3 — Voucher cannot be used**

The system explains why (MSG48): the code does not exist, it is a workshop code used for products or the reverse, it is expired, paused or fully used, the Member's limit is reached, the minimum amount is not met after the automatic discount, no cart line is at a branch in the voucher's branch scope, no ticket in the booking is in the voucher's package, branch, date or session scope, the voucher must be saved and the Member has not saved it (with guidance to save it in the Voucher hub), or the automatic discount already covers the voucher's lines or tickets so the voucher gives no further discount. The Member can choose another voucher or none; the automatic discount still applies.

**Retail Step 1 — A line in scope cannot be bought**

A hidden, stopped or deleted product in scope is excluded from the eligible amount; other lines in scope can still use the program.

**Retail Step 7 / Workshop Step 6 — Conditions changed before acceptance**

If the price, limits or points changed, the system recalculates and asks the actor to confirm the new total, fee or deposit (MSG51).

**Workshop Step 4 — Fee after discounts is 0 VND**

The booking is accepted. Workshop Management confirms it without the payment gateway, and the system finalizes the benefits as for a verified deposit.

**Workshop Step 4 — Fee after discounts is negative because of invalid data**

The system rejects the booking, holds nothing and lets the actor adjust (MSG52). A positive amount below the payment gateway minimum is handled by Workshop Management.

**Workshop Step 2 — Guest requests a voucher or points**

Workshop vouchers and points are for Members only. A Guest booking that requests a voucher or points is rejected as ineligible; the Guest can still book with the automatic discount.

**Workshop Step 1 — Booking is not made directly on the website**

Workshop vouchers and automatic discounts are not extended to external booking channels.

**Retail Step 8 / Workshop Step 7 — Payment not verified in time**

The held use is released under UC-50.

**Any step — Using another person's transaction reference**

Knowing another Member's transaction reference does not allow use of their points or holds (MSG01).

#### Postconditions

- Each order or booking uses the best automatic discount, if any, plus at most one voucher; the two discounts are recorded separately and do not change if the programs change later.
- The voucher use is finalized exactly once after verified payment or acceptance of a valid 0 VND transaction, or released if payment does not complete.
- The branch preparing a retail order does not change the discount.

#### Business Rules

BR-48-01, BR-48-02, BR-48-03, BR-48-04, BR-48-05, BR-48-06

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-48-01 | A retail order or a workshop booking uses the best applicable automatic discount, if any, plus at most one voucher chosen by the Member. When several automatic discounts apply, the largest discount is used, and the earliest created one wins a tie; the automatic discount cannot be declined. A voucher that must be saved can be used only by a Member who saved it. |
| BR-48-02 | The automatic discount is calculated first on the purchasable lines in its scope and divided between them; the voucher is then calculated on its purchasable lines in scope (and, for a branch-limited voucher, only lines whose cart branch is in scope) after their share of the automatic discount, excluding shipping; points come last. A fixed discount applies once per order and a percentage is rounded down, never above the cap or the eligible amount. A voucher that adds no discount after the automatic discount is refused. |
| BR-48-03 | The workshop scope is checked per ticket: the ticket's package must be in scope and, for a voucher, every session of the ticket must match the voucher's branch, date and session scope. Only tickets in scope are discounted; the voucher's minimum fee and discount use their total price after the automatic discount, and the cap applies once per booking. |
| BR-48-04 | A workshop fee after discounts of 0 VND is valid and only a negative fee is rejected. This feature returns each ticket's share of the automatic discount and of the voucher discount; Workshop Management calculates the amount collected at booking and the remaining amount, and this feature does not calculate or store the deposit. |
| BR-48-05 | The use is held on acceptance and finalized once after verified payment, or on acceptance of a valid 0 VND order or booking; for a checkout split into branch orders, when the first branch order with a share of the discount is paid. Browser messages alone never finalize it. |
| BR-48-06 | A workshop automatic discount applies to every direct website booking, including a Guest's; a Guest's discount is recorded without a Member, with no points or voucher and no points earned. Workshop vouchers and points are for Members only. |
