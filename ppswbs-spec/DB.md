## Users and roles

Design status: Draft proposal. Physical column names, types, lengths, indexes and defaults are proposed unless explicitly identified as existing implementation details.

Sources:

- [General Spec - Identity and Authorization](spec-general.md): Firebase UID identity, MySQL role/status authority, optional profile snapshots and no local password store.
- [Member Authentication Specification](features/001-member-authentication/spec.md) and [Data Model](features/001-member-authentication/data-model.md): current `members` persistence, proposed `accounts`/`account_roles`, policy acceptance and Guest-booking linkage.
- [Report 3 - Actors](../documents/docs/report-3-software-requirement-specification/sections/03-i-overall-requirements/04-user-requirements/01-actors.md) and [Use Cases](../documents/docs/report-3-software-requirement-specification/sections/03-i-overall-requirements/04-user-requirements/02-use-cases.md): profile, role assignment and account locking.

Design assumptions and unresolved points:

- `users` remains the logical account name used by this document. Its physical mapping to existing `members` and proposed `accounts` is `TBD`; this proposal does not rename or migrate those tables.
- The authentication feature proposes multiple role assignments through `account_roles`. The logical equivalent is a `user_roles` junction with user/role foreign keys, unique `(user_id, role_id)` and auditable grant/revoke state. That additional table must be specified before implementation; a single `users.role_id` would not represent this model.
- The working agreement/authentication feature names `MEMBER`, `STAFF`, `OWNER`, `ADMIN_TECHNICAL`; Report 3 names Member, Staff, Manager and Admin. Role-code reconciliation and the permission matrix remain `TBD`.
- Policy acceptance and role-change audit remain separate versioned records, not profile flags. Existing policy/auth-audit tables are outside the tables requested here.

### users

| Column | Type | Constraints / description |
| --- | --- | --- |
| `user_id` | BIGINT | Primary key; logical identity referenced by this document's user foreign keys. |
| `external_user_id` | VARCHAR(128) | Required unique Firebase UID, obtained only from a server-verified identity token. Never derived from email. |
| `email` | VARCHAR(255) | Nullable contact/profile snapshot; not a unique identity or account-merge key. |
| `email_verified` | BOOLEAN | Required; defaults to `FALSE`. Snapshot of Firebase email verification, not proof that a booking's contact email was confirmed. |
| `display_name` | VARCHAR(255) | Nullable basic profile/display name. |
| `phone_number` | VARCHAR(30) | Nullable profile phone; stored as text. Required contact fields are enforced by the booking/order flow, not by identity provisioning. |
| `photo_url` | TEXT | Nullable profile-image URL or storage reference. |
| `status` | VARCHAR(30) | Required. Proposed account values: `active`, `suspended`; default `active` for a newly provisioned account. Member entitlement still requires active role and current policy acceptance. Mapping to existing Member states is `TBD`. |
| `created_at` | DATETIME | Required account-creation timestamp. |
| `updated_at` | DATETIME | Required last-update timestamp. |

Indexes:

- Unique `(external_user_id)` for UID lookup and idempotent provisioning.
- Non-unique `(email)` for permitted contact lookup; it must not trigger automatic linking or merging.
- `(status, created_at)` for account-administration queries.

Rules:

- Firebase owns passwords, social credentials and token renewal. This table has no password/hash, provider access token, Firebase ID token or refresh-token column.
- Repeated/concurrent sign-in with the same verified UID provisions one account. Different UIDs remain different accounts even when email text matches.
- MySQL account status and active role assignments determine authorization. Provider claims and profile fields cannot grant business roles.
- Public Guests do not require a synthetic `users` row; `workshop_registration.member_user_id` may remain null.
- Guest-booking import requires verified matching email, explicit Member confirmation and entitlement; it preserves booking status and is idempotent.
- Account suspension blocks protected access without deleting orders, payments, requests or bookings. Account/PII retention and status-change audit policy remain subject to the owning feature.

### roles

| Column | Type | Constraints / description |
| --- | --- | --- |
| `role_id` | BIGINT | Primary key. |
| `role_code` | VARCHAR(50) | Required unique stable business-role code. Working-agreement candidates: `MEMBER`, `STAFF`, `OWNER`, `ADMIN_TECHNICAL`; reconciliation with Report 3 remains `TBD`. |
| `role_name` | VARCHAR(100) | Required human-readable role label; changing the label does not change the role code or permissions. |
| `description` | TEXT | Nullable description of the role's business responsibility and boundary. |
| `is_active` | BOOLEAN | Required; proposed default `TRUE`. An inactive role cannot grant authorization under this proposal. |
| `created_at` | DATETIME | Required role-creation timestamp. |
| `updated_at` | DATETIME | Required last-update timestamp. |

Indexes:

- Unique `(role_code)` for authoritative role lookup.
- `(is_active, role_code)` for active-role administration.

Rules:

- Role definitions and user-role assignments are different records. Users and roles are linked through the additional junction described above, not a comma-separated string or JSON role list.
- First-time Member provisioning grants only the Member role under the approved entitlement flow; operational roles require an authorised, audited grant.
- Guest is an unauthenticated actor, not a role granted to a provisioned account.
- Permissions are not inferred from `role_name`. Dynamic permission configuration would require a separately specified permission model; this table alone does not implement RBAC configuration.
- Referenced roles must not be physically deleted. Deactivation/revocation retains business and audit history; exact administrative authority remains `TBD`.

## Loyalty points

Design status: Future/conditional draft requested for documentation. [Report 3 - Use Cases](../documents/docs/report-3-software-requirement-specification/sections/03-i-overall-requirements/04-user-requirements/02-use-cases.md) lists balance, earning/redemption history and order redemption, while [Report 1 - Limitations](../documents/docs/report-1-project-introduction/sections/07-v-project-scope-limitations/02-limitations-exclusions.md) defers loyalty and states that Members have no loyalty points in V1. This table does not enable loyalty in V1; scope approval and all conversion/eligibility rules remain `TBD`.

### loyalty_points

| Column | Type | Constraints / description |
| --- | --- | --- |
| `loyalty_point_id` | BIGINT | Primary key. Proposed model: one immutable ledger entry, not one mutable balance row. |
| `member_user_id` | BIGINT | Required foreign key to `users`; the subject must have Member identity under the approved loyalty policy. |
| `order_id` | BIGINT | Nullable foreign key to `orders`; set for an entry caused by an eligible order. Other loyalty targets remain `TBD`. |
| `entry_type` | VARCHAR(20) | Required. Proposed values: `earn`, `redeem`, `adjustment`; supported operations require policy approval. |
| `points_delta` | BIGINT | Required non-zero signed integer: positive for earning, negative for redemption; an authorised adjustment may have either sign. |
| `event_key` | VARCHAR(128) | Required unique business-event/idempotency key so retries cannot post the same entry twice. |
| `reason` | TEXT | Nullable for normal earning/redemption; required for a manual adjustment. |
| `recorded_by_user_id` | BIGINT | Nullable foreign key to `users`; set for an authorised manual adjustment, null for a system-posted event. |
| `created_at` | DATETIME | Required posting timestamp. No `updated_at`: posted entries are immutable. |

Indexes:

- Unique `(event_key)` for idempotent ledger posting.
- `(member_user_id, created_at, loyalty_point_id)` for balance/history queries with deterministic ordering.
- `(order_id, entry_type)` for order-related point history.

Rules:

