### 3.3 Create Workshop Booking

#### Primary Actors

Guest, Member

#### Secondary Actors

None

#### Description

As a Guest or Member, I want to create a workshop booking by selecting a published package, location, date, session, and permitted ring-design path so that the system can reserve a seat temporarily and prepare the booking deposit payment.

#### Preconditions

1. The platform is available.
2. The actor has selected a published workshop package.
3. A workshop location and session are available for selection.
4. Capacity is configured for the selected location and session.
5. The actor has the required contact and participant information. A Member may use verified profile information; a Guest must provide the required contact details.

#### Normal Flow

Create Workshop Booking
1. The actor selects a workshop location.
2. The actor selects a date and one available daily session.
3. The actor selects a published workshop package.
4. The system verifies that capacity is configured for the selected location/session and that sufficient capacity remains for the requested participant count.
5. The system displays a booking summary containing the location, date, session, package, participant count, package price, and required deposit.
6. The actor enters the required contact and participant details, or confirms the applicable Member profile details.
7. The actor optionally selects a supported ring-design path.
8. If a design path is selected, the actor completes the permitted design flow and the system stores its request/outcome for the booking.
9. The actor submits the booking.
10. The system creates the booking and package invoice, sets the deposit amount to 50% of the package price, and sets the booking status to payment pending.
11. The system places a temporary hold on the selected capacity and directs the actor to UC18 Pay Workshop Deposit.

#### Alternative Flows





Step 3 — Package is invalid, unpublished, or unavailable
The system rejects the selection and asks the actor to choose another published package.

Step 4 — Capacity is not configured
The system cannot create the booking and informs the actor that the selected session is not available for booking. The actor must select another session or location.

Step 4 — Insufficient capacity
The system rejects the selection, releases any provisional selection, and asks the actor to choose another session or reduce the participant count.

Step 6 — Required contact or participant information is missing or invalid
The system displays validation errors and keeps the actor on the booking form until the information is corrected.

Step 7 — Selected design path is not supported
The system rejects the design selection and asks the actor to choose another supported design path or continue without a design request.

Step 8 — Design request is rejected
The system does not create the booking and asks the actor to select another design choice or continue without the rejected design path.

Step 9 — Actor abandons or cancels before submission
The system creates no booking, invoice, or capacity hold.

Step 10 — Booking creation fails
The system displays an error, creates no incomplete customer-facing booking, and allows the actor to retry.After 

Step 11 — Deposit is not validly confirmed within the hold window
The booking remains unconfirmed and the temporary capacity hold is released. Payment handling continues under UC18 Pay Workshop Deposit.

#### Postconditions

• A booking and package invoice are created with payment-pending status when the selected session can accept the requested participants.• The required deposit is calculated as 50% of the selected package price.• The selected seat capacity is held temporarily for the payment window, for at most 15 minutes.• The booking remains unconfirmed until UC18 Pay Workshop Deposit receives valid payment confirmation.• If the design path is used, its request and outcome are associated with the booking.

#### Business Rules

BR-17-01, BR-17-02, BR-17-03, BR-17-04

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-17-01 | A workshop booking requires a selected package before the schedule/session and supported design path are finalized. |
| BR-17-02 | A booking can be created only when capacity is configured for the selected location/session and sufficient capacity remains. |
| BR-17-03 | The required workshop deposit is 50% of the selected package price; the booking is not confirmed by creating the booking or by a browser redirect alone. |
| BR-17-04 | A pending booking holds the selected capacity for at most 15 minutes and the hold is released when valid payment confirmation is not received. |
