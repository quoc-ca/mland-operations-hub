### 3.28 Process Refund Request

| Use Case ID | UC-90 | Use Case Name | Process Refund Request |
| --- | --- | --- | --- |
| Created By |  | Created Date |  |
| Primary Actors | Manager | Secondary Actors | Staff (assigned-ticket lookup and link assistance only), Mail Gateway |

#### Description

As a Manager, I want to approve, record, confirm, or return a refund request so that money owed to the customer is paid by bank transfer and tracked.

#### Trigger

A Manager opens a refund request pending approval.

#### Preconditions

1. The actor is an active Manager.
2. A refund request exists in a state that allows the selected action.

#### Postconditions

1. Every step records the actor, time, and proof.
2. Folio and booking consequences apply only after the request is refunded.

#### Normal Flow

1. Actor reviews a request pending approval and approves it with a reason.
2. Actor transfers the money outside the system.
3. Actor records the bank reference, with an optional receipt image.
4. Actor confirms the request as refunded after matching the bank statement.

#### Alternative Flows

1a. Account not yet received → Manager may close as unreachable after 30 days and reopen later; assigned Staff may assist with ticket refunds.

#### Exception Flows

1a. Refund is refused, returned, or unmatched → Manager returns it to pending account with a reason, voiding approval and sending a new link.

4a. Request is already refunded → Refunded is final; any late bank return is handled outside the system.

#### Business Rules

BR-90-01, BR-90-02, BR-90-03

#### Non-Functional Requirements

—

#### Priority

—

#### Messages

—

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-90-01 | The system never calls VNPay Refund; there is no terminal rejection of a refund. |
| BR-90-02 | The total refunded or pending for a ticket never exceeds the money collected for it. |
| BR-90-03 | Staff may view only ticket refunds for sessions assigned to them and may enter the recipient account or resend its link; every refund decision stays with the Manager. Group-level refunds are Manager-only in the queue. |
