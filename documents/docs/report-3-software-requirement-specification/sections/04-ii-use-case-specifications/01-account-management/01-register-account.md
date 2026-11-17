### 1.1 Register Account

#### Primary Actors

Guest

#### Secondary Actors

SSO Provider, Mail Gateway

#### Description

As a Guest, I want to create a Member account using email registration or an approved SSO provider so that I can access Member features such as profile management, workshop booking history, loyalty benefits, retail checkout, and custom-manufacturing orders.

#### Preconditions

1. The platform is available.
2. Email registration and/or an approved SSO provider is configured.
3. The submitted email address or external identity is not already linked to an existing account.
4. The Guest has not completed authentication as an existing account.

#### Normal Flow

**Register Account**

1. The Guest opens the Register Account page.
2. The system displays the available registration methods: email registration and configured SSO providers.
3. The Guest selects email registration or an SSO provider.
4. For email registration, the Guest enters the required registration information, including email address and password.
5. For SSO registration, the system redirects the Guest to the selected SSO provider.
6. The system validates the submitted information or verifies the returned SSO identity token.
7. For email registration, the system sends a verification token or OTP to the Guest's email address through the Mail Gateway.
8. The Guest submits the valid verification token or OTP.
9. The system creates one Member account and stores the verified identity mapping.
10. The system sets the new account's role to Member and its status to active.
11. The system sends an account-registration confirmation and redirects the Guest to Sign In or the authenticated Member area.

#### Alternative Flows

**Step 4 — Required registration information is missing or invalid**

The system rejects the submission, displays validation messages, and keeps the Guest on the Register Account page.

**Step 6 — Email address or SSO identity already exists**

The system does not create a duplicate account and informs the Guest that the identity is already registered. The Guest may continue to Sign In or use the password-recovery flow.

**Step 6 — SSO authentication is cancelled or fails**

The system does not create an account, displays an authentication error, and allows the Guest to retry or choose another registration method.

**Step 8 — Verification token or OTP is invalid or expired**

The system rejects the verification attempt, displays an error, and allows the Guest to request a new token or retry while the verification request remains valid.

**Step 7 — Mail Gateway cannot deliver the verification message**

The system does not activate the account, records the notification failure, and allows the Guest to retry or request the verification message again.

**Step 9 — Account creation fails**

The system does not create a partial account, displays an error message, and allows the Guest to retry the registration process.

#### Postconditions

- A new Member account is created only after successful email or SSO identity verification.
- The account is assigned the Member role by default.
- The system does not assign Staff, Manager, or Admin privileges during self-registration.
- The verified identity is linked to only one platform account.
- The Guest may continue to Sign In or access the authenticated Member area.
- If registration fails, no incomplete or duplicate account is created.

#### Business Rules

BR-01-01, BR-01-02, BR-01-03, BR-01-04

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-01-01 | Self-registration creates a Member account only; Staff, Manager, and Admin roles must be assigned through authorized account-management functions. |
| BR-01-02 | An email address or external SSO identity may be linked to only one platform account. |
| BR-01-03 | An email registration becomes active only after successful verification of the required token or OTP. |
| BR-01-04 | An SSO provider establishes identity only; the platform maintains account status, role, and authorization in its own database. |
