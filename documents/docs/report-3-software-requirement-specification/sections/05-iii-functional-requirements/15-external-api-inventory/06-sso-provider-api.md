### 15.6 SSO Provider API

| Attr | Value |
| --- | --- |
| URL | TBD — approved HTTP method and endpoint pattern. |
| Purpose | Supports external identity registration, login, password recovery, and identity-token exchange. The provider establishes identity only; Mland resolves account status, business role, and authorization from its own database. |
| Caller | System ↔ Google/Facebook SSO |
| Auth | Verify external identity tokens, then resolve current Mland account status/role; exact configured provider contract: TBD. |

**Source:** [External API Inventory](../../03-i-overall-requirements/05-system-fuctionalities/02-external-api-inventory.md).
