# Feature-012 Plan — Member Ring Design and Admin Difficulty Policy Rebaseline

**Date created**: 2026-11-26
**Date updated**: 2026-11-26
**Author**: Phan Sơn
**Last updated by**: Phan Sơn

**Status:** DRAFT/PROPOSED; requires Tech Lead contract, migration and visual approval.

## Objective and ownership

Feature-012 remains the single backend owner of saved Member designs, difficulty values, evaluation,
classification, recommendation and immutable policy versions. An active Admin
alone previews/publishes them through the proposed Admin API. FE-09 owns the
Admin UI. Manager retains separately authorized non-difficulty catalogue
fields and has no difficulty command or approval. The [spec](spec.md#41-admin-owned-difficulty-policy--proposed-2026-11-26),
[data model](data-model.md#7-proposed-difficulty-policy-model--2026-11-26),
[Admin OpenAPI](contracts/admin-ring-difficulty.openapi.yaml) and
[design pack](design/architecture.md#8-proposed-admin-difficulty-policy-architecture--2026-11-26)
own the observable rules, schema, transport and unit boundaries respectively.
This revision covers the entire Member-only Feature-012 rebaseline, including
general model discovery, package compatibility, saved-design recommendation
and consultation handoff. Signature Silver 925 includes 3g; any excess is shown
in the advisory design estimate, while workshop payment remains independent.

## Delivery order

1. Approve the Member-only flow, removal of Guest persistence and booking attach,
   general catalogue and two design starting modes, material-aware advisory
   recommendation, Silver 925 3g allowance and the Admin-only policy change.
   Approve the fixed additive formula, configurable values,
   threshold precedence, customer display without `/10`, historical snapshot
   behavior and new written Admin visual baseline as one exact revision.
2. Reconcile the current Flyway history (repository files currently reach
   `V203`) and design an append-only migration for immutable full policies,
   score entries, 15-minute preview-proof metadata, audit and future evaluation
   metadata, nullable package context for general design, and correction of
   Copper/Steel models currently assigned to a Silver package. Seed an initial policy
   from existing catalogue scores, `0.5000`/`0.3000` coefficients and
   `3/6/9/11`, Wax `9`, gemstones `5` thresholds; never update an applied
   migration or rewrite historical `ring_designs`.
3. Rebaseline the customer API and design pack to general or package-filtered
   options, Member-only commands and no booking attach. Return each active model
   with its complete default configuration and server-evaluated score/level;
   the model is never scored from material and shape alone. A general design has
   no selected package until the Member explicitly selects one.
4. Implement Feature-012 read/preview/publish with full-set validation,
   required standard and optional Admin examples, a server-verified proof for
   the exact candidate, active Admin authorization, optimistic expected version,
   idempotency, one transaction and safe failure recovery. Preview reports the
   before/after outcome of every active published model. The 15-minute proof
   binds the candidate, active policy and catalogue revision; publication
   revalidates all models inside its transaction and rejects any newly invalid,
   unscored or too-risky default. Catalogue model publication and option
   retirement enforce the same invariant. Exclude difficulty
   fields from Manager catalogue writes. Return a new active option as visible
   but disabled until Admin publishes its score only when no active model uses it.
5. Bind each Member Save/Evaluate to one effective policy version; persist
   score, level and recommendation outcome with the design version. Historical
   reads return stored fields and do not mix old score with new metadata.
   Return classification and recommendation from the server so frontend
   local thresholds cease to be authoritative.
6. Deliver FE-09 Admin UI after written visual review and API approval. Update
   customer displays, bilingual labels and the route/API inventory. Reconcile
   Feature-013's Member-only consultation contract with its newer spec before
   approving the handoff. Then
   specify and execute fresh unit, integration and E2E scenarios under the
   appropriate testing persona.

## Rollout and recovery

Migration must preserve old catalogue score columns for compatibility while
switching evaluation authority to the seeded policy only after the complete
version exists. An incomplete seed or unreadable active policy blocks new
evaluation safely; it cannot fall back to scattered literals. A failed
publication leaves the prior effective version. Operational rollback publishes
a new version copied from an earlier one. Deployment order is migration and
backend contract, then frontend consumption/Admin editor; no deployment or
runtime change is authorized by this document.

## Approval and verification gates

Report 3 DBR-36 supplies the five-year publication-audit retention floor; an
older policy remains while any retained design references it. Older research,
version history and some UI evidence retain Guest/booking descriptions as
historical context; current contracts and workflows must not implement them.
The physical schema, current applied migration baseline, Member-only
contract, written Admin screen baseline and Feature-013 consultation dependency
must be reviewed together. FE-09 needs its own plan and canonical approval.
Ngô Tiến Đạt is the intended reviewer, not an approver of this revision until
he records approval of exact artifact paths/revisions, scope and date in each
feature's approval record. Phan Sơn is responsible for these document updates.

Acceptance design must include: 12 seeded model defaults and all future active
models fully scored and at or below the cutoff; missing-score/too-risky policy
rejection; catalogue retirement guard; distinct edited-variant evaluation;
3.5g Silver advisory surcharge; Copper/Steel without false package advice;
Silver Clay S999 disclosure; historical score/version stability; concurrency,
idempotency and authorization. A static check of the 12 seed models found all
12 scoreable with a maximum default score of 4.7000 under current seed values;
this is not runtime verification. The current code and historical test results
are evidence of existing behavior only. Artisan ratings are a later
calibration input; no accuracy improvement is asserted by this release.
