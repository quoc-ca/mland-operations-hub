# Feature-012 — Workshop Ring Design Configuration

**Date created**: 2026-11-01
**Date updated**: 2026-11-26
**Author**: Ngô Tiến Đạt
**Last updated by**: Phan Sơn

Status: `APPROVED — MEMBER-ONLY REBASELINE + ADMIN DIFFICULTY POLICY APPROVED (Ngô Tiến Đạt, 2026-11-26)`

## 1. Purpose and boundary

Feature-012 lets an authenticated, active Member configure and save one or more
workshop-ring designs from catalogue-controlled parameters. It supports
published models, blank designs, shape, material, ring size, gemstones,
components, attachments and engraving. The backend validates compatibility and
calculates advisory estimate/difficulty values through its own server-owned rule
boundary. Only an authorized Admin may configure and publish difficulty policy
values; Manager retains only separately authorized non-difficulty catalogue work.

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

## Clarifications

### Session 2026-11-26

- Q: How is a new Manager-added ring option shown before Admin publishes its difficulty score? → A: Visible but disabled for customer selection.
- Q: How long are superseded policy versions and publication audit retained? → A: Audit for at least five years; versions longer while referenced by a saved design.
- Q: What must remain true for an active published model after an Admin policy change? → A: Its complete default configuration remains valid, fully scored and below or at the too-risky cutoff; customer-edited variants are evaluated separately.
- Q: How is the Signature Silver 925 3g allowance treated? → A: 3g is included, not a hard design limit; excess mass is an advisory surcharge, not an automatic workshop charge.
- Q: How does the ring configurator assist members with ring sizing and laser engraving? → A: Sizing includes an interactive size guide popup with standard conversion table (sizes 6–29), measurement reference image (`/size-guide/bang-size.jpg`), and optional image reference upload; laser engraving offers quick preset symbols (🍀, ❤️, ☀️, 🌙, ⭐, ♾️) in addition to custom crop/upload.

## 2. Actors and permissions

| Action | Guest | Member | Staff | Manager | Admin |
|---|---|---|---|---|---|
| See entry/options/preview explanation | Allow | Allow | Allow | Allow | Allow |
| Start or change configuration | Deny; prompt sign-in | Own account | Deny as customer | Deny as customer | Deny |
| Create/save/evaluate version | Deny | Own designs | Deny | Deny | Deny |
| View design/history | Deny private data | Own designs only | Deny by default | Approved operational projection | Technical/audit projection |
| Archive/restore | Deny | Own unsubmitted design | Deny | Deny | Deny |
| View workshop recommendation | Public explanation | Own saved design | Deny | Business context | Deny |
| Change non-difficulty ring-catalogue fields | Deny | Deny | Deny unless separately granted | Allow where the catalogue contract grants it | Deny |
| Preview/publish difficulty policy | Deny | Deny | Deny | Deny | Allow for an active authorized Admin only |

Member identity and account status are checked by the backend on every protected
operation. The client cannot supply owner, role, price, difficulty or rule
version to obtain access.

## 3. Configuration flow

```text
Guest/Member sees design entry
  → Guest protected action prompts sign-in
  → Member selects a published model or a blank design, from general discovery or a compatible workshop page
  → selects parameters and sees local preview
  → Save/Evaluate with Idempotency-Key and If-Match-Version when applicable
  → server validates active options and any selected package context, then calculates an advisory result
  → Member sees saved version, summary and recommendation
```

Options are returned by the backend and are never hard-coded as authoritative
frontend data. The preview is local and clearly marked as a preview until the
server accepts the version. The general design catalogue includes all active
published models; a workshop page shows only models compatible with that
package. A general-catalogue design has no selected package until the Member
chooses one explicitly; a recommendation never silently selects it. The four
currently seeded Copper/Steel models remain in general discovery and lead to
consultation rather than an incompatible Silver workshop package.

## 4. Parameters and validation

The backend validates active options, package compatibility when a package was
explicitly selected, ring size,
dimensions, gemstone/component quantities, attachment placement, engraving
limits and image preprocessing bounds. The current UI rules include a maximum
aggregate of 10 gemstones, at most 2 text engravings, 2 symbol engravings and
2 custom laser images. Contrast and sharpness are each 0..100.

The server-owned evaluation result may contain safe validation reasons,
estimated price, currency, difficulty score, warnings, evaluation timestamp and
`rules_version`. Estimate and difficulty are advisory only; they do not change
workshop package price, payment or manufacturing quote. The Signature Silver
925 workshop includes 3g of silver. A heavier design remains valid and shows
the estimated excess-material amount; the workshop process confirms any amount
actually payable.

