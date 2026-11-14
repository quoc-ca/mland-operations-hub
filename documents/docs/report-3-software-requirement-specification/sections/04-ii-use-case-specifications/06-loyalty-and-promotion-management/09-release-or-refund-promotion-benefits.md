### 6.9 Release or Refund Promotion Benefits

#### Primary Actors

System.

#### Secondary Actors

Order Management and Fulfillment, which reports expiry or cancellation before payment of an order or branch order; Workshop Management, which reports expiry, a confirmed valid cancellation or a cancellation by Mland of a booking or ticket; Member, whose points and voucher uses are returned.

#### Description

This use case returns a Member's points and voucher uses when a transaction does not complete. Held benefits are released when an order or booking expires or a retail order is cancelled before payment; benefits already finalized are refunded when a workshop booking or ticket is validly cancelled before completion or cancelled by Mland. Each benefit is returned exactly once.

This use case includes the following sub-flows:

- Release Held Benefits
- Release Benefits of a Branch Order
- Refund Finalized Workshop Benefits
- Refund Benefits of a Workshop Ticket

#### Preconditions

1. The order or booking held or used points or a voucher.
2. Order Management or Workshop Management has confirmed the outcome.

#### Normal Flow

**Release Held Benefits**

1. The system receives a confirmed result that a retail order or workshop booking expired without accepted payment, or that a retail order was cancelled before payment.
2. The system returns the held points to the available balance and the held voucher use to the Member and the program.
3. The system records a released entry linked to the transaction. Nothing is recorded as used and no extra points are added.

**Release Benefits of a Branch Order**

1. The system receives a confirmed result that a branch order of a split retail checkout was cancelled or expired before payment.
2. The system returns the points held for that branch order to the available balance and closes that branch order's earning share. Other branch orders are not affected; a paid branch order cannot be released.
3. The system releases the voucher use only when every branch order with a share of the voucher discount has ended unpaid and the use is still held, even if a branch order without a share has been paid. A voucher use already finalized is not returned when another branch order is cancelled.

**Refund Finalized Workshop Benefits**

1. The system receives a confirmed valid cancellation of a workshop booking before completion.
2. If the benefits were only held, the system releases them as above.
3. If points were deducted, the system returns exactly the same number of points, even if the point value has changed, linked to the original deduction.
4. If a voucher use was finalized, the system returns one use to the Member and one to the program, even if no points were used. An expired or paused voucher is not extended or reactivated.
5. The program's used count and total discount no longer include the cancelled booking; history is kept.

**Refund Benefits of a Workshop Ticket**

1. The system receives a confirmed result for one ticket of a multi-ticket booking: a valid cancellation (at least 24 hours before the first session) or a cancellation by Mland.
2. The system returns exactly the points used by that ticket, linked to the original deduction; other tickets are not affected.
3. The system returns the group's voucher use once: as soon as any ticket is cancelled by Mland, or when every ticket has ended in a valid cancellation or a cancellation by Mland.

#### Alternative Flows

**Any step — Result received again or previously only partly completed**

The system completes only the missing part. It never returns a benefit twice, never both releases and refunds the same benefit, and never refunds a benefit that was not used.

**Step 1 — No-show, unconfirmed or invalid cancellation**

No benefit is returned and no points are earned. If the result is unclear, the system waits for a confirmed result. A ticket that is a no-show or cancelled late returns no points and prevents the group's voucher use from being returned through valid cancellations.

**Step 1 — Whole-booking or whole-checkout result after per-ticket or per-branch-order results**

Once a ticket or branch order has its own result, a result for the whole booking or checkout is rejected, and the reverse; one transaction follows only one of the two paths.

**Step 1 — Payment and expiry or cancellation happen close together**

The system follows the confirmed result from Order Management or Workshop Management; it never both finalizes and releases the same benefit and never creates a new transaction or deadline because of a lost response.

**Step 1 — Completed workshop or retail return**

This version has no cancellation of completed workshops, no retail returns or refunds, no recovery of earned points and no negative balance.

#### Postconditions

- Held or used benefits are returned exactly once with linked history entries.
- A new order or booking after a release checks the current conditions again.

#### Business Rules

BR-50-01, BR-50-02, BR-50-03, BR-50-04, BR-50-05

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-50-01 | Expiry, or cancellation of a retail order before payment, without accepted payment releases held points and voucher uses exactly once, without recording them as used. |
| BR-50-02 | A confirmed valid workshop cancellation before completion refunds exactly the points deducted and one voucher use to the Member and the program; it does not convert points to money or extend vouchers. |
| BR-50-03 | Release and refund results are tracked per benefit so that retries complete only the missing part and never return a benefit twice. |
| BR-50-04 | No-shows, unconfirmed or invalid cancellations and completed transactions return no benefits; the outcome is decided only by the confirmed result from the owning feature. |
| BR-50-05 | For a split retail checkout or a multi-ticket booking, points are released or refunded per branch order or per ticket. The voucher use returns once: for a checkout, when every branch order with a share of the discount ends unpaid; for a booking, when any ticket is cancelled by Mland or every ticket ends in a valid or Mland cancellation. |
