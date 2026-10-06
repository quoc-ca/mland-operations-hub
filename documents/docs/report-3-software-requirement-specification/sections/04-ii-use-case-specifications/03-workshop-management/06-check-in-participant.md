### 3.6 Check In Participant

#### Primary Actors

Staff

#### Secondary Actors

None

#### Description

Allows Staff to verify a confirmed workshop booking using its QR code or booking code and record the participant's attendance.

#### Preconditions

1. Staff is authenticated and authorized to perform workshop check-in.
2. The workshop session exists.
3. The customer provides a QR ticket or valid booking code.

#### Normal Flow

**Check In Participant**

1. Staff opens the workshop check-in screen.
2. Staff scans the customer's QR code or enters the booking code manually.
3. The system locates the booking and validates its confirmation status, session, and check-in state.
4. The system displays the booking and participant summary for Staff verification.
5. Staff confirms the attendee's presence.
6. The system records the check-in actor and time and displays a successful check-in result.

#### Alternative Flows

**Step 2 — QR/code is invalid or booking is not found**

The system rejects the lookup and asks Staff to retry or use manual verification.

**Step 3 — Booking is unpaid, cancelled, or unconfirmed**

The system refuses check-in and displays the booking status.

**Step 3 — Booking belongs to another session/date**

The system warns Staff and does not check in the participant without an authorized operational decision.

**Step 5 — Booking is already checked in**

The system displays the existing check-in and does not create a duplicate record.

#### Postconditions

• The confirmed booking is marked checked in with the Staff actor and time recorded.• A duplicate check-in is not created.• If the booking is invalid, no attendance record is changed.

#### Business Rules

BR-20-01, BR-20-02, BR-20-03

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-20-01 | Only a confirmed booking may be checked in. |
| BR-20-02 | Check-in may be performed by QR scan or manual booking-code lookup. |
| BR-20-03 | Check-in records the Staff actor and must not create duplicate attendance records. |
