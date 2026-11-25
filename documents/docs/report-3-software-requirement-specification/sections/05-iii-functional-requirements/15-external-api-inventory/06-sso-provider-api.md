### 15.6 SSO Provider API

| Attr | Value |
| --- | --- |
| URL | TBD — approved HTTP method and endpoint pattern. |
| Purpose | Firebase Authentication provides email and password registration, sign-in and password reset, and Google sign-in. The provider establishes identity only; Mland resolves account status, role and authorization from its own database. |
| Caller | System ↔ Firebase Authentication / Google |
| Auth | Verify external identity tokens, then resolve current Mland account status/role; exact configured provider contract: TBD. |

**Source:** [External API Inventory](../../03-i-overall-requirements/05-system-fuctionalities/02-external-api-inventory.md).
