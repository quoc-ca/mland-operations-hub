### 10.6 Fulfil Paid Custom Order

| Use Case ID | UC-72 | Use Case Name | Fulfil Paid Custom Order |
| --- | --- | --- | --- |
| Created By |  | Created Date |  |
| Primary Actors | Staff; Manager | Secondary Actors | None |

#### Description

As Staff or a Manager, I want to move a fully paid custom order through production until it is ready for handover so that manufacturing progress is tracked.

#### Trigger

Staff or a Manager opens the custom order operations list and selects a paid order.

#### Preconditions

1. The actor is signed in as Staff or Manager.
2. The order is paid in full.

#### Postconditions

1. The order is recorded as in production or ready for handover.

#### Normal Flow

1. Actor opens the custom order operations list and selects a paid order.
2. Actor confirms the start of production.
3. System records the order as in production.
4. Actor confirms the finished item is ready for handover.
5. System records production as completed and the order as ready for handover.

#### Alternative Flows

2a. Actor cancels the confirmation at step 2 or 4 → System leaves the status unchanged.

#### Exception Flows

1a. Actor is not Staff or Manager → System denies the change.

3a. Order unpaid, unknown status, or skipped stage at step 3 or 5 → System rejects the change.

3b. Save fails at step 3 or 5 → System displays an error and keeps the previous status.

#### Business Rules

BR-72-01, BR-72-02

#### Non-Functional Requirements

—

#### Priority

—

#### Messages

—

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-72-01 | Production starts only after the complete quoted amount is verified. |
| BR-72-02 | The stages move forward: waiting for production, in production, ready for handover. |
