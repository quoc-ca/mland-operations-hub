#### 2.1.6 SSO Provider

![SSO Provider mockup](assets/screens/07-sso-provider.png)

This screen allows users to:

- Continue to the configured external identity provider for sign-in or registration.
- Return to Mland after provider authentication for account-status and role checks.
- Recover from a failed or cancelled provider sign-in without gaining an authenticated session.

| Field Name | Description |
| --- | --- |
| Provider redirect notice | Read-only external-sign-in handoff message; never collect the provider password in Mland. |
| Continue | Submits only the current account action after validation; failure leaves the user in a safe recoverable state. |
| Google / Email / Support | Alternative access/help labels; supported providers and exact interaction controls follow the approved account flow. |
| Privacy and recovery panel | Informational text; recovery responses must not disclose whether an account exists. |
