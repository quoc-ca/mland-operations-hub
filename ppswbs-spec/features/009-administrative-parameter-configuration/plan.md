# FE-09 Plan — Admin Ring Difficulty Editor

**Date created**: 2026-11-26
**Date updated**: 2026-11-26
**Author**: Phan Sơn
**Last updated by**: Phan Sơn

**Status:** DRAFT/PROPOSED — Tech Lead review pending.

## Objective and ownership

FE-09 owns the protected Admin navigation and editor for the complete
Feature-012 difficulty policy. Feature-012 owns storage, validation, preview,
published-model eligibility, audit and the Admin REST contract. This release
covers ring difficulty only; checkout operational parameters remain separate.

## Delivery design

1. Use the existing Admin shell and shared identity/HTTP/i18n boundaries.
   Resolve active Admin role before mounting the screen; the API independently
   authorizes every read and command.
2. Read effective policy and immutable history. Keep edits in a local complete
   candidate and show missing newly active option scores, field errors and
   unsaved state without mutating the effective policy.
3. Preview required standard and optional non-personal examples. Show an
   exhaustive before/after table for active published models, including default
   gemstones and attachments, affected counts and safe blocking reasons. A
   blocking model disables publication but remains visible for correction.
4. Publish only with the exact candidate's fresh 15-minute server proof,
   expected active policy version and idempotency key. A changed catalogue
   revision, candidate, policy version or expired proof requires another
   preview. Stale or failed commands preserve local edits; identical completed
   retries reuse their original command key.
5. Restore historical values by copying them into a new local candidate,
   previewing and publishing a new immutable version. No historical row changes.

## Visual and contract gates

The proposed written visual baseline is `design/ui-flow.md`; it reuses the Admin
shell and is an explicit exception to adding a new Figma frame. The Admin API
is `../012-workshop-ring-design-configuration/contracts/admin-ring-difficulty.openapi.yaml`.
Ngô Tiến Đạt must review this exact FE-09 spec, plan, workflow, architecture,
written UI and task revision together with Feature-012. `approval.md` remains
pending until an actual decision records scope, paths/revision and date.

## Verification design

Test Admin/Manager/Member/inactive access, full-set validation, standard and
optional preview examples, all active models in the impact table, blocking
missing-score/too-risky model, changed catalogue revision, proof bypass/expiry,
duplicate and stale publish, history restoration, outage recovery, keyboard
navigation and narrow-width layouts. Fresh execution evidence belongs to the
testing phase; historical Feature-012 passes do not satisfy this gate.
