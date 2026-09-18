# **Part 5 — Business Rules**

The following rules are group decisions dated 17 and 18 September 2026, informed by lecturer direction. Rules not expressly stated remain `Open/TBD`.

| ID | Rule |
| --- | --- |
| BR-01 | Guest and Member may both book a workshop package. Each booking creates an invoice and requires a 50% package deposit through the Payment Gateway. |
| BR-02 | A booking is not confirmed by a gateway redirect alone; only a verified gateway confirmation/reconciliation can confirm its deposit. |
| BR-03 | Staff may add an invoice adjustment only with reason, actor, timestamp and customer consent. The issued invoice history is preserved. |
| BR-04 | The workshop final balance is the remaining 50% package price plus approved adjustments, settled cash/bank at the shop when the work is completed or collected. |
| BR-05 | Only a Member may buy a ready-made ring. Full verified online payment reserves one unit, then the Member selects pickup or third-party delivery handoff. |
| BR-06 | the system records delivery only up to staff handoff to a third-party carrier. Carrier integration, live tracking, delivery failure, return and shipping-refund workflows are excluded. |
| BR-07 | Admin Technical may manage technical configuration, integrations, monitoring and technical audit only; this actor cannot change business records. |
| BR-08 | Each operating day has workshop sessions at 09:30–12:00, 13:00–15:30, and 16:00–18:30. Capacity is configured per location/session; exact values are operational configuration. |
| BR-09 | A customer may request rescheduling at least 24 hours before the original session and only to a session with capacity. A no-show loses its booking/deposit unless Staff or Owner records an exception. |
| BR-10 | The 100,000 VND extra-attendee fee applies only to each unbooked person actually participating in making the product. Staff attests participation at check-in; the invoice adjustment requires customer consent. Owner approves any waiver or fee change, and Staff records the reason. |
| BR-11 | Staff may create a continuation booking only on customer request and when capacity is available. Custody requires work-item description, intake photo, location/time, Staff actor, and auditable receipt/release; release requires booking code or QR plus Staff check. |
| BR-12 | The configurator presents only Owner-confirmed components with current price, difficulty, and hard constraints. AI may return candidate image features only; it does not decide price or feasibility. |
| BR-13 | The feasibility engine routes a request as simple/auto-accept, medium/Staff review, advanced/Owner review, or impossible/auto-reject using Owner-configured rules. Rule changes and overrides require audit; numeric values and evaluation evidence remain open. |
| BR-14 | Reference-image processing requires explicit consent. Accepted-request images are retained until order completion; rejected or withdrawn requests are deleted. The review target is six operating hours, and a Guest uses request code plus email to look up the request. |
| BR-15 | The website provides a Google review link or QR for a customer-authored Google review. After verified access and approved governance, Owner may manually trigger one-way import from Google Business Profile; imported reviews may be public on the website and visible to Owner. No payroll or bonus automation is permitted. |
| BR-16 | A ready-ring reservation has no automatic expiry in V1. After verified full payment it remains reserved until pickup or carrier handoff; Staff records carrier, handoff reference, actor, and timestamp. |
| BR-17 | Payment Gateway provider selection, webhook/IPN verification, reconciliation, retry, duplicate-event handling, and refund rules are unresolved. A browser redirect never confirms a booking or retail payment. |
