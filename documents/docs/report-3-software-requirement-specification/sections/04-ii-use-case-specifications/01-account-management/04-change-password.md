### 1.4 Change Password

#### Primary Actors

Member, Staff, Manager, Admin

#### Secondary Actors

Mail Gateway, SSO Provider

#### Description

As an authenticated user, I want to change my local account password so that I can maintain control of my account security.

#### Preconditions

1. The platform is available.
2. The user is authenticated with an active platform account.
3. The account uses a local password that can be changed through the platform.
4. The user knows the current password.

#### Normal Flow

**Change Password**

1. The user opens the Change Password function.
2. The system requests the current password, a new password, and confirmation of the new password.
3. The user submits the password-change request.
4. The system verifies the current password and validates the new password against the password policy.
5. The system securely stores the new password and invalidates other active sessions where required.
6. The system records the password change and sends an account-security notification through the Mail Gateway.
7. The system displays a successful change message and keeps or re-establishes the current session according to the security policy.

#### Alternative Flows

**Step 3 — Current password is incorrect**

The system rejects the request, does not change the password, and allows the user to retry or start password recovery.

**Step 4 — New password is invalid, reused, or does not match confirmation**

The system displays validation messages and does not save the new password.

**Step 4 — Account is managed by an SSO provider**

The system does not change a local password and directs the user to manage credentials through the SSO provider.

**Step 6 — Security notification cannot be delivered**

The password change remains valid, the system records the notification failure, and the message is made available for retry or operational follow-up.

**Step 5 — Password update fails**

The system preserves the previous password and displays an error without partially applying the change.

#### Postconditions

- The local password is changed only after the current password and new-password policy are verified.
- The previous password is not recoverable through the application.
- Other active sessions are invalidated according to the security policy.
- An account-security notification is created even if delivery is temporarily unavailable.

#### Business Rules

BR-04-01, BR-04-02, BR-04-03

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-04-01 | Only an authenticated user may change the password of the current local account. |
| BR-04-02 | A password change requires verification of the current password and the configured password policy. |
| BR-04-03 | SSO-managed credentials are changed through the SSO provider rather than by the platform. |
