# Mland V1 API Contract Catalog

**Date created**: 2026-09-30
**Date updated**: 2026-10-25
**Author**: Ngô Tiến Đạt
**Last updated by**: Phan Sơn

**Version**: 0.4

## 1. Purpose and scope

This document is the cross-system API catalog for the Personalized Product
Sales and Workshop Booking System (PPSWBS). It gives frontend, backend, and
integration work a shared index of V1 boundaries, ownership, access rules, and
contract maturity.

It is **not** a substitute for a feature-level OpenAPI contract. A detailed
feature contract is authoritative for its request/response schemas, validation,
and status codes. The FE-001 Account Management contract is at
[`features/001-account-management/contracts/account-management.openapi.yaml`](./features/001-account-management/contracts/account-management.openapi.yaml).

### Contract maturity

| Label | Meaning |
| --- | --- |
| **Confirmed** | The route and behaviour are described by an existing approved source or detailed feature contract. The referenced Member Auth OpenAPI remains versioned `1.0.0-draft`; this label does not promote it to a released public API. |
| **Draft** | A proposed REST contract derived from an approved V1 use case. It must receive its own feature specification and OpenAPI contract before implementation. |
| **TBD dependency** | A system boundary is required, but its provider, payload, security, retry, governance, or other material detail is not approved. It must not be implemented as though it were final. |

### Boundary classes

| Boundary | Meaning |
| --- | --- |
| **Public client REST** | Browser/client API usable without a Mland Member entitlement. Public does not mean it may disclose protected data. |
| **Protected client REST** | Browser/client API requiring a verified Firebase ID token and the stated Mland role/status. |
| **Inbound integration** | A provider calls Mland, such as a payment confirmation webhook. It uses provider authentication, not a Firebase bearer token. |
| **Outbound integration port** | A Mland module invokes an external provider through an internal adapter/port. It is not a browser REST endpoint. |
| **Internal facade/event** | In-process Spring Modulith boundary. It is not exposed over HTTP and must not use another module's controller, DTO, entity, repository, or adapter. |

## 2. Minimum client-contract profile

- Client REST routes use the `/api/v1` prefix, plural kebab-case resources, and
  `application/json`. Server-rendered pages and Thymeleaf/htmx fragments are
  outside this REST catalog.
- `Authorization: Bearer <Firebase ID token>` proves identity only. Spring
  verifies the token; Mland MySQL is authoritative for account status and logical
  roles. V1 uses a closed four-code vocabulary: `MEMBER`, `STAFF`, `MANAGER`, and
  `ADMIN`. The codes `OWNER` and `ADMIN_TECHNICAL` are retired and must not be
  introduced or inferred from client input.
- Protected requests with a missing, malformed, expired, revoked, wrong-project,
  or unverifiable token receive `401`. A valid identity without the required
  Mland authority or current entitlement receives `403`.
- Controllers use Bean Validation and typed exceptions. Error contracts expose a
  stable safe code, bilingual message key, correlation ID, and applicable field
  errors; they never expose stack traces, tokens, secrets, or unnecessary PII.
- Request/response envelopes, pagination, idempotency headers, upload transport,
  cache directives, and exact resource schemas are **not** standardised here.
  Each feature-level OpenAPI contract must define them where needed.
- A Firebase provider claim, email, display name, or client-supplied role never
  grants an internal Mland role. Different Firebase UIDs are never merged by
  matching email.

## 3. Client REST catalog

All entries below are client-facing HTTP contracts. A route labelled Draft is a
design target, not an implementation instruction or final wire contract.

### 3.1 Account management (`accountmanagement`)

> The legacy `/api/v1/members/...` account-profile routes from the superseded Member Profile
> feature are retired. The canonical contract for account management is `/api/v1/accounts`.
> The FE-001 OpenAPI is authoritative for exact schemas, field types, enum values, and error payloads.

