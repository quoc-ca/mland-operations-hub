### 1.6 Update Personal Profile

#### Primary Actors

Member

#### Secondary Actors

Mail Gateway

#### Description

As a Member, I want to update my permitted personal information so that my contact and fulfilment details remain accurate for future platform workflows.

#### Preconditions

1. The platform is available.
2. The Member is authenticated with an active account.
3. The requested fields are editable profile fields.
4. The new values satisfy the applicable validation rules.

#### Normal Flow

**Update Personal Profile**

1. The Member opens the Personal Profile function.
2. The system displays the current editable profile information.
3. The Member changes one or more permitted fields, such as name, phone number, or address.
4. The system validates the submitted values and checks that protected account fields have not been modified through this use case.
5. The Member reviews and confirms the changes.
6. The system saves the updated profile and records the update time.
7. The system displays the updated profile information.

#### Alternative Flows

**Step 3 — A protected field or another account's data is submitted**

The system rejects the unauthorized field and keeps the stored profile unchanged for that field.

**Step 4 — A profile value is invalid or incomplete**

The system displays field-level validation messages and asks the Member to correct the values.

**Step 5 — Member cancels the update**

The system discards the unsaved changes and keeps the previous profile data.

**Step 6 — Profile update fails**

The system rolls back the incomplete update, displays an error, and preserves the previous profile values.

**Step 6 — A contact-verification message is required but cannot be delivered**

The system does not apply the affected contact change, records the notification failure, and allows the Member to retry verification.

#### Postconditions

- Valid permitted profile fields are updated for the authenticated Member.
- Account role, status, password, and authorization are unchanged.
- Invalid or unauthorized fields are not saved.
- The update time and applicable audit information are recorded.

#### Business Rules

BR-06-01, BR-06-02, BR-06-03

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-06-01 | A Member may update only permitted profile fields belonging to the authenticated account. |
| BR-06-02 | Profile updates must not change the account role, account status, password, or authorization policy. |
| BR-06-03 | A contact field requiring verification is not considered updated until its verification requirement succeeds. |
