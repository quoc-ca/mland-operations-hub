### 15.8 GHTK Delivery Boundary

| Attr | Value |
| --- | --- |
| URL | Not applicable; no GHTK API in V1. |
| Purpose | Records Staff-confirmed handoff of a paid retail or custom order to Giao Hang Tiet Kiem. V1 does not call GHTK APIs, quote delivery fees, track delivery, or process delivery-failure, return, cancellation, or refund workflows. |
| Caller | Staff → System → GHTK manual handoff |
| Auth | No provider API authentication. Only the authorized shop actor may record a valid manual handoff. |

**Source:** [External API Inventory](../../03-i-overall-requirements/05-system-fuctionalities/02-external-api-inventory.md).
