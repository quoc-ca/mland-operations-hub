### 3.1 Workshop Management

#### 3.1.6 Check In Participant

<table>
<tr><td>Primary Actors</td><td>Staff</td><td>Secondary Actors</td><td>None</td></tr>
<tr><td>Description</td><td colspan="3">Allows Staff to verify a confirmed workshop booking using its QR code or booking code and record the participant's attendance.</td></tr>
<tr><td>Preconditions</td><td colspan="3">1. Staff is authenticated and authorized to perform workshop check-in.<br>2. The workshop session exists.<br>3. The customer provides a QR ticket or valid booking code.</td></tr>
<tr><td>Postconditions</td><td colspan="3">• The confirmed booking is marked checked in with the Staff actor and time recorded.<br>• A duplicate check-in is not created.<br>• If the booking is invalid, no attendance record is changed.</td></tr>
<tr><td>Normal<br>Sequence/Flow</td><td colspan="3"><em>Check In Participant</em><br>1. Staff opens the workshop check-in screen.<br>2. Staff scans the customer's QR code or enters the booking code manually.<br>3. The system locates the booking and validates its confirmation status, session, and check-in state.<br>4. The system displays the booking and participant summary for Staff verification.<br>5. Staff confirms the attendee's presence.<br>6. The system records the check-in actor and time and displays a successful check-in result.</td></tr>
<tr><td>Alternative<br>Sequences/Flows</td><td colspan="3"><em>Step 2 — QR/code is invalid or booking is not found</em><br>The system rejects the lookup and asks Staff to retry or use manual verification.<br><br><em>Step 3 — Booking is unpaid, cancelled, or unconfirmed</em><br>The system refuses check-in and displays the booking status.<br><br><em>Step 3 — Booking belongs to another session/date</em><br>The system warns Staff and does not check in the participant without an authorized operational decision.<br><br><em>Step 5 — Booking is already checked in</em><br>The system displays the existing check-in and does not create a duplicate record.</td></tr>
<tr><td>Business Rule</td><td colspan="3">BR-20-01, BR-20-02, BR-20-03</td></tr>
</table>

<table><tr style="background-color:#f4cccc"><th>ID</th><th>Rule Definition</th></tr><tr><td>BR-20-01</td><td>Only a confirmed booking may be checked in.</td></tr><tr><td>BR-20-02</td><td>Check-in may be performed by QR scan or manual booking-code lookup.</td></tr><tr><td>BR-20-03</td><td>Check-in records the Staff actor and must not create duplicate attendance records.</td></tr></table>
