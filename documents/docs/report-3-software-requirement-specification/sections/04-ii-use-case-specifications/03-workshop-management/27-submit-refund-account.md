### 3.27 Submit Refund Account

#### Primary Actors

Guest, Member, Staff, Manager

#### Secondary Actors

Mail Gateway

#### Description

As the Guest or Member payer, or as Staff or a Manager at the counter, I want to enter and confirm the receiving bank account so that a refund can be transferred.

#### Preconditions

1. A refund request is waiting for an account.
2. The payer holds a usable single-use link (valid 7 days), or a Manager or Staff assigned to the ticket records the account at the counter.

#### Postconditions

- The account number is stored encrypted and the request waits for Manager approval.

#### Normal Flow

**Submit Refund Account**

1. The payer opens the account link.
2. The payer enters the bank, account number, and holder name.
3. The system shows the details back and requires explicit confirmation.
4. The system saves the account and moves the request to pending approval.

#### Alternative Flows

**Step 1 — Counter entry**

Staff or a Manager enters and confirms the payer's account; the actor, channel, and time are recorded.

**Step 1 — Link used, revoked, or expired**

The system refuses it and the customer asks the shop for a new link.

**Step 1 — Refund has no ticket**

A group-level refund accepts the account only through the link.

#### Business Rules

BR-89-01, BR-89-02, BR-89-03

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-89-01 | The system performs no bank name lookup and does not compare the holder with the payer. |
| BR-89-02 | The full account number is shown only to the Manager and transferring actor, and never written to email, logs, or audit. |
| BR-89-03 | Without a reply, reminders are sent on days 3 and 7. |
