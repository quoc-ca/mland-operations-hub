### 15.8 GHTK Delivery Boundary

| Attr | Value |
| --- | --- |
| URL | The GHTK delivery-fee endpoint only; no shipment-creation, status or webhook endpoint in V1. |
| Purpose | Quotes the delivery fee of a GHTK retail branch order before payment (50 g per ring). The shipment record, its internal tracking number and the courier's pickup and delivery steps are kept inside the platform and recorded by the GHTK partner account (role CARRIER); a custom order is handed to Giao Hang Tiet Kiem by a Staff-recorded manual handoff. V1 does not create shipments or track delivery through GHTK, or process delivery-failure, return, cancellation, or refund workflows. |
| Caller | Member checkout → System → GHTK fee API; Staff and the GHTK partner account → System (records only) |
| Auth | The fee query uses the provider credential held by the platform as an approved integration secret; without it the platform uses a local substitute. Only the authorized Staff or the active GHTK partner account may record a shipment step. |

**Source:** [External API Inventory](../../03-i-overall-requirements/05-system-fuctionalities/02-external-api-inventory.md).
