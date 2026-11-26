### 1.7 Assign User Role

| Use Case ID | UC-07 | Use Case Name | Assign User Role |
| --- | --- | --- | --- |
| Created By |  | Created Date |  |
| Primary Actors | Admin | Secondary Actors | None |

#### Description

As an Admin, I want to assign an approved operational role to a platform account so that the account receives only required authorization.

#### Trigger

An Admin opens an account's role and location controls.

#### Preconditions

1. The platform is available.
2. The Admin is authenticated and authorized to assign roles.
3. The target account exists and is eligible for role assignment.
4. The target role is a fixed platform role and the transition is allowed.

#### Postconditions

1. The target account has the confirmed approved role assignment.
2. Future authorization checks use the saved role.
3. Previous role, new role, acting Admin, and change time are retained as audit evidence.
4. Account credentials and lock status remain unchanged.

#### Normal Flow

1. Admin opens account details and role controls.
2. System displays searchable accounts and their current role assignments.
3. Admin selects a target account.
4. System displays the approved roles and the account's current role.
5. Admin selects the new role and reviews the resulting authorization scope.
6. System validates the role assignment and the Admin's authority.
7. Admin confirms the assignment.
8. System saves the role assignment with the acting Admin and change time.
9. System displays the updated role assignment.

#### Alternative Flows

None.

#### Exception Flows

3a. Target account cannot be found → System displays a not-found result and changes no role.

4a. Requested role is not an approved fixed role → System rejects the operation and informs Admin that only fixed platform roles apply.

6a. Admin is not authorized for the assignment → System rejects it, logs the denied attempt, and leaves the current role unchanged.

7a. Admin cancels → System discards the proposed assignment and preserves the current role.

8a. Role assignment fails → System rolls back the operation, shows an error, and preserves the previous state.

#### Business Rules

BR-08-01, BR-08-02, BR-08-03

#### Non-Functional Requirements

—

#### Priority

—

#### Messages

—

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-08-01 | Only an authorized Admin may assign a platform role. |
| BR-08-02 | An account has one fixed role; V1 does not configure roles or permissions. |
| BR-08-03 | A role change grants only the selected fixed role's permitted actions after the server saves it. |