### 4.1 Admin-owned difficulty policy — proposed 2026-11-26

The configurable values are the difficulty contribution for each material,
shape, gemstone/component and attachment reference; the one-time standard
engraving coefficient; the per-image coefficient; the ordered difficulty-level
cutoffs; the Wax Casting difficulty cutoff; and the Silver Clay total-gemstone
cutoff. The additive formula, quantity multiplication, four-decimal `HALF_UP`
rounding and recommendation precedence are fixed by this feature, not editable
formula text. Ring size, dimensions, price, density and placement are outside
this difficulty policy.

An Admin reviews a before/after preview on representative, non-personal
designs, then confirms one atomic publication of a complete policy version.
Invalid values, an incomplete option-score set, stale publication version or
storage failure leave the previously effective version unchanged. The published
version is immutable and auditable with Admin actor, time and old/new values;
reverting means publishing another version. New catalogue options cannot become
evaluable until an Admin-supplied difficulty score is published. A newly active
option without a score remains visible in customer options with
`selectable: false` and `unavailableReason: DIFFICULTY_SCORE_PENDING`; the UI
disables selection and explains why. It cannot enter a new draft or evaluation.
Manager catalogue commands must not accept or overwrite difficulty fields.
An active published model is stronger than an ordinary selectable option:
its complete default material, shape, components and attachments must remain
active, compatible with its publication context, fully scored and at or below
the candidate too-risky cutoff. Preview reports every active model and its
before/after eligibility; publish
rejects the whole candidate with model-specific reasons if any active model
would fail. Model publication and catalogue option retirement apply the same
invariant against the effective policy; they cannot silently disable or hide an
already published model. Deliberate model retirement is a separate catalogue
action. Newly active unscored options may still appear disabled when no active
published model depends on them.

Preview always compares the current and candidate policy on server-owned
standard non-personal examples spanning a basic design, gemstone and engraving
cases, threshold boundaries and overlapping recommendation conditions. An Admin
may add non-personal examples; no saved Member design or identity is accepted.
If a valid catalogue example cannot realize a standard case, the response marks
that case unavailable rather than inventing a score. No publication proof is
issued if every standard example is unavailable. A preview with any blocking
model returns the impact table but no publication proof. An eligible preview
stores only an opaque proof reference, Admin account reference, digest of the
exact candidate, expected active and catalogue versions and 15-minute expiry.
It stores no example
contents or Member data. Publication requires matching, unexpired server-side
proof and revalidates the complete candidate and every active published model
within the publication transaction. A concurrent catalogue change invalidates
the proof and requires a new preview. Missing, expired or mismatched
proof cannot publish; a completed identical idempotent retry still replays its
original result after proof expiry.

Publication audit retains actor, time and old/new values for at least five
years under Report 3 DBR-36. An inactive policy version remains available while
any retained design references it, even beyond five years. Neither period is
borrowed from the three-year Member design-history rule.

Each successful new evaluation uses exactly one effective policy version.
Previously saved design versions retain their original score, level,
recommendation outcome and rules version; viewing them does not silently
re-evaluate under current policy. Older records with only score/version retain
those stored fields and receive a historical level/recommendation only when the
corresponding legacy policy and input are reconstructible. A score can exceed
10; no customer UI may append `/10`.

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

Recommendation is calculated on the server from the same effective difficulty
policy used for the saved evaluation, in this precedence:

1. Above the configured too-risky cutoff, return `TOO_RISKY` and no workshop
   recommendation. Explain the consultation/custom-design path without creating
   a request or hiding general workshop discovery.
2. Otherwise, above the configured Wax Casting difficulty cutoff, recommend Wax
   Casting only when the selected material is supported; explain its two
   sessions separated by at least two foundry working dates.
3. Otherwise, above the configured total-gemstone cutoff, recommend Silver Clay
   for the silver material family only. For a saved S925 design, explicitly
   disclose that the suggested Silver Clay workshop uses S999; the saved S925
   material does not change.
4. Otherwise use the approved material/category mapping for the applicable
   Signature workshop. If no compatible package exists, return no package
   recommendation and show consultation and general discovery.

The starting cutoffs correspond to the current observed behavior: level
boundaries `3/6/9/11`, Wax Casting `>9`, Silver Clay `>5` gemstones. They are
seed values for the proposed policy, not evidence of accuracy. Classification
and recommendation thresholds change only through Admin publication.

The recommendation is advisory. It does not force a package, hide other
workshops, create a booking, or mutate payment. The saved-design page displays
`Recommended`, a link to the suggested workshop detail, and the approved reason
text while keeping the general workshop discovery action visible.
The material-compatibility check applies before returning any suggested
package. Copper and Steel 316L currently have no approved compatible workshop
package and therefore receive no package recommendation at any score.

