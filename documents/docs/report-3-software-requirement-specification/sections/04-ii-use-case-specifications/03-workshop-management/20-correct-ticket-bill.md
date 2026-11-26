### 3.17 Correct Ticket Bill

| Use Case ID | UC-82 | Use Case Name | Correct Ticket Bill |
| --- | --- | --- | --- |
| Created By |  | Created Date |  |
| Primary Actors | Manager | Secondary Actors | None |

#### Description

As a Manager, I want to void a wrong bill item or collection, or correct a finalized overcharge, so that the bill is fixed without deleting history.

#### Trigger

A Manager opens a ticket bill to void an erroneous entry or correct an overcharge.

#### Preconditions

1. The actor is an active Manager.
2. The bill is OPEN for a void, or FINALIZED for an overcharge correction.

#### Postconditions

1. The original records are kept and the correction is audited with its reason.
2. An overcharge on a finalized bill is returned through a bank refund request.

#### Normal Flow

1. Actor opens the ticket bill.
2. Actor selects the wrong bill item or collection and enters a reason.
3. System checks the bill state and the cash day of the collection.
4. System voids the record and recalculates the balance.

#### Alternative Flows

2a. Overcharge found after finalization → Manager records a correction up to unrefunded collected money and requests a bank refund without reopening the bill.

#### Exception Flows

2a. Selected collection is the booking-time allocation → System does not offer it for voiding; Manager uses an eligible collection or refund flow.

3a. Cash belongs to a closed day → Void is refused; on an OPEN unrefunded bill, Manager records a negative reversal on the current day.

3b. Bill is closed → System refuses the correction.

#### Business Rules

BR-82-01, BR-82-02, BR-82-03, BR-82-04

#### Non-Functional Requirements

—

#### Priority

—

#### Messages

—

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-82-01 | A wrongly recorded bill item is voided whole; this is not a customer right to cancel a used service. |
| BR-82-02 | An undercharge found after finalization is absorbed by Mland and never collected. |
| BR-82-03 | Confirmed cash of a closed day is never edited or deleted; an allowed reversal is a separate negative entry on the current day. |
| BR-82-04 | The booking-time allocation is immutable and cannot be voided from the ticket bill. |
