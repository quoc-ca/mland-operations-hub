### 3.25 Grant Extra Change Allowance

| Use Case ID | UC-87 | Use Case Name | Grant Extra Change Allowance |
| --- | --- | --- | --- |
| Created By |  | Created Date |  |
| Primary Actors | Manager | Secondary Actors | None |

#### Description

As a Manager, I want to grant one extra counted change to a ticket so that the customer can change sessions after the normal allowance is used.

#### Trigger

A Manager opens a ticket to grant an extra session-change allowance.

#### Preconditions

1. The actor is an active Manager.
2. The ticket exists.

#### Postconditions

1. The ticket receives one extra counted customer change.
2. The grant is recorded with the Manager, reason, and time.

#### Normal Flow

1. Actor opens the ticket.
2. Actor chooses to grant an extra change and enters a reason.
3. System validates the authority and the reason.
4. System records the allowance.

#### Alternative Flows

4a. Same request is repeated → System returns the recorded grant and adds no second allowance.

#### Exception Flows

2a. Reason is missing → System refuses the grant.

3a. Actor is not a Manager → System refuses the grant.

#### Business Rules

BR-87-01, BR-87-02, BR-87-03

#### Non-Functional Requirements

—

#### Priority

—

#### Messages

—

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-87-01 | Only a Manager may grant an extra change, with a reason and audit. |
| BR-87-02 | A grant never performs the change itself. |
| BR-87-03 | Mland-caused changes do not count against the allowance. |
