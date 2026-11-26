### 10.7 Fulfil Custom Order

| Use Case ID | UC-73 | Use Case Name | Fulfil Custom Order |
| --- | --- | --- | --- |
| Created By |  | Created Date |  |
| Primary Actors | Staff; Manager | Secondary Actors | None |

#### Description

As Staff or a Manager, I want to record the handover of a finished custom item by Member pickup or manual GHTK handoff so that the order is completed.

#### Trigger

Staff or a Manager selects a custom order that is ready for handover.

#### Preconditions

1. The actor is signed in as Staff or Manager.
2. The order is ready for handover (use case 72).

#### Postconditions

1. The order is closed with a delivered status.

#### Normal Flow

1. Actor selects the ready custom order.
2. Actor hands the item to the Member at pickup.
3. Actor confirms the order is complete.
4. System records the delivery and closes the order.

#### Alternative Flows

2a. Shop hands the item to GHTK for delivery → Actor confirms the manual handoff, and System records the delivery and closes the order without provider tracking.

3a. Actor cancels the confirmation → System keeps the order ready for handover.

#### Exception Flows

4a. Save fails → System displays an error and keeps the previous status.

#### Business Rules

BR-73-01, BR-73-02

#### Non-Functional Requirements

—

#### Priority

—

#### Messages

—

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-73-01 | Only a paid, finished order can be handed over and closed. |
| BR-73-02 | GHTK is a manual handoff; the platform does not track the custom delivery. |
