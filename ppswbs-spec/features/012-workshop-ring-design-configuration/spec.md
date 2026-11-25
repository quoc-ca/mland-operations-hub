# Feature-012 — Workshop Ring Design Configuration

Date created: 2026-11-01
Date updated: 2026-11-24
Author: Ngô Tiến Đạt
Last updated by: Ngô Tiến Đạt

Status: `MEMBER-ONLY REBASELINE — IMPLEMENTATION PLAN REQUIRED`

## 1. Purpose and boundary

Feature-012 lets an authenticated, active Member configure and save one or more
workshop-ring designs from catalogue-controlled parameters. It supports
published models, blank designs, shape, material, ring size, gemstones,
components, attachments and engraving. The backend validates compatibility and
calculates advisory estimate/difficulty values through the Manager-owned rule
facade.

Guest users may see the configurator entry point, catalogue options, preview
explanation and the sign-in CTA. A Guest may change parameters in a local,
unsaved preview, but may not create a draft, evaluate, save, archive, restore,
view a private design, or invoke a design API. The first protected interaction
opens sign-in and sends no protected command. No Guest design session is part of
the current contract. Local preview changes are discarded when the preview is
abandoned.

Feature-012 does not attach designs to workshop tickets or bookings. After a
Member saves a design, the system may show an advisory workshop recommendation;
all other workshop discovery actions remain visible. Feature-012 does not create
booking, payment, quote, custom order, or ticket records.

## 2. Actors and permissions

| Action | Guest | Member | Staff | Manager | Admin |
|---|---|---|---|---|---|
| See entry/options/preview explanation | Allow | Allow | Allow | Allow | Allow |
| Start or change configuration | Deny; prompt sign-in | Own account | Deny as customer | Deny as customer | Deny |
| Create/save/evaluate version | Deny | Own designs | Deny | Deny | Deny |
| View design/history | Deny private data | Own designs only | Deny by default | Approved operational projection | Technical/audit projection |
| Archive/restore | Deny | Own unsubmitted design | Deny | Deny | Deny |
| View workshop recommendation | Public explanation | Own saved design | Deny | Business context | Deny |
| Change catalogue/rules | Deny | Deny | Deny unless separately granted | Allow | Technical settings only |

Member identity and account status are checked by the backend on every protected
operation. The client cannot supply owner, role, price, difficulty or rule
version to obtain access.

## 3. Configuration flow

```text
Guest/Member sees design entry
  → Guest protected action prompts sign-in
  → Member selects model or blank design
  → selects parameters and sees local preview
  → Save/Evaluate with Idempotency-Key and If-Match-Version when applicable
  → server validates options/package rules and calculates advisory result
  → Member sees saved version, summary and recommendation
```

Options are returned by the backend and are never hard-coded as authoritative
frontend data. The preview is local and clearly marked as a preview until the
server accepts the version.

## 4. Parameters and validation

The backend validates active options, package compatibility, ring size,
dimensions, gemstone/component quantities, attachment placement, engraving
limits and image preprocessing bounds. The current UI rules include a maximum
aggregate of 10 gemstones, at most 2 text engravings, 2 symbol engravings and
2 custom laser images. Contrast and sharpness are each 0..100.

The server-owned evaluation result may contain safe validation reasons,
estimated price, currency, difficulty score, warnings, evaluation timestamp and
`rules_version`. Estimate and difficulty are advisory only; they do not change
workshop package price, payment or manufacturing quote.

## 5. Lifecycle and persistence

```text
DRAFT → SAVED → ARCHIVED
```

Each successful Save/Evaluate creates an immutable design-version row. Member
history retains prior versions for three years. Archive is soft-delete and no
history is hard-deleted. A saved design can be opened for editing by its Member
according to optimistic-version rules.

The former Guest session, booking attachment, `VALIDATED`, `LOCKED`, and
`ATTACHED_TO_BOOKING` paths are legacy-only and must not be created by the
current flow. Existing legacy rows may remain readable only through an approved
compatibility projection; they do not authorize new Guest actions.

## 6. Workshop recommendation