- Proposed balance is the sum of posted `points_delta` values for a Member; do not keep an independently editable balance in `users`.
- Redemption must check eligibility and sufficient points atomically. An earning trigger, points-to-money rate, rounding, minimum redemption and any limits remain `TBD`.
- A posted entry is not edited/deleted to correct a balance; an authorised compensating entry records the reason and actor. This is not an order-refund workflow.
- Point expiry, tiers, temporary checkout reservations and reversal rules are not implied by these fields and require separate design if approved.
- Linking redemption to payable amounts requires order discount snapshots/consumption records not present in the current retail proposal. No change to `orders.total_amount` is authorised by this draft alone.

## Product catalogue, retail orders, payment and delivery

Design status: Draft proposal based on the supplied documents. Column names, SQL types, lengths, defaults, indexes and status codes below are proposed physical-design details, not an already approved or implemented schema.

Sources:

- [Report 2 - Scope and Purpose](../documents/docs/report-2-project-management-plan/sections/02-i-project-overview/01-scope-purpose.md): Member cart, complete-cart checkout, price snapshots, 15-minute quantity holds, full payment, pickup and manual carrier handoff.
- [Report 3 - Actors](../documents/docs/report-3-software-requirement-specification/sections/03-i-overall-requirements/04-user-requirements/01-actors.md) and [Use Cases](../documents/docs/report-3-software-requirement-specification/sections/03-i-overall-requirements/04-user-requirements/02-use-cases.md): catalogue information, images, publishing, prices, available-to-sell quantities and delivery contact/address.
- [Report 3 - Retail Workflow](../documents/docs/report-3-software-requirement-specification/assets/diagrams/workflows/12-ready-ring-retail-swimlane.puml): verified payment and auditable carrier handoff.
- [API Contract Catalog](APIs.md): payment attempts and verified provider events; provider contracts remain `TBD`.

Design assumptions and unresolved points:

- Foreign keys to `users` follow the logical naming already used in this file. The implemented authentication schema uses `members`; the mapping between `users`, Member identity and operational users is `TBD` before implementation.
- One product belongs to one category, one product may have many images, and one retail order has one fulfilment choice and at most one delivery-information record. These cardinalities are design proposals because the supplied ERD/entity-description sections are incomplete.
- Report 2 specifies a 15-minute hold before payment. Report 3's retail workflow specifies reservation after payment without automatic expiry. The checkout/reservation lifecycle is `TBD`; the proposed `pending_payment`, `expired` and `hold_expires_at` fields support the Report 2 approach if that approach is approved.
- Staff versus Manager/Owner catalogue-maintenance authority differs between documents and remains `TBD`. Operational actor fields record the authorised user without deciding that permission conflict.
- This section describes catalogue-product retail. Linking configured ring designs to purchasable items is `TBD`. It does not change the existing rule that `custom_design_requests` has no relationship to orders.
- Voucher/loyalty use appears in Report 3, while Report 1 defers loyalty/marketing. Discount policy and its schema are `TBD`; no unapproved promotion or points foreign key is introduced here.

### products

| Column | Type | Constraints / description |
| --- | --- | --- |
| `product_id` | BIGINT | Primary key. |
| `category_id` | BIGINT | Required foreign key to `categories`; assumes one category per product. |
| `product_name` | VARCHAR(255) | Required catalogue name displayed to customers. |
| `description` | TEXT | Nullable product information displayed on the product-detail page. |
| `unit_price` | DECIMAL(15,2) | Required current selling price; must be non-negative. Exact pricing rules remain `TBD`. |
| `currency` | CHAR(3) | Required currency code, for example `VND`. Approved currency/default remains `TBD`. |
| `available_to_sell_quantity` | INT | Required; defaults to `0`; must be non-negative. Manually maintained sale quantity, not a warehouse inventory balance or a real-time guarantee. |
| `is_published` | BOOLEAN | Required; defaults to `FALSE`. Controls visibility in the public catalogue. |
| `created_at` | DATETIME | Required creation timestamp. |
| `updated_at` | DATETIME | Required last-update timestamp. |

Indexes:

- `(is_published, category_id)` for published catalogue browsing and category filtering.
- `(category_id)` for category membership queries and the foreign key.

Rules:

- Only published products in an active category are shown in the public catalogue; category activation is a proposed visibility rule.
- Product name/description support search; the search implementation is `TBD`. A normal B-tree index is not assumed to support substring search.
- Product availability and the entire cart are revalidated at checkout. Unpublished, invalid or insufficient-quantity products cannot be purchased.
- If the Report 2 hold model is approved, active unpaid holds reduce checkout availability. Held quantity can be derived from `order_items` of non-expired `pending_payment` orders; reserve/release and sale deduction must be atomic and must not oversell or deduct twice.
- Product edits do not rewrite historical order-item snapshots. A product referenced by an order item cannot be physically deleted; unpublish it instead.
- This table does not manage warehouses, lots, serial numbers, procurement or stock-movement ledgers.

### categories

| Column | Type | Constraints / description |
| --- | --- | --- |
| `category_id` | BIGINT | Primary key. |
| `category_name` | VARCHAR(100) | Required category label for grouping/filtering catalogue products. Proposed unique value under the chosen database collation. |
| `description` | TEXT | Nullable explanation of the category. |
| `is_active` | BOOLEAN | Required; defaults to `TRUE`. Proposed flag for enabling the category in public catalogue navigation. |
| `created_at` | DATETIME | Required creation timestamp. |
| `updated_at` | DATETIME | Required last-update timestamp. |

Indexes:

- Unique `(category_name)` to prevent duplicate labels under the chosen collation.
- `(is_active, category_name)` for active-category navigation.

Rules:

- A category may contain many products. Actual category labels and the need for multiple categories per product remain `TBD`.
- Deactivating a category hides its products under the proposed visibility rule; it does not alter existing order records.
- A category referenced by a product cannot be physically deleted until those products are reassigned.
- No category hierarchy or additional jewellery types are assumed.

### orders

| Column | Type | Constraints / description |
| --- | --- | --- |
| `order_id` | BIGINT | Primary key. |
| `order_code` | VARCHAR(64) | Required unique customer-facing order reference. |
| `member_user_id` | BIGINT | Required foreign key to `users`. Only an authenticated Member can place a retail order. |
| `status` | VARCHAR(30) | Required. Proposed values: `pending_payment`, `expired`, `paid`, `preparing`, `ready_for_pickup`, `picked_up`, `prepared_for_carrier`, `handed_to_carrier`. Default and pre-payment states depend on the unresolved checkout lifecycle. |
| `fulfilment_method` | VARCHAR(20) | Nullable before the Member chooses fulfilment after verified full payment. Allowed proposed values: `pickup`, `carrier`. |
| `subtotal_amount` | DECIMAL(15,2) | Required non-negative snapshot of the sum of order-item line amounts. |
| `total_amount` | DECIMAL(15,2) | Required non-negative final amount due. Equals `subtotal_amount` for the proposed retail scope; adjustment/discount rules remain `TBD`. Carrier fees are excluded. |
| `currency` | CHAR(3) | Required currency snapshot; must match the order items and its payment. |
| `hold_expires_at` | DATETIME | Nullable. If the Report 2 approach is approved, required for a pending checkout and set to checkout time plus 15 minutes. It does not expire a paid order. |
| `paid_at` | DATETIME | Nullable until verified full-payment confirmation is accepted; then required. |
| `picked_up_by_user_id` | BIGINT | Nullable foreign key to `users`; required when an authorised operational user records customer pickup. This is the recording actor, not the customer. |
| `picked_up_at` | DATETIME | Nullable; required when `status` is `picked_up`. |
| `created_at` | DATETIME | Required order/checkout creation timestamp under the approved lifecycle. |
| `updated_at` | DATETIME | Required last-update timestamp. |

