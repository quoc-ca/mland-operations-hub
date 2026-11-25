#### 2.3.3 User Account Details

![](assets/screens/73-new-user-user-account-details.png)

This screen allows Admins to:

- View one account's sign-in identity, role, status, location assignment, session revocation state, record version and dates.
- Edit the account's full name and phone number.
- Change a Staff or Manager account between the two roles and update its locations; permissions change only after the server confirms the save.
- Suspend the account after a confirmation dialog with a required reason, or restore a suspended account.
- Resend the setup invitation of a Staff or Manager account that is still PENDING_SETUP, subject to a five-minute cooldown and delivery reconciliation.

An ADMIN account is protected: it cannot be edited, suspended or restored here. A Carrier account cannot change role and has no location. A save made on an outdated version is refused and the latest data is reloaded.

For a pending Staff or Manager account, show invitation creation and expiry times, server-based remaining time and its actual delivery state. A resend creates a new single-use 48-hour link and invalidates the previous version. After two definite SMTP failures, Admin may retrieve the current unexpired message for delivery outside the system. This action is audited and does not mark the email SENT. An uncertain outcome, expired link, used link or superseded version does not expose manual content.

| Field Name | Description |
| --- | --- |
| Account header | Name, sign-in identity and status badge (ACTIVE · In use, PENDING_SETUP · Awaiting activation, SUSPENDED · Suspended). |
| Account overview | System role, status, assigned location (or "Not applicable (nationwide delivery partner)" for the Carrier), Firebase session revocation (Pending, Confirmed or Not applicable), record version, created and last updated dates. |
| Resend invitation | Pending Staff or Manager only; available after five minutes when the prior delivery outcome permits it. Creates a new version for the same Account and Firebase UID. |
| Suspend account | Opens "Confirm account suspension": warns that the account becomes SUSPENDED and its session is revoked, requires **Suspension reason**, and has **Cancel** and **Confirm suspension**. |
| Restore account | Suspended accounts only; disabled while session revocation is still pending. Restores the account and the user must sign in again. |
| Edit profile information | Full name (required) and phone number, with **Save profile**. Not shown for ADMIN. |
| Assign role & location | Staff and Manager only: role drop-down (STAFF or MANAGER), a note on the role change, and locations (one for Staff, one or more for a Manager; inactive locations are marked), with **Update role & location**. |
| Partner scope & permissions | Carrier only: explains that the role is fixed and only the name, phone and suspension can be changed. |
| ← Back to account list | Returns to User Account List. |
