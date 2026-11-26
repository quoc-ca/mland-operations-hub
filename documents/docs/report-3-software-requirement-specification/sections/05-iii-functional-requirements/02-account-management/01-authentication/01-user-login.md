#### 2.1.1 User Login

{{r3-02-user-login width=100% align=center}}

**Description:** Displays the sign-in form and allows Visitors of any role to authenticate with email and password or Google, then redirect to their role home or `returnTo` path.

::: {.docx-table width="100%" columns="50%,50%"}

| Field Name | Description |
| --- | --- |
| Sign in with Google | Authenticates via Google when Firebase is configured. |
| Email address | Required valid email address. |
| Password | Required, masked, at least 6 characters. |
| Forgot password? | Link to Forgot Password. |
| Sign in | Submits credentials; disables inputs while processing. |
| Error message | Displays invalid credentials, suspended account, or network errors. |
| Retry connection | Retries account lookup if the server is temporarily unavailable. |
| Register now | Link to User Register. |
| ← Back | Returns to Home Page. |

:::
