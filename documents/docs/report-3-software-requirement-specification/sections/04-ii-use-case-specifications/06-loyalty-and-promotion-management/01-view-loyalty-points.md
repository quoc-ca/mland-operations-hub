### 6.1 View Loyalty Points

| Use Case ID | UC-42 | Use Case Name | View Loyalty Points |
| --- | --- | --- | --- |
| Created By |  | Created Date |  |
| Primary Actors | Member | Secondary Actors | — |

#### Description

As a Member, I want to view my point balance, held and available points, point value and point history so that I know what I can redeem and how my points changed.

#### Trigger

A Member opens My Loyalty.

#### Preconditions

1. The Member is signed in and the account is active.
2. A confirmed loyalty point policy exists.

#### Postconditions

1. The Member sees the confirmed balance, held points, available points, and VND point value.
2. The Member sees a point history consistent with the balance.
3. Reloading the page after earning, using, releasing, or refunding points shows the confirmed result.
4. No point transaction is created or modified.

#### Normal Flow

**View Point Balance**

1. Member opens My Loyalty.
2. System loads the Member's loyalty account and current point policy.
3. System calculates available points as balance minus held points.
4. System displays balance, held points, available points, and the VND value per point.

**View Point History**

1. Member opens the Point history section of My Loyalty.
2. System loads the Member's point entries, newest first.
3. System displays each entry's type, points changed, timestamp, and linked order or booking reference.

#### Alternative Flows

2a. Member has no point activity yet → System displays a zero balance with guidance on earning points.

H2a. Member has no point entries yet → System displays an empty history with guidance on earning points.

#### Exception Flows

1a. Session is expired or revoked → System prompts sign-in again without showing stale data (MSG02).

1b. Signed-in Staff, Manager, Admin, or Carrier opens My Loyalty → System shows the not-found page before loading Member data.

2b. Loyalty data cannot be loaded → System shows a load error with retry instead of zero balance (MSG05).

2c. Actor requests another Member's balance → System rejects the request without revealing account existence (MSG01).

H2b. History data cannot be loaded → System shows a load error with retry instead of an empty history (MSG05).

H2c. Actor requests another Member's history → System rejects the request (MSG01).

#### Business Rules

BR-42-01, BR-42-02, BR-42-03, BR-42-04, BR-42-05

#### Non-Functional Requirements

—

#### Priority

—

#### Messages

MSG01, MSG02, MSG05

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-42-01 | A Member can view only their own loyalty account and point history; knowing another Member's identifier grants no access. My Loyalty and My Saved Vouchers are Member-only pages. |
| BR-42-02 | Available points equal the balance minus all held points; only available points can be used. |
| BR-42-03 | Each Member has one shared balance for products and workshops; points do not expire and there are no membership tiers or separate wallets. |
| BR-42-04 | Every earned, used, released and refunded entry links to its order or booking; a refund links to the original deduction. Earning, releasing and refunding are automatic results of order and booking processing (UC-44), not Member actions. |
| BR-42-05 | Point history, voucher usage, change logs and operation results are kept for at least 5 years and are not removed while a transaction is open or still referenced. |
