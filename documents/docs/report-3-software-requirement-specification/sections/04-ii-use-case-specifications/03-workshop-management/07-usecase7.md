### 3.1 Workshop Management

#### 3.1.7 Manage Workshop Schedule

<table>
<tr><td>Primary Actors</td><td>Manager</td><td>Secondary Actors</td><td>External Registration System</td></tr>
<tr><td>Description</td><td colspan="3">Allows the Manager to create, view, modify, or cancel workshop calendar sessions, shifts, and branch allocations.</td></tr>
<tr><td>Preconditions</td><td colspan="3">1. The Manager is authenticated and authorized.<br>2. Branches, standard time slots, and required operational configuration are available.<br>3. The requested schedule change is within the configured planning rules.</td></tr>
<tr><td>Postconditions</td><td colspan="3">• The schedule is created or updated with its branch, date, session, and operational state.<br>• Existing bookings are protected from an unsafe schedule change.<br>• An enabled schedule update may be sent to Klook through UC27.</td></tr>
<tr><td>Normal<br>Sequence/Flow</td><td colspan="3"><em>Manage Workshop Schedule</em><br>1. The Manager opens workshop schedule management.<br>2. The system displays existing schedules and their capacity/booking state.<br>3. The Manager creates or selects a schedule.<br>4. The Manager enters or edits the branch, date, session, and open/closed state.<br>5. The system validates conflicts, exceptions, capacity configuration, and existing bookings.<br>6. The Manager submits the change.<br>7. The system saves the schedule and recalculates its public availability.<br>8. The system records the change and queues an enabled Klook synchronization.</td></tr>
<tr><td>Alternative<br>Sequences/Flows</td><td colspan="3"><em>Step 5 — Schedule conflicts or required configuration is missing</em><br>The system rejects the change and identifies the conflicting or missing data.<br><br><em>Step 5 — Existing confirmed bookings would be invalidated</em><br>The system blocks the change or requires an authorized operational handling path before saving.<br><br><em>Step 6 — Manager cancels</em><br>No schedule change is saved.<br><br><em>Step 8 — Klook synchronization fails</em><br>The Mland schedule remains the source-of-truth update and the failed synchronization is queued for retry.</td></tr>
<tr><td>Business Rule</td><td colspan="3">BR-21-01, BR-21-02</td></tr>
</table>

<table><tr style="background-color:#f4cccc"><th>ID</th><th>Rule Definition</th></tr><tr><td>BR-21-01</td><td>A schedule must identify one branch, date, and configured daily session and must not conflict with an active closure or another schedule.</td></tr><tr><td>BR-21-02</td><td>Mland remains the source of truth for workshop schedule and capacity even when updates are synchronized to Klook.</td></tr></table>
