### 10.1 Submit Custom Order

| Use Case ID | UC-67 | Use Case Name | Submit Custom Order |
| --- | --- | --- | --- |
| Created By |  | Created Date |  |
| Primary Actors | Staff; Manager | Secondary Actors | Member |

#### Description

As Staff or a Manager, I want to turn an accepted custom design request into a custom order with a quote so that the Member can review and pay for it.

#### Trigger

Staff or a Manager opens an accepted custom design request to prepare a quote.

#### Preconditions

1. The Member's custom design request has been accepted (use case 40).
2. The actor preparing or approving the quote is signed in as Staff or Manager.

#### Postconditions

1. One custom order exists for the accepted request, with immutable quote versions.
2. No payment, deposit, or retail cart line is created.

#### Normal Flow

1. Staff opens the accepted custom design request.
2. Staff enters the total amount, payment deadline (24–72 hours), and pricing note.
3. System checks role, request state, owning Member, amount and payment deadline.
4. Staff confirms **Duyệt gửi báo giá**; the system records the accepted decision and creates or updates the custom order with a new quote version.
5. The order enters `QUOTE_PENDING`; a Manager reviews and approves the Staff-prepared quote.
6. System marks the quote payable and sets the order to `FULL_PAYMENT_PENDING`, then submits the quote-approved notification to the Member when a recipient email is available.

#### Alternative Flows

2a. Quote prepared by a Manager → System approves the quote immediately and sets the order to `FULL_PAYMENT_PENDING`.

4a. Custom order already exists for the request → System reuses the existing order and stores a new immutable quote version.

#### Exception Flows

3a. Request is not accepted or has no Member owner → System creates no order or quote.

3b. Amount is missing or not greater than zero → System rejects the quote.

5a. Quote is not waiting for approval → System rejects the approval as already decided.

5b. Actor approving is not a Manager → System denies the approval.

#### Business Rules

BR-67-01, BR-67-02, BR-67-03

#### Non-Functional Requirements

—

#### Priority

—

#### Messages

—

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-67-01 | Only an accepted design request of a Member can become a custom order, and one request has one order. |
| BR-67-02 | Staff or a Manager prepares a quote; only a Manager approves it. The amount is greater than zero; the default payment deadline is three days. |
| BR-67-03 | No AI sets or approves a price. |
