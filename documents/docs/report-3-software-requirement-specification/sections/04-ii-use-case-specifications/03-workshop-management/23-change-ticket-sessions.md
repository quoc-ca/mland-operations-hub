### 3.23 Change Ticket Sessions

#### Primary Actors

Guest, Member, Staff, Manager

#### Secondary Actors

Mail Gateway

#### Description

As the buyer, an assigned Staff member, or a Manager, I want to select valid replacement sessions for a ticket so that the participant can attend at another time.

#### Preconditions

1. The ticket folio is open. A Member or Guest requests an advance change of a session that has not started; late-arrival support is entered only by assigned Staff of the ticket's location or a Manager (before the old session ends).
2. The actor is the owning Member or a buyer-verified Guest; assigned Staff or a Manager acting for the buyer gives a reason and the buyer proof.

#### Postconditions

- An applied change moves the ticket, keeps its code and QR, and uses one counted change.

#### Normal Flow

**Change Ticket Sessions**

1. The actor opens the ticket and views the valid replacement sessions.
2. The actor selects new sessions for one or both sessions.
3. The system rechecks capacity, reschedule quota, package rules, time window, and allowance.
4. The system applies the change and emails the result.

#### Alternative Flows

**Step 3 — Price increases**

The buyer confirms the new schedule and amount; the increase is added to the checkout balance.

**Step 3 — Only a Manager could allow it**

When the quota is full, the change is used, or fewer than 24 hours remain before a session that has not started, the request is queued for a Staff or Manager decision.

**Step 1 — The session has already started**

A Member or Guest cannot change it online; the screen asks them to contact the shop (branch name shown). An advance change of a started session is refused and never queued.

**Step 3 — A buyer asks for late-arrival support**

The system refuses it; only Staff or a Manager may enter it.

**Step 3 — Target is invalid or full**

The system refuses the change; a target after the 7th calendar day following the replaced session is never queued.

#### Business Rules

BR-85-01, BR-85-02, BR-85-03, BR-85-04, BR-85-05

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-85-01 | Each ticket allows one customer change, plus any extra change granted by a Manager. |
| BR-85-02 | A change never lowers the price or creates a refund. |
| BR-85-03 | Several tickets of one group change all or none. |
| BR-85-04 | Changing to another location is marked draft in one part of the spec and approved in another; to be confirmed. |
| BR-85-05 | Members and Guests make advance changes only; late-arrival support is limited to assigned Staff of the ticket's location or a Manager. |
