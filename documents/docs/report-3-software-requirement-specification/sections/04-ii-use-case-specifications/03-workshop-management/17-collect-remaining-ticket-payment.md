### 3.14 Collect Remaining Ticket Payment

| Use Case ID | UC-79 | Use Case Name | Collect Remaining Ticket Payment |
| --- | --- | --- | --- |
| Created By |  | Created Date |  |
| Primary Actors | Staff, Manager | Secondary Actors | VNPay Payment Gateway |

#### Description

As an assigned Staff member or a Manager, I want to collect the unpaid amount of a ticket so that the ticket is fully paid.

#### Trigger

An actor opens an unpaid ticket bill to collect money from the customer.

#### Preconditions

1. The actor is a Manager, or Staff with the ticket location in scope and assigned to a session of the ticket.
2. The ticket bill is open and still has an unpaid amount.

#### Postconditions

1. Each payment is recorded on one ticket bill with its method, amount, and status.
2. The bill is finalized when nothing is unpaid or pending.

#### Normal Flow

1. Actor opens the ticket bill.
2. System displays the unpaid amount.
3. Actor selects VNPay, cash, or bank transfer.
4. Actor enters the amount to collect.
5. System checks the amount against received and pending payments.
6. System records the payment.
7. System reduces the unpaid amount after the money is confirmed.

#### Alternative Flows

4a. Customer pays in several parts or methods → System accepts several payments on the same bill.

#### Exception Flows

4b. VNPay amount is below 5,000 VND → System refuses VNPay and informs the actor to use cash or transfer.

7a. Transfer or VNPay is not yet verified → Payment stays pending; VNPay sends signed evidence, and a Manager confirms transfers.

7b. Money is received twice or exceeds the balance → System records it in full and routes the excess to a Manager refund.

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
| BR-79-01 | Cash records the amount received and the change returned; only the applied amount enters the bill. |
| BR-79-02 | The system never raises, waives, or hides a remainder. |
| BR-79-03 | The workshop product is handed over only after finalization, unless a Manager approves early handover. |
| BR-79-04 | An unreconciled VNPay part fails automatically after 24 hours when QueryDR reports no payment. |
