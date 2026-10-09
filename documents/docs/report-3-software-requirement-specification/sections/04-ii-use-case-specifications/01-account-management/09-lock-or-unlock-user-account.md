### 1.9 Lock or Unlock User Account

#### Primary Actors

Admin

#### Secondary Actors

None

#### Description

As an Admin, I want to lock or unlock a user account so that access can be restricted or restored in response to an approved operational or security decision.

#### Preconditions

1. The platform is available.
2. The Admin is authenticated and authorized to manage account status.
3. The target account exists.
4. The requested status change is supported by the account-management policy.

#### Normal Flow

**Lock or Unlock User Account**

1. The Admin opens the User Account Management function.
2. The system displays the target account's current status and permitted account information.
3. The Admin selects Lock Account or Unlock Account.
4. The system displays the effect of the status change and requests a reason or confirmation where required.
5. The Admin confirms the status change.
6. The system validates the Admin's authority and the target account state.
7. The system saves the new account status and records the acting Admin, reason, and change time.
8. If the account is locked, the system invalidates active sessions according to the security policy.
9. The system displays the updated account status.

#### Alternative Flows

**Step 2 — Target account cannot be found**

The system displays a not-found result and does not change any account status.

**Step 3 — Account already has the requested status**

The system treats the operation as an idempotent no-op and displays the current status.

**Step 4 — Admin cancels**

The system discards the requested change and preserves the current account status.

**Step 6 — Status change is not authorized or violates account policy**

The system rejects the operation, records the denied attempt where required, and leaves the account unchanged.

**Step 7 — Status update fails**

The system rolls back the incomplete operation, displays an error, and preserves the previous status.

#### Postconditions

- The target account is locked or unlocked only after an authorized confirmation.
- A locked account cannot create a new authenticated session.
- Active sessions are invalidated according to the approved security policy when an account is locked.
- The status change, reason, acting Admin, and change time are retained as audit evidence.

#### Business Rules

BR-09-01, BR-09-02, BR-09-03

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-09-01 | Only an authorized Admin may change the lock status of a platform account. |
| BR-09-02 | A locked account must not receive a new authenticated session. |
| BR-09-03 | Every account-status change must retain the acting Admin, reason where required, previous status, new status, and change time. |
