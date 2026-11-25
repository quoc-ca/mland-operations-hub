### 3.17 Collect Ticket Balance

#### Primary Actors

Staff, Manager

#### Secondary Actors

VNPay Payment Gateway

#### Description

As an assigned Staff member or a Manager, I want to collect the remaining balance of a ticket by VNPay, cash, or bank transfer from the Guest or Member so that the folio can be finalized.

#### Preconditions

1. The actor is a Manager, or Staff with the ticket location in scope and assigned to a session of the ticket.
2. The ticket folio is open and has a balance due.

#### Postconditions

- Each collection is recorded on one ticket folio with its method, amount, and status.
- The folio is finalized when the balance is zero and no collection is pending.

#### Normal Flow

**Collect Ticket Balance**

1. The actor opens the folio and chooses VNPay, cash, or bank transfer, offered in that order.
2. The actor enters the amount to collect; several collections and mixed methods are allowed.
3. The system checks the amount against the confirmed and pending collections.
4. The system records the collection and updates the balance once the money is confirmed.

#### Alternative Flows

**Step 2 — VNPay part below 5,000 VND**

The system states this before collecting and refuses VNPay; cash or transfer is used.

**Step 4 — Transfer or VNPay not yet verified**

The collection stays pending; a VNPay part is confirmed only by signed provider evidence and a transfer only by a Manager.

**Step 4 — Money received twice or above the balance**

The system records it in full and routes the excess to a Manager refund.

#### Business Rules

BR-79-01, BR-79-02, BR-79-03, BR-79-04

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-79-01 | Cash records the amount received and the change returned; only the applied amount enters the folio. |
| BR-79-02 | The system never raises, waives, or hides a remainder. |
| BR-79-03 | The workshop product is handed over only after finalization, unless a Manager approves an exception. |
| BR-79-04 | An unreconciled VNPay part fails automatically after 24 hours when QueryDR reports no payment; this cannot yet be verified end to end because VNPay QueryDR is not configured. |
