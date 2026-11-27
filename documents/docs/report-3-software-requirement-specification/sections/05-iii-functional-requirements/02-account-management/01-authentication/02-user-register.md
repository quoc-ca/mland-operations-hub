#### 2.1.2 User Register

![](assets/screens/live/03-user-register.png)

This screen allows visitors to:

- Create a Member account with an email and a password confirmed twice, through Firebase Authentication.
- Create a Member account with Google instead.
- Receive a verification email after an email registration; the account is created at once and the unverified email is handled later by the verification reminder in User Profile.
- Be signed in and sent back to the page they came from, or to Home Page, once the server has created the Mland account.

Every self-registered account gets the MEMBER role. An email that is already in use is refused. The page title is **Register an account**, with "Create an M.land account".

**Field Description**

| Field Name | Description |
| --- | --- |
| ← Back | Returns to Home Page. |
| Error message | An email already registered shows the collision message; a weak password, an invalid email or a network error show their own messages. |
| Sign in with Google | Shown only when Firebase is configured; same Google flow as User Login, which creates the Member account on first use. |
| Email address | Required; must be a valid email format. |
| Password | Required, masked, at least 6 characters. |
| Confirm password | Required; must match Password, otherwise "The confirmation password does not match." |
| Terms notice | "By registering, you agree to the terms of use." (plain text, not a link). |
| Register | Creates the Firebase identity, sends the verification email (best effort) and creates the Mland account; shows "Processing…" while working. |
| Sign in now | Link to User Login, after "Already have an account?". |
