### 3.7 Manage Workshop Schedule

| Attribute | Value |
|---|---|
| Primary Actors | Manager<br>**Secondary Actors:** External Registration System |
| Description | Allows the Manager to create, view, modify, or cancel workshop calendar sessions, shifts, and branch allocations. |
| Preconditions | 1. The Manager is authenticated and authorized.<br>2. Branches, standard time slots, and required operational configuration are available.<br>3. The requested schedule change is within the configured planning rules. |
| Postconditions | • The schedule is created or updated with its branch, date, session, and operational state.• Existing bookings are protected from an unsafe schedule change.• An enabled schedule update may be sent to Klook through UC27. |
| Normal Sequence/Flow | Manage Workshop Schedule<br>1. The Manager opens workshop schedule management.<br>2. The system displays existing schedules and their capacity/booking state.<br>3. The Manager creates or selects a schedule.<br>4. The Manager enters or edits the branch, date, session, and open/closed state.<br>5. The system validates conflicts, exceptions, capacity configuration, and existing bookings.<br>6. The Manager submits the change.<br>7. The system saves the schedule and recalculates its public availability.<br>8. The system records the change and queues an enabled Klook synchronization. |
| Alternative Sequences/Flows | <br><br>Step 5 — Schedule conflicts or required configuration is missingThe system rejects the change and identifies the conflicting or missing data.Step 5 — Existing confirmed bookings would be invalidatedThe system blocks the change or requires an authorized operational handling path before saving.Step 6 — Manager cancelsNo schedule change is saved.Step 8 — Klook synchronization failsThe Mland schedule remains the source-of-truth update and the failed synchronization is queued for retry. |
| Business Rule | BR-21-01, BR-21-02 |

| ID | Rule Definition |
|---|---|
| BR-21-01 | A schedule must identify one branch, date, and configured daily session and must not conflict with an active closure or another schedule. |
| BR-21-02 | Mland remains the source of truth for workshop schedule and capacity even when updates are synchronized to Klook. |
