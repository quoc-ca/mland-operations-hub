### 3.27 Submit Refund Account

| Use Case ID | UC-89 | Use Case Name | Submit Refund Account |
| --- | --- | --- | --- |
| Created By |  | Created Date |  |
| Primary Actors | Guest, Member, Staff, Manager | Secondary Actors | Mail Gateway |

#### Description

As the Guest or Member payer, or as Staff or Manager at the counter, I want to enter and confirm the receiving bank account so that a refund can be transferred.

#### Trigger

A payer opens a refund-account link, or an operator records the account at the counter.

#### Preconditions

1. A refund request is waiting for an account.
2. The payer holds a usable 7-day single-use link, or assigned Staff or Manager acts at the counter (link-only for group-level refunds).

#### Postconditions

1. The account number is stored encrypted and the request waits for Manager approval.

#### Normal Flow

1. Actor opens the account link.
2. Actor enters the bank, account number, and holder name.
3. System displays the details back and requires explicit confirmation.
4. System saves the account and moves the request to pending approval.

#### Alternative Flows

1a. Counter entry by assigned Staff or Manager → Operator enters and confirms the payer account; actor, channel, and time are recorded.

1b. Staff resends account link → Allowed only for refunds in the Staff member's assigned-session scope.

1c. Group-level refund without a ticket → Account is accepted only through the link.

#### Exception Flows

1a. Link is used, revoked, or expired → System refuses it and the customer asks the shop for a new link.

#### Business Rules

BR-89-01, BR-89-02, BR-89-03

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
