### 2.2 Manage Products

#### Primary Actors

Staff; Manager with the applicable product-management authority.

#### Secondary Actors

Cloud Storage Service, where product media is created, updated, or retrieved.

#### Description

As an authorized catalogue maintainer, I want to manage retail products throughout their catalogue lifecycle so that products contain valid information, pricing, media, variants, inventory data, and publication state.

This use case includes the following sub-flows:

- Create Product
- Update Product Information
- Publish or Unpublish Product

#### Preconditions

1. The platform is available.
2. The actor is authenticated with an active account and has the required product-management authority.
3. For an update or publication action, the target product exists.

#### Normal Flow

**Create a product**

1. The actor opens product management and chooses to create a product.
2. The actor enters the product information, media, variants, quantities, and applicable pricing data.
3. The system validates the submitted data and required media.
4. The system saves the product in an unpublished state and records attributable audit evidence.

**Update product information**

1. The actor selects an existing product and chooses to update it.
2. The system displays the current product information.
3. The actor changes permitted product data, media, variants, quantities, or pricing data.
4. The system validates the changes and preserves existing order/hold terms where applicable.
5. The system saves the valid changes and records attributable audit evidence.

**Publish or unpublish a product**

1. The actor opens the product's publication controls and selects Publish or Unpublish.
2. The system checks current authority and the latest product state.
3. For Publish, the system validates the publication prerequisites, including category, usable media, variants, and applied approved pricing.
4. The system applies the requested publication state and records the actual outcome.

#### Alternative Flows

**Create or update — unauthorized actor or unknown product**

The system rejects the request without exposing unauthorized product data or changing catalogue data, and records the rejected attempt according to the audit policy.

**Create or update — invalid or incomplete product data**

The system identifies the invalid fields, media, variants, quantities, category, or pricing data, displays field-level validation messages, and does not save a partial product or partial update.

**Create or update — media upload or storage failure**

The system reports the failure, preserves the previous product state, removes incomplete media references where necessary, and allows the actor to retry.

**Update — product does not exist or has changed**

The system rejects the stale update, reloads the latest product state, and asks the actor to review and resubmit the changes.

**Publish — publication prerequisite is missing**

The system keeps the product unpublished, identifies the unmet prerequisite, records the failed attempt, and allows the actor to correct the product data before retrying.

**Publish or unpublish — requested state already exists**

The system performs no second state transition, reports a successful no-change result, and records the attributable attempt.

**Unpublish — existing valid hold or order**

The system unpublishes the product for subsequent public reads and new checkout holds without cancelling valid existing holds, changing accepted prices, extending deadlines, or modifying historical purchases.

**Save or audit failure**

The system preserves the previous catalogue state, reports a safe error, records a failed attempt when possible, and allows the actor to retry.

#### Postconditions

- A valid new product is stored as unpublished until explicitly published.
- Valid product updates are saved without changing existing accepted order or hold terms.
- A successful publish makes the eligible product available to public catalogue reads.
- A successful unpublish prevents subsequent public reads and new checkout holds.
- Failed or unauthorized operations preserve the previous product state and are safely audited.

#### Business Rules

BR-12-01, BR-12-02, BR-12-03, BR-12-04, BR-12-05, BR-12-06, BR-12-07, BR-12-08

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-12-01 | Only an authenticated active Staff member or an authorized Manager may create, update, publish, or unpublish a product. |
| BR-12-02 | A newly created product is stored as unpublished until an authorized actor explicitly publishes it. |
| BR-12-03 | Product creation and update require valid required fields, category, variants, media, quantities, and applicable pricing data. |
| BR-12-04 | A publish request requires an active category, at least one usable image, configured variants, and applied approved pricing for each configured variant. |
| BR-12-05 | A failed validation, media operation, or state save must not create a partial product or partial update. |
| BR-12-06 | An authorized unpublish immediately prevents subsequent public reads and new checkout holds. |
| BR-12-07 | Unpublishing does not cancel valid existing holds, reprice accepted orders, extend deadlines, or alter historical purchases. |
| BR-12-08 | Each create, update, publish, and unpublish attempt records its actual outcome under the platform audit policy. |
