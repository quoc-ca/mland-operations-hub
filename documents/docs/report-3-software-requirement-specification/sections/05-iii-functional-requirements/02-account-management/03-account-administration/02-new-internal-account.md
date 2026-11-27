#### 2.3.2 New Internal Account

![](assets/screens/live/90-new-internal-account.png)

This screen allows Admins to:

- Create a Staff, Manager or Carrier (GHTK) account; Member accounts come only from self-registration and Admin accounts are not created here.
- For Staff or Manager, enter the sign-in email, name, phone and work location(s). The account starts in PENDING_SETUP and an activation invitation is always queued.
- For the Carrier, enter the sign-in email, an initial password, a display name and a phone number; the account is ACTIVE at once and the Admin hands the credentials to the partner directly.
- See a summary of the created account and open its details, create another account or return to the list.

The page title is **Provision staff & partner accounts**. The Carrier's initial password is write-only: it is not stored, logged or shown again. Staff and Manager have no password field. A repeated submission keeps the same request key, so it does not create a second account.

Before creating a Staff or Manager account, the system checks the email against existing identities:

1. Email of an existing Staff or Manager: a notice links to that account's details; nothing is created.
2. Email of an Admin or Carrier: the request is refused.
3. Email known only to Firebase, with no Mland account: a dialog asks the Admin to delete that orphan identity first.
4. Email of a Member: a review dialog shows the Member's data, with a JSON export. While that data is marked DEMO / UNAVAILABLE, replacing the Member is refused.

**Field Description**

| Field Name | Description |
| --- | --- |
| ← Back / Accounts tab | **← Back** returns to User Account List; the **Accounts** tab marks the admin area. |
| Collision notice | Shown after an email check finds a conflict (cases 1 and 2 above); case 1 includes a link "Go to the admin page of account ACC-{id}" (no English label in the app). |
| Sign-in email | Required. For Staff and Manager, labelled "Identity and invitation email (required)" (no English label in the app); it receives a 48-hour activation link. For the Carrier, "Sign-in email (cannot be changed)". |
| Initial password | Carrier only; required, at least 6 characters, with a write-only hint. |
| Full name / Partner display name / Representative | Required, up to 100 characters. |
| Contact phone number | 7 to 30 characters; required for the Carrier, optional for Staff and Manager. |
| Internal role | Options: Branch staff (STAFF), Branch manager (MANAGER), Carrier partner (GHTK). Default: Branch staff (STAFF). A hint below explains each role's scope. |
| Work location (limits access) / Organisational branch assignment | Staff and Manager only; lists active workshop locations, the first one preselected. Staff picks exactly one (radio buttons); a Manager picks one or more (checkboxes). |
| Create account / Cancel | **Create account** checks the email ("Checking email…", no English label in the app), then creates the account ("Creating…"). **Cancel** returns to the list. |
| Created summary | "Account created!" with status ACTIVE (In use) or PENDING_SETUP (Awaiting activation), account ID, role, identity email and account status. For Staff and Manager it states that the invitation was queued and is valid for 48 hours from creation. |
| Summary actions | **View account details**, **Create another account** (clears the form) and **Account list**. |
