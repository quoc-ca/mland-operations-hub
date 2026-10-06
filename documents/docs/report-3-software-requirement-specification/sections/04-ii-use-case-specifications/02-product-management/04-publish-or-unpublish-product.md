### 2.4 Publish or Unpublish Product

#### Primary Actors

Staff; Manager only with explicit applicable publication delegation.

#### Secondary Actors

Cloud Storage Service — catalogue-media dependency for publication; provider selection is deferred to the plan; approved catalogue-media access and retention rules apply.

#### Description

As an authorized catalogue maintainer, I want to explicitly publish an eligible retail product or unpublish it immediately so that public discovery/new checkout reflect current publication while valid held orders and historical purchases retain their agreed terms (UC14).

#### Preconditions

1. The platform is available and the target product exists.
2. The actor is authenticated, has an active account, and holds current authority for the requested publication action.
3. Publishing can assess current category, shared attributes, usable media, variants, applied prices/approval evidence, and quantities. These prerequisites are validated during the flow; their absence does not prevent an authorized unpublish request.

#### Postconditions

- A successful publish makes an eligible product public; zero quantity alone does not block publication/display.
- A successful unpublish excludes the product from subsequent public reads and new checkout/hold acquisition immediately, without waiting for pending held orders.
- Previously accepted valid holds retain frozen purchase terms and original deadlines. No catalogue action creates/releases/settles a hold, extends/revives expiry, cancels an order, or changes payment/fulfilment automatically.
- Rejected/failed requests preserve prior data/publication and receive safe audit evidence with the actual outcome.

#### Normal Flow

**Publish or Unpublish Product**

1. The actor opens the target product's publication controls.
2. The system checks access and loads the current product and publication state.
3. The actor chooses Publish or Unpublish.
4. The actor submits the selected action.
5. The system rechecks current active-account/action authority and the latest product state. If the requested state already exists, it returns the no-change alternative; publication prerequisites at Step 6 apply to an actual publish transition.
6. For Publish, the system validates an active category, valid shared attributes, at least one usable image and configured variant, and a valid applied Manager-approved price/currency with nonnegative quantity for every configured variant. For Unpublish, it does not require publication prerequisites or wait for existing holds.
7. The system applies the requested parent publication state. Unpublish immediately prevents subsequent public reads/new checkout from acquiring new holds; previously accepted valid holds retain their protection.
8. The system records safe attributable audit evidence and confirms the resulting state.

#### Alternative Flows

**Steps 2 and 5 — Unknown product or missing/revoked authority**

The system rejects the request without changing catalogue data or revealing unauthorized drafts, and safely audits rejection. Manager without applicable delegation, Admin, Guest, Member, or an inactive account cannot perform this action.

**Step 6 — Publication prerequisite missing**

The system identifies unmet prerequisites and retains the unpublished state. Proposed or approved-but-unapplied prices are insufficient. The actor may correct data or complete approval/application through UC13, then restart at Step 1. No partial publication occurs.

**Step 6 — All variants have zero quantity**

If all other prerequisites are satisfied, continue to Step 7 and publish with unavailable indicators. Zero quantity does not require rejection or hidden variants.

**Step 5 — Requested state already exists**

Under confirmed A-07, the system checks current authority, reports a successful no-change result, and records safe attempt evidence without a second effective transition.

**Step 7 — Existing valid held order when product is unpublished**

The system unpublishes without waiting for the hold. The existing order may complete verified payment using frozen terms while eligible under the original 10-minute payment-link and 15-minute hold deadlines. No extension, repricing, or automatic cancellation occurs.

**Step 7 — Checkout races with unpublish, or stale cart/expired hold attempts checkout**

Only an already-accepted valid hold before unpublish becomes effective receives protection. Requests after that boundary cannot acquire a new hold, regardless of an earlier public view or stale cart. Expired holds are not revived; late payments follow the existing payment-exception workflow and do not automatically authorize fulfilment.

**Steps 6 and 7 — Required data/media assessment or state save fails**

The system reports a safe error, preserves the previous state, records FAILED evidence, and permits retry. External loss of all usable images is a separate confirmed A-11 public-read policy, not permission to silently rewrite publication state.

#### Business Rules

BR-14-01, BR-14-02, BR-14-03, BR-14-04, BR-14-05, BR-14-06, BR-14-07, BR-14-08

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-14-01 | Active Staff may publish/unpublish; Manager/OWNER requires current explicit applicable delegation. Guest/Member/Admin cannot mutate publication; approval authority alone grants no publication right. Check authority at submission (D-02). |
| BR-14-02 | Publish validates current active category, required shared attributes, at least one usable image and configured variant, and an applied price/currency backed by in-system Manager approval with nonnegative quantity for every configured variant. Proposed or approved-but-unapplied prices are insufficient (D-03/D-11); name is 1–200 characters, description 1–5,000 after trimming, price is positive whole VND, and images are limited to 10 at 5 MB each in JPEG/PNG/WebP with 1–200-character alternative text (D-04). A usable image is successfully stored, readable, and in an allowed format. |
| BR-14-03 | Zero quantity does not block publication or public display; indicate unavailable variants rather than promise a purchase. Publication belongs to the parent product; no additional Product-active or Variant-publication state is introduced (D-03). |
| BR-14-04 | Unpublish takes effect immediately for subsequent public reads/new checkout and cannot acquire new holds. It requires no wait for pending orders and does not alter historical product/variant/options/quantity/price/amount snapshots (D-08). |
| BR-14-05 | Already accepted valid held orders retain payment eligibility at frozen prices within original 10-minute payment-link and 15-minute hold deadlines. Catalogue actions do not extend/revive holds or automatically cancel orders. Stale unheld carts have no protection; expired/late payments follow existing exception rules (D-08). |
| BR-14-06 | Reject a complete maintainer save that invalidates a published product's prerequisites, retaining all old data/publication. Require explicit prior unpublish or a valid same-save replacement; never automatically unpublish as a maintenance side effect (D-10). Inactive categories exclude public reads without changing historical references/publication flags. |
| BR-14-07 | Confirmed requirement (A-07): repeated existing-state requests succeed without a second effective transition. Confirmed A-11 suppresses public reads during external all-image failure without changing flags/history; restore public eligibility when usable media returns and other prerequisites hold. No automatic publication-state transition occurs. |
| BR-14-08 | Publication attempts retain safe normally append-only actor/target/action/time/outcome/reason and permitted before/after/reference evidence with five-year business audit retention. Rejected/failed actions are not success; credentials, tokens, and avoidable personal data are excluded. |
