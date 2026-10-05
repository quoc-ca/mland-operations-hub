### 3.1 Workshop Management

#### 3.1.8 Assign Staff to Workshop Slot

<table>
<tr><td>Primary Actors</td><td>Manager</td><td>Secondary Actors</td><td>None</td></tr>
<tr><td>Description</td><td colspan="3">Allows the Manager to assign qualified Staff members as instructors or goldsmith facilitators for a workshop slot.</td></tr>
<tr><td>Preconditions</td><td colspan="3">1. The Manager is authenticated and authorized.<br>2. The workshop slot exists.<br>3. Candidate Staff accounts are active and eligible for workshop operations.</td></tr>
<tr><td>Postconditions</td><td colspan="3">• The selected Staff assignment is saved for the workshop slot.<br>• The assignment is visible to authorized operational users.<br>• Conflicting or inactive assignments are not saved.</td></tr>
<tr><td>Normal<br>Sequence/Flow</td><td colspan="3"><em>Assign Staff to Workshop Slot</em><br>1. The Manager opens a workshop slot.<br>2. The system displays the current assignments and eligible Staff.<br>3. The Manager selects one or more Staff members and their operational role.<br>4. The system validates Staff status, role eligibility, time overlap, and assignment limits.<br>5. The Manager confirms the assignment.<br>6. The system saves the assignment and displays the updated slot.</td></tr>
<tr><td>Alternative<br>Sequences/Flows</td><td colspan="3"><em>Step 3 — No eligible Staff is available</em><br>The system informs the Manager and leaves the slot unassigned.<br><br><em>Step 4 — Staff has an overlapping assignment or is inactive</em><br>The system rejects that assignment and identifies the conflict.<br><br><em>Step 5 — Manager removes an existing assignment</em><br>The system removes it only after confirmation and records the change.<br><br><em>Step 6 — Save fails</em><br>The system displays an error and preserves the previous assignment state.</td></tr>
<tr><td>Business Rule</td><td colspan="3">BR-22-01, BR-22-02</td></tr>
</table>

<table><tr style="background-color:#f4cccc"><th>ID</th><th>Rule Definition</th></tr><tr><td>BR-22-01</td><td>Only active Staff accounts with the required operational role may be assigned to a workshop slot.</td></tr><tr><td>BR-22-02</td><td>A Staff member must not be assigned to overlapping workshop slots unless an authorized exception is supported.</td></tr></table>
