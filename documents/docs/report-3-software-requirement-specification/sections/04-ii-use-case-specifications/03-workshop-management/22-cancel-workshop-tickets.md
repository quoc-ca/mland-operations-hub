### 3.19 Cancel Workshop Tickets

| Use Case ID | UC-84 | Use Case Name | Cancel Workshop Tickets |
| --- | --- | --- | --- |
| Created By |  | Created Date |  |
| Primary Actors | Guest, Member, Staff, Manager | Secondary Actors | Mail Gateway |

#### Description

As the buyer, or as Staff or Manager acting on the buyer's behalf, I want to cancel one or more unused tickets so that their reserved places are released.

#### Trigger

An actor selects one or more unused tickets in a booking group to cancel.

#### Preconditions

1. The selected tickets belong to one booking group, are unused, and their first session has not started.
2. A Member owns the group; a Guest, Staff, or Manager request carries a one-time-code proof sent to the buyer email.

#### Postconditions

1. All selected tickets are cancelled, or none; capacity of their future sessions is released.
2. The booking-time payment is not refunded.

#### Normal Flow

1. Actor selects the tickets and enters a reason.
2. System verifies the buyer proof and checks every selected ticket.
3. System cancels the tickets and applies the 24-hour benefit rule to each one.
4. System displays the outcome per ticket.

#### Alternative Flows

3a. Cancelled less than 24 hours before the first session → Capacity is released but used points are not returned.

#### Exception Flows

2a. Requester is a recipient or proof is invalid → System refuses the cancellation; a recipient must go through the buyer or shop.

2b. Any selected ticket cannot be cancelled → System cancels no tickets and reports the failing tickets.

#### Business Rules

BR-84-01, BR-84-02, BR-84-03, BR-84-04

#### Non-Functional Requirements

—

#### Priority

—

#### Messages

—

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-84-01 | Cancelling at least 24 hours before the first session returns the ticket's used points. |
| BR-84-02 | The group voucher use is restored once, while the voucher is still valid, only if every ticket ended by valid or Mland cancellation, unless an Mland cancellation already restored it. |
| BR-84-03 | Staff (within location scope) or Manager acts with a reason and the buyer proof. |
| BR-84-04 | A paid-up ticket whose bill is finalized may still be cancelled before the first session; the bill stays finalized and nothing is refunded. |
