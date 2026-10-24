# Quickstart: Feature-08 Verification Walkthrough

This guide is a validation inventory for the planned feature. It does not claim
that FE-08 is implemented or that production GHTK credentials are available.

## Prerequisites

- Java 21 and the backend Maven toolchain.
- Approved and pinned Node.js, React, Vite and TypeScript versions plus the
  frontend build/test tools selected by the frontend-foundation task.
- MySQL test database or the repository's approved integration-test setup.
- Firebase Auth emulator or an approved non-production Firebase project.
- Mock payment and mock GHTK adapters enabled for automated tests.
- No provider token or secret committed to the repository.
- Constitution v3 package relocation approved and Spring Modulith configured to
  detect `features.readyringsales` and `features.fulfilment` as separate modules.

## Scenarios

| ID | Scenario | Expected result |
| --- | --- | --- |
| QS-01 | Guest attempts retail checkout | Access denied; no cart/order/payment effect. |
| QS-02 | Member selects a branch and adds a valid published variant | Cart line records the selected branch; no order or stock-sale effect. |
| QS-03 | Member adds products from two branches and opens checkout | Lines are grouped as separate branch orders; each branch subtotal and GHTK shipping fee is shown separately. |
| QS-04 | Member pays a grouped checkout containing two branch orders | One verified VNPay payment applies to all branch orders exactly once. |
| QS-05 | Member submits stale cart or unavailable quantity | Complete grouped checkout rejected; no partial branch order or hold. |
| QS-06 | Member selects pickup for one branch order | That branch quote excludes GHTK charge; grouped checkout can continue to payment. |
| QS-07 | Member selects GHTK with valid recipient data for one branch order | The development/test mock or approved provider returns a server-issued quote ID; that branch's delivery fee and approved mandatory surcharge are shown before payment. |
| QS-08 | GHTK recipient data is unsupported | GHTK option is blocked for that branch with correction/pickup guidance; no payment attempt is created. |
| QS-09 | Full VNPay payment is verified for a grouped checkout | All branch orders become paid once; duplicate callback is a no-op. |
| QS-10 | Payment is absent after 15-minute hold | Quantities and benefits are released for the grouped checkout; late evidence does not reopen the hold. |
| QS-11 | Paid GHTK branch order receives a successful create response | One shipment and provider identifiers are stored for that branch order; it becomes eligible for preparation. |
| QS-12 | GHTK create response times out after provider accepted the partner ID | The branch order remains paid/shipment-pending; retry uses the same partner ID and does not duplicate shipment. |
| QS-13 | GHTK webhook is duplicated or out of order | Duplicate is acknowledged safely; stale event does not regress that branch order's current status. |
| QS-14 | Webhook is missing or stale | Status retrieval reconciles the branch shipment using the stored provider identifier. |
| QS-15 | Member opens another Member's order | Safe not-found/forbidden result; no protected data is disclosed. |
| QS-16 | Staff attempts wrong-branch handoff | Operation rejected and audited; no custody milestone is recorded. |
| QS-17 | Authorized Staff records GHTK handoff | One custody record is created with actor, branch, time, and carrier reference. |
| QS-18 | Recipient data reaches 30 days after handoff | Recipient/contact/address fields are deleted or irreversibly obscured; minimized order/custody evidence remains. |
| QS-19 | Member opens FE-08 while cart/order data is loading, empty, rejected, stale or unavailable | React displays the approved distinct UI state, preserves safe editable input where applicable, and never fabricates success or authorization. |
| QS-20 | Browser displays a successful VNPay return but backend verification is pending or rejected | React shows pending/error from the Mland API; no branch order is presented as paid from browser state alone. |
| QS-21 | Frontend attempts to request GHTK directly or expose provider configuration | Verification fails; all provider calls and credentials remain server-side. |
| QS-22 | Production GHTK access is absent or disabled | GHTK is disabled with contact-Staff guidance; pickup remains available and no mock/provider call occurs. |
| QS-23 | GHTK is unavailable for one branch in a mixed-branch checkout | Only that branch requires explicit pickup; unaffected branches retain eligible GHTK choices and the recalculated grouped total requires reconfirmation. |
| QS-24 | A referenced GHTK quote expires or changes before payment | Checkout rejects the stale quote ID and creates no payment attempt; the replacement quote, difference and new grouped total are displayed for explicit reconfirmation. |
| QS-25 | Fee quotation times out | Cart and in-memory recipient draft remain available; no order/payment is created and the Member can retry or select pickup for the affected branch. |

## Required validation commands after implementation

Run from `ppswbs_backend` after the backend feature tasks are implemented:

```text
mvn.cmd test
mvn.cmd verify
```

Also run the approved MySQL fresh/upgrade migration checks, OpenAPI validation
for the feature contract, Spring Modulith `ApplicationModules.verify()`, and
the mock-provider contract suite. Record actual command results; do not claim
runtime evidence from this plan alone.

Run the approved frontend build, type, UI-state and API-integration commands
from `ppswbs_frontend` after its package, lockfile and test tools have been
approved and created. This guide intentionally does not invent command names
before that foundation decision exists.

