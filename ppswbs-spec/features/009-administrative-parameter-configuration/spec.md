# Feature-009 — Administrative Ring Difficulty Configuration

**Date created**: 2026-11-26
**Date updated**: 2026-11-26
**Author**: Phan Sơn
**Last updated by**: Phan Sơn

**Status:** DRAFT/PROPOSED — Feature-012 contract and Tech Lead approval pending.

## Purpose and boundary

An active Admin can review, preview and publish the Feature-012 workshop-ring
difficulty policy from one protected administration screen. Feature-012 owns the
policy store, validation, evaluation and Admin REST API. This feature owns the
Admin navigation and UI journey only; it must not create a second policy store
or infer authority from the `/admin` route prefix. Manager retains separately
authorized non-difficulty catalogue operations, with no permission to preview,
publish or approve difficulty values.

The policy covers per-option material, shape, gemstone and attachment scores;
the standard engraving and per-image coefficients; the four ordered difficulty
level cutoffs; the Wax Casting score cutoff; and the Silver Clay gemstone-count
cutoff. Formula structure, rounding, recommendation precedence, price, package
publication, size and geometry are outside this screen. The authoritative
formula and lifecycle are in [Feature-012 spec](../012-workshop-ring-design-configuration/spec.md#41-admin-owned-difficulty-policy--proposed-2026-11-26)
and [data model](../012-workshop-ring-design-configuration/data-model.md#7-proposed-difficulty-policy-model--2026-11-26).
This release covers ring difficulty only. The checkout operational parameters
also mentioned in Report 3 UC 62 remain a separate, unspecified delivery scope.

## Clarifications

### Session 2026-11-26

- Q: Does this FE-09 delivery also cover checkout operational parameters in UC 62? → A: No; ring difficulty only.
- Q: Which examples must Admin preview before publication? → A: Required standard non-personal examples plus optional Admin-selected non-personal examples.
- Q: May Admin publish directly through the API without previewing? → A: No; the API must verify a fresh preview of the exact candidate.
- Q: Can a policy change make an active published model unscored or too risky? → A: No; preview shows every affected model and publication rejects the entire candidate until all active model defaults remain eligible.

## Actors and access

| Action | Active Admin | Manager, Staff, Member, Carrier | Guest |
| --- | --- | --- | --- |
| Open `/admin/ring-difficulty` and read active/history | Allow | Mask to `/404` before page request | Sign-in recovery |
| Preview candidate on non-personal samples | Allow | Deny at API | Unauthorized |
| Publish complete candidate | Allow | Deny at API | Unauthorized |

An inactive or unresolved Admin account cannot load or publish. Browser masking
does not replace server-side authorization. The screen does not show Member
designs, individual orders, payment details or executive revenue data.

## User journey and acceptance

1. Admin opens the screen and sees the effective version, values and publication
   history. Loading, empty/unavailable and authorization states reveal no
   protected values before role/status resolution.
2. Admin edits a local candidate. The UI identifies invalid or missing fields
   without discarding unrelated edits. Newly active options awaiting a score
   are highlighted for completion. It does not publish per-field changes.
3. Admin previews before/after score, level and recommendation for required
   standard non-personal examples and may add non-personal examples. The preview
   also lists before/after scores and eligibility for every active published
   model using its full default configuration. A blocking model returns no
   publication proof. An eligible preview creates only a short-lived proof
   record, not a policy version or Member-data access.
4. Admin confirms publication. One idempotent command with the expected active
   version and a fresh server-issued preview reference atomically makes the
   complete candidate effective only if every active published model remains
   fully scored, valid and at or below the too-risky cutoff; it returns the
   new version and audit metadata. A failed or stale command leaves the prior
   version effective and preserves the form for correction/reload.
5. Admin can inspect past versions. Restoring old values requires a fresh preview
   and a new publication; history is never overwritten.

The new policy applies to later Feature-012 evaluations only. The screen does
not promise improved prediction accuracy: expert artisan ratings are the chosen
future comparison target, but gathering and calibrating those ratings is outside
this release. No Manager approval step is part of this Admin-only journey.

The UI requires a fresh successful preview of the exact local candidate before
enabling confirmation. The backend stores only an opaque preview reference,
Admin account reference, candidate digest, expected active version and expiry
for 15 minutes, plus the catalogue revision; it stores neither sample contents
nor Member data. Publication
must verify this server-side proof for the same Admin, candidate, still-active
version and unchanged catalogue, then independently validate the complete
candidate and all active models inside the transaction. Editing the form
or optional examples, a changed policy/catalogue version or expiry requires
another preview. A direct
publish without valid proof is rejected, while replay of a previously completed
identical idempotent command returns its original result.

## Contract, recovery and exclusions

The proposed [Admin OpenAPI](../012-workshop-ring-design-configuration/contracts/admin-ring-difficulty.openapi.yaml)
is owned by Feature-012. Inputs are complete policy values and optional safe
sample selections; the server supplies required standard examples. No request
accepts a client-provided actor or saved Member design ID. Server
validates positive option quantities, complete active-option coverage, distinct
option keys, non-negative four-decimal scores, strictly ordered level cutoffs,
Wax cutoff below too-risky cutoff, gemstone cutoff within the supported
aggregate limit, and every published model's default eligibility. `400`, `401`,
`403`, `409`, `422` and `503` preserve the candidate
form; after `409`, the Admin reloads the effective version before retrying with
a new command key and preview reference. A duplicate identical publish replays
its result even if its preview reference later expires.

No formula editor, scheduled activation, automatic mass recalculation, catalogue
price editor, executive dashboard, Member-data impact report, artisan-rating
collection or external provider integration is included.
