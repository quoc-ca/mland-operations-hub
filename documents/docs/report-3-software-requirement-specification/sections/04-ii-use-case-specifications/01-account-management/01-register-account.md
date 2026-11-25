### 1.1 Register Account

#### Primary Actors

Guest

#### Secondary Actors

Firebase Authentication

#### Description

A Guest registers a Member account with email and password or Google. Firebase establishes identity; Mland assigns the fixed Member role.

#### Preconditions

1. Firebase Authentication is configured.
2. The identity is not reserved for a pending internal account or linked to another Mland account.

#### Normal Flow

1. The Guest opens registration and chooses email/password or Google.
2. Email registration creates a Firebase identity; Google registration redirects to Google and processes the returned result.
3. The system verifies the Firebase identity and rejects a reserved or duplicate account.
4. The system creates one active Member account bound to that Firebase UID.
5. For email registration, Firebase sends a verification email. The Member may verify later from Profile.
6. The system opens Member home and shows a verification reminder when needed.

#### Alternative Flows

**Invalid input or provider failure:** The system displays safe guidance and does not create a duplicate Member account.

**Email already used by a password identity:** Google registration does not merge accounts automatically. The person is guided to sign in with the original password method.

**Identity reserved for internal setup:** Registration does not create a Member account and gives neutral invitation guidance.

**Verification email fails:** The Member account remains active. Profile provides an explicit resend action; there is no background resend.

#### Postconditions

- One Firebase UID is linked to at most one Mland account.
- Email verification remains a separate condition checked by actions that require it.
- Self-registration never grants Staff, Manager, Carrier, or Admin access.

#### Business Rules

BR-01-01, BR-01-02, BR-01-03, BR-01-04

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-01-01 | Self-registration creates only a Member account. |
| BR-01-02 | A reserved or existing identity cannot create another Member account. |
| BR-01-03 | Email registration creates an active account; Firebase sends verification email and Mland checks verification for protected actions. |
| BR-01-04 | Firebase establishes identity; Mland controls role, status, and business authorization. |
