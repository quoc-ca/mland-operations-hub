### 3.9 Configure Workshop Time Slot

| Attribute | Value |
|---|---|
| Primary Actors | Manager |
| Secondary Actors | None |
| Description | Allows the Manager to define the standard daily operating timeframes used when workshop sessions are created. |
| Preconditions | 1. The Manager is authenticated and authorized.<br>2. The platform is available.<br>3. The proposed time range uses the configured business timezone. |
| Postconditions | • A valid standard time slot is created, updated, activated, or deactivated.• Existing schedules are not silently changed by a configuration edit.• New schedule availability uses the active time-slot configuration. |
| Normal Sequence/Flow | Configure Workshop Time Slot<br>1. The Manager opens time-slot configuration.<br>2. The system displays the configured daily timeframes.<br>3. The Manager enters or edits a slot name, start time, end time, and active state.<br>4. The system validates the time range, ordering, overlap, and uniqueness rules.<br>5. The Manager confirms the configuration.<br>6. The system saves the time slot and displays the updated configuration. |
| Alternative Sequences/Flows | <br><br>Step 4 — End time is not after start timeThe system rejects the slot and requests correction.Step 4 — Timeframes overlap or duplicate another active slotThe system displays the conflict and does not save the change.Step 5 — Existing schedules use the slotThe system warns the Manager and requires an explicit confirmation or prevents deactivation according to operational policy.Step 6 — Save failsThe system displays an error and preserves the previous configuration. |
| Business Rule | BR-23-01, BR-23-02 |

| ID | Rule Definition |
|---|---|
| BR-23-01 | An active workshop time slot must have a valid start/end range and must not overlap another active standard slot. |
| BR-23-02 | Changing a standard time slot must not silently rewrite existing workshop bookings or schedules. |
