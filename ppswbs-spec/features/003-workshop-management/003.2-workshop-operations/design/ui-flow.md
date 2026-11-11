# UI Flow Design: 003.2 Workshop Operations

**Date created**: 2026-11-02
**Date updated**: 2026-11-02
**Author**: Hồ Hoàng Quốc
**Last updated by**: Hồ Hoàng Quốc

## Scope and sources

- Business rules: `spec.md` (packages, locations, slots, sessions, capacity, attendance, check-in, cancellation handoff).
- Transport contract: `contracts/workshop-operations.openapi.yaml`.
- Current implementation reference: `ppswbs_frontend/src/workshop-operations/`.
- This pack describes internal operational UI; it does not grant authorization to the client.

## Actors, routes, and authorization

| Actor | Route | Access rule |
| --- | --- | --- |
| Manager | `/operations/sessions` | Global Feature 003 scope; can manage packages, locations, slots, sessions, assignments, attendance and handoff. |
| Staff | `/operations/sessions` | Only sessions and bookings within the Staff location scope. |
| Guest/Member | Public package/session discovery | Read-only data exposed by the public discovery contract; no operational actions. |

## Screen and interaction flows

| Screen / flow | Workflow/API reference | Required states and recovery |
| --- | --- | --- |
| Package and lifecycle management | `/workshop-packages`, `/workshop-packages/{packageId}`, lifecycle transitions | Loading; empty list; validation; lifecycle conflict; preserve draft fields after server rejection. |
| Location/slot management | `/workshop-locations`, `/workshop-locations/{locationId}/slots`, slot patch | Manager global access; Staff cannot administer catalog; reject inactive location or overlapping slot; show conflict without silently rewriting times. |
| Session calendar/list | `/workshop-sessions`, bulk create, session GET/PATCH | Filter by date/location/state; show capacity and source revision; stale update returns conflict and reload action. |
| Session state actions (backfilled 2026-11-12, 0a21bcb, c0f550d) | `/workshop-sessions/{sessionId}/state-transitions`, `/attendance-finalizations` | Buttons for transitions the lifecycle does not allow from the current state are greyed out. There is no `ATTENDANCE_FINALIZED` state button; "Finalize attendance" uses the dedicated action and is enabled only while the session is `IN_PROGRESS`. |
| Staff assignment | `/workshop-sessions/{sessionId}/staff-assignments` | Manager assigns/removes; Staff sees only allowed scope; unauthorized actions remain disabled but backend response is shown if attempted. |
| Roster and booking lookup | `/workshop-sessions/{sessionId}/booking-lookups/{bookingCode}` | Informational roster only; do not infer check-in from presence in the list. |
| Check-in and override | `/check-ins`, `/check-in-overrides` | Require participant/ticket proof; duplicate or out-of-window check-in is a typed error; no offline or automatic check-in. |
| Ticket resend | `/ticket-resends` | Show idempotency/rate-limit result; do not expose recipient PII or provider payload. |
| Attendance finalization | `/attendance-finalizations` | Show a confirmation summary and immutable evidence reference; after all sessions are finalized, attendance is locked and no ordinary edit is offered. |
| Attendance correction/reversal | `/attendance-corrections`, `/check-in-reversals` | Only the explicitly authorized correction path is shown; require reason/evidence; never silently mutate a finalized result. |
| Cancellation handoff | `/cancellation-requests` | Create Operations handoff; show `PENDING/RESOLVED/CANCELED` outcome; no direct payment/refund action from Operations. |

## Invariants captured for regeneration

1. Manager scope is global across Feature 003; Staff scope is location-based.
2. Session identifiers are opaque UUID references; the UI never substitutes numeric database IDs.
3. No automatic no-show, no offline check-in, and no implicit check-in from roster display.
4. Capacity changes are server-validated and must not reduce below already committed/checked-in participants.
5. Attendance is locked after the finalized boundary; a correction requires the dedicated audited flow.
6. Operations cancellation handoff does not call VNPay refund and does not settle money.

## Session, data handling, and accessibility

- Clear stale session/list data after logout or authorization loss.
- Keep server revision/source version with editable rows to prevent stale overwrites.
- Do not put contact PII, ticket secrets, or credentials in query strings or logs.
- Calendar, table, and check-in controls must be keyboard navigable, labelled, and usable at the approved responsive breakpoints.

## Open decisions and dependencies

- **DRAFT**: exact Figma revision/node IDs and final copy for operational error banners.
- **Traceability gap**: map each screen to its existing `tasks.md` task and UI verification case before regeneration.
