### 3.12 Configure Holiday and Off-Day Exception

| Attribute | Value |
|---|---|
| Primary Actors | Manager |
| Secondary Actors | None |
| Description | Allows the Manager to configure holidays, closures, off-days, capacity overrides, and other calendar exceptions that affect workshop availability. |
| Preconditions | 1. The Manager is authenticated and authorized.<br>2. The exception date and applicable location/session scope are valid.<br>3. The Manager has reviewed any existing bookings affected by the exception. |
| Postconditions | • The calendar exception is saved with its scope, active state, and optional capacity override.• A closed date/session is removed from new public booking availability.• Existing confirmed bookings are not silently cancelled. |
| Normal Sequence/Flow | Configure Holiday and Off-Day Exception<br>1. The Manager opens calendar exception configuration.<br>2. The Manager selects a date and optionally a branch or session scope.<br>3. The Manager sets the exception as closed, open with an override, or otherwise operationally defined.<br>4. The system validates the date, scope, overlap, and impact on existing schedules/bookings.<br>5. The Manager confirms the exception.<br>6. The system saves the exception and recalculates affected availability. |
| Alternative Sequences/Flows | <br><br>Step 3 — Exception conflicts with another active exceptionThe system displays the conflict and asks the Manager to resolve it.Step 4 — Existing booking is affectedThe system warns the Manager and requires an authorized operational decision before a closure can be applied.Step 5 — Manager cancelsNo exception is saved.Step 6 — Save failsThe system displays an error and leaves the previous calendar state unchanged. |
| Business Rule | BR-26-01, BR-26-02 |

| ID | Rule Definition |
|---|---|
| BR-26-01 | An active closure or off-day exception prevents new bookings for its configured date/location/session scope. |
| BR-26-02 | A calendar exception must not silently cancel or invalidate existing confirmed bookings. |