Indexes:

- Unique `(order_code)` for order lookup.
- `(member_user_id, created_at)` for a Member's purchase history.
- `(status, created_at)` for operational order queues.
- `(status, hold_expires_at)` for unpaid-hold expiry processing if that model is approved.

Rules:

- An order contains at least one `order_items` row and is created from the complete validated cart; checkout creates no partial order.
- Prices, quantities, currency and amounts are fixed for that checkout. Catalogue-price changes do not recalculate an existing order.
- Only a verified gateway confirmation matching the payable target, full amount and currency can mark a retail order as paid. Browser redirects are not payment evidence.
- Under the proposed Report 2 lifecycle: `pending_payment` -> `paid` or `expired`. Expiry releases the unpaid hold; a paid order has no automatic reservation expiry. Handling a successful callback arriving after hold expiry remains `TBD`.
- After payment: `paid` -> `preparing`, then the pickup branch `ready_for_pickup` -> `picked_up`, or the carrier branch `prepared_for_carrier` -> `handed_to_carrier`. These exact status codes/transitions are proposed, not final requirements.
- Fulfilment choice is made after verified full payment. Carrier fulfilment requires a complete `delivery_infors` record; pickup does not require delivery contact/address.
- Customer pickup requires recording actor and timestamp. Carrier handoff requires the evidence recorded in `delivery_infors`.
- V1 has no in-system cancellation, refund, return or shipment-tracking lifecycle. Order history must be retained according to an approved retention policy (`TBD`).

### order_items

| Column | Type | Constraints / description |
| --- | --- | --- |
| `order_item_id` | BIGINT | Primary key. |
| `order_id` | BIGINT | Required foreign key to `orders`. |
| `product_id` | BIGINT | Required foreign key to `products` for the catalogue-product retail scope. |
| `product_name_snapshot` | VARCHAR(255) | Required product-name snapshot at checkout; keeps order history readable after a catalogue rename. |
| `quantity` | INT | Required purchased quantity; must be greater than `0`. |
| `unit_price` | DECIMAL(15,2) | Required non-negative selling-price snapshot at checkout, not a live lookup of `products.unit_price`. |
| `line_amount` | DECIMAL(15,2) | Required non-negative line total; must equal `quantity * unit_price`. Proposed stored snapshot; a generated column is an alternative physical implementation. |
| `created_at` | DATETIME | Required timestamp when the checkout line is created. |

Indexes:

- Unique `(order_id, product_id)` under the proposal that each catalogue product appears once per order and its quantity is aggregated.
- `(product_id)` for product-reference queries and the foreign key.

Rules:

- One order has many items; one product may occur in many orders.
- All items in an order use the order's currency. `orders.subtotal_amount` equals the sum of its `line_amount` values.
- Order items are immutable checkout snapshots. Cart quantity updates/removal happen before checkout, not by rewriting a paid order.
- All required quantities are validated and held together if the Report 2 model is approved; failure of any line prevents the complete checkout.
- These are order lines, not cart lines. Persistent cart storage is outside these seven tables.
- Configured/custom ring line types, variants and their effect on line uniqueness remain `TBD`; they are not implicitly linked to `custom_design_requests`.

### product_imgs

| Column | Type | Constraints / description |
| --- | --- | --- |
| `product_img_id` | BIGINT | Primary key. |
| `product_id` | BIGINT | Required foreign key to `products`. |
| `img_url` | TEXT | Required product-image URL or object-storage path. Stores a media reference, not binary image data. |
| `alt_text` | VARCHAR(255) | Nullable image description for accessibility; required when the image conveys information not already expressed by nearby text. |
| `sort_order` | INT | Required non-negative display position; proposed unique position within a product. |
| `created_at` | DATETIME | Required image-record creation timestamp. |
| `updated_at` | DATETIME | Required last-update timestamp. |

Indexes:

- Unique `(product_id, sort_order)` for ordered product-image retrieval.

Rules:

- One product may have multiple catalogue images. The lowest `sort_order` supplies the proposed catalogue thumbnail/main image, avoiding a separate main-image flag.
- Authorised catalogue maintainers may add, replace, reorder or remove image references. File validation, publication minimum-image count and storage retention remain `TBD`.
- These are catalogue images; Member reference images remain in `custom_design_requests.img_url`.
- Changing catalogue images does not alter an order's price or quantity snapshots.

### payments

| Column | Type | Constraints / description |
| --- | --- | --- |
| `payment_id` | BIGINT | Primary key. Proposed model: one row per payment attempt, updated with its verified gateway outcome. |
| `payment_code` | VARCHAR(64) | Required unique internal payment-attempt reference used to correlate provider confirmation with the payable target. |
| `order_id` | BIGINT | Nullable foreign key to `orders`; required for a retail full-payment attempt. |
| `workshop_registration_id` | BIGINT | Nullable logical foreign key to `workshop_registration`; required for a workshop-deposit attempt. This table name follows this file's inventory; mapping to implemented `workshop_bookings` remains `TBD`. |
| `payment_purpose` | VARCHAR(30) | Required. Proposed values: `retail_full_payment`, `workshop_deposit`; must match the selected target. |
| `amount` | DECIMAL(15,2) | Required amount requested for this attempt; must be greater than `0`. Retail amount must match the order's full amount due; workshop deposit formula remains subject to the approved booking policy. |
| `currency` | CHAR(3) | Required currency snapshot; must match the payable target. |
| `provider` | VARCHAR(50) | Required configured Payment Gateway identifier. Provider choice and contract remain `TBD`. |
| `provider_transaction_id` | VARCHAR(255) | Nullable until a provider transaction reference is available. Reference format/uniqueness scope remain `TBD` in the gateway contract. |
| `status` | VARCHAR(20) | Required; proposed default `pending`. Proposed values: `pending`, `succeeded`, `failed`, `expired`. These describe the payment attempt, not fulfilment. |
| `verified_at` | DATETIME | Nullable until an authentic provider outcome has passed server-side verification; required for `succeeded`. |
| `paid_at` | DATETIME | Nullable; required for `succeeded`. Records the confirmed payment timestamp; its provider/local timestamp source remains `TBD`. |
| `failure_reason` | TEXT | Nullable safe failure explanation/code. Must not contain gateway secrets, card data or raw sensitive callback payloads. |
| `created_at` | DATETIME | Required payment-attempt creation timestamp. |
| `updated_at` | DATETIME | Required last-update timestamp. |

Indexes:

- Unique `(payment_code)` for attempt correlation.
- Proposed unique `(provider, provider_transaction_id)` when the provider contract guarantees this uniqueness scope; nullable references allow pending attempts.
- `(order_id, created_at)` for retail payment history.
- `(workshop_registration_id, created_at)` for workshop payment history.
- `(status, created_at)` for pending-attempt processing.

Rules:

