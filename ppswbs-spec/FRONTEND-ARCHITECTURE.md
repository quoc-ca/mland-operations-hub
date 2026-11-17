# Frontend Architecture Baseline

**Status:** Proposed; pending Project Owner ratification in the project Constitution.
**Platform:** React + Vite, client-side rendering, JavaScript/JSX, REST APIs.

This document defines the target production frontend structure. It does not
turn an existing prototype into production evidence and does not replace a
feature's approved requirements, UI design, API contract, or acceptance criteria.

## 1. Target structure

```text
ppswbs_frontend/src/
  main.jsx                         # mount only
  app/
    App.jsx                        # composition root
    router.jsx                     # route composition
    providers/                     # app-wide providers when needed
    layouts/                       # app-wide shells/layouts
    config/                        # runtime configuration
  shared/
    ui/                            # technical UI primitives
    http/                          # transport, typed errors, correlation
    auth/                          # Firebase identity/session adapter
    i18n/                          # localization foundation
    lib/                           # business-agnostic utilities
    types/                         # genuinely shared technical types
  entities/                        # optional shared read/view primitives
  widgets/                         # optional cross-feature compositions
  features/
    <capability>/
      ui/                          # pages and feature components
      application/                 # use-case orchestration and hooks
      domain/                      # pure rules and state transitions
      infrastructure/              # API adapters and DTO mapping
      routes/                      # feature-owned route declarations
      tests/                       # feature tests
```

The `entities/` and `widgets/` folders are optional. They MUST NOT be created
just to satisfy the template. A feature may omit any layer it does not need.

## 2. Responsibilities and dependency rules

| Area | Owns | Must not own |
| --- | --- | --- |
| `app` | Providers, router, layouts, composition | Feature business rules or endpoint details |
| `shared` | HTTP/auth/i18n/UI primitives and technical utilities | Business endpoints, feature policy, mutable feature state |
| `features/<capability>/ui` | Rendering, user input, presentation state | Direct `fetch`, Firebase calls, authorization decisions |
| `application` | Feature use-case orchestration, request lifecycle, local feature state | Raw DOM concerns or backend entity reuse |
| `domain` | Pure business rules, validation, state transitions | React, router, HTTP, Firebase, browser APIs |
| `infrastructure` | REST/Firebase adapters, DTO mapping, transport integration | Rendering or product policy hidden in API helpers |
| `entities` | Shared stable view/read primitives | Feature-owned workflows or write authority |
| `widgets` | Cross-feature visual composition | Cross-feature business ownership |

The permitted direction is:

```text
app
  -> feature routes/ui
    -> feature application
      -> feature domain
      -> feature infrastructure
        -> shared http/auth
```

`shared` never imports a feature. A feature never imports another feature's
private code, mutable state, page, hook, or infrastructure adapter. Cross-feature
work uses a public port or the owning REST API boundary.

## 3. Feature boundary and naming

Feature boundaries follow a user capability or journey, not a backend package
name and not an individual screen. A feature may correspond to one backend
module, several backend contracts, or one workflow crossing multiple read
boundaries; ownership must be explicit in its design artifact.

Use lowercase kebab-case directory names for capabilities, for example:
`product-management`, `loyalty-promotion`, and `account-management`.

Use names that describe intent:

- `ProductListPage`, `useProductList`, `validateProductDraft`.
- `CatalogueApiClient` for a transport adapter.
- `ProductViewModel` for UI-facing mapped data.
- `ProductRules` or named pure functions for domain policy.

Avoid generic names such as `helpers.js`, `commonService.js`, or `utils.js` for
business behavior. `services/` is not a default architectural layer; a service
must be placed in `application/` or `infrastructure/` according to its role.

## 4. API, auth, and state

UI components invoke application use-cases. Application code uses a feature
port or infrastructure adapter. Infrastructure code calls the shared HTTP
client and maps server DTOs into feature view/domain inputs. The shared HTTP
client handles method, URL construction, auth transport, correlation IDs,
idempotency keys, safe error envelopes, and one approved token-refresh path; it
does not know business routes.

The backend remains authoritative for authorization, ownership, business state,
pricing, availability, and concurrency. Route guards and hidden/disabled UI
controls are usability aids only.

State is local to the feature by default. Context is reserved for authenticated
session/account, i18n, and app configuration. Do not introduce a global store or
server cache by default. Any exception must define owner, freshness,
invalidation, isolation, expiry, restart behavior, and recovery.

## 5. Screen and error-state rules

Each feature use-case design must identify the applicable states:

- initial/loading;
- empty/no-result;
- client validation;
- pending/disabled duplicate-submit protection;
- committed success;
- forbidden/unauthenticated;
- typed business/API failure;
- dependency/network unavailable and explicit retry;
- stale/conflict recovery when versioned data is involved.

Pages compose UI and application use-cases; they do not invent missing business
behavior, routes, payloads, auth policy, or error semantics.

## 6. Testing and evidence

Each feature owns tests for its domain rules, application state transitions,
infrastructure request/DTO mapping, and applicable UI states. Shared HTTP/auth
tests cover transport and recovery once; feature tests must not duplicate the
whole transport implementation.

`npm run build` is required for a frontend handoff. `npm test` must cover the
changed feature scope. A mocked fetch proves client behavior only; it is not
evidence of a live backend integration. Live integration evidence must identify
the backend, environment, route, auth/session state, and result.

## 7. Feature design checklist before implementation

- User capability/journey and actor are named.
- Scope and ownership boundary are explicit.
- Routes and navigation are defined.
- API contract, DTOs, errors, authorization, and source of truth are referenced.
- Feature layers and public exports are listed; unnecessary layers are omitted.
- Loading, empty, validation, success, forbidden, failure, retry, and conflict
  behavior are defined where applicable.
- State owner, lifecycle, invalidation, and recovery are defined.
- Cross-feature calls use a public port/API boundary.
- Test scenarios and integration evidence are specified.
- Prototype/local data is explicitly separated from production evidence.

## 8. Current repository application

The existing `src/main.jsx` 109-screen presentation prototype is disposable
reference material. It is not a production route fallback and is not evidence
that the corresponding features are implemented. Product Management and Loyalty
Promotion are the first existing production-oriented feature slices; future
features must be rebuilt under this baseline after their own design and contract
gates are satisfied.
