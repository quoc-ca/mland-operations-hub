# General Spec

**Date created**: 2026-09-29
**Date updated**: 2026-10-31
**Author**: Phan Son
**Last updated by**: Phan Sơn

**Version**: 0.6
**Status**: Working Agreement

> This working agreement summarizes product context and shared design constraints. [Report 2 scope](../documents/docs/report-2-project-management-plan/sections/02-i-project-overview/01-scope-purpose.md), [constraints](../documents/docs/report-2-project-management-plan/sections/02-i-project-overview/02-assumptions-constraints.md), and [decision evidence](../documents/docs/report-2-project-management-plan/sections/02-i-project-overview/07-evidences.md) govern business scope; Report 3 governs detailed requirements. The [constitution](.specify/memory/constitution.md) governs architecture. This update records the group's confirmed technical decisions; it does not reconcile unrelated business-source conflicts or approve feature designs.

> Detailed feature artifacts, the design kit, and code have not been synchronized in this update. Reassess affected assumptions before implementation; this agreement alone does not establish readiness. The spec-folder [AGENT](AGENT.md) governs research and design; code-folder personas govern implementation.

## 1. Context & Goal

**Business problem:** Mland is a ring atelier that needs one bilingual operations platform for public discovery, workshop booking, ring-design consultation, Member retail, custom manufacturing, and shop-side fulfilment. The platform should keep customer intent, operational decisions, payments, and fulfilment evidence connected without becoming an ERP, warehouse-management, accounting, or end-to-end logistics system.

**System goal:** Support Guest and Member journeys while giving Staff, Managers, and Admins clear operational responsibilities. Workshop booking and workshop ring-design review are one journey; custom manufacturing is a distinct Member-only order flow. The [Report 3 context](../documents/docs/report-3-software-requirement-specification/sections/03-i-overall-requirements/01-context-business-flow.md) owns detailed business flows; this file summarizes shared context and design guardrails.