| Boundary | Method and route | Access | Purpose | Maturity |
| --- | --- | --- | --- | --- |
| Protected client REST | `PUT /api/v1/accounts/me` | Verified Firebase identity | Bootstrap or return the Account for the verified Firebase UID (idempotent upsert). | Draft |
| Protected client REST | `GET /api/v1/accounts/me` | Verified Firebase identity | Return the caller's own account summary (role, status, location assignments). | Draft |
| Protected client REST | `PUT /api/v1/accounts/me/profile` | Active `MEMBER` or `PENDING_SETUP` internal account | Update own profile fields (full name, phone); last-successful-commit wins. | Draft |
| Protected client REST | `POST /api/v1/accounts/me/email-sync` | Verified-email Firebase identity | Synchronize email from the verified Firebase ID token into the Account record; never accepts browser-supplied email. | Draft |
| Protected client REST | `DELETE /api/v1/accounts/me/session` | Verified Firebase identity | Sign out: revoke Firebase refresh tokens and clear session state. | Draft |
| Protected client REST | `GET /api/v1/admin/accounts` | `ADMIN` | List accounts; search by email/full name; filter by role/status; paginated; newest changed first. Does not expose Firebase UID, tokens, passwords, or unrelated booking/order data. | Draft |
| Protected client REST | `GET /api/v1/admin/accounts/{id}` | `ADMIN` | Read permitted account detail for a single account. | Draft |
| Protected client REST | `POST /api/v1/admin/accounts/internal` | `ADMIN` | Create a `STAFF` or `MANAGER` internal account and queue an invitation. `Idempotency-Key` header required; identical retries return the original provisioning result; a reused key with a changed payload returns `409`. | Draft |
| Protected client REST | `POST /api/v1/admin/accounts/{id}/invitation/resend` | `ADMIN` | Resend the setup invitation for a `PENDING_SETUP` internal account; regenerates the Firebase action link (never stores the link in audit/log). | Draft |
| Protected client REST | `PUT /api/v1/admin/accounts/{id}/profile` | `ADMIN` | Edit an internal account's profile; requires `version` for optimistic locking; stale version returns `409`. | Draft |
| Protected client REST | `PUT /api/v1/admin/accounts/{id}/role-location` | `ADMIN` | Change role and/or location assignment for a `STAFF` or `MANAGER` account; requires `version`; stale version returns `409`. Location must be active via workshop-location facade. | Draft |
| Protected client REST | `POST /api/v1/admin/accounts/{id}/suspend` | `ADMIN` | Lock an account to `SUSPENDED`; queues Firebase session revocation. Unlock is unavailable until `session_revocation_confirmed_at` is set. | Draft |
| Protected client REST | `POST /api/v1/admin/accounts/{id}/restore` | `ADMIN` | Restore a `SUSPENDED` account to its pre-suspension state; only available after Firebase session revocation is confirmed. | Draft |
| Protected client REST | `GET /api/v1/admin/roles` | `ADMIN` | Return the fixed role catalog (`MEMBER`, `STAFF`, `MANAGER`, `ADMIN`); no dynamic RBAC. | Draft |

All Admin account-management writes are audited (actor, target, action, outcome,
timestamp, correlation ID, before/after role/status/location). Firebase token
revocation for suspension is queued post-commit; FE-07 notification delivery is
post-commit and does not block account mutations.

### 3.2 Public catalogue, availability, and booking

| Owner | Boundary | Method and route | Access | UC | Purpose | Maturity |
| --- | --- | --- | --- | --- | --- | --- |
| `catalogue` | Public client REST | `GET /api/v1/workshop-locations` | Public | UC-01 | Discover bookable locations. | Draft |
| `catalogue` | Public client REST | `GET /api/v1/workshop-packages` | Public | UC-01 | Discover workshop packages and published prices. | Draft |
| `workshopbooking` | Public client REST | `GET /api/v1/workshop-sessions` | Public | UC-01 | Discover configured daily sessions and availability for selected location/date criteria. | Draft |
| `workshopbooking` | Public client REST | `POST /api/v1/workshop-bookings` | Guest or Member | UC-01 | Submit a booking after capacity and selected design-path checks. | Draft |
| `workshopbooking` | Public client REST | `POST /api/v1/booking-lookups` | Guest or Member with booking-contact proof | UC-05 | Return the minimum permitted booking status, invoice/deposit state, and QR only when lookup proof matches. | Draft |

