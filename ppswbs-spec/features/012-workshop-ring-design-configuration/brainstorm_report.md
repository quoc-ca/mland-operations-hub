# Brainstorm Report — Admin-owned Ring Difficulty Policy

**Date created**: 2026-11-26
**Date updated**: 2026-11-26
**Author**: Phan Sơn
**Last updated by**: Phan Sơn

**Status:** DRAFT/PROPOSED design record; not Tech Lead approval.

## Problem-first assessment

The underlying problem is that difficulty scores and decision thresholds are
dispersed between catalogue columns, backend literals and frontend conditions.
This makes calibration, access control and historical explanation unreliable.
An Admin editor is useful only if a published change is complete, traceable and
used consistently by evaluation, labels and recommendation. The six score
groups and thresholds are adjustable inputs; merely moving constants into a
form does not establish predictive accuracy.

## Confirmed requirements and exclusions

The user selected Admin-only authority for per-option scores, engraving
coefficients and classification/recommendation thresholds. Manager retains
non-difficulty catalogue operations but may not touch or approve difficulty.
Feature-012 owns policy storage and Admin API; FE-09 owns the Admin UI. Keep the
additive formula and open-ended score, remove `/10`, and order decisions as
too-risky → Wax Casting → Silver Clay → Signature. Admin previews non-personal
examples then atomically publishes an immediately effective immutable version.
Saved design versions retain old results; later evaluations use the new version.
Expert artisan ratings are the future comparison target, not new data
collection or a claim of improved accuracy in this release.

Out of scope: formula expression editing, normalization, scheduled activation,
mass historical recalculation, automatic calibration, Member-data impact
reporting, published-price control and Manager approval.

## Approaches considered

| Approach | Benefit | Cost/risk | Decision |
| --- | --- | --- | --- |
| Feature-012 owns immutable policy and Admin API; FE-09 owns UI | Evaluation and rules remain in one module; one audit/version boundary | Requires a new domain-specific policy store and Admin contract | Selected |
| FE-09 owns policy and Feature-012 reads through facade | Fits a central Admin-parameter theme | Splits ring-rule ownership across modules and adds a synchronous dependency | Rejected for this feature |
| Generic shared key/value parameters | Fast to expose arbitrary settings | Weak whole-policy validation, versioning and historical reproducibility | Rejected |

## Risks and design response

| Evidence/issue | Response in proposed design |
| --- | --- |
| `ManagerDesignRuleAdapter` reads catalogue `difficulty_score` and hardcodes `0.5000`, `0.3000`, version | Seed a full initial policy from current values; evaluator reads one immutable effective version. |
| Frontend classifies and recommends locally; `formula.md` condition order conflicts with its stated Wax priority | Move level/recommendation to server, use the user-selected precedence and version the output. |
| Existing customer UI prints `/10` while backend score can exceed 10 | Remove `/10`; retain open-ended additive score. |
| Manager catalogue edit could overwrite a difficulty field | Separate write DTO/authorization; a new unscored option stays visible but disabled until Admin publishes its score. |
| Admin could bypass a UI-only preview through a direct API call | Require 15-minute server-side proof bound to Admin, exact candidate and effective version; replay a completed identical command before proof revalidation. |
| Publication history could be deleted while an old saved design still refers to it | Retain audit for at least five years under DBR-36 and keep superseded policy versions while referenced. |
| Existing design read can recompute current fields beside persisted score | Persist future level/recommendation snapshots and avoid fabricating missing legacy outcomes. |
| Existing model creation scores only material/shape and ignores default gemstone/attachment inputs | Evaluate complete model defaults; preview all active models and block policy or catalogue publication that makes one unscored, invalid or too risky. |
| Four Copper/Steel models are seeded with a Silver package reference | Keep the models in general design discovery, correct their association through an append-only migration and show no incompatible workshop recommendation. |
| Catalogue states Signature Silver 925 as maximum 4g while the evaluator uses a 3g included threshold | Correct the catalogue to included 3g; show excess mass as advisory, with payable amount confirmed by workshop operations. |
| Current Feature-012 OpenAPI and implementation contain retired Guest/booking paths | Rebaseline the current customer contract and design pack as Member-only; retain historical files as provenance, not current commands. |
| Historical approval covers earlier scope | Require new named Tech Lead approval of the exact policy/contract/schema revision. |

## Success and verification

The release is successful when an active Admin can preview and publish a
complete policy after a verified preview, all other roles are denied, a stale/invalid or failed publish
leaves the previous policy effective, every new evaluation uses one version,
and historical results remain stable. Representative threshold-equality and
multi-condition examples must yield the declared precedence. Static artifact
trace is Feature-012 spec §4.1/§6 → data model §7 → Admin OpenAPI →
architecture §8/workflows W12–W14 → tasks T026–T034 → fresh scenarios.

Actual calibration quality requires later artisan ratings and an agreed
evaluation method; no accuracy percentage or time saving is claimed here.