- Exactly one target is set: retail attempts have `order_id` and no `workshop_registration_id`; workshop attempts have `workshop_registration_id` and no `order_id`. Guest workshop deposits therefore do not require a Member foreign key on this table.
- One target may have multiple payment attempts. Retrying is conditional on the approved gateway and target-state rules; an already paid target must not be charged again by a normal retry.
- A retail sale requires one accepted verified full payment. Accepting it, recording the paid state and deducting held quantities must be idempotent; repeated callbacks must not repeat those effects.
- The proposed transaction-reference index alone does not define duplicate-event handling. Event identity, signature verification, allowed status transitions, timeout/late-success handling, retry and callback-record storage remain `TBD` in the provider contract.
- A browser redirect, client-supplied success flag or unverifiable callback must not mark payment as `succeeded`.
- Payment data is retained as business history; it is not deleted when catalogue data changes. Payment/audit retention remains `TBD`.
- This proposal does not introduce card-data storage, reconciliation, accounting or in-system refund processing. Custom-manufacturing payment linkage remains `TBD` outside the catalogue-retail/booking targets described here.

### delivery_infors

| Column | Type | Constraints / description |
| --- | --- | --- |
| `delivery_infor_id` | BIGINT | Primary key. Retains the table naming requested in this file. |
| `order_id` | BIGINT | Required unique foreign key to `orders`; at most one delivery-information record per retail order. |
| `recipient_name` | VARCHAR(255) | Required recipient/contact name for carrier fulfilment. |
| `recipient_phone` | VARCHAR(30) | Required contact phone number; stored as text to preserve prefixes and leading zeroes. Exact validation rules remain `TBD`. |
| `delivery_address` | TEXT | Required complete delivery-address snapshot supplied for this order. No address-book relationship is assumed. |
| `delivery_note` | TEXT | Nullable recipient delivery instructions. |
| `carrier_name` | VARCHAR(100) | Nullable before handoff; required when an authorised operational user records actual carrier handoff. |
| `handoff_reference` | VARCHAR(255) | Nullable before handoff; required handoff receipt/reference when handoff is recorded. It is evidence, not live shipment tracking. |
| `handed_off_by_user_id` | BIGINT | Nullable foreign key to `users`; required recording operational actor at carrier handoff. |
| `handed_off_at` | DATETIME | Nullable; required actual carrier-handoff timestamp. |
| `created_at` | DATETIME | Required timestamp when delivery details are saved. |
| `updated_at` | DATETIME | Required last-update timestamp. |

Indexes:

- Unique `(order_id)` for one delivery-information record per order and direct order lookup.
- `(handed_off_by_user_id, handed_off_at)` for actor/time handoff-evidence queries.

Rules:

- A delivery-information record belongs only to an order with verified full payment and `fulfilment_method = carrier`; a pickup order requires no such record.
- Recipient/contact/address fields must be complete before the order is prepared for carrier handoff.
- Carrier name, handoff reference, recording actor and timestamp are recorded together with the transition to `handed_to_carrier`; incomplete evidence cannot complete that transition.
- Under this proposal, recipient details and handoff evidence are fixed after handoff; any correction/audit procedure remains `TBD`.
- Delivery details are an order-specific snapshot, not a live reference to a Member profile or saved address.
- Carrier fees are paid separately to the carrier and are not added to `orders.total_amount` or recorded as a platform payment.
- V1 responsibility ends at carrier handoff. This table has no carrier API, live tracking, delivered/failed-delivery status, return or shipping-refund workflow.
- Recipient-data access, retention and deletion policy remain `TBD`; storing an address does not decide those policies.

## Promotions

Design status: Future/conditional draft. [Report 3 - Use Cases](../documents/docs/report-3-software-requirement-specification/sections/03-i-overall-requirements/04-user-requirements/02-use-cases.md) lists campaigns, voucher codes and order application; [Report 1 - Limitations](../documents/docs/report-1-project-introduction/sections/07-v-project-scope-limitations/02-limitations-exclusions.md) defers loyalty/marketing. Promotion scope, stacking, eligibility and redemption accounting remain `TBD` before this proposal can affect checkout.

### promotions

| Column | Type | Constraints / description |
| --- | --- | --- |
| `promotion_id` | BIGINT | Primary key. |
| `promotion_name` | VARCHAR(255) | Required campaign/promotion name. |
| `description` | TEXT | Nullable customer-facing explanation and conditions. |
| `voucher_code` | VARCHAR(64) | Nullable unique voucher code. Proposed simplification: at most one code per promotion; a campaign with multiple voucher codes requires a separate voucher table. |
| `discount_type` | VARCHAR(20) | Required. Proposed values: `percentage`, `fixed_amount`. |
| `discount_value` | DECIMAL(15,2) | Required positive value; percentage must be at most `100`, fixed amount is expressed in `currency`. |
| `currency` | CHAR(3) | Required for a fixed amount or monetary threshold/cap; may be null for a percentage with no monetary conditions. Must match an eligible order whenever monetary conditions are present. |
| `minimum_order_amount` | DECIMAL(15,2) | Nullable non-negative minimum eligible merchandise amount; the precise eligibility basis remains `TBD`. |
| `maximum_discount_amount` | DECIMAL(15,2) | Nullable positive monetary cap for a percentage promotion; not used for a fixed discount under this proposal. |
| `starts_at` | DATETIME | Required promotion-validity start timestamp. |
| `ends_at` | DATETIME | Required end timestamp; must be later than `starts_at`. Proposed validity interval includes the start and excludes the end. |
| `is_active` | BOOLEAN | Required; proposed default `FALSE`. Manual activation does not override the validity window. |
| `created_by_user_id` | BIGINT | Required foreign key to `users`; authorised campaign-creation actor. Exact managing role remains `TBD`. |
| `created_at` | DATETIME | Required creation timestamp. |
| `updated_at` | DATETIME | Required last-update timestamp. |

Indexes:

- Unique `(voucher_code)` for voucher lookup; multiple null codes are permitted.
- `(is_active, starts_at)` for active/scheduled promotion selection, with end-time filtering.
- `(created_by_user_id, created_at)` for campaign administration.

Rules:

- A voucher is valid only when its promotion is active, within its approved validity window and eligible for the order. Code normalization/case sensitivity remain `TBD` before enforcing uniqueness.
- Discount cannot exceed the eligible order amount; percentage/fixed-amount rounding and tax interaction remain `TBD`.
- A null voucher code does not automatically authorize applying the promotion. Automatic versus code-based application must be specified.
- This table defines a promotion, not proof that an order consumed it. Per-order discount snapshots and redemption/usage records are additional dependencies; counters/limits and concurrent redemption cannot be implemented from this table alone.
- Later campaign edits/deactivation must not recalculate a completed order's historical amounts.
- Stacking with other vouchers/loyalty, product/category restrictions, customer targeting and usage limits are unresolved, not assumed unlimited entitlements.

## Workshop booking, slots, packages and exceptions

Design status: Draft proposal. This section retains the requested logical names `workshop_registration` and `worshop_exceptions`; the latter spelling can be reconciled with `workshop_exceptions` before implementation. The existing backend uses `workshop_bookings` and `booking_email_confirmations`, not this proposed physical schema.

Sources:

- [Report 3 - Workshop Booking Workflow](../documents/docs/report-3-software-requirement-specification/assets/diagrams/workflows/10-workshop-booking-swimlane.puml): configured capacity, Guest/Member booking, package invoice, deposit confirmation and QR notification.
- [Report 3 - Use Cases](../documents/docs/report-3-software-requirement-specification/sections/03-i-overall-requirements/04-user-requirements/02-use-cases.md): slot selection before design, branch/material filtering, group booking, check-in, packages and holiday/off-day exceptions. Group booking is additionally described in [Report 1 - Major Features](../documents/docs/report-1-project-introduction/sections/07-v-project-scope-limitations/01-major-features.md).
- [Report 1 - Proposed Solution](../documents/docs/report-1-project-introduction/sections/06-iv-proposed-solution/00-overview.md): three operating sessions, package deposits, additional participants and continuation work.
- [Member Authentication Specification](features/001-member-authentication/spec.md): Guest contact-email confirmation before payment, a hashed one-time token and 15-minute expiry.
- [Report 3 - Continuation Workflow](../documents/docs/report-3-software-requirement-specification/assets/diagrams/details/26-continuation-custody-detail.puml): Staff-created continuation booking and separate custody evidence.

