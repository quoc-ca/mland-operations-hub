# General Spec
# Version: 0.1 | Status: Working Agreement | Updated: 2026-10-06

> This working agreement guides agent planning and implementation. It consolidates the current product context without replacing the approved documentation baseline in `../documents/docs/`. Report 2 defines V1 scope and business decisions; Report 3 is the current requirements baseline. Detailed rules remain in those reports. Unresolved architecture, deployment, performance, availability, accessibility, and role-code decisions remain `TBD` until formally approved.

## 1. Context & Goal

**Business problem:** Mland is a ring atelier that needs one bilingual operations platform for public discovery, workshop booking, ring-design consultation, Member retail, custom manufacturing, and shop-side fulfilment. The platform should keep customer intent, operational decisions, payments, and fulfilment evidence connected without becoming an ERP, warehouse-management, accounting, or end-to-end logistics system.

**System goal:** Support Guest and Member journeys while giving Staff, Managers, and Admins clear operational responsibilities. Workshop booking and workshop ring-design review are one journey; custom manufacturing is a distinct Member-only order flow. Report 3 remains the detailed source for its use cases, permission matrix, and business rules; this file summarizes their shared context and implementation guardrails.

**V1 scope summary:** Account/RBAC, public product and workshop catalogues, workshop schedules/bookings, workshop ring design, independent AI support and human-reviewed AI suggestions, Member retail, loyalty/promotions, custom manufacturing, workflow email, operational administration/dashboard, read-only Google Maps information, and workshop registration synchronization with Klook. Staff operates product catalogue and manually maintained available-to-sell quantities. Managers operate workshop schedules/capacity and business approvals, promotions, and the executive dashboard. Admins manage accounts/RBAC, operational parameters, and approved integration credentials. The exact role-code mapping is unresolved; see [Actors & Roles](#2-actors--roles).

**Technology context:** Java 21 and Spring Boot; Thymeleaf server-rendered pages with htmx progressive enhancement; REST API; Spring Data JPA/JPA and MySQL. Firebase Authentication is the current identity-provider baseline. Firebase Hosting is approved only as a preview capability; it does not select production hosting, backend origin, database deployment, or CDN behavior.

**Architecture convention:** The backend is a Spring Modulith modular monolith. Each business module owns `facade/`, `web/`, `application/`, `domain/`, and `infrastructure/`; its named `facade/` is the only Java interface another business module may call. Firebase identity integration remains within the `members` module. Cross-domain calls are synchronous one-way facade calls by default; internal domain events are reserved for callbacks, asynchronous workflows, or cycle removal and require idempotent consumers. The technical `common` kernel has no business entities, DTOs, or shared business workflows.

## 2. Actors & Roles

Report 3 defines these business-facing platform actors. Firebase proves identity; Mland/MySQL determines application account state and authorization.

| Actor | Type | Responsibility and boundary |
| --- | --- | --- |
| Guest | Public customer | Browses published products, ring models, workshop packages/schedules, and public store information; may use basic AI support and book a workshop. Cannot submit a booking-design image, place retail checkout, or create a custom-manufacturing order. |
| Member | Authenticated customer | Uses own profile/history, workshop booking and booking-design capabilities, retail/loyalty, consultation, and custom-manufacturing journeys. May submit booking-design images and separately create a custom order. |
| Staff | Internal business actor | Maintains product catalogue and available-to-sell quantities; checks in participants; reviews booking-design requests at or below 3,000,000 VND; prepares paid retail orders; records pickup/GHTK handoff and custom-order final amounts as authorized. |
| Manager | Internal business actor | Manages workshop schedule, staff assignment, capacity, materials, closures, promotions/vouchers, and dashboard; reviews booking-design requests above 3,000,000 VND; decides published prices after considering AI suggestions; may configure custom-order queue and final amounts. |
| Admin | Internal technical/operational actor | Manages accounts, roles/RBAC, account status, operational parameters, and approved integration credentials. Does not replace Staff/Manager business approval unless an explicit permission allows it. |
| SSO Provider | External identity service | Firebase Authentication is the current identity baseline; configured sign-in methods establish identity and issue Firebase ID tokens. Provider identity/claims do not confer Mland roles. |
| Payment Gateway | External provider | VNPay, the sole V1 payment provider for workshop deposits, retail orders, and custom-order deposit/balance. |
| AI API | External provider | Paid Gemini API for basic text support, workshop booking-design image analysis, and price suggestions for new workshop packages or retail products. Suggestions support human decisions only. |
| Mail Gateway | External provider | Mland-domain SMTP service for account-security and workflow messages such as QR tickets, payment requests/receipts, review results, and order-status messages. It is not a notification centre. |
| Cloud Storage Service | External provider | Stores catalogue media and eligible custom-manufacturing reference images subject to retention/deletion rules. |
| Google Maps API | External provider | Google Maps Embed and Places provide read-only map and permitted public review/rating information with attribution and a link to Google Maps. |
| Delivery Provider | External provider | Giao Hàng Tiết Kiệm (GHTK), used only for Staff-recorded manual handoff; no V1 carrier API integration. |
| External Registration System | External provider | Klook, the sole V1 external workshop-registration partner; Mland remains source of truth for capacity. |

**Role-code reconciliation:** Report 2/3 use the business labels `Member`, `Staff`, `Manager`, and `Admin`; the authentication feature and DB working material use codes including `MEMBER`, `STAFF`, `OWNER`, and `ADMIN_TECHNICAL`. The mapping and any permission differences remain `TBD`. Do not silently map Manager to Owner or Admin to Admin Technical, change a role code, or grant authority based on a display label.

The existing authentication working agreement requires MFA for privileged internal roles. Preserve this requirement; the role-code mapping and the approved enforcement/recovery mechanism must be resolved before enabling those roles.

## 3. Functional Requirements

The following system-level requirements summarize Report 2/3. Feature specifications and the public API contract own detailed behavior.

### Identity and authorization

* The web client SHALL use the Firebase Web SDK for enabled Google, Facebook, and Firebase email/password sign-in methods. Provider availability remains subject to configuration/approval.
* The client SHALL send a Firebase ID token as `Authorization: Bearer` on protected API requests. The Spring backend SHALL verify the token with Firebase Admin SDK before using its UID. Raw tokens SHALL NOT be placed in URLs, logs, or committed files.
* MySQL SHALL remain authoritative for Mland account status, business role, entitlement, and audit. Provider, email, display-name, photo, or Firebase custom claims SHALL NOT grant business authority.
* A verified first sign-in SHALL provision at most one Member by Firebase UID. Accounts with different UIDs SHALL NOT be merged automatically based on email.
* Guest-accessible public journeys remain public; Member-only history, retail checkout, booking-design image submission, and custom manufacturing require the corresponding Mland authorization.

### Catalogue and workshop booking

* Guests and Members SHALL be able to browse published products, workshop packages/schedules, available ring models, and permitted public store information.
* A workshop booking SHALL select a package before schedule/slot and supported ring-design path. A booking is confirmed only after valid confirmation of the 50% package-price deposit through VNPay.
* A Member may submit a workshop booking-design image for paid Gemini analysis and human review. Staff reviews estimated values at or below 3,000,000 VND; a Manager reviews values above that threshold. The Member can view the recorded result.
* AI MAY suggest materials/components or prices for a new workshop package or retail product. A Manager decides and publishes the price; AI does not publish it or decide custom-manufacturing orders.
* Klook synchronization SHALL respect Mland-owned capacity and the approved availability/hold flow. Mland V1 does not consume Klook cancellation events.

### Retail, loyalty, and promotions

* Retail checkout is Member-only. Checkout revalidates the whole cart, snapshots applicable prices, and does not create a partial order when a cart line is invalid or unavailable.
* A retail order requires verified full payment before sale completion and held quantity deduction. One eligible voucher may be used; loyalty points are a non-cash discount and may be combined within configured limits.
* Staff records internal order milestones, customer pickup, or manual GHTK handoff. Courier transit/tracking states are outside the system.
* Managers operate promotions and vouchers; Admin-configured parameters govern loyalty rates, point value, and discount caps.

### Custom manufacturing and fulfilment

* Only a Member may submit a custom-manufacturing order using a reference image or supported manual configuration, an optional requested deadline, and a fulfilment method. This is separate from workshop booking-design review.
* Manager/Admin configuration defines minimum deadline, unavailable deadline dates, and maximum queue size. The system assigns the earliest valid date when none is requested and auto-accepts only when deadline and queue-capacity conditions pass.
* An accepted custom request reserves a queue slot when deposit payment starts. Deposit is 50% of the wax-package price; when ready, Staff/Manager records the remaining 50% plus actual surcharges and the system requests the balance through email/VNPay.
* Fulfilment requires verified payment and is completed by Member pickup or Staff-recorded manual GHTK handoff. AI SHALL NOT price, accept, reject, or review custom orders.

### Administration and workflow communication

* Admin manages accounts/RBAC, account lock status, operating parameters, and approved third-party credentials. Managers own the specified workshop and business-approval operations.
* The executive dashboard summarizes business revenue, workshop utilization, and branch KPIs for authorized roles.
* Workflow messages are system responses delivered through the Mland-domain Mail Gateway where applicable. V1 does not add a standalone notification centre.

### Public store information

* Google Maps Embed and Places SHALL be used read-only to display store location and permitted public review/rating information with required attribution and a Google Maps link.
* Mland SHALL NOT persist Places review/rating content, collect an internal store review, or submit/write reviews to Google.

## 4. Non-functional Requirements

* **Payment integrity:** VNPay is the sole V1 provider. A browser Return URL is display-only. Payment state changes require a valid signed IPN or the defined signed QueryDR recovery result while the associated hold remains active. Validate signature/checksum, transaction reference, amount, and success status; process duplicate events idempotently.
* **Identity and authorization security:** Verify Firebase token signature, issuer, audience, and expiration with Firebase Admin SDK. Authentication proves identity; Mland authorization decides business access on every protected operation. Do not log ID/provider tokens, secrets, or unnecessary PII.
* **Payment timing:** A payment link expires after 10 minutes. A workshop slot, retail cart, or custom-order queue slot is held for at most 15 minutes. If no valid evidence exists at minute 15, release the hold; late payment evidence does not automatically fulfil and requires manual operational follow-up.
* **AI data:** Gemini is a paid service. Application-level text prompts/responses are retained for no more than 30 days; AI image input is not retained after processing. AI output supports human decisions only.
* **Personal-data retention:** Custom-manufacturing reference images are retained until pickup or GHTK handoff. Rejected/withdrawn custom requests and independently classified images are deleted immediately after processing. GHTK recipient/address data are deleted or irreversibly obscured 30 days after handoff. VNPay references and business audit evidence are retained for five years. No card data is stored.
* **Privacy notice:** Mland's general Terms and Privacy Policy states image-processing purposes, approved retention periods, Google attribution, and delivery-recipient data handling; V1 does not add a feature-specific image-consent checkbox.
* **Integration boundaries:** Google Places content is not persisted; GHTK has no V1 API/quote/tracking/failure/return workflow; Klook cancellation events are not consumed; workflow SMTP only delivers requested messages.
* **Hosting and caching:** Firebase Hosting is preview-only. Cache behavior applies only to deliberately public content; never cache identity tokens, authenticated responses, booking capacity, payment status, invoices, Member history, or administration data.
* **Auditability:** Payment confirmation, price publication, booking-design review, custom final amount, pickup, and carrier handoff retain business audit evidence under the stated retention period.
* **Unbaselined quality targets:** Performance, availability/SLA, accessibility standard, supported browser/device baseline, production hosting/deployment, and production backend origin remain `TBD`. Do not infer numeric targets from the preview setup.

## 5. Data Model

Physical schemas remain owned by feature plans, migrations, and the evolving [DB working document](DB.md). This section defines only conceptual ownership and invariants.

| Data | Authority/owner | Constraint |
| --- | --- | --- |
| Firebase UID / `external_user_id` | Firebase identity mapped by Mland MySQL | Unique Member lookup identity; never use email as an automatic merge key. |
| Account status, business roles, entitlements, policy acceptance, and auth audit | Mland MySQL / Members domain | Provider claims do not grant roles; role-code reconciliation remains `TBD`. |
| Profile/contact snapshot | Mland account/profile capability | Contact values may be absent or change; they are not authorization keys. |
| Workshop packages, schedules, capacity, bookings, and check-in evidence | Mland business domains | Mland owns workshop capacity, including when exchanging availability with Klook. |
| Catalogue, component/material and published-price data | Mland catalogue domain | Staff maintains product catalogue; Managers decide published prices for newly priced packages/products. |
| Booking-design input, AI analysis, review outcome, and custom-order request | Separate Mland workflows | Workshop booking-design review is distinct from custom manufacturing; AI does not decide custom orders. |
| Retail cart/order, invoices, loyalty/voucher application, payment references, and fulfilment milestones | Mland sales/payment/fulfilment domains | Full verified retail payment precedes sale completion; fulfilment evidence ends at pickup or carrier handoff. This does not create accounting or reconciliation scope. |
| Media and custom reference images | Approved cloud storage service under Mland retention policy | Apply the deletion/retention rules in [Non-functional Requirements](#4-non-functional-requirements). |
| Credentials, passwords, and Firebase token lifecycle | Firebase Authentication/provider | Never copy provider credentials, password hashes, or refresh lifecycle into Mland business storage. |

## 6. Error Handling & Security

* Missing, malformed, expired, revoked, wrong-project, or unverifiable Firebase bearer tokens SHALL return `401 Unauthorized` without exposing token details. A verified identity with a locked account or insufficient Mland entitlement SHALL return `403 Forbidden` without protected data.
* Concurrent provisioning of the same verified Firebase UID SHALL leave at most one Mland Member record through a unique identity constraint and transaction handling.
* Same-email/different-UID collisions SHALL not merge Member records, bookings, orders, or roles automatically. Provider-link and account-recovery failures must preserve existing account/history and return safe guidance.
* Every protected operation SHALL authorize using Mland role/status and resource ownership on the server. Do not trust client-supplied role/price/status or provider claims.
* VNPay confirmation SHALL validate signed provider evidence, reference, amount, and success status. Return URL alone cannot confirm payment. Duplicate callbacks are idempotent; a payment after hold expiry is an operational exception and does not trigger fulfilment.
* On payment expiry without valid confirmation, release the relevant hold. Klook cancellation is not an inbound system event in V1; GHTK delivery progress is not a system workflow.
* Do not log passwords, raw ID/provider tokens, secrets, card data, or unnecessary PII. Keep redacted, purpose-limited audit evidence for business actions under the five-year retention decision.
* Client sign-out SHALL use the Firebase SDK. This agreement does not create a separate Mland password store, application-signed JWT, or refresh-token system.

## 7. Acceptance Criteria

* [ ] A Guest can browse public catalogue/workshop/store information and create a workshop booking only through the package-first flow; a booking is confirmed only after the verified 50% VNPay deposit.
* [ ] A verified Firebase UID provisions at most one Member; repeated sign-in retains business history, and provider/email claims cannot grant Mland roles or merge different UIDs.
* [ ] A Member's workshop booking-design image can be analyzed and reviewed by the threshold-appropriate human role, and the Member can view the outcome; the separate custom-manufacturing path is never decided by AI.
* [ ] Member retail checkout revalidates the whole cart and requires verified full payment before completing the sale; loyalty/voucher application follows configured limits.
* [ ] A Member custom order follows the configured deadline/queue, 50% wax-package deposit, recorded final balance, verified balance payment, and pickup/manual-handoff flow.
* [ ] VNPay payment links expire after 10 minutes and associated holds after at most 15 minutes; invalid/duplicate callbacks cannot confirm twice, and Return URL cannot confirm payment.
* [ ] Google Maps/Places display is read-only with attribution/link and no persisted Places content; Klook capacity exchange respects Mland as source of truth; GHTK handoff is manual and no courier tracking is claimed.
* [ ] Gemini, AI-image, custom-image, delivery-address, payment-reference, and audit data follow the specified retention periods; no card data is stored.
* [ ] Unresolved role mapping, test environment, deployment, performance, availability, and accessibility decisions remain explicitly `TBD` until approved; implementation introduces no Mland password hash, application-signed JWT, committed provider secret, or raw token logging.

## 8. Use Case Relationships

```text
Guest / Member ──browse package, choose design path──► Workshop booking
Workshop booking ──50% deposit / verified VNPay──► Confirmed booking
Member ──booking-design image──► Gemini analysis ──► Staff / Manager review ──► Member views outcome
Member ──separate custom order──► deadline + queue validation ──50% deposit──► final balance ──► pickup / manual GHTK handoff
Member ──retail cart──► whole-cart validation ──full VNPay payment──► pickup / manual GHTK handoff
Manager ──considers Gemini suggestion──► decides published package/product price
Mland ──availability / enabled booking updates──► Klook (Mland retains capacity authority)
Mland ──read-only map / attributed Places data──► public store information
Mland workflow ──requested message──► Mland-domain Mail Gateway (no notification centre)
Firebase Authentication ──verified ID token──► Mland account, status and authorization
```

## 9. Out of Scope

* WMS/ERP, warehouse locations, serials/lots, stock movements, procurement, inventory ledger, real-time stock guarantees, accounting, tax, payment reconciliation, or a general e-commerce platform.
* Guest retail checkout, address book, shipping quote, GHTK API integration/tracking, delivery-failure processing, returns, in-system cancellation, and refunds. Customers seeking cancellation/refund contact the shop directly.
* Mland-managed passwords/password hashes, Mland-signed JWTs, automatic same-email account merging, or granting business roles from provider claims.
* AI-generated final designs, automated feasibility/price/accept/reject decisions for custom manufacturing, AI publication of prices, or a standalone notification centre.
* Google Places review/rating persistence, internal store reviews, or review write-back to Google; automatic processing of Klook cancellations.
* Firebase Test Lab as the testing strategy for the server-rendered web application.
* Firebase Hosting production deployment, Hosting rewrites, Cloud Run/VPS selection, production database deployment, production custom domain, or a production Firebase project decision.

## 10. Notes / Open Questions

* How should Report 3 business labels `Manager`/`Admin` reconcile with implementation role codes `OWNER`/`ADMIN_TECHNICAL` without changing approved permission boundaries? This remains `TBD`; do not infer a mapping.
* Which Firebase project hierarchy, billing owner, access roles, secret-management process, and provider approvals will be used for development/staging/production?
* Will Firebase Auth Emulator or a separate non-production Firebase project be used for authentication integration tests? The production project must not be used for tests.
* What is the final safe account-linking, recovery, and support path for provider collisions or same-email/different-UID cases?
* How will MFA be enforced, recovered, and audited for privileged internal roles under the selected Firebase/Identity Platform capability and role mapping?
* Which provider contracts, credentials, callback URLs, quotas, supplier approvals, and SMTP/cloud-storage configuration are required for go-live? Keep credentials out of this repository.
* What production origin/deployment model, performance/availability targets, accessibility standard, and browser/device baseline will be approved?
* Which preview content may be published through Firebase Hosting, with what access boundary and expiry lifecycle?

## References

### Product and business baseline

* [Report 2 — Scope and Purpose](../documents/docs/report-2-project-management-plan/sections/02-i-project-overview/01-scope-purpose.md)
* [Report 2 — Assumptions and Constraints](../documents/docs/report-2-project-management-plan/sections/02-i-project-overview/02-assumptions-constraints.md)
* [Report 2 — Evidences and Decision Traceability](../documents/docs/report-2-project-management-plan/sections/02-i-project-overview/07-evidences.md)

### Requirements baseline

* [Report 3 — Context Diagram](../documents/docs/report-3-software-requirement-specification/sections/03-i-overall-requirements/01-context-business-flow.md)
* [Report 3 — Actors](../documents/docs/report-3-software-requirement-specification/sections/03-i-overall-requirements/04-user-requirements/01-actors.md)
* [Report 3 — Permission Matrix](../documents/docs/report-3-software-requirement-specification/sections/03-i-overall-requirements/04-user-requirements/04-permission-matrix.md)
* [Report 3 — Business Rules](../documents/docs/report-3-software-requirement-specification/sections/07-v-requirement-appendix/01-business-rules.md)
* [Report 3 — NFR Overview](../documents/docs/report-3-software-requirement-specification/sections/06-iv-non-functional-requirements/00-overview.md)
* [Report 3 — External Interfaces](../documents/docs/report-3-software-requirement-specification/sections/06-iv-non-functional-requirements/01-external-interfaces.md)

### Architecture and identity baseline

* [Project Constitution](.specify/memory/constitution.md)
* [Member Authentication Feature Spec](features/001-member-authentication/spec.md)
* [Database working document](DB.md)
* [Firebase Authentication](https://firebase.google.com/docs/auth/)
* [Firebase Admin token verification](https://firebase.google.com/docs/auth/admin/verify-id-tokens)
* [Firebase Hosting use cases](https://firebase.google.com/docs/hosting/use-cases)
* [Firebase Hosting preview channels](https://firebase.google.com/docs/hosting/test-preview-deploy)
