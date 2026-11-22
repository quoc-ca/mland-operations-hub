# Tasks — Feature-004 Design Request and Human Review

Date created: 2026-11-20
Date updated: 2026-11-20
Author: Ngô Tiến Đạt
Last updated by: Ngô Tiến Đạt

## Phase A — Documentation rebasing

- [ ] Rename logical feature to `004-design-request-and-human-review`.
- [ ] Remove AI, Gemini, Basic AI and price-suggestion references from Feature-004.
- [ ] Synchronize `spec-general.md`, `APIs.md`, `DB.md`, `screen.md`, Report 1, Report 3 and registry.
- [ ] Mark old AI E2E evidence as historical/obsolete.

## Phase B — Contract and data model

- [ ] Define Guest opaque session/tracking token lifecycle and five-request daily quota.
- [ ] Define five-image request model and image-quality lifecycle.
- [ ] Define `NEED_REVIEW → ACCEPTED/REJECTED` transitions.
- [ ] Require bounded explanation for every Staff/Manager decision.
- [ ] Define idempotency, optimistic version and audit behavior.
- [ ] Create the human-review OpenAPI contract; do not use the legacy AI contract as baseline.

## Phase C — Figma and frontend specification

- [ ] Prepare Guest/Member customer and Staff/Manager operational frames.
- [ ] Include loading, quality, quota, pending, accepted, rejected, token and conflict states.
- [ ] Record Figma revision and wait for explicit user approval.
- [ ] Finalize frontend handoff and screen-to-API mapping after approval.

## Phase D — Implementation handoff

- [ ] Backend implements persistence, quality validation, Guest quota and human review.
- [ ] Frontend implements the approved design and safe projections.
- [ ] Do not introduce compatibility behavior for the retired AI flow.

## Phase E — Verification

- [ ] Run backend unit/integration tests.
- [ ] Run frontend unit/component tests and build.
- [ ] Run Browser MCP E2E scenarios E2E-004-001 through E2E-004-012.
- [ ] Record screenshots and execution status; never mark unexecuted tests PASS.
## Phase F — Custom-order full-payment handoff

- [ ] Add Member Requests and Orders views under My Custom Orders.
- [ ] Display immutable quote total, currency, quote generation and expiry.
- [ ] Start exactly one payment attempt for the complete active quote.
- [ ] Do not add custom orders to the retail cart.
- [ ] Remove deposit, remaining-balance and final-balance actions from the custom-order UI.
- [ ] Show quote/payment expiry and cancellation, including request-new-quote where permitted.
- [ ] Test ownership isolation, duplicate payment commands, stale quote, expired quote and late payment evidence.
