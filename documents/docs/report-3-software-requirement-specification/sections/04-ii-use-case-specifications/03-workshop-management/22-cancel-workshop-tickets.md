### 3.22 Cancel Workshop Tickets

#### Primary Actors

Guest, Member, Staff, Manager

#### Secondary Actors

Mail Gateway

#### Description

As the buyer, or as Staff or Manager acting on the buyer's behalf, I want to cancel one or more unused tickets so that their reserved places are released.

#### Preconditions

1. The selected tickets belong to one booking group, are unused, and their first session has not started.
2. A Member owns the group; a Guest, Staff, or Manager request carries a one-time-code proof sent to the buyer contact email.

#### Postconditions

- All selected tickets are cancelled, or none; capacity of their future sessions is released.
- The booking-time payment is not refunded.

#### Normal Flow

**Cancel Workshop Tickets**

1. The actor selects the tickets and enters a reason.
2. The system verifies the buyer proof and checks every selected ticket.
3. The system cancels the tickets and applies the 24-hour benefit rule to each one.
4. The system shows the outcome per ticket.

#### Alternative Flows

**Step 2 — Recipient or invalid proof**

The system refuses the cancellation; a recipient must go through the buyer or the shop.

**Step 2 — Any ticket cannot be cancelled**

No ticket is cancelled and the failing tickets are reported.

**Step 3 — Less than 24 hours before the first session**

Capacity is released but used points are not returned.

#### Business Rules

BR-84-01, BR-84-02, BR-84-03, BR-84-04

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-84-01 | Cancelling at least 24 hours before the first session returns the ticket's used points. |
| BR-84-02 | The group voucher use is restored once, while the voucher is still valid, only if every ticket ended by valid or Mland cancellation, unless an Mland cancellation already restored it. |
| BR-84-03 | Staff (within location scope) or Manager acts with a reason and the buyer proof. |
| BR-84-04 | A paid-up ticket whose folio is finalized may still be cancelled before the first session; the folio stays finalized and nothing is refunded. |
