#### 2.1.3 Forgot Password

![](assets/screens/live/04-forgot-password.png)

This screen allows users with an email-and-password account to:

- Enter their email address to request a password reset email from Firebase.
- Receive the same confirmation whether or not the email belongs to an account.
- Set the new password through the link in the email, on the identity provider's page.
- Return to User Login.

Google sign-in is not offered on this screen. The page title is **Recover password**, with "Forgot your password?".

**Field Description**

| Field Name | Description |
| --- | --- |
| ← Back | Opens User Login. |
| Description | States that recovery instructions are sent if the request can be processed, and that the message does not confirm whether the email has an account. |
| Confirmation | "If the email exists in the system, a password reset message has been sent." shown for every valid request. |
| Email address | Required; must be a valid email format. |
| Validation error | Shown only when the email is empty or not a valid address. |
| Send reset link | Requests the reset email; shows "Processing…" while working. |
| Back to sign in | Also opens User Login. |
