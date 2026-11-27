#### 2.3.3 User Account Details

![](assets/screens/live/91-user-account-details.png)

This screen allows Admins to:

- View one account's sign-in identity, role, status, location assignment, session revocation state, record version and dates.
- Edit the account's full name and phone number.
- Change a Staff or Manager account between the two roles and update its locations; permissions change only after the server confirms the save.
- Suspend the account after a confirmation dialog with a required reason, or restore a suspended account.
- Resend the setup invitation of a Staff or Manager account that is still PENDING_SETUP.

An ADMIN account is protected: it cannot be edited, suspended or restored here. A Carrier account cannot change role and has no location. A save made on an outdated version is refused and the latest data is reloaded.

For a pending Staff or Manager account, the screen shows the invitation's delivery state, creation and expiry times, and remaining time. A resend creates a new 48-hour link and invalidates the previous one; the server refuses a resend within five minutes of the last one. When delivery has FAILED, the Admin can copy the current unexpired message for delivery outside the system. This copy is audited and does not mark the email SENT.

**Field Description**

| Field Name | Description |
| --- | --- |
| ← Back to account list / Accounts tab | Returns to User Account List; the **Accounts** tab marks the admin area. |
| Account header | Name and account code, sign-in identity and status badge (ACTIVE · In use, PENDING_SETUP · Awaiting activation, SUSPENDED · Suspended). |
| Account overview | System role, status, assigned location (or "Not applicable (nationwide delivery partner)" for the Carrier), Firebase session revocation (Awaiting processing (Pending), Revoked (Confirmed) or Not applicable), record version, created and last updated dates. |
| Resend invitation | Pending Staff or Manager only. Opens an inline warning that the previous link stops working and a new 48-hour period starts, with **Confirm resend** and **Cancel** (no English label in the app). |
| Copy invitation email manually | Pending Staff or Manager only, shown when delivery FAILED and disabled once the link has expired (no English label in the app). Opens a dialog with the expiry time, subject, plain-text body and setup link, each with a copy button. |
| Suspend account | Not shown for ADMIN or a suspended account. Opens "Confirm account suspension": warns that the account becomes SUSPENDED and its session is revoked, requires **Suspension reason**, and has **Cancel** and **Confirm suspension**. |
| Restore account | Suspended accounts only; disabled while session revocation is still pending. Restores the account and the user must sign in again. |
| ADMIN notice | "* The ADMIN account is protected and cannot be suspended or edited directly." |
| Activation invitation details | Pending Staff or Manager only (no English label in the app): delivery state (QUEUED, SENT or FAILED), invitation creation time, 48-hour activation deadline, remaining time ("Expired" when over) and SMTP send attempts out of 2. |
| Edit profile information | Full name (required) and phone number, with **Save profile**. Not shown for ADMIN. |
| Assign role & location | Staff and Manager only. **Internal role** options: Branch staff (STAFF), Branch manager (MANAGER); default: the current role. A note appears when the role changes. Locations: one for Staff, one or more for a Manager; inactive locations are marked. **Update role & location** saves. |
| Partner scope & permissions | Carrier only: explains that the role is fixed and only the name, phone and suspension can be changed. |
