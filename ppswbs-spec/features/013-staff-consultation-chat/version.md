# Feature-013 Version Log — Staff Consultation Chat

**Date created**: 2026-11-18  
**Date updated**: 2026-11-24
**Author**: Ngô Tiến Đạt  
**Last updated by**: Ngô Tiến Đạt

## Version 1.0 — Approved, Implemented and Verified

- Separate Feature-013 boundary for Member-to-Staff/Manager consultation chat.
- Member-only customer access; Guest chat remains out of scope.
- Staff is location-scoped; Manager has global access and assignment authority.
- REST + visible-tab polling; no WebSocket/SSE.
- Immutable NFC-normalized UTF-8 text, maximum 2,000 Unicode code points.
- Member rate limit, idempotency, optimistic locking and metadata-only audit logging.
- Created `V183__staff_consultation_chat.sql` and implemented backend/frontend chat flow.
- Existing Version 1.0 unit and E2E evidence remains the historical baseline.

## Version 1.1 — Sidebar and customer-side lifecycle update

- Added cursor pagination with default page size 20 and maximum 50.
- Excluded Member-hidden conversations from the Member sidebar while retaining them for Staff and Manager operational history.
- Allowed Member to create a general consultation without a design context.
- Added Member-only soft hide for `CLOSED` conversations and confirmation UI.
- Added `MEMBER_HIDE` audit action and migration `V184__consultation_member_soft_delete.sql`.
- Confirmed Staff can close conversations within active location scope and Manager can close globally.
- Added fixed-width loading, empty and loaded sidebar states.
- Added E2E scenarios for pagination, contextless chat, Member hide, Staff scope, Manager close and layout exceptions.

## Current Verification

## Version 1.2 — 2026-11-22 Guest and saved-design consultation extension

- Guest can start a contextless consultation or open one from an owner-visible
  Feature-012 design.
- Member keeps Firebase-authenticated ownership behavior.
- Staff and Manager can participate in the same conversation; Manager retains
  global operational authority and Staff remains location-scoped where required.
- The design context is safe-projection only and does not approve, quote, book,
  pay for, or mutate the design.
- Implementation, contract validation and E2E verification are pending.

**Status:** `DOCUMENTATION UPDATED — IMPLEMENTATION PENDING`

- Backend, frontend and E2E verification for Version 1.1 is pending.
- Flyway `V184` must be validated against `flyway_schema_history` before deployment.

## Status

`IMPLEMENTED — VERIFICATION PENDING FOR VERSION 1.1`

## Version 1.3 — 2026-11-24 frontend consistency and stable-scroll handoff

- Created editable Figma draft frames in page 498:2:
  - Member contextual consultation desktop `590:71`.
  - Member contextual consultation mobile `590:108`.
  - Staff/Manager consultation desktop `590:127`.
- The contextual and general consultation chats share one outer width,
  responsive behavior, header spacing, message padding and composer geometry.
- Loading, empty, error and loaded sidebar states must reserve identical width.
- Initial route entry and conversation loading stay at the top; polling,
  focus and visibility refreshes must not move the reader. Explicit successful
  send is the only normal action allowed to reveal the newest message.
- Added E2E requirements for Member, Staff and Manager covering layout parity,
  stable scroll position, polling, send/retry, closed state, permissions,
  pagination, empty/loading width and Vietnamese text rendering.

**Status:** `FIGMA DRAFT PENDING USER APPROVAL — CODE NOT CHANGED`
