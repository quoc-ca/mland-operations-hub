# Feature-012 Version and Decision Log

Date created: 2026-11-08  
Date updated: 2026-11-08  
Author: Ngô Tiến Đạt  
Last updated by: Ngô Tiến Đạt  

## 1. Current status

`SPEC/DESIGN READY FOR REVIEW — IMPLEMENTATION BLOCKED BY APPROVAL`

No production code, migration or runtime configuration is included in this
preparation package.

## 2. Scope decisions

| Decision | Chosen direction | Rationale |
| --- | --- | --- |
| Feature boundary | Separate Feature-012 under FE-05 | Configured design has its own data, version, validation and facade boundary. |
| Customer scope | Guest and Member | Existing Report 3 ring-configuration requirements allow both actors. |
| Target | Workshop ring only | Prevents collision with Feature-004 and custom manufacturing. |
| Entry point | After package, before branch/session | Package determines the available design options. |
| Booking cardinality | One design for one booking | Avoids participant-level design schema in V1. |
| Design source | Published model or blank design | Preserves existing model-selection and configurator paths. |
| Human review | Not required for rule-valid configuration | Server compatibility rules are sufficient for this path. |
| Estimate | Advisory only | Package price and deposit remain authoritative. |
| Branch check | Revalidate after branch selection | Branch support is not known at package-only entry. |

## 3. Persistence and version decisions

| Decision | Chosen direction |
| --- | --- |
| Guest persistence | Opaque server-session reference. |
| Guest expiry | 20 minutes of inactivity. |
| Member persistence | Current design plus version history. |
| Version trigger | Each successful explicit Save/Evaluate. |
| Unsaved edits | Local active-form state only. |
| Archive | Soft-delete/archive only. |
| Hard delete | Not allowed for design history. |
| Booking submit | Locks the attached design version. |
| Booking failure | Keeps the validated design available for retry. |

## 4. Authority decisions

- Manager owns business rules, component/material availability, price inputs and
  difficulty rules.
- Backend owns option validation, compatibility, estimate, difficulty, ownership,
  version and attach authorization.
- Feature-003 owns booking, capacity, payment, ticket and booking lifecycle.
- Feature-004 owns AI analysis and must not automatically create a configured
  design.
- Admin does not receive routine business/financial authority through this
  feature.

## 5. Rejected approaches

- Merging the feature into Feature-004: rejected because parameter configuration
  does not require AI and Guest access differs from Feature-004.
- Merging all persistence into Feature-003: rejected because booking must consume
  a facade/snapshot instead of owning design repositories.
- Applying estimate to package/deposit automatically: rejected because current
  workshop payment rules use package price and the estimate is advisory.
- Hard-deleting drafts/history: rejected because version history and audit must
  survive normal user cleanup.
- Letting the client calculate compatibility or price: rejected because backend
  is the authority and catalogue/rule data is dynamic.

## 6. Contract decisions recorded for review

The following are implementation proposals, not yet approved baselines:

- Use opaque public `designId` references rather than database IDs.
- Use `Idempotency-Key` for write commands and `If-Match-Version` for versioned
  updates.
- Expose package-scoped options through a public read endpoint.
- Expose booking attachment through a one-way `WorkshopRingDesignFacade`.
- Store Guest session state in the Feature-012-owned MySQL logical table
  `guest_design_sessions`; use an opaque reference/digest, inactivity expiry and
  replay protection. This is not a new global identity/session system.

## 7. Remaining implementation gates

1. Approve physical schema and migration design after verifying the current
   repository/database baseline (`V102` is the current repository baseline).
2. Implement and verify the Guest session store, cleanup and replay mechanics.
3. Provide the Manager-owned catalogue/rule facade and configured option data.
4. Approve and implement the Feature-003 attachment/snapshot facade.
5. Obtain formal Tech Lead approval with revision, scope and date.

## 8. Change log

### 2026-11-08 — Initial preparation and blocker closure

- Created research and draft specification.
- Recorded Guest/Member workshop-only configuration scope.
- Recorded package-first entry and branch revalidation.
- Recorded version, archive, session and booking attachment decisions.
- Confirmed row-per-version `ring_designs` with `source_design_id` lineage and
  no separate version table in V1.
- Confirmed Feature-012-owned MySQL Guest sessions with 20-minute inactivity
  expiry and three-year Member history retention.
- Confirmed synchronous `attachAndLockDesignForBooking(...)` transaction
  boundary and immutable booking snapshot ownership.
- Confirmed Manager-owned rule facade and config-driven evaluation; no fixed
  numerical formula is owned by Feature-012.
- Updated cross-feature ownership and flow documentation. No code, tests or
  migration was created.
