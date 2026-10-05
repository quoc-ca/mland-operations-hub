### 3.1 Workshop Management

#### 3.1.5 Look Up Booking Details

<table>
<tr><td>Primary Actors</td><td>Guest, Member</td><td>Secondary Actors</td><td>None</td></tr>
<tr><td>Description</td><td colspan="3">Allows a customer to retrieve a workshop booking by reference code and view its schedule, package, status, payment information, and QR check-in ticket.</td></tr>
<tr><td>Preconditions</td><td colspan="3">1. The platform is available.<br>2. The customer has a booking reference code.<br>3. The booking exists and is eligible for customer lookup.</td></tr>
<tr><td>Postconditions</td><td colspan="3">• The system displays the booking details and QR ticket when the reference is valid.<br>• No booking, payment, capacity, or check-in data is changed.</td></tr>
<tr><td>Normal<br>Sequence/Flow</td><td colspan="3"><em>Look Up Booking Details</em><br>1. The customer opens the booking lookup page.<br>2. The customer enters the booking reference code.<br>3. The system validates the format and searches for the booking.<br>4. The system displays the location, date, session, package, booking status, invoice/deposit status, participant summary, and QR check-in ticket when available.<br>5. The customer reviews or saves the displayed QR ticket.</td></tr>
<tr><td>Alternative<br>Sequences/Flows</td><td colspan="3"><em>Step 2 — Reference code is blank or malformed</em><br>The system displays a validation message and asks the customer to enter a valid code.<br><br><em>Step 3 — Booking is not found</em><br>The system displays a not-found message without exposing another customer's data.<br><br><em>Step 4 — Booking is not confirmed or has no QR ticket</em><br>The system displays the current payment/booking status and omits the QR ticket until the booking is confirmed.<br><br><em>Step 3 — Lookup fails</em><br>The system displays an error and allows the customer to retry.</td></tr>
<tr><td>Business Rule</td><td colspan="3">BR-19-01, BR-19-02</td></tr>
</table>

<table><tr style="background-color:#f4cccc"><th>ID</th><th>Rule Definition</th></tr><tr><td>BR-19-01</td><td>Only a valid booking reference code may retrieve booking details.</td></tr><tr><td>BR-19-02</td><td>A QR check-in ticket is displayed only for a confirmed booking.</td></tr></table>
