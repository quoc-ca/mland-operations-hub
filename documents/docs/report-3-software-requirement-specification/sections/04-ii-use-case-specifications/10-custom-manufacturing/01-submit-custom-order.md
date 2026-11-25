### 10.1 Submit Custom Order

#### Primary Actors

- Member

#### Secondary Actors

- Staff
- Manager

#### Description

This use case (use case 67) turns an accepted custom design request into a custom order with a quote. The Member submits the design request (use case 39); once it is accepted, Staff or a Manager prepares the quote that creates the order.

#### Preconditions

1. The Member's custom design request has been accepted (use case 40).
2. The actor preparing or approving the quote is signed in as Staff or Manager.

#### Normal Flow

**Submit Custom Order**

1. Staff or a Manager opens the accepted request.
2. The actor enters the total amount, the payment deadline and a pricing note.
3. The system checks the role, the request state, the owning Member and the amount.
4. The system creates the custom order for the request, or reuses the existing one, and stores a new quote version.
5. A quote prepared by Staff waits for approval; a Manager reviews and approves it.
6. The approved quote becomes payable and the order waits for the Member's full payment.

#### Alternative Flows

**Step 2 — Quote prepared by a Manager**

The quote is approved immediately and the order waits for full payment.

**Step 3 — Request is not accepted or has no Member owner**

The system creates no order or quote.

**Step 3 — Amount is missing or not greater than zero**

The system rejects the quote.

**Step 5 — Quote is not waiting for approval**

The system rejects the approval as already decided.

**Step 5 — Actor approving is not a Manager**

The system denies the approval.

#### Postconditions

- One custom order exists for the accepted request, with immutable quote versions.
- No payment, deposit or retail cart line is created.

#### Business Rules

- BR-67-01
- BR-67-02
- BR-67-03

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-67-01 | Only an accepted design request of a Member can become a custom order, and one request has one order. |
| BR-67-02 | Staff or a Manager prepares a quote; only a Manager approves it. The amount is greater than zero; the default payment deadline is three days. |
| BR-67-03 | No AI sets or approves a price. |
