### 10.3 Pay Custom Order Quote

#### Primary Actors

- Member

#### Secondary Actors

- Payment Gateway

#### Description

This use case (use case 69) lets the owning Member pay the whole approved quote through VNPay from the custom order, or ask for a new quote after it expires.

#### Preconditions

1. The Member is signed in and owns the custom order.
2. The latest quote is approved, unpaid and not expired.

#### Normal Flow

**Pay Custom Order Quote**

1. The Member opens the custom order.
2. The system shows the amount, quote version and payment deadline.
3. The Member selects full payment.
4. The system checks ownership, quote state and deadline.
5. The system creates a VNPay payment for the exact quoted amount and sends the Member to VNPay.
6. The verified result is recorded under use case 71.

#### Alternative Flows

**Step 4 — Quote has expired**

The system marks the quote expired and creates no payment; the Member may request a new quote.

**Step 4 — Quote not approved or order already paid**

The system rejects the payment.

**Step 4 — Actor does not own the order**

The system denies access.

**After expiry — Member requests a new quote**

The order returns to waiting for a quote; earlier quote versions are kept. A request is refused while a quote is still valid.

#### Postconditions

- A payment attempt exists for the quoted amount; the browser return alone does not mark the order paid.

#### Business Rules

- BR-69-01
- BR-69-02

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-69-01 | A custom order is paid in one payment equal to the quote; there is no deposit, partial payment or later balance. |
| BR-69-02 | Only an approved, unexpired quote of an own order can be paid. |
