# Feature-012 Research — Workshop Ring Design Configuration

Date created: 2026-11-08
Date updated: 2026-11-09
Author: Ngô Tiến Đạt
Last updated by: Ngô Tiến Đạt

## 1. Research purpose

This document prepares a separate feature for configuring a workshop ring by
supported parameters. It records evidence, decisions, proposals and unresolved
gates before a data model, API contract or production code is approved.

The feature is intentionally separate from Feature-004 AI analysis and from
Feature-003 workshop booking. It belongs to the broader FE-05 Personalized
Design and Quote Workflow.

## 2. Sources inspected

| Source | Evidence used |
| --- | --- |
| `ppswbs-spec/AGENT.md` | Spec/design workflow, source-of-truth rules, module boundaries, approval gate and no-code boundary. |
| `ppswbs-spec/spec-general.md` | Workshop ring-design scope, role boundaries, payment boundary, AI/custom-manufacturing separation. |
| `ppswbs-spec/screen.md` | Package → design → branch → session → contact → summary → deposit flow and the Ring Options Configurator flow. |
| `ppswbs-spec/DB.md` | Proposed `ring_designs`, gemstone/attachment junctions, component snapshot, estimate and rules-version model. |
| Report 3 functional requirements | Existing screens for design path, ring configuration, components, attachments, engraving and parameter customization. |
| Report 3 workshop booking use case | Package-first booking, optional design path, branch/session validation and no partial booking on failure. |
| Feature-003 booking artifacts | Booking owns booking/session/capacity/payment and consumes cross-feature facts through a facade. |
| Feature-004 artifacts | Independent AI Design Request is Member-only and must not be converted automatically into a configured design. |
| `ppswbs-spec/features/README.md` | Feature registry; Feature-012 is now registered as draft/spec-design-ready. |

## 3. Confirmed facts

### 3.1 Business boundary

- The product supports workshop ring design as part of the workshop journey.
- A package is selected before the supported design path is finalized.
- The design path is completed before branch and session selection.
- The booking flow owns capacity, booking, payment, tickets and booking state.
- `ring_designs` is currently a proposed logical/physical model, not an
  implemented Feature-012 contract.
- `ring_designs.design_type` distinguishes `catalogue_model` and
  `configured_design` in the proposed DB registry.
- Gemstones and attachments require normalized junctions; component IDs alone
  do not prove compatibility.
- An estimate is informational and is not automatically a payable amount.
- A configured design is not a custom-manufacturing order.

### 3.2 Existing documented UI behavior

Report 3 describes screens for:

- Design path selection.
- Existing ring design selection.
- Ring configuration.
- Parameter customization.
- Ring components.
- Ring attachment.
- Ring engraving.
- Design summary and review before booking.

The current screen flow describes three ways to enter workshop customization:
an existing shop model, supported parameters, or image analysis. Feature-012
owns only the parameter/model configuration path. Image analysis remains owned
by Feature-004 when the separate AI flow is used.

## 4. Decisions recorded for Feature-012

These decisions were supplied and confirmed in the planning discussion:

1. The scope is workshop rings only.
2. Guest and Member may configure a workshop ring.
3. Entry occurs after package selection and before branch/session selection.
4. One configured design applies to the whole booking in V1.
5. Users may start from a published model or a blank design.
6. Supported parameters are shape, material, ring size, gemstone/component,
   attachment and engraving.
7. Available options come from backend catalogue/package context; the client
   must not hard-code compatibility.
8. Branch compatibility is revalidated after branch selection.
9. A valid parameter configuration does not require Staff/Manager review.
10. Estimate and difficulty are advisory only; package price and deposit remain
    unchanged.
11. Guest draft state uses an opaque server-session reference and expires after
    20 minutes of inactivity.
12. Member design state is persisted with current design and version history.
13. Each successful Save/Evaluate creates a new design version; unsaved local
    edits do not create a version.
14. Designs not attached to a booking use archive/soft-delete, not hard-delete.
15. A design is locked after booking submission.
16. A failed booking submission does not delete the validated design; it remains
    available for booking retry.
17. Manager owns business rules, material/component availability, price inputs
    and difficulty rules.

## 5. Boundary analysis

### Feature-003 Workshop Booking

Feature-003 owns package selection, branch/session selection, capacity holds,
booking creation, deposit/payment and ticket lifecycle. It may consume a safe
Feature-012 facade or snapshot, but it must not access Feature-012 repositories
or entities directly.

### Feature-004 AI Chatbox Analysis and Suggestion

Feature-004 owns Basic AI Support, image consent, temporary image processing,
candidate feature analysis and human review for independent requests. An AI
result is not automatically converted into a Feature-012 configured design.
Any future bridge must use an explicit facade and a Member-approved action.

### Custom manufacturing

Custom manufacturing is a separate Member-only order path with its own image
retention, queue, deposit, final amount and fulfilment rules. Feature-012 must
not accept, price, review or fulfil a custom-manufacturing order.

