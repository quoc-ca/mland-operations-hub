# IV. Non-functional Requirements

This section records the approved V1 non-functional constraints that affect behaviour. Performance, availability, accessibility, and deployment targets remain to be baselined separately; payment, provider, retention, and logging controls below are not `TBD`. The source decisions and vendor evidence are in [Report 2 Evidences](../../../report-2-project-management-plan/sections/02-i-project-overview/07-evidences.md).

| Area | V1 requirement |
| --- | --- |
| Payment integrity | VNPay is the sole payment provider. A browser Return URL is display-only. The application changes payment state only after validating a signed IPN, or the defined signed QueryDR recovery result while the associated hold remains active. Each confirmation validates signature, transaction reference, amount, and successful status; duplicate events are idempotent. |
| Payment timing | A payment link expires after 10 minutes. A workshop slot, retail cart, or custom-order queue slot is held for at most 15 minutes. When no valid payment evidence exists at minute 15, the application releases the hold and does not automatically fulfil a later payment exception. |
| AI data | Gemini is the paid AI service. Application-level text prompts and responses are retained for no more than 30 days. AI image input is not retained after processing. AI suggestions support a human decision and never decide a custom-manufacturing order. |
| Personal data retention | Custom-manufacturing reference images are retained until pickup or GHTK handoff; rejected or withdrawn custom requests and independently classified images are deleted immediately after processing. GHTK recipient/address data are deleted or irreversibly obscured 30 days after handoff. VNPay references and business audit evidence are retained for five years. Card data is never stored. |
| Privacy notice | Mland's general Terms and Privacy Policy states image-processing purposes, approved retention periods, Google attribution, and delivery-recipient data handling. V1 does not add a feature-specific image-consent checkbox. |
| Google Maps data | Google Maps Embed and Places are read-only. Places review/rating content is not persisted and must display required attribution and a link back to Google Maps. |
| Delivery boundary | GHTK is a manual-handoff provider only. V1 has no GHTK API integration, delivery fee quotation, delivery tracking, delivery-failure, return, cancellation, or refund workflow. |
| Auditability | Payment confirmation, price publication, booking-design review, custom final amount, pickup, and GHTK handoff must retain business audit evidence for the stated retention period. |

The V1 implementation remains a Spring Boot modular monolith. This is an implementation convention and does not define a production deployment target or supersede the business constraints above.
