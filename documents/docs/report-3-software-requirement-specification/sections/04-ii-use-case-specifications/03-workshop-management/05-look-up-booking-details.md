### 3.5 Look Up Booking Details

| Attribute | Value |
|---|---|
| Primary Actors | Guest, Member |
| Secondary Actors | None |
| Description | Allows a customer to retrieve a workshop booking by reference code and view its schedule, package, status, payment information, and QR check-in ticket. |
| Preconditions | 1. The platform is available.<br>2. The customer has a booking reference code.<br>3. The booking exists and is eligible for customer lookup. |
| Postconditions | • The system displays the booking details and QR ticket when the reference is valid.• No booking, payment, capacity, or check-in data is changed. |
| Normal Sequence/Flow | Look Up Booking Details<br>1. The customer opens the booking lookup page.<br>2. The customer enters the booking reference code.<br>3. The system validates the format and searches for the booking.<br>4. The system displays the location, date, session, package, booking status, invoice/deposit status, participant summary, and QR check-in ticket when available.<br>5. The customer reviews or saves the displayed QR ticket. |
| Alternative Sequences/Flows | <br><br>Step 2 — Reference code is blank or malformedThe system displays a validation message and asks the customer to enter a valid code.Step 3 — Booking is not foundThe system displays a not-found message without exposing another customer's data.Step 4 — Booking is not confirmed or has no QR ticketThe system displays the current payment/booking status and omits the QR ticket until the booking is confirmed.Step 3 — Lookup failsThe system displays an error and allows the customer to retry. |
| Business Rule | BR-19-01, BR-19-02 |

| ID | Rule Definition |
|---|---|
| BR-19-01 | Only a valid booking reference code may retrieve booking details. |
| BR-19-02 | A QR check-in ticket is displayed only for a confirmed booking. |
