#### 2.2.2 Change Password

![Change Password mockup](assets/screens/09-change-password.png)

This screen allows authenticated users with locally managed credentials to:

- Verify the current locally managed password.
- Submit a new password that satisfies the configured password policy.
- Receive confirmation or validation feedback; provider-managed credentials are changed at the provider.

| Field Name | Description |
| --- | --- |
| Password (generic field) | Masked illustrative input; the frame does not distinguish current-password verification from the new password. |
| Continue | Submits only the current account action after validation; failure leaves the user in a safe recoverable state. |
| Google / Email / Support | Alternative access/help labels; supported providers and exact interaction controls follow the approved account flow. |
| Privacy and recovery panel | Informational text; recovery responses must not disclose whether an account exists. |
