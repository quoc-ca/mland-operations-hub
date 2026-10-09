#### 2.1.3 Email Verification

![Email Verification mockup](assets/screens/04-email-verification.png)

This screen allows users to:

- View registration-verification instructions and the current verification outcome.
- Complete verification through a valid link or token.
- Request another verification when eligible after expiry or failure.

| Field Name | Description |
| --- | --- |
| Verification notice | Read-only instruction to check email; link expiry and eligible resend follow the account-verification policy. |
| Continue | Submits only the current account action after validation; failure leaves the user in a safe recoverable state. |
| Google / Email / Support | Alternative access/help labels; supported providers and exact interaction controls follow the approved account flow. |
| Privacy and recovery panel | Informational text; recovery responses must not disclose whether an account exists. |
