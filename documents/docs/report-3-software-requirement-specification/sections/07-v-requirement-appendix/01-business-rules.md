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
| GBR-05 | Feature-004 analyzes an eligible independent Design Request only after its ownership, consent, input and security checks. Review authority, terminal outcomes and revision rules follow the approved Feature-004 policy; the request has no workshop-booking side effect. | UC-30, UC-35, UC-36, UC-37 | Separates independent Design Request review from workshop booking and custom manufacturing. | Independent Design Request workflow |
| GBR-06 | AI price suggestions may support a Manager's price decision for a new workshop package or retail product, but AI never publishes the price or decides a custom-manufacturing order. | UC-31 | Preserves accountable human pricing decisions. | Catalogue pricing workflow |
| GBR-07 | A custom-manufacturing order is Member-only and is created from a reference image or supported manual configuration, optional deadline, and fulfilment method. It is auto-accepted only when its deadline is valid and custom queue capacity remains. | UC-59, UC-60 | Custom manufacturing has a distinct, capacity-limited lifecycle. | Custom-order workflow |
| GBR-08 | A custom-order deposit equals 50% of the wax-package price. The final balance equals the remaining 50% plus actual surcharges recorded by Staff or Manager. | UC-61, UC-63, UC-64 | Defines the agreed custom-order payment split. | Custom-order and VNPay workflows |
| GBR-09 | A paid retail order uses store pickup or Staff-recorded manual GHTK handoff. Internal statuses include preparation/packing, ready for pickup, picked up, or handed to GHTK; courier tracking is not a system status. | UC-48 to UC-53, UC-65 | V1 ends shop responsibility at pickup or handoff. | Fulfilment workflow |
| GBR-10 | One voucher or one automatic discount may be used per retail order, and one voucher per workshop booking. Loyalty points may be combined with an automatic discount, or with a voucher that allows points; points are a non-cash discount and limits are Manager-configured. | UC-44, UC-48, UC-51 | Defines the minimum V1 loyalty/promotion policy. | Checkout and operational-parameter configuration |
| GBR-11 | Klook obtains Mland availability/hold before it confirms a workshop booking. Mland remains the capacity source of truth; Klook cancellation events do not release Mland capacity automatically. | UC-27, UC-28 | Mland does not consume Klook cancellation events in V1. | Klook integration workflow |
| GBR-12 | Google Maps/Places data is read-only, displayed with required attribution and link, and is not persisted. A customer is redirected to Google Maps to submit a review. | UC-57, UC-58 | Complies with the agreed Google Maps boundary. | Public store information flow |
| GBR-13 | The application retains Gemini text prompts/responses for at most 30 days and no AI image input after processing. Custom reference images, delivery data, and payment/audit records follow their approved retention periods. | UC-30, UC-35, UC-59 and NFR | Limits personal-data retention to the approved purpose. | Data-retention controls |
| GBR-14 | Product review, price and publication are separate states. A Manager approves, adjusts or rejects products and prices; an approved price is applied as a separate action, and only a product with an approved review, active category, usable image and applied price for every size can be published. A Manager's create screen and Publish now send these steps as separate audited commands and never approve a Pending review submitted by Staff in one click. | UC-12 to UC-17 | Keeps catalogue prices accountable and prevents unreviewed content from being sold. | Product review, pricing and publication workflow |
| GBR-15 | A product has 1–25 fixed sizes from 5 to 29, each with one read-only SKU `SP{product code}-S{size}` that never changes and is never reused. Quantity is kept per size at each selling branch and cannot fall below valid holds at that branch; the price is shared by all branches. | UC-11 to UC-13 | Gives each sellable ring variant a stable identity and protects held stock. | Product creation, import and quantity update |
| GBR-16 | Hiding, stopping sale of a size or at a branch, changing price, deactivating a category or deleting a product never cancels valid holds, changes accepted orders or removes history; deletion is a soft delete. | UC-12, UC-16, UC-17, UC-18 | Protects terms already agreed with customers. | Product management and checkout revalidation |
| GBR-17 | Excel import creates draft products under the same rules as manual creation, validates the whole file before creating anything, and is all or nothing. Quantities are entered per selected branch within the actor's scope, and a preview expires after 60 minutes. | UC-13 | Allows bulk entry without weakening data quality. | Product import workflow |
| GBR-18 | Only a Manager manages vouchers, automatic discounts and the loyalty point policy. Admin manages accounts and roles only and has no promotion or point permission. | UC-45, UC-46, UC-51 | Keeps commercial decisions with the business owner. | Role-based access checks |
| GBR-19 | Each retail order uses at most one voucher or automatic discount, and each workshop booking at most one workshop voucher. The retail eligible amount includes only purchasable lines in scope (for a branch-limited voucher, only lines at branches in scope) and excludes shipping; a workshop voucher discounts only the tickets in scope. | UC-48 | Prevents stacked discounts and defines a clear discount base. | Checkout and booking calculation |
| GBR-20 | For a workshop booking, the voucher and then points are subtracted from the package fee; Workshop Management calculates the deposit and the remaining amount from the fee after discounts. A fee after discounts of 0 VND is accepted; only a negative fee is rejected. | UC-44, UC-48 | Avoids discounting or deducting points twice across deposit and remaining payments. | Workshop booking and deposit workflow |
| GBR-21 | Points and voucher uses are held when an order or booking is accepted and finalized exactly once after verified payment or acceptance of a valid 0 VND transaction; they are released on expiry or cancellation before payment and refunded once after a confirmed valid or Mland workshop cancellation, per branch order or per ticket where the transaction is split. | UC-44, UC-48, UC-50 | Ensures customers do not lose benefits and limits are not exceeded. | Benefit hold, release and refund workflow |
| GBR-22 | Points are earned once per transaction on the amount actually paid after discounts and points, excluding shipping, rounded down: after verified full retail payment, or when a workshop is completed and fully paid; a multi-ticket booking earns per completed ticket from one group total. | UC-49 | Rewards real spending in both services from one shared balance. | Point earning workflow |
| GBR-23 | Policy and program changes apply only to transactions accepted afterwards; accepted orders and bookings keep the discount, points and policy recorded at acceptance. | UC-45, UC-46, UC-51 | Honors terms already agreed with customers. | Policy snapshot at acceptance |
| GBR-24 | A product is sold at one or more active branches from the shared branch list; quantity and sale status are kept per size at each branch, and a customer can buy only at a branch where the size is on sale and available. A Manager manages stock at every branch; a Staff only at assigned branches, and changes content, images and proposed prices only of products sold at one of them. | UC-10 to UC-14 | Each branch holds different stock, while one product definition and price are shared. | Branch stock, branch scope checks and public availability |
| GBR-25 | A product voucher may be limited to one or more branches; only cart lines whose branch is in scope are eligible, and the branch scope cannot change after creation. Automatic discounts have no branch limit. | UC-45 to UC-48 | Lets a promotion target the stock of a specific branch. | Program validation and checkout calculation |
| GBR-26 | Product pages show the largest automatic discount as a reduced price and vouchers as labels only; this display never replaces the checkout quote and a failure to read promotions never blocks the page. | UC-10, UC-11, UC-47 | Shows customers the current offer without promising a final amount. | Public product card and product details |
| GBR-27 | A rejected write request to promotion or loyalty management is recorded with the actor, the request path containing only identifiers, the error code and the rejected field names, without values, tokens or personal data. | UC-45, UC-46, UC-47, UC-51 | Keeps evidence of refused changes without storing sensitive data. | Promotion and loyalty audit |
