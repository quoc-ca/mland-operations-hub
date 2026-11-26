### 3.21 Close Branch Cash Day

| Use Case ID | UC-83 | Use Case Name | Close Branch Cash Day |
| --- | --- | --- | --- |
| Created By |  | Created Date |  |
| Primary Actors | Staff, Manager | Secondary Actors | None |

#### Description

As a Staff member of the branch or a Manager, I want to submit the daily cash count so that the branch cash book is closed and differences are resolved.

#### Trigger

An actor opens the cash close screen for a branch working day.

#### Preconditions

1. The actor is a Manager, or Staff whose location scope contains the branch.
2. The branch has not yet closed the selected working day (Vietnam time).

#### Postconditions

1. The day is recorded with expected cash, counted cash, difference, and closer.
2. A day with a difference is closed only after Manager confirmation.

#### Normal Flow

1. Actor opens the cash close of the branch.
2. System displays the expected confirmed cash of the day, net of change returned.
3. Actor enters the counted amount and submits it.
4. System records the close and closes the day when there is no difference.

#### Alternative Flows

3a. Counted amount differs → Reason is required and the close waits until a Manager confirms the difference with a reason.

4a. Cash is collected after submission → Cash belongs to the next working day, even while a difference awaits confirmation.

#### Exception Flows

4a. Day is already closed or in the future → System refuses the close.

#### Business Rules

BR-83-01, BR-83-02, BR-83-03, BR-83-04

#### Non-Functional Requirements

—

#### Priority

—

#### Messages

—

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-83-01 | Each branch closes its cash book once per working day. |
| BR-83-02 | Session assignment is not required because the close is per branch. |
| BR-83-03 | Bank transfers and VNPay are not part of the cash close. |
| BR-83-04 | Confirmed cash of a closed day never changes. |
