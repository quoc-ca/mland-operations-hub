### 3.7 Manage Workshop Schedule

| Use Case ID | UC-25 | Use Case Name | Manage Workshop Schedule |
| --- | --- | --- | --- |
| Created By |  | Created Date |  |
| Primary Actors | Manager | Secondary Actors | None |

#### Description

As a Manager, I want to manage branches, time slots, sessions, seat capacity, and Staff assignments so that branch schedules stay accurate.

#### Trigger

A Manager opens workshop schedule or branch management.

#### Preconditions

1. The Manager is authenticated and active.
2. A session requires a configured active time slot at its location.
3. A Staff assignee is an active account with the session location in scope.

#### Postconditions

1. Branches, time slots, sessions, capacity, and assignments are saved.
2. Existing sessions, tickets, and holds are never silently changed or invalidated.
3. Assignment changes are kept in history and take effect immediately on Staff access.

#### Normal Flow

**Manage Branches**

1. Actor opens the branch list.
2. System displays active and inactive branches.
3. Actor enters a new branch code, name, and address.
4. System validates the details and creates the branch.
5. Actor edits the name, address, or active status of a branch.
6. System saves the change with an audit entry.

**Configure Time Slots**

1. Actor opens time-slot configuration.
2. System displays the configured daily timeframes of the location.
3. Actor enters or edits the slot name, start time, end time, and active state.
4. System validates the time range, overlap, and uniqueness.
5. System saves the time slot.

**Manage Sessions**

1. Actor opens session management.
2. Actor enters single or bulk session details with location, slot, dates, weekdays, and capacity.
3. System validates the slot, date, uniqueness, and capacity.
4. System saves sessions as Draft or Published and reports created and skipped sessions.
5. Actor publishes a session or pauses its sales.
6. System records an audit entry.

**Change Session Capacity**

1. Actor opens a session.
2. System displays capacity, confirmed seats, and held seats.
3. Actor enters the new capacity.
4. System saves the capacity with an audit entry.

**Manage Staff Assignments**

1. Actor opens a session.
2. System displays the assigned Staff.
3. Actor assigns or revokes a Staff member.
4. System saves the change and records it in history.

#### Alternative Flows

B5a. Actor deactivates a branch → System stops planning new slots and sessions there.

T3a. Existing sessions use the edited slot → Change applies only to future sessions; slots are never deleted.

S5a. Actor cancels a session with confirmed tickets or live holds → Session moves to Cancellation Requested and hands off to ticket settlement.

S5b. Actor cancels a session without tickets or live holds → System cancels the session directly.

A3a. System assigns active in-scope Staff automatically → Assignments revoked by a Manager are never added back automatically.

A3b. Actor removes the last assignment after check-in has opened → System asks for confirmation.

#### Exception Flows

B4a. Code or name is missing, or the code already exists → System rejects the branch.

B5b. Actor changes the branch code → System rejects the change.

B5c. Branch still has a session that has not ended → System refuses deactivation.

T4a. End time is not after start time → System rejects the slot.

T4b. Slot overlaps or duplicates another active slot → System shows the conflict and saves nothing.

T5a. Save fails → System keeps the previous configuration.

S3a. Slot start is in the past or over 90 days ahead → System rejects the creation.

S3b. Session already exists for the location, date, and slot → Bulk generation skips it; single creation is rejected.

S5c. Actor attempts to change date, slot, or location → System refuses; actor cancels the session and creates another.

S5d. Actor selects Cancelled as a direct state change → System rejects it and requires the reasoned cancellation flow.

C4a. Capacity is below one → System rejects the value; Manager pauses sales to stop bookings.

C4b. Capacity is below confirmed seats plus live holds → System rejects the change.

A4a. Staff is not eligible or the session state disallows assignment → System rejects the change.

#### Business Rules

BR-21-01, BR-21-02, BR-22-01, BR-22-02, BR-23-01, BR-23-02, BR-24-01, BR-24-02, BR-93-01, BR-93-02

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
| BR-22-01 | Only an active Staff account with the location in scope may be assigned. There is no operational role, time-overlap check, or assignment limit. |
| BR-22-02 | Only a Manager assigns or revokes Staff; assignment gives Staff access to the session. |
| BR-23-01 | An active workshop time slot must have a valid start/end range and must not overlap another active slot of the same location, and must start and end on the same day. |
| BR-23-02 | Changing a standard time slot must not silently rewrite existing workshop bookings or schedules. |
| BR-24-01 | Capacity counts participants of eligible tickets and holds. |
| BR-24-02 | Session capacity is at least one and cannot be reduced below committed seats. Participant limits belong to the package booking line. |
| BR-93-01 | A branch code is required, unique, and cannot be changed after creation. |
| BR-93-02 | A branch cannot be deactivated while it has a session that has not ended. |
