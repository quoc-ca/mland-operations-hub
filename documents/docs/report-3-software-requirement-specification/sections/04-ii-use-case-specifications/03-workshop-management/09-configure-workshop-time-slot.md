### 3.9 Configure Workshop Time Slot

#### Primary Actors

Manager

#### Secondary Actors

None

#### Description

Allows the Manager to define the standard daily operating timeframes used when workshop sessions are created.

#### Preconditions

1. The Manager is authenticated and authorized.
2. The platform is available.
3. The proposed time range uses the configured business timezone.

#### Normal Flow

Configure Workshop Time Slot
1. The Manager opens time-slot configuration.
2. The system displays the configured daily timeframes.
3. The Manager enters or edits a slot name, start time, end time, and active state.
4. The system validates the time range, ordering, overlap, and uniqueness rules.
5. The Manager confirms the configuration.
6. The system saves the time slot and displays the updated configuration.

#### Alternative Flows





Step 4 — End time is not after start time
The system rejects the slot and requests correction.

Step 4 — Timeframes overlap or duplicate another active slot
The system displays the conflict and does not save the change.

Step 5 — Existing schedules use the slot
The system warns the Manager and requires an explicit confirmation or prevents deactivation according to operational policy.

Step 6 — Save fails
The system displays an error and preserves the previous configuration.

#### Postconditions

• A valid standard time slot is created, updated, activated, or deactivated.• Existing schedules are not silently changed by a configuration edit.• New schedule availability uses the active time-slot configuration.

#### Business Rules

BR-23-01, BR-23-02

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-23-01 | An active workshop time slot must have a valid start/end range and must not overlap another active standard slot. |
| BR-23-02 | Changing a standard time slot must not silently rewrite existing workshop bookings or schedules. |
