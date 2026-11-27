### 6.4 UC-66 Manage Loyalty Point Policy

#### UC Name

UC-66 Manage Loyalty Point Policy

#### Primary Actors

Manager

#### Secondary Actors

None

#### Description

As a Manager, I want to update the shared loyalty point policy so that future retail orders and workshop bookings use the new rates.

#### Preconditions

- The Manager is signed in with an active account and the Manager role.
- A current loyalty point policy exists.

#### Normal Flow

***Manage Loyalty Point Policy***

1. The Manager opens Point policy.
2. The system shows the earning amount per point, point value, minimum points per use and point-discount cap.
3. The system shows the rounding mode, the policy version and its creation time.
4. The Manager selects Edit.
5. The system opens the Change policy form with the current values and no errors.
6. The Manager changes one or more values.
7. The system checks each value as it changes.
8. The Manager selects Save.
9. The system validates the values and the policy version.
10. The system saves the new policy version and records the change in the audit log.
11. The system shows the new policy and confirms the update (MSG52).

#### Alternative Flows

***Step 1.1 Signed-in Actor Is Not a Manager***

1. The system shows the not-found page without loading the policy.
2. The use case ends.

***Step 2.1 Policy Cannot Be Loaded***

1. The system informs the Manager that the data could not be loaded (MSG05).
2. The system offers Retry.
3. The use case continues at step 2.

***Step 7.1 Value Is Invalid***

1. The system shows the rule under the invalid field (MSG08).
2. The Manager corrects the value.
3. The use case continues at step 7.

***Step 8.1 Manager Cancels the Change***

1. The Manager selects Cancel.
2. The system closes the form and keeps the current policy.
3. The use case ends.

***Step 9.1 Policy Was Changed by Someone Else***

1. The system rejects the save made against an older version (MSG03).
2. The Manager reloads the latest policy.
3. The use case continues at step 6.

***Step 9.2 Actor Lost the Manager Role or Session***

1. The system rejects the request (MSG01) or asks the Manager to sign in again (MSG02).
2. The system records the refused request in the audit log.
3. The use case ends with the previous policy kept.

***Step 10.1 Result Is Not Confirmed***

1. The system informs the Manager that the result could not be confirmed (MSG04).
2. The system keeps the typed values and the previous policy.
3. The Manager selects Retry with the same request.
4. The use case continues at step 9.

#### Postconditions

- Orders and bookings accepted after the save use the new policy.
- Orders and bookings accepted earlier keep the policy recorded at acceptance.
- The previous policy version is kept and the change is in the audit log.

#### Business Rule

BR-09, BR-15, BR-19, BR-20, BR-24, BR-203, BR-205
