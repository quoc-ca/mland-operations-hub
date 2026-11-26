### 3.8 Assign Staff to Workshop Slot

| Use Case ID | UC-26 | Use Case Name | Assign Staff to Workshop Slot |
| --- | --- | --- | --- |
| Created By |  | Created Date |  |
| Primary Actors | Manager | Secondary Actors | None |

#### Description

As a Manager, I want to revoke or restore the assignment of Staff to a workshop session so that only authorized Staff can access the session.

#### Trigger

A Manager opens a workshop session to manage Staff assignments.

#### Preconditions

1. The Manager is authenticated and active.
2. The session is Draft, Published, Sales Paused, or In Progress.
3. The assignee is an active Staff account with the session location in scope.

#### Postconditions

1. The assignment change is saved with history and takes effect immediately on Staff access.

#### Normal Flow

1. Actor opens a session.
2. System displays the assigned Staff.
3. Actor assigns or revokes a Staff member.
4. System validates the Staff account, location scope, and session state.
5. System saves the change and records it in history.

#### Alternative Flows

3a. System auto-assigns active in-scope Staff to sessions → Manager-revoked assignments are never added back automatically.

5a. Removing the last assignment after check-in has opened → System asks for confirmation.

#### Exception Flows

4a. Staff is not eligible → System rejects the assignment.

#### Business Rules

BR-22-01, BR-22-02

#### Non-Functional Requirements

—

#### Priority

—

#### Messages

—

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-22-01 | Only an active Staff account with the location in scope may be assigned. There is no operational role, time-overlap check, or assignment limit. |
| BR-22-02 | Only a Manager assigns or revokes Staff; assignment gives Staff access to the session. |
