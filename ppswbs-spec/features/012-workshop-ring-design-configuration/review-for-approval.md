# Final Revision Review — Feature-012 and FE-09

**Date created**: 2026-11-26
**Date updated**: 2026-11-26
**Author**: Phan Sơn
**Last updated by**: Phan Sơn

**Status:** PROPOSED FOR REVIEW. No Tech Lead approval is recorded.
**Intended reviewer:** Ngô Tiến Đạt.
**Candidate revision fingerprint:** `SHA-256 0fcb4666b9631b5226f42d2eef79f5a2f82f6bcad13775eb6c236865e9cdb246`.

## Decision requested

Review the complete Member-only Feature-012 and Admin-only FE-09 revision, not
only the Admin editor. The requested decision covers requirements, two design
entry modes, model eligibility, recommendation, customer/Admin contracts,
physical migration design, concurrency, written FE-09 visual baseline and
acceptance scenarios. If accepted, record the exact reviewed artifact revision,
approved scope, date and Tech Lead in each feature's canonical `approval.md`.
The historical 2026-11-09 Feature-012 implementation approval and 2026-11-22
customer visual approval do not authorize this revision.

| Topic | Proposed decision for review |
| --- | --- |
| Silver 925 allowance | Signature Silver 925 includes 3g. More than 3g is allowed; the excess is an advisory design estimate. Workshop operations confirm any payable amount. The old catalogue wording “maximum 4g” is corrected. |
| Published models | Both model-based and blank designs are supported. Every active published model's entire default configuration must remain valid, fully scored and at or below the too-risky cutoff under the effective policy. Customer edits are evaluated separately. Policy or catalogue publication rejects a change that breaks an active model; deliberate model retirement is separate. |
| General and package discovery | General catalogue includes all eligible active models. A package page shows only materially compatible models. Four Copper/Steel seed models remain published in general discovery; their erroneous Silver package association requires an append-only correction. General designs may have no package reference. |
| Workshop advice | Copper/Steel have no approved compatible workshop recommendation and retain consultation/general discovery. Silver-family advice may suggest Silver Clay S999 for an S925 design only with explicit S999 disclosure; the saved design remains S925. Wax Casting messaging explains two sessions separated by at least two foundry working dates. Advice never mutates booking or payment. |
| Admin publication | Only active Admin can publish a complete immutable policy after required standard and optional non-personal preview. Preview lists impact for every active published model. A blocking model yields no publication proof. The 15-minute proof binds Admin, candidate, active policy and catalogue revision; publication rechecks models in one transaction. |
| History and retention | New saves bind score, level and advice to one policy version. Old design results are not silently recalculated. Audit lasts at least five years; referenced policy versions remain longer. |

## Evidence and remaining approval gates

- Static review of V142 seed rows and the current additive formula found 12/12
  model defaults with complete referenced scores and a maximum of 4.7000. This
  does not prove runtime behavior; current model creation evaluates empty
  component lists, so fresh implementation tests must cover full defaults.
- The customer OpenAPI now describes public general/package-filtered reads and
  Member-only commands. The Admin OpenAPI exposes exhaustive model impact and
  proof-gated publication. Both are proposed and need Tech Lead contract review.
- `data-model.md` §8 proposes append-only physical tables, nullable package
  context, score widening and association correction. The applied database
  baseline is not verified here; no migration number or executable SQL is
  approved by this review memo.
- FE-09 now has a plan and canonical pending approval record. Its written UI
  baseline reuses the Admin shell without a new Figma frame and needs explicit
  Tech Lead acceptance.
- Feature-013 `spec.md` was rebaselined to Member-only on 2026-11-25, after its
  2026-11-18 approval. Its current consultation context boundary must be
  revalidated before the Feature-012 handoff is approved.
- Report 3 ring-design fragments and historical Feature-012 research/version/
  test evidence include package-first or retired Guest/booking descriptions.
  They are not implementation authority for this revision. Any Report 3
  fragment edit requires a separately named document task under
  `documents/AGENT.md`.

## Review package and verification map

Feature-012: `spec.md`, `plan.md`, `formula.md`, `data-model.md`, both OpenAPI
contracts, `design/architecture.md`, `design/workflows.md`, `design/ui-flow.md`,
`frontend-handoff.md`, `tasks.md` and `design/readiness-review.md`.
FE-09: `spec.md`, `plan.md`, its three `design/` files and `tasks.md`.
Shared boundaries: Feature-003 workshop catalogue, `ppswbs-spec/APIs.md` and
`ppswbs-spec/route-access-matrix.md`.
The fingerprint covers these 21 design/contract/source files (12 Feature-012,
6 FE-09, 3 shared), excluding this review memo and pending approval records.
It is SHA-256 over paths sorted by repository-relative POSIX path, with each
path's UTF-8 bytes, a NUL separator, raw file bytes and a final NUL separator.
Any edit to a covered file changes the candidate revision and requires a new
fingerprint before a decision is recorded.

The acceptance set is: all published-model defaults, missing-score and
too-risky publish rejection, concurrent catalogue change, explicit model
retirement, edited variants, 3.5g Silver advisory amount, Copper/Steel with no
false package, S925/S999 disclosure, old snapshot stability, Admin denial,
proof expiry/bypass, idempotent replay, stale publish, and keyboard/mobile UI.
Tests are specified for later execution; none are claimed to pass for the new
revision. `design/readiness-review.md` remains the gate ledger. Until the named
Tech Lead decision and remaining dependency/physical review evidence exist,
both features remain DRAFT/PROPOSED.
