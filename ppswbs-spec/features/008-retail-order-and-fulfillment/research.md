# Research: Retail Order and Fulfillment

**Feature**: `008-retail-order-and-fulfillment`
**Date**: 2026-10-23 | **Updated**: 2026-10-24

This research resolves design choices needed to plan FE-08. It does not create
production credentials, approve a migration, or claim that the external
provider contract is ready for go-live.

## Decision 1: Extend the existing ready-ring sales and fulfilment boundaries

- **Decision**: Keep cart, checkout, order snapshots, payment-state projection,
  and Member order reads in `readyringsales`. Keep preparation, carrier
  shipment, webhook/status synchronization, and physical custody evidence in
  `fulfilment`. Connect them with post-commit events and named facades only.
- **Rationale**: The backend already declares `readyringsales` as the owner of
  ready-ring sales and delivery choice, and `fulfilment` as the owner of
  continuation/custody/handoff. This preserves the modular-monolith boundary
  and avoids cross-module repository access.
- **Alternatives considered**: One large order module was rejected because it
  would absorb carrier custody and existing fulfilment responsibilities;
  putting GHTK calls in a controller was rejected because it would bypass
  transaction, retry, and credential boundaries.

## Decision 2: Use a post-commit paid-order event for shipment creation

- **Decision**: After the local verified-payment transaction commits,
  `readyringsales` publishes a minimal `RetailOrderPaid` event. `fulfilment`
  consumes it idempotently and creates the GHTK shipment outside the local
  order/payment transaction.
- **Rationale**: A remote GHTK call cannot participate in the MySQL transaction.
  The event gives durable recovery after process restart and prevents a
  network timeout from rolling back or duplicating the paid sale.
- **Alternatives considered**: Calling GHTK while holding the checkout/payment
  transaction was rejected because it creates long locks and ambiguous commit
  behavior. A synchronous controller call was rejected for the same reason.

## Decision 3: Use a contract-first GHTK adapter with a mock provider

- **Decision**: Define a provider-neutral `ShippingProviderPort` and implement
  a deterministic mock provider first. The production GHTK adapter is enabled
  only after partner credentials, callback authentication, quotas, and the
  prepaid/no-COD service mapping are approved.
- **Rationale**: The feature specification explicitly selects contract-first
  development until real credentials are available. It also prevents sample
  documentation fields from being treated as a production guarantee.
- **Alternatives considered**: Calling GHTK directly from application code was
  rejected because it leaks provider details and prevents reliable contract
  tests.

## Decision 4: GHTK boundary shape

- **Decision**: Use four provider operations: fee quote before payment, shipment
  creation after verified payment, status retrieval for reconciliation, and
  inbound webhook status updates. Credentials remain server-side.
- **Evidence**: GHTK documents `GET /services/shipment/fee` with `Token` and
  `X-Client-Source`, weight in grams, destination/pickup data and declared
  value; shipment creation uses `POST /services/shipment/order`; status uses
  `GET /services/shipment/v2/{TRACKING_ORDER}`; webhook updates include
  `partner_id`, `label_id`, `status_id`, and `action_time` and are acknowledged
  with HTTP 200.
