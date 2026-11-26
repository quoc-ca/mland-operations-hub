### 1.3 Reset Password

| Use Case ID | UC-03 | Use Case Name | Reset Password |
| --- | --- | --- | --- |
| Created By |  | Created Date |  |
| Primary Actors | Guest, Member | Secondary Actors | Firebase Authentication |

#### Description

As a Guest or Member, I want to request a Firebase password recovery link so that I can reset my password without sharing it with staff.

#### Trigger

A Guest or Member selects Forgot Password.

#### Preconditions

1. Firebase password recovery is configured.
2. The user can provide a sign-in email address.

#### Postconditions

1. Firebase manages the new password; Mland does not store it.
2. The recovery response does not reveal account existence.

#### Normal Flow

1. Guest or Member opens Forgot Password.
2. Guest or Member submits an email address.
3. System requests a recovery email from Firebase and displays a non-disclosing response.
4. Firebase sends a time-limited recovery link to the eligible identity.
5. Guest or Member opens the link and sets a new password on Firebase's recovery page.
6. Firebase validates the link and updates the credential.
7. Guest or Member signs in with the new password.

#### Alternative Flows

None.

#### Exception Flows

2a. Email is unknown or identity is Google-only → System shows the same non-disclosing response; Google-only users recover via Google.

6a. Link is expired, delivery fails, or update fails → Password remains unchanged and System shows safe retry guidance.

#### Business Rules

BR-03-01, BR-03-02, BR-03-03

#### Non-Functional Requirements

—

#### Priority

—

#### Messages

—

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-03-01 | Recovery uses Firebase's time-limited link. |
| BR-03-02 | Recovery responses must not disclose whether an email is registered. |
| BR-03-03 | Mland must not store or disclose passwords to Staff or Admin. |
