### 10.5 Record Custom Payment Outcome

#### Primary Actors

- System

#### Secondary Actors

- Payment Gateway

#### Description

This use case (use case 71) records the verified full payment of the active quote, or its expiry.

#### Preconditions

1. A payment was started for an approved quote (use case 69).

#### Normal Flow

**Record Custom Payment Outcome**

1. The system receives a verified payment result from the Payment Gateway.
2. The system matches the order and checks the amount against the quote.
3. The system marks the quote and order paid and records the transaction reference.
4. The order enters the waiting-for-production stage.

#### Alternative Flows

**Step 1 — Result is not verified or not successful**

The order is not marked paid.

**Step 2 — Amount does not match**

The system does not accept the payment.

**Deadline passes without payment**

The quote is treated as expired; no final balance is created.

#### Postconditions

- The order is paid in full, or remains unpaid or expired.

#### Business Rules

- BR-71-01
- BR-71-02

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-71-01 | Only a verified payment result for the exact quoted amount marks the order paid. |
| BR-71-02 | An expired quote cannot be paid; a new quote version is required. |
