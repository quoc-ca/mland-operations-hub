### 2.3 Update Product Information

#### Primary Actors

Staff; Manager.

#### Secondary Actors

Cloud Storage Service.

#### Description

As an authorized actor, I want to maintain product content, images, predefined variants, quantities, and the proposal/approval/application of variant prices while preserving publication prerequisites and existing purchase terms (UC13).

#### Preconditions

1. The platform is available and the target product/variant exists.
2. The actor is authenticated, has an active account, and holds current authority for the requested action. Only Manager/OWNER may approve a price proposal; other Manager maintenance requires explicit applicable delegation.
3. The relevant category/options/media and existing hold/order references can be assessed for validation. A price approval request has an identified proposal; price application requires a matching approved decision.

#### Postconditions

- An accepted maintenance save retains complete valid changes with stable product/variant identities and existing publication state.
- A saved proposal or Manager approval alone changes no applied sale price or publication state. An explicitly applied matching approved price affects new checkout immediately.
- Historical product names, variant/options, quantities, prices/currencies, and amounts remain frozen. No hold/payment/fulfilment is created, released, settled, extended, or automatically cancelled.
- A rejected/failed save preserves prior data/publication state and provides correction/retry guidance with safe audit evidence.

#### Normal Flow

**Update Product Information**

1. The actor opens the target product in the maintenance context.
2. The system checks access and loads current shared attributes, images, variants, quantities, applied/proposed prices, approval evidence, publication state, and relevant used/held constraints.
3. The actor selects shared-data, image, unused-variant-configuration, or quantity maintenance. Price proposal, approval, and application follow the action-specific alternatives below.
4. The actor edits permitted values, including adding/replacing/reordering/removing image references or configuring a new nonduplicate variant.
5. The actor reviews and submits the complete change.
6. The system rechecks current active-account/action authority and business state; validates input, image positions, per-product combination uniqueness, immutable used configurations, quantity constraints, and all publication prerequisites for an already-published product.
7. The system saves the complete valid change, preserving identity, publication, other variants' values, and frozen purchase records. Price/hold operations occur only in their authorized separate actions/workflows.
8. The system records attributable audit evidence and confirms the result.

#### Alternative Flows

**Step 3 — Save a variant price proposal**

The permitted Staff/delegated maintainer enters a proposed amount/currency for an identified variant/configuration and submits through Steps 5–8. The proposal is retained separately for review; any current applied price and publication state remain unchanged. Reviewed-term changes require new review and preserve prior proposal/decision evidence. Human proposals do not require AI integration.

**Step 3 — Manager approves a price proposal**

1. Manager opens the identified proposal and reviews its exact variant/configuration, amount, and currency.
2. Manager submits approval; the system rechecks active Manager decision authority and that the reviewed terms remain unchanged.
3. The system records in-system approval evidence binding proposal, variant/configuration, amount/currency, deciding actor, and time, and safely audits the outcome.
4. The system confirms approval without applying a sale price or publishing. Applying the price requires the separate permitted application alternative.

**Step 3 — Apply an approved variant price**

The permitted Staff/delegated maintainer selects the approved terms and submits application. At Step 6, the system requires an exact in-system Manager-approved match for the variant/configuration/amount/currency and validates any published-product prerequisites. At Step 7, it applies the price immediately for future checkout, retaining existing valid held orders' frozen prices and original deadlines. Step 8 audits application; approval alone is insufficient.

**Steps 2 and 6 — Unknown target or missing/revoked authority**

The system returns a safe error, exposes no unauthorized draft content, changes no data, and safely audits rejection. Guest/Member/Admin cannot maintain or approve. Manager without delegation cannot perform non-approval maintenance; Staff cannot approve prices.

**Step 6 — Invalid input or duplicate combination**

The system rejects the entire save with correction guidance and no partial mutation. A combination already configured on the same product is invalid; combinations on other products do not cause this rejection. Resume at Step 4.

**Step 6 — Edit a variant configuration used in any hold/order**

The system rejects changes to that variant's size/engraving even if the hold has expired. The actor may configure a new nonduplicate variant instead. Approved price and permitted quantity maintenance remain possible without rewriting used configuration/history.

**Step 6 — Maintainer edit would invalidate published-product prerequisites**

The system rejects the complete save and retains all prior data and publication state, with REJECTED audit evidence. This includes removing the last usable image or adding a variant without an applied approved price. The actor must explicitly unpublish through UC14 first or submit valid replacement data in the same save; there is no automatic unpublish.

**Step 6 — Quantity reduction below active held quantity**

