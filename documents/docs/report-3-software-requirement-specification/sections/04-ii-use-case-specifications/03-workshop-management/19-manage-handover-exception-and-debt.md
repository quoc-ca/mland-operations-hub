### 3.19 Manage Handover Exception and Debt

| Use Case ID | UC-81 | Use Case Name | Manage Handover Exception and Debt |
| --- | --- | --- | --- |
| Created By |  | Created Date |  |
| Primary Actors | Manager | Secondary Actors | None |

#### Description

As a Manager, I want to approve a handover before payment is verified and record or close the resulting debt so that exceptions stay traceable.

#### Trigger

A Manager opens an unfinalized ticket folio to approve a handover exception or record debt.

#### Preconditions

1. The actor is an active Manager.
2. The ticket has a folio that is not yet fully verified as paid.

#### Postconditions

1. The exception or debt is recorded with the Manager, reason, and time.
2. The balance and folio state are not changed by the exception or debt record.

#### Normal Flow

1. Actor opens the ticket folio.
2. Actor approves the handover exception and enters a reason.
3. System records the exception and allows product handover.
4. Actor records the outstanding debt with an amount and a reason.

#### Alternative Flows

2a. Folio is already finalized → No exception is needed because handover is already allowed.

4a. Debt is followed up → Manager closes the recorded debt with a reason; the balance is settled by a collection.

#### Exception Flows

4a. Debt amount is zero or above the balance due → System refuses the debt record.

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
| BR-81-01 | Only a Manager may approve the exception, record a debt, or close it, and a reason is required. |
| BR-81-02 | A recorded debt never finalizes a folio. |
| BR-81-03 | There is no automatic reminder, deadline, or booking block. |
