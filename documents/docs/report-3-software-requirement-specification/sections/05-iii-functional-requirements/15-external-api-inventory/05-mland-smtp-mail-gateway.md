### 15.5 Mland SMTP / Mail Gateway

| Attr | Value |
| --- | --- |
| URL | TBD — approved HTTP method and endpoint pattern. |
| Purpose | Delivers account-security and workflow emails, including OTP or verification messages, workshop QR tickets, payment requests/receipts, booking-design review results, and order-status messages. The mail service does not make business decisions or provide a notification centre. |
| Caller | System → Mail Gateway |
| Auth | Configured mail-gateway credentials; exact transport and authentication contract: TBD. |

**Source:** [External API Inventory](../../03-i-overall-requirements/05-system-fuctionalities/02-external-api-inventory.md).
