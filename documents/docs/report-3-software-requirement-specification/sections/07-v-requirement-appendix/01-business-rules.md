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
||||