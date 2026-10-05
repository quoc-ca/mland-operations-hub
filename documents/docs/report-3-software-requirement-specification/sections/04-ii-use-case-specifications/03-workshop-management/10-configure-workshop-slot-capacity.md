### 3.10 Configure Workshop Slot Capacity

| Attribute | Value |
|---|---|
| Primary Actors | Manager<br>**Secondary Actors:** None |
| Description | Allows the Manager to set the maximum seat capacity and participant limits for a workshop location/session. |
| Preconditions | 1. The Manager is authenticated and authorized.<br>2. The target branch and workshop session exist.<br>3. Capacity values use the configured participant-count policy. |
| Postconditions | • The capacity configuration is saved for the selected location/session.• Public and integration availability uses the new capacity after validation.• Existing confirmed bookings are not deleted or silently reduced below their committed participant count. |
| Normal Sequence/Flow | Configure Workshop Slot Capacity<br>1. The Manager selects a branch, date/session scope, or capacity template.<br>2. The system displays current capacity, held seats, confirmed participants, and remaining availability.<br>3. The Manager enters the maximum seat capacity and any participant limit.<br>4. The system validates non-negative values and compares the proposed capacity with existing holds and bookings.<br>5. The Manager confirms the change.<br>6. The system saves the capacity and recalculates availability. |
| Alternative Sequences/Flows | <br><br>Step 4 — Capacity is below committed participantsThe system rejects the change or requires an approved exception; existing bookings remain protected.Step 4 — Invalid or inconsistent participant limitThe system displays validation errors and asks the Manager to correct the values.Step 6 — Save failsThe system displays an error and leaves the previous capacity unchanged. |
| Business Rule | BR-24-01, BR-24-02 |

| ID | Rule Definition |
|---|---|
| BR-24-01 | Group capacity is calculated from the total participant count of eligible bookings and holds, not from the number of booking rows. |
| BR-24-02 | Capacity cannot be reduced below already committed participants without an authorized operational handling process. |
