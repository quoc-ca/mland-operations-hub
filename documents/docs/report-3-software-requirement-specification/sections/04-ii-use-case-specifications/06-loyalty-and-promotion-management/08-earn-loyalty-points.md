### 6.8 Earn Loyalty Points

#### Primary Actors

System.

#### Secondary Actors

Order Management and Fulfillment, which reports verified full retail payment or payment of a branch order; Workshop Management, which reports a completed and fully paid booking or ticket; Member, who receives the points.

#### Description

This use case awards loyalty points to a Member once per eligible transaction, based on the amount actually paid after discounts and points, using the point policy recorded when the transaction was accepted.

#### Preconditions

1. The transaction belongs to a Member.
2. The point policy recorded at acceptance is available.

#### Normal Flow

**Earn Loyalty Points**

1. The system receives a verified result: full payment of a retail order, or a workshop booking that is both completed and fully paid.
2. The system checks that the transaction has not already earned points.
3. The system calculates the earning base: the item amount actually paid after discounts and points, excluding shipping, for a retail order; or the package fee actually paid, once for the whole booking, for a workshop.
4. The system divides the base by the earning rate and rounds down. With the initial policy of 10,000 VND per point, 9,999 VND earns 0 points and 10,000 to 19,999 VND earns 1 point; remainders are not carried over.
5. The system adds the points to the Member's balance and records an earned entry linked to the transaction.

**Earn Points per Ticket or per Branch Order**

1. When a multi-ticket workshop booking or a retail checkout split into branch orders is accepted, the system calculates the points of the whole booking or checkout once, with the policy recorded at acceptance and rounded down, and divides them between the tickets or branch orders by the largest remainder method. For example, three tickets of 466,666, 466,667 and 466,667 VND earn 93 points together, not 92.
2. When Workshop Management reports that a ticket is completed (checked out) and fully paid, after the group deposit is finalized, the system adds that ticket's share once.
3. When Order Management reports that a branch order is paid, the system adds that branch order's share once.
4. A ticket that is cancelled or a no-show, or a branch order that is cancelled or expires before payment, earns nothing.

#### Alternative Flows

**Step 1 — Workshop booking only booked, deposit paid, checked in or cancelled**

No points are earned. A booking that is fully paid but not completed, or completed but not fully paid, earns nothing until both conditions are met, in either order.

**Step 2 — Result received again or response lost**

The system does not add points a second time; balance and history stay consistent.

**Step 2 — Recorded policy missing or saving fails**

The system does not report success, keeps the transaction available for safe reprocessing, and never substitutes the current policy.

**Step 1 — Browser shows success only**

A browser message is not enough evidence to award points.

#### Postconditions

- The Member's balance and history include the earned points exactly once.
- New points can be used only after they are added, in both retail and workshop transactions.

#### Business Rules

BR-49-01, BR-49-02, BR-49-03, BR-49-04

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-49-01 | Points are earned only for Member transactions: a retail order after verified full payment, or a workshop booking that is both completed and fully paid. |
| BR-49-02 | The base is the amount actually paid after discounts and points, excluding shipping; points are rounded down per transaction and a workshop earns once on the fee actually paid. |
| BR-49-03 | Each eligible transaction has exactly one earning result, calculated with the policy recorded at acceptance; retries never add points twice. |
| BR-49-04 | For a multi-ticket booking or a checkout split into branch orders, points are rounded down once for the whole group and divided by the largest remainder method; each ticket or branch order earns only its own share, once, and a ticket or branch order that does not complete earns nothing. |