Design assumptions and unresolved points:

- A `slots` row represents one dated session at one location, not a recurring timetable template. A group registration consumes its `participant_count` seats in one slot. These are proposed physical modeling choices.
- Location IDs below are logical foreign keys to an additional `workshop_locations` table, not to a table already described here. Location master data and branch/material/package compatibility require separate specifications.
- Booking workflows specify a 50% package deposit, while Report 3 also lists material-based deposit configuration. The proposed package deposit percentage defaults to 50 for that workflow; precedence of material/package rules and group-pricing basis remain `TBD`.
- Capacity values, temporary seat-hold timing, payment deadline after email confirmation, exception precedence and safe rescheduling policies remain `TBD`. Email-token expiry is not a retail-stock or payment-attempt expiry.
- Package invoices, adjustment consent, settlement, policy acceptance, email tokens and custody evidence remain separate records. These four tables alone do not represent the complete workshop workflow.

### workshop_registration

| Column | Type | Constraints / description |
| --- | --- | --- |
| `workshop_registration_id` | BIGINT | Primary key. Logical booking identity referenced by `payments.workshop_registration_id`. |
| `booking_code` | VARCHAR(64) | Required unique booking/lookup reference. Existing persistence creates a code with the booking; its public exposure/QR issuance follows the approved confirmation flow. |
| `member_user_id` | BIGINT | Nullable foreign key to `users`: null for a Guest booking, set for a Member booking or an explicitly imported eligible Guest booking. |
| `slot_id` | BIGINT | Required foreign key to `slots`; records the selected dated location/session. |
| `workshop_package_id` | BIGINT | Required foreign key to `workshop_packages`. |
| `ring_design_id` | BIGINT | Nullable foreign key to `ring_designs` for a permitted catalogue/configured-design selection. In-person consultation may leave it null; it does not link a custom image request. |
| `design_path` | VARCHAR(30) | Nullable proposed path: `catalogue_model`, `configured_design`, `in_person`. Exact required paths and timing remain `TBD`; image-request linkage is excluded by the existing `custom_design_requests` rules. |
| `contact_name` | VARCHAR(255) | Required booking contact name under this proposal. |
| `contact_email` | VARCHAR(255) | Required booking-notification and contact-email confirmation address; independent of a Member's mutable profile email. |
| `canonical_email` | VARCHAR(255) | Required normalized contact-email lookup value, following the approved email policy. Not an identity or automatic-link key. |
| `contact_phone` | VARCHAR(30) | Required booking contact phone under this proposal; exact validation remains `TBD`. |
| `participant_count` | INT | Required positive number of booked participants; used for slot-capacity consumption. |
| `package_name_snapshot` | VARCHAR(255) | Required package-name snapshot at booking/invoice creation. |
| `package_price_snapshot` | DECIMAL(15,2) | Required non-negative listed package-price snapshot. Whether group pricing is per person or per group remains `TBD`. |
| `total_amount` | DECIMAL(15,2) | Required non-negative initial booking-invoice amount derived by the approved package/group rules. Subsequent operational adjustments belong to billing records. |
| `deposit_percentage_snapshot` | DECIMAL(5,2) | Required applicable deposit percentage snapshot, greater than `0` and at most `100`; baseline workflow uses `50`. |
| `deposit_amount` | DECIMAL(15,2) | Required non-negative deposit due snapshot; calculated from the approved invoice basis, percentage and rounding rules. |
| `currency` | CHAR(3) | Required invoice/payment currency snapshot. |
| `confirmation_state` | VARCHAR(30) | Required contact-email confirmation state. Existing candidates: `EMAIL_CONFIRMATION_PENDING`, `CONFIRMED`, `EXPIRED`; Guest bookings start pending. Member confirmation policy remains `TBD`. |
| `status` | VARCHAR(30) | Required booking state, separate from email confirmation. Proposed values: `pending`, `confirmed`, `checked_in`, `completed`, `expired`. Exact state mapping to existing persistence remains `TBD`. |
| `confirmed_at` | DATETIME | Nullable until the booking's required confirmation gates, including verified deposit where applicable, are satisfied. |
| `checked_in_by_user_id` | BIGINT | Nullable foreign key to `users`; authorised Staff actor for recorded group check-in. |
| `checked_in_at` | DATETIME | Nullable; required when check-in is recorded. |
| `actual_participant_count` | INT | Nullable until check-in; non-negative actual participating count, independent of the booked count. |
| `parent_registration_id` | BIGINT | Nullable self-referencing foreign key for a Staff-created continuation booking; must not reference itself or create a cycle. |
| `created_by_user_id` | BIGINT | Nullable foreign key to `users`; records the authenticated creation actor where applicable and is required for a Staff-created continuation. Guest creation does not require a user row. |
| `created_at` | DATETIME | Required booking-creation timestamp. |
| `updated_at` | DATETIME | Required last-update timestamp. |

Indexes:

- Unique `(booking_code)` for lookup/check-in correlation.
- `(member_user_id, created_at)` for Member booking history.
- `(slot_id, status)` for capacity and operational attendance queries.
- `(canonical_email, member_user_id)` for permitted Guest-booking import selection.
- `(parent_registration_id)` for linked continuation history.
- `(confirmation_state, created_at)` for confirmation-state queries; actual token-expiry scanning uses the separate confirmation table's expiry field.

Rules:

- Guests and Members may book; Member linkage is optional and never created merely because a profile and booking email have matching text.
- Select a slot before the booking design path. Validate the selected package/design against location/material capabilities and approved constraints before proceeding.
- Capacity validation and reservation/release must be transactional for the entire group. Do not calculate availability by counting booking rows instead of participants.
- A Guest's pending email confirmation refuses payment/settlement and committed capacity. Its one-time token hash/expiry stays in `booking_email_confirmations` (or its approved logical equivalent), never in this table as plaintext.
- Consuming a valid email token confirms only the email gate; it does not by itself mark the booking deposit paid. Verified gateway deposit confirmation is required for normal booking confirmation and QR-ticket issuance.
- Overdue temporary Guest bookings expire idempotently and release temporary capacity/invoice state under the authentication feature; full integration and state naming still require the booking plan.
- Package and contact snapshots are not rewritten by later catalogue/profile edits. Confirmed bookings are not silently moved or repriced when a slot/package/exception changes.
- Group-level check-in records actor, time and actual count. Per-person attendance, if required, needs separate participant records; it is not represented by one group timestamp.
- Unbooked additional participants and any fee waiver/change are processed through billing/consent/approval records, not by silently changing the original package/deposit snapshot.
- Only Staff create a capacity-checked continuation on customer request. Continuation fee/deposit rules remain `TBD`; parent linkage does not automatically reuse or charge the original deposit. Custody intake/release evidence is outside this table.

### slots

