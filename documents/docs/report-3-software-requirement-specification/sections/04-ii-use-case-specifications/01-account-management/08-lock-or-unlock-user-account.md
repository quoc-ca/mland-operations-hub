### 1.8 Lock or Unlock User Account

| Use Case ID | UC-08 | Use Case Name | Lock or Unlock User Account |
| --- | --- | --- | --- |
| Created By |  | Created Date |  |
| Primary Actors | Admin | Secondary Actors | None |

#### Description

As an Admin, I want to lock or unlock a user account so that access can be restricted or restored per operational or security decisions.

#### Trigger

An Admin selects Lock Account or Unlock Account in User Account Management.

#### Preconditions

1. The platform is available.
2. The Admin is authenticated and authorized to manage account status.
3. The target account exists.
4. The requested status change is supported by account-management policy.

#### Postconditions

1. The target account is locked or unlocked only after authorized confirmation.
2. A locked account cannot create a new authenticated session.
3. Active sessions are invalidated per security policy when an account is locked.
4. Status change, reason, acting Admin, and change time are retained as audit evidence.

#### Normal Flow

1. Admin opens User Account Management.
2. System displays the target account's current status and permitted information.
3. Admin selects Lock Account.
4. System displays the status-change effect and requests a reason or confirmation.
5. Admin confirms the status change.
6. System validates the Admin's authority and the target account state.
7. System saves the new status with the acting Admin, reason, and change time.
8. System invalidates active sessions for the locked account per security policy.
9. System displays the updated account status.

#### Alternative Flows

3a. Admin selects Unlock Account → System validates and restores active status without session invalidation.

3b. Account already has the requested status → System treats the operation as an idempotent no-op and displays the current status.

#### Exception Flows

2a. Target account cannot be found → System displays a not-found result and leaves status unchanged.

5a. Admin cancels → System discards the requested change and preserves the current status.

6a. Status change is unauthorized or violates policy → System rejects it, logs the denied attempt where required, and leaves the account unchanged.

7a. Status update fails → System rolls back the operation, shows an error, and preserves the previous status.

#### Business Rules

BR-09-01, BR-09-02, BR-09-03

#### Non-Functional Requirements

—

#### Priority

—

#### Messages

—

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-09-01 | Only an authorized Admin may change the lock status of a platform account. |
| BR-09-02 | A locked account must not receive a new authenticated session. |
| BR-09-03 | Every account-status change must retain the acting Admin, reason where required, previous status, new status, and change time. |
