### 3.8 Assign Staff to Workshop Slot

#### Primary Actors

Manager

#### Secondary Actors

None

#### Description

Allows the Manager to assign qualified Staff members as instructors or goldsmith facilitators for a workshop slot.

#### Preconditions

1. The Manager is authenticated and authorized.
2. The workshop slot exists.
3. Candidate Staff accounts are active and eligible for workshop operations.

#### Normal Flow

Assign Staff to Workshop Slot
1. The Manager opens a workshop slot.
2. The system displays the current assignments and eligible Staff.
3. The Manager selects one or more Staff members and their operational role.
4. The system validates Staff status, role eligibility, time overlap, and assignment limits.
5. The Manager confirms the assignment.
6. The system saves the assignment and displays the updated slot.

#### Alternative Flows





Step 3 — No eligible Staff is available
The system informs the Manager and leaves the slot unassigned.

Step 4 — Staff has an overlapping assignment or is inactive
The system rejects that assignment and identifies the conflict.

Step 5 — Manager removes an existing assignment
The system removes it only after confirmation and records the change.

Step 6 — Save fails
The system displays an error and preserves the previous assignment state.

#### Postconditions

• The selected Staff assignment is saved for the workshop slot.• The assignment is visible to authorized operational users.• Conflicting or inactive assignments are not saved.

#### Business Rules

BR-22-01, BR-22-02

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-22-01 | Only active Staff accounts with the required operational role may be assigned to a workshop slot. |
| BR-22-02 | A Staff member must not be assigned to overlapping workshop slots unless an authorized exception is supported. |
