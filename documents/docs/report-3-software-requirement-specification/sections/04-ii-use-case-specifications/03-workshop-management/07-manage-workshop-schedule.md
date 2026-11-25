### 3.7 Manage Workshop Schedule

#### Primary Actors

Manager

#### Secondary Actors

None

#### Description

Allows the Manager to create workshop sessions, one at a time or in bulk, change their capacity and check-in opening, publish or pause them, and cancel them.

#### Preconditions

1. The Manager is authenticated and active.
2. The location has configured active time slots.

#### Postconditions

- The session is stored with its location, date, slot, capacity, and state.
- Existing tickets and holds are never silently invalidated.

#### Normal Flow

**Manage Workshop Schedule**

1. The Manager opens the session management screen.
2. The Manager creates one session, or generates sessions for a location, slots, date range, and weekdays, entering the capacity.
3. The system validates the slot, the date, uniqueness, and the capacity.
4. The system saves the sessions as Draft by default, or Published when chosen, and reports created and skipped sessions.
5. The Manager changes the state or capacity of a session when needed.
6. The system records an audit entry. Enabled partner availability is published automatically.

#### Alternative Flows

**Step 3 — Slot start is in the past or more than 90 days ahead**

The system rejects the creation.

**Step 3 — A session already exists for the location, date, and slot**

Bulk generation skips it without overwriting; single creation is rejected.

**Step 5 — Change of date, slot, or location**

The system refuses the change. The Manager cancels the session and creates another.

**Step 5 — Cancellation of a session with confirmed tickets or live holds**

The session moves to Cancellation Requested and is handed to ticket settlement. A session without them is cancelled directly.

#### Business Rules

BR-21-01, BR-21-02

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-21-01 | A session is unique for its location, date, and configured slot. |
| BR-21-02 | Only a Manager creates, changes, or cancels sessions. Mland is the source of truth for capacity. |
