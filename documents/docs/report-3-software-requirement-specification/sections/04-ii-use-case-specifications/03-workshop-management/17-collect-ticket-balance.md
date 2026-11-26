### 3.17 Collect Ticket Balance

| Use Case ID | UC-79 | Use Case Name | Collect Ticket Balance |
| --- | --- | --- | --- |
| Created By |  | Created Date |  |
| Primary Actors | Staff, Manager | Secondary Actors | VNPay Payment Gateway |

#### Description

As an assigned Staff member or a Manager, I want to collect the remaining balance of a ticket by VNPay, cash, or bank transfer so that the folio can be finalized.

#### Trigger

An actor opens an open ticket folio with a balance due to collect payment.

#### Preconditions

1. The actor is a Manager, or Staff with the ticket location in scope and assigned to a session of the ticket.
2. The ticket folio is open and has a balance due.

#### Postconditions

1. Each collection is recorded on one ticket folio with its method, amount, and status.
2. The folio is finalized when the balance is zero and no collection is pending.

#### Normal Flow

1. Actor opens the folio and selects VNPay, cash, or bank transfer in that order.
2. Actor enters the amount to collect.
3. System checks the amount against confirmed and pending collections.
4. System records the collection and updates the balance once the money is confirmed.

#### Alternative Flows

2a. Actor splits payment across several collections or mixed methods → System accepts multiple collections on the same folio.

#### Exception Flows

2a. VNPay part is below 5,000 VND → System refuses VNPay and informs the actor to use cash or transfer.

4a. Transfer or VNPay is not yet verified → Collection stays pending; VNPay confirms via signed evidence and transfer via a Manager.

4b. Money is received twice or exceeds the balance → System records it in full and routes the excess to a Manager refund.

#### Business Rules

BR-79-01, BR-79-02, BR-79-03, BR-79-04

#### Non-Functional Requirements

—

#### Priority

—

#### Messages

—

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-79-01 | Cash records the amount received and the change returned; only the applied amount enters the folio. |
| BR-79-02 | The system never raises, waives, or hides a remainder. |
| BR-79-03 | The workshop product is handed over only after finalization, unless a Manager approves an exception. |
| BR-79-04 | An unreconciled VNPay part fails automatically after 24 hours when QueryDR reports no payment; this cannot yet be verified end to end because VNPay QueryDR is not configured. |
