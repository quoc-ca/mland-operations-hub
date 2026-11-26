# Route access and 404 masking matrix

**Date created**: 2026-11-20
**Date updated**: 2026-11-26
**Author**: Phan Sơn
**Last updated by**: Hồ Hoàng Quốc

**Status:** Decision recorded from the product-direction session on 2026-11-20. This document is the shared browser-route inventory; a feature remains authoritative for its server-side action and resource authorization.

## Shared handling

| Browser state | Required result |
| --- | --- |
| Guest opens a route that requires a signed-in Account | Use the existing `/login` recovery; this is not a masked route. |
| Auth is loading or unconfigured | Render the existing neutral/non-PII loading or unavailable state; do not redirect. |
| Signed-in actor's Account bootstrap cannot resolve role/status because of a technical failure | Keep the current URL, do not mount the protected page, and render a neutral unavailable state with explicit retry. Do not use cached role data or turn this condition into `/404`. |
| Signed-in actor opens an internal route outside the route's allowlist | `replace('/404')`. Do not render the protected page, start its page-specific request, retain the attempted URL in navigation state, or show an access-denied reason. |
| A server command/resource request is refused after a permitted route opened | The owning feature handles the typed API outcome. It remains `403` unless that feature's approved contract specifically defines non-disclosure `404`; browser masking must not rewrite an API status. |

The allowlist is exact and role groups are not inherited: `ADMIN` is not a Manager or Staff superuser, `MANAGER` is not a Staff role, and `CARRIER` is not a Member role. A route may name more than one role only when its owning feature explicitly grants the same capability. The backend decides the current role/status/ownership/scope for every request independently of this browser guard.

## Registered internal routes

| Route family | Allowed browser role(s) | Owning feature | Notes |
| --- | --- | --- | --- |
| `/admin/accounts`, `/admin/roles` | ADMIN | 001 | Account lifecycle and role catalogue only. |
| `/admin/ring-difficulty` | ADMIN | 009 UI / 012 policy API | Proposed Admin-only policy editor. Manager is masked before mount/request; the backend independently requires active Admin. |
| `/admin/notifications/*` | ADMIN, MANAGER | 007 | Explicit shared notification-management capability; the `/admin` prefix is historical and is not an inference rule. A later route rename requires its own approved compatibility plan. |
| `/carrier`, `/carrier/stats` | CARRIER | 008 | CARRIER may also use public routes and the explicitly allowed password self-service route. It has no Member commerce/history capability. |
| `/staff/home` | STAFF | 014 | Staff home; the Manager home is `/manager/home` (owner decision 2026-11-26). |
| `/staff/retail-*` | STAFF | 008 | Manager access requires an explicit future FE-008 rule; it is not inherited from Manager's global Workshop authority. |
| `/manager/retail-payment-reconciliations`, `/manager/home`, `/manager/dashboard` | MANAGER | 008, 013, 010 | Admin parameter access never grants these Manager routes. |
| `/catalogue-management/*` | STAFF, MANAGER | 002 | Shared catalogue-work capability; each write is still action-authorized by FE-002. |
| `/promotion-management/*` | MANAGER | 006 | Admin has no promotion-management capability. |
| `/operations/sessions*`, `/operations/packages`, `/operations/package-add-ons` | STAFF, MANAGER | 003.2 | Staff remains location/session-scoped at the server; Manager authority is only for actions FE-003.2 explicitly grants. |
| `/operations/admin`, `/operations/locations`, `/operations/slots`, `/operations/package-availability`, `/operations/late-payments`, `/operations/audit` | MANAGER | 003.2 / 003.1 | These are Manager operational-administration/review routes; they are not made Staff-visible by their shared `/operations` prefix. |
| `/settlement*` | STAFF, MANAGER | 003.3 | Each page/action retains the feature's Staff assignment and Manager decision checks. |
| `/design-review-requests*`, `/price-proposals/*/ai-suggestions` | STAFF, MANAGER | 004 | Reviewer authority and resource scope stay server-owned. |
| `/review-moderation*` | STAFF, MANAGER | 011 | Moderation authority stays server-owned. |
| `/operations/consultations` | STAFF, MANAGER | 013 Staff Consultation Chat | Staff scope and Manager global authority are server-owned. |

## Self-service and public boundaries

Feature-012 proposes public `/design/rings` and `/design/rings/models` discovery,
with optional compatible-package entry under `/workshops/:packageReference/design`.
Guests may browse and change a local preview, but no protected design command
is sent before Member sign-in. `/design/rings/:designId` and `/my/designs` are
active Member owner routes; other signed-in roles are masked before mount and
the backend enforces ownership independently. A package route does not imply
that every published material is supported by that package.

FE-001 self-service is explicit: `MEMBER`, `STAFF`, `MANAGER`, and `ADMIN` may open Profile, Address Book, Email and Password canonical routes and aliases; `CARRIER` may open only `/security/password` among those routes. `CARRIER` may use public journeys but is masked from Member commerce/history routes such as `/member/cart`, `/member/checkout`, `/member/orders`, `/me/loyalty`, `/me/saved-vouchers`, and Member design history. An Account in `SUSPENDED` or `PENDING_SETUP` state is masked from every internal route with `/404`. A Member is masked from every registered internal route above. Any route absent from this inventory must be classified by its owner before implementation; a URL prefix alone is insufficient evidence of access.

## Required documentation verification

For each registered route, the owning feature must document its allowlist and any action/resource-level exception. Documentation verification covers direct navigation by every signed-in role, Guest login recovery, no protected-page request before `/404`, and server-side denial after a role/status/scope change. This matrix adds no REST endpoint, OpenAPI status change, schema change, or API-level role implication.
