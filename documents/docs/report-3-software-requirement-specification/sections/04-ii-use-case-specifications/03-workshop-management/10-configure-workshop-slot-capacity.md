### 3.10 Configure Workshop Slot Capacity

| Use Case ID | UC-28 | Use Case Name | Configure Workshop Slot Capacity |
| --- | --- | --- | --- |
| Created By |  | Created Date |  |
| Primary Actors | Manager | Secondary Actors | None |

#### Description

As a Manager, I want to set the seat capacity of an individual workshop session so that booking limits reflect actual branch capacity.

#### Trigger

A Manager opens a workshop session to update its seat capacity.

#### Preconditions

1. The Manager is authenticated and active.
2. The target session exists.

#### Postconditions

1. The new capacity applies to that session only.
2. Confirmed tickets and live holds are never reduced.

#### Normal Flow

1. Actor opens a workshop session.
2. System displays capacity, confirmed seats, and held seats.
3. Actor enters the new capacity.
4. System validates the value and saves it with an audit entry.

#### Alternative Flows

None.

#### Exception Flows

4a. Capacity is below one → System rejects the value; Manager pauses sales to stop bookings.

4b. Capacity is below confirmed seats plus live holds → System always rejects the change.

#### Business Rules

BR-24-01, BR-24-02

#### Non-Functional Requirements

—

#### Priority

—

#### Messages

—

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-24-01 | Capacity counts participants of eligible tickets and holds. |
| BR-24-02 | Session capacity is at least one and cannot be reduced below committed seats. Participant limits belong to the package booking line. |
