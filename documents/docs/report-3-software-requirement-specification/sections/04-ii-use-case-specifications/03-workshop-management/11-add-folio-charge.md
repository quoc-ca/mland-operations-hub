### 3.11 UC-35 Add Folio Charge

#### UC Name

UC-35 Add Folio Charge

#### Primary Actors

Staff, Manager

#### Secondary Actors

None

#### Description

As a Staff member or a Manager, I want to add a customer-agreed add-on or variable charge to a ticket's open folio so that the customer pays for extra services used at the workshop.

#### Preconditions

- The actor is signed in as an active Manager, or as active Staff assigned to a session of the ticket.
- The ticket has an Open folio.
- A session of the ticket has started.
- The customer has agreed to the add-on or charge.

#### Normal Flow

***Add Catalogue Add-on***

1. The actor opens the ticket folio by its ticket code.
2. The system shows the folio lines, the balance due and the add-ons sold for the ticket's package and location.
3. The actor ticks one or more add-ons the customer agreed to.
4. The actor ticks that the customer agreed.
5. The actor submits the selected add-ons.
6. The system checks the actor's access, the folio state and the session start.
7. The system takes each add-on price from the catalogue and checks the minimum charge.
8. The system adds each add-on as a folio line with a consent reference, the actor and the time.
9. The system records each added charge in the audit log.
10. The system recomputes the folio total and balance due.
11. The system confirms that the add-ons have been added (MSG208).

#### Alternative Flows

***Step 2.1 Ticket Has No Folio***

1. The system informs the actor that no folio matches the ticket code (MSG246).
2. The use case ends without adding a charge.

***Step 2.2 No Add-on Is Sold for the Package and Location***

1. The system shows that no add-on is available (MSG214).
2. The actor can only add a variable charge.
3. The use case continues at step 3.

***Step 3.1 Actor Adds a Variable Charge***

1. The actor enters the charge description, the amount and the reason.
2. The actor ticks that the customer agreed.
3. The actor submits the variable charge.
4. The system checks the actor's access, the folio state and the session start.
5. The system checks the minimum charge amount.
6. The system adds the charge as a folio line with a consent reference, the actor and the time.
7. The use case continues at step 9.

***Step 4.1 Customer Agreement Is Not Ticked***

1. The system keeps the submit button disabled, or refuses the request (MSG211).
2. The system adds no charge.
3. The use case continues at step 4.

***Step 6.1 Actor Is Outside the Ticket's Branch Scope***

1. The system refuses the charge (MSG01).
2. The system adds no charge.
3. The use case ends without adding a charge.

***Step 6.2 Staff Is Not Assigned to the Ticket's Session***

1. The system refuses the charge (MSG212).
2. The system records the refusal in the audit log.
3. The use case ends without adding a charge.

***Step 6.3 Folio Is No Longer Open***

1. The system informs the actor that the folio is finalized or closed (MSG210).
2. The system adds no charge.
3. The use case ends without adding a charge.

***Step 6.4 Session Has Not Started***

1. The system informs the actor that the ticket's session has not started (MSG209).
2. The system adds no charge.
3. The use case ends without adding a charge.

***Step 7.1 Amount Is Below the Minimum or the Add-on Is No Longer Sold***

1. The system refuses the charge (MSG213).
2. The system adds no charge.
3. The use case continues at step 3.

#### Postconditions

- Each added charge is a confirmed folio line with its consent reference, actor and time.
- The folio total and balance due include the new charges.
- Each added charge is recorded in the audit log.
- A refused request adds no folio line.

#### Business Rule

BR-113, BR-114, BR-115, BR-178
