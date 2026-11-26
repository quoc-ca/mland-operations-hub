### 6.3 Redeem Loyalty Points

| Use Case ID | UC-44 | Use Case Name | Redeem Loyalty Points |
| --- | --- | --- | --- |
| Created By |  | Created Date |  |
| Primary Actors | Member | Secondary Actors | Order Management and Fulfillment, Workshop Management |

#### Description

As a Member, I want to apply available loyalty points at retail checkout or workshop booking so that I receive a non-cash discount.

#### Trigger

A Member reviews loyalty point redemption during retail checkout or workshop booking.

#### Preconditions

1. The Member is signed in and the account is active.
2. The Member is reviewing a retail checkout or direct website workshop booking.
3. A confirmed loyalty point policy exists.

#### Postconditions

1. Used points are deducted once after verified payment or valid 0 VND acceptance and recorded in history.
2. The accepted transaction retains its recorded point policy and discount even if policy changes later.
3. Collecting the remaining workshop amount does not deduct points again.

#### Normal Flow

1. System displays available points and the maximum allowed points for the order or booking.
2. Member enters a whole number of points within the allowed range.
3. System calculates the point discount and displays item amount, discounts, shipping, and total.
4. Member confirms the order or booking.
5. System rechecks price, conditions, available points, and limits, then holds the points on acceptance.
6. System deducts held points once after payment verification and records a used entry.

#### Alternative Flows

2a. Member chooses not to use points → System holds and deducts no points.

6a. Transaction is a valid 0 VND order or booking → System deducts held points once on acceptance.

6b. Checkout splits into branch orders → System divides points on acceptance and deducts each share on branch payment.

6c. Booking has multiple workshop tickets → System divides points across tickets on acceptance and deducts once on deposit verification.

#### Exception Flows

1a. Selected voucher disallows points → System disables point use and explains why (MSG49).

2a. Entered points are below minimum or above maximum → System rejects the input and shows the allowed range (MSG50).

5a. Points, price, or limits changed before acceptance → System recalculates and asks the Member to confirm the new total (MSG51).

5b. Another transaction holds the same points → System rejects the hold and returns the updated limit for reconfirmation.

6a. Payment expires or retail order is cancelled before payment → System releases held points under UC-50.

6b. Browser reports payment success without verified result → System does not deduct points.

#### Business Rules

BR-44-01, BR-44-02, BR-44-03, BR-44-04, BR-44-05

#### Non-Functional Requirements

—

#### Priority

—

#### Messages

MSG49, MSG50, MSG51

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-44-01 | Points are a non-cash discount chosen by the Member; the system never deducts points automatically. |
| BR-44-02 | Points used are whole numbers from the minimum per use up to `min(available points, floor(cap × amount after program discount / point value))`. |
| BR-44-03 | Points are calculated on the amount after both the automatic discount and the voucher. They can be combined with an automatic discount, and with a voucher only when that voucher allows points; an order or booking with a voucher that does not allow points cannot use points. |
| BR-44-04 | Points are held when the order or booking is accepted and deducted once after verified payment of the retail order (or of each branch order, for its share) or the workshop deposit, or on acceptance of a valid 0 VND order or booking; the remaining workshop payment does not deduct points again. |
| BR-44-05 | Points earned by a transaction cannot discount that same transaction. |
