### 6.7 Apply Voucher to Order or Booking

#### Primary Actors

- Member

#### Secondary Actors

- Order Management and Fulfillment (retail checkout and payment)
- Workshop Management (booking and deposit payment)

#### Description

This use case allows a Member to apply one voucher or one automatic discount to a retail order, or one workshop voucher to a direct website workshop booking, and see the exact discount before confirming. The discount is held on acceptance and finalized once after verified payment.

This use case includes the following sub-flows:

- Apply Promotion to Retail Order
- Apply Voucher to Workshop Booking

#### Preconditions

1. The Member is signed in and the account is active.
2. The Member is at retail checkout, or is booking a workshop directly on the website.

#### Normal Flow

**Apply Promotion to Retail Order**

1. The system calculates the eligible amount from the cart lines in the program's scope that can be bought, before any discount or points and excluding shipping.
2. If several automatic discounts apply, the system selects the one with the largest discount; on a tie, the earliest created one.
3. The Member may instead select a saved voucher or enter a code directly. The code is trimmed and matched ignoring case. A voucher replaces the automatic discount; they are not combined.
4. The system checks the voucher's scope, time, status, minimum amount and limits.
5. The system calculates the discount: a fixed amount once per order, or a percentage rounded down to the VND, never above the maximum discount or the eligible amount. For example, 10% of an eligible 600,000 VND with a 50,000 VND cap gives 50,000 VND.
6. The Member may add points under UC-44 Redeem Loyalty Points, then confirms the order.
7. The system rechecks all conditions and holds one voucher use together with the accepted order.
8. When the full payment is verified, or a valid 0 VND order is accepted, the system finalizes the use once.

**Apply Voucher to Workshop Booking**

1. The Member selects a saved workshop voucher or enters its code.
2. The system checks the package, branch, participation date and session of every selected session, the booking period, the minimum package fee and the limits.
3. The system subtracts the voucher from the package fee, then points if allowed, to get the fee after discounts. The maximum discount applies once per booking, not per participant.
4. The system shows the original fee, each discount, the fee after discounts, and the deposit and remaining amount calculated by Workshop Management. For example, a 1,000,000 VND fee with a 100,000 VND voucher and 200 points gives 880,000 VND, a 440,000 VND deposit and 440,000 VND remaining.
5. The Member confirms the booking; the system holds the voucher use and records the policy in effect.
6. When the deposit is verified, the system finalizes the use once. The remaining payment does not apply the voucher again.

#### Alternative Flows

**Retail Step 4 / Workshop Step 2 — Voucher cannot be used**

The system explains why (MSG48): the code does not exist, it is a workshop code used for products or the reverse, it is expired, paused or fully used, the Member's limit is reached, the minimum amount is not met, or a selected session is outside the voucher's branch, date or session scope. The Member can choose another voucher or none.

**Retail Step 1 — A line in scope cannot be bought**

A hidden, stopped or deleted product in scope is excluded from the eligible amount; other lines in scope can still use the program.

**Retail Step 7 / Workshop Step 5 — Conditions changed before acceptance**

If the price, limits or points changed, the system recalculates and asks the Member to confirm the new total, fee or deposit (MSG51).

**Workshop Step 3 — Fee after discounts is 0 or less**

The system rejects the booking and lets the Member adjust (MSG52). Free workshop bookings are not created.

**Workshop Step 1 — Booking is not a direct Member booking on the website**

Workshop vouchers are not extended to Guests or external booking channels.

**Retail Step 8 / Workshop Step 6 — Payment not verified in time**

The held use is released under UC-50.

**Any step — Using another person's transaction reference**

Knowing another Member's transaction reference does not allow use of their points or holds (MSG01).

#### Postconditions

- Each order or booking uses at most one promotion; the discount is recorded and does not change if the program changes later.
- The voucher use is finalized exactly once after verified payment, or released if payment does not complete.
- The branch preparing a retail order does not change the discount.

#### Business Rules

- BR-48-01
- BR-48-02
- BR-48-03
- BR-48-04
- BR-48-05

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-48-01 | A retail order uses at most one automatic discount or one voucher, and a workshop booking uses at most one workshop voucher. |
| BR-48-02 | The retail eligible amount includes only purchasable lines in scope, before discounts and points, excluding shipping; a fixed discount applies once per order and a percentage is rounded down, never above the cap or the eligible amount. |
| BR-48-03 | For a multi-session workshop package, every selected session must match the voucher's branch, date and session scope; the cap applies once per booking. |
| BR-48-04 | The workshop fee after discounts must be greater than 0; Workshop Management calculates `deposit = ceil(fee after discounts / 2)` and the remaining amount, and this feature does not calculate or store the deposit. |
| BR-48-05 | The use is held on acceptance and finalized once after verified payment, or immediately for a valid 0 VND retail order; browser messages alone never finalize it. |
