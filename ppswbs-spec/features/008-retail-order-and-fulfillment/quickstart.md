# Quickstart: Feature-08 Verification Walkthrough

This guide is a validation inventory for the planned feature. It does not claim
that FE-08 is implemented or that production GHTK credentials are available.

## Prerequisites

- Java 21 and the backend Maven toolchain.
- MySQL test database or the repository's approved integration-test setup.
- Firebase Auth emulator or an approved non-production Firebase project.
- Mock payment and mock GHTK adapters enabled for automated tests.
- No provider token or secret committed to the repository.

## Scenarios

| ID | Scenario | Expected result |
| --- | --- | --- |
| QS-01 | Guest attempts retail checkout | Access denied; no cart/order/payment effect. |
| QS-02 | Member selects a branch and adds a valid published variant | Cart line records the selected branch; no order or stock-sale effect. |
| QS-03 | Member adds products from two branches and opens checkout | Lines are grouped as separate branch orders; each branch subtotal and GHTK shipping fee is shown separately. |
| QS-04 | Member pays a grouped checkout containing two branch orders | One verified VNPay payment applies to all branch orders exactly once. |
| QS-05 | Member submits stale cart or unavailable quantity | Complete grouped checkout rejected; no partial branch order or hold. |
| QS-06 | Member selects pickup for one branch order | That branch quote excludes GHTK charge; grouped checkout can continue to payment. |
| QS-07 | Member selects GHTK with valid recipient data for one branch order | Mock fee quote returns; that branch's delivery fee and approved mandatory surcharge are shown before payment. |
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

## Required validation commands after implementation

Run from `ppswbs_backend` after the feature tasks are implemented:

```text
mvn.cmd test
mvn.cmd verify
```

Also run the approved MySQL fresh/upgrade migration checks, OpenAPI validation
for the feature contract, Spring Modulith `ApplicationModules.verify()`, and
the mock-provider contract suite. Record actual command results; do not claim
runtime evidence from this plan alone.

