### 3.10 Configure Workshop Slot Capacity

#### Primary Actors

Manager

#### Secondary Actors

None

#### Description

Allows the Manager to set the maximum seat capacity and participant limits for a workshop location/session.

#### Preconditions

1. The Manager is authenticated and authorized.
2. The target branch and workshop session exist.
3. Capacity values use the configured participant-count policy.

#### Normal Flow

**Configure Workshop Slot Capacity**

1. The Manager selects a branch, date/session scope, or capacity template.
2. The system displays current capacity, held seats, confirmed participants, and remaining availability.
3. The Manager enters the maximum seat capacity and any participant limit.
4. The system validates non-negative values and compares the proposed capacity with existing holds and bookings.
5. The Manager confirms the change.
6. The system saves the capacity and recalculates availability.

#### Alternative Flows

**Step 4 — Capacity is below committed participants**

The system rejects the change or requires an approved exception; existing bookings remain protected.

**Step 4 — Invalid or inconsistent participant limit**

The system displays validation errors and asks the Manager to correct the values.

**Step 6 — Save fails**

The system displays an error and leaves the previous capacity unchanged.

#### Postconditions

• The capacity configuration is saved for the selected location/session.• Public and integration availability uses the new capacity after validation.• Existing confirmed bookings are not deleted or silently reduced below their committed participant count.

#### Business Rules

BR-24-01, BR-24-02

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-24-01 | Group capacity is calculated from the total participant count of eligible bookings and holds, not from the number of booking rows. |
| BR-24-02 | Capacity cannot be reduced below already committed participants without an authorized operational handling process. |
