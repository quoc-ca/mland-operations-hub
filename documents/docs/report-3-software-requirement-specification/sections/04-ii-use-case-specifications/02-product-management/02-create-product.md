### 2.2 Create Product

#### Primary Actors

Staff; Manager only with explicit applicable product-creation delegation.

#### Secondary Actors

Cloud Storage Service.

#### Description

As an authorized catalogue maintainer, I want to create an unpublished retail product with shared attributes, optional draft images, and separately identified predefined variants with proposed prices and manual quantities so that prices can be reviewed before application/publication.

#### Preconditions

1. The platform is available.
2. The actor is authenticated, has an active platform account, and has current product-creation authority. Manager's price-decision authority alone is insufficient.
3. Category reference data and permitted variant option values are available. Use Mland-provided configured size/engraving values, including “Không khắc” (No engraving); Mland must supply the initial option list.

#### Postconditions

- On success, one complete unpublished product/variant/image-reference group has stable identities, retained proposed prices/currencies, and independent quantities.
- Creation does not establish an applied sale price, Manager approval, public visibility, cart/order, or hold.
- Invalid, unauthorized, or failed creation establishes no partial catalogue group and is not reported as successful.
- The attempt has safe attributable audit evidence. The actor may continue to price approval/application in UC13, then publication in UC14.

#### Normal Flow

**Create Product**

1. The actor opens the Create Product form.
2. The system checks access and presents shared product fields, selectable category data, configured variant options, and image controls.
3. The actor enters a shared name/description and selects a category.
4. The actor configures one or more predefined size/engraving combinations, entering each variant's proposed amount/currency and manual remaining-unsold quantity. No prior price approval is needed to prepare a draft.
5. The actor optionally adds draft image references, alternative text, and distinct display positions.
6. The actor reviews the draft and submits creation.
7. The system rechecks current active-account/action authority and validates the complete submission, category, configured options, per-product combination uniqueness, quantities, prices, and image references. Name must be 1–200 characters and description 1–5,000 after trimming; there must be an active category and at least one unique configured variant with positive whole-VND proposed price and nonnegative integer quantity. Images, if supplied, must meet the media limits: at most 10, 5 MB each, JPEG/PNG/WebP, alternative text 1–200 characters.
8. The system saves the complete group with stable product/variant identities and an unpublished parent state; proposed amounts remain separate from effective sale prices.
9. The system retains safe SUCCESS audit evidence and confirms creation in the maintenance context, with continuation to UC13/UC14.

#### Alternative Flows

**Steps 2 and 7 — Missing or revoked creation authority**

The system denies Guest, Member, Admin, inactive accounts, and Manager without current applicable delegation. No catalogue data changes, protected draft data is not exposed, and the rejection is safely audited. The flow ends.

**Step 7 — Invalid fields, category, quantity, proposed price, or image references**

The system identifies validation errors and preserves editable input for correction. Examples include blank required name/description, inactive/missing category, missing configured variants, invalid proposed price/currency, or negative/fractional quantity. No partial group is saved; resume at the applicable input step.

**Step 7 — Duplicate variant combination**

The system rejects duplicate size/engraving combinations within the same product, including concurrent attempts, without partial mutation. The same combination may exist on another product. Resume at Step 4; at most one duplicate combination can be accepted.

**Step 7 — Existing product has the same display name**

The name alone does not cause rejection. If all other validation passes, continue to Step 8 with a distinct stable identity; no undocumented SKU requirement is added.

**Steps 5 and 8 — Required submitted-media operation or save fails**

The system reports a safe failure, retains FAILED evidence, and permits correction/retry. It does not report success or persist a partial catalogue group. Failed-upload files unlinked to catalogue data are removed after 24 hours; this cleanup does not authorize deletion of referenced/history media.

**Step 9 — Actor tries to apply or publish an unapproved draft price**

The draft remains unpublished and available for review. UC13/UC14 rejects application/publication without a matching in-system Manager approval and explicit permitted application; saving the proposal itself remains allowed.

#### Business Rules

BR-12-01, BR-12-02, BR-12-03, BR-12-04, BR-12-05, BR-12-06, BR-12-07, BR-12-08

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-12-01 | Active Staff may create products; Manager requires explicit applicable creation delegation. Guest, Member and Admin cannot create products. |
| BR-12-02 | Creation saves an unpublished product with price proposals; approval, price application and publication remain separate actions. Draft images are optional. |
| BR-12-03 | Each variant has a stable identity, independent proposed price/quantity and a size/engraving combination unique within its product, including concurrent creation. |
| BR-12-04 | Display names need not be unique; identity belongs to the product/variant, with no separate SKU, variant-publication flag or editable parent stock total. |
| BR-12-05 | Creation requires valid name/description, an active category, at least one configured variant, positive whole-VND proposed prices and nonnegative integer quantities. |
| BR-12-06 | Images require alternative text, distinct positions and compliance with the shared media limits, access and retention policy. |
| BR-12-07 | Failed validation or saving creates no partial product, variant or image-reference group; retry alone does not guarantee creation deduplication. |
| BR-12-08 | Record each attempt and its actual outcome under the shared audit policy. |
