# Workflow Design: FE-09 Admin Ring Difficulty

**Date created**: 2026-11-26
**Date updated**: 2026-11-26
**Author**: Phan Sơn
**Last updated by**: Phan Sơn

**Status:** DRAFT/PROPOSED.

## WF-09-01 — Read policy and history

Active Admin enters `/admin/ring-difficulty`. The route guard resolves current
account role/status before mounting; the FE-09 hook calls the Feature-012 Admin
read APIs. Success displays one effective version and immutable history. A
technical auth/bootstrap failure shows a neutral retry state without exposing
cached policy; a signed-in non-Admin is masked to `/404` before page request.

## WF-09-02 — Preview candidate

Admin changes a local complete candidate and chooses Preview. The hook sends
that candidate, expected active version and optional non-personal samples to
Feature-012. The service includes required standard non-personal examples,
validates the full policy and computes old/new score, level and recommendation
using its evaluation boundary. It also evaluates the complete default inputs
of every active published model and returns each old/new score, eligibility and
blocking reason. A blocking model yields the comparison without publication
proof; an eligible preview returns an opaque 15-minute preview reference. The
UI keeps that reference only while the local
candidate, active policy version and catalogue revision match. Invalid candidates show field-level errors
without losing edits. Preview stores only proof metadata, never a policy version,
sample contents or Member records.

## WF-09-03 — Publish candidate

After successful preview, Admin confirms. The hook sends the candidate with
expected active version, preview reference and an `Idempotency-Key`. Feature-012
verifies active Admin and matching unexpired server-side preview proof, then
revalidates full coverage and constraints. Within the publication transaction,
it re-evaluates every active model from complete defaults and rejects the whole
candidate if any becomes invalid, unscored or too risky. One transaction records an
immutable new policy and audit and switches the effective version. Success
refreshes values/history. A stale version returns `409`; the UI retains edits
and requires reload/re-preview. An identical retry uses the original command
key even if the proof later expires; a changed payload uses a new key. Missing,
expired or mismatched proof requires a fresh preview. Validation, authorization
or storage failure never partially activates a candidate.

## WF-09-04 — Restore previous values

Admin selects a historical version as the local candidate, previews it against
the current version and publishes it through WF-09-03. No historical row is
edited or deleted. This is not an automatic rollback or scheduled activation.
