### 1.4 Change Password

| Use Case ID | UC-04 | Use Case Name | Change Password |
| --- | --- | --- | --- |
| Created By |  | Created Date |  |
| Primary Actors | Member, Staff, Manager, Admin, Carrier | Secondary Actors | Firebase Authentication |

#### Description

As an authenticated user, I want to change my own password via Firebase so that my credential stays updated without Mland viewing it.

#### Trigger

An authenticated user opens Change Password.

#### Preconditions

1. The account is active and the current session is valid.
2. Firebase lists a password sign-in method for this identity.

#### Postconditions

1. Firebase manages the changed password; Mland stores no copy.
2. No password value is saved in browser storage, logs, or audit.

#### Normal Flow

1. User opens Change Password.
2. User enters the current password, new password, and confirmation.
3. System reauthenticates User with Firebase using the current password.
4. Firebase validates the new password and updates it.
5. System clears the form fields and displays a success message.

#### Alternative Flows

None.

#### Exception Flows

1a. Identity is Google-only → System hides the password-change control.

3a. Current password is incorrect → System clears only the current-password field and keeps proposed values in temporary memory.

3b. Session is expired or disabled → System discards unsaved values and directs User to sign in again.

4a. Provider or validation fails → Current password remains active and System shows actionable guidance.

#### Business Rules

BR-04-01, BR-04-02, BR-04-03

#### Non-Functional Requirements

—

#### Priority

—

#### Messages

—

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-04-01 | Only the signed-in owner of a password identity may change its password. |
| BR-04-02 | The current password must reauthenticate the owner before the change. |
| BR-04-03 | A Google-only identity has no Mland password-change form. |
