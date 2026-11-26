### 3.20 Change Ticket Sessions

| Use Case ID | UC-85 | Use Case Name | Change Ticket Sessions |
| --- | --- | --- | --- |
| Created By |  | Created Date |  |
| Primary Actors | Guest, Member, Staff, Manager | Secondary Actors | Mail Gateway |

#### Description

As a buyer, Staff member, or Manager, I want to change ticket sessions and handle change requests so that participants can attend later.

#### Trigger

An actor opens a ticket to change its sessions, opens the change request queue, or grants an extra change.

#### Preconditions

1. For a change, the ticket bill is open.
2. A Member or Guest changes only sessions that have not started.
3. A buyer is the owning Member or verified Guest; Staff or Manager acting for them gives reason and buyer proof.
4. Deciding Staff have the request location in scope; only a Manager grants an extra change.

#### Postconditions

1. An applied change moves the ticket, keeps its code and QR, and uses one counted change.
2. A decided request records the deciding account and reason, and the customer is emailed.
3. A granted extra change is recorded with the Manager, reason, and time.

#### Normal Flow

**Change Sessions**

1. Actor opens the ticket and views the valid replacement sessions.
2. Actor selects new sessions for one or both sessions.
3. System rechecks capacity, reschedule quota, package rules, time window, and allowance.
4. System applies the change and emails the result.

**Decide Change Request**

1. Actor opens the change request queue.
2. Actor approves a pending request with a reason.
3. System checks the actor scope and the request state.
4. System applies the change as one granted counted change and emails the customer.

**Grant Extra Change**

1. Actor opens the ticket.
2. Actor chooses to grant an extra change and enters a reason.
3. System validates the authority and the reason.
4. System records one extra counted change.

#### Alternative Flows

C3a. Price increases → Buyer confirms the new schedule and amount; the increase is added to the checkout balance.

C3b. Quota is full, change is used, or advance window closed → System queues the request for a Staff or Manager decision.

D2a. Actor rejects the request → Ticket keeps its schedule; the internal reason is not sent to the customer.

G4a. Same grant request is repeated → System returns the recorded grant and adds nothing.

#### Exception Flows

C1a. A Member or Guest picks a started session → System refuses, never queues it, and shows the branch to contact.

C1b. A Member or Guest asks for late-arrival support → System refuses; only Staff or a Manager may enter it.

C3c. Target session is invalid or full → System refuses; targets past day 7 after the replaced session are never queued.

D3a. Request has expired or is already decided → System refuses the decision; the customer is notified of expiry.

D4a. Target location no longer sells the package → Approval is refused and no allowance is used.

G2a. Reason is missing → System refuses the grant.

G3a. Actor is not a Manager → System refuses the grant.

#### Business Rules

BR-85-01, BR-85-02, BR-85-03, BR-85-04, BR-85-05, BR-86-01, BR-86-02, BR-86-03, BR-87-01, BR-87-02, BR-87-03

#### Non-Functional Requirements

—

#### Priority

—

#### Messages

—

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-85-01 | Each ticket allows one customer change, plus any extra change granted by a Manager. |
| BR-85-02 | A change never lowers the price or creates a refund. |
| BR-85-03 | Several tickets of one group change all or none. |
| BR-85-04 | A ticket may move to another location that sells the package, without Manager approval; a package requiring one location moves both sessions together. |
| BR-85-05 | Members and Guests make advance changes only; late-arrival support is limited to assigned Staff of the ticket's location or a Manager. |
| BR-86-01 | Staff decides only requests of locations in their scope; a Manager is global; Admin has no access. |
| BR-86-02 | An advance request expires at the earlier start of the changed session or a requested session. |
| BR-86-03 | A late-arrival request expires at the earlier of the end of the 7th calendar day or a requested session start. |
| BR-87-01 | Only a Manager may grant an extra change, with a reason and audit. |
| BR-87-02 | A grant never performs the change itself. |
| BR-87-03 | Mland-caused changes do not count against the allowance. |
