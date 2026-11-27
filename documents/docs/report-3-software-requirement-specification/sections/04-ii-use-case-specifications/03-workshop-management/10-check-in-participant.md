### 3.10 UC-34 Check In Participant

#### UC Name

UC-34 Check In Participant

#### Primary Actors

Staff, Manager

#### Secondary Actors

Mail Gateway (SMTP)

#### Description

As a Staff member or a Manager, I want to check in each ticket holder of a session by QR code or ticket code so that attendance is recorded per ticket.

#### Preconditions

- The actor is signed in as an active Manager, or as active Staff assigned to the session.
- The session is Published, Sales paused or In progress.
- The participant holds an active ticket for this session.

#### Normal Flow

***Check In Participant***

1. The actor opens the session from the workshop operations list.
2. The system shows the check-in desk, the session facts and the attendance roster.
3. The actor scans the ticket QR code into the check-in field.
4. The actor submits the check-in.
5. The system checks the actor's access, the session state and the check-in opening time.
6. The system checks that the ticket is active and belongs to this session.
7. The system records the ticket as checked in with the actor and the time.
8. The system updates the attendance roster and its check-in count.
9. The system confirms that the check-in has been recorded (MSG195).

#### Alternative Flows

***Step 3.1 Customer Gives the Ticket Code***

1. The actor types the ticket code instead of scanning the QR code.
2. The use case continues at step 4.

***Step 3.2 Customer Knows Only the Booking Code***

1. The actor enters the booking code in the ticket lookup.
2. The system lists the booking's tickets of this session with their check-in state.
3. The actor picks one ticket from the list.
4. The use case continues at step 4.

***Step 3.3 No Valid Ticket Matches the Booking Code***

1. The system shows a generic result that no valid ticket exists in this session (MSG200).
2. The system reveals no ticket of another session or branch.
3. The use case continues at step 3.

***Step 3.4 Customer Cannot Present a Ticket***

1. The actor enters the booking code in the ticket delivery panel.
2. The actor requests a ticket resend.
3. The system checks the resend limit of the booking.
4. The system sends the booking's active unused tickets to the booking contact email through the Mail Gateway.
5. The system records the actor, the booking and the outcome in the audit log.
6. The system confirms that the resend has been requested (MSG201).
7. The use case continues at step 3.

***Step 3.5 Resend Limit Is Reached***

1. The system informs the actor of the limit and the waiting time (MSG202).
2. The system sends no ticket.
3. The use case continues at step 3.

***Step 3.6 Booking Has No Ticket to Resend***

1. The system informs the actor that no active unused ticket can be resent (MSG203).
2. The use case continues at step 3.

***Step 3.7 Exceptional Check-In Is Needed***

1. The actor opens the exceptions panel.
2. The actor enters the ticket code, a standardized reason code and the required note or evidence.
3. The actor submits the check-in override.
4. The system checks that the ticket is valid for this session and not yet checked in.
5. The system records the check-in with the reason, the actor and the time in the audit log.
6. The system confirms that the override has been recorded (MSG204).
7. The use case continues at step 8.

***Step 3.8 Actor Finalizes the Session Attendance***

1. The actor selects to finalize the attendance after the session has ended.
2. The system shows how many tickets are checked in and how many are not (MSG205).
3. The actor confirms that the session has ended.
4. The system checks that the session is In progress.
5. The system marks every unchecked active ticket of the session as No-show.
6. The system moves the session to Attendance finalized with the actor and the time.
7. The system confirms that the attendance has been finalized (MSG206).
8. The use case ends.

***Step 3.9 Manager Corrects a Wrong Check-In***

1. The Manager enters the ticket code, a reason code and the required note or evidence.
2. The Manager selects to reverse the check-in or to set the final attendance.
3. The system checks that the correction window of the ticket is still open.
4. The system records the corrected attendance with before and after evidence in the audit log.
5. The system confirms that the correction has been recorded (MSG207).
6. The use case ends.

***Step 3.10 Staff Tries to Correct a Check-In***

1. The system refuses the correction (MSG01).
2. The system changes no attendance.
3. The use case ends without correcting the check-in.

***Step 5.1 Actor Is Not Allowed to Operate the Session***

1. The system refuses the check-in (MSG01).
2. The system records no attendance.
3. The use case ends without checking in the participant.

***Step 5.2 Check-In Has Not Opened Yet***

1. The system informs the actor of the check-in opening time (MSG197).
2. The system records no attendance.
3. The use case ends without checking in the participant.

***Step 5.3 Session State Does Not Allow Check-In***

1. The system informs the actor that the session no longer accepts check-in (MSG199).
2. The system records no attendance.
3. The use case ends without checking in the participant.

***Step 6.1 Ticket Is Already Checked In***

1. The system shows that the ticket was already checked in and its original check-in time (MSG196).
2. The system records no new attendance.
3. The use case ends.

***Step 6.2 Ticket Is Unknown or Not Valid for This Session***

1. The system refuses the ticket as cancelled, revoked, unknown or belonging to another session (MSG198).
2. The system records no attendance.
3. The use case ends without checking in the participant.

#### Postconditions

- Each checked-in ticket has one attendance record per session, with the actor and the time.
- Overrides, resends and corrections are recorded in the audit log with the actor and the reason.
- After finalization, every unchecked active ticket of the session is No-show.
- A refused attempt changes no attendance.

#### Business Rule

BR-107, BR-108, BR-109, BR-110, BR-111, BR-112, BR-185