## 7. Member-only API baseline

```text
GET  /api/v1/workshop-design-options          # public general options, optional package filter
GET  /api/v1/workshop-designs                 # Member only
POST /api/v1/workshop-designs                 # Member only
GET  /api/v1/workshop-designs/{designId}      # owner/authorized projection
POST /api/v1/workshop-designs/{designId}/versions
POST /api/v1/workshop-designs/{designId}/evaluate
POST /api/v1/workshop-designs/{designId}/archive
POST /api/v1/workshop-designs/{designId}/restore
```

All mutations require `Idempotency-Key`; versioned mutations require
`If-Match-Version`. There is no current `attach-to-booking` command,
`X-Guest-Design-Session` or `X-Guest-Session` authorization path.

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
2. Active Member can create and save a model-based or blank design from general
   discovery or a compatible workshop page. Published-model evaluation includes
   every default component and attachment, not only material and shape.
3. Member can view only own designs and history; another Member receives a safe
   authorization error.
4. Invalid/inactive/incompatible options are rejected before version creation.
5. The 10-gemstone and engraving limits are enforced by frontend feedback and
   backend validation.
6. Save/Evaluate creates immutable versions and stale updates return `409`.
7. Archive preserves history and does not hard-delete rows.
8. Server-owned policy classifies and recommends in the precedence above,
   including no workshop recommendation beyond the too-risky cutoff; threshold
   equality follows the documented strict `>` rule.
9. Recommendation never hides other workshops or changes booking/payment state.
10. Feature-012 never creates or locks a booking/ticket design attachment.
11. Feature-004 can consume only an immutable Member-owned snapshot.
12. Active Admin can preview and publish a complete policy; Manager and all
    other actors cannot read its private administration API or mutate any
    difficulty value through catalogue commands.
13. A new policy affects only later evaluations. Historical design reads retain
    their prior score and version, and simultaneous/stale Admin publications
    cannot partially replace the effective policy.
14. Admin preview lists every active published model's before/after score and
    eligibility. Publish is rejected atomically when a model would lose a
    required score, become invalid or exceed the candidate too-risky cutoff;
    catalogue activation/retirement cannot bypass this guard.
15. A 3.5g Silver 925 design remains valid and shows an advisory excess-material
    amount. Copper/Steel designs remain scoreable without a false workshop
    recommendation; Silver Clay advice for S925 discloses S999.

## 12. Technical Projection for Admin & Audit (C7 Compliance)

> [!IMPORTANT]
> **Đồng bộ mã nguồn & Kiến trúc cho Agent/Lập trình viên tiếp theo:**
> - **Endpoint:** `GET /api/v1/workshop-designs/{designId}/technical-projection`
> - **Quyền truy cập (Authorized Roles):** `ADMIN`, `MANAGER` (Guest, Member, Staff: `403 FORBIDDEN` / `401 UNAUTHORIZED`).
> - **DTO phía Backend:** `WorkshopRingDesignTechnicalProjectionDto` (`edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.dto.WorkshopRingDesignTechnicalProjectionDto`).
> - **Nguyên lý Phân tách Trách nhiệm (Separation of Duties):**
>   - Admin có nhiệm vụ kiểm toán kỹ thuật, giám sát cấu hình nhẫn và đối soát quy tắc (rule version, độ khó difficulty score), nhưng **tuyệt đối không được can thiệp thương mại** hoặc xem thông tin cá nhân khách hàng.
>   - Do đó, Technical Projection **loại bỏ/mask** toàn bộ: giá ước tính thương mại (`estimatedPrice`, tiền tệ), thông tin người sở hữu (`ownerReference`, `ownerType`), và dữ liệu nhạy cảm khắc laser của khách (base64 hình ảnh hoặc text khắc riêng tư).
>   - Technical Projection **chỉ cung cấp** các thông số kỹ thuật phục vụ kiểm toán: `designId`, `packageReference`, `designVersion`, `designType`, `status`, `materialReference`, `shapeReference`, `ringSize`, `difficultyScore`, `rulesVersion`, `isLocked`, `createdAt`, `updatedAt`.
> - **Khi tích hợp Frontend Admin/Audit:** Khi phát triển màn hình kiểm toán mẫu nhẫn cho Admin, frontend gọi trực tiếp endpoint `GET /api/v1/workshop-designs/{designId}/technical-projection`, không gọi endpoint thương mại `/api/v1/workshop-designs/{designId}`.
