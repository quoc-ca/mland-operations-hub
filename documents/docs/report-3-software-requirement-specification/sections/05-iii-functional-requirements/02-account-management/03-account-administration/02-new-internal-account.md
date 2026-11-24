#### 2.3.2 New Internal Account

This screen allows Admins to:

- Create a Staff, Manager or Carrier (GHTK) account; Member accounts come only from self-registration and Admin accounts are not created here.
- For Staff or Manager, enter the person's name and phone and choose the work location(s); the account starts in PENDING_SETUP and the person activates it through the invitation sent by the notification system.
- For the Carrier, enter the sign-in email, an initial password, a display name and a phone number; the account is ACTIVE at once and the Admin hands the credentials to the partner directly.
- See a summary of the created account and open its details, create another account or return to the list.

The initial password is write-only: it is not stored, logged or shown again. A repeated submission with the same request key does not create a second account. The form has no email field for Staff or Manager; how the invitation address is supplied is TBD.

| Field Name | Description |
| --- | --- |
| Internal role | Branch staff (STAFF), Branch manager (MANAGER) or Carrier partner (GHTK), with a hint about each role's scope. |
| Sign-in email | Carrier only; required, valid format, cannot be changed later. |
| Initial password | Carrier only; required, at least 6 characters. |
| Full name / Partner display name | Required, up to 100 characters. |
| Contact phone number | 7 to 30 characters; required for the Carrier, optional for Staff and Manager. |
| Work location | Staff and Manager only; active workshop locations, the first one preselected. Staff picks exactly one (radio buttons); a Manager picks one or more (checkboxes). |
| Send the account setup instructions | Checkbox shown for Staff and Manager, checked by default. |
| Create account / Cancel | Validates and creates the account ("Creating…"); **Cancel** returns to the list. |
| Created summary | "Account created!" with account ID, role, identity email and status (ACTIVE or PENDING_SETUP), and the hand-over note for the Carrier or the invitation note for Staff and Manager. |
