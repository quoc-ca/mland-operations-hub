### 3.14 UC-38 Decide Ticket Change Request

#### UC Name

UC-38 Decide Ticket Change Request

#### Primary Actors

Staff, Manager

#### Secondary Actors

Mail Gateway (SMTP)

#### Description

As a Staff member or a Manager, I want to approve or reject a queued ticket change request of my location so that a change blocked only by a liftable rule is decided with a reason.

#### Preconditions

- The actor is signed in as an active Manager, or as active Staff whose location scope includes the request's location.
- The change request is Pending and has not expired.

#### Normal Flow

***Approve Ticket Change Request***

1. The actor opens the change request queue.
2. The system marks pending requests whose time has run out as Expired.
3. The system lists the pending requests of the actor's locations with ticket, sessions, blocking rule, price increase and expiry.
4. The actor selects to approve one request.
5. The system asks for a decision reason (MSG229).
6. The actor enters the reason and confirms the approval.
7. The system checks the actor's scope, the request state and its expiry.
8. The system grants one extra change for the ticket with the actor and the reason.
9. The system checks that the folio is Open and the buyer confirmed any price increase.
10. The system moves the ticket to the requested sessions after the capacity, quota and package checks.
11. The system records the request as Approved with the applied change, the actor and the time.
12. The system records the decision in the audit log.
13. The system emails the buyer the new schedule through the Mail Gateway.
14. The system confirms that the change has been approved and applied (MSG228).

#### Alternative Flows

***Step 3.1 No Request Matches the Status Filter***

1. The system shows that no change request is found (MSG230).
2. The use case ends.

***Step 4.1 Actor Rejects the Request***

1. The actor selects to reject the request.
2. The system asks for a decision reason (MSG229).
3. The actor enters the reason and confirms the rejection.
4. The system checks the actor's scope, the request state and its expiry.
5. The system records the request as Rejected with the reason, the actor and the time.
6. The system records the decision in the audit log.
7. The system emails the buyer that the ticket keeps its schedule through the Mail Gateway.
8. The system confirms that the change has been rejected (MSG228).
9. The use case ends.

***Step 6.1 Decision Reason Is Empty***

1. The system asks for a reason (MSG235).
2. The system records no decision.
3. The use case continues at step 6.

***Step 7.1 Staff Is Outside the Request's Location Scope***

1. The system refuses the decision (MSG01).
2. The system records no decision.
3. The use case ends without deciding the request.

***Step 7.2 Request Has Expired or Was Already Decided***

1. The system informs the actor that the request can no longer be decided (MSG231).
2. The system records no decision.
3. The use case ends without deciding the request.

***Step 9.1 Change Can No Longer Be Applied***

1. The system refuses the approval with the reason, such as no seat, a full quota, a closed folio or an unconfirmed price increase (MSG232).
2. The system keeps the request Pending and grants no extra change.
3. The use case ends without changing the ticket.

#### Postconditions

- An approved request has one applied change and one extra change granted to the ticket.
- A rejected request leaves the ticket on its current sessions.
- Each decision records the actor, the reason and the time in the audit log.
- The buyer is emailed the outcome of the decision.

#### Business Rule

BR-122, BR-123, BR-124, BR-131