| Column | Type | Constraints / description |
| --- | --- | --- |
| `slot_id` | BIGINT | Primary key. |
| `location_id` | BIGINT | Required logical foreign key to the separately specified `workshop_locations` table. |
| `slot_date` | DATE | Required local operating date at the location. |
| `start_time` | TIME | Required local session start time. |
| `end_time` | TIME | Required local session end time; later than `start_time` for the documented same-day sessions. |
| `capacity` | INT | Nullable until configured; when set, must be non-negative. Null means configuration required; zero means no bookable seats. |
| `is_open` | BOOLEAN | Required; proposed default `FALSE` until the dated session is intentionally opened for booking. |
| `created_at` | DATETIME | Required session-creation timestamp. |
| `updated_at` | DATETIME | Required last-update timestamp. |

Indexes:

- Unique `(location_id, slot_date, start_time)` for duplicate dated-session prevention.
- `(slot_date, is_open, location_id)` for date/location availability browsing.

Rules:

- Documented local sessions are 09:30-12:00, 13:00-15:30 and 16:00-18:30. Exceptions or changes must follow the approved schedule policy; no numeric seat capacity is invented.
- A slot is bookable only when intentionally open, its capacity is configured, applicable exceptions permit it, and sufficient seats remain.
- Remaining seats are derived from consuming registrations/temporary holds under the approved lifecycle, not stored as an independently editable `available_seats` counter.
- Capacity cannot be lowered below existing committed participants without an explicit conflict-resolution procedure. Availability checks must also account for valid temporary holds where that policy applies.
- A slot belongs to one location and can host multiple registrations. Package/material compatibility is not inferred from a single package foreign key on the slot.
- A dated occurrence is not a recurrence rule. Schedule generation, overlap validation and time-zone storage/conversion need the workshop plan; local date/time values must be interpreted consistently using the location's configured time zone.
- Referenced slots are retained for history; closure/rescheduling does not silently delete or move paid bookings.

### workshop_packages

| Column | Type | Constraints / description |
| --- | --- | --- |
| `workshop_package_id` | BIGINT | Primary key. |
| `package_code` | VARCHAR(64) | Required unique stable package reference. |
| `package_name` | VARCHAR(255) | Required public package name. |
| `description` | TEXT | Nullable package content and experience description. |
| `conditions` | TEXT | Nullable participation/package conditions; required conditions must be approved before publication. |
| `duration_minutes` | INT | Nullable positive advertised duration; must fit the eligible session under the approved package/session policy. |
| `price` | DECIMAL(15,2) | Required non-negative current listed package price; pricing unit/group rules remain `TBD`. |
| `currency` | CHAR(3) | Required package currency code. |
| `deposit_percentage` | DECIMAL(5,2) | Required proposed package deposit percentage; baseline default `50`, greater than `0` and at most `100`. Precedence against material-based settings remains `TBD`. |
| `img_url` | TEXT | Nullable package-display image URL or storage reference. |
| `is_published` | BOOLEAN | Required; proposed default `FALSE`. Controls public discoverability and eligibility for new bookings. |
| `created_at` | DATETIME | Required package-creation timestamp. |
| `updated_at` | DATETIME | Required last-update timestamp. |

Indexes:

- Unique `(package_code)` for stable catalogue lookup.
- `(is_published, package_name)` for public package selection.

Rules:

- A package may be used by many registrations. Price/name/deposit changes affect new bookings, not historical booking/invoice snapshots.
- Package materials may be multiple options; a separately specified `workshop_package_materials` relationship is needed rather than one arbitrary `material_id` on the package.
- Branch material availability and branch/package eligibility require separate location relationships; `materials.is_active` alone does not prove local availability.
- Publication does not create session capacity or automatically make the package available at every branch.
- Package images are catalogue media, not Member custom-design references. Multi-image package storage, if required, needs a separate image table.
- AI package-price suggestions do not update the configured selling price automatically. Approval/maintenance authority and pricing formulas remain `TBD`.
- Referenced packages cannot be physically deleted; unpublish them to stop new bookings.

### worshop_exceptions

| Column | Type | Constraints / description |
| --- | --- | --- |
| `workshop_exception_id` | BIGINT | Primary key. The requested table spelling `worshop_exceptions` is retained; naming correction is a separate decision. |
| `location_id` | BIGINT | Nullable logical foreign key to `workshop_locations`; null with no slot means all locations on the exception date under this proposal. |
| `slot_id` | BIGINT | Nullable foreign key to `slots`; set for a single-session exception and null for a whole-day scope. |
| `exception_date` | DATE | Required affected local operating date. A multi-day holiday uses one dated record per day under this proposal. |
| `is_closed` | BOOLEAN | Required; proposed default `TRUE`. Blocks new booking in the matching scope. |
| `capacity_override` | INT | Nullable non-negative replacement capacity for an open affected session; not used when `is_closed` is true. |
| `start_time_override` | TIME | Nullable replacement session start; if used, an end-time override and a specific `slot_id` are also required. |
| `end_time_override` | TIME | Nullable replacement session end; must be later than the corresponding start override. |
| `reason` | TEXT | Required holiday/off-day/schedule-change explanation. |
| `is_active` | BOOLEAN | Required; proposed default `TRUE`. Inactive exceptions do not affect new availability decisions. |
| `created_by_user_id` | BIGINT | Required foreign key to `users`; authorised schedule-exception actor. |
| `created_at` | DATETIME | Required exception-creation timestamp. |
| `updated_at` | DATETIME | Required last-update timestamp. |

Indexes:

- `(exception_date, is_active, location_id)` for date/location exception selection.
- `(slot_id, is_active)` for session-specific exceptions.

Rules:

- Proposed scopes are global/date (both references null), location/date (`location_id` set, no slot), or one dated slot (both references set). For a slot scope, location/date must match the referenced slot.
- One active exception per identical scope/date is proposed. Nullable scope keys mean a naive unique `(location_id, exception_date, slot_id)` index does not enforce this; conditional keys or transactional validation must be designed.
- Proposed resolution chooses the most specific matching scope: slot, then location/date, then global/date. Approval is required for this precedence and whether any broader closure must remain absolute; the actual policy is `TBD`.
- A closed exception has no capacity/time override; an open exception must supply a meaningful capacity or paired time change under this proposal. Range/overlap and committed-capacity validation remain mandatory.
- Creating/changing an exception affects eligibility for new bookings; paid/confirmed registrations require explicit operational handling and are not silently cancelled, moved or refunded.
- This table describes schedule exceptions, not customer cancellations, payment failures, workshop custody or inventory adjustments.

## Custom design request

### custom_design_requests

| Column | Type | Constraints / description |
| --- | --- | --- |
| `custom_design_request_id` | BIGINT | Primary key. |
| `member_user_id` | BIGINT | Required foreign key to `users`. Only a Member can submit a request. |
| `request_description` | TEXT | Required description entered by the Member. |
| `img_url` | TEXT | Required reference-image URL or object-storage path. Each request stores exactly one image. |
| `status` | VARCHAR(20) | Required; defaults to `need_review`. Allowed values: `need_review`, `accepted`, `rejected`. |
| `reviewed_by_user_id` | BIGINT | Nullable foreign key to `users`; set when the request is accepted or rejected. |
| `reviewed_at` | DATETIME | Nullable; set when the request is accepted or rejected. |
| `review_reason` | TEXT | Required when `status` is `rejected`; not required when `status` is `accepted`. |
| `created_at` | DATETIME | Required submission timestamp. |
| `updated_at` | DATETIME | Required last-update timestamp. |

Indexes:

