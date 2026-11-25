### 3.6 Check In Participant

#### Primary Actors

Staff, Manager

#### Secondary Actors

None

#### Description

As an assigned Staff member or a Manager, I want to check in a ticket holder for a session by QR code or ticket code so that attendance is recorded per ticket and per session.

#### Preconditions

1. The actor is a Manager, or Staff who has the location in scope and is assigned to the session.
2. The session is published, sales-paused, or in progress.
3. The check-in opening time of the session has passed (60 minutes before the start by default).

#### Postconditions

- Attendance of the ticket for that session is recorded once.
- A refused scan changes nothing.

#### Normal Flow

**Check In Participant**

1. The actor opens the session.
2. The actor scans the ticket QR code or enters the ticket code.
3. The system validates the ticket, the session, the actor scope, and the check-in time.
4. The system records the attendance immediately and shows the result.

#### Alternative Flows

**Step 2 — Only a booking code is entered**

The system lists the tickets of that session for selection; the booking code alone never checks anyone in.

**Step 3 — Check-in has not opened**

The system refuses the check-in.

**Step 3 — Ticket belongs to another session or location**

The system refuses the check-in; an override cannot move the ticket.

**Step 3 — Ticket already checked in**

The system reports that the ticket is already checked in and records nothing new.

**Step 4 — A recorded check-in is wrong**

Only a Manager may reverse it, with a reason.

#### Business Rules

BR-20-01, BR-20-02, BR-20-03

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-20-01 | Attendance is recorded per ticket and per session. |
| BR-20-02 | Staff needs both location scope and session assignment; a Manager has global access. |
| BR-20-03 | Check-in works only online and only after the session check-in opening time; overrides require a reason. |
