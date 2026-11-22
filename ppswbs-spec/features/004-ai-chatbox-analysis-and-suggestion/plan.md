# Implementation Plan: Feature-004 Design Request & Custom Manufacturing Integration

**Date created**: 2026-10-21
**Date updated**: 2026-11-21
**Author**: Ngô Tiến Đạt
**Last updated by**: Ngô Tiến Đạt
**Status**: Implemented across Dev A, Dev B, Dev C, and Dev D.

## Summary

Feature-004 is an independent React/Vite frontend and Spring backend capability.
The frontend baseline is implemented under `src/features/designreview/`.
Feature-004 owns request intake, clarification flow, human review, Feature-012
snapshot capture, and quote handoff.
Custom Manufacturing owns the custom order lifecycle, quote generation and versioning,
Manager approval, 100% full payment (no deposit, no cart line, no split payments),
quote expiry/cancellation, and production/fulfilment tracking.

## Confirmed baseline

- React 19, Vite 7 and `react-router` 7.
- Spring Boot backend with Java 21+ and MySQL Flyway migrations (`V190__custom_manufacturing_and_order_intake.sql`).
- Real routes:
  - `/design-review-requests` (Staff/Manager review queue)
  - `/design-review-requests/:requestId` (Review & decision detail, quote prep)
  - `/design-requests/new` & `/account/design-requests` (intake & tracking)
  - `/account/my-custom-orders` (Requests & Orders tabs)
  - `/account/my-custom-orders/:customOrderId` (Order detail, stepper, full payment CTA)
- Backend is authoritative for permission, ownership, review and quote state.
- Guest uses opaque session & tracking token (no custom order creation).
- Member owns custom design requests and custom manufacturing orders.

## Delivery slices & Developer assignments

1. **Dev A (Feature-004 Backend Intake & Review):** [COMPLETED]
   - Added tables `custom_design_reference_snapshots` and `custom_design_request_revisions`.
   - Feature-012 snapshot capture via `WorkshopRingDesignFacade.getDesignSnapshot()`.
   - Support for `CLARIFICATION_REQUIRED` decision with mandatory explanation.
   - `reviseDesignRequest()` with `If-Match-Version` validation.
   - Dual-mapped endpoints on `/api/v1/design-requests` and `/api/v1/custom-design-requests`.
2. **Dev B (Custom Manufacturing & Payment Backend):** [COMPLETED]
   - Added tables `custom_orders`, `custom_order_quotes`, `custom_order_payment_context`, `custom_order_idempotency_records`.
   - Updated `payment_owner_registrations` constraint to include `CUSTOM_ORDER_FULL_PAYMENT`.
   - Order aggregate lifecycle: `QUOTE_PENDING` -> `FULL_PAYMENT_PENDING` -> `PAID` -> `IN_PRODUCTION` -> `FULFILLED` -> `CLOSED` (or `EXPIRED`/`CANCELLED`).
   - Quote generation & versioning; Manager approval gate.
   - VNPay ingress payment integration for 100% full payment; strict ban on deposit, balance payment, or cart line.
   - Quote expiry and cancellation logic with new quote request increment.
3. **Dev C (Frontend):** [COMPLETED]
   - Dual-tab Member page `/account/my-custom-orders` (Requests tab with clarification modal and Orders tab with full payment button & quote generation).
   - Order detail page `/account/my-custom-orders/:customOrderId` with 4-step progress stepper and Feature-012 snapshot preview.
   - Staff/Manager review detail page with `CLARIFICATION_REQUIRED` selector and quote preparation form.
   - Navigation entry in `MainTopbar` under user profile menu.
   - Verified 0 occurrences of deposit, balance payment, or cart wording in custom orders.
4. **Dev D (Spec & Documentation Sync):** [COMPLETED]
   - Rebaselined `spec.md`, `data-model.md`, `plan.md`, `tasks.md`, `version.md` to reflect unified architecture.

## Verification

- Backend compilation verified with Java 25 (`release 21`).
- Frontend unit tests passing: 31/31 tests in `src/features/designreview`.
- Session lifecycle router tests passing (29/29).
