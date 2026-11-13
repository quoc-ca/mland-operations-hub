# Feature Specification: Workshop Ring Design Configuration

Date created: 2026-11-08
Date updated: 2026-11-09
Author: Ngô Tiến Đạt
Last updated by: Ngô Tiến Đạt

**Feature:** 012 — Workshop Ring Design Configuration  
**Status:** SPEC/DESIGN READY FOR REVIEW — IMPLEMENTATION BLOCKED BY APPROVAL
**Parent business scope:** FE-05 Personalized Design and Quote Workflow  
**Source priority:** This feature contract, approved Feature-003 contracts,
`spec-general.md`, `DB.md`, and approved Report 3 requirements. Existing code or
visual prototypes are evidence only and do not override a documented decision.

## 1. Objective and boundary

Feature-012 lets a Guest or Member configure one workshop ring from approved
catalogue options after selecting a workshop package and before selecting a
branch/session. The feature validates the current configuration, returns an
advisory estimate/difficulty result, maintains design versions, and exposes a
safe snapshot for workshop booking.

The feature does not create a booking, hold capacity, initiate payment, issue a
ticket, publish a product, accept a custom-manufacturing order, or perform AI
analysis.

## 2. Actors and permissions

| Actor | Allowed actions | Conditions |
| --- | --- | --- |
| Guest | Browse options, create/edit/evaluate a draft, view its current summary, and pass a validated design to a booking attempt | Must hold the current opaque design-session reference; session expires after 20 minutes of inactivity. |
| Member | Browse options, create/edit/evaluate designs, view own current designs and version history, archive own unsubmitted designs, and attach an eligible design to a booking | Firebase identity is authenticated; backend verifies Member status and ownership. |
| Staff | No Feature-012 customer-design mutation or business-rule administration | May receive only data exposed through another approved operational workflow. |
| Manager | Owns approved business rules, option availability, component price inputs and difficulty rules | Management operations require the separate approved authority contract. |
| Admin | Technical/audit access only where separately granted | Does not make routine design, price or feasibility decisions. |
| Workshop Booking | Consumes validated design snapshot through the Feature-012 facade | Must not access Feature-012 repositories directly. |
| AI Provider | No direct permission | Feature-004 remains the owner of any AI call. |

Client-provided owner, role, price, difficulty, compatibility, status or
booking association is untrusted. All authorization and validation are repeated
server-side.

## 3. Design input and lifecycle

### 3.1 Entry

```text
Guest/Member
→ select published workshop package
→ open Workshop Ring Design Configuration
→ choose published model or blank design
```

The package is required because it determines the supported option set. Branch
and session are selected after the design draft, but branch compatibility is
rechecked before booking acceptance.

### 3.2 Supported parameters

The server supplies the current option schema and values for:

- Base shape.
- Base material.
- Ring size.
- Gemstone/component selections and quantities.
- Attachment selections, quantities and placement where supported.
- Engraving content and placement where supported.

The frontend renders these controls from the server-returned option schema. It
must not hard-code available materials, sizes, components, placements,
quantities or engraving limits. Each control shows its label, current value and
a short explanation of its effect on the visual preview. Sliders are used only
for numeric or ordered options; discrete catalogue choices use selectable cards,
pills or equivalent accessible controls.

The exact size taxonomy, placement vocabulary, quantity limits and engraving
content rules are catalogue/rule data and remain a pre-implementation gate.

### 3.3 Model-based and blank configuration

- A published catalogue model can be used as a starting point.
- A blank design starts from the package's server-supplied default option set.
- Selecting a model creates a new configured-design lineage; it does not edit the
  published catalogue model.
- A configured design is private to its Guest session or Member ownership scope.

### 3.4 Save and evaluate

Unsaved edits may remain local to the active form. A successful explicit
Save/Evaluate command:

1. Revalidates all current fields against the package and active catalogue.
2. Creates a new immutable logical design version.
3. Stores the selected component snapshot and rules version.
4. Calculates the server-owned estimate and difficulty result.
5. Returns validation messages and a safe design summary.

An invalid command creates no new version. A retry of the same command uses the
same `Idempotency-Key`.

## 4. Compatibility and validation

Validation is ordered as follows:

```text
Transport
→ Guest session/Firebase identity
→ ownership and status
→ package existence/publication
→ option schema and field validation
→ active catalogue validation
→ package compatibility
→ component cardinality/placement rules
→ estimate/difficulty evaluation
→ persistence/version transition
```

The backend rejects:

- Missing required design fields.
- Inactive, unpublished or unknown options.
- Options not supported by the selected package.
- Invalid ring size, quantity, placement or engraving content.
- Client-supplied estimate, difficulty or rules version that does not match the
  server result.
- A stale design version.
- A design belonging to another Member or Guest session.

