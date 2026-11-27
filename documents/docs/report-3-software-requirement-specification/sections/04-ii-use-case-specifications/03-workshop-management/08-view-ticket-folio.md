### 3.8 UC-15 View Ticket Folio

#### UC Name

UC-15 View Ticket Folio

#### Primary Actors

Guest, Member, Staff, Manager

#### Secondary Actors

None

#### Description

As a ticket owner, assigned Staff member or Manager, I want to view the bill, add-ons and remaining balance of a workshop ticket so that I know what is paid and due.

#### Preconditions

- The ticket has its own folio.
- A Member owns the booking group of the ticket.
- A Guest holds the ticket code and one stored contact value of the group.
- A Staff member is assigned to a session of the ticket at a location in scope.

#### Normal Flow

***View Ticket Folio***

1. The actor opens the ticket from the booking group.
2. The actor selects View the ticket payment folio.
3. The system verifies that the actor may read this ticket's folio.
4. The system shows the folio total, the amount paid, the amount awaiting verification and the balance due.
5. The system shows each collection with its method and amount.
6. The system shows each additional charge with its amount and status.
7. The system confirms that the ticket folio has been displayed.

#### Alternative Flows

***Step 2.1 Guest Opens the Folio***

1. The system asks for the email or the phone number used for the booking.
2. The Guest enters exactly one contact value.
3. The system checks the ticket code with that contact value, sent in the request body.
4. The use case continues at step 4.

***Step 2.2 Staff or Manager Opens the Folio***

1. The Staff or Manager opens the ticket from the session roster or the settlement workspace.
2. The system shows the operator view of the folio.
3. The use case continues at step 3.

***Step 3.1 Guest Code or Contact Does Not Match***

1. The system informs the Guest that no ticket matches the code and contact, without naming the wrong value (MSG188).
2. The use case continues at step 1 of Step 2.1.

***Step 3.2 Member Does Not Own the Ticket***

1. The system informs the Member that the ticket was not found in the account (MSG188).
2. The use case ends without showing a folio.

***Step 3.3 Staff Is Not Assigned to the Ticket***

1. The system refuses the request and records the refusal (MSG01).
2. The use case ends without showing a folio.

***Step 4.1 Folio Cannot Be Loaded***

1. The system informs the actor that the data could not be loaded (MSG05).
2. The use case ends without showing a folio.

#### Postconditions

- Only an authorized actor sees the folio.
- Viewing changes no folio data and uses no buyer verification.

#### Business Rule

BR-125, BR-102, BR-103
