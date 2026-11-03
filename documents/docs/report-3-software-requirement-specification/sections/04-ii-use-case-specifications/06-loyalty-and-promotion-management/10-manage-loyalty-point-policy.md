### 6.10 Manage Loyalty Point Policy

#### Primary Actors

- Manager

#### Secondary Actors

- None

#### Description

This use case allows a Manager to view and update the shared loyalty point policy used for both retail products and workshops, while transactions that were already accepted keep the policy recorded at that time.

#### Preconditions

1. The Manager is signed in, the account is active, and the current role is Manager.

#### Normal Flow

**Manage Loyalty Point Policy**

1. The Manager opens the loyalty point policy.
2. The system shows the current values. The initial policy is 10,000 VND per earned point, 100 VND per redeemed point, at least 10 points per use and a cap of 10% of the amount after the program discount.
3. The Manager changes one or more values.
4. The system checks that the earning rate and point value are positive, the minimum points per use is a positive whole number, and the cap is greater than 0 up to 100%.
5. The system saves the new policy, records a change log and confirms (MSG53).

#### Alternative Flows

**Step 4 — Invalid value**

The system identifies the invalid value (MSG08), rejects the change and keeps the previous valid policy.

**Any step — Actor is not a Manager or the session has expired**

The system rejects the request (MSG01, MSG02). Admin cannot view or change the point policy.

**Step 5 — Save not confirmed or response lost**

The system does not report the update (MSG04); new transactions continue to use the last confirmed policy.

#### Postconditions

- Transactions accepted after the change use the new policy.
- Transactions accepted earlier, including workshops completed after the change, keep the policy and calculation recorded at acceptance.

#### Business Rules

- BR-51-01
- BR-51-02
- BR-51-03

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-51-01 | Only a Manager may view and change the loyalty point policy; Admin manages accounts and roles only. |
| BR-51-02 | The earning rate and point value are positive, the minimum points per use is a positive whole number, and the cap is greater than 0 up to 100%; an invalid or unconfirmed change keeps the previous policy. |
| BR-51-03 | A policy change applies only to transactions accepted afterwards; earlier transactions keep their recorded policy, and a missing recorded policy is never replaced with the current one. |
