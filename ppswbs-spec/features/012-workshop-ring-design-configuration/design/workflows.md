# Feature-012 Workflows — Workshop Ring Design Configuration

**Date created**: 2026-11-08
**Date updated**: 2026-11-26
**Author**: Ngô Tiến Đạt
**Last updated by**: Phan Sơn

**Status:** 2026-11-22 workflow change and proposed 2026-11-26 Admin policy documented; names are logical until API and code approval.

The current workflow ends at `SAVED` design summary, advisory workshop
recommendation and optional Feature-013 consultation. The former booking
attachment and branch-revalidation workflows are historical and are not current
frontend or backend actions.

## W1 — Load general or package-filtered options

1. Guest or Member enters general design discovery or a published package page; Guest changes remain local only.
2. UI calls `GET /workshop-design-options`, optionally filtering by opaque package reference.
3. Controller validates an explicit package filter and query shape.
4. Application service asks the catalogue boundary for active options and models, filtering models by material/package compatibility when a package is present.
5. Rules service evaluates each published model's complete default material, shape, components and attachments under one policy version; it returns score/level for every active model in scope. An unexpected persisted invariant violation returns a safe unavailable error instead of a partial list.
6. UI renders server-returned options and models. A model cannot be hidden merely because a policy update omitted its score; publication of such a policy is rejected.

Failure: package unavailable, invalid or rules service unavailable returns a safe
typed error; no design mutation occurs. Retry is an explicit read retry.

## W2 — Create a blank or model-based draft

1. User selects `BLANK` or a published model.
2. Client sends `POST /workshop-designs` with an optional explicitly selected package context and idempotency key.
3. Backend authenticates the Member before any create command; a Guest attempting the command receives a sign-in response.
4. Backend verifies model publication, complete default eligibility and package compatibility when a package was selected.
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
5. Service verifies Member ownership.
6. Rules boundary validates active options, any explicitly selected package compatibility, cardinality,
   placement, size and engraving constraints.
7. Service calculates server-owned estimate/difficulty with `rules_version`.
8. One transaction creates the new immutable version and evaluation snapshot.
9. Response returns the latest version and safe summary.

Failure rolls back the version write. A stale version returns
`409 DESIGN_VERSION_CONFLICT`; the client keeps local input and reloads before
retrying with a new command key. An identical retry replays the original result.

## W4 — Saved-design recommendation

1. The customer saves a design and receives a `SAVED` owner-safe projection.
2. The server counts gemstones and reads the persisted difficulty score.
3. The server resolves a material-compatible semantic package recommendation from catalogue data. Copper/Steel yield no package; an S925 design may receive Silver Clay S999 advice only with explicit material disclosure.
4. The response includes recommendation code, package link/reference, reason,
   rule version, and the Wax Casting note about two sessions separated by at least two foundry working dates when applicable.
5. The customer may follow the recommendation, edit/archive the design, start
   consultation, or explore all workshops. No booking or payment mutation occurs.

## W5 — Consultation/reference handoff

1. Member selects `Tư vấn thiết kế` from a saved-design page.
2. Feature-012 verifies Member ownership.
3. Feature-012 returns a safe design snapshot to Feature-013 through the approved
   boundary; no raw session value, internal database ID or private data crosses it.
4. Feature-013 opens a new or existing consultation context without creating a
   booking, payment, approval or order mutation.

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
- Saved-design recommendation and consultation failures do not delete the design.
- Late or duplicate commands return the existing safe result where the approved
  idempotency scope matches.
- Feature-004 responses never bypass these validation or ownership checks.

## W9 — Booking attachment boundary

The former `attachAndLockDesignForBooking(...)` operation and its booking
snapshot types are retained only as historical context. The 2026-11-22
baseline removes booking attachment from Feature-012. Current consumers must
use the saved-design recommendation or the Feature-013 consultation handoff;
no booking, payment, ticket or capacity mutation is created by this feature.

## W10 — Frontend live-preview interaction

1. The route loads a general or package-filtered option schema; package context
   exists only when explicitly selected.
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

## W11 — Historical frontend branch recovery (retired)

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

## W12 — Admin policy read and preview (proposed 2026-11-26)

An active Admin reads the effective policy and immutable history through the
Feature-012 Admin controller/service. A preview submits a complete candidate
and optional non-personal examples. The service adds standard non-personal
examples spanning a basic design, gemstone/engraving cases, cutoff boundaries
and overlapping recommendation conditions. It validates the whole candidate,
then calculates old/new score, level and recommendation with the real evaluator.
It also evaluates every active published model from full defaults under both
policies and returns per-model score/eligibility and blocking reasons. An
unrealizable standard case is reported as unavailable, not fabricated. A
preview with any blocking model returns the impact table without publication
proof. An eligible preview stores only an opaque proof reference, Admin
reference, candidate digest, active and catalogue versions and 15-minute
expiry; no sample contents,
policy version or Member record is persisted. Manager, inactive Admin and other
actors are denied before private values are returned; store outage yields retry.

## W13 — Admin policy publication (proposed 2026-11-26)

After preview, Admin confirms a candidate with expected active version,
matching preview reference and one idempotency key. The policy service first
replays a completed identical command when present; otherwise it checks active
Admin, unexpired proof for the exact candidate/policy/catalogue revision and
candidate completeness. Inside the same transaction it re-evaluates all active
published model defaults and rejects the candidate if any is invalid, unscored
or too risky. One transaction records the complete
immutable version, minimized audit and effective-pointer switch. A stale
version, invalid proof or duplicate key with changed payload returns conflict.
Invalid data or persistence failure leaves the old version effective. Restoring a prior
configuration repeats this workflow as a new version; Manager has no approval
or write step.

## W14 — Customer evaluation across policy versions (proposed 2026-11-26)

At Save/Evaluate the service captures one active policy version, evaluates the
fixed additive formula and server-side precedence, then stores score, level,
recommendation and version in the new immutable design version. A concurrent
Admin publication cannot mix entries from two policies in one evaluation.
Viewing a historical design returns the persisted snapshot; it does not
re-evaluate using current values. Legacy rows without reconstructible outcome
metadata show only known stored fields. Rule-service failure creates no design
version and preserves the Member's local draft for retry. An active catalogue
option absent from the captured policy remains visible in option reads but
disabled with `DIFFICULTY_SCORE_PENDING`; it cannot enter a new saved version.
An active published model cannot reference it: catalogue publication and policy
publication reject that state instead of silently disabling the model.