- `(status, created_at)` for the review queue.
- `(member_user_id, created_at)` for a Member's request history.

Rules:

- Lifecycle: `need_review` -> `accepted` or `rejected`.
- A Member cannot edit a request description or its reference images after submission. A rejected request cannot be reviewed again or resubmitted; the Member creates a new request instead.
- This table has no relationship to `ring_designs`, `orders`, pricing, payment, fulfilment, guest requests, or AI analysis.

## Ring designs and component catalogue

Design status: Draft proposal for catalogue models and component-configured designs. The supplied documents describe approved components, price inputs and hard constraints but do not enumerate final component attributes, units, cardinalities or numerical scoring/pricing formulas. The physical fields below are proposed, with unresolved business values retained as `TBD`.

Sources:

- [Report 3 - Actors](../documents/docs/report-3-software-requirement-specification/sections/03-i-overall-requirements/04-user-requirements/01-actors.md) and [Use Cases](../documents/docs/report-3-software-requirement-specification/sections/03-i-overall-requirements/04-user-requirements/02-use-cases.md): available ring models, system-provided configuration components, material/gemstone choices and branch material availability.
- [Report 3 - Ring Design Workflow](../documents/docs/report-3-software-requirement-specification/assets/diagrams/workflows/11-ring-design-triage-swimlane.puml): catalogue/configurator validation against approved components and hard constraints; numeric rules remain `TBD`.
- [Report 1 - Proposed Solution](../documents/docs/report-1-project-introduction/sections/06-iv-proposed-solution/00-overview.md): staff-maintained component price inputs and feasibility constraints.
- The existing **Custom design request** section in this file defines an independent review-only request with no relationship to `ring_designs`, orders, pricing, payment, fulfilment or AI analysis. That explicit boundary is preserved even where broader source diagrams show other image-processing paths.

Design assumptions and unresolved points:

- `ring_designs` represents either a system catalogue model or one customer's configured design/version. A design uses one base material and one base ring shape under this proposal; multi-material construction and other cardinalities remain `TBD`.
- A design may contain multiple gemstone and attachment types. Normalized `ring_design_gemstones` and `ring_design_attachments` junctions are additional dependencies, each with design/component foreign keys and positive quantity; size/placement/variant uniqueness remains `TBD`. The requested five tables alone cannot express those many-to-many selections.
- `attachments` means physical decorative/assembly components on a ring, not uploaded files. `shape` means the base ring-form option, not a gemstone cut. These interpretations are design assumptions to confirm against the final catalogue.
- Component catalogue prices are estimate inputs, not warehouse stock, invoices or approved manufacturing charges. Units, labour charges, rounding, tax and score aggregation remain `TBD`.
- Active component flags apply globally; branch/material availability and package compatibility need additional relationships. A single flag does not prove that a component can be used in every workshop.
- Linking designs to `products`/`order_items`, per-person designs for group bookings, rule-version storage and quote acceptance require separate design. No custom-image request is automatically converted into a ring design or purchasable item.

### ring_designs

| Column | Type | Constraints / description |
| --- | --- | --- |
| `ring_design_id` | BIGINT | Primary key. Proposed identity of a specific design/version. |
| `design_name` | VARCHAR(255) | Required display name for a catalogue model; nullable for a customer's unnamed configured design. |
| `description` | TEXT | Nullable catalogue/design explanation. |
| `design_type` | VARCHAR(30) | Required. Proposed values: `catalogue_model`, `configured_design`; does not include image-based custom requests. |
| `created_by_user_id` | BIGINT | Nullable foreign key to `users`; authorised catalogue author or Member configuration creator when authenticated. Null can represent a permitted Guest workshop configuration. |
| `source_design_id` | BIGINT | Nullable self-referencing foreign key to a catalogue model used as a configuration starting point or to a preceding design version. Must not reference itself or create a cycle. |
| `material_id` | BIGINT | Required foreign key to `materials` under the single-base-material proposal. |
| `shape_id` | BIGINT | Required foreign key to `shape` for the base ring form. |
| `ring_size` | VARCHAR(30) | Nullable selected ring size. Size system, allowed values and the point at which it becomes required remain `TBD`; it is not a free-text substitute for validated size options. |
| `img_url` | TEXT | Nullable catalogue preview/model image. Not a Member custom-request reference image. |
| `component_snapshot` | JSON | Nullable while editing a draft; required once a design is validated/frozen under this proposal. Structured snapshot of chosen components, quantities, relevant attributes and estimate inputs; JSON structure/version remains `TBD`. |
| `estimated_price` | DECIMAL(15,2) | Nullable non-negative system-calculated estimate using approved component/price rules. Not an AI-generated price, fixed order price or amount charged. |
| `currency` | CHAR(3) | Nullable until an estimate exists; required with `estimated_price` and must match its monetary inputs. |
| `difficulty_score` | DECIMAL(10,4) | Nullable non-negative calculated difficulty score. Scale, formula and thresholds remain `TBD`. |
| `rules_version` | VARCHAR(64) | Nullable until evaluation; required with stored estimate/difficulty results under this proposal. Identifies the approved rule set used; its backing rule records remain a separate dependency. |
| `status` | VARCHAR(20) | Required; proposed default `draft`. Proposed values: `draft`, `validated`, `published`, `archived`; `published` is only for a catalogue model. |
| `created_at` | DATETIME | Required design-version creation timestamp. |
| `updated_at` | DATETIME | Required last-update timestamp. |

Indexes:

- `(design_type, status, created_at)` for published-model browsing and configuration queues.
- `(created_by_user_id, created_at)` for permitted creator history.
- `(material_id, shape_id)` for component-based model filtering.
- `(shape_id)` for shape-reference queries and the foreign key.
- `(source_design_id)` for model/version lineage.

Rules:

- Catalogue/configured designs are validated against approved active components, location/package compatibility and hard constraints. Merely selecting existing IDs is not enough to prove feasibility.
- Gemstone and attachment selections use the additional junctions described above. A design with no stone or decoration has no corresponding selection rows, rather than a fake zero-ID component.
- The JSON snapshot preserves the selected configuration and evaluation inputs; it does not replace relational foreign keys, compatibility checks or an approved rule store.
- Proposed lifecycle: draft configurations become `validated`; validated catalogue models may become `published`; retirement uses `archived`. Validation/publication authority and exact transitions remain `TBD`.
- Only published catalogue models are publicly selectable. A customer's configuration is private to its permitted booking/Member journey and is not automatically published.
- Referenced validated designs are frozen versions under this proposal. Changing a referenced model or configuration creates a new version; later component-price edits do not silently rewrite historical selections/estimates.
- A price estimate is informational until the owning billing/order flow snapshots and accepts the payable amount. No automatic pricing, purchase or manufacturing follows design validation.
- Guest configuration storage, access credentials and abandoned-draft retention remain `TBD`; a null creator does not make a design public.
- This table has no `custom_design_request_id`, reference-image review status, AI output or request-review decision. The existing custom-request lifecycle remains separate.

### gemstones

