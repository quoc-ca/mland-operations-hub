## **4.4  \[Entity\] State Transition Table**

| Entity | Minimum baseline transition | Boundary / open detail |
| --- | --- | --- |
| Booking | `deposit pending` → `confirmed` only after verified 50% gateway deposit; rescheduling requires at least 24 hours and capacity. | Detailed cancellation/refund state, no-show exception implementation, and provider-specific payment transition are `TBD`. |
| Invoice | `issued` → `deposit recorded` → `final settlement recorded`; an approved adjustment may be added without erasing invoice history. | Invoice numbering, reversal and consent wording are `TBD`. |
| Payment Transaction | `pending confirmation` → `confirmed` or unresolved/failed. | Provider-specific webhook/reconciliation and retry states are `TBD`. |
| Retail Order | `payment pending` → `paid/reserved` → `pickup ready` **or** `handed to carrier`. | No carrier status, delivery failure, return or shipping-refund state is in V1. |
| Stock Reservation | Created only with the `paid/reserved` retail state for one unit and remains until pickup or carrier handoff. | Manual-exception/release rules and stock model are `TBD`. |
