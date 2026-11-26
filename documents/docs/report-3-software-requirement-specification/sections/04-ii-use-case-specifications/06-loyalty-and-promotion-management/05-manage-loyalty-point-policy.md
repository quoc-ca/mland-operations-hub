### 6.5 Manage Loyalty Point Policy

| Use Case ID | UC-51 | Use Case Name | Manage Loyalty Point Policy |
| --- | --- | --- | --- |
| Created By |  | Created Date |  |
| Primary Actors | Manager | Secondary Actors | — |

#### Description

As a Manager, I want to view and update the shared loyalty point policy so that future retail and workshop transactions use updated rates.

#### Trigger

A Manager opens the Point Policy screen.

#### Preconditions

1. The Manager is signed in, the account is active, and the role is Manager.

#### Postconditions

1. Transactions accepted after the update use the new loyalty point policy.
2. Previously accepted transactions retain the policy recorded at their acceptance.

#### Normal Flow

1. Manager opens the Point Policy screen.
2. System displays the current earning rate, point value, minimum points per use, and percentage cap.
3. Manager updates one or more policy values.
4. System validates that rates are positive, minimum points are positive integers, and cap is valid.
5. System saves the new policy, records a change log, and confirms (MSG53).

#### Alternative Flows

None.

#### Exception Flows

1a. Actor is not a Manager or session expired → System denies access (MSG01, MSG02); only a rejected save is logged, not a rejected view or a missing sign-in.

4a. Entered policy value is invalid → System identifies the invalid value and keeps the previous policy (MSG08).

5a. Save is not confirmed or response is lost → System reports no update and keeps the last policy (MSG04).

#### Business Rules

BR-51-01, BR-51-02, BR-51-03, BR-51-04, BR-51-05

#### Non-Functional Requirements

—

#### Priority

—

#### Messages

MSG01, MSG02, MSG04, MSG08, MSG53

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-51-01 | Only a Manager may view and change the loyalty point policy; Admin manages accounts and roles only. |
| BR-51-02 | The earning rate and point value are positive, the minimum points per use is a positive whole number, and the cap is greater than 0 up to 100%; an invalid or unconfirmed change keeps the previous policy. |
| BR-51-03 | A policy change applies only to transactions accepted afterwards; earlier transactions keep their recorded policy, and a missing recorded policy is never replaced with the current one. |
| BR-51-04 | The starting policy is 1 point earned per 10,000 VND of eligible amount, 1 point worth 100 VND, at least 10 points per use, and points covering at most 10% of the amount after discounts and before points; shipping is excluded for products, and a workshop uses the package fee, not only the deposit. The same policy applies to products and workshops. |
| BR-51-05 | The Change policy form checks each field as soon as its value changes, with the same rules as on save, and starts without errors each time it is reopened. |
