### 6.1 View Loyalty Point Balance

| Use Case ID | UC-42 | Use Case Name | View Loyalty Point Balance |
| --- | --- | --- | --- |
| Created By |  | Created Date |  |
| Primary Actors | Member | Secondary Actors | — |

#### Description

As a Member, I want to view my loyalty point balance, held points, available points, and point value so that I know what I can redeem.

#### Trigger

A Member opens My Loyalty.

#### Preconditions

1. The Member is signed in and the account is active.
2. A confirmed loyalty point policy exists.

#### Postconditions

1. The Member sees the confirmed balance, held points, available points, and VND point value.
2. Reloading the page after earning, using, releasing, or refunding points shows the confirmed result.
3. No point transaction is created.

#### Normal Flow

1. Member opens My Loyalty.
2. System loads the Member's loyalty account and current point policy.
3. System calculates available points as balance minus held points.
4. System displays balance, held points, available points, and the VND value per point.

#### Alternative Flows

2a. Member has no point activity yet → System displays a zero balance with guidance on earning points.

4a. Member selects Point History → System opens View Loyalty Point History (UC-43).

#### Exception Flows

1a. Session is expired or revoked → System prompts sign-in again without showing stale balance (MSG02).

2a. Loyalty data cannot be loaded → System shows a load error with retry instead of zero balance (MSG05).

2b. Actor requests another Member's balance → System rejects the request without revealing account existence (MSG01).

#### Business Rules

BR-42-01, BR-42-02, BR-42-03

#### Non-Functional Requirements

—

#### Priority

—

#### Messages

MSG01, MSG02, MSG05

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-42-01 | A Member can view only their own loyalty account; knowing another Member's identifier grants no access. |
| BR-42-02 | Available points equal the balance minus all held points; only available points can be used. |
| BR-42-03 | Each Member has one shared balance for products and workshops; points do not expire and there are no membership tiers or separate wallets. |
