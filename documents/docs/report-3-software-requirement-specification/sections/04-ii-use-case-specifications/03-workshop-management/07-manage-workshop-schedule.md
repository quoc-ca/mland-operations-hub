### 3.7 Manage Workshop Schedule

| Use Case ID | UC-25 | Use Case Name | Manage Workshop Schedule |
| --- | --- | --- | --- |
| Created By |  | Created Date |  |
| Primary Actors | Manager | Secondary Actors | None |

#### Description

As a Manager, I want to create, update, publish, pause, and cancel workshop sessions so that branch schedules stay accurate without invalidating existing tickets.

#### Trigger

A Manager opens the session management screen.

#### Preconditions

1. The Manager is authenticated and active.
2. The location has configured active time slots.

#### Postconditions

1. The session is stored with its location, date, slot, capacity, and state.
2. Existing tickets and holds are never silently invalidated.

#### Normal Flow

1. Actor opens the session management screen.
2. Actor enters single or bulk session details with location, slot, date range, weekdays, and capacity.
3. System validates the slot, date, uniqueness, and capacity.
4. System saves sessions as Draft or Published and reports created and skipped sessions.
5. Actor changes capacity or transitions state to publish or pause sales.
6. System records an audit entry.

#### Alternative Flows

5a. Actor cancels a session with confirmed tickets or live holds → Session moves to Cancellation Requested and hands off to ticket settlement.

5b. Actor cancels a session without tickets or live holds → System cancels the session directly.

#### Exception Flows

3a. Slot start is in the past or over 90 days ahead → System rejects the creation.

3b. Session already exists for the location, date, and slot → Bulk generation skips it without overwriting; single creation is rejected.

5a. Actor attempts to change date, slot, or location → System refuses; actor cancels the session and creates another.

5b. Actor selects CANCELLED in direct state transition → System rejects it; actor uses the reasoned cancellation flow.

#### Business Rules

BR-21-01, BR-21-02

#### Non-Functional Requirements

—

#### Priority

—

#### Messages

—

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-21-01 | A session is unique for its location, date, and configured slot. |
| BR-21-02 | Only a Manager creates, changes, or cancels sessions. Mland is the source of truth for capacity. |