Under confirmed A-05, the system rejects a value below that variant's active held total; another variant's quantity cannot cover the reduction. It does not delete/release/settle holds. The actor may correct the quantity at Step 4; this rule is confirmed by the user's Q14 approval.

**Step 6 — Stale conflicting submission**

Under confirmed A-06, the system rejects the later conflicting edit, preserves the accepted state, and asks the actor to reload/review from Step 2. The accepted concurrent change is retained; reload/review is required before resubmission.

**Step 6 — Unapproved, mismatched, or changed reviewed price terms**

The system rejects application/approval of mismatched variant/configuration/amount/currency terms and retains the current effective price and prior evidence. The changed proposal requires fresh Manager review. External approval assertions, AI suggestions, or a different variant's approval are insufficient.

**Steps 4 and 7 — Required media operation or save fails**

The system reports a safe failure, preserves prior catalogue data, retains FAILED evidence, and permits correction/retry. Removing an image reference does not authorize deletion of shared/history media; external image-loss visibility policy remains confirmed A-11 and differs from manual edit rejection.

#### Business Rules

BR-13-01, BR-13-02, BR-13-03, BR-13-04, BR-13-05, BR-13-06, BR-13-07, BR-13-08, BR-13-09, BR-13-10, BR-13-11

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-13-01 | Staff maintains catalogue data/proposals/application. Only active Manager/OWNER approves prices; other Manager maintenance requires current explicit applicable delegation. Guest/Member/Admin have no business mutation or approval rights. Recheck authority at submission (D-02). |
| BR-13-02 | Proposal, approval, application, and publication are separate actions. Proposed replacements retain the old applied price/publication. Every initial/replacement sale price needs in-system Manager approval and explicit permitted application; AI/outside-system assertions do not substitute (D-11). |
| BR-13-03 | Approval binds exact unchanged proposal, variant/configuration, amount/currency, deciding actor, and time. Changed terms require fresh review, preserving earlier terms/decision evidence; a different variant's approval is invalid (D-11). |
| BR-13-04 | Configured size/engraving combinations are unique within each product, including concurrent changes. After any hold/order use, configuration is immutable even after expiry. A different configuration needs a new nonduplicate variant; approved price/quantity edits remain permitted (D-07/D-09). Mland supplies configured option values, including “Không khắc” (No engraving); initial data remains a dependency (D-06). |
| BR-13-05 | An accepted save is complete and preserves identity and publication. Reject the entire maintainer edit if its final state breaks published-product prerequisites; retain previous data/flag and audit rejection. Require explicit prior unpublish or valid same-save replacement, never automatic unpublish (D-10). |
| BR-13-06 | Image references may be added/replaced/reordered/removed with alternative text and unique positions per product. Reference removal does not authorize shared/history media deletion. At most 10 images, 5 MB each, JPEG/PNG/WebP, with alternative text of 1–200 characters. Draft access requires product authority. Keep referenced/history files; delete eligible unreferenced files after 30 days and failed unlinked uploads after 24 hours. Provider selection is deferred to the plan; outage/recovery follows confirmed A-11 (D-04). |
| BR-13-07 | Manual remaining-unsold quantity belongs to each variant, not a separately editable parent stock total. Quantity changes do not mutate holds or promise physical stock. Confirmed requirement (A-05): reject reductions below the same variant's active held total, without cross-variant substitution. |
| BR-13-08 | Applied approved prices affect new checkout immediately. Existing valid held orders may pay frozen prices within original 10-minute payment-link and 15-minute hold deadlines; updates neither extend/revive holds nor automatically cancel orders (D-08). Historical names/options/quantity/price/amount remain unchanged. |
| BR-13-09 | Distinct variants retain distinct hold/order-line identities and frozen option/price snapshots (D-01). Merge same-variant selections only when price/snapshot terms also match (confirmed A-10). Preserve legacy snapshots/holds, and configure reviewed legacy products into variants before new checkout; never guess historical options (D-06). |
| BR-13-10 | Confirmed requirement (A-06): reject stale conflicting edits with reload/review guidance instead of silent overwrite. Trimmed name is 1–200 characters and description 1–5,000. Proposed/applied prices are positive whole VND, rejecting decimals rather than rounding/converting (D-04). |
| BR-13-11 | Maintenance/proposal/review/application attempts retain safe normally append-only evidence with actual SUCCESS/REJECTED/FAILED outcomes and five-year business audit retention. Preserve reviewed terms; exclude credentials, tokens, and avoidable personal data. |
