# Backend Design Blocker Log

## BE-DESIGN-0001 — FE-08 shared-boundary prerequisites

- **Recorded at:** 2026-10-26T00:00:00+07:00
- **Reporter:** Codex
- **Feature / task:** FE-08 Retail Order and Fulfillment; tasks T005–T006 and all dependent backend implementation tasks
- **Status:** BLOCKED

### Stopped scope and design sources

The stopped scope is all FE-08 backend implementation, including its module,
schema migrations, REST endpoints, and tests. The affected design sources are
`ppswbs-spec/features/008-retail-order-and-fulfillment/spec.md`,
`plan.md`, `data-model.md`, and
`contracts/retail-order-and-fulfillment.openapi.yaml`.

### Precise missing or conflicting decisions

1. FE-08 requires an authenticated, active Member and server-side ownership
   checks. `SecurityConfiguration` currently permits every `/api/v1/**`
   request and does not establish an authenticated principal from the existing
   Firebase token verifier. The implementation contract for applying verified
   Firebase identity to an API request has not been provided.
2. FE-08 has approved `branchId` as an immutable UUID across branch orders and
   cross-feature facts. The available account-management facade exposes only
   `Long locationId` values through `getLocationScope`, and migration V5 stores
   `location_id BIGINT`. No approved mapping or replacement boundary relates
   these identifiers.
3. FE-08 must use named public boundaries from FE-02 for authoritative
   catalogue/variant/stock/shipping facts, FE-06 for benefit lifecycle, and
   the shared payment domain for verified payment evidence. No corresponding
   backend modules or named facade contracts exist in the imported source.

### Evidence, facts, and unknowns

- Fact: `configuration/SecurityConfiguration.java` configures stateless
  security but permits all API routes.
- Fact: `features/accountmanagement/service/TokenVerificationService.java`
  can verify a Firebase token, while no inspected request filter or principal
  bridge invokes it.
- Fact: `features/accountmanagement/facade/AccountFacade.java` returns
  `List<Long>` for location scope, and migration
  `V5__account_management.sql` persists `location_id BIGINT`.
- Fact: no source files for product/catalogue, loyalty/promotion, payment, or
  retail/order modules were found under `src/main`.
- Unknown: the approved authentication boundary, the canonical branch/location
  identity migration or mapping, and the exact facade DTOs/error semantics
  owned by FE-02, FE-06, and payment.

### Why implementation cannot proceed correctly

Choosing any of these contracts locally would invent authorization, ownership,
foreign-key identity, and financial/inventory behavior. That could expose a
Member's cart/order to another identity, associate an order with the wrong
branch, or duplicate policy/payment state once the owning features are merged.

### Work state and verification

- **Files changed:** this blocker log only; no FE-08 Java source, migration,
  test, or contract was changed.
- **Checks run:** targeted source inspection of security, account facade,
  account migration, module architecture test, and FE-08 artifacts.
- **Checks not run:** Maven tests were not run because implementation has not
  begun and the blocker is a missing approved design boundary.

### Required decision or design to resume

The responsible owners must provide or approve all of the following:

1. the shared Firebase-request authentication/principal and authorization
   boundary that FE-08 must use;
2. one canonical branch identity for FE-08, plus an approved migration or
   facade mapping from current account `locationId` values; and
3. versioned named facades (or approved stubs owned by their teams) for FE-02
   catalogue/stock facts, FE-06 benefit lifecycle, and verified payment
   evidence.

After those sources are available, re-check FE-08 gates B01–B10 and append a
resolution entry to this record before implementation resumes.

### Resolution update — 2026-10-26T00:00:00+07:00

The responsible feature owner, Thinh, approved `locationId: Long` as the
canonical FE-08 location identifier, matching account management migration V5
and `AccountFacade#getLocationScope`. The FE-08 OpenAPI contract now exposes
`locationId` as `integer/int64`; no branch/location mapping is required for
this release. This resolves item 2 only. Authentication and the FE-02, FE-06,
and payment boundaries remain blocked until their real contracts are supplied
or an explicitly bounded development substitute is approved.

### Resolution update — 2026-10-26T00:00:00+07:00

Thinh approved a development-only substitute for the remaining boundaries so
FE-08 can be implemented and demonstrated before the owning teams merge their
facades. The substitute must be isolated behind FE-08 ports and enabled only
by the `local` Spring profile:

