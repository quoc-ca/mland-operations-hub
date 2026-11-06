### 6.1 View Loyalty Point Balance

#### Primary Actors

- Member

#### Secondary Actors

- None

#### Description

This use case allows a Member to open My Loyalty and see their own point balance, the points currently held by pending orders or bookings, the points available to use, and the current value of one point. One shared balance is used for both retail products and workshops.

#### Preconditions

1. The Member is signed in through the shared Account Management sign-in and the account is active.
2. A confirmed loyalty point policy exists.

#### Normal Flow

**View Loyalty Point Balance**

1. The Member opens My Loyalty.
2. The system loads the Member's own loyalty account and the current point policy.
3. The system calculates available points as the balance minus the points held by pending orders or bookings.
4. The system displays the balance, held points, available points and the value of one point in VND. For example, a balance of 200 with 50 held shows 150 available.
5. The Member may continue to UC-43 View Loyalty Point History.

#### Alternative Flows

**Step 1 — Session expired or revoked**

The system asks the Member to sign in again (MSG02) and does not show the last known balance as current.

**Step 2 — Data cannot be loaded**

The system shows a load error with a retry option (MSG05) instead of a zero balance.

**Step 2 — Member has no point activity yet**

The system displays a balance of 0 with guidance on how points are earned.

**Step 2 — Request for another Member's balance**

The system rejects the request without revealing whether the account exists (MSG01).

#### Postconditions

- The Member sees the confirmed balance, held and available points.
- After points are earned, used, released or refunded, reloading the page shows the confirmed result.
- No point transaction is created.

#### Business Rules

- BR-42-01
- BR-42-02
- BR-42-03

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-42-01 | A Member can view only their own loyalty account; knowing another Member's identifier grants no access. |
| BR-42-02 | Available points equal the balance minus all held points; only available points can be used. |
| BR-42-03 | Each Member has one shared balance for products and workshops; points do not expire and there are no membership tiers or separate wallets. |
