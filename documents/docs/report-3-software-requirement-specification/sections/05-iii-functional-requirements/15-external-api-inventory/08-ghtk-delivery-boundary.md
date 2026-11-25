### 15.8 GHTK Delivery Boundary

| Attr | Value |
| --- | --- |
| URL | GHTK delivery-fee query (GET, configurable base URL). |
| Purpose | Quotes the delivery fee and fails closed when unavailable. No shipment is created or tracked through GHTK; shipment steps are recorded in the GHTK partner portal of the platform. No delivery-failure, return, cancellation or refund workflow. |
| Caller | System → GHTK |
| Auth | Configured GHTK token and client source. |

**Source:** [External API Inventory](../../03-i-overall-requirements/05-system-fuctionalities/02-external-api-inventory.md).
