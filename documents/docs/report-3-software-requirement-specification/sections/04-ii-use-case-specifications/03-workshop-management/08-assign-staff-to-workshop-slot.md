### 3.8 Assign Staff to Workshop Slot

| Attribute | Value |
|---|---|
| Primary Actors | Manager |
| Secondary Actors | None |
| Description | Allows the Manager to assign qualified Staff members as instructors or goldsmith facilitators for a workshop slot. |
| Preconditions | 1. The Manager is authenticated and authorized.<br>2. The workshop slot exists.<br>3. Candidate Staff accounts are active and eligible for workshop operations. |
| Postconditions | • The selected Staff assignment is saved for the workshop slot.• The assignment is visible to authorized operational users.• Conflicting or inactive assignments are not saved. |
| Normal Sequence/Flow | Assign Staff to Workshop Slot<br>1. The Manager opens a workshop slot.<br>2. The system displays the current assignments and eligible Staff.<br>3. The Manager selects one or more Staff members and their operational role.<br>4. The system validates Staff status, role eligibility, time overlap, and assignment limits.<br>5. The Manager confirms the assignment.<br>6. The system saves the assignment and displays the updated slot. |
| Alternative Sequences/Flows | <br><br>Step 3 — No eligible Staff is availableThe system informs the Manager and leaves the slot unassigned.Step 4 — Staff has an overlapping assignment or is inactiveThe system rejects that assignment and identifies the conflict.Step 5 — Manager removes an existing assignmentThe system removes it only after confirmation and records the change.Step 6 — Save failsThe system displays an error and preserves the previous assignment state. |
| Business Rule | BR-22-01, BR-22-02 |

| ID | Rule Definition |
|---|---|
| BR-22-01 | Only active Staff accounts with the required operational role may be assigned to a workshop slot. |
| BR-22-02 | A Staff member must not be assigned to overlapping workshop slots unless an authorized exception is supported. |
