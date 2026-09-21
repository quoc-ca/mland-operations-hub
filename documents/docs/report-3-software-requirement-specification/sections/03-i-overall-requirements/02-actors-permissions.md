## 2. Actors and Permission Boundary

| Actor | Permitted interaction |
| --- | --- |
| Guest | Book a workshop, pay deposit, submit consented design reference, and look up a booking. No ready-ring checkout or Member history. |
| Member | All shared booking actions plus permitted participation/transaction/upcoming-workshop history and ready-ring retail. Loyalty is not in V1. |
| Staff | Manage sessions, check in participants, record auditable adjustments/consent, custody, settlement, continuation booking, fulfilment, and carrier handoff. |
| Owner | Maintain owner-confirmed catalogue/rules, approve exceptions/overrides, and review audits. |
| AI API | Returns image feature candidates only after consent; it does not set price or feasibility. |
| Email Sender | Delivers system-requested notifications; provider and delivery contract are `TBD`. |
| Payment Gateway | Returns payment confirmation; browser redirect alone never confirms a booking/order. Provider and webhook/IPN contract are `TBD`. |
| Admin Technical | Controls technical configuration, integration monitoring, and technical audit only; cannot mutate business invoices, payments, orders, or records. |
