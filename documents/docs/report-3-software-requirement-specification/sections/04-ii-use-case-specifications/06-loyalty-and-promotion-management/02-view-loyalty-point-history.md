### 6.2 View Loyalty Point History

#### Primary Actors

- Member

#### Secondary Actors

- None

#### Description

This use case allows a member to review the loyalty point ledger for their account. The history includes points earned, redeemed, held, adjusted, or expired, together with the related order or business event when available.

#### Preconditions

1. The member is signed in.
2. The member's loyalty point ledger is available.
3. The member is authorized to access only their own loyalty data.

#### Normal Flow

**View Loyalty Point History**

1. The member opens the loyalty point history.
2. The system retrieves the member's loyalty transactions.
3. The member optionally filters the history by transaction type or date range.
4. The system displays each transaction's date, type, amount, balance impact, status, and related order reference when available.
5. The member opens a transaction to view its details.

#### Alternative Flows

**Step 2 — No transaction history**

1. The member has no loyalty point transactions.
2. The system displays an empty-state message.

**Step 3 — Invalid filter**

1. The selected date range or filter is invalid.
2. The system rejects the filter and asks the member to provide valid criteria.

**Step 2 — History retrieval failure**

1. The system cannot retrieve the ledger.
2. The system displays an error message and allows the member to retry.

#### Postconditions

- The member can view the permitted loyalty point transaction history.
- No loyalty point transaction is modified by this use case.
- Sensitive information belonging to another member is not disclosed.

#### Business Rules

- BR-39-01
- BR-39-02

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-39-01 | Loyalty point history must be traceable to an account, business event, and transaction status when applicable. |
| BR-39-02 | Loyalty point history is read-only for members. |

