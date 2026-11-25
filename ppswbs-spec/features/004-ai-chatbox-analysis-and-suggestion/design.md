# Feature-004 Frontend Design — Design Request and Human Review

Date created: 2026-11-20
Date updated: 2026-11-24
Author: Ngô Tiến Đạt
Last updated by: Ngô Tiến Đạt

## Baseline

React/Vite, shared auth, HTTP, i18n and UI primitives remain the frontend baseline.
The existing `designreview` slice may be reused, but AI pages, AI messages, AI
result cards and price-suggestion screens are retired from Feature-004.

## Routes

```text
/design-requests/new
/design-requests/:designRequestId
/my/designs?tab=custom
/account/my-custom-orders
/account/my-custom-orders/{customOrderId}
/design-review-requests
/design-review-requests/:reviewRequestId
```

`/design-requests/new` is Member-only. A Guest may see the entry point, but the
frontend must show a sign-in prompt before form, upload or tracking access. The backend remains authoritative for Member ownership. Review routes are Staff/Manager-only; Admin receives only the
approved technical projection.

## Screens and states

Customer screens: entry/form, five-image gallery, per-image quality feedback,
submission confirmation, Member history, request detail and
accepted/rejected result. The Member custom-order area has separate Requests and
Orders views. A valid quote is paid in full from custom-order detail; there is
no Add to Cart, deposit or balance-payment screen.

Operational screens: review queue, review detail, image gallery, mandatory
explanation form, decision confirmation, terminal conflict and access denied.

Every screen covers loading, empty, invalid input, image processing, blurry image,
five-image limit, pending review, accepted, rejected,
conflict, retry and service failure. Custom-order screens also cover quote ready,
full-payment pending, payment success, quote expiry/cancellation and request-new-
quote states.

## UI behavior

- Show image quality feedback immediately after upload.
- Disable submit while any image is processing or too blurry.
- Preserve description and selected images during recoverable errors.
- Use one locale consistently per session and valid UTF-8 text/font rendering.
- Never display raw token, database ID, storage key, private contact or AI data.
- Use stable test IDs for image slots, quality states, quota messages, decision form,
  explanation field and status/result projections.

## Existing-style-first visual rule

Feature-004 must follow the closest approved existing customer and operations
patterns before introducing new components. The in-place Figma frames are the
visual reference for this feature; reuse their layout, typography, tokens,
spacing, controls, responsive behavior and bilingual interaction states. Any
new pattern must be the smallest reusable extension required by the human-review
and custom-order requirements. Visual approval does not change the Feature-004
business rules, API contract, permission boundary or state lifecycle.

## Figma gate

Use the approved existing in-place frames and current application page patterns
as the Feature-004 visual baseline. Do not create a duplicate Figma page when an
existing frame can be updated. Record node IDs and revision in `version.md`.
Visual approval is complete for the current baseline; implementation remains
subject to the specification, API contract, permission and engineering gates.

## Draft redesign for user review — 2026-11-24

The current React implementation was reviewed before preparing a new draft.
The request page keeps its existing fields and validation but replaces the
basic visual treatment with the existing Mland shell, card hierarchy and
responsive patterns.

Draft frames in file mland-operation-hub, page 498:2:

| Screen | Desktop | Mobile |
|---|---:|---:|
| Custom design request | 588:43 | 590:43 |

The draft gives the selected Feature-012 snapshot a clear reference card,
makes the five-image limit visible and makes image-quality feedback a first-
class state beside the upload area. It does not add a field or change the
Member-only request boundary.

## Consultation geometry and scroll contract

The contextual and general consultation chats share one outer width token,
header/message/composer spacing scale, desktop maximum width and mobile
full-width behavior. Loading, empty, error and loaded sidebar states reserve
the same horizontal width.

The implementation must not scroll the page or message thread as a side effect
of route mount, conversation selection, polling, focus, visibility change,
loading or empty-state rendering. The first view opens at the top. A successful
explicit send may reveal the sent message. Polling preserves the reader's
position; a new inbound message while reading older content uses a non-blocking
new-message affordance rather than moving the viewport.

## Frontend and E2E handoff

The next implementation agent must recompose the request page from existing
shared patterns, reuse one consultation surface-width contract, remove effects
that scroll solely because messages changed or polling completed, preserve
Vietnamese UTF-8 rendering, and add E2E coverage for geometry, initial top
position, polling stability, send behavior, sidebar width stability and
permission-safe context display.

No production code is changed by this update. The Figma draft remains pending
user approval.