- **Source**: [GHTK fee API](https://api.ghtk.vn/docs/submit-order/calculate-shipping-fee/),
  [GHTK order API](https://api.ghtk.vn/docs/submit-order/submit-order-express/),
  [GHTK tracking API](https://api.ghtk.vn/docs/submit-order/tracking-status/),
  [GHTK webhook](https://api.ghtk.vn/docs/submit-order/webhook/).

## Decision 5: Fee and package inputs

- **Decision**: FE-02 owns authoritative shipping weight and package
  dimensions for each eligible product/variant. FE-08 sends the calculated
  package weight and declared order value for a fee quote. Checkout charges the
  delivery fee plus only Mland-approved mandatory surcharges; optional
  insurance is not silently charged.
- **Rationale**: The Member needs a stable fee before payment, and the same
  accepted snapshot must be used for shipment creation. Staff may report an
  operational mismatch, but cannot silently reprice a paid order.
- **Alternatives considered**: Staff-entered weight was rejected as the normal
  source because it would make pre-payment quotation unreliable. A fixed
  category profile was rejected because product variants may have different
  packaging.

## Decision 6: Idempotency, webhook, and reconciliation behavior

- **Decision**: Reuse the immutable Mland partner order identifier for GHTK
  creation retries. Store provider identifiers once. Validate webhook fields,
  deduplicate by provider event identity/content, ignore stale backward moves,
  and return HTTP 200 only after a valid event is durably accepted. A scheduled
  or staff-triggered status retrieval reconciles stale/missing webhook data.
- **Rationale**: GHTK can retry callbacks and a create response can be lost.
  Safe replay is required to preserve the paid order without creating duplicate
  shipments.
- **Alternatives considered**: Creating a new partner ID per retry was rejected
  because it can create duplicate shipments. Treating webhook delivery as the
  only source was rejected because callbacks can be delayed or missed.

## Decision 7: Scope reconciliation and verification

- **Decision**: This feature plan follows the newly clarified FE-08 spec for
  automatic GHTK creation/tracking. That decision is approved for V1 and
  supersedes the older project baseline that describes manual GHTK handoff and
  no tracking. Before production code is merged, Report 3 and the cross-feature
  API/background-job inventories must be synchronized with the approved scope.
- **Rationale**: Keeping an active manual-only baseline would make the SRS and
  implementation contract contradict each other. A mock provider and contract
  tests can proceed without production credentials, but production must expose
  pickup instead of fabricated carrier behavior until access is approved.
- **Alternatives considered**: Ignoring the conflict was rejected because it
  would leave the API catalog and SRS inconsistent.

## Decision 8: Verification strategy

- **Decision**: Use unit tests for quote/fee/status mapping and state guards,
  provider contract tests against the mock adapter, Spring MVC contract tests,
  JPA/MySQL migration tests, event redelivery tests, and Spring Modulith
  verification. Add a small set of opt-in sandbox tests only after GHTK access
  is approved.
- **Rationale**: The constitution requires fresh evidence for API, persistence,
  authorization, integration, and module-boundary changes without requiring
  production provider access during ordinary development.
- **Alternatives considered**: Live-only integration tests were rejected because
  they are non-deterministic and would require secrets in routine development.

## Decision 9: Marketplace-style grouped checkout

- **Decision**: Record the selected processing branch on each cart line when
  the product is added. At checkout, group lines by branch into separate branch
  orders. The Member reviews each branch subtotal and GHTK shipping fee
  separately, then pays the combined checkout amount once through VNPay.
- **Rationale**: The clarified business flow treats each branch like a separate
  shop while preserving the convenience of one payment. Per-branch order and
  fulfilment state prevents one branch's preparation or carrier status from
  changing another branch's order.
- **Alternatives considered**: Rejecting mixed-branch carts was rejected because
  the intended user experience is marketplace-like. Separate payment sessions
  per branch were rejected because the Member explicitly requires one payment.

## Decision 10: Replace the superseded SSR structure with separate React and REST

- **Decision**: FE-08 application UI belongs to the separate React + Vite client.
  Spring Boot exposes versioned REST endpoints and does not render FE-08 pages or
  use htmx. Backend modules follow
  `features/<feature>/{controller,dto,dao,entity,service,facade}`.
- **Rationale**: Constitution v3.0.0 supersedes the older progressive
  Thymeleaf/htmx baseline. Keeping the old structure would make the plan fail
  the current architecture gate even if the resulting screens worked.
- **Alternatives considered**: Retaining Thymeleaf as a temporary FE-08 UI was
  rejected because there is no ratified exception. Creating a second shared
  order service was rejected because the existing modular monolith is enough.

## Decision 11: Keep frontend state feature-local and provider-neutral

- **Decision**: The React client owns only transient view/form/request state for
  FE-08. The backend remains authoritative for cart ownership, current price,
  availability, holds, payment, order and shipment status. The browser calls
  Mland REST APIs only and never calls GHTK directly.
- **Rationale**: This avoids treating route guards, local totals, prototype
  fixtures or browser payment redirects as business evidence. It also keeps
  provider credentials and raw callbacks outside the frontend bundle.
- **Alternatives considered**: Persisting authenticated order state in browser
  storage was rejected without a lifecycle/security decision. Adding a state
  library by default was rejected because the required complexity and version
  have not been approved.

## Decision 12: Treat frontend foundation and package relocation as explicit gates

- **Decision**: Before scaffold or production code, approve pinned frontend
  versions/tools and the migration path from current root-level module shells to
  Constitution v3 `features/` modules. Verification must prove each feature is a
  distinct Spring Modulith module and must include frontend build/UI/API states.
- **Rationale**: `ppswbs_frontend` currently has no package or lockfile, while the
  backend package tree predates Constitution v3. Guessing versions or silently
  layering new code over both structures would create two competing baselines.
- **Alternatives considered**: Scaffolding with whatever CLI version happens to
  be installed was rejected as non-reproducible. Copying the static prototype
  into production was rejected because it contains fixture behavior and no real
  authorization or API lifecycle.

## Decision 13: Quote before order and recover per branch

- **Decision**: Request GHTK fees using the authenticated Member's current cart
  and branch context before creating an order. A quote request creates no order,
  hold or payment attempt. A successful response contains a server-issued quote
  identifier, branch, fee components and expiry; checkout submits that identifier
  rather than a client-trusted fee. Checkout acceptance revalidates/requotes as
  needed; changed or expired fees are shown for explicit reconfirmation. On
  timeout, React retains the recipient draft only in current in-memory checkout
  state and offers retry or explicit pickup for the affected branch.
- **Rationale**: The previous draft route required an `orderId` before the
  operation that creates orders, which was circular and could introduce a draft
  order lifecycle absent from the specification. Per-branch recovery preserves
  unaffected GHTK choices and the all-or-nothing grouped checkout invariant.
- **Alternatives considered**: Creating a provisional order for quotation was
  rejected because quoting must not create purchase-side effects. Trusting a
  client-supplied old fee or silently switching to pickup was rejected because
  the Member must accept the payable terms.

## Decision 14: Production carrier capability fails closed to pickup

- **Decision**: When approved GHTK access is absent or disabled, the backend
  reports GHTK unavailable without calling a mock. React disables GHTK, shows
  safe contact-Staff guidance, and leaves pickup available. A failure for one
  branch does not remove items or disable eligible choices for other branches.
- **Rationale**: Mock shipment identifiers cannot represent real custody, and a
  silent manual fallback would reintroduce an unapproved workflow. Pickup keeps
  retail checkout usable without fabricating carrier behavior.
- **Alternatives considered**: Mock-in-production, automatic manual handoff and
  whole-checkout blocking were rejected because they either create false
  evidence or unnecessarily block unaffected/pickup orders.

## Decision 15: FE-08 owns the retail side of the FE-06 benefit transaction

- **Decision**: Integrate FE-08 with the FE-06 named interface
  `loyaltypromotion::facade`, following FE-06 gate G-03 and tasks T054/T064. FE-08 builds
  trusted retail facts for read-only benefit preview. When the Member accepts
  checkout, `readyringsales` opens the source transaction and calls
  `reserveAcceptedTransaction` with mandatory participation before committing
  the grouped checkout, branch orders, inventory holds, benefit snapshot, and
  benefit hold together. Under the source lifecycle guard, verified full
  payment or approved zero-total acceptance calls `settleAcceptedBenefits`
  followed by `grantEarnedPoints`; an authoritative unpaid payment failure or
  expiry calls `releaseHeldBenefits` and `finishIneligibleEarn` and restores
  the source cart. All outcome calls use the
  same source reference, monotonic outcome version, and idempotent receipt.
- **Rationale**: Merely revalidating a promotion result does not protect voucher
  quota or Member points and leaves a split-brain window where the retail order
  commits while the benefit reservation fails. Source-owned atomic reservation
  and lifecycle arbitration keep order, inventory, payment, points, promotion
  quota, and earned-points evidence consistent without creating a reverse
  dependency from loyalty into retail repositories.
- **Alternatives considered**: Browser-side reservation, direct access to FE-06
  entities/repositories, `REQUIRES_NEW`, and post-commit in-memory events were
  rejected because they cannot provide rollback in both directions. Releasing
  benefits from a timer, browser return, carrier cancellation, or ambiguous late
  payment was rejected; FE-08 must first establish an authoritative source
  outcome or preserve the case for reconciliation. A Member cancellation before
  payment initiation uses the same outcome adapter to release holds and restore
  the cart; refund and carrier-cancellation UX are not added by this plan.

