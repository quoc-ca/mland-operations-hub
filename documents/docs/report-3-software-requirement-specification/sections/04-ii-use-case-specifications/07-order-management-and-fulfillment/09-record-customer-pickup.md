### 7.6 Record Customer Pickup

| Use Case ID | UC-60 | Use Case Name | Record Customer Pickup |
| --- | --- | --- | --- |
| Created By |  | Created Date |  |
| Primary Actors | Staff | Secondary Actors | Member; Mail Gateway |

#### Description

As Staff of the order's branch, I want to record that a ready pickup order was handed to the customer who gave its order code so that the picked-up completion is attributable.

#### Trigger

A customer presents an order code at the branch counter to collect a pickup order.

#### Preconditions

1. The platform is available and the retail order exists.
2. The actor is authenticated with verified identity, has an active Staff account, and is assigned to the order's branch.
3. The order is store pickup and either ready_for_pickup (Chờ khách nhận) after payment or Chờ thu tiền mặt.
4. The customer gives the order code at the counter.

#### Postconditions

1. Successful confirmation records picked_up as the irreversible terminal state with order, branch, confirming Staff, and time.
2. Rejected attempts preserve prior state without duplicate release, settlement, OTP, or refund states.

#### Normal Flow

1. Staff receives the order code from the customer and opens the matching ready pickup order.
2. System rechecks the active Staff account, branch assignment, and verified paid pickup readiness.
3. Staff hands the order to the customer and confirms the handover.
4. System rechecks current state and records ready_for_pickup to picked_up once.
5. System stores order, branch, confirming Staff, and time, displaying completion to Staff and Member.

#### Alternative Flows

2a. Cash at the counter order (Chờ thu tiền mặt) before 72-hour hold deadline → Customer pays exact amount at counter and Staff presses Xác nhận đã thu; System records payment, preparation, and handover in one transaction straight to Hoàn thành (no cash/change entry recorded).

#### Exception Flows

2b. Cash at the counter save failure or expired 72-hour hold → If any part fails, nothing is recorded and Staff retries; after 72 hours, the unpaid order expires and items return to the Member's cart.

2c. Not Staff, wrong branch, or inactive account → System rejects completion without private exposure or state change (Manager, Admin, Guest, and Member cannot record pickup; Feature-008 T089).

2d. Unpaid, unprepared, wrong method, or incompatible terminal state → System rejects without skipping milestones or changing another terminal result.

2e. Processing branch became inactive → System preserves the branch and deadlines without silent transfer or refund; currently assigned Staff may complete paid orders.

4a. Pickup already recorded or stale submission → System acknowledges a repeated completion without change and rejects stale or conflicting submissions.

4b. Read or save failure → System reports a safe actual failure and preserves accepted state without false completed pickup.

#### Business Rules

BR-60-01, BR-60-02, BR-60-03, BR-60-04, BR-60-05, BR-60-06, BR-60-07

#### Non-Functional Requirements

—

#### Priority

—

#### Messages

—

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-60-01 | Only active Staff assigned to the order's branch record pickup and counter cash; a Manager cannot (Feature-008 T089). |
| BR-60-02 | The order code given by the customer is enough to hand over the order; the Member does not sign in at the counter (Feature-008 FR-08-017, T088). |
| BR-60-03 | Record ready_for_pickup → picked_up only after verified payment and a recorded preparation; a cash at the counter order is paid, prepared and handed over in one transaction. |
| BR-60-04 | Retain the order, branch, confirming Staff and time; collect no identity-document copy, pickup OTP, extra contact/address or raw credentials. |
| BR-60-05 | Pickup never charges again, changes prices/deadlines or repeats quantity/benefit settlement; a cash-at-the-counter order is charged once, at the counter. |
| BR-60-06 | A repeated completion returns successful no-change; reject stale/conflicting requests without another handover effect. |
| BR-60-07 | Record each attempt and its actual outcome under the shared audit policy. |
