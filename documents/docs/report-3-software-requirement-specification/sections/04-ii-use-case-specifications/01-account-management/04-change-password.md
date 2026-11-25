### 1.4 Change Password

#### Primary Actors

Member, Staff, Manager, Admin, Carrier

#### Secondary Actors

Firebase Authentication

#### Description

An authenticated owner of an email/password identity changes their own password without Mland storing or viewing it.

#### Preconditions

The account is active, the current session is valid, and Firebase lists a password sign-in method for this identity.

#### Normal Flow

1. The owner opens Change Password and enters the current password, new password and confirmation.
2. The browser reauthenticates the owner with Firebase using the current password.
3. Firebase checks the new password and updates it.
4. The form clears its fields and shows success.

#### Alternative Flows

**Google-only identity:** The password-change control is hidden.

**Incorrect current password:** Only that field is cleared. Proposed new values stay in temporary form memory for correction.

**Expired or disabled session:** Unsaved form values are discarded and the owner is directed to sign in again.

**Provider or validation failure:** The current password remains in force and the page shows actionable guidance.

#### Postconditions

- Firebase manages the changed password; Mland stores no copy.
- No password value is saved in browser storage, logs or audit.

#### Business Rules

BR-04-01, BR-04-02, BR-04-03

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-04-01 | Only the signed-in owner of a password identity may change its password. |
| BR-04-02 | The current password must reauthenticate the owner before the change. |
| BR-04-03 | A Google-only identity has no Mland password-change form. |
