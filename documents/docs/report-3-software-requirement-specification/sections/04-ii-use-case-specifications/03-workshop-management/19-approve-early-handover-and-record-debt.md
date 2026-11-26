### 3.16 Approve Early Handover and Record Debt

| Use Case ID | UC-81 | Use Case Name | Approve Early Handover and Record Debt |
| --- | --- | --- | --- |
| Created By |  | Created Date |  |
| Primary Actors | Manager | Secondary Actors | None |

#### Description

As a Manager, I want to let a customer receive the product before full payment so that the unpaid debt stays traceable.

#### Trigger

A Manager opens a ticket that is not fully paid to approve early handover or record debt.

#### Preconditions

1. The actor is an active Manager.
2. The ticket bill is not yet confirmed as fully paid.

#### Postconditions

1. The early handover or debt is recorded with the Manager, reason, and time.
2. The unpaid amount and bill status do not change.

#### Normal Flow

1. Actor opens the ticket bill.
2. Actor approves early handover and enters a reason.
3. System records the approval and allows product handover.
4. Actor records the outstanding debt with an amount and a reason.

#### Alternative Flows

2a. Ticket is already fully paid → No approval is needed because handover is already allowed.

4a. Debt is followed up → Manager closes the recorded debt with a reason; the customer pays it as a normal payment.

#### Exception Flows

4a. Debt amount is zero or above the unpaid amount → System refuses the debt record.

#### Business Rules

BR-81-01, BR-81-02, BR-81-03

#### Non-Functional Requirements

—

#### Priority

—

#### Messages

—

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-81-01 | Only a Manager may approve early handover, record a debt, or close it, and a reason is required. |
| BR-81-02 | A recorded debt never finalizes a ticket bill. |
| BR-81-03 | There is no automatic reminder, deadline, or booking block. |
