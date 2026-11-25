### 1.6 Manage User Account

#### Primary Actors

Admin

#### Secondary Actors

Mail Gateway

#### Description

As an Admin, I want to create, view, and update platform user accounts so that the system maintains accurate account records for customers and internal operational users.

#### Preconditions

1. The platform is available.
2. The Admin is authenticated and authorized to manage user accounts.
3. The requested account operation is within the Admin's permitted scope.
4. Required account information is available for the selected operation.

#### Normal Flow

**Manage User Account**

1. The Admin opens the User Account Management function.
2. The system displays searchable user-account records and permitted account fields.
3. The Admin selects an existing account or starts a new account record.
4. The Admin enters or updates the permitted account information.
5. The system validates the account data and checks for duplicate identity information.
6. The Admin reviews and confirms the operation.
7. The system creates or updates the account record and records the acting Admin and change time.
8. For a new Staff or Manager account, the system records a mandatory invitation with the pending Account before queuing its email after commit.
9. The system displays the saved account and the invitation's actual delivery state; creation alone does not mean the email was sent.

#### Alternative Flows

**Email belongs to a Member:** Admin may review and export a JSON preview of the Member's retail and workshop obligations. Current preview records are marked DEMO and cannot prove that accepted work is clear. Live retirement and email release remain deferred; creation stops until real obligation checks and a separate approval are available. An Admin or Carrier identity cannot be replaced through this flow.

**Step 3 — Account cannot be found**

The system displays a not-found result and allows the Admin to retry the search without exposing unrelated account data.

**Step 5 — Required data is missing, invalid, or duplicated**

The system displays validation errors and does not create or update the account.

**Step 6 — Admin cancels**

The system discards unsaved changes and leaves the existing account unchanged.

**Step 7 — Account save fails**

The system rolls back the incomplete operation, displays an error, and preserves the previous account state.

**Step 8 — Account notification cannot be delivered**

The account operation remains recorded, while the notification failure is recorded for retry or operational follow-up.

#### Postconditions

- A valid account is created or updated according to the confirmed operation.
- The system records the acting Admin, change time, and applicable audit information.
- This use case does not assign roles or change account lock status; those actions belong to UC7 and UC8.
- Sensitive credentials are not displayed or written to ordinary logs.

#### Business Rules

BR-07-01, BR-07-02, BR-07-03

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-07-01 | Only an authorized Admin may create or update platform user-account records through this use case. |
| BR-07-02 | A new Staff or Manager requires an intended email, a bound Firebase UID, and active location assignment. The Admin cannot supply its password or skip the invitation. |
| BR-07-03 | Role assignment and account locking are separate controlled operations and must not be changed implicitly by account editing. |
