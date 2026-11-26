### 3.9 Configure Holiday and Off-Day Exception

> **Retired (confirmed 2026-11-25):** use case 30 is not part of the platform; no specification or implementation exists for it. The text below is kept only as history and must not be implemented.

| Use Case ID | UC-30 | Use Case Name | Configure Holiday and Off-Day Exception |
| --- | --- | --- | --- |
| Created By |  | Created Date |  |
| Primary Actors | Manager | Secondary Actors | None |

#### Description

As a Manager, I want to configure holidays, closures, off-days, and capacity overrides so that workshop availability reflects calendar exceptions.

#### Trigger

A Manager opens the calendar exception configuration screen.

#### Preconditions

1. The Manager is authenticated and authorized.
2. The exception date and applicable location or session scope are valid.
3. The Manager has reviewed any existing bookings affected by the exception.

#### Postconditions

1. The calendar exception is saved with its scope and active state.
2. A closed date or session is removed from new booking availability.
3. Existing confirmed bookings are not silently cancelled.

#### Normal Flow

1. Actor opens calendar exception configuration.
2. Actor selects a date and optionally a branch or session scope.
3. Actor sets the exception as closed, open with an override, or operationally defined.
4. System validates the date, scope, overlap, and impact on existing schedules and bookings.
5. Actor confirms the exception.
6. System saves the exception and recalculates affected availability.

#### Alternative Flows

4a. Existing booking is affected → System warns the Manager and requires an operational decision before applying a closure.

#### Exception Flows

3a. Exception conflicts with another active exception → System displays the conflict and asks the Manager to resolve it.

5a. Manager cancels → No exception is saved.

6a. Save fails → System displays an error and leaves the previous calendar state unchanged.

#### Business Rules

BR-26-01, BR-26-02

#### Non-Functional Requirements

—

#### Priority

—

#### Messages

—

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-26-01 | An active closure or off-day exception prevents new bookings for its configured date/location/session scope. |
| BR-26-02 | A calendar exception must not silently cancel or invalidate existing confirmed bookings. |
