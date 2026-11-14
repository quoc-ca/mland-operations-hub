# Feature-012 Workflows — Workshop Ring Design Configuration

Date created: 2026-11-08
Date updated: 2026-11-09
Author: Ngô Tiến Đạt
Last updated by: Ngô Tiến Đạt

**Status:** Proposed workflow design; names are logical until API and code
approval.

## W1 — Load package-scoped options

1. Guest/Member enters configuration after selecting a published package.
2. UI calls `GET /workshop-design-options` with opaque package reference.
3. Controller validates package visibility and query shape.
4. Application service asks the package/catalogue boundary for active options.
5. Rules service returns option schema, compatibility metadata and rule version.
6. UI renders only returned options.

Failure: package unavailable, invalid or rules service unavailable returns a safe
typed error; no design mutation occurs. Retry is an explicit read retry.

## W2 — Create a blank or model-based draft

1. User selects `BLANK` or a published model.
2. Client sends `POST /workshop-designs` with package context and idempotency key.
3. Backend authenticates Member or resolves/creates the opaque Guest session.
4. Backend verifies model publication and package compatibility.
5. Service creates a private draft boundary.
6. Response returns an opaque design reference and safe current state.

Concurrent duplicate commands replay the same result for the same idempotency
key. A changed payload under the same key returns conflict.

## W3 — Save and evaluate a design version

1. User edits parameters locally.
2. User explicitly chooses Save/Evaluate.
3. Client sends the complete current configuration, `Idempotency-Key` and
   `If-Match-Version`.
4. Controller validates transport and rejects client price/difficulty fields.
5. Service verifies Guest session or Member ownership.
6. Rules boundary validates active options, package compatibility, cardinality,
   placement, size and engraving constraints.
7. Service calculates server-owned estimate/difficulty with `rules_version`.
8. One transaction creates the new immutable version and evaluation snapshot.
9. Response returns the latest version and safe summary.

Failure rolls back the version write. A stale version returns
`409 DESIGN_VERSION_CONFLICT`; the client keeps local input and reloads before
retrying with a new command key. An identical retry replays the original result.

## W4 — Revalidate after branch selection

1. Booking supplies package, branch and design version through the public facade.
2. Feature-012 verifies design ownership and version status.
3. Rules boundary checks branch material/component support.
4. If valid, the facade returns an immutable booking-safe snapshot.
5. If invalid, the facade returns `DESIGN_BRANCH_INCOMPATIBLE` with safe reasons.

No booking, capacity hold or payment mutation is performed by Feature-012.

## W5 — Attach design during booking retry

1. Feature-003 requests attachment before booking acceptance.
2. Feature-012 revalidates version, package, branch and ownership under the
   approved boundary.
3. Feature-003 atomically persists its own booking snapshot and booking facts.
4. On booking failure, the configured design remains valid and retryable.
5. On successful booking submission, Feature-012 marks the version attached and
   locked according to the approved cross-feature transaction/event boundary.

If the shared transaction cannot span modules, the implementation must use an
approved atomic command/event/outbox boundary; it must not claim success from a
partial attach.

## W6 — Guest session expiry

1. Each successful read or write touches `last_activity_at`.
2. A background cleanup or request-time check expires sessions inactive for 20
   minutes.
3. Expired references return `401 DESIGN_SESSION_EXPIRED`.
4. No design mutation or booking attachment is permitted after expiry.
5. The response does not reveal internal design existence or owner data.

## W7 — Member history and archive

1. Member requests own design history with Firebase identity.
2. Backend filters by authenticated ownership and returns safe version summaries.
3. Member archives only an unsubmitted design with idempotency/version headers.
4. Service records audit evidence and changes lifecycle state to `ARCHIVED`.
5. Historical versions remain retained; no hard-delete is performed.

## W8 — Failure, retry and concurrency policy

- Read failures preserve navigation context and provide explicit retry.
- Write retries reuse the original `Idempotency-Key`.
- Stale writes use `If-Match-Version` and return conflict without partial state.
- Rule/catalogue outage creates no version and preserves local form input.
- Booking failure does not delete the design.
- Late or duplicate attach commands return the existing safe result where the
  approved idempotency scope matches.
- Feature-004 responses never bypass these validation or ownership checks.

## W9 — Booking attachment boundary

The approved logical operation is `attachAndLockDesignForBooking(...)` with
`ConfiguredDesignBookingCommand`, `ConfiguredDesignBookingSnapshot` and
`ConfiguredDesignBookingOutcome` boundary types. It revalidates Guest session
or Member ownership, package/branch compatibility and `VALIDATED` design state.
It returns a server-built immutable snapshot to Feature-003 and locks the
design only when the surrounding booking transaction commits. Payment,
capacity-hold and ticket creation remain outside this operation. A rollback
leaves the design available for retry; a repeated `operationReference` returns
the prior outcome, while changed payload with the same reference is a conflict.

## W10 — Frontend live-preview interaction

1. The route loads the package reference and requests the package-scoped option
   schema.
2. The application hook normalizes the response into control descriptors and a
   local draft; the UI renders only those descriptors.
3. A material, gemstone, ring-size or band-width change updates local draft
   state and derives a new `PreviewModel` immediately.
4. `RingPreview` redraws from `PreviewModel` without a network request and does
   not change the authoritative design version.
5. Save/Evaluate serializes the complete normalized draft, creates one
   idempotency key for the command and sends the last `If-Match-Version`.
6. On success, the application layer replaces the authoritative design/version
   and evaluation snapshot, then marks the UI validated.
7. On validation, stale-version or rules-service failure, the UI keeps the
   local draft and displays the corresponding state from `ui-flow.md`.

The live preview is visual feedback, not a client-side compatibility or price
calculation. No API request is made for every slider movement unless a future
approved contract explicitly requires server preview.

## W11 — Frontend branch recovery

1. Booking supplies the selected branch context through the approved booking
   integration boundary.
2. The client calls the Feature-012 attach/revalidation adapter only when the
   current design version is available.
3. A compatible result proceeds to Booking; the Feature-012 UI does not create
   payment, capacity or ticket effects.
4. `DESIGN_BRANCH_INCOMPATIBLE` maps to the recovery state shown in Figma node
   `457:1795`.
5. The user may replace the unsupported option or return to branch selection.
   The saved design and safe local draft are retained.
6. Retrying the same attach command reuses its operation reference; changing
   the design creates a new version before a new attach attempt.