- a local actor resolver accepts `X-Debug-Firebase-Uid`, resolves it through
  the existing public account facade, and enforces active `MEMBER` status;
- deterministic local adapters provide catalogue/stock, loyalty/benefit, and
  payment responses; and
- non-local profiles fail closed at these adapter boundaries until the owning
  feature's approved facade is merged.

This unblocks development implementation only. It does not approve production
authentication, live payment, live GHTK, or cross-feature contracts. Replace
the local adapters rather than changing FE-08 REST/persistence semantics when
the approved facades become available.

## BE-DESIGN-0002 — FE-08 baseline inventories contradict approved fulfilment flow

- **Recorded at:** 2026-10-26T00:00:00+07:00
- **Reporter:** Codex
- **Feature / task:** FE-08 Retail Order and Fulfillment; prerequisite tasks T001 and T002
- **Status:** BLOCKED

### Stopped scope and design sources

All FE-08 implementation is stopped before the next source, migration, test,
or configuration change. The affected sources are FE-08 `plan.md` and
`tasks.md` (Phase 1), the FE-08 OpenAPI contract, Report 3 external API and
background-job inventories, and `ppswbs-spec/APIs.md`.

### Precise conflict

FE-08 specifies automatic GHTK fee quotation, shipment creation after verified
payment, webhook-first status synchronization, and status-API fallback. Report
3's external API inventory currently states that V1 records only a manual GHTK
handoff and does not call GHTK APIs, quote fees, or track delivery. In addition,
the API catalog lists obsolete retail checkout and fulfilment-choice routes
instead of the current FE-08 cart, quote, grouped-checkout, payment, and
tracking contract.

### Evidence, facts, and unknowns

- Fact: `tasks.md` marks T001 and T002 incomplete and states that Phase 1 gates
  Phase 2.
- Fact: Report 3 external API inventory row 8 says "V1 does not call GHTK APIs,
  quote delivery fees, [or] track delivery".
- Fact: `ppswbs-spec/APIs.md` lines 158-159 list only
  `POST /api/v1/ready-ring-orders` and
  `PUT /api/v1/ready-ring-orders/{orderId}/fulfilment-choice`, which conflict
  with the feature OpenAPI contract.
- Fact: this check found no current FE-08 Java files under
  `features/readyringsales`; no new migration, test, or runtime configuration
  is pending from this implementation pass.
- Unknown: whether the Report 3/API catalog owners approve replacing their
  manual-only baseline with the automatic GHTK V1 scope.

### Why implementation cannot proceed correctly

Implementing automatic provider behavior while an active project baseline says
manual-only would create incompatible requirements and expose routes whose
documented authority is unclear. Choosing which shared source prevails would
be a product and cross-team documentation decision, not an FE-08 implementation
choice.

### Work state and verification

- **Files changed in this implementation pass:** this appended blocker report
  only. The prior FE-08 OpenAPI `locationId` alignment remains a separate
  documented change.
- **Checks run:** SpecKit prerequisite check, checklist scan (22/22 checked),
  targeted Report 3/API catalog comparison, and backend worktree inspection.
- **Checks not run:** Maven, migration, and API tests; code implementation is
  stopped at the design gate.

### Required decision or design to resume

The Report 3 and API catalog owners must approve and synchronize the automatic
GHTK V1 flow, including pickup-only production fallback and the FE-08 REST
route set. After T001 and T002 have evidence of completion, re-check the task
dependencies and append a resolution update to this record before restarting
FE-08 implementation.

### Resolution update — 2026-10-26T00:00:00+07:00

Thinh approved the required shared-document updates. T001 now records
automatic GHTK quotation, prepaid/no-COD shipment creation, webhook/status
reconciliation, and pickup-only production fallback in the Report 3 API and
background-job inventories. T002 now aligns `APIs.md` with the FE-08 REST
contract and its development-only adapter boundary. The contradictory
manual-only GHTK statement and obsolete fulfilment-choice route were removed.
This resolves BE-DESIGN-0002; development remains subject to the explicitly
scoped local substitutes recorded under BE-DESIGN-0001.

## BE-DESIGN-0003 — FE-08 physical persistence mapping is not approved

