### 7.9 Record Customer Pickup

#### Primary Actors

Staff.

#### Secondary Actors

Member; Mail Gateway.

#### Description

As Staff of the order's branch, I want to record that a ready pickup order was handed to the customer who gave its order code so that the picked-up completion is attributable.

#### Preconditions

1. The platform is available and the retail order exists.
2. The actor is authenticated with verified identity, has an active Staff account, and is assigned to the order's processing branch.
3. The order is for store pickup and is prepared and ready_for_pickup (Chờ khách nhận) after verified payment, or is a cash at the counter order waiting for payment (Chờ thu tiền mặt).
4. The customer gives the order code at the counter.

#### Postconditions

- Successful confirmation records picked_up as the irreversible pickup terminal state with the order, branch, confirming Staff and time.
- Rejected/failed attempts preserve prior state. No duplicate release, payment/quantity settlement, document copy, pickup OTP or courier/cancellation/refund state is introduced.

#### Normal Flow

**Record Customer Pickup**

1. The customer gives the order code; Staff opens the matching ready pickup order at its processing branch.
2. The system rechecks the active Staff account, the branch assignment and verified paid pickup readiness.
3. Staff hands the order to the customer and confirms the handover.
4. The system rechecks current state, then records ready_for_pickup to picked_up once.
5. The system stores the order, branch, confirming Staff and time, and displays the recorded pickup completion (Staff Hoàn thành, Member Đã giao). Optional completion email follows the shared notification policy and never reverses completion.

#### Alternative Flows

**Steps 2 and 4 — Not Staff, wrong branch or inactive account**

Reject completion without private exposure or state change. Only active Staff assigned to the order's branch record pickup; Manager, Admin, Guest and Member cannot record shop completion (Feature-008 T089).

**Steps 2 and 4 — Unpaid, unprepared, wrong method or incompatible terminal state**

Reject without skipping milestones or changing another terminal result. An order that was not prepared cannot be handed over; use the appropriate preceding use case.

**Steps 2 and 3 — Cash at the counter order (Chờ thu tiền mặt)**

Before the hold deadline (72 hours) the customer pays the exact amount at the counter and Staff presses Xác nhận đã thu; the system records payment for that branch order, its preparation and the handover in one transaction, so the order goes straight to Hoàn thành without waiting in Chờ khách nhận. No cash-received or change entry is recorded. If any part fails nothing is recorded and Staff presses again. After the deadline the unpaid order expires and its items return to the Member's cart.

**Step 4 — Pickup already recorded or stale submission**

Never record a second effective pickup or alternate terminal. The system acknowledges a repeated completion without change and rejects stale/conflicting submissions.

**Step 4 — Read/save failure**

Report a safe actual failure and preserve accepted state/evidence, without false completed pickup.

**Steps 2 and 4 — Processing branch became inactive**

An inactive branch cannot receive new checkout, but accepted orders retain their branch and original deadlines. Currently assigned Staff may complete paid orders; physical handover problems require operational follow-up without automatic transfer, cancellation or refund.

#### Business Rules

BR-60-01, BR-60-02, BR-60-03, BR-60-04, BR-60-05, BR-60-06, BR-60-07

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
