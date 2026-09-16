## **4.4  \[Entity\] State Transition Table**

| Entity | Minimum baseline transition | Boundary / open detail |
| --- | --- | --- |
| Booking | `deposit pending` → `confirmed` only after verified 50% gateway deposit. | Cancellation/no-show state and refund policy are `TBD`. |
| Invoice | `issued` → `deposit recorded` → `final settlement recorded`; an approved adjustment may be added without erasing invoice history. | Invoice numbering, reversal and consent wording are `TBD`. |
| Payment Transaction | `pending confirmation` → `confirmed` or unresolved/failed. | Provider-specific webhook/reconciliation and retry states are `TBD`. |
| Retail Order | `payment pending` → `paid/reserved` → `pickup ready` **or** `handed to carrier`. | No carrier status, delivery failure, return or shipping-refund state is in V1. |
| Stock Reservation | Created only with the `paid/reserved` retail state for one unit. | Expiry/release rules are `TBD`. |
