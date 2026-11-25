### 1.5 Manage Personal Profile

#### Primary Actors

Member

#### Secondary Actors

Firebase Authentication

#### Description

A Member views and updates their own permitted profile information. Firebase controls verification of the sign-in email.

#### Preconditions

The Member is authenticated with an active account and owns the requested profile.

#### Normal Flow

1. The Member opens Profile and sees their name, sign-in email, phone and saved delivery addresses.
2. The Member may explicitly request or resend a Firebase verification email and reload the verification state.
3. After the email is verified, the Member edits permitted name or phone fields.
4. The system validates and saves those fields and displays the updated profile.

#### Alternative Flows

**Unverified email:** Protected profile changes remain unavailable. A Firebase verification-send failure leaves the account in place and shows safe recovery guidance.

**Invalid session, values or ownership:** The system denies the action or shows field-level errors without changing the stored profile.

**Save failure:** Previous values remain available and the Member may retry.

#### Postconditions

- Only the owner's permitted profile fields change.
- Role, status, password and authorization remain unchanged.
- A change to sign-in email uses the separate Firebase verified-email flow, not ordinary profile save.

#### Business Rules

BR-05-01, BR-05-02, BR-06-01, BR-06-02, BR-06-03

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-05-01 | A Member may view only their own profile. |
| BR-05-02 | Viewing a profile does not change security state. |
| BR-06-01 | Only permitted owner profile fields may be edited after required email verification. |
| BR-06-02 | Profile updates do not change role, status, password or authorization. |
| BR-06-03 | Firebase verifies a proposed sign-in email before Mland adopts it. |