Recommendation is calculated from server-owned evaluation data after a Member
saves a design:

- more than five gemstones → recommend Silver Clay;
- difficulty above nine → recommend Wax Casting and show that it requires two
  sessions on two different days;
- if neither rule applies, use the approved material/category mapping for the
  applicable Signature workshop.

The recommendation is advisory. It does not force a package, hide other
workshops, create a booking, or mutate payment. The saved-design page displays
`Recommended`, a link to the suggested workshop detail, and the approved reason
text while keeping the general workshop discovery action visible.

## 7. Member-only API baseline

```text
GET  /api/v1/workshop-design-options          # public catalogue/options only
GET  /api/v1/workshop-designs                 # Member only
POST /api/v1/workshop-designs                 # Member only
GET  /api/v1/workshop-designs/{designId}      # owner/authorized projection
POST /api/v1/workshop-designs/{designId}/versions
POST /api/v1/workshop-designs/{designId}/evaluate
POST /api/v1/workshop-designs/{designId}/archive
POST /api/v1/workshop-designs/{designId}/restore
```

All mutations require `Idempotency-Key`; versioned mutations require
`If-Match-Version`. There is no current `attach-to-booking` command and no
There is no `X-Guest-Design-Session` or `X-Guest-Session` authorization path.

## 8. Security, privacy and integration

- Public option DTOs contain only catalogue data approved for browsing.
- Member DTOs contain only the Member's own design data.
- No raw internal ID, session value, private identity, payment data or audit
  evidence is exposed to a Guest.
- Feature-004 may consume an immutable Member-owned design snapshot through an
  approved facade; it cannot access Feature-012 repositories.
- Feature-013 consultation may receive a safe Member design context; this is not
  an approval or booking attachment.

## 9. Errors and concurrency

| Condition | Response |
|---|---|
| Missing/invalid Member identity | `401 DESIGN_MEMBER_AUTH_REQUIRED` |
| Authenticated non-Member/inactive account | `403 DESIGN_MEMBER_ACCESS_DENIED` |
| Invalid field/option | `400/422 DESIGN_INPUT_INVALID` or `DESIGN_OPTION_INVALID` |
| Incompatible package/rule | `422 DESIGN_PACKAGE_INCOMPATIBLE` |
| Wrong ownership | `403 DESIGN_ACCESS_DENIED` |
| Stale version | `409 DESIGN_VERSION_CONFLICT` |
| Duplicate command | Existing result or `409 IDEMPOTENCY_CONFLICT` |
| Rule service unavailable | `503 DESIGN_RULES_UNAVAILABLE` |

Retries reuse the same idempotency key and preserve the Member's local form
state. No unauthenticated retry may create or mutate a design.

## 10. Explicit non-goals

- Guest design persistence or Guest session storage. Local Guest preview changes
  are allowed only in memory and are not a design lifecycle state.
- Booking/ticket attachment or per-ticket design assignment.
- Retail product customization.
- AI image analysis or AI price suggestion.
- Custom-order creation, quote, payment or fulfilment.
- Direct catalogue-table mutation from this feature.
- Public sharing or marketplace publishing of Member designs.

## 11. Acceptance scenarios

1. Guest sees the design entry, can browse public options and can change local
   preview parameters, but the first configure/save/evaluate action opens sign-in
   and makes no protected API call.
2. Active Member can create and save a model-based or blank design.
3. Member can view only own designs and history; another Member receives a safe
   authorization error.
4. Invalid/inactive/incompatible options are rejected before version creation.
5. The 10-gemstone and engraving limits are enforced by frontend feedback and
   backend validation.
6. Save/Evaluate creates immutable versions and stale updates return `409`.
7. Archive preserves history and does not hard-delete rows.
8. Saved design with more than five gemstones recommends Silver Clay; difficulty
   above nine recommends Wax Casting with the two-session/two-day note.
9. Recommendation never hides other workshops or changes booking/payment state.
10. Feature-012 never creates or locks a booking/ticket design attachment.
11. Feature-004 can consume only an immutable Member-owned snapshot.