`POST /booking-lookups` deliberately keeps booking contact proof out of a URL.
Capacity values, expiry behaviour, booking schema, and payment-provider handoff
remain feature-contract decisions.

### 3.3 Ring design and feasibility review (`designreview`)

| Boundary | Method and route | Access | UC | Purpose | Maturity |
| --- | --- | --- | --- | --- | --- |
| Public client REST | `GET /api/v1/ring-models` | Public | UC-03 | List published ring-model design paths approved through the catalogue authority. | Draft |
| Public client REST | `GET /api/v1/ring-components` | Public | UC-03 | List published components usable by the configurator. | Draft |
| Protected client REST | `POST /api/v1/design-requests` | Eligible Member with recorded consent | UC-03 | Submit a booking-design image request after package, location, session, ownership, capability and consent checks. Configured design choices use the owning booking/design contract and are not silently treated as image requests. | Draft |
| Protected client REST | `GET /api/v1/design-requests/{designRequestId}` | Owning Member; authorized Staff/Manager review scope | UC-03 | Read the permitted analysis state, review result, and next action without exposing raw provider payloads or another Member's data. | Draft |
| Protected client REST | `GET /api/v1/design-review-requests` | Staff or Manager within review scope | UC-03, UC-08 | List booking-design requests assigned to the reviewer's authority and branch/delegation scope. | Draft |
| Protected client REST | `POST /api/v1/design-review-requests/{reviewRequestId}/decisions` | Staff or Manager, according to value/authority tier | UC-03, UC-08 | Record Accepted, Rejected, or Clarification Required with actor, reason, current design version and decision context. AI cannot create this decision. | Draft |
| Public or protected client REST | `POST /api/v1/ai-support/messages` | Guest or Member; public context only | UC-26 | Submit an in-scope public-information/FAQ prompt and receive a validated basic-support response. No transaction or business decision is created. | Draft |
| Protected client REST | `POST /api/v1/price-proposals/{proposalId}/ai-suggestions` | Manager | UC-28 | Request an advisory package/product price suggestion from complete configured inputs; the suggestion never publishes or changes the final price. | Draft |

AI output is candidate feature data or advisory pricing information only. Every
valid booking-design request requires human review; no AI auto-accept or
auto-reject is permitted. Consent, temporary image retention/deletion, upload
transport, rate limits, provider schema, and final pricing remain feature
contract concerns and must not be inferred from a browser response.

#### Feature-04 endpoint detail

The following is the API catalog-level contract for Feature-04. The feature
OpenAPI contract remains authoritative for exact schemas, field types, enum values,
headers and error payloads.

