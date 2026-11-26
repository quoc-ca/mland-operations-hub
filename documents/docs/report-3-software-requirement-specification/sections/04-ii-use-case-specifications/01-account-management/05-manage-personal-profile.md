### 1.5 Manage Personal Profile

| Use Case ID | UC-05 | Use Case Name | Manage Personal Profile |
| --- | --- | --- | --- |
| Created By |  | Created Date |  |
| Primary Actors | Member | Secondary Actors | Firebase Authentication |

#### Description

As a Member, I want to view and update my own permitted profile information so that my contact details stay accurate.

#### Trigger

A Member opens Profile.

#### Preconditions

1. The Member is authenticated with an active account.
2. The Member owns the requested profile.

#### Postconditions

1. Only the owner's permitted profile fields change.
2. Role, status, password, and authorization remain unchanged.
3. Sign-in email changes use the separate Firebase verified-email flow.

#### Normal Flow

1. Member opens Profile.
2. System displays name, sign-in email, phone, and saved delivery addresses.
3. Member edits permitted name or phone fields.
4. System validates and saves the updated fields.
5. System displays the updated profile.

#### Alternative Flows

2a. Member requests or resends a Firebase verification email → Firebase sends the email and System reloads the verification state.

#### Exception Flows

3a. Email is unverified → Protected profile changes stay unavailable; verification-send failure shows safe recovery guidance.

4a. Session, values, or ownership are invalid → System denies the action or shows field errors without changing the profile.

4b. Save fails → Previous values remain available and Member may retry.

#### Business Rules

BR-05-01, BR-05-02, BR-06-01, BR-06-02, BR-06-03

#### Non-Functional Requirements

—

#### Priority

—

#### Messages

—

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-05-01 | A Member may view only their own profile. |
| BR-05-02 | Viewing a profile does not change security state. |
| BR-06-01 | Only permitted owner profile fields may be edited after required email verification. |
| BR-06-02 | Profile updates do not change role, status, password or authorization. |
| BR-06-03 | Firebase verifies a proposed sign-in email before Mland adopts it. |