| Column | Type | Constraints / description |
| --- | --- | --- |
| `gemstone_id` | BIGINT | Primary key. |
| `gemstone_code` | VARCHAR(64) | Required unique catalogue-option code. |
| `gemstone_name` | VARCHAR(100) | Required gemstone-option display name. |
| `description` | TEXT | Nullable approved gemstone details. |
| `gemstone_type` | VARCHAR(100) | Nullable stone/material classification; final taxonomy remains `TBD`. |
| `color` | VARCHAR(50) | Nullable catalogue color label. |
| `cut_name` | VARCHAR(100) | Nullable gemstone cut/form label; distinct from the base ring `shape`. |
| `dimensions` | VARCHAR(100) | Nullable descriptive size specification. Supported units/dimensions and structured option modeling remain `TBD`. |
| `unit_price` | DECIMAL(15,2) | Required non-negative current catalogue price for the documented `price_unit`. |
| `price_unit` | VARCHAR(20) | Required explicit pricing unit, for example `piece`. Approved units/conversions remain `TBD`. |
| `currency` | CHAR(3) | Required price currency code. |
| `difficulty_score` | DECIMAL(10,4) | Nullable non-negative configured difficulty contribution; no score or aggregation rule is invented. |
| `img_url` | TEXT | Nullable catalogue-option image URL or storage reference. |
| `is_active` | BOOLEAN | Required; proposed default `FALSE` until the option is approved for new selections. |
| `created_at` | DATETIME | Required option-creation timestamp. |
| `updated_at` | DATETIME | Required last-update timestamp. |

Indexes:

- Unique `(gemstone_code)` for stable option lookup.
- `(is_active, gemstone_type, color)` for supported gemstone filtering.

Rules:

- One gemstone option may be used by many designs through `ring_design_gemstones`; selection quantity must be positive and use compatible units.
- The proposal treats a code as a specific selectable option. Variant combinations, allowable sizes and compatibility with ring form/settings remain `TBD` before validating selection uniqueness.
- Price/score changes affect new evaluations only; historical configured designs keep their snapshots.
- An inactive gemstone cannot be newly selected, but existing design/history references remain valid records. Referenced options are not physically deleted.
- This is component master data, not a stock count, lot/serial record, procurement table or guarantee of branch availability.

### materials

| Column | Type | Constraints / description |
| --- | --- | --- |
| `material_id` | BIGINT | Primary key. |
| `material_code` | VARCHAR(64) | Required unique material-option code. |
| `material_name` | VARCHAR(100) | Required display name of the base ring material. |
| `description` | TEXT | Nullable material characteristics and use conditions. |
| `purity` | VARCHAR(50) | Nullable approved purity/grade label, where relevant; final format remains `TBD`. |
| `unit_price` | DECIMAL(15,2) | Required non-negative catalogue price for one documented pricing unit. |
| `price_unit` | VARCHAR(20) | Required explicit unit, for example `gram` or `piece`; final unit system remains `TBD`. A price is not implicitly a per-ring total. |
| `currency` | CHAR(3) | Required material-price currency code. |
| `difficulty_score` | DECIMAL(10,4) | Nullable non-negative configured difficulty contribution; rule values remain `TBD`. |
| `img_url` | TEXT | Nullable material preview URL or storage reference. |
| `is_active` | BOOLEAN | Required; proposed default `FALSE` until approved for new selections. |
| `created_at` | DATETIME | Required material-creation timestamp. |
| `updated_at` | DATETIME | Required last-update timestamp. |

Indexes:

- Unique `(material_code)` for stable material lookup.
- `(is_active, material_name)` for active-material selection.

Rules:

- A material may be used by many designs and multiple workshop packages. Package-material and branch-material relationships are separately specified dependencies.
- Gram-based pricing requires a validated quantity/weight input and conversion policy. Neither material name nor ring size alone determines a charge without approved estimation rules.
- Branch availability is recorded per location, not as a global stock/availability counter on `materials`.
- Material-based deposit configuration mentioned in Report 3 is unresolved relative to the 50% package baseline; no additional material deposit rule is silently activated here.
- Price/difficulty changes preserve historical snapshots. Retire a referenced material by disabling new selections, not deleting it.
- This table does not track warehouse inventory, purchases, measured manufacturing consumption or live market-metal prices.

### attachments

| Column | Type | Constraints / description |
| --- | --- | --- |
| `attachment_id` | BIGINT | Primary key. Represents a physical ring decoration/assembly component under the stated interpretation. |
| `attachment_code` | VARCHAR(64) | Required unique component-option code. |
| `attachment_name` | VARCHAR(100) | Required component display name. |
| `description` | TEXT | Nullable physical-component description and use conditions. |
| `attachment_type` | VARCHAR(100) | Nullable approved component classification; final taxonomy remains `TBD`. |
| `unit_price` | DECIMAL(15,2) | Required non-negative current price per explicit `price_unit`. |
| `price_unit` | VARCHAR(20) | Required pricing unit, for example `piece`; units remain subject to catalogue approval. |
| `currency` | CHAR(3) | Required price currency code. |
| `difficulty_score` | DECIMAL(10,4) | Nullable non-negative configured effort/difficulty contribution; not an independent feasibility decision. |
| `img_url` | TEXT | Nullable physical-component preview URL or storage reference. |
| `is_active` | BOOLEAN | Required; proposed default `FALSE` until approved for selection. |
| `created_at` | DATETIME | Required component-creation timestamp. |
| `updated_at` | DATETIME | Required last-update timestamp. |

Indexes:

- Unique `(attachment_code)` for stable option lookup.
- `(is_active, attachment_type)` for component selection/filtering.

Rules:

- A design may use multiple attachment types/quantities through `ring_design_attachments`; each selected quantity is positive.
- Allowed attachment/material/shape/gemstone combinations and placement constraints must be approved and validated; being active is not sufficient proof of compatibility.
- Estimates use approved component prices and quantities, with any labour/assembly rules specified separately. AI does not assign final charges or accept an invalid combination.
- Retiring or repricing a component does not rewrite a frozen design snapshot. Referenced attachment options are retained for history.
- This table does not store chat files, Member reference-image uploads or inventory movements.

### shape

| Column | Type | Constraints / description |
| --- | --- | --- |
| `shape_id` | BIGINT | Primary key. Retains the singular table name requested in this document. |
| `shape_code` | VARCHAR(64) | Required unique base ring-form option code. |
| `shape_name` | VARCHAR(100) | Required ring-form display name. |
| `description` | TEXT | Nullable approved form/structural description and constraints. |
| `price_adjustment` | DECIMAL(15,2) | Nullable non-negative configured form-related estimate contribution. Null means no approved standalone price input, not an automatic zero charge. The final pricing model remains `TBD`. |
| `currency` | CHAR(3) | Nullable when there is no monetary contribution; required with `price_adjustment`. |
| `difficulty_score` | DECIMAL(10,4) | Nullable non-negative configured structural-difficulty contribution; formula/thresholds remain `TBD`. |
| `img_url` | TEXT | Nullable ring-form preview URL or storage reference. |
| `is_active` | BOOLEAN | Required; proposed default `FALSE` until the form is approved for new selections. |
| `created_at` | DATETIME | Required ring-form creation timestamp. |
| `updated_at` | DATETIME | Required last-update timestamp. |

Indexes:

- Unique `(shape_code)` for stable ring-form lookup.
- `(is_active, shape_name)` for available-form selection.

Rules:

- One base ring form may be referenced by many designs; one base form per design is a stated proposal, not a documented final cardinality.
- Shape is separate from gemstone cut, ring size and a complete catalogue model. Its exact structural attribute schema remains `TBD`.
- Material, component placement and size compatibility must be checked against approved hard constraints; a numeric score alone cannot override an incompatible design.
- A standalone shape price contribution is included only under an approved estimation rule and must not double-count component/labour prices.
- Form/score/price changes do not rewrite frozen design versions. Disable an option for new selection rather than deleting referenced history.
