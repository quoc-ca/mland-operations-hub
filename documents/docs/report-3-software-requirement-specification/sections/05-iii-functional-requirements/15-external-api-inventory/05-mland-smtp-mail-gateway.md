### 15.5 Mland SMTP / Mail Gateway

| Attr | Value |
| --- | --- |
| URL | TBD — approved HTTP method and endpoint pattern. |
| Purpose | Delivers mandatory Staff/Manager invitations and workflow emails, including workshop and retail confirmations. Account verification and password-reset emails are sent by Firebase. SMTP acceptance does not prove inbox delivery or account activation. The mail service makes no business decision and provides no notification centre. |
| Caller | System → Mail Gateway |
| Auth | Configured mail-gateway credentials; exact transport and authentication contract: TBD. |

**Source:** [External API Inventory](../../03-i-overall-requirements/05-system-fuctionalities/02-external-api-inventory.md).