- **Recorded at:** 2026-10-26T00:00:00+07:00
- **Reporter:** Codex
- **Feature / task:** FE-08 Retail Order and Fulfillment; prerequisite task T005 and dependent migrations T012 onward
- **Status:** BLOCKED

### Stopped scope and design sources

Backend implementation is stopped before creating entities, repositories,
Flyway migrations, controllers, or tests. The sources are FE-08 `data-model.md`,
`plan.md`, and `tasks.md` T005/T012.

### Precise missing decision

The data model expressly says it does not authorize a database migration and
leaves physical table names, indexes, locking clauses, and Flyway ordering to
be confirmed. The required `implementation-readiness.md` does not exist, while
T005 remains incomplete and explicitly gates backend Phase 2.

### Evidence, facts, and unknowns

- Fact: `implementation-readiness.md` is absent.
- Fact: `data-model.md` line 4 says physical tables, indexes, locking, and
  Flyway ordering require confirmation.
- Fact: T012 requires a migration covering carts, branch orders/items, holds,
  quotes, fulfilment, shipments, events, evidence, idempotency/version, and
  retention indexes.
- Unknown: the approved table/foreign-key/index mapping, the selected
  concurrency mechanism for cart and checkout rows, and the exact migration
  allocation for this baseline.

### Why implementation cannot proceed correctly

Inventing a schema or lock strategy would make an irreversible migration and
could conflict with the eventual FE-02, FE-06, payment, and fulfilment
boundaries. The local adapters approve substitute integrations; they do not
approve physical persistence design.

### Work state and verification

- **Files changed in this pass:** shared documentation and task evidence for
  T001/T002, plus this blocker report; no FE-08 Java source, migration, or test
  was created.
- **Checks run:** existence check for implementation readiness, targeted task/
  data-model inspection, and `git diff --check`.
- **Checks not run:** Maven, migration, and API tests because the persistence
  design gate is unresolved.

### Required decision or design to resume

Approve a physical FE-08 schema and concurrency/migration approach, then record
it in `implementation-readiness.md` under Thinh's ownership. At minimum it
must define the cart/order/hold/quote/shipment records, foreign-key ownership,
indexes, optimistic or pessimistic locking points, and the next migration
number. Re-check T005 before any implementation resumes.

### Resolution update — 2026-10-26T00:00:00+07:00

Thinh approved a cart-first migration strategy. The new implementation
readiness record defines V6 as the cart-only schema, its ownership, constraints,
optimistic concurrency behavior, and the deferred V7-V9 allocations. This
resolves the persistence gate for US1 only. Checkout, payment, quote,
shipment, and operations implementation remain blocked by their later-slice
facade and persistence gates.

### Development-substitute correction — 2026-10-26T00:00:00+07:00

Spring Modulith verification shows that account management does not yet expose
its facade as a named interface, so FE-08 cannot legally depend on it. For the
approved local-only cart slice, the actor substitute therefore accepts a
positive `X-Debug-Account-Id` without calling account management. This is not
production authorization; non-local profiles continue to fail closed. Replace
this substitute with the approved account facade after its owner exports one.

### Resolution update â€” 2026-10-27T00:00:00+07:00

Thinh authorized the next FE-08 development slice: persisted checkout with
temporary deterministic substitutes for FE-06 vouchers/points, GHTK quotation,
and VNPay payment verification. `implementation-readiness.md` now authorizes
the V7 checkout aggregate schema, row-lock/idempotency behavior, and the
local-only mock boundary. The substitutes must not call external providers or
claim real payment, voucher, points, stock, or shipment effects. Non-local
profiles remain fail-closed and the approved owner facades will replace the
adapters without changing checkout REST or persistence semantics.

## BE-DESIGN-0004 — FE-10 production reporting source gates

- **Recorded at:** 2026-10-27T00:00:00+07:00
- **Reporter:** Codex
- **Feature / task:** FE-10 Manager Dashboard production implementation
- **Status:** BLOCKED

Production FE-10 remains blocked because the approved Manager capability,
active `account_location_assignments` reporting facade, final paid workshop
invoices, completed-session capacity, and accepted attendance aggregates do
not exist in the imported backend. The user, Thinh, approved a `local`-profile
development substitute only: reporting may compose deterministic demo facts
using `locationId: Long`, while every non-local profile fails closed. This
does not approve production authorization, real revenue reporting, or source
schema changes owned by other features.
