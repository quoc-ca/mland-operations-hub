### 1.3 Reset Password

#### Primary Actors

Guest, Member

#### Secondary Actors

Mail Gateway, SSO Provider

#### Description

As a user who cannot access my password, I want to reset it through the approved recovery flow so that I can sign in again without support staff viewing or receiving my password.

#### Preconditions

1. The platform is available.
2. The password-recovery service and Mail Gateway are configured.
3. The user can provide the email address associated with a local platform account.
4. The account is eligible for local password recovery.

#### Normal Flow

**Reset Password**

1. The user opens the Forgot Password page.
2. The user enters the email address associated with the account.
3. The system validates the request format and creates a time-limited recovery request.
4. The system sends a password-reset link or one-time token to the email address through the Mail Gateway.
5. The user opens the link or submits the valid token.
6. The system displays the password-reset form without revealing sensitive account information.
7. The user enters and confirms a new password.
8. The system validates the token, password policy, and recovery-request status.
9. The system stores the new password securely, invalidates applicable previous sessions, and records the change.
10. The system sends a password-change confirmation and allows the user to Sign In.

#### Alternative Flows

**Step 2 — Email address is unknown or not eligible for local recovery**

The system displays the same non-disclosing response as a valid request and does not reveal whether an account exists. An SSO-managed user is directed to recover access through the SSO provider.

**Step 5 — Recovery link or token is invalid or expired**

The system rejects the request, displays an error, and allows the user to request a new recovery message.

**Step 7 — New password does not meet the password policy**

The system displays the password requirements and does not change the existing password.

**Step 4 — Mail Gateway cannot deliver the recovery message**

The system records the notification failure, does not change the password, and allows the user to retry later.

**Step 9 — Password update fails**

The system keeps the existing password, records the failure, and asks the user to restart the recovery flow.

#### Postconditions

- A valid local account has a new securely stored password after successful token verification.
- Previous applicable sessions are invalidated after the password is changed.
- The system does not reveal account existence through the recovery response.
- An SSO-managed identity is not assigned a local password by this use case.

#### Business Rules

BR-03-01, BR-03-02, BR-03-03

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-03-01 | Password recovery must use a time-limited, single-use verification link or token. |
| BR-03-02 | The system must not reveal whether an email address is registered through password-recovery responses. |
| BR-03-03 | Passwords must be stored securely and must never be sent to Staff, Admin, or external providers. |