**V1 scope summary:** Account and fixed-role management, public product and workshop catalogues, workshop schedules/bookings, workshop ring design, independent AI support and human-reviewed AI suggestions, Member retail, loyalty/promotions, custom manufacturing, workflow email, operational administration/dashboard, read-only Google Maps information, and approved OTA workshop connectivity. Klook and GetYourGuide are OTA candidates; Mland may synchronize their schedules and availability in sales-closed preparation mode, but neither may accept bookings until its own feature gates pass, including proof that its voucher/ticket delivery supports one Mland-scannable ticket per participant. Staff operates product catalogue and manually maintained available-to-sell quantities. Managers operate workshop schedules/capacity and business approvals, promotions, and the executive dashboard. Admins manage accounts, fixed-role reference, operational parameters, and approved integration credentials. The fixed role vocabulary is defined in [Actors & Roles](#2-actors--roles).

**Technology context:** Separate React + Vite frontend with client-side rendering and Java 21/Spring Boot backend communicating through REST APIs; Spring Data JPA/JPA, Flyway, and MySQL. The backend does not render the application UI; SSR/Thymeleaf/htmx are no longer the UI baseline. HTML email templates are separate from UI rendering. Firebase Authentication remains the identity-provider baseline. Firebase Hosting is preview-only; production origins, hosting, database deployment, CDN behavior, and repository separation are not selected by this decision.

**Architecture convention:** The backend remains a Spring Modulith modular monolith. Business code follows `features/<feature>/{controller,dto,dao,entity,service,facade}`. Each feature must be detected and verified as a distinct module, rather than collapsing into a single `features` module. Java package names use valid identifiers, for example `accountmanagement` for the documentation slug `account-management`. Controllers handle transport/validation; services own use cases/transactions; DAOs use JPA; entities stay internal. Only named `facade/` interfaces and explicit boundary types are public between features. One-way facade calls are the default; events serve async/callback/cycle-removal needs with idempotent consumers. `common` remains a technical kernel without business entities, DTOs, or shared workflows; account-management owns identity mapping.

**State direction:** Hybrid stateless/stateful is a system direction, detailed per feature. A feature identifies stateless interactions and retained state with a rationale; it need not use both. Authentication/session state is distinct from business state. Shared mechanisms require consistent contracts, not independent conflicting choices by consumers. This decision does not mandate a global session policy or introduce Redis, an application JWT, or a refresh-token system.

## 2. Actors & Roles

[Report 3 actors](../documents/docs/report-3-software-requirement-specification/sections/03-i-overall-requirements/04-user-requirements/01-actors.md) and the [permission matrix](../documents/docs/report-3-software-requirement-specification/sections/03-i-overall-requirements/04-user-requirements/04-permission-matrix.md) own detailed responsibilities. Firebase proves identity; Mland/MySQL determines account state and authorization.

| Actor | Type | Responsibility and boundary |
| --- | --- | --- |
| Guest | Public customer | Browses published products, ring models, workshop packages/schedules, and public store information; may use basic AI support. Independent Design Request creation and image submission remain subject to the approved ownership policy. |
| Member | Authenticated customer | Uses own profile/history, workshop booking, independent Design Request, retail/loyalty, consultation, and custom-manufacturing journeys. May create a Design Request when the request satisfies its ownership, consent, and review policy. |
| Staff | Internal business actor | Maintains product catalogue and available-to-sell quantities; checks in participants; reviews independent Design Requests only within the approved operational scope; prepares paid retail orders; records pickup/GHTK handoff and custom-order final amounts as authorized. |
| Manager | Internal business actor | Manages workshop schedule, staff assignment, capacity, materials, closures, promotions/vouchers, and dashboard; reviews independent Design Requests when the approved policy requires Manager authority; decides published prices after considering AI suggestions; may configure custom-order queue and final amounts. |
| Admin | Internal technical/operational actor | Manages accounts, fixed-role reference, account status, operational parameters, and approved integration credentials. Does not replace Staff/Manager business approval unless an explicit feature authority allows it. |
| SSO Provider | External identity service | Firebase Authentication is the current identity baseline; configured sign-in methods establish identity and issue Firebase ID tokens. Provider identity/claims do not confer Mland roles. |
| Payment Gateway | External provider | VNPay, the sole V1 payment provider for workshop deposits, retail orders, and custom-order deposit/balance. |
| AI API | External provider | Paid Gemini API for basic text support, independent Design Request image analysis, and price suggestions for new workshop packages or retail products. Suggestions support human decisions only. |
| Mail Gateway | External provider | Mland-domain SMTP service for account-security and workflow messages such as QR tickets, payment requests/receipts, review results, and order-status messages. It is not a notification centre. |
| Cloud Storage Service | External provider | Stores catalogue media and eligible custom-manufacturing reference images subject to retention/deletion rules. |
| Google Maps API | External provider | Google Maps Embed and Places provide read-only map and permitted public review/rating information with attribution and a link to Google Maps. |
| Delivery Provider | External provider | Giao Hàng Tiết Kiệm (GHTK), used only for Staff-recorded manual handoff; no V1 carrier API integration. |
| External Registration System | External provider | Klook and GetYourGuide are OTA candidates. Mland remains source of truth for capacity; each provider requires separate onboarding and proof of ticket-per-participant delivery before 003.4 go-live. |

**Fixed account roles (approved, closed):** V1 uses exactly four role codes: `MEMBER`, `STAFF`, `MANAGER`, and `ADMIN`. The codes `OWNER` and `ADMIN_TECHNICAL` are retired and MUST NOT be reintroduced or inferred from client input or display labels. Firebase proves identity only; Mland MySQL decides the current role, account status, ownership, and Workshop Location scope. Account management does not provide dynamic RBAC or let provider claims grant a role.

**Canonical Workshop Location (approved, closed on 2026-10-27):** `Workshop Location` is the one shared site-and-scope concept for workshop operations, identified everywhere by `workshopLocationId`. A feature may display the word “branch”, but it MUST NOT introduce a second branch identity or a Branch-to-Workshop-Location mapping for the same scope. The workshop-location feature owns the lifecycle and active-state authority.

**MFA:** V1 does not require MFA, Firebase Identity Platform, or a forced fixed/eight-hour session re-login. Idle expiry is handled by Firebase token lifetime alone. Firebase reauthentication remains limited to Firebase-sensitive credential changes (e.g. email or password update via Firebase); protected Mland commands recheck current account authorization at execution time.

**Post-authentication routing:** After a successful sign-in, every role is routed to `/`. Feature dashboards are outside FE-001 scope. Unverified users see a non-blocking email-verification reminder; per-feature access controls enforce their own verified-email requirement.

## 3. Functional Requirements

The following system-level requirements summarize Report 2/3 and their [business rules](../documents/docs/report-3-software-requirement-specification/sections/07-v-requirement-appendix/01-business-rules.md). Feature specifications and API contracts own detailed behavior. A source conflict must be reported with evidence rather than silently settled by this summary.

### Identity and authorization

* The web client SHALL use the Firebase Web SDK for enabled Google, Facebook, and Firebase email/password sign-in methods. Provider availability remains subject to configuration/approval.
* Backend REST uses one shared identity gate. Only routes explicitly declared public by their owning contract may proceed unauthenticated; every other route is denied unless the gate verifies a Firebase `Authorization: Bearer` ID token, including revocation, before using the UID. Any additional credential transport/session flow requires explicit feature design and a consistent shared contract; the hybrid direction alone does not authorize it. Raw tokens SHALL NOT appear in URLs, logs, or committed files.
* MySQL SHALL remain authoritative for Mland account status, business role, entitlement, and audit. Provider, email, display-name, photo, or Firebase custom claims SHALL NOT grant business authority.
* The shared gate SHALL expose to consuming features only `CurrentAccountActor`: opaque account ID, current fixed role, lifecycle status, email-verification state, and active location IDs. Firebase UID and raw token stay inside the identity boundary. Each consuming feature SHALL derive and enforce its own business-action authority from this actor; Account Management SHALL NOT operate a cross-feature capability/RBAC store.
* An identity maps to exactly one Mland Account by Firebase UID. A verified first sign-in may bootstrap a Member Account only; a pending Staff/Manager invitation is activated only through its intended verified identity and valid active location assignment. Accounts with different UIDs SHALL NOT be merged automatically based on email.
* Account lifecycle states are `ACTIVE`, `PENDING_SETUP`, and `SUSPENDED`; Firebase email verification is a separate condition. A deactivated location does not change account role/status automatically, but commands scoped to that location are denied.
* Guest-accessible public journeys remain public; Member-only history, retail checkout, independent Design Request creation or image submission, and custom manufacturing require the corresponding Mland authorization or approved ownership proof.

### Catalogue and workshop booking

* Guests and Members SHALL be able to browse published products, workshop packages/schedules, available ring models, and permitted public store information.
* A workshop booking SHALL select a package before schedule/slot and supported ring-design path. A booking is confirmed only after valid confirmation of the 50% package-price deposit through VNPay.
* An eligible actor may submit an independent Design Request only after the applicable ownership, input, and purpose-specific consent checks pass. Image analysis is temporary and advisory. Human review, reviewer scope, terminal outcomes, withdrawal, revision, and approval expiry follow the approved Feature-004 policy; AI never accepts, rejects, or publishes a business decision. An authorized requester can view the recorded result for their own request.
* AI MAY suggest materials/components or prices for a new workshop package or retail product. A Manager decides and publishes the price; AI does not publish it or decide custom-manufacturing orders.
* OTA synchronization SHALL respect Mland-owned capacity and the approved availability/booking flow. Per-provider availability, ticket delivery, callback, cancellation/refund, commercial, and reconciliation behaviour is owned by feature 003.4; no channel is live until its gates pass.

### Retail, loyalty, and promotions

* Retail checkout is Member-only. Checkout revalidates the whole cart, snapshots applicable prices, and does not create a partial order when a cart line is invalid or unavailable.
* A retail order requires verified full payment before sale completion and held quantity deduction. One eligible voucher may be used; loyalty points are a non-cash discount and may be combined within configured limits.
* Staff records internal order milestones, customer pickup, or manual GHTK handoff. Courier transit/tracking states are outside the system.
* Managers operate promotions and vouchers; Admin-configured parameters govern loyalty rates, point value, and discount caps.

### Custom manufacturing and fulfilment

* Only a Member may submit a custom-manufacturing order using a reference image or supported manual configuration, an optional requested deadline, and a fulfilment method. This is separate from independent Design Request review.
* Manager/Admin configuration defines minimum deadline, unavailable deadline dates, and maximum queue size. The system assigns the earliest valid date when none is requested and auto-accepts only when deadline and queue-capacity conditions pass.
* An accepted custom request reserves a queue slot when deposit payment starts. Deposit is 50% of the wax-package price; when ready, Staff/Manager records the remaining 50% plus actual surcharges and the system requests the balance through email/VNPay.
* Fulfilment requires verified payment and is completed by Member pickup or Staff-recorded manual GHTK handoff. AI SHALL NOT price, accept, reject, or review custom orders.

### Administration and workflow communication

* Admin manages accounts, fixed-role reference, account lock status, operating parameters, and approved third-party credentials. Managers own the specified workshop and business-approval operations.
* The executive dashboard summarizes business revenue, workshop utilization, and branch KPIs for authorized roles.
* Workflow messages are system responses delivered through the Mland-domain Mail Gateway where applicable. V1 does not add a standalone notification centre.

### Public store information

* Google Maps Embed and Places SHALL be used read-only to display store location and permitted public review/rating information with required attribution and a Google Maps link.
* Mland SHALL NOT persist Places review/rating content, collect an internal store review, or submit/write reviews to Google.

## 4. Non-functional Requirements

Detailed requirements remain in the [Report 3 NFR baseline](../documents/docs/report-3-software-requirement-specification/sections/06-iv-non-functional-requirements/00-overview.md) and [external interfaces](../documents/docs/report-3-software-requirement-specification/sections/06-iv-non-functional-requirements/01-external-interfaces.md).

* **Feature state design:** For retained state, define owner, storage, lifecycle, expiry/invalidation, consistency/concurrency, user isolation, security, and recovery as applicable. Spec owns observable behavior; plan owns mechanisms. Shared state must have one consistent contract referenced by consumers.
* **Frontend/backend boundary:** Define UI loading/empty/validation/success/error and recovery states; enforce business access on the backend. V1 client requests use Firebase `Authorization: Bearer` identity tokens. CORS uses an explicit allowlist of selected frontend origins; it is never disabled globally. CSRF applies only to any later explicitly approved cookie-authenticated flow and is not a substitute for bearer-token verification; it is not disabled globally as a shortcut for bearer APIs.
* **Runtime configuration and secrets:** Database, identity-provider, and other runtime credentials are supplied only through the approved environment/secret channel. Committed configuration has no credential default or fallback. Missing required configuration fails safely rather than connecting to an unintended service. The database defaults found in the prior implementation were confirmed synthetic and not reused.
* **Migration coordination:** Flyway versions use one repository-wide, append-only sequence of increasing integers. Before creating a migration, its owner checks and reserves the next available version with the group. Only migrations not merged and not applied may be renumbered.
* **Payment integrity:** VNPay is the sole V1 provider. A browser Return URL is display-only. One technical `paymentintegration` ingress receives the signed VNPay `GET`, verifies checksum once, deduplicates delivery, and routes redacted evidence by immutable owner-qualified merchant reference to its owning feature; it never changes booking/order state itself. Payment state changes require a valid signed IPN or the defined signed QueryDR recovery result while the associated hold remains active; final-payment/refund lifecycle rules remain owned by the settlement feature. Validate signature/checksum, transaction reference, amount, currency, and success status; process duplicate events idempotently.
* **Identity and authorization security:** Verify Firebase token signature, issuer, audience, expiration, and revocation with Firebase Admin SDK. Authentication proves identity; Mland authorization decides business access on every protected operation. Do not log ID/provider tokens, secrets, or unnecessary PII. A route not explicitly public must fail closed when the shared gate cannot establish `CurrentAccountActor`.
* **Payment timing:** A workshop payment link and its capacity hold share one absolute deadline at most 10 minutes after hold creation. Retail-cart and custom-order queue holds remain at most 15 minutes; release each hold at its configured deadline, never later than its limit. Late payment evidence does not automatically fulfil and requires manual operational follow-up. The sole exception is a Manager-approved Workshop Final Bill link: it is valid for 24 hours, sends at most one reminder before expiry, never renews automatically, and does not release the completed item or erase the balance on expiry. Reissue requires an authorized, audited action.
* **AI data:** Gemini is a paid service. Application-level text prompts/responses are retained for no more than 30 days. Independent Design Request image input requires recorded, purpose-specific consent, is stored temporarily, and is deleted after processing; AI output supports human decisions only. Custom-manufacturing reference images follow their separate retention rule until pickup or GHTK handoff.
* **Personal-data retention:** Custom-manufacturing reference images are retained until pickup or GHTK handoff. Rejected/withdrawn custom requests and independently classified images are deleted immediately after processing. GHTK recipient/address data are deleted or irreversibly obscured 30 days after handoff. VNPay references and business audit evidence are retained for five years. No card data is stored.
* **Privacy notice and consent:** Mland's general Terms and Privacy Policy states image-processing purposes, approved retention periods, Google attribution, and delivery-recipient data handling. Independent Design Request image processing additionally requires an affirmative, versioned, purpose-specific consent recorded before temporary storage or AI analysis.
* **Integration boundaries:** Google Places content is not persisted; GHTK has no V1 API/quote/tracking/failure/return workflow; OTA callbacks are processed only by an approved 003.4 provider adapter and go-live gate; workflow SMTP only delivers requested messages.
* **Hosting and caching:** Firebase Hosting is preview-only. Cache behavior applies only to deliberately public content; never cache identity tokens, authenticated responses, booking capacity, payment status, invoices, Member history, or administration data.
* **Auditability:** Payment confirmation, price publication, independent Design Request review, custom final amount, pickup, and carrier handoff retain business audit evidence under the stated retention period. FE-001 account-management audit/security evidence (role changes, status changes, location assignment, lock/unlock) is retained for five years. Audit metadata includes actor account ID, target account ID, action, outcome, timestamp, and correlation ID; it records only before/after values for role, status, location, or lock reason. It never includes raw passwords, Firebase tokens, Firebase action links, raw images, or unnecessary PII (full name, phone, unrelated contact data).
* **Workshop authorization:** Account Management owns the canonical fixed role code, current Account status, and effective active Workshop Location scope. Effective scope is the intersection of active Account assignments and currently active authoritative Workshop Locations; it is not a cached or raw assignment list. Each Workshop feature owns its action-specific authorization rule and evaluates it server-side from those facts plus its own resource scope/assignment data (for example, workshop settlement uses Workshop-Location-scoped capabilities workshop.settlement.defer, workshop.settlement.reschedule, and workshop.settlement.refund; an inactive actor loses them immediately). It queries current facts immediately before a sensitive command and fails closed when those facts are unavailable; V1 does not use cross-module locking, so a command already checked before a concurrent Account change commits may complete. A feature MUST NOT infer authority from a display label, Firebase claim, or retired code, and MUST NOT infer Manager = OWNER.
* **Workshop Location deactivation:** Deactivating a Workshop Location does not automatically change any Account's role or status. However, commands scoped to a deactivated Workshop Location (e.g. role/location assignment, check-in) must be denied. The workshop-location feature is the canonical authority for active state; Account Management stores assignments by workshopLocationId and validates them through the workshop-location facade only.
* **Unbaselined quality targets:** Performance, availability/SLA, accessibility standard, supported browser/device baseline, production hosting/deployment, and production backend origin remain `TBD`. Do not infer numeric targets from the preview setup.

## 5. Data Model

Physical schemas remain owned by feature plans, migrations, and the evolving [DB working document](DB.md). This section defines only conceptual ownership and invariants.

| Data | Authority/owner | Constraint |
| --- | --- | --- |
| Firebase UID / `external_user_id` | Firebase identity mapped by Mland MySQL | Unique Account lookup identity; never use email as an automatic merge key. One Firebase UID maps to exactly one Mland Account. |
| Account status and role | Mland MySQL / `accountmanagement` module | Closed role vocabulary: `MEMBER`, `STAFF`, `MANAGER`, `ADMIN`. Account statuses: `ACTIVE`, `PENDING_SETUP`, `SUSPENDED`. Email verification is a separate Firebase-backed condition, not a fourth status. Provider claims do not grant roles. |
| Workshop Location scope | Workshop Location authority (owned by FE-03) | `workshopLocationId` is the sole shared identifier for workshop site/scope. Effective active scope is active Account assignment ∩ currently active authoritative Workshop Location. Staff has exactly one active Workshop Location assignment; Manager has one or more. Account Management stores assignments and validates them through the workshop-location facade only; it does not maintain a local copy or a separate branch mapping. |
| Canonical role/status/Workshop Location facts, shared actor context, and auth audit | Mland MySQL / `accountmanagement` module | Account Management owns the facts and exposes the shared actor context; each business feature owns its action-specific authorization policy. Provider, Firebase email, display name, photo, or Firebase custom claims do not grant business authority. Five-year retention for account security/audit evidence. |
| Profile/contact snapshot | Mland account/profile capability | Contact values may be absent or change; they are not authorization keys. |
| Workshop packages, schedules, capacity, bookings, and check-in evidence | Mland business domains | Mland owns workshop capacity, including when exchanging availability with an approved OTA. |
| Catalogue, component/material and published-price data | Mland catalogue domain | Staff maintains product catalogue; Managers decide published prices for newly priced packages/products. |
| Independent Design Request input, AI analysis, review outcome, and custom-order request | Separate Mland workflows | Feature-004 Design Request review is independent from workshop booking and distinct from custom manufacturing; AI does not decide custom orders. |
| Retail cart/order, invoices, loyalty/voucher application, payment references, and fulfilment milestones | Mland sales/payment/fulfilment domains | Full verified retail payment precedes sale completion; fulfilment evidence ends at pickup or carrier handoff. This does not create accounting or reconciliation scope. |
| Media and custom reference images | Approved cloud storage service under Mland retention policy | Apply the deletion/retention rules in [Non-functional Requirements](#4-non-functional-requirements). |
| Credentials, passwords, and Firebase token lifecycle | Firebase Authentication/provider | Never copy provider passwords, password hashes, or refresh lifecycle into Mland business storage; application state is not an unapproved credential store. |
| Application/session state, if used | Owning feature or explicitly shared mechanism | Define persistence, lifecycle, invalidation, isolation, and recovery; do not confuse credential lifecycle with business state or infer a new state store. |

## 6. Error Handling & Security

* Missing, malformed, expired, revoked, wrong-project, or unverifiable Firebase bearer tokens SHALL return `401 Unauthorized` without exposing token details. A verified identity whose Mland Account is `PENDING_SETUP` or `SUSPENDED`, lacks an active required location assignment, or fails the feature-owned action rule SHALL return `403 Forbidden` without protected data. Additional approved credential flows must define corresponding safe API errors and frontend recovery without granting fallback access.
* Concurrent provisioning of the same verified Firebase UID SHALL leave at most one Mland Account record through a unique identity constraint and transaction handling.
* Same-email/different-UID collisions SHALL not merge Account records, bookings, orders, or roles automatically. Provider-link and account-recovery failures must preserve existing account/history and return safe guidance.
* Every protected operation SHALL authorize using Mland role/status and resource ownership on the server. Do not trust client-supplied role/price/status or provider claims.
* Apply the payment-evidence, expiry, duplicate-callback, and redacted-audit requirements in [Non-functional Requirements](#4-non-functional-requirements); an invalid callback or expired hold cannot trigger fulfilment.
* Client sign-out SHALL use the Firebase SDK. Features with additional application/session state must define logout clearing/invalidation and replay/expiry behavior in their shared credential contract. Sign-out in the UI alone is not proof of backend invalidation. This agreement creates no separate Mland password store, application-signed JWT, or refresh-token system.

## 7. Acceptance Criteria

* [ ] The React + Vite frontend runs separately and uses REST APIs; the backend does not render the application UI or rely on Thymeleaf/htmx forms.
* [ ] The feature package structure preserves distinct Modulith modules and facade-only dependencies; applicable module verification is planned and later evidenced.
* [ ] The shared identity gate has an explicit public-route allowlist and deny-by-default fallback; a consuming feature receives only the documented `CurrentAccountActor` boundary type and independently checks its own business actions.
* [ ] Each affected feature documents applicable stateless/stateful behavior, lifecycle, isolation, recovery, security, UI states, and verification; shared auth/state contracts are consistent. Design completeness does not itself prove implementation.
* [ ] A Guest can browse public catalogue/workshop/store information and create a workshop booking only through the package-first flow; a booking is confirmed only after the verified 50% VNPay deposit.
* [ ] A verified Firebase UID provisions at most one Member; repeated sign-in retains business history, and provider/email claims cannot grant Mland roles or merge different UIDs.
* [ ] An authorized independent Design Request can be analyzed and reviewed by the policy-appropriate human role, and its owner can view the outcome; the request has no booking, payment, capacity, or session side effect, and the separate custom-manufacturing path is never decided by AI.
* [ ] Member retail checkout revalidates the whole cart and requires verified full payment before completing the sale; loyalty/voucher application follows configured limits.
* [ ] A Member custom order follows the configured deadline/queue, 50% wax-package deposit, recorded final balance, verified balance payment, and pickup/manual-handoff flow.
* [ ] Workshop VNPay payment links and capacity holds share one absolute deadline of at most 10 minutes; invalid/duplicate callbacks cannot confirm twice, and Return URL cannot confirm payment.
* [ ] Google Maps/Places display is read-only with attribution/link and no persisted Places content; Klook capacity exchange respects Mland as source of truth; GHTK handoff is manual and no courier tracking is claimed.
* [ ] Gemini, AI-image, custom-image, delivery-address, payment-reference, and audit data follow the specified retention periods; no card data is stored.
* [ ] Test environment, deployment, performance, availability, and accessibility decisions remain explicitly `TBD` until approved; implementation introduces no Mland password hash, application-signed JWT, committed provider secret, or raw token logging.

## 8. Use Case Relationships

```text
Guest / Member ──browse package, choose design path──► Workshop booking
Workshop booking ──50% deposit / verified VNPay──► Confirmed booking
Guest / Member ──independent Design Request──► Gemini analysis ──► Staff / Manager review ──► owner views outcome
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
* Selecting Firebase Test Lab as the application testing strategy merely because Firebase is used; frontend/backend verification is chosen by the affected design.
* Firebase Hosting production deployment, Hosting rewrites, Cloud Run/VPS selection, production database deployment, production custom domain, or a production Firebase project decision.

## 10. Notes / Open Questions

* **Decided on 2026-10-24:** separate React + Vite frontend, Java 21/Spring Boot backend, no application SSR, feature-oriented layers, retained Modulith, and hybrid state direction. These are not architecture TBDs; mechanism details belong to the owning feature/shared contract.
* **Shared identity baseline synchronized on 2026-10-26:** Firebase proves identity only; Account Management is authoritative for Mland account status, fixed role (`MEMBER`, `STAFF`, `MANAGER`, `ADMIN`), and Workshop Location scope. The backend uses one bearer/revocation identity gate and exposes only `CurrentAccountActor` to consuming features; each feature owns its action-capability mapping. The codes `OWNER` and `ADMIN_TECHNICAL` are retired and resolved; no further TBD mapping is open.
* **Canonical Workshop Location synchronized on 2026-10-27:** `workshopLocationId` is the single shared workshop site/scope identifier. “Branch” is a display label only, not a second entity, identifier, or mapping. This group decision was approved by Hồ Hoàng Quốc (PM); Phan Sơn updated this working agreement.
* **Deferred synchronization:** existing feature specs/plans/tasks/contracts, APIs.md, design kit, and code may retain old SSR, package, or persona assumptions. Review the affected material before implementation; do not silently rewrite or mark it ready in this update. The [account-management artifacts](features/001-account-management/) are a current design source, not proof of compliance with the amended baseline.
* **MFA is not required in V1:** Post-V1 MFA design (enforcement, recovery, auditing) is deferred and must not be inferred from this agreement.
* Which Firebase project hierarchy, billing owner, access roles, secret-management process, and provider approvals will be used for development/staging/production?
* Will Firebase Auth Emulator or a separate non-production Firebase project be used for authentication integration tests? The production project must not be used for tests.
* What is the final safe account-linking, recovery, and support path for provider collisions or same-email/different-UID cases?
* Which provider contracts, credentials, callback URLs, quotas, supplier approvals, and SMTP/cloud-storage configuration are required for go-live? Keep credentials out of this repository.
* What frontend/backend origins, production deployment model, performance/availability targets, accessibility standard, and browser/device baseline will be approved? CORS, credential transport, and CSRF design must match the selected origins and owning feature contracts.
* Which preview content may be published through Firebase Hosting, with what access boundary and expiry lifecycle?
