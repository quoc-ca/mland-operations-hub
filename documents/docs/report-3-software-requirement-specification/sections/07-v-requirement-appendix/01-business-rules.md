<!-- # Architecture Convention

## Purpose

PPSWBS V1 is a Spring Modulith modular monolith. The convention keeps business ownership explicit while retaining one Spring Boot deployment and synchronous in-process calls where appropriate. It is an implementation convention, not a decision on production deployment, payment provider, retention, logging implementation, or other unresolved NFR controls.

## Module structure

Each business module owns this internal structure when its first approved feature is implemented:

```text
<module>/
  facade/          # explicit Java API exposed to other business modules
  web/             # REST/page controllers and HTTP DTOs
  application/     # use cases and transaction boundaries
  domain/          # JPA entities, value objects, business invariants
  infrastructure/  # Spring Data repositories, queries, external adapters
```

Only `facade/` is a Spring Modulith named interface. A module must not import another module's controllers, HTTP DTOs, entities, repositories, or infrastructure adapters. The technical `configuration` and `common` packages are not business modules. `common` may contain only typed errors, correlation IDs, clock/ID abstractions, and security context; it must not become a shared business-model package.

## V1 ownership map

| Module | Owns |
| --- | --- |
| `members` | External identity mapping, Member status/entitlement, consent, policy acceptance, Member audit and future profile. |
| `workshopbooking` | Capacity, booking, booking contact-email confirmation and expiry. |
| `catalogue` | Workshop packages, ready-ring product definitions, components and prices. |
| `designreview` | Design input, consented references, feasibility review and override. |
| `billing` | Invoices, adjustments, customer consent and settlement facts. |
| `payments` | Payment attempts, transactions and verified gateway callbacks. |
| `readyringsales` | Ready-ring availability, Member sales, reservation and delivery choice. |
| `fulfilment` | Continuation, custody, release and handoff evidence. |
| `operations` | Staff check-in and operational workflows. |
| `technicaladmin` | Technical configuration/monitoring, without business-record mutation. |

Audit evidence remains in the domain whose workflow produced it. V1 has no central audit event store or aggregate Admin Technical audit screen.

## Dependency and event rules

Facade calls are synchronous and one-way by default. A domain event is used only for a callback, an inherently asynchronous workflow, or removal of a module cycle. Internal events remain inside the Spring Boot application in V1; Kafka and microservices are not implied. Events carry minimal identifiers/version data, are published after the producing transaction commits, and consumers must be idempotent.

For example, billing publishes a payment request and payments publishes a verified payment confirmation. This prevents a `billing` to `payments` and `payments` to `billing` dependency cycle while keeping the gateway webhook boundary correct. Ready-ring sales similarly publishes a fulfilment-start event rather than creating a sales-to-fulfilment-to-sales call cycle.

## Enforcement

Every business module is a closed `@ApplicationModule` with declared allowed dependencies and a `facade/` `@NamedInterface`. `ApplicationModules.verify()` runs in automated tests and rejects dependency cycles, undeclared dependencies, and access to module internals. Runtime fail-fast module verification is not enabled in V1. -->

## 1. Business Rules

|GBR-ID|Rule Statement|Applies To|Rationale|Enforced By|
|---|---|---|---|---|
| GBR-01 | A workshop booking requires a selected package before its schedule/slot and design path are chosen. | UC-17 | The package price determines the deposit. | Workshop booking flow |
| GBR-02 | A workshop booking is confirmed only after a verified 50% VNPay deposit of the selected package price. | UC-18 | A reservation deposit secures the booking. | VNPay confirmation and workshop booking flow |
| GBR-03 | A VNPay Return URL never confirms payment. The application accepts only a valid signed IPN or the defined signed QueryDR recovery result while the hold remains active. | UC-18, UC-46, UC-61, UC-64 | Prevents client-side or duplicate payment confirmation. | VNPay adapter and payment workflow |
| GBR-04 | A VNPay link expires after 10 minutes. The linked workshop slot, retail cart, or custom-order queue slot is held for at most 15 minutes and is released when no valid confirmation exists. | UC-17, UC-18, UC-45, UC-46, UC-61 | Limits blocked inventory and capacity. | Hold-expiry workflow |
| GBR-05 | A Member booking-design image is analyzed only within the workshop booking journey. Staff reviews requests estimated at or below 3,000,000 VND; a Manager reviews requests above that value. | UC-30, UC-35, UC-36, UC-37 | Separates workshop design review from custom manufacturing. | Booking-design review workflow |
| GBR-06 | AI price suggestions may support a Manager's price decision for a new workshop package or retail product, but AI never publishes the price or decides a custom-manufacturing order. | UC-31 | Preserves accountable human pricing decisions. | Catalogue pricing workflow |
| GBR-07 | A custom-manufacturing order is Member-only and is created from a reference image or supported manual configuration, optional deadline, and fulfilment method. It is auto-accepted only when its deadline is valid and custom queue capacity remains. | UC-59, UC-60 | Custom manufacturing has a distinct, capacity-limited lifecycle. | Custom-order workflow |
| GBR-08 | A custom-order deposit equals 50% of the wax-package price. The final balance equals the remaining 50% plus actual surcharges recorded by Staff or Manager. | UC-61, UC-63, UC-64 | Defines the agreed custom-order payment split. | Custom-order and VNPay workflows |
| GBR-09 | A paid retail order uses store pickup or Staff-recorded manual GHTK handoff. Internal statuses include preparation/packing, ready for pickup, picked up, or handed to GHTK; courier tracking is not a system status. | UC-48 to UC-53, UC-65 | V1 ends shop responsibility at pickup or handoff. | Fulfilment workflow |
| GBR-10 | One voucher may be used per retail order. Voucher and loyalty-point discounts may be combined; points are a non-cash discount and limits are Admin-configured. | UC-40 to UC-46 | Defines the minimum V1 loyalty/promotion policy. | Checkout and operational-parameter configuration |
| GBR-11 | Klook obtains Mland availability/hold before it confirms a workshop booking. Mland remains the capacity source of truth; Klook cancellation events do not release Mland capacity automatically. | UC-27, UC-28 | Mland does not consume Klook cancellation events in V1. | Klook integration workflow |
| GBR-12 | Google Maps/Places data is read-only, displayed with required attribution and link, and is not persisted. A customer is redirected to Google Maps to submit a review. | UC-57, UC-58 | Complies with the agreed Google Maps boundary. | Public store information flow |
| GBR-13 | The application retains Gemini text prompts/responses for at most 30 days and no AI image input after processing. Custom reference images, delivery data, and payment/audit records follow their approved retention periods. | UC-30, UC-35, UC-59 and NFR | Limits personal-data retention to the approved purpose. | Data-retention controls |
