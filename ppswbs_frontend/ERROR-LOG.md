# Frontend design blockers

## FE-DESIGN-0001 — BLOCKED

- Time: 2026-10-31T19:16:50+07:00.
- Reporter: Codex. Scope: project-wide frontend and Figma screen implementation.
- Sources: `AGENT.md` sections 2–5; `../ppswbs-spec/screen.md`; Figma file `chUTTiTlLrFW7Dv0IdN4gC`, frame `1:543`.
- Evidence: `ppswbs_frontend` currently contains only `AGENT.md`; there is no application scaffold or package manifest. The supplied Figma frame is a generic Home Page with placeholder content. The screen flow enumerates many journeys, while corresponding complete screen designs, UI states, API bindings, and an approved frontend implementation plan/version are not established by that frame alone.
- Impact: production implementation of the whole system would require choosing missing design and behavior, dependencies and integration contracts. In particular, navigation and authenticated/payment state must not be inferred from a generic visual sample.
- User-provided design direction: square-looking buttons, existing color palette, concise responsive layouts, banner and product-image carousels; create Figma screens and real frontend.
- Changes before stopping: no frontend code, Figma edits, or tests. Figma Home Page metadata and design context were read.
- To resume: establish the first screen's complete design and applicable behavior/API contracts, and a frontend scaffold plan with dependency versions; clarify ownership and unresolved cross-feature integration as necessary. The user requested both Figma design and implementation, so design scope should be coordinated with this gate.

### Resolution update — 2026-10-31T19:25:00+07:00

- The user explicitly approved creating the frontend foundation and requested that the work continue from the Home Page.
- The current implementation scope is limited to the public Home Page: responsive presentation, banner carousel, and product-image carousel. Product, account, cart, workshop booking, payment, and role-specific journeys remain outside this resolved scope until their screen behavior and API contracts are supplied.

### Resolution update — 2026-10-31T20:10:00+07:00

- The user explicitly expanded the work to cover all project screens, including areas owned by other team members.
- A React/Vite prototype now registers 109 screen routes from `../ppswbs-spec/screen.md`. It includes responsive UI flows for public catalogue and workshops, Member cart/checkout/orders, Staff retail operations, Manager dashboard, and role-scoped workspace shells for the remaining screen inventory.
- These are presentation prototypes with local example data. They do not claim live authorization, payment, delivery, booking, product, or loyalty integration until the owning feature contracts are connected.

## FE-DESIGN-0002 — BLOCKED

- Time: 2026-10-31T20:35:00+07:00.
- Reporter: Codex. Scope: FE-08 production frontend integration for the Member cart, checkout, payment initiation, and order history.
- Sources checked: `ppswbs_backend/.../readyringsales/controller/CartController.java`, `CheckoutController.java`, `dto/CartDtos.java`, `dto/CheckoutDtos.java`; `ppswbs-spec/APIs.md` rows for `readyringsales`; `ppswbs-spec/features/008-retail-order-and-fulfillment/tasks.md` T004 and T020.
- Facts: backend exposes typed cart and checkout routes, all of which require an active Member resolved from a verified Firebase bearer identity. The existing frontend has no approved Firebase client/session provider, no defined API-origin configuration, and no approved token lifecycle/error-recovery contract. T004 remains open and explicitly records these frontend decisions as missing.
- Why work is stopped: wiring calls with a browser-invented token, `X-Debug-Account-Id`, local role, guessed backend URL, or invented retry/session behavior would bypass or contradict the documented authorization contract. Payment and GHTK paths additionally require their owner contracts.
- Files changed before stopping: none for this integration attempt. Existing prototype files remain local-data presentation only. Checks performed: source/contract inspection only; no API request was made.
- Needed to resume: approved frontend API base URL and Firebase client/session contract (how the current ID token is obtained/refreshed and made available to FE-08), plus the intended user-facing handling for `401`, `403`, validation, conflict, and integration-unavailable API errors. Then Cart, Checkout, payment-initiation, cancellation, and order-history requests can be connected without guessing.

### Partial delivery update — 2026-10-31T20:48:00+07:00

- The public FE-02 catalogue contract is independent of Firebase identity and is now consumed by the frontend through `src/api/catalogue.js`: product list, categories, product detail and public media URLs.
- Catalogue and product-detail views implement loading, empty, error and retry states. The Member-only add-to-cart action remains disabled pending the FE-08 identity/session contract.
- `npm.cmd run build` passed. Manual live-API check to `http://127.0.0.1:8080/api/v1/products` timed out because the local backend was not responding; this is an environment observation, not proof that the endpoint contract is invalid.

### FE-08 UI preparation update — 2026-10-31T21:05:00+07:00

- Product detail now supports public-product variant selection and a 1–99 quantity picker; it does not claim an inventory count that the public catalogue contract does not expose.
- Cart UI supports local quantity edits, removal, branch grouping and the 50-line presentation limit. Checkout validates recipient fields, presents delivery/pickup, voucher/points and branch shipping summaries. Member order filtering and Staff-operation progress are interactive UI states.
- `src/api/retail.js` encodes the known protected FE-08 API routes and typed error shape. It deliberately requires a caller-provided Firebase ID token and makes no request without one.
- Verification: `npm.cmd run build` passed. These interactions are client-side preparation, not evidence of Member API integration.

### GHTK quote integration check — 2026-11-01T00:00:00+07:00

- Scope requested: connect the FE-08 checkout UI to the local, read-only GHTK fee-quote endpoint.
- Backend contract now expects a verified Member bearer token and `POST /api/v1/ready-ring-shipping-quotes` with `locationId` plus recipient address/province/district/ward. It returns a server quote or `deliveryAvailable=false`; it does not create a shipment.
- The existing `RetailCheckoutWorkspace` is a local-data prototype. `src/api/retail.js` deliberately requires a caller-provided Firebase ID token, but no approved Firebase session provider/token lifecycle/API-origin recovery flow exists in the rendered application.
- No frontend source was changed. Calling the protected endpoint with a browser-invented token or a debug account header would violate the Member authorization contract.
- To resume this narrow integration: provide the existing FE-01 auth/session hook or provider that yields the current Firebase ID token for the Member checkout route, and confirm the API-origin/error-recovery contract. The existing GHTK recipient form can then call `createRetailClient(getIdToken).quoteShipping(...)` without exposing the provider token.
