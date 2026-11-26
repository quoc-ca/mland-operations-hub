### 3.9 Configure Workshop Time Slot

| Use Case ID | UC-27 | Use Case Name | Configure Workshop Time Slot |
| --- | --- | --- | --- |
| Created By |  | Created Date |  |
| Primary Actors | Manager | Secondary Actors | None |

#### Description

As a Manager, I want to define daily time slots per location so that workshop sessions can be scheduled consistently.

#### Trigger

A Manager opens the time-slot configuration screen.

#### Preconditions

1. The Manager is authenticated and authorized.
2. The platform is available.
3. The proposed time range uses the configured business timezone.

#### Postconditions

1. A valid standard time slot is created, updated, activated, or deactivated.
2. Existing schedules are not silently changed by a configuration edit.
3. New schedule availability uses the active time-slot configuration.

#### Normal Flow

1. Actor opens time-slot configuration.
2. System displays the configured daily timeframes.
3. Actor enters or edits a slot name, start time, end time, and active state.
4. System validates the time range, ordering, overlap, and uniqueness rules.
5. Actor confirms the configuration.
6. System saves the time slot and displays the updated configuration.

#### Alternative Flows

5a. Existing sessions use the slot → Change applies only to future sessions; existing sessions keep their times and slots are never deleted.

#### Exception Flows

4a. End time is not after start time → System rejects the slot and requests correction.

4b. Timeframes overlap or duplicate another active slot → System displays the conflict and does not save the change.

6a. Save fails → System displays an error and preserves the previous configuration.

#### Business Rules

BR-23-01, BR-23-02

#### Non-Functional Requirements

—

#### Priority

—

#### Messages

—

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-23-01 | An active workshop time slot must have a valid start/end range and must not overlap another active slot of the same location, and must start and end on the same day. |
| BR-23-02 | Changing a standard time slot must not silently rewrite existing workshop bookings or schedules. |
