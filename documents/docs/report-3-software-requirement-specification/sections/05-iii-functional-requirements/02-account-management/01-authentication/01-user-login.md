#### 2.1.1 User Login

![User Login mockup](assets/screens/02-user-login.png)

This screen allows users to:

- Enter email and password or choose a supported SSO sign-in method.
- Authenticate and reach the dashboard permitted by the current account role and status.
- Open account registration or password recovery when needed.

| Field Name | Description |
| --- | --- |
| Email | Email-format text for the selected account flow; example text is not initial account data. |
| Password | Masked credential input; never prefill from the illustrative dots or expose stored passwords. |
| Continue | Submits only the current account action after validation; failure leaves the user in a safe recoverable state. |
| Google / Email / Support | Alternative access/help labels; supported providers and exact interaction controls follow the approved account flow. |
| Privacy and recovery panel | Informational text; recovery responses must not disclose whether an account exists. |
