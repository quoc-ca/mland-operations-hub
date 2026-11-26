### 1.6 Manage User Account

| Use Case ID | UC-06 | Use Case Name | Manage User Account |
| --- | --- | --- | --- |
| Created By |  | Created Date |  |
| Primary Actors | Admin | Secondary Actors | Mail Gateway |

#### Description

As an Admin, I want to create, view, and update platform user accounts so that customer and internal account records stay accurate.

#### Trigger

An Admin opens User Account Management.

#### Preconditions

1. The platform is available.
2. The Admin is authenticated and authorized to manage user accounts.
3. The requested account operation is within the Admin's permitted scope.
4. Required account information is available for the selected operation.

#### Postconditions

1. A valid account is created or updated per the confirmed operation.
2. System records the acting Admin, change time, and audit details.
3. Role assignment and account locking remain separate (UC-07, UC-08).
4. Sensitive credentials are never displayed or written to ordinary logs.

#### Normal Flow

1. Admin opens User Account Management.
2. System displays searchable user-account records and permitted account fields.
3. Admin selects an existing account.
4. Admin updates the permitted account information.
5. System validates the account data and checks for duplicate identity information.
6. Admin reviews and confirms the operation.
7. System saves the account record with the acting Admin and change time.
8. System displays the saved account record.

#### Alternative Flows

3a. Admin starts a new Staff or Manager account → System creates the pending account, records a mandatory invitation, queues email after commit, and shows actual delivery state.

#### Exception Flows

3b. Account cannot be found → System displays a not-found result and allows retry without exposing unrelated data.

5a. Email belongs to a Member → Admin may export a DEMO JSON preview of obligations; creation stops until live retirement is approved; Admin/Carrier cannot be replaced.

5b. Required data is missing, invalid, or duplicated → System shows validation errors and does not save the account.

6a. Admin cancels → System discards unsaved changes and leaves the account unchanged.

7a. Account save fails → System rolls back the operation, shows an error, and preserves the previous state.

3c. Invitation email fails delivery → Account remains recorded and the notification failure is logged for retry or follow-up.

#### Business Rules

BR-07-01, BR-07-02, BR-07-03

#### Non-Functional Requirements

—

#### Priority

—

#### Messages

—

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-07-01 | Only an authorized Admin may create or update platform user-account records through this use case. |
| BR-07-02 | A new Staff or Manager requires an intended email, a bound Firebase UID, and active location assignment. The Admin cannot supply its password or skip the invitation. |
| BR-07-03 | Role assignment and account locking are separate controlled operations and must not be changed implicitly by account editing. |
