### 3.24 UC-55 Grant Extra Change Allowance

#### UC Name

UC-55 Grant Extra Change Allowance

#### Primary Actors

Manager

#### Secondary Actors

None

#### Description

As a Manager, I want to grant one extra counted session change to a ticket so that the customer can change sessions after the normal allowance is used.

#### Preconditions

- The actor is signed in as an active Manager.
- The ticket exists.

#### Normal Flow

***Grant Extra Change Allowance***

1. The Manager opens the ticket actions screen.
2. The Manager enters the ticket code in the extra change section.
3. The Manager selects to grant an extra change.
4. The system asks for a reason in a dialog.
5. The Manager enters the reason and confirms.
6. The system checks the Manager role and that the ticket exists.
7. The system records one extra counted change for the ticket with the Manager, reason and time.
8. The system writes an audit record of the grant.
9. The system confirms that the extra change has been granted (MSG245).

#### Alternative Flows

***Step 5.1 Reason Is Missing***

1. The system shows that a reason is required (MSG235).
2. The system grants nothing.
3. The use case continues at step 5.

***Step 6.1 Actor Is Not a Manager***

1. The system refuses the grant (MSG01).
2. The system records no allowance.
3. The use case ends without a grant.

***Step 6.2 Ticket Does Not Exist***

1. The system informs the Manager that no ticket matches the code (MSG246).
2. The system records no allowance.
3. The use case continues at step 2.

***Step 7.1 Same Grant Is Sent Again***

1. The system returns the grant recorded earlier for the same request.
2. The system adds no second allowance.
3. The use case continues at step 9.

#### Postconditions

- The ticket has one more counted customer change available.
- The grant is audited with the Manager, reason and time.
- The grant performs no session change by itself.

#### Business Rule

BR-131, BR-143
