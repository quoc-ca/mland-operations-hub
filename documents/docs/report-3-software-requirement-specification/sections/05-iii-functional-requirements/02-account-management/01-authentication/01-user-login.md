#### 2.1.1 User Login

![](assets/screens/02-user-login.png)

This screen allows users of every role to:

- Sign in with email and password, or with Google, through Firebase Authentication.
- Have their Mland account resolved (or created on first Google sign-in) by the server after the identity sign-in.
- Reach their destination: a Carrier always goes to Carrier Portal; others go to the `returnTo` page if one is given, otherwise a Member goes to Home Page, Staff to Staff Home, a Manager to Manager Dashboard and an Admin to User Account List.
- Open User Register or Forgot Password.

Only a `returnTo` path on this site is followed.

| Field Name | Description |
| --- | --- |
| Sign in with Google | Shown when Firebase is configured. Redirects to Google and processes the returned sign-in result. An email already registered with a password is refused with guidance to use that method. |
| Email address | Required; must be a valid email format. |
| Password | Required, masked, at least 6 characters. |
| Forgot password? | Link to Forgot Password. |
| Sign in | Submits the form; shows "Processing…" and disables the inputs while working. |
| Error message | Wrong email or password shows "Incorrect email or password."; a suspended or ineligible account shows "Your account is suspended or not eligible."; other messages cover too many attempts, network errors and blocked pop-ups. |
| Retry connection | Shown when the account service is temporarily unavailable; retries the account lookup without signing in again. |
| Register now | Link to User Register. |
| ← Back | Returns to Home Page. |
