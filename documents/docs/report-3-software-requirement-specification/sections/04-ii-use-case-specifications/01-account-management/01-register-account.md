### 1.1 Register Account

| Use Case ID | UC-01 | Use Case Name | Register Account |
| --- | --- | --- | --- |
| Created By |  | Created Date |  |
| Primary Actors | Guest | Secondary Actors | Firebase Authentication |

#### Description

As a Guest, I want to register with email and password or Google so that I can use Mland as a Member.

#### Trigger

A Guest selects Register.

#### Preconditions

1. Firebase Authentication is configured.
2. The identity is not reserved for a pending internal account or linked to another Mland account.

#### Postconditions

1. One active Member account is linked to the Firebase UID.
2. The Member lands on Member home.
3. Email verification stays a separate condition, checked only by protected actions.
4. Self-registration never grants Staff, Manager, Carrier, or Admin access.

#### Normal Flow

1. Guest opens registration and chooses email/password or Google.
2. Guest submits email and password. Firebase creates the identity.
3. System verifies the Firebase identity.
4. System creates one active Member account bound to the Firebase UID.
5. System opens Member home.

#### Alternative Flows

2a. Guest chooses Google → System redirects to Google and processes the returned result.

4a. Guest registered with email → Firebase sends a verification email; Member may verify later from Profile.

5a. Email is unverified → System shows a verification reminder on Member home.

#### Exception Flows

2a. Input is invalid or the provider fails → System shows safe guidance and creates no duplicate account.

3a. Google email already belongs to a password identity → System does not merge accounts and guides the person to sign in with the original method.

3b. Identity is reserved for internal setup or already linked → System creates no Member account and shows neutral invitation guidance.

4a. Verification email fails → Account stays active; Profile offers a manual resend; no automatic resend.

#### Business Rules

BR-01-01, BR-01-02, BR-01-03, BR-01-04

#### Non-Functional Requirements

—

#### Priority

—

#### Messages

—

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-01-01 | Self-registration creates only a Member account. |
| BR-01-02 | A reserved or existing identity cannot create another Member account. |
| BR-01-03 | Email registration creates an active account; Firebase sends the verification email and Mland checks verification for protected actions. |
| BR-01-04 | Firebase establishes identity; Mland controls role, status, and business authorization. |
