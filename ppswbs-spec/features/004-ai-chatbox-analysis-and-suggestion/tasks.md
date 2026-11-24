# Tasks — Feature-004 Design Request and Human Review & Custom Manufacturing Integration

Date created: 2026-11-20
Date updated: 2026-11-22
Author: Ngô Tiến Đạt
Last updated by: Ngô Tiến Đạt

## Phase A — Documentation rebasing
- [x] Rename logical feature to `004-design-request-and-human-review`.
- [x] Remove AI, Gemini, Basic AI and price-suggestion references from Feature-004.
- [x] Synchronize `spec-general.md`, `APIs.md`, `DB.md`, `screen.md`, Report 1, Report 3 and registry.
- [x] Mark old AI E2E evidence as historical/obsolete.

## Phase B — Contract and data model
- [x] Define Guest opaque session/tracking token lifecycle and five-request daily quota.
- [x] Define five-image request model and image-quality lifecycle.
- [x] Define `NEED_REVIEW → CLARIFICATION_REQUIRED / ACCEPTED / REJECTED` transitions.
- [x] Require bounded explanation for every Staff/Manager decision.
- [x] Define idempotency, optimistic version and audit behavior.
- [x] Create the human-review and custom-design-order-intake OpenAPI contracts.

## Phase C — Figma and frontend specification
- [x] Prepare Guest/Member customer and Staff/Manager operational frames.
- [x] Include loading, quality, quota, pending, clarification, accepted, rejected, token and conflict states.
- [x] Record Figma revision and component requirements.
- [x] Finalize frontend handoff and screen-to-API mapping.

## Phase D — Implementation handoff (Dev A, B, C, D)
- [x] Dev A (Backend intake & review): Implement persistence, Feature-012 ring snapshot capture, revision on clarification, quality validation, Guest quota, and dual-mapped endpoints.
- [x] Dev B (Custom Manufacturing & Payment backend): Implement custom order aggregate, quote generation & versioning, Manager approval gate, 100% full payment integration via VNPay, quote expiry/cancellation, and status tracking.
- [x] Dev C (Frontend): Implement Member `/account/my-custom-orders` (Requests/Orders tabs), `/account/my-custom-orders/:customOrderId` detail & progress stepper, Staff review clarification & quote preparation, zero deposit/cart UI.
- [x] Dev D (Documentation sync): Synchronize feature specifications, data models, plans, tasks, and changelog.

## Phase E — Verification

- [ ] Retire or isolate historical AI-support, AI-image-analysis and AI-price
  implementation/tests so they cannot be exposed by the current human-review
  routes or treated as current acceptance evidence.

## Phase F — 2026-11-22 change request

### Replacement Figma baseline

- [x] Recreate Feature-004 customer request and Staff/Manager review screens
  from the current React/Vite shell and shared visual conventions.
- [x] Record the approved existing-frame nodes in the Feature-012 cross-feature
  handoff; the replacement page remains historical.
- [x] Obtain user approval for the existing-frame visual baseline.

- [ ] Update customer and staff copy to the approved Vietnamese terminology.
- [ ] Accept any owner-visible Feature-012 design state and capture an
  immutable snapshot at custom-request submission.
- [ ] Add the saved-design `Tư vấn thiết kế` entry point for Guest and Member.
- [ ] Extend Feature-013 context and Guest-session contract as required.
- [x] Remove remaining obsolete AI-analysis wording from active UI/spec sections;
  retain only historical audit references.
- [ ] Add Staff/Manager E2E coverage for design-state references, ownership,
  mandatory explanations, consultation participation and stale decisions.
- [x] Backend compilation check with Java 25 (`release 21`).
- [x] Frontend unit/component tests in `src/features/designreview` (31/31 passing).
- [x] Session lifecycle regression tests (29/29 passing).
- [ ] Detailed E2E test scripts execution (waiting for user-provided test scenario scripts).

## Frontend redesign and consultation regression — 2026-11-24

- [ ] T004-060 Implement the approved existing-style Figma request layout.
- [ ] T004-061 Keep the Feature-012 reference, description and five-image
  controls visually consistent with shared Mland components.
- [ ] T004-062 Share the consultation outer width/spacing contract with
  Feature-013; do not create a narrower contextual chatbox.
- [ ] T004-063 Remove load/polling-driven auto-scroll and preserve the reader
  position; allow explicit successful send behavior only.
- [ ] T004-064 Add E2E for desktop/mobile geometry, initial top position,
  polling stability, send/retry, loading/empty width and Vietnamese rendering.
- [ ] T004-065 Do not mark complete until screenshots and all affected Member,
  Staff and Manager scenarios pass without regression.

## Phase F — Custom-order full-payment handoff
- [x] Add Member Requests and Orders views under My Custom Orders.
- [x] Display immutable quote total, currency, quote generation and expiry.
- [x] Start exactly one payment attempt for the complete active quote.
- [x] Do not add custom orders to the retail cart.
- [x] Remove deposit, remaining-balance and final-balance actions from the custom-order UI.
- [x] Show quote/payment expiry and cancellation, including request-new-quote where permitted.
- [x] Test ownership isolation, duplicate payment commands, stale quote, expired quote and late payment evidence.
