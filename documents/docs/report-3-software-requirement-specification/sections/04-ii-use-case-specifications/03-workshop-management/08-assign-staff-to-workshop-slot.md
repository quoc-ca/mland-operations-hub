### 3.8 Assign Staff to Workshop Slot

#### Primary Actors

Manager

#### Secondary Actors

None

#### Description

Allows the Manager to revoke or restore the assignment of Staff to a workshop session. Active Staff of the location are assigned to its sessions automatically.

#### Preconditions

1. The Manager is authenticated and active.
2. The session is Draft, Published, Sales Paused, or In Progress.
3. The assignee is an active Staff account with the session location in scope.

#### Postconditions

- The assignment change is saved with history and takes effect immediately on Staff access.

#### Normal Flow

**Assign Staff to Workshop Slot**

1. The Manager opens a session.
2. The system displays the assigned Staff.
3. The Manager assigns or revokes a Staff member.
4. The system validates the Staff account, location scope, and session state.
5. The system saves the change and records it.

#### Alternative Flows

**Step 4 — Staff is not eligible**

The system rejects the assignment.

**Step 5 — Removing the last assignment after check-in has opened**

The system asks for confirmation.

**Automatic assignment**

The system assigns active in-scope Staff to new and existing sessions; an assignment the Manager revoked is never added back automatically.

#### Business Rules

BR-22-01, BR-22-02

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-22-01 | Only an active Staff account with the location in scope may be assigned. There is no operational role, time-overlap check, or assignment limit. |
| BR-22-02 | Only a Manager assigns or revokes Staff; assignment gives Staff access to the session. |
