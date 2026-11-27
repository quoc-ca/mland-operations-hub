### 3.29 UC-60 Revoke or Rotate Magic Link

#### UC Name

UC-60 Revoke or Rotate Magic Link

#### Primary Actors

Manager

#### Secondary Actors

Mail Gateway (SMTP)

#### Description

As a Manager, I want to revoke or rotate the Magic Link of a booking group after an access incident so that a leaked link can no longer open the booking.

#### Preconditions

- The actor is signed in as an active Manager.
- The booking group is confirmed.

#### Normal Flow

***Rotate Magic Link***

1. The Manager opens the detail page of a workshop session.
2. The system shows the booking recovery panel.
3. The Manager enters the booking group code.
4. The Manager chooses a standard incident reason and adds an optional note.
5. The Manager selects to rotate the Magic Link.
6. The system checks the Manager role and that the group is confirmed.
7. The system revokes every active Magic Link of the group.
8. The system issues a new Magic Link that keeps the remaining lifetime of the old link.
9. The system emails the new link to the contact email of the group.
10. The system records the action, reason, Manager and time in the audit log.
11. The system confirms that the Magic Link has been rotated (MSG257).

#### Alternative Flows

***Step 4.1 Reason Needs a Note***

1. The Manager chooses the "other reason" option.
2. The system does not allow the action until the Manager enters a note.
3. The use case continues at step 4.

***Step 5.1 Manager Revokes the Magic Link Only***

1. The Manager selects to revoke the Magic Link.
2. The system revokes every active Magic Link of the group without issuing a new one.
3. The system records the action, reason, Manager and time in the audit log.
4. The system confirms that the Magic Link has been revoked (MSG257).
5. The use case ends with no usable Magic Link.

***Step 5.2 Same Request Is Sent Again***

1. The system returns the stored outcome of the earlier request.
2. The system revokes nothing more and sends no second email.
3. The use case continues at step 11.

***Step 6.1 Actor Is Not a Manager***

1. The system refuses the action (MSG01).
2. The system changes no link.
3. The use case ends without a change.

***Step 6.2 Booking Group Does Not Exist***

1. The system informs the Manager that no booking group matches the code (MSG246).
2. The use case continues at step 3.

***Step 6.3 Booking Group Is Not Confirmed***

1. The system informs the Manager that only a confirmed group has a Magic Link (MSG258).
2. The system changes no link.
3. The use case ends without a change.

#### Postconditions

- Every earlier Magic Link of the group no longer opens the booking.
- After a rotation, only the newly emailed link works.
- The incident is audited with the action, reason, Manager and time.

#### Business Rule

BR-141, BR-143
