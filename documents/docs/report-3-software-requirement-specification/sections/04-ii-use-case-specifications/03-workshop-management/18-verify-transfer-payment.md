### 3.15 Verify Transfer Payment

| Use Case ID | UC-80 | Use Case Name | Verify Transfer Payment |
| --- | --- | --- | --- |
| Created By |  | Created Date |  |
| Primary Actors | Manager | Secondary Actors | None |

#### Description

As a Manager, I want to confirm a pending bank-transfer collection or mark it as failed so that the ticket balance reflects only verified receipts.

#### Trigger

A Manager opens a pending bank-transfer collection on a ticket bill.

#### Preconditions

1. The actor is an active Manager.
2. A pending collection exists on the ticket bill.

#### Postconditions

1. A confirmed transfer reduces the balance; a failed collection does not.
2. The decision, acting account, and time are recorded.

#### Normal Flow

1. Actor opens the pending collection of the ticket.
2. Actor checks that the money arrived in the shop account.
3. Actor confirms the transfer.
4. System confirms the collection, updates the balance, and finalizes the bill if nothing is due or pending.

#### Alternative Flows

None.

#### Exception Flows

2a. Money cannot be verified → Manager marks the pending collection as failed with a reason so payment can be retried.

3a. Actor is not a Manager → System refuses the action.

3b. Collection belongs to another ticket → System reports it as not found and changes nothing.

#### Business Rules

BR-80-01, BR-80-02, BR-80-03

#### Non-Functional Requirements

—

#### Priority

—

#### Messages

—

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-80-01 | Transfer confirmation is Manager-only in V1; remote confirmation is allowed. |
| BR-80-02 | A customer screenshot is never sufficient evidence. |
| BR-80-03 | A VNPay collection is never marked as paid manually. |
