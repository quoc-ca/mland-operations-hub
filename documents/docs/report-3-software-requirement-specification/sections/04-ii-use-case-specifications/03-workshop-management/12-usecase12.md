### 3.1 Workshop Management

#### 3.1.12 Configure Holiday and Off-Day Exception

<table>
<tr><td>Primary Actors</td><td>Manager</td><td>Secondary Actors</td><td>None</td></tr>
<tr><td>Description</td><td colspan="3">Allows the Manager to configure holidays, closures, off-days, capacity overrides, and other calendar exceptions that affect workshop availability.</td></tr>
<tr><td>Preconditions</td><td colspan="3">1. The Manager is authenticated and authorized.<br>2. The exception date and applicable location/session scope are valid.<br>3. The Manager has reviewed any existing bookings affected by the exception.</td></tr>
<tr><td>Postconditions</td><td colspan="3">• The calendar exception is saved with its scope, active state, and optional capacity override.<br>• A closed date/session is removed from new public booking availability.<br>• Existing confirmed bookings are not silently cancelled.</td></tr>
<tr><td>Normal<br>Sequence/Flow</td><td colspan="3"><em>Configure Holiday and Off-Day Exception</em><br>1. The Manager opens calendar exception configuration.<br>2. The Manager selects a date and optionally a branch or session scope.<br>3. The Manager sets the exception as closed, open with an override, or otherwise operationally defined.<br>4. The system validates the date, scope, overlap, and impact on existing schedules/bookings.<br>5. The Manager confirms the exception.<br>6. The system saves the exception and recalculates affected availability.</td></tr>
<tr><td>Alternative<br>Sequences/Flows</td><td colspan="3"><em>Step 3 — Exception conflicts with another active exception</em><br>The system displays the conflict and asks the Manager to resolve it.<br><br><em>Step 4 — Existing booking is affected</em><br>The system warns the Manager and requires an authorized operational decision before a closure can be applied.<br><br><em>Step 5 — Manager cancels</em><br>No exception is saved.<br><br><em>Step 6 — Save fails</em><br>The system displays an error and leaves the previous calendar state unchanged.</td></tr>
<tr><td>Business Rule</td><td colspan="3">BR-26-01, BR-26-02</td></tr>
</table>

<table><tr style="background-color:#f4cccc"><th>ID</th><th>Rule Definition</th></tr><tr><td>BR-26-01</td><td>An active closure or off-day exception prevents new bookings for its configured date/location/session scope.</td></tr><tr><td>BR-26-02</td><td>A calendar exception must not silently cancel or invalidate existing confirmed bookings.</td></tr></table>