| Method and route | Input | Successful output | Allowed actors and conditions | Important errors / side effects |
| --- | --- | --- | --- | --- |
| `POST /api/v1/ai-support/messages` | JSON `{ message }`; normalized text up to 2,000 characters. | Validated text response with safe scope metadata and correlation data where permitted. | Guest or Member; only approved public catalogue, workshop, store and FAQ context. Provider timeout is 30 seconds. | `400/422` for invalid or unsupported input, prompt injection or unsafe output; `429` for AI rate limit; `503` for provider failure. No booking, payment, review or price mutation. |
| `POST /api/v1/design-requests` | Multipart image plus `bookingCode`, `consentVersion`, `designVersion`, and `Idempotency-Key`. Image is limited to 10 MB and 4096×4096 pixels. | `202` versioned design-request projection with analysis state and next action. | Authenticated active Member owning the eligible booking context. Consent must exist before storage/provider call; maximum three image attempts per rolling five minutes. | `401/403/409/422/429` for identity, scope, stale version, consent, image, security or rate-limit failures. Invalid requests do not call Gemini; accepted image input is temporary and deleted after processing. |
| `GET /api/v1/design-requests/{designRequestId}` | Path identifier; no request body. | Member-safe or reviewer-scoped analysis/review projection. | Owning Member, in-scope Staff/Manager, or approved Admin technical/audit access. | `401/403/404`; never returns raw image, raw provider payload, credentials or another Member's data. |
| `GET /api/v1/design-review-requests` | Optional scope/filter and pagination query parameters defined by the feature contract. | Reviewer-scoped queue of requests requiring human action. | Staff or Manager with active status, branch/delegation scope and applicable authority. | `401/403`; read-only and must not broaden reviewer scope through client parameters. |
| `POST /api/v1/design-review-requests/{reviewRequestId}/decisions` | JSON `{ decision, designVersion, reason }`; decision is `ACCEPTED`, `REJECTED` or `CLARIFICATION_REQUIRED`. | Updated review projection with reviewer, decision time, reason and next action. | In-scope Staff/Manager; Staff only at or below 3,000,000 VND, Manager above threshold or where Manager authority is required. | `401/403/409/422`; stale/concurrent decisions return conflict. Reviewer cannot review own/conflicted request. AI cannot create the decision. |
| `POST /api/v1/price-proposals/{proposalId}/ai-suggestions` | Proposal identifier and validated package/product pricing context; no custom-manufacturing order, Member quotation, invoice or payment amount. | Advisory price-suggestion projection with warning/advisory flag and provider correlation. | Authorized Manager only. | `401/403/422/503` for authorization, incomplete/invalid input or provider failure. Manager may use manual fallback; AI cannot apply or publish a price. |

For all protected routes, Firebase identity is verified first and Mland role,
status, ownership and authority are checked server-side. Client-supplied role,
reviewer, estimate, price, status or decision is not trusted. Validation or
security failure prevents the provider call and any business-state mutation.

### 3.4 Ready-ring retail, invoices, and payments

| Owner | Boundary | Method and route | Access | UC | Purpose | Maturity |
| --- | --- | --- | --- | --- | --- | --- |
| `catalogue` | Public client REST | `GET /api/v1/ready-rings` | Public | UC-04 | Browse published ready-ring products. | Draft |
| `catalogue` | Public client REST | `GET /api/v1/ready-rings/{readyRingId}` | Public | UC-04 | Read a selected ready-ring's permitted published detail. | Draft |
| `readyringsales` | Protected client REST | `POST /api/v1/ready-ring-orders` | Active Member | UC-04 | Create a Member retail order pending verified full payment. | Draft |
| `readyringsales` | Protected client REST | `PUT /api/v1/ready-ring-orders/{orderId}/fulfilment-choice` | Order-owning Member after eligible payment state | UC-04 | Choose pickup or third-party carrier handoff. | Draft |
| `billing` | Protected client REST | `GET /api/v1/invoices/{invoiceId}` | Authorised customer or Staff | UC-01, UC-06 | Read permitted invoice, deposit, adjustment, and settlement facts. | Draft |
| `payments` | Protected client REST | `POST /api/v1/payment-attempts` | Authorised booking/order customer | UC-01, UC-04 | Start a deposit or retail payment attempt; it does not confirm payment. | Draft |
| `payments` | Protected client REST | `GET /api/v1/payment-attempts/{paymentAttemptId}` | Authorised customer or Staff | UC-01, UC-04 | Read the customer-safe status of a payment attempt. | Draft |

The browser redirect or client response from a payment provider never confirms a
booking or retail order. A verified inbound provider event is required. Stock
reservation, expiration, reconciliation, refund, and gateway payloads are
unresolved dependencies.

### 3.5 Staff operations and fulfilment