After branch selection, the backend revalidates the design against branch
material/component support. Failure returns a safe compatibility error and keeps
the design available for correction.

## 5. Estimate and difficulty

Estimate and difficulty are derived server-side from the approved Manager-owned
catalogue/rule set. They are advisory context only:

- They do not change the workshop package price.
- They do not change the 50% package deposit.
- They are not an invoice, quote acceptance or final manufacturing amount.
- They are stored with the evaluated design version and `rules_version`.
- Historical versions retain their evaluation snapshot when rules later change.

No AI provider is called by Feature-012 to calculate these values.

## 6. Version and status rules

The logical lifecycle is:

```text
DRAFT
  → VALIDATED
  → ATTACHED_TO_BOOKING
  → LOCKED

DRAFT/VALIDATED → ARCHIVED
```

Rules:

- Each successful Save/Evaluate creates a new design version.
- Older versions remain readable in Member history and audit projections.
- Only the latest unarchived version may be edited.
- A version attached to a booking cannot be edited or archived through the
  customer flow.
- Booking submission locks the attached version.
- A failed booking submission does not invalidate or delete the design.
- Archive is a soft-delete operation; normal feature actions never hard-delete
  design history.

The physical status vocabulary and version-column mapping must be reconciled with
the existing `DB.md` proposal before migration or implementation.

## 7. Guest session and Member persistence

### Guest

- The server issues an opaque design-session reference.
- The reference contains no raw email, phone, database ID or design payload.
- The session is scoped to the Guest flow and expires after 20 minutes of
  inactivity.
- Expiry removes the ability to mutate or attach the draft.
- The expired session response must not reveal whether a particular internal
  design row existed.

### Member

- Firebase proves identity; MySQL/backend account state proves authorization.
- Member may view only own designs and versions.
- Member history contains current design and prior version snapshots.
- Member may archive an unsubmitted design but cannot hard-delete its history.

The server-session storage mechanism is the Feature-012-owned MySQL logical
`guest_design_sessions` boundary. The frontend stores only the opaque session
reference required by the approved transport and never persists raw identity,
database IDs or design payloads in local storage. Session persistence and
cleanup remain backend responsibilities.

## 8. Booking attachment

Feature-003 calls the Feature-012 facade with a design reference/version and its
booking context. Feature-012:

1. Verifies ownership or Guest-session proof.
2. Verifies the design version is `VALIDATED` and not stale/archived.
3. Revalidates package and selected branch compatibility.
4. Returns a safe immutable design snapshot.

Feature-003 stores the snapshot as part of its approved booking boundary. Feature-
012 does not create booking, invoice, hold, payment or ticket records.

Attachment must be all-or-nothing from the booking use case's perspective. If
booking creation or payment preparation fails, the validated design remains
available for a new attempt and no partial booking side effect is claimed.

## 9. Permission matrix

| Action | Guest | Member | Staff | Manager | Admin |
| --- | --- | --- | --- | --- | --- |
| Read package-scoped options | Public package context | Public/package context | No customer mutation | Business context | Technical projection only if granted |
| Create draft | Own active Guest session | Own account | Deny | Deny as customer actor | Deny |
| Save/Evaluate | Own active Guest session | Own design | Deny | Deny as customer actor | Deny |
| View design/version | Own session only | Own designs only | Deny by default | Approved operational projection only | Technical/audit projection only |
| Archive unsubmitted design | Own active session where applicable | Own unsubmitted design | Deny | Deny | Deny |
| Attach to booking | Own active session | Own design | Deny | Deny as customer actor | Deny |
| Change business option/rule catalogue | Deny | Deny | Deny unless another approved feature grants catalogue input | Allow under Manager authority | Technical settings only |

## 10. Error, retry and concurrency behavior

| Condition | Result | Mutation/provider behavior |
| --- | --- | --- |
| Invalid schema/field | `400 DESIGN_INPUT_INVALID` | No version created. |
| Unknown/inactive option | `422 DESIGN_OPTION_INVALID` | No version created. |
| Package incompatibility | `422 DESIGN_PACKAGE_INCOMPATIBLE` | No version created. |
| Branch incompatibility | `422 DESIGN_BRANCH_INCOMPATIBLE` | No booking attachment. Design remains editable. |
| Guest session expired | `401 DESIGN_SESSION_EXPIRED` | No design mutation. |
| Wrong ownership | `403 DESIGN_ACCESS_DENIED` | No data disclosure. |
| Stale version | `409 DESIGN_VERSION_CONFLICT` | Reload/reconcile; no partial write. |
| Duplicate idempotent command | Existing result or `409 IDEMPOTENCY_CONFLICT` | No second version. |
| Option/rule service unavailable | `503 DESIGN_RULES_UNAVAILABLE` | Preserve local input; explicit retry. |
| Booking attach failure | Typed booking integration error | Design remains available; no payment/booking side effect from Feature-012. |

