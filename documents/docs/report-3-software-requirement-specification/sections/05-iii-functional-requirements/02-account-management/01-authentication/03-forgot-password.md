#### 2.1.3 Forgot Password

![](assets/screens/05-forgot-password.png)

This screen allows users with an email-and-password account to:

- Enter their email address to request a password reset email from Firebase.
- Receive the same confirmation whether or not the email belongs to an account.
- Set the new password through the link in the email, on the identity provider's page.
- Return to User Login.

Google sign-in is not offered on this screen.

| Field Name | Description |
| --- | --- |
| Description | States that the message does not confirm whether the email has an account. |
| Email address | Required; must be a valid email format. |
| Send reset link | Requests the reset email; shows "Processing…" while working. |
| Confirmation | "If the email exists in the system, a password reset message has been sent." shown for every valid request. |
| Validation error | Shown only when the email is empty or not a valid address. |
| Back to sign in | Opens User Login. |
