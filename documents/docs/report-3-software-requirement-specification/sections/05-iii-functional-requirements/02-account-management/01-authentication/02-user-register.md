#### 2.1.2 User Register

![](assets/screens/03-user-register.png)

This screen allows users to:

- Register a Member account using email and a matching password confirmation, or supported SSO.
- Complete the required email verification before email-account activation.
- Receive validation feedback when the email or external identity is already linked to an account.

| Field Name | Description |
| --- | --- |
| Email | Email-format text for the selected account flow; example text is not initial account data. |
| Password | Masked credential input; never prefill from the illustrative dots or expose stored passwords. |
| Confirm password | Masked confirmation that must match the new password; it is not a separate account credential. |
| Continue | Submits only the current account action after validation; failure leaves the user in a safe recoverable state. |
| Google / Email / Support | Alternative access/help labels; supported providers and exact interaction controls follow the approved account flow. |
| Privacy and recovery panel | Informational text; recovery responses must not disclose whether an account exists. |
