### 1.8 Assign User Role

#### Primary Actors

Admin

#### Secondary Actors

None

#### Description

As an Admin, I want to assign an approved operational role to a platform account so that the account receives only the authorization required for its responsibilities.

#### Preconditions

1. The platform is available.
2. The Admin is authenticated and authorized to assign roles.
3. The target account exists and is eligible for role assignment.
4. The target role is defined in the platform's approved role catalogue.

#### Normal Flow

**Assign User Role**

1. The Admin opens the User Role Assignment function.
2. The system displays searchable accounts and their current role assignments.
3. The Admin selects a target account.
4. The system displays the approved roles and the current role for that account.
5. The Admin selects the new role and reviews the resulting authorization scope.
6. The system validates the role assignment and checks the Admin's authority to apply it.
7. The Admin confirms the assignment.
8. The system saves the role assignment, records the acting Admin and change time, and applies the role to future authorization checks.
9. The system displays the updated role assignment.

#### Alternative Flows

**Step 3 — Target account cannot be found**

The system displays a not-found result and does not change any role assignment.

**Step 4 — No approved role is available**

The system rejects the operation and informs the Admin that the role catalogue must be configured by an authorized Admin.

**Step 6 — Admin is not authorized for the selected assignment**

The system rejects the assignment, records the denied attempt according to the security policy, and leaves the current role unchanged.

**Step 7 — Admin cancels**

The system discards the proposed assignment and preserves the current role.

**Step 8 — Role assignment fails**

The system rolls back the incomplete operation, displays an error, and preserves the previous authorization state.

#### Postconditions

- The target account has the confirmed approved role assignment.
- Future authorization checks use the saved role according to the platform policy.
- The previous role, new role, acting Admin, and change time are retained as audit evidence.
- Account credentials and lock status are unchanged.

#### Business Rules

BR-08-01, BR-08-02, BR-08-03

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-08-01 | Only an authorized Admin may assign a platform role. |
| BR-08-02 | A role assignment must use a role from the approved platform role catalogue. |
| BR-08-03 | Role assignment must not grant permissions outside the selected role's configured policy. |
