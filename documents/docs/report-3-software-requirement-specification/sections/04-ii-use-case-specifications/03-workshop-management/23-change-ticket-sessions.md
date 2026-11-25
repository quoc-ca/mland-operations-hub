### 3.23 Change Ticket Sessions

#### Primary Actors

Guest, Member, Staff, Manager

#### Secondary Actors

Mail Gateway

#### Description

As the buyer, an assigned Staff member, or a Manager, I want to select valid replacement sessions for a ticket so that the participant can attend at another time.

#### Preconditions

1. The ticket folio is open, and the request is an advance change (24 hours before the changed session) or late-arrival support (before the old session ends).
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

When the quota is full, the change is used, or the advance window is closed, the request is queued for a decision.

**Step 3 — Target is invalid or full**

The system refuses the change; a target after the 7th calendar day following the replaced session is never queued.

#### Business Rules

BR-85-01, BR-85-02, BR-85-03, BR-85-04

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-85-01 | Each ticket allows one customer change, plus any extra change granted by a Manager. |
| BR-85-02 | A change never lowers the price or creates a refund. |
| BR-85-03 | Several tickets of one group change all or none. |
| BR-85-04 | Changing to another location is marked draft in one part of the spec and approved in another; to be confirmed. |
