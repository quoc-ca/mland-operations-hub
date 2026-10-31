# Implementation Plan: Independent Feature-04 Design Workspace

**Date created**: 2026-10-21  
**Date updated**: 2026-10-28  
**Author**: Ngô Tiến Đạt  
**Last updated by**: Ngô Tiến Đạt  

## Summary

Rebase Feature-04 from booking-dependent design analysis to an independent Design
Request capability. Basic AI Support and Manager price suggestion remain separate
use cases. Feature-04 may use Account Management and, only when explicitly needed,
an approved Catalogue facade. It has no Workshop Booking dependency.

## Blocking decisions before code

1. Guest Design Request access and secure ownership proof.
2. Whether a text-only Design Request is valid.
3. Whether all requests or only AI-image requests require Staff/Manager review.
4. Withdraw/revision behavior and approval expiry.
5. Independent image-retention duration after processing.

These are product, authorization and data decisions, not implementation defaults.

## Required code rework

The current backend must be reworked, not patched around, because it currently:

- accepts `bookingCode` and `designVersion` in a booking context and calls booking eligibility;
- stores booking-specific fields and consent purpose;
- imports the legacy booking facade/package;
- routes review by booking/branch/value assumptions;
- uses booking-dependent request idempotency and test fixtures;
- exposes booking-based screen/API semantics.

The agent must remove these dependencies after the decisions above are approved.

## Module boundary

```text
designreview → accountmanagement::facade
designreview → catalogue::facade (optional opaque context only)
```

No import, facade call, entity or repository from Workshop Booking, Workshop
Operations, Settlement, Payment or Ticketing is allowed.

## Delivery slices

1. Approve ownership/review/retention decisions and update spec/design/contract.
2. Replace booking-dependent DesignRequest schema with independent owner/version model.
3. Rework identity and ownership extraction for Member and approved Guest proof.
4. Rework image consent, temporary storage, validation, rate limit and idempotency.
5. Rework AI analysis and result state without booking eligibility or booking snapshots.
6. Rework human review scope, decision lifecycle, revision, withdrawal and audit.
7. Keep price suggestion isolated and enforce Manager/Admin visibility.
8. Update migration append-only, tests, OpenAPI and frontend screens.
9. Run Modulith, MySQL migration, contract, security, concurrency and E2E verification.

## Provider boundaries

- `AiTextSupportPort`: normalized public prompt in, safe text result out.
- `ImageFeatureAnalysisPort`: validated temporary image plus allowlisted independent context in, candidate result out.
- `AiPriceSuggestionPort`: validated catalogue proposal snapshot in, advisory result out.
- `ReferenceImageStoragePort`: temporary put/get/delete with observable retry-safe cleanup.

All provider calls use correlation IDs, a 30-second timeout, safe error mapping and
no raw payload logging. Prompt max is 2,000 characters; image max is 10 MB and
4096×4096. AI never changes business state.

## Verification gates

- No booking field/import/facade remains in Feature-04.
- No Design Request action mutates booking, payment, capacity, ticket or custom-order state.
- Ownership isolation and approved Guest proof are tested.
- Image consent precedes storage/provider call; rate limiting is atomic.
- OpenAPI, data model, design, tasks and code agree on independent flow.
- Flyway clean-install and upgrade tests pass without version-collision workaround.
