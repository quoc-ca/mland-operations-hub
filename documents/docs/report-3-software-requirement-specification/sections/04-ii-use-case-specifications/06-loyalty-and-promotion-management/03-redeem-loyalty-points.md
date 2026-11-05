### 6.3 Redeem Loyalty Points

#### Primary Actors

- Member

#### Secondary Actors

- Order Management and Fulfillment (retail checkout and payment)
- Workshop Management (booking and deposit payment)

#### Description

This use case allows a Member to choose how many available points to use as a non-cash discount on a retail order or a direct website workshop booking. Points are applied after the voucher or automatic discount, are held when the order or booking is accepted, and are deducted only after the payment is verified.

#### Preconditions

1. The Member is signed in and the account is active.
2. The Member is reviewing a retail checkout or a workshop booking.
3. A confirmed loyalty point policy exists. The initial policy is 100 VND per point, at least 10 points per use, and a cap of 10% of the amount after the program discount.

#### Normal Flow

**Redeem Loyalty Points**

1. The system shows the available points and the maximum points allowed for this order or booking. The maximum is the smaller of the available points and `floor(cap × amount after program discount / point value)`; with the initial policy, `min(available, floor(10% × amount after discount / 100))`.
2. The Member chooses not to use points, or enters a whole number of points from the minimum to the maximum.
3. The system calculates the point discount and shows the item amount, program discount, point discount, shipping (retail only) and total. For a workshop, it shows the fee after discounts, which Workshop Management uses to calculate the deposit and the remaining amount.
4. The Member confirms the order or booking.
5. The system rechecks the price, conditions, available points and limits, records the point policy in effect, and holds the points together with the accepted order or booking.
6. After the retail payment or the workshop deposit is verified, the system deducts the held points once and records a used entry.

#### Alternative Flows

**Step 1 — The selected voucher does not allow points**

The system disables point use and explains why (MSG49). Automatic discounts always allow points.

**Step 2 — Points below the minimum or above the maximum**

The system rejects the amount and shows the allowed range (MSG50). For example, an amount after discount of 9,900 VND allows at most 9 points, so the 10-point minimum cannot be reached.

**Step 5 — Points, price or limits changed before acceptance**

The system recalculates and asks the Member to confirm the new total (MSG51). No order or booking is created with a total the Member did not agree to.

**Step 5 — Another transaction is holding the same points**

Held points cannot be used twice. The transaction that cannot hold the points receives the new limit and must confirm again.

**Step 6 — Payment is not verified before the hold expires, or the Member cancels a retail order before payment**

The held points are released under UC-50 Release or Refund Promotion Benefits.

**Step 6 — Browser shows payment success but no verified result exists**

The system does not deduct points from a browser message alone.

#### Postconditions

- Used points are deducted exactly once after verified payment and recorded in the history.
- The order or booking keeps the point policy and discount recorded when it was accepted, even if the policy changes later.
- Collecting the remaining workshop amount does not deduct points again.

#### Business Rules

- BR-44-01
- BR-44-02
- BR-44-03
- BR-44-04
- BR-44-05

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-44-01 | Points are a non-cash discount chosen by the Member; the system never deducts points automatically. |
| BR-44-02 | Points used are whole numbers from the minimum per use up to `min(available points, floor(cap × amount after program discount / point value))`. |
| BR-44-03 | Points can be combined with an automatic discount, or with a voucher only when that voucher allows points. |
| BR-44-04 | Points are held when the order or booking is accepted and deducted once after verified payment of the retail order or the workshop deposit; the remaining workshop payment does not deduct points again. |
| BR-44-05 | Points earned by a transaction cannot discount that same transaction. |
