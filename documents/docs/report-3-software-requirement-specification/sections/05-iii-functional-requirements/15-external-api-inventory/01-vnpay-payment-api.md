### 15.1 VNPay Payment API

| Attr | Value |
| --- | --- |
| URL | TBD — approved HTTP method and endpoint pattern. |
| Purpose | Creates payment requests and receives approved signed payment confirmations for workshop deposits, retail payments, custom-order deposits, and custom-order final balances. The system validates the signature, transaction reference, amount, success status, and duplicate-event status. A browser Return URL never confirms payment. |
| Caller | System → VNPay / VNPay → System |
| Auth | Configured VNPay signing/verification credentials; validate reference, amount, currency and result. Exact provider contract: TBD. |

**Source:** [External API Inventory](../../03-i-overall-requirements/05-system-fuctionalities/02-external-api-inventory.md).
