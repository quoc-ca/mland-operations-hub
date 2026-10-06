### 3.13 Synchronize Workshop Schedule

#### Primary Actors

Manager

#### Secondary Actors

External Registration System

#### Description

Exchanges enabled workshop availability updates with Klook while Mland remains the source of truth for capacity and holds.

#### Preconditions

1. Klook credentials and endpoint configuration are approved and available.
2. Mland has a current schedule, capacity, and availability state.
3. The schedule or synchronization job is enabled.

#### Normal Flow

Synchronize Workshop Schedule
1. The Manager saves a schedule/capacity change or the synchronization job starts.
2. The system selects the affected branch, date, session, capacity, and availability state.
3. The system builds the approved Klook availability payload.
4. The system sends the update to Klook.
5. The system validates the response and records the synchronization status and correlation details.
6. Klook uses Mland availability/hold before confirming an external booking.

#### Alternative Flows





Step 2 — No enabled integration configuration
The system records that synchronization is skipped and keeps Mland operations available.

Step 4 — Klook rejects or cannot receive the update
The system records the failure and schedules a retry without changing Mland capacity.

Step 5 — Response is invalid or duplicated
The system does not apply an ambiguous response and records the issue for reconciliation.

Step 6 — Klook requests confirmation without an Mland hold
The system rejects the confirmation path because Mland must authorize availability first.

#### Postconditions

• An enabled availability update is sent to Klook and the synchronization result is recorded.• Mland capacity and holds remain authoritative.• A failed synchronization is available for retry and does not overwrite Mland availability.

#### Business Rules

GBR-11, BR-27-01, BR-27-02

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| GBR-11 | Klook obtains Mland availability/hold before it confirms a workshop booking; Mland remains the capacity source of truth. |
| BR-27-01 | Only enabled and approved Klook integration configuration may send workshop availability updates. |
| BR-27-02 | A synchronization failure must not alter Mland's schedule, capacity, or hold state. |
