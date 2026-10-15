# Mland V1 API Contract Catalog

## 1. Purpose and scope

This document is the cross-system API catalog for the Personalized Product
Sales and Workshop Booking System (PPSWBS). It gives frontend, backend, and
integration work a shared index of V1 boundaries, ownership, access rules, and
contract maturity.

It is **not** a substitute for a feature-level OpenAPI contract. A detailed
feature contract is authoritative for its request/response schemas, validation,
and status codes. The existing Member Authentication contract is at
[`features/001-member-authentication/contracts/member-auth.openapi.yaml`](./features/001-member-authentication/contracts/member-auth.openapi.yaml).

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
  verifies the token; Mland MySQL is authoritative for `MEMBER`, `STAFF`,
  `OWNER`, and `ADMIN_TECHNICAL` roles, account status, and entitlement.
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

### 3.1 Member and policy (`members`)

| Boundary | Method and route | Access | UC | Purpose | Maturity |
| --- | --- | --- | --- | --- | --- |
| Protected client REST | `PUT /api/v1/members/me` | Verified Firebase identity | UC-02 | Provision or return the Member for the verified Firebase UID. | Confirmed |
| Protected client REST | `GET /api/v1/members/me/entitlement` | Verified Firebase identity | UC-02 | Return current Member entitlement. | Confirmed |
| Public client REST | `GET /api/v1/policies/current` | Public | UC-02 | Return current Terms and Privacy metadata/content locations. | Confirmed |
| Protected client REST | `POST /api/v1/members/me/policy-acceptances` | Verified Firebase identity | UC-02 | Record acceptance of exactly the effective policy pair. | Confirmed |
| Protected client REST | `GET /api/v1/members/me/guest-booking-import-preview` | Eligible Member | UC-02, UC-05 | Preview verified-email Guest bookings that can be explicitly linked. | Confirmed |
| Protected client REST | `POST /api/v1/members/me/guest-booking-imports` | Eligible Member | UC-02, UC-05 | Idempotently link currently eligible Guest bookings after explicit confirmation. | Confirmed |
| Public client REST | `POST /api/v1/workshop-bookings/{bookingCode}/email-confirmations` | One-time confirmation token | UC-01 | Consume a Guest booking email-confirmation token. | Confirmed |
| Protected client REST | `GET /api/v1/members/me/workshop-bookings` | Active Member | UC-01, UC-05 | List the caller's permitted workshop history and upcoming bookings. | Draft |
| Protected client REST | `GET /api/v1/members/me/ready-ring-orders` | Active Member | UC-04 | List the caller's permitted retail orders and transactions. | Draft |

The seven Confirmed routes above, including their documented outcomes and three
documented request bodies, are defined by the Member Auth OpenAPI file.

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
| Public client REST | `GET /api/v1/ring-models` | Public | UC-03 | List Owner-confirmed ring-model design paths. | Draft |
| Public client REST | `GET /api/v1/ring-components` | Public | UC-03 | List published components usable by the configurator. | Draft |
| Public client REST | `POST /api/v1/design-requests` | Guest or Member with required consent | UC-03 | Submit a reference-image design request or a validated configured design choice. | Draft |
| Public or protected client REST | `GET /api/v1/design-requests/{designRequestId}` | Requester-authorised access | UC-03 | Read the requester-visible feasibility result and next action. | Draft |
| Protected client REST | `POST /api/v1/design-review-requests/{reviewRequestId}/decisions` | Staff or Owner, according to review tier | UC-03, UC-08 | Record a human feasibility decision or permitted override with actor, reason, and context. | Draft |

AI output is candidate feature data only. AI scoring, threshold values, image
retention, upload transport, rate limits, and final pricing are not confirmed
by this catalog.

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
| `billing` | Protected client REST | `POST /api/v1/invoices/{invoiceId}/adjustments` | Staff; Owner approval when required | UC-06 | Record an auditable adjustment with required reason, actor, time, and customer consent. | Draft |
| `billing` | Protected client REST | `POST /api/v1/invoices/{invoiceId}/settlements` | Staff | UC-06 | Record in-shop cash or bank-transfer settlement and resulting fulfilment state. | Draft |
| `fulfilment` | Protected client REST | `POST /api/v1/continuation-bookings` | Staff | UC-07 | Create a capacity-checked, linked continuation booking; customers cannot create it themselves. | Draft |
| `fulfilment` | Protected client REST | `POST /api/v1/custody-records` | Staff | UC-07 | Record custody intake for a work-in-progress item. | Draft |
| `fulfilment` | Protected client REST | `POST /api/v1/custody-records/{custodyRecordId}/releases` | Staff | UC-07 | Record auditable custody release or customer handover. | Draft |
| `fulfilment` | Protected client REST | `POST /api/v1/ready-ring-orders/{orderId}/carrier-handoffs` | Staff | UC-04 | Record carrier, handoff reference, actor, and time after an eligible order. | Draft |

### 3.6 Owner catalogue, rules, and audit review

| Owner | Boundary | Route family | Access | UC | Purpose | Maturity |
| --- | --- | --- | --- | --- | --- | --- |
| `catalogue` | Protected client REST | `GET/POST/PATCH /api/v1/workshop-packages`, `/workshop-locations`, `/workshop-sessions`, `/ready-rings`, `/ring-models`, `/ring-components` | Owner | UC-08 | Maintain owner-confirmed publishable catalogue data and operational availability inputs. | Draft |
| `designreview` | Protected client REST | `GET/POST/PATCH /api/v1/feasibility-rules` | Owner | UC-08 | Maintain approved feasibility constraints and rules. | Draft |
| Domain owner | Protected client REST | `GET /api/v1/{domain-resources}/{id}/audit-events` | Owner where the domain permits review | UC-06, UC-08 | Read audit evidence held by the owning domain. | Draft |

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

## 4. External integration contracts

These rows are deliberately separate from client REST. They require their own
provider-specific contract, security review, and feature approval.

| Owner | Boundary | Contract / route | Counterparty | Trace | Required invariant | Maturity |
| --- | --- | --- | --- | --- | --- | --- |
| `payments` | Inbound integration | `POST /api/v1/integrations/payments/{provider}/events` | Payment Gateway | UC-01, UC-04 | Verify provider authenticity and event validity; process duplicates idempotently; only a valid event confirms booking/order payment. | TBD dependency |
| `designreview` | Outbound integration port | `ImageFeatureAnalysisPort.analyse(...)` | AI Vision API | UC-03 | Send a reference image only after explicit consent and accepted validation; treat output only as candidate features. | TBD dependency |
| `workshopbooking`, `readyringsales` | Outbound integration port | `NotificationDeliveryPort.send(...)` | Email Sender | UC-01, UC-04 | Send requested booking/order notifications without leaking tokens or unnecessary PII. | TBD dependency |
| Review-import owner (to be assigned) | Outbound integration port | `GoogleReviewImportPort.importPermittedReviews(...)` | Google Business Profile | Supporting capability; Google review-import detail diagram | One-way, Owner-requested manual import only after verified profile, authorised API access, and approved retention/attribution/appeal policy. | TBD dependency |

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