| Owner | Boundary | Method and route | Access | UC | Purpose | Maturity |
| --- | --- | --- | --- | --- | --- | --- |
| `operations` | Protected client REST | `POST /api/v1/workshop-bookings/{bookingCode}/check-ins` | Staff | UC-06 | Check in a confirmed customer and actual participating people. | Draft |
| `billing` | Protected client REST | `POST /api/v1/invoices/{invoiceId}/adjustments` | Staff; Manager approval when required | UC-06 | Record an auditable adjustment with required reason, actor, time, and customer consent. | Draft |
| `billing` | Protected client REST | `POST /api/v1/invoices/{invoiceId}/settlements` | Staff | UC-06 | Record in-shop cash or bank-transfer settlement and resulting fulfilment state. | Draft |
| `fulfilment` | Protected client REST | `POST /api/v1/continuation-bookings` | Staff | UC-07 | Create a capacity-checked, linked continuation booking; customers cannot create it themselves. | Draft |
| `fulfilment` | Protected client REST | `POST /api/v1/custody-records` | Staff | UC-07 | Record custody intake for a work-in-progress item. | Draft |
| `fulfilment` | Protected client REST | `POST /api/v1/custody-records/{custodyRecordId}/releases` | Staff | UC-07 | Record auditable custody release or customer handover. | Draft |
| `fulfilment` | Protected client REST | `POST /api/v1/ready-ring-orders/{orderId}/carrier-handoffs` | Staff | UC-04 | Record carrier, handoff reference, actor, and time after an eligible order. | Draft |

### 3.6 Business catalogue, rules, and audit review

| Owner | Boundary | Route family | Access | UC | Purpose | Maturity |
| --- | --- | --- | --- | --- | --- | --- |
| `catalogue` | Protected client REST | `GET/POST/PATCH /api/v1/workshop-packages`, `/workshop-locations`, `/workshop-sessions`, `/ready-rings`, `/ring-models`, `/ring-components` | Staff/Manager according to resource authority | UC-08 | Maintain publishable catalogue data and operational availability inputs; Manager owns business pricing decisions. | Draft |
| `designreview` | Protected client REST | `GET/POST/PATCH /api/v1/feasibility-rules` | Manager for business rules; Admin for approved technical configuration only | UC-08 | Maintain approved feasibility constraints without allowing technical administration to make business decisions. | Draft |
| Domain owner | Protected client REST | `GET /api/v1/{domain-resources}/{id}/audit-events` | Manager for business evidence; Admin for approved technical evidence | UC-06, UC-08 | Read audit evidence held by the owning domain within the caller's authority. | Draft |

There is no central audit event store or aggregate Admin Technical audit screen in
V1. The final audit-resource paths, write rules, retention, and visibility must
be owned by each feature contract.

### 3.7 Technical administration (`technicaladmin`)

| Boundary | Method and route | Access | UC | Purpose | Maturity |
| --- | --- | --- | --- | --- | --- |
| Protected client REST | `GET /api/v1/technical-configurations` | Admin Technical | UC-09 | List permitted non-secret technical configuration metadata. | Draft |
| Protected client REST | `PATCH /api/v1/technical-configurations/{configurationId}` | Admin Technical | UC-09 | Apply an approved, permitted technical configuration change and audit it. | TBD dependency |
| Protected client REST | `GET /api/v1/technical-integrations/{integrationKey}/health` | Admin Technical | UC-09 | Inspect approved integration health and safe diagnostic state. | TBD dependency |
| Protected client REST | `GET /api/v1/technical-audit-events` | Admin Technical | UC-09 | Read technical-administration audit evidence only. | TBD dependency |

Admin Technical cannot mutate invoices, payments, orders, bookings, custody, or
other business records. Secrets are never returned through these APIs. The
configuration model, integration-monitoring data, and technical-auth mechanism
remain unapproved.

### 3.8 Executive reporting (`reporting`)

| Owner | Boundary | Method and route | Access | UC | Purpose | Maturity |
| --- | --- | --- | --- | --- | --- | --- |
| `reporting` | Protected client REST | `GET /api/v1/executive-reports` | Active Manager account with `REPORTING_EXECUTIVE_VIEW` and at least one active MySQL `staff_branch_assignment`; requested branch UUIDs must be within that assignment scope | UC-10 | Return read-only, aggregated executive KPIs for an authorized reporting period and branch scope. This is not the FE-09 Admin parameter-impact view and never grants Admin access to executive revenue, ranking, customer, order, or payment data. | Draft — feature OpenAPI is defined; endpoint is not implemented. |