### Feature-011 Review Management

Feature-011 reviews workshop sessions and retail products. It does not own ring
configuration. A configured design may appear only as permitted booking context;
it is not a review target.

### FE-09 Administrative Parameter Configuration

FE-09 remains the operational administration boundary. Feature-012 consumes
approved business configuration; it does not grant Admin technical authority to
make routine business or financial decisions. Manager remains the business owner
for the rules listed in this feature.

## 6. Proposed flow

```text
Guest/Member selects package
→ loads package-scoped options
→ chooses published model or blank design
→ edits supported parameters
→ saves/evaluates current draft
→ server validates options and compatibility
→ server returns design version, validation, estimate and difficulty
→ user reviews Design Summary
→ user selects branch and session
→ server revalidates branch compatibility
→ booking receives an immutable design snapshot
→ booking submit either commits or leaves the design available for retry
```

## 7. Conflicts and gaps found

| ID | Finding | Impact | Treatment |
| --- | --- | --- | --- |
| R-01 | `ring_designs` is documented as a draft proposal, not an implemented schema. | Cannot create a migration or claim physical persistence is ready. | Define logical ownership now; keep physical mapping and migration pending approval. |
| R-02 | Existing docs use both package/branch compatibility and design options without one public option contract. | Frontend could display unsupported options. | Feature-012 API must return server-owned option and compatibility data. |
| R-03 | Existing design rows do not expose a settled version-number/lineage contract. | Save/evaluate history and optimistic locking are ambiguous. | Define logical `designVersion`; approve physical mapping before code. |
| R-04 | Guest server-session mechanism is not present as a shared contract. | A new global session policy must not be invented silently. | Define an opaque feature boundary and record the storage mechanism as a technical gate. |
| R-05 | Ring-size taxonomy, placement, quantity, engraving limits and numeric rules are explicitly TBD in `DB.md`. | Validation cannot be fully implemented from current docs. | Manager must approve catalogue/rule data before implementation. |
| R-06 | Booking facade attachment contract is not yet defined. | Booking cannot safely snapshot or lock a configured design. | Add a one-way facade contract and integration tests before code. |
| R-07 | Feature-012 had not been registered in `features/README.md` during the initial research. | Registry evidence was incomplete. | The feature is now registered as draft/spec-design-ready; implementation approval is still required. |

## 8. Remaining implementation gates

- Approve the physical mapping for configured-design versions.
- Implement and verify the server-session storage/cleanup while preserving the
  fixed 20-minute inactivity behavior.
- Implement the Manager-owned option/rule facade and provide active catalogue
  data for size, placement, quantity and engraving.
- Implement the approved Feature-003 attach/snapshot facade.
- Verify `flyway_schema_history` before selecting a future migration greater
  than `V102`.
- Obtain formal approval in `approval.md` with revision, scope, date and Tech
  Lead.

## 9. Research conclusion

The feature is suitable for a separate SPEC/DESIGN package. The business
boundary, logical data model and API boundary are sufficiently clear for review.
Physical schema, session implementation, rule-facade implementation, booking
facade implementation and formal approval remain implementation gates.

## 10. Blocker closure decisions — 2026-11-08

| Area | Decision | Consequence |
| --- | --- | --- |
| Version persistence | One immutable `ring_designs` row is one design version; `source_design_id` preserves lineage. | No separate version table in V1; physical sequence mapping still requires approval. |
| Guest persistence | Feature-012-owned MySQL `guest_design_sessions` with opaque reference/digest. | 20-minute inactivity expiry, explicit lifecycle state, cleanup and replay protection. |
| Booking integration | Synchronous `attachAndLockDesignForBooking(...)` in the Booking transaction. | Commit locks the design and stores an immutable snapshot; rollback preserves retryability. |
| Evaluation authority | Manager-owned catalogue/rule facade. | No duplicate catalogue tables or hard-coded numerical formula in Feature-012. |
| Retention | Member design history is retained for three years. | Archive is soft-delete; history is not hard-deleted. |
| Migration | Current repository baseline is `V102`. | Any future migration must be append-only and greater than `V102` after DB-history verification. |

These decisions move the package to `SPEC/DESIGN READY FOR REVIEW`; they do not
constitute implementation approval.

## 11. Figma evidence added — 2026-11-09

The existing Figma file now contains a Feature-012 section with the following
traceable frames: section `457:1553`, desktop configuration `457:1554`, mobile
configuration `457:1655`, model/blank selection `457:1698`, validation and
estimate `457:1749`, and branch recovery `457:1795`.

The design evidence confirms the intended presentation direction: generous
spacing, clear helper text beside controls, visible current values, a live
preview area and explicit recovery states. It does not approve new catalogue
values, backend fields, pricing formulas or business transitions. Figma revision
approval remains a separate gate.
