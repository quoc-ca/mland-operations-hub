### 3.6 Check In Participant

| Use Case ID | UC-24 | Use Case Name | Check In Participant |
| --- | --- | --- | --- |
| Created By |  | Created Date |  |
| Primary Actors | Staff, Manager | Secondary Actors | None |

#### Description

As an assigned Staff member or a Manager, I want to check in a ticket holder for a session by QR code or ticket code so that attendance is recorded per ticket and per session.

#### Trigger

An actor opens a session to check in a participant.

#### Preconditions

1. The actor is a Manager, or Staff with the location in scope and assigned to the session.
2. The session is published, sales-paused, or in progress.
3. The check-in opening time of the session has passed (60 minutes before the start by default).

#### Postconditions

1. Attendance of the ticket for that session is recorded once.
2. A refused scan changes nothing.

#### Normal Flow

1. Actor opens the session.
2. Actor scans the ticket QR code or enters the ticket code.
3. System validates the ticket, session, actor scope, and check-in opening time.
4. System records the attendance immediately and shows the result.

#### Alternative Flows

2a. Only a booking code is entered → System lists the session tickets for selection without checking anyone in.

#### Exception Flows

3a. Check-in has not opened → System refuses the check-in.

3b. Ticket belongs to another session or location → System refuses the check-in; an override cannot move the ticket.

3c. Ticket is already checked in → System reports that the ticket is already checked in and records nothing new.

4a. Recorded check-in is wrong → Only a Manager may reverse it with a reason.

#### Business Rules

BR-20-01, BR-20-02, BR-20-03

#### Non-Functional Requirements

—

#### Priority

—

#### Messages

—

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-20-01 | Attendance is recorded per ticket and per session. |
| BR-20-02 | Staff needs both location scope and session assignment; a Manager has global access. |
| BR-20-03 | Check-in works only online and only after the session check-in opening time; overrides require a reason. |
