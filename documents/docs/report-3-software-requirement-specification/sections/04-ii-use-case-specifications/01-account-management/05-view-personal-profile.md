### 1.5 View Personal Profile

#### Primary Actors

Member

#### Secondary Actors

None

#### Description

As a Member, I want to view my personal profile so that I can verify the account information used by my bookings, orders, and account communications.

#### Preconditions

1. The platform is available.
2. The Member is authenticated with an active account.
3. The requested profile belongs to the authenticated Member.

#### Normal Flow

**View Personal Profile**

1. The Member opens the Personal Profile function.
2. The system identifies the authenticated Member account.
3. The system retrieves the Member's permitted profile data.
4. The system displays the profile information, including the permitted name, email, phone number, and address fields.
5. The Member may continue to Update Personal Profile or return to another Member function.

#### Alternative Flows

**Step 2 — Session is missing, expired, or invalid**

The system denies access to the profile and asks the user to Sign In again.

**Step 3 — Profile retrieval fails**

The system displays an error and allows the Member to retry without exposing partial data as complete.

**Step 3 — Profile data is incomplete**

The system displays the available permitted data and identifies fields that require an update without inventing values.

#### Postconditions

- The Member views only the profile associated with the authenticated account.
- No profile data is changed.
- No other Member's personal information is disclosed.

#### Business Rules

BR-05-01, BR-05-02

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-05-01 | A Member may view only the personal profile associated with the authenticated account. |
| BR-05-02 | Profile viewing is read-only and must not change account status, role, credentials, or authorization. |