Write retries reuse the original `Idempotency-Key`; clients must not generate a
new key for the same user action. Versioned commands use `If-Match-Version`.

## 11. Security, privacy, audit and retention

- Never trust client-supplied owner, role, price, difficulty or compatibility.
- Do not expose database IDs, raw session values, private Member identity or
  internal audit fields in public DTOs.
- Do not store raw secrets or payment data in design records.
- Guest session data is subject to the 20-minute inactivity expiry and approved
  cleanup policy.
- Member design history is retained for three years under the approved
  business-history policy.
- Save, evaluate, archive, attach, denied access, stale conflict and rule
  failures produce minimized audit evidence with actor scope, action, outcome,
  version and correlation ID.
- The feature does not retain or process Feature-004 raw image input.

## 12. Explicit non-goals

- Retail product customization.
- Custom-manufacturing order creation, acceptance, pricing or fulfilment.
- AI image analysis or AI price suggestion.
- Workshop package price changes or payment calculation.
- Capacity, branch schedule, session creation or ticket issuance.
- Public sharing or marketplace publication of Member designs.
- Staff/Manager manual review of every valid configured design.
- Hard deletion of design history.
- A new global identity, JWT or session system.

## 13. Acceptance scenarios

1. Given a Guest has selected a published package, when valid parameters are
   saved, then the server creates a version bound to the active opaque session.
2. Given a Guest session is inactive for 20 minutes, when the Guest evaluates or
   attaches the design, then the server returns `401 DESIGN_SESSION_EXPIRED`.
3. Given a Member saves a design twice, then two versions exist and the earlier
   version remains readable in Member history.
4. Given a blank design omits a required field, then the command returns
   `400 DESIGN_INPUT_INVALID` and creates no version.
5. Given an option is inactive or unsupported by the package, then the server
   returns a typed `422` error before persistence.
6. Given the selected branch does not support the design, then attachment is
   rejected and the design remains available for correction.
7. Given a design is attached and booking submission succeeds, then the design
   version is locked and the booking stores the approved snapshot.
8. Given booking submission fails, then no partial booking/payment effect is
   created by Feature-012 and the design remains retryable.
9. Given two concurrent saves use the same version, only one succeeds; the other
   receives `409 DESIGN_VERSION_CONFLICT`.
10. Given a client sends a fake estimate or difficulty, the server ignores or
    rejects it and persists only server-derived values.
11. Given a Member archives an unsubmitted design, history remains auditable and
    no physical deletion occurs.
12. Given Feature-004 returns candidate features, no automatic Feature-012 design
    or booking mutation occurs.

## 14. Blocker closure addendum

The logical decisions below are now fixed for design review:

1. Each design version is one immutable `ring_designs` row; `source_design_id`
   records lineage and no separate version table is required in V1.
2. Guest sessions are Feature-012-owned MySQL records with opaque reference or
   digest, `ACTIVE/EXPIRED/REVOKED` state and 20-minute inactivity expiry.
3. Booking uses synchronous `attachAndLockDesignForBooking(...)`; the design
   locks only when the booking transaction commits and rollback leaves it
   retryable.
4. Evaluation is delegated to a Manager-owned catalogue/rule facade. Feature-012
   stores safe input/output snapshots and `rules_version`, but no hard-coded
   numerical pricing or difficulty formula.
5. Member design history is retained for three years; archive is soft-delete.

Implementation remains blocked only by physical schema/migration approval,
session-store implementation, rule-facade implementation, booking-facade
implementation and formal Tech Lead approval.

## 15. Frontend interaction contract from Figma

The approved-for-review visual baseline is a spacious two-column desktop
configurator and a stacked mobile configurator. The frontend must preserve these
observable behaviors:

- The preview updates immediately from local selected values; it must not wait
  for Save/Evaluate to redraw.
- Save/Evaluate remains the server-authoritative boundary. A local preview before
  validation is clearly labelled as preview and is not a validated design or
  payable price.
- Material, gemstone, ring size and band-width controls show the active
  selection/value. Helper text stays adjacent to the control.
- The estimate is labelled `Indicative estimate`/`Tham khảo` and keeps the
  warning that package price and deposit are unchanged.
- Branch incompatibility offers correction actions while preserving the saved
  design and safe unsaved form values.
- Guest and Member share the customer UI; backend responses determine ownership,
  session validity and authorization.
- Loading, empty, validation, unavailable-service, session-expired and retry
  states must be represented without exposing internal IDs or session values.

Figma is a visual/layout source only. It does not authorize new API fields,
catalogue values, pricing rules or business transitions.
