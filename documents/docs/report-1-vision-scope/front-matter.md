# Vision & Scope Document

**Personalized Product Sales and Workshop Booking System — Report 1**

> **Active project, under validation.** The current V1 baseline starts on 15 September 2026; unverified design and algorithm details remain open.

| Project information | Value |
| --- | --- |
| Group | SEP490_G22 |
| English project name | Personalized Product Sales and Workshop Booking System |
| Status | Active V1 baseline, under validation |

**Known Mland context:** Mland offers personalised jewellery-making workshops focused on rings. Booking and quote interactions are currently handled manually through channels such as Instagram and WhatsApp.

> **Stakeholder statement — 16 September 2026:** Mland currently operates two workshop locations. A proposed operating day has three 150-minute sessions: 09:30–12:00, 13:00–15:30, and 16:00–18:30. A proposed attendance rule charges 100,000 VND for each unbooked additional person who actually participates in making the product; staff confirm participation at check-in. A work-in-progress product may be held by the shop and continued under a later booking created by staff. These inputs were partly approved by the group decision on 18 September 2026.

> **Group decision — 17 September 2026, informed by lecturer direction:** The system has eight actors: Guest, Member, Staff, Owner, AI API, Email Sender, Payment Gateway, and Admin Technical. Guest and Member may book; every workshop package has a 50% online deposit and invoice. Staff record auditable, customer-consented adjustments; the remaining 50% plus adjustments is settled by cash or bank transfer at the shop. Only Member may buy ready-made rings: full online payment reserves one unit, then the Member chooses pickup or third-party delivery handoff. Admin Technical manages technical configuration, integrations, monitoring, and technical audit only.

> **Group decision — 18 September 2026:** The three daily sessions are 09:30–12:00, 13:00–15:30, and 16:00–18:30; capacity is configured per location/session. Rescheduling requires at least 24 hours and an available session; a no-show loses its booking/deposit unless Staff or Owner records an exception. The 100,000 VND extra-attendee fee applies only to an unbooked person who actually makes the product, is Staff-attested at check-in, and is added only with customer consent; Owner approves a waiver/change. Staff-created continuation requires customer request and available capacity, with auditable custody/release evidence. The configurator uses only Owner-confirmed components; AI returns candidate features only and feasibility routing is simple/medium/advanced/impossible. Customer reference images require consent and have a defined request lifecycle. Google review collection is by Google link/QR; Owner may manually import reviews one-way from Google only after access and governance prerequisites.

> **TBD — group decision required:** exact location capacity/capability values; component/package data, prices and hard constraints; numeric feasibility thresholds and evaluation evidence; consent wording, provider retention and review-operation coverage; manual-refund/force-majeure policy; Google Review OAuth/API access plus retention/attribution/appeal policy; delivery-recipient retention and failed-handoff handling; all Payment Gateway provider, webhook/IPN, reconciliation, retry, duplicate-event and refund rules; and V1 success measures.