The authoritative schema, period validation, source-quality semantics, and error
contract are in
[`features/010-manager-dashboard/contracts/manager-dashboard.openapi.yaml`](./features/010-manager-dashboard/contracts/manager-dashboard.openapi.yaml).
The capability and branch-assignment checks are deny-by-default; a Firebase
identity or Admin technical-configuration permission alone is insufficient.

## 4. External integration contracts

These rows are deliberately separate from client REST. They require their own
provider-specific contract, security review, and feature approval.

| Owner | Boundary | Contract / route | Counterparty | Trace | Required invariant | Maturity |
| --- | --- | --- | --- | --- | --- | --- |
| `payments` | Inbound integration | `POST /api/v1/integrations/payments/{provider}/events` | Payment Gateway | UC-01, UC-04 | Verify provider authenticity and event validity; process duplicates idempotently; only a valid event confirms booking/order payment. | TBD dependency |
| `designreview` | Outbound integration port | `ImageFeatureAnalysisPort.analyse(...)` | AI Vision API | UC-03 | Send a reference image only after explicit consent and accepted validation; treat output only as candidate features. | TBD dependency |
| `workshopbooking`, `readyringsales` | Outbound integration port | `NotificationDeliveryPort.send(...)` | Email Sender | UC-01, UC-04 | Send requested booking/order notifications without leaking tokens or unnecessary PII. | TBD dependency |
| Review-import owner (to be assigned) | Outbound integration port | `GoogleReviewImportPort.importPermittedReviews(...)` | Google Business Profile | Supporting capability; Google review-import detail diagram | One-way, authorized business-requested manual import only after verified profile, authorised API access, and approved retention/attribution/appeal policy. | TBD dependency |

Provider selection, signing/authentication scheme, payload schema, retry and
reconciliation policy, dead-letter handling, rate limits, operational ownership,
and data-retention rules are not implied by the route or port names above.

## 5. Internal Spring Modulith contracts

These are ownership and dependency boundaries, not public APIs. Each business
module exposes only its `facade/` named interface; web, application, domain, and
infrastructure internals remain private to the module.

| Source | Contract type | Target | Trace | Responsibility | Maturity |
| --- | --- | --- | --- | --- | --- |
| `members.facade` | Synchronous facade | Other business modules | UC-01, UC-02, UC-04 | Supply permitted Member entitlement or summary data without exposing Member internals. | Confirmed architecture boundary |
| `workshopbooking.facade` | Synchronous facade | `members` | UC-02, UC-05 | Provide Guest-booking eligibility/linking operations; `members` must not access booking persistence directly. | Confirmed feature boundary |
| `billing` | Post-commit domain event | `payments` | UC-01, UC-04 | Publish a payment request without creating a billing/payments dependency cycle. | Confirmed architecture pattern |
| `payments` | Post-commit domain event | `billing` | UC-01, UC-04 | Publish verified payment confirmation; consumers must be idempotent. | Confirmed architecture pattern |
| `readyringsales` | Post-commit domain event | `fulfilment` | UC-04 | Start fulfilment without a sales-to-fulfilment-to-sales call cycle. | Confirmed architecture pattern |

Internal events carry only the minimum identifiers/version data, publish after
the producer transaction commits, and have idempotent consumers. Kafka or
microservices are not implied for V1.

## 6. Contract-delivery rule

Before implementing any Draft or TBD dependency row, its owning feature must
ratify a SpecKit feature specification and publish/update a feature-level
OpenAPI contract (or an integration contract for a non-HTTP boundary). That
contract must define exact schemas, validation, authorization, success/error
statuses, audit impact, and relevant idempotency/concurrency behaviour.
