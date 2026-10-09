### 6.1 View Loyalty Point Balance

#### Primary Actors

- Member

#### Secondary Actors

- None

#### Description

This use case allows a member to view the current loyalty point balance and the point status that can be used in an eligible order. The system calculates the displayed balance from the member's valid loyalty ledger and active loyalty policy.

#### Preconditions

1. The member is signed in.
2. The member's account and loyalty profile are available.
3. An active loyalty policy is configured by the system administrator.

#### Normal Flow

**View Loyalty Point Balance**

1. The member opens the loyalty section.
2. The system retrieves the member's valid loyalty point ledger and active policy.
3. The system calculates the available, held, expired, and redeemed point amounts when applicable.
4. The system displays the spendable point balance, applicable limits, and the latest balance update time.

#### Alternative Flows

**Step 2 — No loyalty activity**

1. The member has no loyalty point transaction.
2. The system displays a zero balance and explains how points can be earned.

**Step 2 — Loyalty policy is unavailable**

1. No active loyalty policy can be retrieved.
2. The system displays the recorded balance and temporarily hides policy-dependent redemption limits.

**Step 2 — Data retrieval failure**

1. The system cannot retrieve the latest point balance.
2. The system displays an error message and allows the member to retry.

#### Postconditions

- The member can view the latest available loyalty point balance.
- No points are earned, held, redeemed, or changed by this use case.
- The displayed balance is based on the member's own account.

#### Business Rules

- BR-38-01
- BR-38-02

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-38-01 | Loyalty points are non-cash benefits and may be used only under the active loyalty policy. |
| BR-38-02 | The system must display only the loyalty balance belonging to the signed-in member. |

