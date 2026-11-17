### 15.7 Cloud Storage API

| Attr | Value |
| --- | --- |
| URL | TBD — approved HTTP method and endpoint pattern. |
| Purpose | Stores catalogue media and eligible custom-manufacturing reference images outside the application database. Access and deletion follow the approved retention rules; rejected or withdrawn custom requests and independently classified AI images are deleted after processing. |
| Caller | System ↔ Cloud Storage Service |
| Auth | Configured storage credentials and purpose-specific read/write/delete access; exact provider and access contract: TBD. |

**Source:** [External API Inventory](../../03-i-overall-requirements/05-system-fuctionalities/02-external-api-inventory.md).
