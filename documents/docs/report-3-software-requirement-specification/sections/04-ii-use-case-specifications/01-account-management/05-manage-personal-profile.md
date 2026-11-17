### 1.5 Manage Personal Profile

#### Primary Actors

Member

#### Secondary Actors

Mail Gateway

#### Description

As a Member, I want to view and update my permitted personal information so that I can verify and maintain the account information used by my bookings, orders, and account communications.

#### Preconditions

1. The platform is available.
2. The Member is authenticated with an active account.
3. The requested profile belongs to the authenticated Member.

#### Normal Flow

**Manage Personal Profile**

1. The Member opens the Personal Profile function.
2. The system identifies the authenticated Member account and retrieves its permitted profile data.
3. The system displays the profile information, including the permitted name, email, phone number, and address fields.
4. The Member may return to another Member function or choose to update one or more permitted fields.
5. The system validates the submitted values and checks that protected account fields have not been modified through this use case.
6. The Member reviews and confirms the changes.
7. The system saves the valid profile changes and records the update time.
8. If contact verification is required, the system sends the verification message through the Mail Gateway.
9. The system displays the updated profile information.

#### Alternative Flows

**Step 2 — Session is missing, expired, or invalid**

The system denies access to the profile and asks the user to Sign In again.

**Step 3 — Profile retrieval fails**

The system displays an error and allows the Member to retry without exposing partial data as complete.

**Step 3 — Profile data is incomplete**

The system displays the available permitted data and identifies fields that require an update without inventing values.

**Step 5 — A protected field or another account's data is submitted**

The system rejects the unauthorized field and keeps the stored profile unchanged for that field.

**Step 5 — A profile value is invalid or incomplete**

The system displays field-level validation messages and asks the Member to correct the values.

**Step 6 — Member cancels the update**

The system discards the unsaved changes and keeps the previous profile data.

**Step 7 — Profile update fails**

The system rolls back the incomplete update, displays an error, and preserves the previous profile values.

**Step 8 — A contact-verification message is required but cannot be delivered**

The system does not apply the affected contact change, records the notification failure, and allows the Member to retry verification.

#### Postconditions

- The Member views only the profile associated with the authenticated account.
- Valid permitted profile fields are updated for the authenticated Member.
- No profile data is changed when the Member only views the profile.
- Account role, status, password, and authorization are unchanged.
- Invalid or unauthorized fields are not saved.
- The update time and applicable audit information are recorded.

#### Business Rules

BR-05-01, BR-05-02, BR-06-01, BR-06-02, BR-06-03

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-05-01 | A Member may view only the personal profile associated with the authenticated account. |
| BR-05-02 | Profile viewing is read-only and must not change account status, role, credentials, or authorization. |
| BR-06-01 | A Member may update only permitted profile fields belonging to the authenticated account. |
| BR-06-02 | Profile updates must not change the account role, account status, password, or authorization policy. |
| BR-06-03 | A contact field requiring verification is not considered updated until its verification requirement succeeds. |
