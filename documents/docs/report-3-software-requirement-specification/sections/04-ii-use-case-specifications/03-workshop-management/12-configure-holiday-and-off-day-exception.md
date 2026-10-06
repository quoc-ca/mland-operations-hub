### 3.12 Configure Holiday and Off-Day Exception

#### Primary Actors

Manager

#### Secondary Actors

None

#### Description

Allows the Manager to configure holidays, closures, off-days, capacity overrides, and other calendar exceptions that affect workshop availability.

#### Preconditions

1. The Manager is authenticated and authorized.
2. The exception date and applicable location/session scope are valid.
3. The Manager has reviewed any existing bookings affected by the exception.

#### Normal Flow

**Configure Holiday and Off-Day Exception**

1. The Manager opens calendar exception configuration.
2. The Manager selects a date and optionally a branch or session scope.
3. The Manager sets the exception as closed, open with an override, or otherwise operationally defined.
4. The system validates the date, scope, overlap, and impact on existing schedules/bookings.
5. The Manager confirms the exception.
6. The system saves the exception and recalculates affected availability.

#### Alternative Flows

**Step 3 — Exception conflicts with another active exception**

The system displays the conflict and asks the Manager to resolve it.

**Step 4 — Existing booking is affected**

The system warns the Manager and requires an authorized operational decision before a closure can be applied.

**Step 5 — Manager cancelsNo exception is saved.**

**Step 6 — Save fails**

The system displays an error and leaves the previous calendar state unchanged.

#### Postconditions

• The calendar exception is saved with its scope, active state, and optional capacity override.• A closed date/session is removed from new public booking availability.• Existing confirmed bookings are not silently cancelled.

#### Business Rules

BR-26-01, BR-26-02

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-26-01 | An active closure or off-day exception prevents new bookings for its configured date/location/session scope. |
| BR-26-02 | A calendar exception must not silently cancel or invalidate existing confirmed bookings. |
