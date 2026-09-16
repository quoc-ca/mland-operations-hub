## **1.3  System Roles**

The approved V1 context has eight actors. Their detailed permissions, identity proofing and authentication mechanisms remain `TBD` unless stated below.

| Actor | Approved V1 responsibility | Boundary |
| --- | --- | --- |
| Guest | Book a workshop, pay its deposit, provide permitted design input and view the guest booking/invoice outcome. | Cannot use Member-only retail checkout, participation history, transaction history or upcoming-event view. |
| Member | Perform the Guest booking journey; view Member history/upcoming workshops; buy a ready-made ring. | Loyalty is deferred. |
| Staff | Manage workshop operations, fulfilment, recorded invoice adjustments, in-shop cash/bank settlement and delivery handoff. | Adjustment needs reason, actor, time and customer consent. |
| Owner | Manage authorised business configuration and review business audit/override records. | Detailed approval rules remain `TBD`. |
| Admin Technical | Manage technical configuration, integration, monitoring and technical audit. | Must not mutate invoice, payment, order or other business records. |
| AI API | Receive only a consented reference image and return candidate features/quality. | Does not create a design, final price or business decision. |
| Email Sender | Deliver permitted booking, review and order notifications. | Provider and content rules are `TBD`. |
| Payment Gateway | Confirm workshop deposits and Member retail full payments. | A redirect alone is not payment confirmation; provider and webhook verification are `TBD`. |
