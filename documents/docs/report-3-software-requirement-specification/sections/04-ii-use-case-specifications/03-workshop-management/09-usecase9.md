### 3.1 Workshop Management

#### 3.1.9 Configure Workshop Time Slot

<table>
<tr><td>Primary Actors</td><td>Manager</td><td>Secondary Actors</td><td>None</td></tr>
<tr><td>Description</td><td colspan="3">Allows the Manager to define the standard daily operating timeframes used when workshop sessions are created.</td></tr>
<tr><td>Preconditions</td><td colspan="3">1. The Manager is authenticated and authorized.<br>2. The platform is available.<br>3. The proposed time range uses the configured business timezone.</td></tr>
<tr><td>Postconditions</td><td colspan="3">• A valid standard time slot is created, updated, activated, or deactivated.<br>• Existing schedules are not silently changed by a configuration edit.<br>• New schedule availability uses the active time-slot configuration.</td></tr>
<tr><td>Normal<br>Sequence/Flow</td><td colspan="3"><em>Configure Workshop Time Slot</em><br>1. The Manager opens time-slot configuration.<br>2. The system displays the configured daily timeframes.<br>3. The Manager enters or edits a slot name, start time, end time, and active state.<br>4. The system validates the time range, ordering, overlap, and uniqueness rules.<br>5. The Manager confirms the configuration.<br>6. The system saves the time slot and displays the updated configuration.</td></tr>
<tr><td>Alternative<br>Sequences/Flows</td><td colspan="3"><em>Step 4 — End time is not after start time</em><br>The system rejects the slot and requests correction.<br><br><em>Step 4 — Timeframes overlap or duplicate another active slot</em><br>The system displays the conflict and does not save the change.<br><br><em>Step 5 — Existing schedules use the slot</em><br>The system warns the Manager and requires an explicit confirmation or prevents deactivation according to operational policy.<br><br><em>Step 6 — Save fails</em><br>The system displays an error and preserves the previous configuration.</td></tr>
<tr><td>Business Rule</td><td colspan="3">BR-23-01, BR-23-02</td></tr>
</table>

<table><tr style="background-color:#f4cccc"><th>ID</th><th>Rule Definition</th></tr><tr><td>BR-23-01</td><td>An active workshop time slot must have a valid start/end range and must not overlap another active standard slot.</td></tr><tr><td>BR-23-02</td><td>Changing a standard time slot must not silently rewrite existing workshop bookings or schedules.</td></tr></table>
