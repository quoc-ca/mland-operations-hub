### 3.1 Workshop Management

#### 3.1.10 Configure Workshop Slot Capacity

<table>
<tr><td>Primary Actors</td><td>Manager</td><td>Secondary Actors</td><td>None</td></tr>
<tr><td>Description</td><td colspan="3">Allows the Manager to set the maximum seat capacity and participant limits for a workshop location/session.</td></tr>
<tr><td>Preconditions</td><td colspan="3">1. The Manager is authenticated and authorized.<br>2. The target branch and workshop session exist.<br>3. Capacity values use the configured participant-count policy.</td></tr>
<tr><td>Postconditions</td><td colspan="3">• The capacity configuration is saved for the selected location/session.<br>• Public and integration availability uses the new capacity after validation.<br>• Existing confirmed bookings are not deleted or silently reduced below their committed participant count.</td></tr>
<tr><td>Normal<br>Sequence/Flow</td><td colspan="3"><em>Configure Workshop Slot Capacity</em><br>1. The Manager selects a branch, date/session scope, or capacity template.<br>2. The system displays current capacity, held seats, confirmed participants, and remaining availability.<br>3. The Manager enters the maximum seat capacity and any participant limit.<br>4. The system validates non-negative values and compares the proposed capacity with existing holds and bookings.<br>5. The Manager confirms the change.<br>6. The system saves the capacity and recalculates availability.</td></tr>
<tr><td>Alternative<br>Sequences/Flows</td><td colspan="3"><em>Step 4 — Capacity is below committed participants</em><br>The system rejects the change or requires an approved exception; existing bookings remain protected.<br><br><em>Step 4 — Invalid or inconsistent participant limit</em><br>The system displays validation errors and asks the Manager to correct the values.<br><br><em>Step 6 — Save fails</em><br>The system displays an error and leaves the previous capacity unchanged.</td></tr>
<tr><td>Business Rule</td><td colspan="3">BR-24-01, BR-24-02</td></tr>
</table>

<table><tr style="background-color:#f4cccc"><th>ID</th><th>Rule Definition</th></tr><tr><td>BR-24-01</td><td>Group capacity is calculated from the total participant count of eligible bookings and holds, not from the number of booking rows.</td></tr><tr><td>BR-24-02</td><td>Capacity cannot be reduced below already committed participants without an authorized operational handling process.</td></tr></table>
