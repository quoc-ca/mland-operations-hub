### 3.10 Configure Workshop Slot Capacity

#### Primary Actors

Manager

#### Secondary Actors

None

#### Description

Allows the Manager to set the seat capacity of an individual workshop session. A time slot itself carries no capacity.

#### Preconditions

1. The Manager is authenticated and active.
2. The target session exists.

#### Postconditions

- The new capacity applies to that session only.
- Confirmed tickets and live holds are never reduced.

#### Normal Flow

**Configure Workshop Slot Capacity**

1. The Manager opens a session.
2. The system displays capacity, confirmed seats, and held seats.
3. The Manager enters the new capacity.
4. The system validates it and saves it with an audit entry.

#### Alternative Flows

**Step 4 — Capacity below one**

The system rejects the value; the Manager pauses sales to stop bookings.

**Step 4 — Capacity below confirmed seats plus live holds**

The system always rejects the change.

#### Business Rules

BR-24-01, BR-24-02

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-24-01 | Capacity counts participants of eligible tickets and holds. |
| BR-24-02 | Session capacity is at least one and cannot be reduced below committed seats. Participant limits belong to the package booking line. |
