### 6.8 Earn Loyalty Points

| Use Case ID | UC-49 | Use Case Name | Earn Loyalty Points |
| --- | --- | --- | --- |
| Created By |  | Created Date |  |
| Primary Actors | System | Secondary Actors | Order Management and Fulfillment, Workshop Management, Member |

#### Description

As the System, I want to award loyalty points once per eligible Member transaction after verified completion and payment so that Members earn accurate rewards.

#### Trigger

Order Management and Fulfillment or Workshop Management reports a verified eligible transaction outcome.

#### Preconditions

1. The transaction belongs to a Member.
2. The point policy recorded at transaction acceptance is available.

#### Postconditions

1. The Member's balance and history include the earned points exactly once.
2. Newly added points become available for future retail and workshop transactions.

#### Normal Flow

**Earn Loyalty Points**

1. System receives a verified full retail payment or a completed, fully paid workshop booking.
2. System verifies the transaction has not already earned points.
3. System computes the earning base from the net amount actually paid, excluding shipping.
4. System divides the base by the recorded earning rate and rounds down.
5. System adds the earned points to the Member's balance and records an earned entry.

**Earn Points per Ticket or per Branch Order**

1. System calculates group points once at acceptance and divides them across tickets or branch orders.
2. System adds a ticket's share once upon Workshop Management completion and full-payment report.
3. System adds a branch order's share once upon Order Management payment report.

#### Alternative Flows

1a. Transaction is a Guest workshop booking → System closes the earning result with 0 points.

2a. Verified result is received again → System ignores the duplicate and keeps balance and history unchanged.

#### Exception Flows

1a. Workshop booking is only booked, deposit-paid, checked in, or cancelled → System awards no points.

1b. Browser reports success without verified backend result → System awards no points.

2a. Recorded policy is missing or save fails → System keeps transaction for reprocessing without substituting current policy.

Per-Ticket 2a. Ticket is cancelled or no-show → System awards no points for that ticket.

Per-Branch 3a. Branch order is cancelled or expires unpaid → System awards no points for that branch order.

#### Business Rules

BR-49-01, BR-49-02, BR-49-03, BR-49-04

#### Non-Functional Requirements

—

#### Priority

—

#### Messages

—

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-49-01 | Points are earned only for Member transactions: a retail order after verified full payment, or a workshop booking that is both completed and fully paid. |
| BR-49-02 | The base is the amount actually paid after discounts and points, excluding shipping; points are rounded down per transaction and a workshop earns once on the fee actually paid. |
| BR-49-03 | Each eligible transaction has exactly one earning result, calculated with the policy recorded at acceptance; retries never add points twice. |
| BR-49-04 | For a multi-ticket booking or a checkout split into branch orders, points are rounded down once for the whole group and divided by the largest remainder method; each ticket or branch order earns only its own share, once, and a ticket or branch order that does not complete earns nothing. |
