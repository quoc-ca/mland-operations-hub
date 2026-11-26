### 3.22 Process Refund Request

| Use Case ID | UC-90 | Use Case Name | Process Refund Request |
| --- | --- | --- | --- |
| Created By |  | Created Date |  |
| Primary Actors | Guest, Member, Staff, Manager | Secondary Actors | Mail Gateway |

#### Description

As a payer or Mland operator, I want to submit the receiving account and process the refund so that owed money is returned.

#### Trigger

A payer opens a refund-account link, an operator records the account, or a Manager opens a refund request.

#### Preconditions

1. A refund request exists in a state that allows the selected action.
2. A payer holds a usable 7-day single-use link.
3. Assigned Staff or a Manager may enter the account at the counter; group-level refunds accept the link only.
4. Only an active Manager decides the refund.

#### Postconditions

1. The account number is stored encrypted and the request waits for Manager approval.
2. Every step records the actor, time, and proof.
3. Bill and booking consequences apply only after the request is refunded.

#### Normal Flow

**Submit Refund Account**

1. Actor opens the account link.
2. Actor enters the bank, account number, and holder name.
3. System displays the details back for confirmation.
4. Actor confirms the details.
5. System saves the account and moves the request to pending approval.

**Approve and Record Refund**

1. Actor reviews a pending request and approves it with a reason.
2. Actor transfers the money outside the system.
3. Actor records the bank reference, with an optional receipt image.
4. Actor confirms the request as refunded after matching the bank statement.

#### Alternative Flows

S1a. Assigned Staff or Manager enters the account at the counter → System records the actor, channel, and time.

S1b. Assigned Staff resends the link for an assigned-session refund → System sends a new account link.

P1a. Account is not received after 30 days → Manager may close the request as unreachable and reopen it later.

#### Exception Flows

S1c. Link is used, revoked, or expired → System refuses it; the customer asks the shop for a new link.

P1b. Refund is refused, returned, or unmatched → Manager returns it to pending account with a reason; System sends a new link.

P4a. Request is already refunded → Refunded is final; a late bank return is handled outside the system.

#### Business Rules

BR-89-01, BR-89-02, BR-89-03, BR-90-01, BR-90-02, BR-90-03

#### Non-Functional Requirements

—

#### Priority

—

#### Messages

—

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-89-01 | The system performs no bank name lookup and does not compare the holder with the payer. |
| BR-89-02 | The full account number is shown only to the Manager and transferring actor, and never written to email, logs, or audit. |
| BR-89-03 | Without a reply, reminders are sent on days 3 and 7. |
| BR-90-01 | The system never calls VNPay Refund; there is no terminal rejection of a refund. |
| BR-90-02 | The total refunded or pending for a ticket never exceeds the money collected for it. |
| BR-90-03 | Staff may view only ticket refunds for sessions assigned to them and may enter the recipient account or resend its link; every refund decision stays with the Manager. Group-level refunds are Manager-only in the queue. |
