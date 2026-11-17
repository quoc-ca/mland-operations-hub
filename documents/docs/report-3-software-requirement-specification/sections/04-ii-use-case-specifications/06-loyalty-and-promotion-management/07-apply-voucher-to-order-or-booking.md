### 6.7 Apply Voucher to Order or Booking

#### Primary Actors

Member.

#### Secondary Actors

Order Management and Fulfillment (retail checkout and payment); Workshop Management (booking and deposit payment).

#### Description

This use case allows a Member to apply one voucher or one automatic discount to a retail order, or one workshop voucher to a direct website workshop booking, and see the exact discount before confirming. The discount is held on acceptance and finalized once after verified payment, or when a valid 0 VND order or booking is accepted.

This use case includes the following sub-flows:

- Apply Promotion to Retail Order
- Apply Voucher to Workshop Booking

#### Preconditions

1. The Member is signed in and the account is active.
2. The Member is at retail checkout, or is booking a workshop directly on the website.

#### Normal Flow

**Apply Promotion to Retail Order**

1. The system calculates the eligible amount from the cart lines in the program's scope that can be bought, before any discount or points and excluding shipping. For a voucher limited to branches, a line is eligible only when the branch recorded on the cart line is in the voucher's branch scope; the branch that later prepares the order does not count.
2. If several automatic discounts apply, the system selects the one with the largest discount; on a tie, the earliest created one.
3. The Member may instead select a saved voucher or enter a code directly. The code is trimmed and matched ignoring case. A voucher replaces the automatic discount; they are not combined.
4. The system checks the voucher's scope, time, status, minimum amount and limits.
5. The system calculates the discount: a fixed amount once per order, or a percentage rounded down to the VND, never above the maximum discount or the eligible amount. For example, 10% of an eligible 600,000 VND with a 50,000 VND cap gives 50,000 VND. With a 10% voucher limited to branch A, a cart with one 1,000,000 VND line at branch A and one at branch B gets 100,000 VND off, for the line at branch A only.
6. The Member may add points under UC-44 Redeem Loyalty Points, then confirms the order.
7. The system rechecks all conditions and holds one voucher use together with the accepted order.
8. When the full payment is verified, or a valid 0 VND order is accepted, the system finalizes the use once. When Order Management splits the checkout into branch orders that are paid separately, the discount is divided between the branch orders on acceptance; the voucher use is finalized when the first branch order that has a share of the discount is paid.

**Apply Voucher to Workshop Booking**

1. The Member selects a saved workshop voucher or enters its code.
2. The system checks each ticket in the booking: the ticket is in scope when its package is in the voucher's package scope (if any) and every session of the ticket matches the voucher's branch, participation date and session scope (if any). It also checks the booking period, the minimum package fee and the limits.
3. The system calculates the discount on the total price of the tickets in scope, then subtracts points if allowed, to get the fee after discounts. Tickets outside the scope are not discounted. The minimum package fee is checked against the tickets in scope, and the maximum discount applies once per booking, not per participant.
4. The system returns the fee after discounts and which tickets are in scope. Workshop Management divides the discount between the tickets, calculates the amount collected at booking and the remaining amount, and shows the original fee, each discount, the fee after discounts, the deposit and the remaining amount. For example, a 1,000,000 VND fee with a 100,000 VND voucher and 200 points gives a fee after discounts of 880,000 VND.
5. The Member confirms the booking; the system holds the voucher use and records the policy in effect.
6. When the deposit is verified, or Workshop Management confirms a 0 VND booking without the payment gateway, the system finalizes the use once. The remaining payment does not apply the voucher again.

#### Alternative Flows

**Retail Step 4 / Workshop Step 2 — Voucher cannot be used**

The system explains why (MSG48): the code does not exist, it is a workshop code used for products or the reverse, it is expired, paused or fully used, the Member's limit is reached, the minimum amount is not met, no cart line is at a branch in the voucher's branch scope, or no ticket in the booking is in the voucher's package, branch, date or session scope. The Member can choose another voucher or none.

**Retail Step 1 — A line in scope cannot be bought**

A hidden, stopped or deleted product in scope is excluded from the eligible amount; other lines in scope can still use the program.

**Retail Step 7 / Workshop Step 5 — Conditions changed before acceptance**

If the price, limits or points changed, the system recalculates and asks the Member to confirm the new total, fee or deposit (MSG51).

**Workshop Step 3 — Fee after discounts is 0 VND**

The booking is accepted. Workshop Management confirms it without the payment gateway, and the system finalizes the benefits as for a verified deposit.

**Workshop Step 3 — Fee after discounts is negative because of invalid data**

The system rejects the booking, holds nothing and lets the Member adjust (MSG52). A positive amount below the payment gateway minimum is handled by Workshop Management.

**Workshop Step 1 — Booking is not a direct Member booking on the website**

Workshop vouchers are not extended to Guests or external booking channels.

**Retail Step 8 / Workshop Step 6 — Payment not verified in time**

The held use is released under UC-50.

**Any step — Using another person's transaction reference**

Knowing another Member's transaction reference does not allow use of their points or holds (MSG01).

#### Postconditions

- Each order or booking uses at most one promotion; the discount is recorded and does not change if the program changes later.
- The voucher use is finalized exactly once after verified payment or acceptance of a valid 0 VND transaction, or released if payment does not complete.
- The branch preparing a retail order does not change the discount.

#### Business Rules

BR-48-01, BR-48-02, BR-48-03, BR-48-04, BR-48-05

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-48-01 | A retail order uses at most one automatic discount or one voucher, and a workshop booking uses at most one workshop voucher. |
| BR-48-02 | The retail eligible amount includes only purchasable lines in scope (and, for a branch-limited voucher, only lines whose cart branch is in scope), before discounts and points, excluding shipping; a fixed discount applies once per order and a percentage is rounded down, never above the cap or the eligible amount. |
| BR-48-03 | The workshop scope is checked per ticket: the ticket's package must be in scope and every session of the ticket must match the voucher's branch, date and session scope. Only tickets in scope are discounted; the minimum fee and the discount use their total price, and the cap applies once per booking. |
| BR-48-04 | A workshop fee after discounts of 0 VND is valid and only a negative fee is rejected. Workshop Management divides the discount between tickets and calculates the amount collected at booking and the remaining amount; this feature does not calculate or store the deposit. |
| BR-48-05 | The use is held on acceptance and finalized once after verified payment, or on acceptance of a valid 0 VND order or booking; for a checkout split into branch orders, when the first branch order with a share of the discount is paid. Browser messages alone never finalize it. |
