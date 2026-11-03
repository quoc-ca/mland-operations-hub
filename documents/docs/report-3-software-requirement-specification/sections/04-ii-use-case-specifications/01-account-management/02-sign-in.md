### 1.2 Sign In

#### Primary Actors

Guest, Member, Staff, Manager, Admin

#### Secondary Actors

SSO Provider

#### Description

As a registered user, I want to sign in with my account credentials or an approved SSO provider so that the system can authenticate me and provide the features allowed by my platform role.

#### Preconditions

1. The platform is available.
2. The user has an existing platform account or an identity linked to an approved SSO provider.
3. The account is active and is not locked.
4. The selected authentication method is configured and available.

#### Normal Flow

**Sign In**

1. The user opens the Sign In page.
2. The system displays email/password and configured SSO sign-in options.
3. The user selects a sign-in method.
4. For email/password sign-in, the user enters the registered email address and password.
5. For SSO sign-in, the system redirects the user to the selected SSO provider.
6. The system validates the submitted credentials or verifies the returned SSO identity token.
7. The system resolves the matching platform account, status, and assigned role from its own database.
8. The system creates an authenticated session with the permitted authorization context.
9. The system records the successful sign-in event and redirects the user to the appropriate authenticated area.

#### Alternative Flows

**Step 4 — Email address or password is invalid**

The system rejects the sign-in attempt without revealing which credential is incorrect and allows the user to retry or reset the password.

**Step 5 — SSO authentication is cancelled or fails**

The system does not create a session, displays an authentication error, and allows the user to retry or choose another method.

**Step 6 — SSO identity is not linked to a platform account**

The system does not grant access and directs the user to Register Account or the approved account-linking flow.

**Step 7 — Account is locked, inactive, or restricted**

The system denies access, displays an appropriate account-status message, and does not create an authenticated session.

**Step 6 — Authentication provider is unavailable**

The system displays an availability error and allows the user to retry when the provider is available.

#### Postconditions

- A valid user receives an authenticated session with the permissions of the resolved platform role.
- An invalid, locked, inactive, or unlinked identity receives no authenticated session.
- The system does not assign or elevate a role during sign-in.
- The successful or failed sign-in result is recorded according to the security logging policy.

#### Business Rules

BR-02-01, BR-02-02, BR-02-03, BR-02-04

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-02-01 | Sign-in succeeds only after the submitted credentials or SSO identity token is successfully verified. |
| BR-02-02 | The platform resolves account status, role, and authorization from its own database. |
| BR-02-03 | A locked or inactive account must not receive an authenticated session. |
| BR-02-04 | Sign-in must not create, change, or elevate the user's platform role. |
