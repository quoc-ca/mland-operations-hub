#### 2.3.2 New Internal Account

This screen allows Admins to:

- Create a Staff, Manager or Carrier (GHTK) account; Member accounts come only from self-registration and Admin accounts are not created here.
- For Staff or Manager, enter the intended sign-in email, name, phone and active work location(s). The account starts in PENDING_SETUP with a mandatory invitation.
- For the Carrier, enter the sign-in email, an initial password, a display name and a phone number; the account is ACTIVE at once and the Admin hands the credentials to the partner directly.
- See a summary of the created account and open its details, create another account or return to the list.

The Carrier's initial password is write-only: it is not stored, logged or shown again. Staff and Manager have no password field. A repeated submission with the same request key and unchanged data does not create a second account or invitation; changed data with that key is refused. The Admin reviews an email collision before any replacement. Member review and JSON export may show clearly labelled DEMO data, but cannot authorize live Member retirement or release that email.

| Field Name | Description |
| --- | --- |
| Internal role | Branch staff (STAFF), Branch manager (MANAGER) or Carrier partner (GHTK), with a hint about each role's scope. |
| Sign-in email | Required for every new internal account. For Staff and Manager it is the intended invitation recipient and must be available or pass the separate collision review. |
| Initial password | Carrier only; required, at least 6 characters. |
| Full name / Partner display name | Required, up to 100 characters. |
| Contact phone number | 7 to 30 characters; required for the Carrier, optional for Staff and Manager. |
| Work location | Staff and Manager only; active workshop locations, the first one preselected. Staff picks exactly one (radio buttons); a Manager picks one or more (checkboxes). |
| Invitation | Mandatory for Staff and Manager; no opt-out. Its 48-hour validity starts when the invitation version is created, regardless of email delivery. |
| Create account / Cancel | Validates and creates the account ("Creating…"); **Cancel** returns to the list. |
| Created summary | Shows account ID, role, email and ACTIVE or PENDING_SETUP. For Staff and Manager, shows invitation creation and expiry times and the actual queued, sent, failed or uncertain delivery state; it does not claim delivery on account creation. |
