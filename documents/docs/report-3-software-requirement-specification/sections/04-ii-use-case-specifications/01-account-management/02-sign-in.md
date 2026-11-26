### 1.2 Sign In

| Use Case ID | UC-02 | Use Case Name | Sign In |
| --- | --- | --- | --- |
| Created By |  | Created Date |  |
| Primary Actors | Guest, Member, Staff, Manager, Admin | Secondary Actors | SSO Provider |

#### Description

As a registered user, I want to sign in with credentials or an approved SSO provider so that I can access permitted platform features.

#### Trigger

A user selects Sign In.

#### Preconditions

1. The platform is available.
2. The user has an existing platform account or an approved SSO identity.
3. The account is active and unlocked.
4. The selected authentication method is configured.

#### Postconditions

1. A valid user receives an authenticated session with the resolved role's permissions.
2. An invalid, locked, inactive, or unlinked identity receives no session.
3. Sign-in does not assign or elevate any role.
4. The sign-in attempt is recorded per security logging policy.

#### Normal Flow

1. User opens the Sign In page.
2. System displays email/password and configured SSO sign-in options.
3. User selects email/password sign-in.
4. User submits the registered email address and password.
5. System validates the submitted credentials.
6. System resolves the matching platform account, status, and role from its database.
7. System creates an authenticated session with the permitted authorization context.
8. System records the successful sign-in event.
9. System redirects User to the appropriate authenticated area.

#### Alternative Flows

3a. User selects SSO sign-in → System redirects User to the SSO provider and verifies the returned identity token.

#### Exception Flows

4a. Email address or password is invalid → System rejects the attempt without revealing which credential failed and allows retry or password reset.

3b. SSO authentication is cancelled or fails → System creates no session, shows an error, and allows retry or another method.

5a. SSO identity is not linked to a platform account → System denies access and directs User to Register Account or account linking.

5b. Authentication provider is unavailable → System displays an availability error and allows retry later.

6a. Account is locked, inactive, or restricted → System denies access, shows an account-status message, and creates no session.

#### Business Rules

BR-02-01, BR-02-02, BR-02-03, BR-02-04

#### Non-Functional Requirements

—

#### Priority

—

#### Messages

—

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-02-01 | Sign-in succeeds only after the submitted credentials or SSO identity token is successfully verified. |
| BR-02-02 | The platform resolves account status, role, and authorization from its own database. |
| BR-02-03 | A locked or inactive account must not receive an authenticated session. |
| BR-02-04 | Sign-in must not create, change, or elevate the user's platform role. |
