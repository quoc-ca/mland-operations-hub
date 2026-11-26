### 6.2 View Loyalty Point History

| Use Case ID | UC-43 | Use Case Name | View Loyalty Point History |
| --- | --- | --- | --- |
| Created By |  | Created Date |  |
| Primary Actors | Member | Secondary Actors | — |

#### Description

As a Member, I want to review my loyalty point history so that I can track points earned, used, released, and refunded.

#### Trigger

A Member opens Point History from My Loyalty.

#### Preconditions

1. The Member is signed in and the account is active.

#### Postconditions

1. The Member sees a point history consistent with their balance.
2. No point transaction is created or modified.

#### Normal Flow

1. Member opens Point History from My Loyalty.
2. System loads the Member's point entries, newest first.
3. System displays each entry's type, points changed, timestamp, and linked order or booking reference.

#### Alternative Flows

2a. Member has no point entries yet → System displays an empty history with guidance on earning points.

#### Exception Flows

1a. Session is expired or revoked → System asks the Member to sign in again (MSG02).

2a. History data cannot be loaded → System shows a load error with retry instead of an empty history (MSG05).

2b. Actor requests another Member's history → System rejects the request (MSG01).

#### Business Rules

BR-43-01, BR-43-02, BR-43-03

#### Non-Functional Requirements

—

#### Priority

—

#### Messages

MSG01, MSG02, MSG05

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-43-01 | A Member can view only their own point history. |
| BR-43-02 | Every earned, used, released and refunded entry links to its order or booking; a refund links to the original deduction. |
| BR-43-03 | Point history, voucher usage, change logs and operation results are kept for at least 5 years and are not removed while a transaction is open or still referenced. |
