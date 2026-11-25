#### 2.2.2 Change Email / Password

![](assets/screens/09-change-password.png)

This screen allows signed-in users to:

- Change their password by entering the current password and the new one twice; the current password is checked again with Firebase before the change.
- Request a change of sign-in email; the new address receives a verification email and the sign-in email changes only after it is verified.
- See, for a Google-only account, that the password section is hidden and that the email must be changed in the Google account.
- As the Carrier, change only the password, on a separate page that leads back to Carrier Portal.

The password route is open to Members, Staff, Managers, Admins and the Carrier; the email route is not open to the Carrier. Both sections are part of the profile page, which scrolls to the requested one.

| Field Name | Description |
| --- | --- |
| Current password | Required for a password change. A wrong value shows "The current password is incorrect." and clears only this field. |
| New password | Required, at least 6 characters. |
| Confirm new password | Must match the new password, otherwise "The confirmation password does not match." |
| Update password | Enabled only when all three password fields are filled (on the Carrier page it is disabled only while the change is processing); shows "Password changed successfully." and clears the fields. |
| Current password (email section) | Required for password accounts and used to reauthenticate with Firebase before requesting the email change. An incorrect value clears only this field. |
| New email address | Required and must contain "@"; an address used by another account shows "This email address is already used by another account." |
| Request email change | Sends the verification email and shows "A confirmation email was sent to {email}. Your current email stays unchanged until verification is complete." |
| Google account notice | Replaces the email form for a Google-only account. |
| ← Back to the carrier portal | Carrier page only; title "Change carrier partner password". |
