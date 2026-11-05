### 6.2 View Loyalty Point History

#### Primary Actors

- Member

#### Secondary Actors

- None

#### Description

This use case allows a Member to review the history of their loyalty points. Each entry shows points earned, used, released or refunded, the number of points changed and the retail order or workshop booking it belongs to.

#### Preconditions

1. The Member is signed in and the account is active.

#### Normal Flow

**View Loyalty Point History**

1. The Member opens the point history from My Loyalty.
2. The system loads the Member's own point entries, newest first.
3. The system displays each entry's type (earned, used, released or refunded), the points changed, the time and the linked order or booking reference.
4. The Member may open the linked order or booking.

#### Alternative Flows

**Step 1 — Session expired or revoked**

The system asks the Member to sign in again (MSG02).

**Step 2 — Data cannot be loaded**

The system shows a load error with a retry option (MSG05) and does not present it as an empty history.

**Step 2 — No entries yet**

The system shows an empty history with guidance on how points are earned.

**Step 2 — Request for another Member's history**

The system rejects the request (MSG01).

#### Postconditions

- The Member sees a consistent history that matches the balance.
- No point transaction is created or changed.

#### Business Rules

- BR-43-01
- BR-43-02
- BR-43-03

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-43-01 | A Member can view only their own point history. |
| BR-43-02 | Every earned, used, released and refunded entry links to its order or booking; a refund links to the original deduction. |
| BR-43-03 | Point history, voucher usage, change logs and operation results are kept for at least 5 years and are not removed while a transaction is open or still referenced. |
