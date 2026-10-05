## Controlled values — P2.2

Design status: Proposed physical-design correction for review. This section specifies the constraint design for the logical tables in this document; it does not claim that these constraints already exist in MySQL. Implementation requires an approved physical mapping and a new versioned migration. P2.3–P2.10 remain separate review items.

### Storage decision and alternatives

| Approach | Advantages | Trade-offs | Proposal for this document |
| --- | --- | --- | --- |
| `VARCHAR` with named, enforced `CHECK` constraints | Explicit database validation; readable codes; retains the documented column types and supports JPA string mappings. | Application code and constraint lists must evolve together through reviewed migrations. | Use for the closed status/type/decision domains below. |
| MySQL native `ENUM` | Compact declaration and database-controlled vocabulary. | Couples the vocabulary to the MySQL column type; ORM mappings and changes to the list need care. | Do not use for these columns. Java enums remain useful independently of the SQL type. |
| Reference table with FK | Appropriate for managed catalogue values with labels, metadata and activation rules. | Adds tables/joins; adding a row cannot implement the service behavior for a new workflow state. | Retain existing reference entities such as `roles`; do not introduce generic status tables for these closed domains. |

### Physical enforcement contract

- Each column in the registry below retains its documented `VARCHAR(n)` length and uses `CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin`. This case-sensitive, `NO PAD` collation makes the `IN` checks reject case variants and trailing spaces. Leading spaces, empty strings and misspellings also fall outside the lists. The exact stored spellings are the registry values; UI labels/translations are separate.
- Every registry check must be a named `CONSTRAINT ... CHECK (...) ENFORCED`. All registry columns are `NOT NULL` except `orders.fulfilment_method`, which is explicitly nullable until the existing fulfilment flow supplies a choice. `CHECK` alone does not replace `NOT NULL`: MySQL also accepts a check result of `UNKNOWN` for null expressions.
- A registry default of **none** means no SQL default: the owning flow must supply a value. Do not invent a default for unresolved order creation or Member email-confirmation policy. Existing explicit defaults are retained below.
- Required engine capabilities: enforced checks require MySQL 8.0.16 or later; the proposed `utf8mb4_0900_bin` collation requires MySQL 8.0.17 or later. The feature plan must select and verify a compatible server release, collation and strict SQL mode. These minimum capabilities do not select the production version. Non-strict writes, `INSERT IGNORE`, `UPDATE IGNORE` or disabled check enforcement must not be used to bypass invalid business input.
- Each owning Java domain uses a typed enum with an explicit mapping to these persisted strings. Use `@Enumerated(EnumType.STRING)` only when enum constant names exactly match the stored codes and the physical mapping preserves `VARCHAR`; otherwise use a JPA `AttributeConverter` for the enum/string mapping. Do not combine the two mappings or persist ordinal numbers. API/server-side validation rejects unsupported codes before persistence; the database independently rejects invalid writes.
- The registry is the authoritative vocabulary within this draft. Column descriptions reference its named checks. A value-domain check validates a row's current value; it does not authorize an actor, validate a transition from a previous state, verify a callback or prove consistency with another table. Those existing service rules still apply; detailed transition policy remains P2.10.

Technical basis: [MySQL CHECK constraints](https://dev.mysql.com/doc/refman/8.4/en/create-table-check-constraints.html), [enforced checks introduced in 8.0.16](https://dev.mysql.com/doc/relnotes/mysql/8.0/en/news-8-0-16.html), and [8.0.17 collation changes](https://dev.mysql.com/doc/relnotes/mysql/8.0/en/news-8-0-17.html).

### Closed-domain constraint registry

Every expression below is a table-level `CHECK` body, evaluated using the column collation specified above. Constraint names include their table and column to remain unique within the schema. **None** in the Default column means an explicit value is required; **NULL** is permitted only in the one nullable column.

| Column | Nullability | Default | Constraint name | Exact `CHECK` expression |
| --- | --- | --- | --- | --- |
| `users.status` | `NOT NULL` | `'active'` | `chk_users_status` | `status IN ('active', 'suspended')` |
| `audit_logs.outcome` | `NOT NULL` | none | `chk_audit_logs_outcome` | `outcome IN ('SUCCESS', 'REJECTED', 'FAILED')` |
| `loyalty_points.entry_type` | `NOT NULL` | none | `chk_loyalty_points_entry_type` | `entry_type IN ('earn', 'redeem', 'adjustment', 'expiry')` |
| `orders.status` | `NOT NULL` | none | `chk_orders_status` | `status IN ('pending_payment', 'expired', 'paid', 'preparing', 'ready_for_pickup', 'picked_up', 'prepared_for_carrier', 'handed_to_carrier')` |
| `orders.fulfilment_method` | nullable | `NULL` | `chk_orders_fulfilment_method` | `fulfilment_method IS NULL OR fulfilment_method IN ('pickup', 'carrier')` |
| `payments.payment_purpose` | `NOT NULL` | none | `chk_payments_payment_purpose` | `payment_purpose IN ('retail_full_payment', 'workshop_deposit')` |
| `payments.status` | `NOT NULL` | `'pending'` | `chk_payments_status` | `status IN ('pending', 'succeeded', 'failed', 'cancelled', 'expired')` |
| `payment_transactions.status` | `NOT NULL` | none | `chk_payment_transactions_status` | `status IN ('received', 'succeeded', 'failed', 'cancelled', 'unknown')` |
| `promotions.discount_type` | `NOT NULL` | none | `chk_promotions_discount_type` | `discount_type IN ('percentage', 'fixed_amount')` |
| `workshop_registration.confirmation_state` | `NOT NULL` | none | `chk_workshop_registration_confirmation_state` | `confirmation_state IN ('EMAIL_CONFIRMATION_PENDING', 'CONFIRMED', 'EXPIRED')` |
| `workshop_registration.status` | `NOT NULL` | none | `chk_workshop_registration_status` | `status IN ('pending', 'confirmed', 'checked_in', 'completed', 'expired')` |
| `custom_design_requests.status` | `NOT NULL` | `'need_review'` | `chk_custom_design_requests_status` | `status IN ('need_review', 'accepted', 'rejected')` |
| `custom_design_reviews.decision` | `NOT NULL` | none | `chk_custom_design_reviews_decision` | `decision IN ('accepted', 'rejected')` |
| `ring_designs.design_type` | `NOT NULL` | none | `chk_ring_designs_design_type` | `design_type IN ('catalogue_model', 'configured_design')` |
| `ring_designs.status` | `NOT NULL` | `'draft'` | `chk_ring_designs_status` | `status IN ('draft', 'validated', 'published', 'archived')` |
| `promotion_redemptions.status` | `NOT NULL` | `'reserved'` | `chk_promotion_redemptions_status` | `status IN ('reserved', 'redeemed', 'released')` |
| `promotion_redemptions.discount_type_snapshot` | `NOT NULL` | none | `chk_promotion_redemptions_discount_type_snapshot` | `discount_type_snapshot IN ('percentage', 'fixed_amount')` |
| `loyalty_policies.discount_basis` | `NOT NULL` | none | `chk_loyalty_policies_discount_basis` | `discount_basis IN ('retail_subtotal', 'after_promotions')` |
| `loyalty_redemptions.status` | `NOT NULL` | `'reserved'` | `chk_loyalty_redemptions_status` | `status IN ('reserved', 'redeemed', 'released')` |
| `loyalty_redemptions.discount_basis_snapshot` | `NOT NULL` | none | `chk_loyalty_redemptions_discount_basis_snapshot` | `discount_basis_snapshot IN ('retail_subtotal', 'after_promotions')` |

P2.5 adds two promotion domains. The four-table P2.6 model adds three loyalty domains and extends the original ledger `entry_type` with `expiry`; refund-restoration codes are outside the current design. All other prior domain definitions remain unchanged. A later approved migration must reconcile historical values and compatible readers before changing an implemented vocabulary or enabling writers.

### Vocabulary meaning and compatibility boundaries

- `payments.status` describes one payment attempt. A persisted attempt starts at `pending`; `created` and `initiated` describe operations, not additional persisted codes. `cancelled` preserves the attempt outcome already named in the payment lifecycle; it does not cancel an order/booking or introduce a refund feature. `expired` describes local attempt expiry under the owning payment policy, not necessarily proof that the provider did not collect funds. Deadline, retry and late-success resolution remain separate decisions.
- `payment_transactions.status` is a closed internal callback-result vocabulary, not arbitrary provider text. The provider adapter must explicitly map verified provider codes to it. `received` is not proof of payment; `unknown` means an unrecognized provider outcome and cannot confirm a payment. Provider authenticity, amount/currency/target matching and approved event handling remain required even for a `succeeded` callback row. Provider-specific mapping remains in the gateway contract.
- `workshop_registration.confirmation_state` records only the email gate; `status = confirmed` records the booking's approved confirmation gates. Guest creation explicitly supplies `EMAIL_CONFIRMATION_PENDING`. Email token state belongs to the separate confirmation record. No Member email-gate default is selected here.
- These are logical proposal values, not a drop-in constraint set for the implemented V1 schema. For example, existing `MemberStatus` includes `PENDING_POLICY_ACCEPTANCE`, `ACTIVE`, `SUSPENDED`; existing `ConfirmationState` includes `EMAIL_CONFIRMATION_PENDING`, `EMAIL_CONFIRMED`, `CANCELLED_EMAIL_UNCONFIRMED`; existing `BookingStatus` includes `PENDING`, `CONFIRMED`, `CANCELLED`, `FAILED`. The authentication data model also describes proposed confirmation names. Physical mapping must explicitly reconcile these differences without erasing policy entitlement, email-gate or booking semantics. Do not apply the logical lowercase domains to existing uppercase columns or silently map `FAILED`/`CANCELLED` to `expired`.
- Existing row/business invariants remain mandatory: purpose must match the payment target; published designs must be catalogue models; fulfilment milestones must match the selected method; ledger signs and rejection reasons must satisfy their domain rules. Membership in a valid list alone does not satisfy them.
- `loyalty_points` and `promotions` remain future/conditional modules. Closing their existing vocabularies does not enable redeem, expiry, refunds, stacking or new payment purposes. Those modules require their separately approved designs.
- Managed component classifications, units, variants, placements and ring-size taxonomies have no approved value lists yet. Do not fabricate enums for `gemstone_type`, `attachment_type`, `price_unit`, `variant_code`, `placement` or `ring_size` from examples. Final catalogue design must provide validated option/reference data before those fields are used as controlled selections. `roles` remains its existing reference entity; P2.4 owns its physical FK/activation rules. Audit action/entity code catalogs are separate from this outcome registry.

### Migration and acceptance criteria

- Keep applied V1 migrations immutable. Before a later migration, inventory distinct existing values, nulls, case/whitespace variants and defaults; identify the actual logical-to-physical mappings and resolve every unsupported value explicitly. Never turn an unknown state into success/active automatically. Validate existing data before changing collation or enforcing checks.
- Introduce/remove codes only through a reviewed vocabulary change, updated application mappings and a versioned migration. For additions, expand the database constraint before enabling writers of the new code, and ensure active readers can handle it. Do not remove a historical code until data and readers have an explicitly approved compatibility strategy; stopping new use belongs to service policy.
- Database verification must show all 20 named domain checks as enforced, exact nullability/defaults and the code-column collation. Direct writes must accept each listed code and reject an unlisted code, wrong case, leading/trailing whitespace and the empty string. Required columns reject null; `orders.fulfilment_method` accepts null and both listed choices. Defaults must belong to their domains.
- Application verification must cover enum/string round trips and rejection of unsupported input. All persisted codes in the existing lifecycle examples must belong to their respective registry domains. Invalid transitions between two otherwise valid codes remain service-level checks.
- This documentation correction is ready for review when each of the 20 specified closed-domain columns links to a concrete check and the payment vocabulary is internally consistent. Database enforcement is verified only after the separately approved migration is implemented; this edit does not execute database tests or alter backend code.

## Delete behavior — P2.3

Design status: Proposed physical-design correction for review. This section defines deletion and retirement for all 69 logical foreign keys currently specified in this document. It follows the P2.2 vocabulary registry, including explicitly documented later extensions, and does not apply a migration. P2.4 specifies role-assignment administration; P2.7 specifies audit/payment evidence retention, privacy, access and guarded expiry. Other domain lifetimes and a future physical archive still need their scoped plans.

Sources: existing history/snapshot rules in this document, [ERD retention rules](../documents/ERD.md#6-quy-tắc-không-thể-thể-hiện-chỉ-bằng-đường-nối), [Report 2 scope and data-handling boundaries](../documents/docs/report-2-project-management-plan/sections/02-i-project-overview/01-scope-purpose.md), and the [authentication data model](features/001-member-authentication/data-model.md). The FK actions below are a design proposal; keeping a business row does not authorize retaining its PII or media beyond the applicable approved policy.

### Decision and alternatives

| Approach | Benefit | Limitation in this model | Proposal |
| --- | --- | --- | --- |
| `ON DELETE RESTRICT` plus explicit retirement/history retention | Preserves relationships, actors, snapshots and timelines; blocks deleting a referenced parent. | Does not prevent direct deletion of an unreferenced parent or child row; service and access rules must protect them. | Use for every FK below. |
| `ON DELETE SET NULL` | Allows deleting a parent while retaining the child. | Loses identity/provenance; null already means Guest/system actions, exception scope, payment target or lineage. | No current FK uses it. Nullable does not mean deletable. |
| `ON DELETE CASCADE` for owned children | Simplifies aggregate cleanup. | Can propagate loss of order items, payment evidence or frozen selections; the FK cannot distinguish draft from history. | No current FK uses it. Delete eligible draft/media rows explicitly. |

- **FK delete action** governs a physical parent `DELETE`. Declare each listed named FK with explicit `ON DELETE RESTRICT`, using an engine that enforces the FK contract and keeping FK checks enabled. Do not rely on implicit defaults. [MySQL FK documentation](https://dev.mysql.com/doc/refman/8.4/en/create-table-foreign-keys.html) describes the referential actions.
- **Logical retirement / soft delete** prevents new operational use while preserving the row and its relationships. Use the domain flags/states below, without adding a universal `deleted_at`, another `deleted` status or a synthetic replacement user. Suspension alone restricts access; it is not completed account erasure.
- **Archive** here retains historical rows for authorized history/evidence reads, with updates limited by domain rules. It does not add an `archived` state to orders/bookings/payments or move rows to archive tables. Physical archival must preserve the FK graph and remains P2.7.
- Deactivation does not cascade to child flags/statuses or null their FKs. New-use queries check applicable parent/relationship flags; authorized history queries can still read inactive/unpublished parents. Existing orders, bookings, holds and frozen designs are not silently deleted, cancelled, moved or repriced.

### Foreign-key delete-action registry

This registry is authoritative for existing FK column descriptions. Each row is a separate named constraint referencing the documented parent primary key. Required/nullable definitions follow the current table definitions, including the P2.6 consolidation. `audit_logs.entity_name/entity_id` are a logical polymorphic reference, not a declared FK; the owning service must still preserve their history.

| Child FK column | Referenced parent key | Proposed FK constraint name | `ON DELETE` |
| --- | --- | --- | --- |
| `users.role_id` | `roles.role_id` | `fk_users_role_id` | `RESTRICT` |
| `audit_logs.actor_user_id` | `users.user_id` | `fk_audit_logs_actor_user_id` | `RESTRICT` |
| `loyalty_points.member_user_id` | `users.user_id` | `fk_loyalty_points_member_user_id` | `RESTRICT` |
| `loyalty_points.order_id` | `orders.order_id` | `fk_loyalty_points_order_id` | `RESTRICT` |
| `loyalty_points.recorded_by_user_id` | `users.user_id` | `fk_loyalty_points_recorded_by_user_id` | `RESTRICT` |
| `products.category_id` | `categories.category_id` | `fk_products_category_id` | `RESTRICT` |
| `orders.member_user_id` | `users.user_id` | `fk_orders_member_user_id` | `RESTRICT` |
| `orders.location_id` | `workshop_locations.location_id` | `fk_orders_location_id` | `RESTRICT` |
| `orders.picked_up_by_user_id` | `users.user_id` | `fk_orders_picked_up_by_user_id` | `RESTRICT` |
| `order_items.order_id` | `orders.order_id` | `fk_order_items_order_id` | `RESTRICT` |
| `order_items.product_id` | `products.product_id` | `fk_order_items_product_id` | `RESTRICT` |
| `product_imgs.product_id` | `products.product_id` | `fk_product_imgs_product_id` | `RESTRICT` |
| `payments.order_id` | `orders.order_id` | `fk_payments_order_id` | `RESTRICT` |
| `payments.workshop_registration_id` | `workshop_registration.workshop_registration_id` | `fk_payments_workshop_registration_id` | `RESTRICT` |
| `payment_transactions.payment_id` | `payments.payment_id` | `fk_payment_transactions_payment_id` | `RESTRICT` |
| `delivery_infos.order_id` | `orders.order_id` | `fk_delivery_infos_order_id` | `RESTRICT` |
| `delivery_infos.handed_off_by_user_id` | `users.user_id` | `fk_delivery_infos_handed_off_by_user_id` | `RESTRICT` |
| `workshop_package_locations.workshop_package_id` | `workshop_packages.workshop_package_id` | `fk_workshop_package_locations_workshop_package_id` | `RESTRICT` |
| `workshop_package_locations.location_id` | `workshop_locations.location_id` | `fk_workshop_package_locations_location_id` | `RESTRICT` |
| `material_locations.material_id` | `materials.material_id` | `fk_material_locations_material_id` | `RESTRICT` |
| `material_locations.location_id` | `workshop_locations.location_id` | `fk_material_locations_location_id` | `RESTRICT` |
| `promotions.created_by_user_id` | `users.user_id` | `fk_promotions_created_by_user_id` | `RESTRICT` |
| `promotion_locations.promotion_id` | `promotions.promotion_id` | `fk_promotion_locations_promotion_id` | `RESTRICT` |
| `promotion_locations.location_id` | `workshop_locations.location_id` | `fk_promotion_locations_location_id` | `RESTRICT` |
| `promotion_locations.created_by_user_id` | `users.user_id` | `fk_promotion_locations_created_by_user_id` | `RESTRICT` |
| `workshop_registration.member_user_id` | `users.user_id` | `fk_workshop_registration_member_user_id` | `RESTRICT` |
| `workshop_registration.slot_id` | `slots.slot_id` | `fk_workshop_registration_slot_id` | `RESTRICT` |
| `workshop_registration.workshop_package_id` | `workshop_packages.workshop_package_id` | `fk_workshop_registration_workshop_package_id` | `RESTRICT` |
| `workshop_registration.ring_design_id` | `ring_designs.ring_design_id` | `fk_workshop_registration_ring_design_id` | `RESTRICT` |
| `workshop_registration.checked_in_by_user_id` | `users.user_id` | `fk_workshop_registration_checked_in_by_user_id` | `RESTRICT` |
| `workshop_registration.parent_registration_id` | `workshop_registration.workshop_registration_id` | `fk_workshop_registration_parent_registration_id` | `RESTRICT` |
| `workshop_registration.created_by_user_id` | `users.user_id` | `fk_workshop_registration_created_by_user_id` | `RESTRICT` |
| `slots.location_id` | `workshop_locations.location_id` | `fk_slots_location_id` | `RESTRICT` |
| `workshop_package_materials.workshop_package_id` | `workshop_packages.workshop_package_id` | `fk_workshop_package_materials_workshop_package_id` | `RESTRICT` |
| `workshop_package_materials.material_id` | `materials.material_id` | `fk_workshop_package_materials_material_id` | `RESTRICT` |
| `workshop_exceptions.location_id` | `workshop_locations.location_id` | `fk_workshop_exceptions_location_id` | `RESTRICT` |
| `workshop_exceptions.slot_id` | `slots.slot_id` | `fk_workshop_exceptions_slot_id` | `RESTRICT` |
| `workshop_exceptions.created_by_user_id` | `users.user_id` | `fk_workshop_exceptions_created_by_user_id` | `RESTRICT` |
| `custom_design_requests.member_user_id` | `users.user_id` | `fk_custom_design_requests_member_user_id` | `RESTRICT` |
| `custom_design_requests.reviewed_by_user_id` | `users.user_id` | `fk_custom_design_requests_reviewed_by_user_id` | `RESTRICT` |
| `custom_design_reviews.custom_design_request_id` | `custom_design_requests.custom_design_request_id` | `fk_custom_design_reviews_custom_design_request_id` | `RESTRICT` |
| `custom_design_reviews.reviewer_user_id` | `users.user_id` | `fk_custom_design_reviews_reviewer_user_id` | `RESTRICT` |
| `ring_design_gemstones.ring_design_id` | `ring_designs.ring_design_id` | `fk_ring_design_gemstones_ring_design_id` | `RESTRICT` |
| `ring_design_gemstones.gemstone_id` | `gemstones.gemstone_id` | `fk_ring_design_gemstones_gemstone_id` | `RESTRICT` |
| `ring_design_attachments.ring_design_id` | `ring_designs.ring_design_id` | `fk_ring_design_attachments_ring_design_id` | `RESTRICT` |
| `ring_design_attachments.attachment_id` | `attachments.attachment_id` | `fk_ring_design_attachments_attachment_id` | `RESTRICT` |
| `ring_designs.created_by_user_id` | `users.user_id` | `fk_ring_designs_created_by_user_id` | `RESTRICT` |
| `ring_designs.source_design_id` | `ring_designs.ring_design_id` | `fk_ring_designs_source_design_id` | `RESTRICT` |
| `ring_designs.material_id` | `materials.material_id` | `fk_ring_designs_material_id` | `RESTRICT` |
| `ring_designs.shape_id` | `shape.shape_id` | `fk_ring_designs_shape_id` | `RESTRICT` |
| `promotion_redemptions.promotion_id` | `promotions.promotion_id` | `fk_promotion_redemptions_promotion_id` | `RESTRICT` |
| `promotion_redemptions.order_id` | `orders.order_id` | `fk_promotion_redemptions_order_id` | `RESTRICT` |
| `promotion_redemptions.member_user_id` | `users.user_id` | `fk_promotion_redemptions_member_user_id` | `RESTRICT` |
| `promotion_redemptions.location_id` | `workshop_locations.location_id` | `fk_promotion_redemptions_location_id` | `RESTRICT` |
| `promotion_redemptions.redeemed_payment_id` | `payments.payment_id` | `fk_promotion_redemptions_redeemed_payment_id` | `RESTRICT` |
| `payment_promotion_redemptions.payment_id` | `payments.payment_id` | `fk_payment_promotion_redemptions_payment_id` | `RESTRICT` |
| `payment_promotion_redemptions.promotion_redemption_id` | `promotion_redemptions.promotion_redemption_id` | `fk_payment_promotion_redemptions_promotion_redemption_id` | `RESTRICT` |
| `loyalty_points.loyalty_policy_id` | `loyalty_policies.loyalty_policy_id` | `fk_loyalty_points_loyalty_policy_id` | `RESTRICT` |
| `loyalty_points.loyalty_redemption_id` | `loyalty_redemptions.loyalty_redemption_id` | `fk_loyalty_points_loyalty_redemption_id` | `RESTRICT` |
| `loyalty_points.source_credit_entry_id` | `loyalty_points.loyalty_point_id` | `fk_loyalty_points_source_credit_entry_id` | `RESTRICT` |
| `loyalty_policies.created_by_user_id` | `users.user_id` | `fk_loyalty_policies_created_by_user_id` | `RESTRICT` |
| `loyalty_redemptions.order_id` | `orders.order_id` | `fk_loyalty_redemptions_order_id` | `RESTRICT` |
| `loyalty_redemptions.member_user_id` | `users.user_id` | `fk_loyalty_redemptions_member_user_id` | `RESTRICT` |
| `loyalty_redemptions.loyalty_policy_id` | `loyalty_policies.loyalty_policy_id` | `fk_loyalty_redemptions_loyalty_policy_id` | `RESTRICT` |
| `loyalty_redemptions.redeemed_payment_id` | `payments.payment_id` | `fk_loyalty_redemptions_redeemed_payment_id` | `RESTRICT` |
| `loyalty_allocations.loyalty_redemption_id` | `loyalty_redemptions.loyalty_redemption_id` | `fk_loyalty_allocations_loyalty_redemption_id` | `RESTRICT` |
| `loyalty_allocations.credit_entry_id` | `loyalty_points.loyalty_point_id` | `fk_loyalty_allocations_credit_entry_id` | `RESTRICT` |
| `loyalty_allocations.debit_entry_id` | `loyalty_points.loyalty_point_id` | `fk_loyalty_allocations_debit_entry_id` | `RESTRICT` |
| `payments.loyalty_redemption_id` | `loyalty_redemptions.loyalty_redemption_id` | `fk_payments_loyalty_redemption_id` | `RESTRICT` |

P2.5 adds eight relationships to the original 49-FK registry; existing delete actions remain unchanged. Reservation/payment links receive the same history protection.
The four-table P2.6 model adds 12 relationships for policies, credit provenance, shared hold/consumption allocations and the direct payment-generation FK, for 69 FKs overall. Existing FK delete actions remain unchanged; removed split-table/refund relationships are not part of this registry. Generated uniqueness keys do not create additional FKs.

### Retirement and history rules by group

| Group | Normal removal/retirement behavior | History and physical-delete boundary |
| --- | --- | --- |
| Users / roles | Restrict access with `users.status = suspended`; retire a role with `roles.is_active = FALSE`. | Retain user/role keys and actor/subject FKs, even when nullable. Null retains its original Guest/system/no-actor meaning. Account erasure requires separate PII handling; users assigned an inactive role remain P2.4. |
| Products / categories / product images | Unpublish products; deactivate categories. Authorized maintainers may explicitly remove/replace catalogue image rows. | Keep historical order-item product FKs/snapshots. Category reassignment requires the permitted catalogue flow; never physically delete a referenced category/product. Removing an image reference does not authorize deleting shared storage objects/history media. |
| Orders / order items / delivery / payments | Retain records through existing lifecycles; unpaid hold expiry changes state/releases resources without deleting rows. | No routine deletion, including expired/unpaid orders or unsuccessful payment attempts. Keep items, payment targets, actors/timestamps and callbacks. Policy-driven PII redaction must preserve non-PII financial/operational rows and required evidence. |
| Bookings / slots / packages / branches / exceptions | Keep bookings, including expired ones. Close new slot selection with `is_open = FALSE`; unpublish packages; deactivate locations/exceptions and package-location/material-location links. | Preserve Member, slot/package/design, check-in and continuation FKs. Never null an exception location/slot and broaden its scope. Parent retirement does not cancel, relocate or purge a booking. |
| Designs / components / package-material links | Retire retained designs with existing `status = archived`; deactivate component options. Disable package-material compatibility through the proposed relationship `is_active` flag. | Preserve frozen selections, component FKs and source lineage. Explicit selection removal is allowed only while a design is editable; validated/frozen/referenced designs keep their selections. Guest designs remain private in history. |
| Custom requests / review timeline | Keep submitted request metadata/current decision and append-only reviews; do not invent another request status. | Preserve Member/reviewer/request links. Policy-driven image/PII erasure does not cascade request/review history; storage-reference handling follows that policy. The review-only boundary stays unchanged. |
| Promotions / promotion-location links | Deactivate `promotions.is_active`; disable branch assignment with proposed `promotion_locations.is_active`. | Keep campaign, assignment and creator keys. Disabling affects new eligibility only, never historical discounts. P2.5 reservations/redemptions and attempt links are retained with restricted FKs; releasing unused quota does not delete evidence. |
| Audit logs / payment callbacks / loyalty history | Keep append-only evidence/ledger rows and authorized history reads; retire a published loyalty policy with `is_active = FALSE`. | No routine deletion, actor nulling, cascade or rewriting posted entries. Preserve policy versions, lots, holds, shared allocation evidence and immutable payment-generation bindings. Only P2.6 one-time settlement may attach a debit/time to a held allocation. Retention/redaction/physical archive/purge remain P2.7. |

The relationship `is_active` flags added below supply the missing retirement mechanism in `promotion_locations` and `workshop_package_materials`. They add no redemption/pricing/capacity rules. Audit flag changes; a retained current row is not a complete activation/deactivation timeline.

### Physical deletion, ORM and archive boundaries

- Business-history and identity rows have no routine hard-delete operation. FK protection is insufficient for leaf rows: deleting an order item, review, callback, ledger or audit row can violate history rules without violating an FK. Owning services and database access policy must protect them; expiry is a state/resource operation, not cleanup by `DELETE`.
- Explicit hard deletion is limited to eligible mutable draft selections/catalogue image references and authorized cleanup of never-used catalogue configuration or private draft designs. A removable draft must still be `draft`, never frozen/validated or used by a booking/derived design, with no other history dependency. Never-used configuration must have no external references or usage/audit-history obligation; absence of incoming FKs alone does not prove eligibility. Retire the row if eligibility cannot be established. Identity, transaction, submitted-request, promotion, audit and ledger records are excluded from this cleanup exception.
- An eligible draft-design cleanup explicitly deletes its mutable selections before the parent in a guarded atomic operation, keeping FK checks enabled and rejecting concurrent references/state changes. Do not enable cascade for every design. Cleanup authority/audit follows the owning feature; full transaction design remains P2.8.
- JPA must not use `CascadeType.REMOVE`, delete behavior through `CascadeType.ALL`, `orphanRemoval = true` or bulk-delete paths on retained relationships. Draft selection removal/image replacement uses explicit guarded owning-service operations. Parent deletion must not bypass `RESTRICT` by deleting historical children first.
- PII/media erasure is separate from row deletion/retirement. Preserve required keys and non-PII financial/capacity/audit facts, honor approved data-handling policy and do not treat archive as an exemption. This section does not choose retention durations, anonymization fields or replacement values for required fields.
- Until physical archive/purge is approved, retain history in the current tables. A future archive must preserve stable IDs, relationships, event/idempotency lookup and history access. Copying a parent to an archive table does not satisfy a live child's FK; deleting its live parent still fails. Do not disable FK checks or detach/null history to make archive deletion succeed.
- P2.7 proposes a narrowly authorized exception for expired leaf audit/callback evidence and prohibited-PII cleanup, with retention/hold/reference checks and permanent payment-attempt retirement before callback-key purge. It does not authorize routine business-history deletion, ledger erasure or parent/FK cleanup. Applied schema/actions remain unchanged until its implementation is approved.

### Migration and acceptance criteria

- Keep applied V1 migrations immutable. Map the registry to physical tables, inventory incoming references/existing FK actions and reject orphan data before a new versioned migration. V1 omits explicit `ON DELETE` clauses; `member_auth_audit_events.member_id` has no declared FK. This proposal does not imply that all 69 logical relationships exist or that a missing physical relationship has been repaired.
- Implementation must expose all 69 mapped constraints with `DELETE_RULE = RESTRICT`, correct parent keys and the documented required/nullable shapes. No listed FK uses `CASCADE` or `SET NULL`; role indexing and assignment/deactivation enforcement remain P2.4.
- For each FK, database verification attempts deletion of a referenced parent and verifies rejection with child rows/FK values unchanged, including self-references and populated nullable FKs. Null is permitted only for the documented original condition, never as a delete side effect.
- Service verification covers retirement flags/states, authorized history access, rejection of protected child deletes even when the FK permits them, and retention of expiry/payment evidence. Eligible draft/media cleanup must preserve historical aggregates and reject concurrent references/state changes.
- Inactive promotion-location/package-material links must be excluded from new eligibility without rewriting existing snapshots; flag changes must be auditable. Storage erasure and physical archive/purge need their separately approved policies/checks.
- P2.3 is ready for documentation review when every specified FK appears in this registry and each requested group has a removal/history rule. Database/ORM enforcement requires later approved implementation; this edit does not run a migration or claim database-test results.

## Single-role constraints — P2.4

Design status: Proposed correction for review, extending the existing single-role design and P2.3 FK registry. This section specifies `users.role_id`, assignment eligibility and role retirement; it does not implement the schema or complete P2.5–P2.10. Role-code reconciliation, the detailed administrative permission matrix and logical-to-physical account mapping remain separate approval decisions.

### Physical constraint contract

| Item | Proposed definition | Purpose / boundary |
| --- | --- | --- |
| Required role | `users.role_id BIGINT NOT NULL`, no SQL default; same integer type/signedness as `roles.role_id`. | Every provisioned account references exactly one role. Guest has no user row; never represent Guest with a null role or synthetic role assignment. |
| Explicit role index | Non-unique `idx_users_role_id (role_id)`. | Supports the FK, role-member lookup and the in-use check. Many users may share a role; this column must not be unique. Declare it explicitly instead of relying on an automatically created FK index. |
| Role FK | `fk_users_role_id`: `users(role_id)` references `roles(role_id)`, with `ON DELETE RESTRICT` and `ON UPDATE RESTRICT`. | Uses the same named relationship as P2.3, not an additional duplicate FK. A referenced role cannot be deleted; stable surrogate role IDs are not changed. |
| Role activation | `roles.is_active BOOLEAN NOT NULL DEFAULT TRUE`, as already proposed. | Existing FK proves existence only; assignment eligibility is checked by the owning transactional service using the lock protocol below. |
| Provisioning role | Resolve the approved `MEMBER` record by its stable `role_code` and verify it is active before provisioning. | Do not hard-code a numeric role ID, use a SQL default role ID, trust Firebase role claims or fall back to a different role when MEMBER is missing/inactive. |

The FK does not enforce `roles.is_active = TRUE`. MySQL CHECK expressions cannot reference another table or use a subquery. Index/FK/nullability are database constraints; the active-role and in-use rules below are application invariants protected by database row locks. Technical basis: [MySQL FK constraints and indexes](https://dev.mysql.com/doc/refman/8.4/en/create-table-foreign-keys.html) and [CHECK limitations](https://dev.mysql.com/doc/refman/8.4/en/create-table-check-constraints.html).

### Assignment enforcement and alternatives

| Approach | Benefit | Cost / limitation | Proposal |
| --- | --- | --- | --- |
| FK plus owning JPA service, transaction and pessimistic row locks | Fits the modular monolith/JPA ownership model; keeps authorization, eligibility and audit together. | Every provisioning, reassignment, import, job and role-toggle writer must follow the same protocol; arbitrary privileged SQL is outside its active-role guarantee. | Use for this design. Direct role/activation writes outside the approved workflow are prohibited. |
| FK plus MySQL triggers for assignment and deactivation, with service authorization | Can enforce additional rules for direct database writes. | Requires versioned trigger definitions, concurrency/lock-order verification and coordinated service/database rule maintenance; a trigger alone does not authorize the actor. | Not selected for this proposal. Revisit explicitly if independent direct writers must be supported. |

- The owning identity/authorization application service handles both provisioning/role assignment and role activation changes. Web/profile-update endpoints do not bind a caller-supplied `role_id` or `is_active` directly to an entity. Other modules use the owning facade; a generic repository save, bulk import or batch update must not bypass these checks.
- A new assignment or replacement requires an existing active target role, an independently authorized actor and the existing single-role/separation-of-duties rules. Check the acting user's current MySQL status/role and permission, not provider claims or a stale role cached at sign-in. No account may acquire a second simultaneous role; update its one `role_id` and retain the old/new assignment in audit.
- The same-role request is a no-op only after authorization and current active-role validation; it does not create a second assignment. An inactive current role found in legacy/inconsistent data must not be treated as an authorized no-op. Deny protected access and repair through an explicitly authorized reassignment to an active role, without automatic reactivation or privilege fallback.
- Active-role assignment does not activate a suspended account or confer Member entitlement without current policy acceptance. Changing role preserves account status, Firebase UID and business-history references.

### Deactivating a role that is in use

| Policy | Consequence | Proposal |
| --- | --- | --- |
| Reject while any user references the role | Preserves the invariant that every current user assignment points to an active role; requires explicit reassignment first. | Select this policy. Count all users, including suspended ones. |
| Deactivate and retain assignments, denying those users access | Quickly revokes a role but leaves assigned inactive roles and can lock out a whole group. | Not selected; conflicts with the draft's active-assignment invariant. Suspend an individual account for account-level access restriction. |
| Automatically migrate all users to a fallback role | Allows retirement in one operation. | Not selected; can silently change privileges/Member eligibility and lacks an approved mapping. Use individually authorized role changes. |

- For an active role, deactivate only when the current in-use lookup finds **no** `users.role_id` reference. Otherwise reject with a stable safe business conflict such as `ROLE_IN_USE`; leave the role, users and their account statuses unchanged. Suspended users still retain an assignment and block deactivation.
- Reassign affected users to separately approved active roles first, auditing each change, then retry deactivation. Do not null assignments, delete users, invent a fallback, auto-suspend a group or physically delete the role. P2.3 retirement is therefore `is_active = FALSE` only after this eligibility gate.
- An authorized deactivation of an already inactive, unused role is an idempotent no-op. An inactive role that still has users is inconsistent legacy/bypassed state and requires explicit repair, not silent success. Reactivation changes only the role flag; it never reassigns users, unsuspends accounts or restores policy entitlement.
- Historical assignment/actor evidence remains valid when an unused role is deactivated. The current `role_id` describes current assignment; old roles in historical audit remain historical facts, not current authorization grants. The precise administrator role-code/permission mapping remains `TBD`; default-deny the operation until that capability is explicitly authorized.

### Shared concurrency and audit protocol

1. Perform the role-related write inside one owning-service transaction. Acquire `PESSIMISTIC_WRITE` locks on the involved role rows before assignment/deactivation eligibility checks, and hold them until commit/rollback. Every operation capable of adding, replacing or removing a current assignment, including provisioning, must participate; the target-role row is the common serialization point with deactivation.
2. Use one lock order across these workflows: involved role IDs in ascending order, then involved existing user IDs in ascending order. Include current/target roles and the acting user's authorization role as applicable. Preliminary reads may discover IDs only; after locks, reload/refresh current role flags, user assignments/status and actor authority. If an assignment changed to a role outside the locked set, roll back and restart with the correct set rather than continuing from stale data. Do not use a cached JPA entity as fresh eligibility evidence.
3. Assignment holds the target-role lock, verifies it remains active, locks/rechecks the account when it exists, and writes its role plus `updated_at`. Provisioning inserts with the verified target role and retains UID uniqueness/idempotency; a missing/inactive MEMBER role aborts local provisioning with a controlled configuration error, without creating a roleless account.
4. Deactivation holds the role lock and performs a **current locking lookup** for any user referencing that role through `idx_users_role_id`, without filtering account status. A JPA locking query returning at most one matching user is sufficient; a non-locking `COUNT`/`exists` from an older transaction snapshot is not sufficient. Do not use `SKIP LOCKED` to decide a role is unused. With all assignment writers participating, an assignment committed first makes deactivation reject; a deactivation committed first makes subsequent assignment reject.
5. A successful reassignment records `ASSIGN_ROLE` with target user, actor, old/new role IDs, timestamp and correlation ID in the applicable audit record. Activation changes record stable `DEACTIVATE_ROLE`/`REACTIVATE_ROLE` action codes and old/new flag. Commit each state change and its successful audit evidence together; if either fails, roll back both. Existing security rules still record rejected unauthorized/self-benefit attempts; rejection/failure evidence must not disappear with the failed business transaction. Broader audit transaction design remains P2.8, JSON schema design P2.9.
6. On deadlock/lock timeout, roll back and return a controlled retryable conflict or perform a bounded retry that repeats authorization/eligibility checks. Do not retry a stale write without locks. Role toggles and same-role retries must not duplicate business effects or report success before commit.

This is a design requirement for the future role service, not a claim about current locking behavior. [Spring Data JPA locking](https://docs.spring.io/spring-data/jpa/reference/jpa/locking.html) supports repository lock metadata; [MySQL locking reads](https://dev.mysql.com/doc/refman/8.4/en/innodb-locking-reads.html) distinguish current locking reads from snapshot reads. The implementation must verify actual lock/query behavior on the selected MySQL release. Protected operations continue to resolve current account status, active role and entitlement through the existing MySQL authorization boundary.

### Compatibility and acceptance criteria

- Existing V1 `members` has no `role_id` or `roles` FK; older authentication planning proposes `accounts/account_roles`. This section preserves the requested `users.role_id` single-role design, but does not silently modify those artifacts or infer that their migration has happened. The feature plan must reconcile the physical account mapping before adding these constraints; applied migrations stay immutable.
- Inventory legacy assignments, null/missing/inactive targets and any multiple-role grants before backfill. Resolve each conflict explicitly and preserve identity/history/policy semantics; never pick an arbitrary role or privilege precedence. Backfill and validate before enforcing `NOT NULL`/FK and switching authorization reads. Admin capability/role-code reconciliation cannot be inferred from display names.
- Schema verification must show one required `role_id`, no numeric SQL default, explicit non-unique `idx_users_role_id`, and `fk_users_role_id` referencing the correct role key with `DELETE_RULE = RESTRICT` and `UPDATE_RULE = RESTRICT`. Null/missing-role writes and deletion of a referenced role must fail at the DB; multiple users referencing one role must succeed. Do not claim that direct SQL assigning an existing inactive role fails under this FK-only database contract.
- Owning-service verification must reject inactive target roles for provisioning/reassignment, reject role deactivation with any referenced active **or suspended** user, permit deactivation after all users are explicitly reassigned, and preserve user status/UID/history. Protected access must fail closed for an inactive assigned role in legacy data. Reactivation and same-role/no-op retries must obey authorization and avoid duplicate changes.
- Use two independent MySQL transactions to verify both assignment/deactivation commit orders, reassignment races and fresh in-use reads; neither outcome may leave a committed user pointing to an inactive role. Audit-write failure must roll back the successful-state attempt. Permission denial/self-privilege escalation must leave role/assignment unchanged and retain the required rejection evidence.
- Documentation acceptance: all five P2.4 requirements have concrete definitions, the P2.3 FK name/action is reused, and users/roles descriptions below reference this protocol. This edit changes documentation only; migration, schema/service tests and permission-matrix approval remain implementation prerequisites.

## Users and roles

Design status: Draft proposal. Physical column names, types, lengths, indexes and defaults are proposed unless explicitly identified as existing implementation details.

Sources:

- [General Spec - Identity and Authorization](spec-general.md): Firebase UID identity, MySQL role/status authority, optional profile snapshots and no local password store.
- [Member Authentication Specification](features/001-member-authentication/spec.md) and [Data Model](features/001-member-authentication/data-model.md): current `members` persistence, proposed `accounts`/`account_roles`, policy acceptance and Guest-booking linkage.
- [Report 3 - Actors](../documents/docs/report-3-software-requirement-specification/sections/03-i-overall-requirements/04-user-requirements/01-actors.md) and [Use Cases](../documents/docs/report-3-software-requirement-specification/sections/03-i-overall-requirements/04-user-requirements/02-use-cases.md): profile, role assignment and account locking.

Design assumptions and unresolved points:

- `users` remains the logical account name used by this document. Its physical mapping to existing `members` and proposed `accounts` is `TBD`; this proposal does not rename or migrate those tables.
- Each provisioned user has exactly one active business role at a time. This proposal uses `users.role_id` as the required foreign key to `roles`; it does not use a multi-role `user_roles`/`account_roles` junction. A role change replaces the current role and must be auditable; historical role assignments remain in the separate role-change audit records.
- The working agreement/authentication feature names `MEMBER`, `STAFF`, `OWNER`, `ADMIN_TECHNICAL`; Report 3 names Member, Staff, Manager and Admin. Role-code reconciliation and the permission matrix remain `TBD`.
- Policy acceptance and role-change audit remain separate versioned records, not profile flags. Existing policy/auth-audit tables are outside the tables requested here.

### users

| Column             | Type         | Constraints / description                                                                                                                                                                                                            |
| ------------------ | ------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `user_id`          | BIGINT       | Primary key; logical identity referenced by this document's user foreign keys.                                                                                                                                                       |
| `external_user_id` | VARCHAR(128) | Required unique Firebase UID, obtained only from a server-verified identity token. Never derived from email.                                                                                                                         |
| `email`            | VARCHAR(255) | Nullable contact/profile snapshot; not a unique identity or account-merge key.                                                                                                                                                       |
| `email_verified`   | BOOLEAN      | Required; defaults to `FALSE`. Snapshot of Firebase email verification, not proof that a booking's contact email was confirmed.                                                                                                      |
| `display_name`     | VARCHAR(255) | Nullable basic profile/display name.                                                                                                                                                                                                 |
| `phone_number`     | VARCHAR(30)  | Nullable profile phone; stored as text. Required contact fields are enforced by the booking/order flow, not by identity provisioning.                                                                                                |
| `photo_url`        | TEXT         | Nullable profile-image URL or storage reference.                                                                                                                                                                                     |
| `role_id`          | BIGINT       | `NOT NULL`, no SQL default; `fk_users_role_id` references `roles.role_id` with `ON DELETE RESTRICT ON UPDATE RESTRICT`. Non-unique `idx_users_role_id (role_id)`. Assignment requires an active role under the P2.4 locking protocol; Guest has no user row. |
| `status`           | VARCHAR(30)  | `NOT NULL DEFAULT 'active'`; enforced `chk_users_status` from the P2.2 registry. Member entitlement still requires active role and current policy acceptance. Mapping to existing Member states is `TBD`. |
| `created_at`       | DATETIME     | Required account-creation timestamp.                                                                                                                                                                                                 |
| `updated_at`       | DATETIME     | Required last-update timestamp.                                                                                                                                                                                                      |

Indexes:

- Unique `(external_user_id)` for UID lookup and idempotent provisioning.
- Explicit non-unique `idx_users_role_id (role_id)` for role lookup, the FK and P2.4's current in-use check; multiple users may share a role.
- Non-unique `(email)` for permitted contact lookup; it must not trigger automatic linking or merging.
- `(status, created_at)` for account-administration queries.

Rules:

- Firebase owns passwords, social credentials and token renewal. This table has no password/hash, provider access token, Firebase ID token or refresh-token column.
- Repeated/concurrent sign-in with the same verified UID provisions one account. Different UIDs remain different accounts even when email text matches.
- MySQL account status and the single active role determine authorization. Provider claims and profile fields cannot grant business roles. An inactive role or suspended account cannot authorize protected actions.
- A user cannot simultaneously hold `MEMBER` and `STAFF` (or any other second business role). A role change replaces the current assignment and requires active-target validation, the shared role/user lock protocol and atomic audit under P2.4; it is not an additional role grant. Provisioning applies the same gate and never falls back to another role when MEMBER is unavailable.
- Separation of duties is mandatory: the creator of a design, order, voucher, promotion or other business record cannot review, approve, discount, redeem or otherwise authorize that same record for personal benefit.
- Review/approval services must reject self-review and self-benefit when the acting user matches the record creator, `member_user_id`, voucher/promotion creator or another recorded beneficiary. The rejection and attempted action must be recorded in the applicable security/audit log.
- Public Guests do not require a synthetic `users` row; `workshop_registration.member_user_id` may remain null.
- Guest-booking import requires verified matching email, explicit Member confirmation and entitlement; it preserves booking status and is idempotent.
- Account suspension blocks protected access without deleting orders, payments, requests or bookings. Account/PII retention and status-change audit policy remain subject to the owning feature.

Guest identity and contact data are booking-scoped rather than account-scoped. A Guest does not receive a synthetic `users` row. The Guest's submitted name, email and phone are stored in `workshop_registration` as booking contact data and must not be used as an automatic identity-link or account-merge key. Access to Guest contact data is limited to the booking/payment/operational purposes that require it; retention, masking and deletion must follow the approved booking-record retention policy.

Guest-data handling rules:

- `workshop_registration.contact_name`, `contact_email`, `canonical_email` and `contact_phone` are the authoritative Guest contact snapshot for that booking. They are not copied into `users` merely because an email matches an existing account.
- Booking-management and payment-support operations may read the minimum Guest fields needed for notification, confirmation, payment reconciliation, capacity operations and check-in. Catalogue or design-maintenance operations do not receive Guest contact data by default.
- Guest contact values must be masked in general operational lists and logs where the full value is not required. Security/audit logs must not store raw email or phone values when a stable booking/user reference is sufficient.
- After the approved booking-record retention period, Guest PII must be deleted or irreversibly anonymized while preserving non-PII financial, capacity and audit facts required for business history. The exact retention period and anonymization fields remain an approval decision, not an implicit database default.
- A Guest-to-Member import requires verified control of the email address, explicit Member confirmation and an idempotent operation. It must record the link decision and actor; an email text match alone is never sufficient.
- Guest contact data must not be used to grant Member pricing, loyalty entitlement, role privileges or voucher eligibility before the account link and policy checks are complete.

### roles

| Column        | Type         | Constraints / description                                                                                                                                           |
| ------------- | ------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `role_id`     | BIGINT       | Primary key.                                                                                                                                                        |
| `role_code`   | VARCHAR(50)  | Required unique stable business-role code. Working-agreement candidates: `MEMBER`, `STAFF`, `OWNER`, `ADMIN_TECHNICAL`; reconciliation with Report 3 remains `TBD`. |
| `role_name`   | VARCHAR(100) | Required human-readable role label; changing the label does not change the role code or permissions.                                                                |
| `description` | TEXT         | Nullable description of the role's business responsibility and boundary.                                                                                            |
| `is_active`   | BOOLEAN      | `NOT NULL DEFAULT TRUE`. Inactive roles cannot authorize actions or receive new assignments. Setting `FALSE` requires no current user references, including suspended users, under P2.4's locking protocol. |
| `created_at`  | DATETIME     | Required role-creation timestamp.                                                                                                                                   |
| `updated_at`  | DATETIME     | Required last-update timestamp.                                                                                                                                     |

Indexes:

- Unique `(role_code)` for authoritative role lookup.
- `(is_active, role_code)` for active-role administration.

Rules:

- Role definitions are separate master records, and each user references exactly one role through `users.role_id`; roles must not be stored as comma-separated text or JSON.
- First-time Member provisioning assigns the `MEMBER` role under the approved entitlement flow. Assigning `STAFF`, `OWNER` or `ADMIN_TECHNICAL` replaces the previous role and requires an authorized, audited role-change operation.
- Guest is an unauthenticated actor, not a role granted to a provisioned account.
- `STAFF` may review or approve eligible Member records, but never records created by or belonging to the acting Staff user. A Staff user cannot combine Staff privileges with Member entitlements through a second role.
- Permissions are not inferred from `role_name`. Dynamic permission configuration would require a separately specified permission model; this table alone does not implement RBAC configuration.
- Referenced roles cannot be physically deleted: `fk_users_role_id` uses `ON DELETE RESTRICT`. Role deactivation is rejected while any user references the role, including suspended users. Explicitly reassign users to approved active roles first; then deactivate under P2.4's shared locks and audit. Reactivation never changes assignments/account status/entitlement. Exact administrative capability and role-code reconciliation remain `TBD`.

### audit_logs

This table stores administrative actions that do not belong to a single core business timeline. Payment callbacks belong to `payment_transactions`; custom-design decisions belong to `custom_design_reviews`.

| Column          | Type         | Constraints / description                                                                      |
| --------------- | ------------ | ---------------------------------------------------------------------------------------------- |
| `audit_log_id`  | BIGINT       | Primary key.                                                                                   |
| `actor_user_id` | BIGINT       | Nullable foreign key to `users`; null is allowed for system actions.                           |
| `entity_name`   | VARCHAR(64)  | Required logical entity name, for example `users`, `promotions` or `workshop_exceptions`; snapshot profile/entity/action binding follows P2.9. |
| `entity_id`     | BIGINT       | Nullable identifier of the affected entity.                                                    |
| `action`        | VARCHAR(64)  | Required stable action code, for example `ASSIGN_ROLE`, `UPDATE_CAMPAIGN` or `CREATE_HOLIDAY`. |
| `outcome`       | VARCHAR(20)  | `NOT NULL`, no default; enforced `chk_audit_logs_outcome` from the P2.2 registry. |
| `reason`        | TEXT         | Nullable explanation; required for rejected or failed administrative actions.                  |
| `before_data`   | JSON         | SQL-null when no admissible before-image; otherwise required `audit_change/1` envelope, closed profile, 16 KiB cap and paired-image rules under P2.9. |
| `after_data`    | JSON         | SQL-null when no committed after-image; otherwise required `audit_change/1` envelope, closed profile, 16 KiB cap and outcome/pair rules under P2.9. |
| `request_id`    | VARCHAR(128) | Nullable correlation identifier for tracing the initiating request.                            |
| `created_at`    | DATETIME     | Required append-only audit timestamp.                                                          |

Indexes and rules:

- Index `(entity_name, entity_id, created_at)` for entity history.
- Index `(actor_user_id, created_at)` for administrator history.
- Index `(action, created_at)` for security review and reporting.
- Index `(created_at, audit_log_id)` for bounded P2.7 cutoff scans. This is not an age-only delete rule.
- Audit rows are append-only and must not contain passwords, tokens, payment credentials or unnecessary Guest PII.
- Five-year retention from the original `created_at`, field allowlists, scoped reads/exports, holds and the limited privacy/expiry exceptions follow P2.7. Normal administration cannot modify/delete evidence; a rejected operation is evidence too.
- Role changes must record the old and new role in `before_data`/`after_data`; promotion changes record the changed campaign fields; schedule-exception actions record the affected scope and date.
- A rejected self-review, self-approval or self-benefit attempt must create an `audit_logs` row with `outcome = REJECTED` and a structured `reason`.

## Core lifecycle transitions

The following transitions are the proposed allowed state changes. A service must reject any transition not listed here, and the status update plus its business timeline/audit record must be atomic.

### Custom design request

```text
need_review -> accepted
need_review -> rejected
```

- Only an authorized reviewer may perform the transition.
- `rejected` requires a reason.
- A request cannot return from `accepted` or `rejected` to `need_review`.
- A new review decision is not a silent overwrite; it requires a new `custom_design_reviews` row and must satisfy the request policy.

### Payment

```text
pending -> succeeded
pending -> failed
pending -> cancelled
pending -> expired
```

- A persisted payment attempt starts at `pending`; creation/initiation are operations, not stored statuses. This vocabulary follows `chk_payments_status`; payment policy still owns expiry, retry and late-success handling.
- Only a verified provider callback may move a payment to `succeeded`.
- Duplicate callbacks are idempotent and cannot repeat the business effect.
- A failed or cancelled attempt cannot be changed to `succeeded` without a new verified provider event and the approved retry rule.
- A successful payment cannot be changed back to a pending or failed state by a browser redirect.

### Retail order

```text
pending_payment -> paid
pending_payment -> expired
paid -> preparing
preparing -> ready_for_pickup
preparing -> prepared_for_carrier
ready_for_pickup -> picked_up
prepared_for_carrier -> handed_to_carrier
```

- `paid` requires a verified full payment matching the order target, amount and currency.
- `expired` is allowed only for an unpaid order whose approved hold has expired; it releases the unpaid hold exactly once.
- Pickup requires `picked_up_by_user_id` and `picked_up_at`.
- Carrier handoff requires a complete `delivery_infos` record and handoff evidence.
- No cancellation, refund or return transition is implied by this V1 lifecycle.

### Workshop registration

```text
pending -> confirmed
pending -> expired
confirmed -> checked_in
checked_in -> completed
```

- `confirmed` requires the approved email-confirmation and deposit/payment gates.
- `expired` releases any temporary capacity hold exactly once.
- `checked_in` requires `checked_in_by_user_id`, `checked_in_at` and a non-negative `actual_participant_count`.
- A parent/continuation link does not automatically transfer status, deposit or capacity from the parent registration.

## Separation-of-duties matrix

The actor and subject/beneficiary must be evaluated separately for every protected action. A foreign key only proves that a record exists; it does not prove that the actor is independent or authorized.

| Action                                 | Actor may perform                         | Mandatory prohibition                                                                                  |
| -------------------------------------- | ----------------------------------------- | ------------------------------------------------------------------------------------------------------ |
| Create custom design/request           | `MEMBER`, permitted `STAFF` workflow      | The creator cannot review, accept or reject the same design/request.                                   |
| Review custom design                   | authorized `STAFF`/`OWNER`                | Actor cannot equal `created_by_user_id`, `member_user_id` or another recorded beneficiary.             |
| Create or update promotion/voucher     | authorized `STAFF`/`OWNER`                | Creator cannot approve, redeem or receive a personal benefit from the same promotion/voucher.          |
| Redeem promotion/voucher               | eligible `MEMBER`                         | Actor cannot redeem a promotion they created or administratively approved for personal use.            |
| Place retail order or workshop booking | eligible `MEMBER` or permitted Guest flow | Staff operational authority must not silently grant Member pricing, loyalty or voucher entitlement.    |
| Verify payment                         | authorized payment/system process         | The actor cannot alter the payable target, amount or currency to benefit their own order/booking.      |
| Assign or change role                  | authorized administrator                  | Actor cannot grant themselves a privileged role or bypass the approved role-change workflow.           |
| Create/change schedule exception       | authorized `STAFF`/`OWNER`                | Actor cannot use the exception to bypass capacity, pricing or approval controls for their own booking. |

Enforcement rules:

- The service must compare the authenticated actor with every applicable creator, owner, `member_user_id`, reviewer, promotion creator and beneficiary field before authorization.
- A prohibited self-review or self-benefit action is rejected before changing business data and creates an `audit_logs` row with `outcome = REJECTED`.
- A Staff user has one role only and cannot obtain Member entitlements through a second role. Any approved exception for staff participation must be handled by an independent authorized reviewer and recorded in audit data.
- Review and approval screens must display the actor-independent target context needed for the check; UI hiding alone is not an enforcement mechanism.

## Loyalty points

Design status: Future/conditional draft requested for documentation. [Report 3 - Use Cases](../documents/docs/report-3-software-requirement-specification/sections/03-i-overall-requirements/04-user-requirements/02-use-cases.md) lists balance, earning/redemption history and order redemption, while [Report 1 - Limitations](../documents/docs/report-1-project-introduction/sections/07-v-project-scope-limitations/02-limitations-exclusions.md) defers loyalty and states that Members have no loyalty points in V1. P2.6 below specifies the conditional redeem/expiry mechanism; scope approval, earning triggers and actual policy values remain unresolved. The approved simplification uses four loyalty tables and excludes refund-driven point restoration. This design does not enable loyalty in V1.

### loyalty_points

| Column | Type | Constraints / description |
| --- | --- | --- |
| `loyalty_point_id` | BIGINT | Primary key. One immutable ledger entry, not one mutable balance row. |
| `member_user_id` | BIGINT | Required foreign key to `users`; the subject must have Member identity under the approved loyalty policy. |
| `order_id` | BIGINT | Nullable foreign key to `orders`; set for an entry caused by an eligible order. Other loyalty targets remain `TBD`. |
| `loyalty_policy_id` | BIGINT | Required foreign key to `loyalty_policies` for every posting; legacy entries need approved provenance reconciliation before enforcing this extension. |
| `loyalty_redemption_id` | BIGINT | Nullable foreign key to `loyalty_redemptions`; required only for a `redeem` debit. |
| `source_credit_entry_id` | BIGINT | Nullable foreign key to `loyalty_points`; required only for `expiry`, referencing an earlier positive entry for the same Member. |
| `entry_type` | VARCHAR(20) | `NOT NULL`, no default; enforced `chk_loyalty_points_entry_type` from P2.2 controls `earn`, `redeem`, `adjustment`, `expiry`. |
| `points_delta` | BIGINT | Required non-zero signed integer: positive for `earn`, negative for `redeem`/`expiry`; an authorized adjustment may have either sign. |
| `expires_at` | DATETIME | Nullable immutable positive-credit deadline; null means approved non-expiry. Negative entries have null. |
| `redeem_redemption_key` | BIGINT generated | Nullable `STORED` value `CASE WHEN entry_type = 'redeem' THEN loyalty_redemption_id ELSE NULL END`; enforces one debit per redemption, not another FK. |
| `event_key` | VARCHAR(128) | Required unique business-event/idempotency key so retries cannot post the same entry twice. |
| `reason` | TEXT | Nullable for normal earning/redemption; required for a manual adjustment. |
| `recorded_by_user_id` | BIGINT | Nullable foreign key to `users`; set for an authorised manual adjustment, null for a system-posted event. |
| `created_at` | DATETIME | Required posting timestamp. No `updated_at`: posted entries are immutable. |

Indexes:

- Unique `(event_key)` for idempotent ledger posting.
- Unique `uk_loyalty_points_redeem (redeem_redemption_key)` prevents multiple redeem debits for one reservation generation.
- `(member_user_id, created_at, loyalty_point_id)` supports balance/history queries with deterministic ordering.
- `(order_id, entry_type)` supports order-related point history.
- `(member_user_id, expires_at, loyalty_point_id)` supports credit/expiry discovery; explicit `(loyalty_policy_id)`, `(loyalty_redemption_id)` and `(source_credit_entry_id)` support restricted FKs.

Rules:

- Posted balance is the sum of posted `points_delta`; spendable balance also excludes held and overdue points as specified in P2.6. Do not keep an independently editable balance in `users`.
- Named enforced `chk_loyalty_points_shape` requires nonzero delta and mutually exclusive shapes: `earn` is positive with redemption/source null; `redeem` is negative with redemption non-null and source/expiry null; `expiry` is negative with source non-null and redemption/expiry null; `adjustment` has redemption/source null, a non-null/non-blank reason and non-null actor, with expiry null when negative. Explicit `IS NULL`/`IS NOT NULL` tests prevent nullable links bypassing the check. Service validation rejects self-reference/cycles; do not reference an `AUTO_INCREMENT` primary key in a MySQL `CHECK` expression.
- All required ordinary fields above are `NOT NULL`; generated keys are read-only. Positive credit expiry must be later than its grant time. Credit/debit signs, same-Member identity, historical policy/expiry, order/beneficiary equality and complete debit allocations are service invariants under P2.6, beyond a row check.
- Redemption checks eligibility and sufficient unexpired/unheld points atomically, using the versioned whole-block conversion and snapshots in P2.6. Actual rate/minimum/caps/lifetime and earning triggers require approved policy values before activation.
- Posted entries are never edited/deleted to correct a balance. An authorized manual correction uses a new `adjustment` entry with reason, actor and unique event key; this is not a substitute for a refund or an automatic restoration of redeemed points.
- P2.6 covers holds, shared source allocations, actual consumption and expiry. Tiers and earning rules remain separate approval inputs. Refunds and refund-driven point restoration are outside this design.
- Order loyalty snapshots and immutable payment-generation FKs provide the proposed payable-amount linkage. Promotion/loyalty stacking still requires a separately approved joint policy.

## Loyalty redemption and expiry — P2.6

Design status: Four-table simplification selected for documentation. Covers Member retail checkout point holds, point-to-money policy snapshots, paid redemption and per-credit expiry. The four tables are `loyalty_policies`, `loyalty_points`, `loyalty_redemptions` and `loyalty_allocations`. Source allocation and consumption share one table; payment attempts carry their exact-generation FK directly in `payments`. There is no refund function, refund-compensation table or refund-restoration ledger type. No module/migration is implemented. The Report 1 deferral versus Report 2/3 loyalty scope conflict remains for scope approval; actual rates/lifetimes and P2.10 policy inputs remain unresolved.

Physical convention: every ordinary field described as Required is `NOT NULL`, with no implicit default; nullable/default/generated exceptions are explicit. Controlled strings use the P2.2 collation/check contract. Row checks validate local values; cross-row sums, currency/identity equality, source chronology and authorization require the owning transaction protocol.

### Approach and balance model

| Approach | Benefit | Limitation | Selection |
| --- | --- | --- | --- |
| Mutable account balance | Simple balance reads. | Loses source/expiry provenance and requires independently maintained totals for holds. | Not selected. |
| Separate reservation allocations and debit consumptions | Separates immutable evidence for each stage. | Duplicates each checkout's source slices when the hold is consumed. | Consolidated into the shared allocation model below. |
| Immutable ledger credits as lots, with shared source allocations and an attempt FK in `payments` | Preserves hold/expiry provenance using four loyalty tables. | Requires a guarded one-time allocation settlement and per-Member serialization. | Selected; no separate mutable lot/balance table. |

- A positive `loyalty_points` entry is a credit lot. Its amount, policy and optional `expires_at` are immutable. Every negative entry must be fully backed by `loyalty_allocations` rows with `debit_entry_id` set.
- For credit lot L: `consumed(L) = SUM(loyalty_allocations.points_quantity WHERE credit_entry_id = L AND debit_entry_id IS NOT NULL)`; `remaining(L) = L.points_delta - consumed(L)`. Held points are the sum of rows for L whose redemption is `reserved` and whose `debit_entry_id IS NULL`. For an unexpired lot, `available(L) = remaining(L) - held(L)`; expired lots have zero spendable points even if the expiry worker has not yet posted their debit. Neither remaining nor available may be negative.
- Released allocations are retained with no debit and count toward neither held nor consumed points. Redeemed allocations count as consumed only, never as a second hold. A reserve/release changes availability without posting a debit/credit; redemption converts the existing allocation rows into consumption exactly once.
- Posted balance remains the sum of ledger deltas. Spendable balance is the sum of available unexpired lots; held and overdue-unposted-expiry amounts explain the difference. Do not display posted balance as spendable while ignoring holds or worker lag.
- Within eligible credits, allocate earliest-expiring credits first, then non-expiring credits, with `(created_at, loyalty_point_id)` as deterministic tie-breakers. Each selected lot's grant-policy currency must match the redemption/order currency; a new redemption policy may change conversion terms but cannot silently convert credit currency. Do not allocate beyond available points or let a checkout hold extend credit lifetime. Quantity/money arithmetic uses exact integer/decimal operations with overflow checks, never floating point.

### loyalty_policies

Versioned terms provide a concrete conversion/expiry mechanism without inventing stakeholder rates. Policies are currency-specific; points are Member-scoped and cannot be freely exchanged between currencies without approved eligibility.

| Column | Type | Constraints / description |
| --- | --- | --- |
| `loyalty_policy_id` | BIGINT | Primary key. |
| `policy_version` | VARCHAR(64) | Required unique stable version identifier. |
| `currency` | CHAR(3) | Required approved quote currency. |
| `points_per_unit` | BIGINT | Required positive integer block size. |
| `amount_per_unit` | DECIMAL(15,2) | Required positive money reduction per whole block; must respect the approved currency precision. |
| `minimum_redeem_points` | BIGINT | Required positive minimum, a multiple of `points_per_unit`. |
| `maximum_discount_percentage` | DECIMAL(5,2) | Required value greater than 0 and at most 100; applies to the policy's eligible basis. |
| `maximum_discount_amount` | DECIMAL(15,2) | Nullable positive absolute money cap; null means explicitly approved no additional absolute cap. |
| `discount_basis` | VARCHAR(30) | `NOT NULL`, no default; enforced `chk_loyalty_policies_discount_basis` from P2.2 controls `retail_subtotal` or `after_promotions`. Selection requires an approved policy; this does not authorize stacking. |
| `earn_expiry_days` | INT | Nullable positive whole-day lifetime for new eligible credits; null means explicitly approved non-expiring credits. The actual value and calendar/time interpretation require approval. |
| `effective_from` | DATETIME | Required start of the approved policy's validity. |
| `effective_until` | DATETIME | Nullable exclusive validity end, later than the start when set. |
| `is_active` | BOOLEAN | `NOT NULL DEFAULT FALSE`; only an approved published policy can be activated for new quotes/grants. |
| `active_currency` | CHAR(3) generated | Nullable `STORED` value `CASE WHEN is_active THEN currency ELSE NULL END`; not editable. |
| `published_at` | DATETIME | Nullable until first approval/publication; retained after retirement. Terms are immutable once published or referenced. |
| `created_by_user_id` | BIGINT | Required foreign key to `users`; policy creator, not a customer entitlement. |
| `created_at` | DATETIME | Required creation timestamp. |
| `updated_at` | DATETIME | Required timestamp for guarded activation/retirement metadata changes. |

- Unique `(policy_version)` and `uk_loyalty_policies_active_currency (active_currency)` allow at most one active policy per currency while retaining inactive versions. Activation/retirement is audited and coordinated; validity dates still gate use. No active applicable policy means redemption is unavailable, not a guessed default rate.
- Enforced `chk_loyalty_policies_terms` requires positive units/amount/minimum, `MOD(minimum_redeem_points, points_per_unit) = 0`, `0 < maximum_discount_percentage <= 100`, positive optional absolute cap/lifetime, valid optional end date and non-null `published_at` when active. Expressions must explicitly handle nullable fields and guard modulo with a positive-unit `CASE`. Index `(created_by_user_id)` supports the restricted FK.
- Conversion is exact: requested points must meet the minimum and be a multiple of the block size; `discount_amount = (points_quantity / points_per_unit) * amount_per_unit`. Reject unsupported fractions, excess caps, insufficient points and totals outside the approved positive-payable flow; do not silently burn rounded-up points or clamp the Member's request. Caps and eligibility are validated before snapshotting.
- The approved basis is exactly `orders.subtotal_amount` for `retail_subtotal`, or `subtotal_amount - promotion_discount_amount` for `after_promotions`. `discount_amount <= eligible_amount_snapshot * maximum_discount_percentage_snapshot / 100` and any absolute cap; the combined order discounts must also leave a valid payable total. A basis code supplies arithmetic, not permission to combine promotions and loyalty. Without approved stacking, a checkout cannot contain both reductions.
- New terms require a new published version; retirement blocks new quotes but does not reprice a valid existing hold, extend existing lot expiry or rewrite historical ledger entries. Earning triggers, actual configured rates/lifetimes, stacking and policy-administration permissions remain approval inputs.

### loyalty_redemptions

One row is one immutable checkout quote/reservation generation with guarded lifecycle metadata. It follows `reserved -> redeemed` or `reserved -> released`; both terminal states are irreversible. Redeemed points cannot be released or restored through a refund workflow.

| Column | Type | Constraints / description |
| --- | --- | --- |
| `loyalty_redemption_id` | BIGINT | Primary key. |
| `reservation_key` | VARCHAR(128) | Required unique trusted idempotency key; conflicting order/user/points/quote reuse is rejected. |
| `order_id` | BIGINT | Required foreign key to `orders`. |
| `member_user_id` | BIGINT | Required foreign key to `users`; must equal the order beneficiary. |
| `loyalty_policy_id` | BIGINT | Required foreign key to `loyalty_policies`. |
| `status` | VARCHAR(20) | `NOT NULL DEFAULT 'reserved'`; enforced `chk_loyalty_redemptions_status` from P2.2 controls `reserved`, `redeemed`, `released`. |
| `active_order_id` | BIGINT generated | Nullable `STORED` value `CASE WHEN status IN ('reserved', 'redeemed') THEN order_id ELSE NULL END`; not another FK. |
| `points_quantity` | BIGINT | Required positive whole number of held/to-be-spent points. |
| `points_per_unit_snapshot` | BIGINT | Required positive conversion block size. |
| `amount_per_unit_snapshot` | DECIMAL(15,2) | Required positive conversion money unit. |
| `minimum_redeem_points_snapshot` | BIGINT | Required positive minimum used to validate this quote. |
| `maximum_discount_percentage_snapshot` | DECIMAL(5,2) | Required positive percentage cap, at most 100. |
| `maximum_discount_amount_snapshot` | DECIMAL(15,2) | Nullable positive absolute cap from the approved policy. |
| `discount_basis_snapshot` | VARCHAR(30) | `NOT NULL`, no default; enforced `chk_loyalty_redemptions_discount_basis_snapshot` from P2.2. |
| `eligible_amount_snapshot` | DECIMAL(15,2) | Required positive approved discount basis; cannot exceed the permitted order basis. |
| `discount_amount` | DECIMAL(15,2) | Required positive frozen money reduction, at most the eligible amount and policy caps; actual accepted discount only when redeemed. |
| `currency` | CHAR(3) | Required quote currency matching policy/order/payment. |
| `reserved_at` | DATETIME | Required reservation timestamp. |
| `expires_at` | DATETIME | Required deadline later than reservation; at most the order hold deadline and the earliest finite expiry of allocated credits. No new hold duration is invented. |
| `redeemed_payment_id` | BIGINT | Nullable foreign key to `payments`; required only in redeemed state, for the exact accepted attempt whose immutable `payments.loyalty_redemption_id` points to this generation. |
| `redeemed_at` | DATETIME | Nullable until redeemed; then required and not earlier than reservation. |
| `released_at` | DATETIME | Nullable until released; then required and not earlier than reservation. |
| `release_reason` | VARCHAR(100) | Nullable safe diagnostic reason; required/non-blank only when released. |
| `updated_at` | DATETIME | Required lifecycle metadata-update timestamp. |

- Unique `(reservation_key)` and `uk_loyalty_redemptions_active_order (active_order_id)` enforce one reserved/redeemed generation per order while keeping released history. Indexes `(member_user_id, status, expires_at)`, `(order_id, reserved_at)`, `(status, expires_at)`, `(loyalty_policy_id)` and `(redeemed_payment_id)` support holds/history/FKs.
- Named enforced `chk_loyalty_redemptions_quote` checks positive quantities/unit/amount/minimum/basis, whole-block divisibility of quantity/minimum, quantity at least minimum, the exact conversion formula, percentage cap at most 100, positive optional cap and discount within the eligible basis and both applicable caps. Guard division/modulo with `CASE` on a positive unit so invalid zero units cannot produce `UNKNOWN` instead of rejection. Monetary precision/overflow and cross-table policy/quote agreement remain service checks.
- Named enforced `chk_loyalty_redemptions_lifecycle` has the P2.5 terminal metadata shape: reserved has null payment and terminal timestamps/reason; redeemed has a non-null payment and redeemed time with null release fields; released has a non-null release time/non-blank reason and null redemption evidence. Require `expires_at > reserved_at`, `reserved_at <= redeemed_at < expires_at` when redeemed, and explicit non-null/date-order tests. Late payment follows the exception path. Immutable quote, identity, policy, key and deadlines are never overwritten on retry.

### loyalty_allocations

One row identifies a fixed quantity from one positive ledger credit. A checkout allocation starts as a hold and may be settled once into actual consumption; expiry and authorized negative adjustments insert consumption rows directly. No independent allocation status is added: the parent redemption state and debit link determine its meaning.

| Column | Type | Constraints / description |
| --- | --- | --- |
| `loyalty_allocation_id` | BIGINT | Primary key; stable identity across the hold-to-consumption transition. |
| `credit_entry_id` | BIGINT | Required foreign key to `loyalty_points`; must identify a positive credit for the same Member as any linked redemption/debit. |
| `loyalty_redemption_id` | BIGINT | Nullable foreign key to `loyalty_redemptions`; present for every checkout hold/consumption and null for expiry or negative-adjustment consumption. |
| `debit_entry_id` | BIGINT | Nullable foreign key to `loyalty_points`; null for a held/released allocation, set exactly once for a negative posted entry. |
| `points_quantity` | BIGINT | Required positive source quantity, immutable after insertion. |
| `created_at` | DATETIME | Required allocation creation timestamp, immutable. |
| `consumed_at` | DATETIME | Nullable until a debit is attached; required with a debit, not earlier than creation, immutable once set. |

Indexes and row checks:

- Unique `uk_loyalty_allocations_redemption_credit (loyalty_redemption_id, credit_entry_id)` permits at most one source slice per checkout generation. Unique `uk_loyalty_allocations_debit_credit (debit_entry_id, credit_entry_id)` permits at most one slice per posted debit/source pair. Separate keys are intentional: MySQL permits multiple nulls in nullable unique keys, so one combined redemption/debit/credit key would not protect both shapes.
- `(credit_entry_id, debit_entry_id)` supports source-consumption reads; `(credit_entry_id, loyalty_redemption_id)` supports source-hold discovery. The two unique keys support the redemption and debit FKs.
- Named enforced `chk_loyalty_allocations_shape` requires `points_quantity > 0 AND (loyalty_redemption_id IS NOT NULL OR debit_entry_id IS NOT NULL) AND ((debit_entry_id IS NULL AND consumed_at IS NULL) OR (debit_entry_id IS NOT NULL AND consumed_at IS NOT NULL AND consumed_at >= created_at)) AND (debit_entry_id IS NULL OR debit_entry_id <> credit_entry_id)`. All required columns are `NOT NULL`; both-null references, zero/negative quantities, orphan consumption timestamps and self-consumption are invalid.

Cross-row lifecycle and reconciliation:

- For a `reserved` redemption, all allocations have null debit/time and their sum equals its `points_quantity`. For a `released` redemption, the same fixed rows retain null debit/time and no longer hold points. A released generation can never receive a debit.
- For a `redeemed` redemption, every allocation references its one `redeem` ledger debit; that debit's `loyalty_redemption_id` matches the allocation's parent, and source quantities still equal the original hold. The source set is finalized when the reservation transaction commits; no later checkout slice may be inserted. Settlement only attaches `debit_entry_id` and `consumed_at` (the trusted local debit posting time), together, once; it never inserts a duplicate copy or overwrites identity, source, quantity or creation time. The posting, all attachments and the parent state change commit atomically.
- With no redemption, a row must have a debit for `expiry` or an authorized negative `adjustment`, never `redeem`. Such rows are immutable from insertion. An expiry debit uses exactly its `source_credit_entry_id`, historical policy and remaining unspent quantity. Negative adjustments cannot consume points held by another checkout.
- Each debit's allocated sum equals `-points_delta`; total consumed per credit cannot exceed its grant, and consumed plus active holds cannot exceed that grant. All linked credits/debits/redemptions belong to the same Member and compatible policy currency. A redeem debit's order matches its redemption; its source credits may come from earlier, different orders. Expiry retains its source credit's policy/order provenance. These relationships and date/source chronology are service invariants under the Member lock; FK/row checks alone cannot enforce them.
- Rows are never deleted on release or after payment. Settled rows are immutable; retries recognize the committed debit/parent result instead of attaching another debit or changing consumption timestamps.

### Payment-attempt linkage in `payments`

- `payments.loyalty_redemption_id` is a nullable restricted FK to the exact checkout generation; `loyalty_linked_at` records its pre-initiation binding time. Define both fields, their index and row check in the `payments` table below. This is a many-attempts-to-one-generation relationship, not another loyalty table.
- Bind both fields when creating a retail attempt, in the same local transaction and before provider initiation. For an order with a non-zero loyalty discount, the link is required and its Member/order/currency/points/money snapshots must match. For a retail quote with no loyalty discount, or for a workshop attempt, both fields are null. The committed link is immutable, including an originally absent link; do not attach loyalty later to an existing attempt.
- Several permitted attempts can share one still-reserved generation. Retrying on a replacement generation creates a new payment attempt; never relink an old attempt, even after release or evidence retirement. A late callback must not infer a newer generation from `order_id`.
- `loyalty_redemptions.redeemed_payment_id` identifies the one accepted linked attempt and remains null until redemption. It serves a different purpose from the attempt's quote-generation FK; both agree when payment is accepted.

### Ledger extension, atomicity and acceptance

- `loyalty_points` has policy, redemption/source-credit references and immutable credit expiry. Supported types are `earn`, `redeem`, `adjustment`, `expiry`; earn is positive, redeem/expiry are negative, and authorized adjustment may have either sign. Manual corrections retain required reason/actor rules and do not implement refunds.
- Every new posting references an approved policy. Expiry retains the credit's original policy/order provenance after retirement. New credit expiry is calculated once at grant; negative rows have no own spendable expiry. `source_credit_entry_id` is required only for expiry and references an earlier positive source for the same Member; a redeem debit uses its reservation's conversion-policy version.
- Atomically reserve the generation, all shared source allocations, matching order point/money snapshots and audit under the Member lock; validate the complete checkout hold before commit. Release changes generation state/metadata and audit without deleting allocations, attaching a debit or posting a ledger delta. Payment creation commits its immutable generation FK/time before provider initiation; no provider call runs under loyalty database locks. Global checkout/stock boundaries remain P2.8.
- At accepted payment, atomically post one redeem debit, attach that debit/time to every existing source allocation, mark the generation redeemed and apply the accepted payment/order/resource effects with audit. Failure rolls back all effects. Unique event keys and the one-debit-per-redemption key prevent callback/retry double spending. No ledger debit is posted for an unpaid hold.
- Release/failure/ambiguous-result/retry guards follow P2.5: do not release while another linked attempt can legitimately succeed; never release redeemed points. A released generation stays terminal; a fresh quote needs a fresh key/attempt, and stale callbacks cannot consume its replacement. Reacquisition rechecks eligible unexpired credits and must match the frozen order quote; otherwise create a new checkout. Payment/expiry after the exact generation's deadline follows the existing exception path.
- The Member's existing `users` row is the `PESSIMISTIC_WRITE` serialization point for all ledger, allocation, hold, settlement, adjustment and expiry writers. Use current reads after locking, not cached totals. P2.8 supplies the full ordering; the business subsequence stays order, involved promotions, Member, policy and dependent reservation/attempt/lot records. All writers follow that protocol.
- An expiry worker discovers due lots/affected holds without treating the scan as authority. If order/reservation release is needed, plan/acquire affected orders in ascending ID order before the Member lock, then revalidate; missed orders require rollback/replan. Release due holds first, then post one uniquely keyed expiry debit and direct shared allocation for each lot's remaining amount; skip zero remainder. Never acquire earlier-order locks while holding the Member gate. Failed expiry transactions are retryable; overdue points remain unavailable while the worker catches up.
- Every new FK has named `ON DELETE RESTRICT` and `ON UPDATE RESTRICT` under P2.3. Ledger, fixed allocations, reservation history and payment FK/time evidence are retained without ORM delete cascades. Policy retirement uses `is_active = FALSE`; retirement/PII cleanup does not erase point history. Retention remains P2.7. Technical mechanisms: [MySQL locking reads](https://dev.mysql.com/doc/refman/8.4/en/innodb-locking-reads.html), [generated columns](https://dev.mysql.com/doc/refman/8.4/en/create-table-generated-columns.html), [nullable unique indexes](https://dev.mysql.com/doc/refman/8.4/en/create-table.html) and [CHECK restrictions](https://dev.mysql.com/doc/refman/8.4/en/create-table-check-constraints.html); implementation must verify the selected release/ORM behavior.
- A later migration must reconcile legacy credits/debits into provable expiry and complete allocation provenance before enabling redemption. Do not invent grant dates/rates/expiry, reset balances or allocate historical debits arbitrarily. Where prior split hold/consumption/link records exist, reconcile source pairs and exact payment-generation bindings without duplication or lost timestamps; incompatible historical codes require an approved compatibility plan, not deletion or silent remapping. Applied migrations remain immutable.
- Verify competing checkouts; conversion/minimum/caps; currency/beneficiary mismatch; FEFO allocation; insufficient/held/expired points; policy changes after reservation; duplicate/old callbacks and success-versus-release/expiry races. Reconcile every debit/source sum and ensure redeemed rows count only as consumed, while released rows count as neither held nor consumed.
- Verify allocation one-time settlement, direct expiry/negative-adjustment shapes, immutable attempt FK/time, full rollback on ledger/allocation/audit failure and worker retries. A failed payment releases an eligible unpaid hold without a compensating credit. No refund or redeemed-point-restoration behavior is included.
- Documentation acceptance: exactly four loyalty table definitions; no refund-compensation FK/columns or ledger type; direct payment-generation linkage; explicit hold/consume/release formulas, source expiry and conversion snapshots. Actual rates, lifetimes, earning triggers, stacking and feature-scope approval remain implementation gates; this edit neither enables the module nor claims database-test results.

## Audit and payment evidence retention — P2.7

Design status: Proposed storage/access/expiry contract for review, not an implemented retention job. The authoritative SRS already selects **five years** for VNPay references and business audit evidence, **30 days after GHTK handoff** for delivery-recipient/address data, and no card-data storage. This section applies those choices to `audit_logs`, domain-owned business audit and `payments`/`payment_transactions`; it does not assert a statutory retention period or introduce accounting, reconciliation or refund workflows. Report 2 A-04 still requires Finance/Legal confirmation of the five-year choice before go-live.

Sources: [approved SRS NFR](../documents/docs/report-3-software-requirement-specification/sections/06-iv-non-functional-requirements/00-overview.md), [Report 2 assumptions A-03/A-04 and constraints C-07](../documents/docs/report-2-project-management-plan/sections/02-i-project-overview/02-assumptions-constraints.md), [stakeholder decision E-01](../documents/docs/report-2-project-management-plan/sections/02-i-project-overview/07-evidences.md), [SRS actors](../documents/docs/report-3-software-requirement-specification/sections/03-i-overall-requirements/04-user-requirements/01-actors.md), and [existing redacted auth-audit model](features/001-member-authentication/data-model.md#audit-and-sensitive-data). The SRS permission matrix/job inventory contains placeholders, so the detailed permissions and jobs below are proposals rather than existing approved capabilities.

### Alternatives and selected archive boundary

| Approach | Benefit | Trade-off | Proposal |
| --- | --- | --- | --- |
| Retain minimal evidence in current tables; separate operational and historical reads | Keeps existing FK graph, stable IDs and event/idempotency lookup. | Needs indexed age-based reads, restricted access and expiry processing. | Select for this design. Historical archive is a read/access classification, not a new business status. |
| Move evidence into separate archive tables/object storage | Can reduce active-table volume. | Requires verified transfer, integrity/manifests, archive lookup and preservation of live references and duplicate-event guards. | Defer until an approved storage/volume plan proves the entire contract; export alone is not archival authority. |
| Delete automatically when a row reaches a fixed age | Simple cleanup. | Can destroy referenced evidence or event keys while callbacks/obligations remain open. | Reject age-only deletion; expiry requires the guards below. |

- No `archived` status, generic `deleted_at`, partition-drop policy or extra archive table is added. During retention, source records stay in current tables and remain available to authorized history/evidence queries. Archiving never changes amounts, statuses, beneficiaries or successful-payment effects.
- A historical read is scoped by domain, entity and date range, with pagination and field masking. Operational screens request only the data needed for the current action; an old unresolved exception remains operationally visible even if most related history is read through the historical path. Age alone cannot close an exception.
- A future physical transfer must preserve the FK graph, IDs, supported reads, event lookup and original retention deadlines. Verify row counts/keys and integrity before any source removal; copying a referenced parent elsewhere does not satisfy a live FK. No FK disabling, cascade, actor nulling or receipt re-creation is an archive shortcut.

### Retention clocks and expiry matrix

Use trusted application timestamps in UTC. For this proposal, five years means five calendar years from the anchor; a 29 February anniversary in a non-leap year uses 28 February. Ordinary reads, retries, exports, archive copies and `updated_at` do not restart a clock. Preserve the original anchor across migration/restore. Missing/unprovable legacy timestamps block destructive cleanup until reconciled; do not substitute the migration date.

| Data | Anchor and retention | On reaching the deadline |
| --- | --- | --- |
| `audit_logs` and domain-owned business audit evidence | Each event's original `created_at`/`occurred_at` + five years, including recorded rejected/failed business actions. | Eligible for guarded expiry deletion when no scoped hold or evidence dependency remains. Keep identifiers/facts and approved safe snapshots until then; never retain forbidden payloads for five years. |
| Correlated `payment_transactions` evidence | Each distinct persisted event's original `received_at` + five years, including safe failure/unknown/late-event evidence. Recognition of an identical duplicate does not reset its first receipt timestamp. | Delete only after the linked attempt's evidence-retirement gate is committed and this row's deadline/hold/dependency checks pass. Keeping an event longer for an active dependency requires recorded justification/review. |
| VNPay references and associated `payments` evidence | Five years from the latest trusted local `created_at`, `verified_at`, or original `received_at` of retained distinct callback/recovery evidence for that attempt. A successful outcome received later must not lose its evidence early. | Apply the permanent attempt-retirement gate before callback-key purge/reference redaction. Keep still-required minimal FK/business facts; raw provider references may be removed only when no continuing evidence purpose requires them. |
| Provider callback body, headers, signatures, credentials, Return URL payload or card data | No persistent retention in these tables, logs, audit JSON, exports or backups. Process required verification input in memory. | Discard after verification/handling. A digest is not a recoverable callback body or a substitute for signature verification. Do not add a five-year raw-payload store. |
| Delivery recipient/name/phone/address copied into evidence | Prohibited copies; the operational source is `delivery_infos`. Its approved cutoff is `handed_off_at` + 30 days. | Delete or irreversibly obscure source contact/address at the cutoff and remove accidental evidence/export copies; retain only handoff/amount/actor/ID facts needed for business history. This is not permission to keep a contact-data copy in audit for five years. |

- The five-year policy is an evidence-retention period, not permission to retain all customer PII. Do not delete an entire referenced order/user/payment simply because an audit/callback record expires. Other identity, consent, booking, loyalty or financial-fact lifetimes are not selected by this section; retain only a documented continuing purpose and dependency, with a review deadline, rather than asserting permanent retention.
- Unresolved payment exceptions, a documented dispute or an independently approved evidence hold can block expiry for its exact scope. The hold register records domain/record IDs or bounded range, reason/case reference, authorizer, start, expiry/review deadline and release decision; it contains no original sensitive payload. Finance/Legal are confirmation/review stakeholders, not new application roles. Hold approval/release must follow authorized independent review, not the investigated actor's unilateral choice.
- A hold extends only necessary admissible evidence, never card data/secrets or a blanket copy of delivery PII. Revalidate its continuing purpose at the recorded review date and process expiry after release. No whole-database indefinite hold or silent extension by changing `updated_at` is allowed.
- The retention-control register and its approved storage/backup configuration are implementation-plan dependencies. Its unavailable/ambiguous state stops destructive cleanup and raises an operational alert; it must not be interpreted as proof that no hold exists. Finance/Legal confirmation and current privacy-notice alignment are go-live gates, not fabricated legal conclusions.

### Data minimization, redaction and reconciliation evidence

| Area | Retain during its approved evidence period | Do not store / privacy handling |
| --- | --- | --- |
| Audit envelope | Stable audit ID, actor FK when originally present, entity key, action/outcome, timestamp, correlation ID, safe reason code and approved changed business values. | No email, phone, address, Firebase UID/token, credentials, image URLs/content or arbitrary form/request/exception dumps in `reason`, `before_data` or `after_data`. Correlation IDs must be opaque identifiers, not encoded PII. |
| Audit change snapshots | Allowlisted role IDs, configuration keys, schedule scope/date, campaign terms and relevant price/quantity/amount changes, attributed to the original action. | Filter before persistence; do not snapshot entire JPA entities, profile/contact objects or payment credentials. P2.9 owns JSON version/schema details; P2.7 defines the permitted-data boundary. |
| Payment/recovery evidence | Payment/internal target IDs, provider/transaction/event references when actually supplied, normalized verified result, expected/reported amount and currency, receipt/verification/processing times, accepted or rejected business effect in its owning audit, and payload fingerprint. | Safe adapter summaries only. Do not infer payment from a hash/status alone, retain raw signed messages, or copy billing/delivery contacts. Provider authentication/result mapping remains the payment adapter contract. |
| Preserved business graph after evidence expiry | Still-required internal keys/FKs, original amounts/currency, states/timestamps and promotion/loyalty snapshots, plus the permanent retired-attempt guard. | Remove no-longer-required direct identifiers/provider references where allowed and document remaining linkability. Linked user IDs, transaction references and hashes are pseudonymous/linkable, not proof of irreversible anonymization. |

- A business timeline stays in its owning domain; `audit_logs` retains its documented administrative scope and is not converted into a central store for every workflow. Apply the same evidence/minimization contract to existing `member_auth_audit_events` through its physical feature mapping. No table rename, new cross-domain audit dependency or aggregate Admin audit screen is introduced here.
- Preserve the original actor/subject/entity FKs under P2.3. Account suspension or PII cleanup does not replace actors with a generic user or set actor FKs to null. Null keeps its original system/Guest meaning. Authorized identity/profile erasure is a separate policy; avoiding profile/contact copies in evidence makes it possible without rewriting business facts.
- Normal evidence is append-only. If prohibited sensitive content was accidentally persisted, an independently authorized privacy operation may irreversibly remove only the sensitive fields/copies, preserving safe event facts and FK identity. Record scope, safe reason, authorizer, job/correlation ID, field paths/counts and completion; never copy the removed values into the cleanup audit. This narrowly defined privacy exception is not a right to edit an inconvenient outcome or financial snapshot.
- Exact VNPay references needed for an approved investigation/export remain restricted and retained until their evidence period/hold ends. Normal views mask them; authorized evidence access can expose the exact required fields. Exporting this evidence supports externally handled payment exceptions; it does not implement V1 reconciliation/accounting/refunds.

### Access contract

Every read/export checks the active account/role under P2.4, an explicit permission, domain/entity/branch scope and purpose. Retention does not grant access. The existing `OWNER`/`ADMIN_TECHNICAL` versus SRS Manager/Admin naming disagreement remains visible; do not silently map roles or grant access based on a label. Until the physical permission mapping is approved, new evidence endpoints deny access by default.

| Principal / purpose | Allowed proposed access | Limits |
| --- | --- | --- |
| Member / Guest | Existing own-order/booking payment summary or receipt only through its authorized customer flow. | No `audit_logs`, callback rows, other customers' records, raw references or bulk evidence export. Guest access requires the existing scoped flow, not guessed IDs. |
| Staff | Minimal verified payment status/amount and relevant action history for an assigned operational target, under explicit scoped permission. | No global audit/callback search, credential fields or bulk financial evidence export. Payment evidence does not permit Staff to override confirmation. |
| Authorized business reviewer (Owner/Manager capability after role reconciliation) | Domain-scoped business audit/payment history; exact reference evidence/export only with a separate purpose-scoped permission and authorized review. | Revenue-dashboard access alone does not grant callback/evidence access. No outcome mutation, unilateral expiry override or automatic access to all account/security audit. |
| Authorized account/security administrator | Relevant account/role administrative audit needed for approved administration/investigation. | Not a general grant to business payment evidence. Technical troubleshooting uses masked minimal diagnostics; exact evidence requires approved time-limited access. |
| Retention/backup service and approved infrastructure operator | Narrow service-account operations required for the approved job or restore, with least-privilege database/storage access. | No business confirmation/price/role override. Administrative execution does not bypass review, expiry, holds, immutable facts or restore checks. |

- Audit evidence searches, sensitive reads, exports, hold changes and cleanup jobs with actor/service identity, scope/purpose, time and result; do not log query payloads or exported contents. Self-recording the access audit must not recursively create an endless audit chain. An export is field-minimized, access-restricted and has the source record's remaining deadline; it never gets a fresh five years from export time.
- Infrastructure/DB access is a distinct least-privilege control, not an implicit application role permission. Encrypt retained evidence/backups in transit and at rest; manage keys separately from evidence. Choice of deployment/storage/key service belongs to the approved feature/operations plan, not a new provider decision in DB.md.

### Retirement, expiry jobs and restore safety

- Extend `payments` with nullable `evidence_retired_at`, default `NULL`, plus index `(evidence_retired_at, created_at)` for lifecycle/retention selection. Null means not yet retired. A set value is an immutable, trusted local timestamp for **permanent closure to further application payment business effects**; it is not a new payment status or proof of payment/refund. Enforced `chk_payments_evidence_retired_at`: `evidence_retired_at IS NULL OR evidence_retired_at >= created_at`; `created_at` is required `NOT NULL`.
- Retirement requires a completed terminal payment/target outcome or an independently documented resolved exception, no in-flight/recovery/reprocessing action, expiry of all required evidence periods and no active hold/dependency requiring full evidence. Acquire the existing attempt/target locks and revalidate these gates; a cleanup scan is not authority. A pending/unresolved attempt cannot be retired just because it is old. Commit retirement, protected expiry/redaction and safe cleanup audit atomically for the locked batch; audit failure rolls back the destructive business-table changes. Full cross-domain transaction ordering remains P2.8.
- Every callback/recovery path checks `evidence_retired_at` before any business mutation, including duplicate/late success. Once set, the attempt can never cause another application confirmation/reservation/redemption effect; record a safe rejection/exception if needed. This guard cannot prevent a provider from reporting an external collection and does not resolve that exception automatically. Its stable internal `payment_code` remains recognizable even if provider references/callback rows have expired. Retiring is irreversible and never resets the original payment status or order/booking/loyalty effect. This gate must be implemented and verified **before** purging duplicate-event lookup evidence.
- Before five years, callback rows/event keys and exact required references remain in the selected live-table archive. At expiry, the approved service may delete eligible leaf `audit_logs`/`payment_transactions` records after checking incoming physical references, holds and dependencies. Never delete an event key while its attempt can still accept effects. Existing retained promotion-attempt links and redeemed-payment FKs continue pointing to the minimal `payments` row; its immutable loyalty-generation FK/time also survive retirement; this section does not authorize parent/history deletion or cascade.
- Maintain a restricted cleanup/hold-decision manifest with policy/version, trusted cutoff, record ranges/IDs, hold exclusions, authorizer/service identity, counts, failures and completion/restore-suppression evidence. It stores no original PII/callback body. Decision/job evidence itself follows five years from the recorded decision/completion; do not keep original expired payloads inside the manifest. Necessary minimal restore-suppression/retirement markers cannot expire while a surviving restorable copy could resurrect their data/effects; track that specific backup dependency and its review deadline instead of keeping all expired evidence.
- Jobs use indexed cutoff scans (`audit_logs.created_at`, `payment_transactions.received_at`), bounded batches, current-state checks and retry-safe completion. Complete approved cleanup by its deadline; detect overdue/unprocessed records and alert. Due PII is unavailable to normal reads/exports even while a failed cleanup is being retried. A failed batch must not leave partly purged evidence with an uncommitted retirement/audit guard.
- Backups, replicas, search indexes, caches, materialized reports, exports and future physical archives are part of the same data inventory. Copying does not reset retention; expiry/redaction must cover these copies. The operations plan must specify bounded backup lifetimes, access controls and suppression/restore procedure before production. A backup cannot be treated as a separately authorized indefinite archive.
- Isolate restored data from business traffic until cleanup manifests/holds and irreversible retired-attempt guards have been reapplied and verified, including suppression of already expired/redacted PII. Restoring an older backup must not resurrect deleted delivery contacts, expired callback keys or an attempt that can accept payment again. Preserve current deletion/retirement records outside the snapshot being restored under the approved protected control-register plan. Block restore activation when that evidence cannot be reconciled.

### Migration and acceptance

- Inventory logical/physical audit stores, payment fields and all data copies; identify evidence/PII boundaries and incoming FKs. Preserve applied migrations. The backend currently has `member_auth_audit_events` and a `payments` module shell, not the implemented logical payment/audit schemas or retention jobs. Implementation needs approved mapping, permissions, Finance/Legal confirmation, control-register/backup configuration and a new migration; no production cleanup is executed by this edit.
- Verify five-year/30-day cutoffs and UTC/leap-day boundaries, immutable original anchors, duplicate recognition without clock reset, late first evidence without early deletion, holds/release and unavailable-register failure. A source copy/export/restore must not extend its deadline.
- Verify allowlists and accidental-PII cleanup without changing safe facts/actor FKs; no credentials, card/contact data or raw callback payload in audit, logs, export, manifest or backup. Test masked normal views, exact-reference authorized evidence reads, own-target scope, inactive-role denial and denial of cross-branch/bulk/unapproved technical access.
- Verify expiry under incoming-reference/retained-redemption constraints; permanently retired attempts reject late/duplicate/recovery effects after callback-key purge. Race retirement against callback/hold/reprocessing and roll back on cleanup-audit failure. Verify retry-safe batches and restore of an older snapshot without replay or PII resurrection.
- Documentation acceptance: audit/payment retention clocks, admissible/minimized evidence, privacy treatment, access, archive/expiry and restore guards are explicit. The five-year value is grounded in the approved SRS; confirmation and infrastructure/role mappings remain named implementation gates. P2.8 transaction boundaries and P2.9 JSON schemas remain separate reviews.

## Transaction boundaries and concurrency — P2.8

Design status: Proposed transaction contract for review. Defines the six requested units: complete order/items creation, resource holds, callback/application of payment, custom review/current request, business audit, and expired-hold release. Also coordinates the reviewed P2.4–P2.7 gates. This is documentation only; it neither implements locking/worker delivery nor enables conditional promotion/loyalty scope. Refund processing and refund-driven point restoration are excluded. Actual business rates, unavailable schema mappings and detailed transition policy remain their existing approval inputs.

Sources: [project transaction/module constraints](AGENT.md), [architecture working agreement](spec-general.md), [authoritative SRS payment/hold constraints](../documents/docs/report-3-software-requirement-specification/sections/06-iv-non-functional-requirements/00-overview.md), and the current [booking service](../ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/workshopbooking/application/WorkshopBookingService.java), [audit adapter](../ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/members/infrastructure/JpaAuditEventWriter.java) and [dependency manifest](../ppswbs_backend/pom.xml). Java 21/Spring Boot/JPA/MySQL is a modular monolith with one-way domain facades, not a distributed transaction system. Existing services contain transaction annotations, but do not demonstrate this contract: booking sends mail before commit, expiry can be followed by a rollback-causing exception, and logical retail/payment processing is not implemented.

### Alternatives and selected transaction model

| Approach | Benefit | Trade-off | Proposal |
| --- | --- | --- | --- |
| One long transaction including provider/email/AI/storage calls | Looks sequential. | External effects cannot be rolled back with MySQL; network latency holds locks and can leave misleading emails or duplicate initiation. | Reject. |
| Independent commits for each row/module | Short individual writes. | Can leave a partial cart, paid order without point debit, review without current decision, or action without audit. | Reject for one business invariant. |
| Short local transactions for invariant sets, durable receipt/work registration, and post-commit external work | Atomic local business effects with recoverable asynchronous work. | Requires explicit retry/delivery state and idempotent consumers. | Select. No saga, XA, Kafka or new shared business service is implied. |

- The owning application use case starts the write transaction through the Spring proxy (or an approved explicit transaction boundary). Repositories and synchronous owning-facade writes join the **same transaction manager/physical transaction** using `REQUIRED`; invariant participants cannot independently commit with `REQUIRES_NEW`. A callback event consumer may start a new transaction after the producing receipt commits; its invariant participants still join that consumer's transaction.
- Proposal isolation for these business write units is explicit `READ_COMMITTED`, with JPA pessimistic gate locks and fresh database projections. Confirm the actual connection/transaction settings on the selected MySQL release; nested `REQUIRED` calls inherit the outer characteristics. Do not rely on a server default, `readOnly = true`, annotation presence, cached entities or a plain availability read for write safety. Every writer of a protected quantity/quota/balance participates in the common gate protocol.
- Flush/commit, required successful audit and durable work registration must all succeed before a response claims a local action succeeded. Roll back the **entire unit** on any invalidating persistence/constraint/audit/checked-or-unchecked application failure. Configure rollback rules explicitly for checked failures; do not catch an exception in a rollback-only transaction and report success, or use self-invocation to pretend a new boundary exists.
- Firebase/provider authenticity checks and parsing may happen before local locks; current MySQL authorization, target state, quote/generation identity, amount/currency, retirement and deadlines are revalidated inside the applicable transaction. No provider, SMTP, AI, object-storage call or HTTP response emission runs while holding business locks.

Technical basis: [Spring transactional boundaries and rollback](https://docs.spring.io/spring-framework/reference/6.2/data-access/transaction/declarative/annotations.html), [transaction propagation](https://docs.spring.io/spring-framework/reference/6.2/data-access/transaction/declarative/tx-propagation.html), and [MySQL READ COMMITTED](https://dev.mysql.com/doc/refman/8.4/en/innodb-transaction-isolation-levels.html). These references explain mechanisms, not evidence that the current backend has implemented them.

### Shared lock order and current-state decisions

Discover identifiers without treating preliminary reads as authority. Acquire required **existing** rows in the order below, ascending primary key within each named table. Skip irrelevant tiers; never acquire an earlier tier after holding a later one. Determine the strongest needed lock up front rather than upgrading a shared lock later. New private aggregate rows may be inserted after all existing gates are acquired; allocate their IDs/command references without prematurely locking foreign parents.

| Tier | Rows / purpose | Protocol |
| --- | --- | --- |
| 1 | Involved `roles` for current actor authorization/assignment | Preserve P2.4 role-before-user ordering. Role assignment/deactivation uses `PESSIMISTIC_WRITE`; a business operation needing a stable authorization flag uses a shared pessimistic read. A system callback/job does not invent a human actor or reauthorize a frozen purchase through a fabricated role. |
| 2 | Existing aggregate anchors: `orders`, `workshop_registration`, `custom_design_requests`, then `ring_designs` | `PESSIMISTIC_WRITE` for a state-changing anchor; shared lock only for immutable-use validation. For a Member-wide point-expiry operation affecting several orders, plan and lock the full anchor set first. |
| 3 | Resource gates: `products`, then `slots` | Exclusive gates for every hold, acceptance, release or resource-quantity/capacity edit affecting them. Lock the complete cart/group, never only the first item or one participant. |
| 4 | `promotions` | Preserve P2.5 quota gates and ascending promotion IDs. Hold/consume/release and quota-limit edits use the same exclusive gate. |
| 5 | Involved `users` | Ascending IDs include actor/beneficiary where needed. P2.6 Member row is exclusive for every points writer; stable authorization-only reads may share. A provisioning transaction has no existing new-user row to lock. |
| 6 | Policy/configuration parents and eligibility links | Fixed table order: `loyalty_policies`, `workshop_locations`, `categories`, `workshop_packages`, `materials`, `gemstones`, `attachments`, `shape`, `workshop_package_locations`, `material_locations`, `workshop_package_materials`, `promotion_locations`. Shared checks/exclusive edits coordinate changes affecting new eligibility; composite-key links lock in lexicographic key order. |
| 7 | Dependent evidence/state | Fixed order: `promotion_redemptions`, `loyalty_redemptions`, `payments`, `payment_transactions`, then `loyalty_points`, `loyalty_allocations` and domain-owned confirmation/review/audit/work records in a documented deterministic key order. Lock before updating or deciding from their metadata. |

- Recheck the discovered role, beneficiary, anchor/resource IDs and policy/reservation/link sets after locks. If the current dependency set introduces an earlier unheld gate, roll back and rediscover; do not bolt that lock onto an already-running transaction. All edit/import/admin/job paths participate. This order covers explicit application locks; implicit FK/unique-index locks can still deadlock, so retry remains necessary.
- Quantity/quota/balance projections under their locked parent gate use fresh `READ_COMMITTED` scalar queries. Do not issue broad locking joins/counts that acquire other orders/users from an earlier tier, and do not reuse an older JPA snapshot. P2.4's current locking user lookup remains required at its user tier. Lock/query behavior must be verified on real MySQL, not inferred from an in-memory test database.
- `SKIP LOCKED` may select separate technical queue work, not prove business availability, unused roles, valid quota or sufficient points. Release a technical work claim transaction before acquiring business gates. Workers never lock a callback/lot first and then climb backwards to its order or Member.
- Logical deadline checks use one fresh trusted UTC decision time **after gate acquisition**, immediately before the state decision: normal acceptance requires `decision_time < every applicable hold/reservation deadline`; equality is expired. Record that decision time in the accepted transition and commit under the held locks. Provider time, browser time, receipt time or transaction-start time alone cannot authorize a stale hold. An event received before expiry but processed after the gate deadline takes the exception path; rollback/retry samples a new time.

### Quantity and hold semantics for this proposal

- To remove double subtraction, select the **remaining-unsold quantity** interpretation of `products.available_to_sell_quantity` for this transaction proposal. Let `Q` be that quantity and `H` the sum of item quantities in still-valid unpaid holds. Under the product gates, `available = Q - H`. Paid quantities have already been deducted from Q and are **not** subtracted again from availability. This is a sale-quantity gate, not a warehouse/movement ledger.
- Reserve by atomically inserting the complete pending order/items and approved discount generations; do not decrement Q at reserve time. On the first accepted payment, decrement each Q by exactly its line quantity and leave the pending-hold state in the same commit. Release/expiry removes the unpaid hold by its guarded terminal transition; it does not increase Q because reserve never decreased Q. Duplicate acceptance/release does neither again. Manual Q edits lock the products and cannot set Q below current valid holds or zero.
- A workshop reserves the full `participant_count` under its slot gate. Confirmation converts its temporary hold into committed participant consumption, not a second group reservation. Expiry frees only the unpaid temporary consumption. Slot availability is a fresh sum of participants in consuming states/valid holds, not a booking-row count or independently editable seat counter. Exact consuming-state mapping remains the booking feature's existing approval gate.
- A pending workshop resource hold needs an explicit persisted `workshop_registration.hold_expires_at`; add this nullable deadline/default `NULL`, required for a pending temporary resource hold, later than its trusted creation/reservation time. It is independent of the email token's deadline and cannot be inferred from `updated_at` or silently extended by token confirmation. Index `(status, hold_expires_at)` supports candidate discovery. Normal SRS holds last at most 15 minutes and payment links at most 10 minutes; P2.8 does not add an extension policy or override a shorter allocated-credit deadline.
- The remaining-unsold interpretation and booking deadline are proposed clarifications to the logical model, not facts about implemented inventory/booking tables. Before rollout, reconcile any historical ceiling/counter interpretation and physical booking mapping with evidence; never subtract historical paid lines twice, infer held seats from incomplete old rows or invent expiry timestamps.

### Transaction matrix

The names below identify logical units, not extra tables or Spring modules. A transaction may write several owned tables through facades; the unit's failure has the stated rollback scope.

| Unit | Must commit together | Failure / outside the transaction |
| --- | --- | --- |
| `T-CHECKOUT`: create complete retail order/items and hold | Validate authorized cart/branch/currency/current eligibility under all resource gates; insert order and **all** item snapshots; set hold deadline/amounts; reserve selected P2.5 quota and P2.6 lot allocations/point-money snapshots if enabled; required domain audit; durable downstream work if any. | Any invalid item/stock/discount/policy/audit aborts the entire order/hold. No partial cart or autonomous item/discount commit. Gateway initiation belongs to a later committed attempt's post-commit work. |
| `T-BOOK-HOLD`: create/continue a complete workshop group | Slot/branch/package/design eligibility, complete group hold and snapshots/deadline, required local confirmation/invoice state when its approved feature owns them, audit and durable notification intent. | Insufficient group seats or unresolved pricing/mapping creates no partial group. Guest email confirmation is separate from deposit/committed booking confirmation. Generate/send messages only after commit. |
| `T-ATTEMPT`: prepare payment/retry | Lock target/resources/reservations; revalidate deadlines, frozen payable quote and no accepted payment; create or recognize the exact payment attempt; append its promotion-generation link and set its immutable loyalty-generation FK/time; audit/work registration. | No links to a replacement generation on an old attempt. Call VNPay after commit. Network ambiguity leaves the committed attempt for signed recovery/expiry; it is not permission to release/recharge. |
| `T-RECEIPT`: receive verified IPN/QueryDR evidence | Normalize authenticated correlated evidence, insert/recognize its deduplicated `payment_transactions` row, and durably register its processing work in the same commit. This unit touches receipt/attempt evidence only and never subsequently acquires earlier business gates. | No order/capacity/quota/points effect here. An insert/work failure acknowledges no completed receipt. Unmatched/unverifiable input follows the safe P2.7 rejection path, not a fabricated payment FK. |
| `T-PAYMENT`: apply one received event | Owning consumer locks target and full resources/discount generations; rechecks retirement, exact link/quote and deadlines; applies only an allowed payment transition; converts stock/seat hold and target confirmation; redeems quota; posts the single point debit and settles existing `loyalty_allocations` rows; completes callback processing/first-application marker; required successful audit and durable receipt/QR/notification intent. | Any failure rolls back **all** local business effects and completion markers; `T-RECEIPT` evidence/work remains recoverable. A duplicate is an authorized idempotent result, not a second debit/deduction. Genuine late/mismatched/old-generation evidence gets a committed non-application reason/audit, no new fulfilment. |
| `T-REVIEW`: custom review and current request | Lock/recheck request and reviewer authorization/independence; append one allowed `custom_design_reviews` decision; update all current-request decision/actor/time/reason fields; required owning audit/notification intent. | No review-only or status-only commit. Competing reviewers see the committed winner's state; another terminal decision is rejected. No order/payment/design/AI linkage is added to this review-only flow. |
| `T-EXPIRE`: release one target's expired unpaid hold | Under target/resource/discount/Member gates, recheck due time and absence of already accepted payment; change only allowed unpaid target state; release exact quota/point hold generations and approved temporary booking/email/invoice state; expire the relevant pending attempt only through its allowed rules; audit/work completion. | Rollback leaves the original whole hold/state set. A paid/redeemed target is a no-op, not a release/refund. No stock increment or repeated seat release. Successful later evidence remains an external exception. |
| `T-AUDIT`: success, expected denial or technical failure | Successful action audit joins its business transaction. An expected pre-mutation denial may commit only its safe rejection audit and typed denied result. After a failed business transaction has fully rolled back, a fresh bounded audit transaction records safe failure context. | Never commit `SUCCESS` independently of the action. Never throw a rollback-causing denial after writing its only rejection audit and expect it to survive. Audit unavailable means no successful mutation and a controlled operational failure. |

- P2.6 Member-wide lot expiry and P2.7 retirement/cleanup use the same tier planning, complete invariant-set commit and safe audit/work rules; preserve their original source expiry, evidence deadlines and irreversible guards. A Member-wide expiry batch locks all affected anchors before the Member; if discovery missed an affected order, abort/replan.
- Jobs discover candidates outside the authoritative write unit, then process one target or a bounded, deterministically locked related set per transaction. Do not wrap the entire expiry backlog in a single scheduler transaction. If expiry is requested while returning an API error, let the expiry use case commit a typed expired result before the web layer emits the error; do not mutate to expired and then throw inside the same rollback-configured transaction.

### Durable callback processing and command idempotency

- Add required opaque `orders.checkout_key VARCHAR(128)`, no default, unique `uk_orders_checkout_key`. One trusted checkout command retains its key across retries and binds Member, branch/currency, complete cart quantities and requested benefits. Exact repeat returns the original scoped order/quote/state, without repricing, creating new holds or reopening expired checkout. Reuse with another subject/cart/benefit request is a conflict; a changed quote/new checkout requires a new key. A unique-key race rolls back the loser before loading the winner in a fresh transaction; never continue a partially failed insert.
- Existing `booking_code`, `payment_code`, promotion/loyalty reservation keys and ledger keys remain their command/event gates. Creation allocates a trusted stable command reference once, before transaction retries; unrelated transport submissions do not automatically share one. Booking/attempt retries compare identity/snapshots and current state, not just key presence. Keys/fingerprints cannot contain customer contacts or credentials; exact provider/key encoding belongs to the approved adapter contract.
- Add `uk_payment_transactions_fingerprint (provider, payment_id, payload_hash)` for duplicate normalized verified receipts, retaining the native `(provider, provider_event_id)` uniqueness where supplied. Hash generation/normalization is trusted server work after authentication; verify matching identity/amount/currency/content on a native-key collision rather than overwriting evidence. Provider IDs are nullable when the authenticated message does not supply them; no fake VNPay event ID is fabricated. Fingerprints recognize receipts, not proof of signature/payment or the sole business idempotency guard.
- Extend `payment_transactions` with nullable `business_applied_at` and safe nullable `processing_reason VARCHAR(100)`, both default `NULL`. `processed_at IS NULL` means no final local processing decision. On first accepted effect, commit `business_applied_at` and `processed_at` with that effect and keep reason null. A completed duplicate/rejection/exception has null first-application time and a non-blank safe diagnostic reason when it did not apply this event. An already completed identical receipt returns its existing result; its original timestamps/first-application marker are not overwritten. Transient failures leave the receipt incomplete for retry, with separate safe failure evidence if required.
- Enforced `chk_payment_transactions_processing` has this exact row shape: `(processed_at IS NULL AND business_applied_at IS NULL AND processing_reason IS NULL) OR (processed_at IS NOT NULL AND processed_at >= received_at AND ((business_applied_at IS NOT NULL AND business_applied_at >= received_at AND business_applied_at <= processed_at AND processing_reason IS NULL) OR (business_applied_at IS NULL AND processing_reason IS NOT NULL AND CHAR_LENGTH(TRIM(processing_reason)) > 0)))`. `received_at` is required `NOT NULL`. Diagnostics contain no raw provider payload/PII and are not new payment status codes. Immutable receipt facts and the one accepted target/attempt, exact-generation and ledger gates prevent another event/fingerprint applying the same business effect again.
- Callback publication carries only minimal receipt/attempt IDs and explicit contract metadata. The payment module owns receipt/adapters; the target-domain consumer owns resource/target application and calls the payment facade in its transaction. Publication does not require a reverse Java import to the target module. Normal checkout can call the payment facade one-way; do not introduce mutual payment/order facade dependencies or move business orchestration into `common`. Verify the final module graph.
- A durable processing work record/publication must be saved with `T-RECEIPT`, and recovery must find unfinished receipts even after a process restart. Proposed mechanism is the existing Spring Modulith JPA event-publication capability with explicit configuration/schema/republication verification; the dependency alone is not proof it works. Its technical tables/versioned event mapping belong to the feature plan, outside this logical business-table inventory. Consumer completion may be redelivered after business commit; durable `processed_at`/application guards make it a no-op.
- The callback HTTP handler stays outside these database transactions and follows the approved VNPay response contract. Durable receipt acceptance is not a claim that an order is paid. When a response code requires completed merchant processing, await/observe the committed consumer result before that response; timeout/failure must not fabricate success. The signed QueryDR recovery gate remains the approved live-hold rule. A Return URL only displays current committed state.

### External effects, audit survival and retry

- SMTP, provider initiation and eligible storage cleanup run after the producing commit from durable registered work. Plain `AFTER_COMMIT`/in-memory events alone can lose work if the process stops; do not claim durable delivery from that annotation. Notification failure cannot roll back a paid order/booking or turn it into unpaid. Delivery is retryable and may repeat externally; promise idempotent local state, not exactly-once email or network effects.
- Provider-initiation retries use the same committed attempt and the approved provider idempotency/recovery contract; a timeout is ambiguous and cannot trigger a fresh charge automatically. A new attempt is a separate permitted command after target/generation revalidation. Notification work stores minimal IDs; load allowed target contact data through its owning facade. Confirmation tokens/secrets cannot be stored in publication JSON/plaintext; approved token issuance/reissuance must preserve the original hold limit.
- Expected authorization/self-review/eligibility denial is decided before business mutation. Persist its domain-appropriate rejection audit and return a typed denied result so the transaction can commit that audit; the outer web boundary maps it to the error response. If an exception/persistence failure invalidates a business unit, roll it back first, then call a separate proxied audit use case in a fresh transaction outside the released business locks. Never open a rejection `REQUIRES_NEW` while suspending an outer transaction whose locked/FK rows the audit needs.
- Safe failure evidence uses original command/correlation IDs, actor/service identity, target IDs and stable diagnostic context, not raw exception/request dumps or uncommitted new-parent FKs. If failure-audit persistence also fails, surface a controlled error and sanitized operational alert; do not claim durable rejection evidence or commit the denied action. Required action/rejection evidence stays in the appropriate owning timeline, respecting P2.7 privacy and scope.
- Required successful audit may be the existing immutable owning timeline itself when it contains the approved actor/service, target, decision, time and correlation facts. Do not duplicate every review/payment into administrative `audit_logs` merely to satisfy the matrix. Missing required evidence fields/physical mapping must be supplied by that domain's approved implementation; a logger call alone is not durable business evidence.
- Deadlock, lock timeout, optimistic conflict or lost commit acknowledgment triggers a fresh boundary/current-state lookup, never a stale in-transaction replay. Roll back the full application unit even if the engine timed out only one statement. Bounded retries re-discover gates, reauthorize and recheck deadlines; use the original command/event key to distinguish committed success from an uncommitted attempt. Validation/permission denial, conflicting key reuse and closed/expired generations are not blind-retry cases. Retry count/time budget/alerts must be configured and verified in the implementation plan, never an unbounded loop.

Technical basis: [Spring transaction-bound events](https://docs.spring.io/spring-framework/reference/6.2/data-access/transaction/event.html), [Spring Modulith 1.3 durable event publications](https://docs.spring.io/spring-modulith/reference/1.3/events.html), [MySQL locking reads](https://dev.mysql.com/doc/refman/8.4/en/innodb-locking-reads.html) and [deadlock handling](https://dev.mysql.com/doc/refman/8.4/en/innodb-deadlocks-handling.html). Production behavior depends on the verified selected versions, proxy invocation, transaction manager, queries and recovery configuration.

### Physical integration and acceptance

- The P2.8 additions are command/processing fields, indexes/date-shape checks and a booking hold deadline; no FK, business status vocabulary or numeric business policy is added. Update these fields in their table definitions below; keep the 69-FK/20-domain registries and reviewed role/promotion/loyalty/retention invariants. Technical publication tables are not asserted to be migrated or included in those counts.
- Keep applied migrations immutable. Reconcile existing order keys, quantity meaning, booking deadlines, callback fingerprints/native-ID nullability and completion provenance before enabling constraints/writers. Do not manufacture historical checkout commands, hold dates or accepted callbacks from current status alone. Resolve physical identity/booking mapping and current domain-audit ownership without treating the existing shared audit adapter as the intended final boundary.
- Verification requires independent MySQL transactions: two carts competing for the final quantity, reversed multi-item input order, last group seats/quota/points, manual quantity/cap edits, role change versus checkout and custom reviewers racing. Verify full rollback when any item/discount/review/audit/work registration fails. Fresh projections must not oversell, oversubscribe, overspend or duplicate a terminal review.
- Verify all payment/expiry commit orders at just before/equal/after deadline; receipt-before-deadline with delayed processing; old-generation and ambiguous-initiation outcomes; duplicate native events/fingerprints; one failed attempt while another can still succeed; retired attempts; audit/ledger failure after receipt persistence. State/quantity/seat/quota/point/completion changes either all commit or all roll back, while committed receipt evidence remains retryable.
- Kill processing after receipt commit, after business commit but before work acknowledgment, and after work registration but before email/provider execution. Restart/redeliver without a second sale/debit/booking confirmation. Verify retry after lost commit acknowledgment, independent rejection/failure-audit survival, scheduler batch isolation and module-graph verification. Neither an in-memory mock nor annotation inspection proves these guarantees.
- Documentation acceptance: each of the six P2.8 operations has explicit commit/rollback scope, current-state gates, audit behavior, retry and external-effect boundary. Backend/schema/concurrency tests remain later implementation gates; this edit runs no database migration or provider call. P2.9 JSON compatibility and P2.10 remaining detailed business policies stay separate reviews.

## JSON snapshot contracts and compatibility — P2.9

Design status: Proposed documentation correction for review. Scope is exactly `ring_designs.component_snapshot`, `audit_logs.before_data` and `audit_logs.after_data`: version identification, closed validation schemas, compatibility and permitted payload bounds. This proposal adds no table, FK, SQL status or sibling version column. It does not implement a validator/migration or change P2.7 retention, P2.8 transaction boundaries or P2.10 pricing/availability policies.

### Problem, alternatives and ownership

The risk is that syntactically valid JSON can have an unknown shape, silently lose old meaning or carry fields that P2.7 prohibits. A version number alone does not prevent these failures; the producer, validator and historical reader must share an immutable contract.

| Approach | Benefits | Trade-off | Decision |
| --- | --- | --- | --- |
| Versioned envelope inside each JSON document | Version/name travel with snapshots, exports and backups; no separate version authority. | Every reader must unwrap the envelope and dispatch by contract. | Select for all three columns. |
| Separate `*_schema_version` columns | Simple SQL version filtering. | Each JSON and its sibling value must stay consistent; detached exports need extra metadata. | Not selected for these bounded snapshots. |
| Infer shape or consult a mutable schema/reference table | Fewer explicit payload fields or runtime configurability. | Ambiguous old shapes and mutable validation rules can reinterpret historical evidence. | Not selected; schemas are immutable reviewed application resources, embedded in versioned database constraints. |

- The existing Java 21/Spring Boot/JPA/MySQL modular-monolith stack remains unchanged. Design/catalogue producers own component schemas; the business domain performing an administrative action owns its audit profile and writer. A technical validator may validate bytes/shapes, but must not become a central business audit service in `common`.
- The implemented authentication audit `safe_metadata` text and its current ad hoc serialization are not evidence that these logical JSON contracts already exist. Map logical/physical columns during the owning feature plan; no implicit conversion of that text to any V1 profile is authorized.
- Schema resources and immutable generated inline constraint schemas are deployment artifacts, not user-editable configuration. Their physical resource names, Java validator library and migrations belong to the approved implementation plan.

### Envelope and contract registry

Every non-SQL-null value is an object with **exactly** `schema_name`, `schema_version` and `data`. Names are case-sensitive exact strings; version is a JSON integer, never a string, decimal version label or default. V1 accepts only the pairs below. An object/array/scalar without the envelope, JSON literal `null`, missing keys and unknown names/versions are invalid new writes.

| Column | Exact `schema_name` | `schema_version` | V1 `data` | SQL-null boundary |
| --- | --- | --- | --- | --- |
| `ring_designs.component_snapshot` | `ring_design_components` | `1` | Closed configuration, selected catalogue inputs and nullable evaluation, defined below. | Allowed only while `status = draft`; a non-null draft snapshot must still validate. Required for validated/published/archived designs under the existing frozen-snapshot proposal. |
| `audit_logs.before_data` | `audit_change` | `1` | One closed action profile and allowlisted pre-action values. | No before-image, for example creation or rejection without an admissible comparison. |
| `audit_logs.after_data` | `audit_change` | `1` | The same contract, with committed post-action values. | No after-image, for example rejection/failure without a committed mutation. |

- `schema_version` identifies structure, field meanings, units and null semantics. `ring_designs.rules_version` identifies the approved evaluation rule set. They are independent; changing a rule set does not automatically change the JSON schema, and a schema upgrade cannot supply a missing rule set.
- All listed objects are closed (`additionalProperties: false`), including nested objects. Required nullable fields must appear explicitly with JSON `null`; null means unavailable/not applicable under the documented field rule, never zero, unlimited, a made-up component or an inferred old value.
- IDs and positive BIGINT limits use canonical base-10 **strings** matching `^[1-9][0-9]{0,18}$`, additionally checked to be at most `9223372036854775807`. This avoids lossy numeric conversion by JSON clients. Selection quantities use JSON integers in `1..2147483647`.
- Non-negative `DECIMAL(15,2)` values use decimal strings with exactly two fractional digits, at most 13 integer digits and no signs/exponents/leading zeroes except zero itself. `DECIMAL(10,4)` scores use four fractional digits and at most six integer digits. Parse/check using exact decimal arithmetic; never binary floating point. Positive values/percentage caps still follow their existing business rules.
- Currency strings have three uppercase letters, and must be an approved currency in the owning flow; a regex is not a currency reference catalogue. UTC instants use `YYYY-MM-DDTHH:mm:ssZ`; operating dates/times use `YYYY-MM-DD`/`HH:mm:ss` in the existing branch-time context. Validate real calendar/time values, source timezone mapping and paired ranges in the service; regex matching alone is insufficient.

### Component V1 data and freeze rules

V1 preserves existing catalogue facts, selected quantities/variant/placement and evaluation outputs; it introduces no arbitrary `attributes`, `metadata`, rule expressions or user text bag. All properties in the following schema are required, including nullable ones. Empty gemstone/attachment arrays mean no selection of that kind.

Proposed JSON Schema, Draft 4, for `ring_design_components/1`:

```json
{
  "$schema": "http://json-schema.org/draft-04/schema#",
  "type": "object",
  "additionalProperties": false,
  "required": ["schema_name","schema_version","data"],
  "properties": {
    "schema_name": {"type":"string","enum":["ring_design_components"]},
    "schema_version": {"type":"integer","enum":[1]},
    "data": {
      "type": "object",
      "additionalProperties": false,
      "required": ["ring_design_id","captured_at","ring_size","material","shape","gemstones","attachments","evaluation"],
      "properties": {
        "ring_design_id": {"type":"string","minLength":1,"maxLength":19,"pattern":"^[1-9][0-9]{0,18}$"},
        "captured_at": {"type":"string","minLength":20,"maxLength":20,"pattern":"^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$"},
        "ring_size": {"type":["string","null"],"minLength":1,"maxLength":30},
        "material": {
          "type": "object",
          "additionalProperties": false,
          "required": ["material_id","material_code","purity","unit_price","price_unit","currency","difficulty_score"],
          "properties": {
            "material_id": {"type":"string","minLength":1,"maxLength":19,"pattern":"^[1-9][0-9]{0,18}$"},
            "material_code": {"type":"string","minLength":1,"maxLength":64},
            "purity": {"type":["string","null"],"minLength":1,"maxLength":50},
            "unit_price": {"type":"string","minLength":1,"maxLength":16,"pattern":"^(0|[1-9][0-9]{0,12})\\.[0-9]{2}$"},
            "price_unit": {"type":"string","minLength":1,"maxLength":20},
            "currency": {"type":"string","pattern":"^[A-Z]{3}$","minLength":3,"maxLength":3},
            "difficulty_score": {"type":["string","null"],"minLength":1,"maxLength":11,"pattern":"^(0|[1-9][0-9]{0,5})\\.[0-9]{4}$"}
          }
        },
        "shape": {
          "type": "object",
          "additionalProperties": false,
          "required": ["shape_id","shape_code","price_adjustment","currency","difficulty_score"],
          "properties": {
            "shape_id": {"type":"string","minLength":1,"maxLength":19,"pattern":"^[1-9][0-9]{0,18}$"},
            "shape_code": {"type":"string","minLength":1,"maxLength":64},
            "price_adjustment": {"type":["string","null"],"minLength":1,"maxLength":16,"pattern":"^(0|[1-9][0-9]{0,12})\\.[0-9]{2}$"},
            "currency": {"type":["string","null"],"pattern":"^[A-Z]{3}$","minLength":3,"maxLength":3},
            "difficulty_score": {"type":["string","null"],"minLength":1,"maxLength":11,"pattern":"^(0|[1-9][0-9]{0,5})\\.[0-9]{4}$"}
          }
        },
        "gemstones": {
          "type": "array",
          "maxItems": 128,
          "items": {
            "type": "object",
            "additionalProperties": false,
            "required": [
              "selection_id",
              "component_id",
              "component_code",
              "quantity",
              "variant_code",
              "placement",
              "gemstone_type",
              "color",
              "cut_name",
              "dimensions",
              "unit_price",
              "price_unit",
              "currency",
              "difficulty_score"
            ],
            "properties": {
              "selection_id": {"type":"string","minLength":1,"maxLength":19,"pattern":"^[1-9][0-9]{0,18}$"},
              "component_id": {"type":"string","minLength":1,"maxLength":19,"pattern":"^[1-9][0-9]{0,18}$"},
              "component_code": {"type":"string","minLength":1,"maxLength":64},
              "quantity": {"type":"integer","minimum":1,"maximum":2147483647},
              "variant_code": {"type":["string","null"],"minLength":1,"maxLength":64},
              "placement": {"type":["string","null"],"minLength":1,"maxLength":100},
              "gemstone_type": {"type":["string","null"],"minLength":1,"maxLength":100},
              "color": {"type":["string","null"],"minLength":1,"maxLength":50},
              "cut_name": {"type":["string","null"],"minLength":1,"maxLength":100},
              "dimensions": {"type":["string","null"],"minLength":1,"maxLength":100},
              "unit_price": {"type":"string","minLength":1,"maxLength":16,"pattern":"^(0|[1-9][0-9]{0,12})\\.[0-9]{2}$"},
              "price_unit": {"type":"string","minLength":1,"maxLength":20},
              "currency": {"type":"string","pattern":"^[A-Z]{3}$","minLength":3,"maxLength":3},
              "difficulty_score": {"type":["string","null"],"minLength":1,"maxLength":11,"pattern":"^(0|[1-9][0-9]{0,5})\\.[0-9]{4}$"}
            }
          }
        },
        "attachments": {
          "type": "array",
          "maxItems": 128,
          "items": {
            "type": "object",
            "additionalProperties": false,
            "required": [
              "selection_id",
              "component_id",
              "component_code",
              "quantity",
              "variant_code",
              "placement",
              "attachment_type",
              "unit_price",
              "price_unit",
              "currency",
              "difficulty_score"
            ],
            "properties": {
              "selection_id": {"type":"string","minLength":1,"maxLength":19,"pattern":"^[1-9][0-9]{0,18}$"},
              "component_id": {"type":"string","minLength":1,"maxLength":19,"pattern":"^[1-9][0-9]{0,18}$"},
              "component_code": {"type":"string","minLength":1,"maxLength":64},
              "quantity": {"type":"integer","minimum":1,"maximum":2147483647},
              "variant_code": {"type":["string","null"],"minLength":1,"maxLength":64},
              "placement": {"type":["string","null"],"minLength":1,"maxLength":100},
              "attachment_type": {"type":["string","null"],"minLength":1,"maxLength":100},
              "unit_price": {"type":"string","minLength":1,"maxLength":16,"pattern":"^(0|[1-9][0-9]{0,12})\\.[0-9]{2}$"},
              "price_unit": {"type":"string","minLength":1,"maxLength":20},
              "currency": {"type":"string","pattern":"^[A-Z]{3}$","minLength":3,"maxLength":3},
              "difficulty_score": {"type":["string","null"],"minLength":1,"maxLength":11,"pattern":"^(0|[1-9][0-9]{0,5})\\.[0-9]{4}$"}
            }
          }
        },
        "evaluation": {
          "type": ["object","null"],
          "additionalProperties": false,
          "required": ["rules_version","estimated_price","currency","difficulty_score"],
          "properties": {
            "rules_version": {"type":"string","minLength":1,"maxLength":64},
            "estimated_price": {"type":["string","null"],"minLength":1,"maxLength":16,"pattern":"^(0|[1-9][0-9]{0,12})\\.[0-9]{2}$"},
            "currency": {"type":["string","null"],"pattern":"^[A-Z]{3}$","minLength":3,"maxLength":3},
            "difficulty_score": {"type":["string","null"],"minLength":1,"maxLength":11,"pattern":"^(0|[1-9][0-9]{0,5})\\.[0-9]{4}$"}
          }
        }
      }
    }
  }
}
```

- `material`/`shape` IDs and codes must match the selected parents. Each array item uses the **junction row ID** as `selection_id` and the selected catalogue ID as `component_id`. Require a one-to-one match with that design's gemstone/attachment selection rows, including quantity, variant and placement; prohibit duplicated selection IDs. Do not impose uniqueness on component ID alone: the existing duplicate/placement taxonomy remains unresolved.
- Catalogue attributes, price/unit/currency and difficulty inputs are copied from approved server-side catalogue state in the guarded design transaction; accept neither a client-built snapshot nor an entire JPA entity serialization. `captured_at` is the server snapshot instant. A frozen snapshot is the historical source for those values after catalogue edits/deactivation, not a command to restore or reprice live parents.
- `price_unit`, purity, size, variant, placement and classification strings are bounded storage shapes, not approval of a free vocabulary. Validate selections against the owning approved options/rules. A nullable shape contribution/currency is not an implicit zero charge.
- `evaluation = null` means no stored evaluation results and requires the row's estimate, currency, difficulty result and `rules_version` to be null. A non-null evaluation requires a non-blank approved `rules_version` and at least one estimate/difficulty result; an estimate requires currency, and currency is null if there is no estimate. Every evaluation field must exactly match its corresponding row column.
- Evaluation must use the captured inputs and approved rules. V1 does not invent material weight, unit conversion, labour cost, branch availability or package-price inputs. If a rule needs an input not represented here, do not issue an estimate/freeze based on an incomplete snapshot: define a reviewed typed schema version for that input first. The P2.10 formulas/taxonomies remain unresolved.
- Capture component rows, selections, snapshot and corresponding results atomically under P2.8's owning aggregate/current-state gates. Validation/freeze checks required snapshot presence and supported input/rule compatibility; a shape-valid document alone cannot prove design feasibility. Once frozen, do not mutate/rebuild JSON from current catalogue values; corrections create a new design version under existing lineage rules.

### Audit V1 profiles and paired images

Audit V1 uses `data = {profile, values}`. The profile closes the allowed keys/types; the service also binds it to an approved logical entity/action. Actor, target, outcome, reason, request ID and timestamp already belong to row columns and must not be duplicated into arbitrary JSON.

| Exact profile | Applicable existing context | Allowed snapshot values / binding |
| --- | --- | --- |
| `role_assignment` | `entity_name = users`, `action = ASSIGN_ROLE`. | Exactly `role_id`. Before/after contain the old/new role; target is `entity_id`. |
| `role_activation` | `entity_name = roles`, `DEACTIVATE_ROLE` / `REACTIVATE_ROLE`. | Exactly boolean `is_active`. Target is `entity_id`; P2.4 remains authoritative. |
| `promotion_change` | `entity_name = promotions`, existing `UPDATE_CAMPAIGN` action. | Non-empty subset of the listed discount/limit/window/activation fields. Validate complete pre/post campaign state, even when the audit records only changed fields. |
| `workshop_exception` | `entity_name = workshop_exceptions`, existing `CREATE_HOLIDAY` or proposed `UPDATE_HOLIDAY` / `DEACTIVATE_HOLIDAY` / `REACTIVATE_HOLIDAY` actions. | Scope/date and the listed closure/capacity/time/activation fields; no free-text holiday reason in JSON. The row's minimized reason can explain the action. |
| `reference_activation` | Proposed stable `ACTIVATE_REFERENCE` / `DEACTIVATE_REFERENCE` actions for the listed `is_active` reference entities/relationships. | Exactly `is_active`, `location_id`, `parent_id`. Simple-key targets use `entity_id` and null scope IDs; composite link targets carry their exact two parent IDs as specified below. |

| `package_material_activation` | `entity_name = workshop_package_materials`, proposed `ACTIVATE_PACKAGE_MATERIAL` / `DEACTIVATE_PACKAGE_MATERIAL` actions. | Exactly `workshop_package_id`, `material_id` and boolean `is_active`; null `entity_id` for the composite target. |

Proposed JSON Schema, Draft 4, shared by `before_data` and `after_data`:

```json
{
  "$schema": "http://json-schema.org/draft-04/schema#",
  "type": "object",
  "additionalProperties": false,
  "required": ["schema_name","schema_version","data"],
  "properties": {
    "schema_name": {"type":"string","enum":["audit_change"]},
    "schema_version": {"type":"integer","enum":[1]},
    "data": {
      "oneOf": [
        {
          "type": "object",
          "additionalProperties": false,
          "required": ["profile","values"],
          "properties": {
            "profile": {"type":"string","enum":["role_assignment"]},
            "values": {
              "type": "object",
              "additionalProperties": false,
              "required": ["role_id"],
              "properties": {"role_id":{"type":"string","minLength":1,"maxLength":19,"pattern":"^[1-9][0-9]{0,18}$"}},
              "minProperties": 1
            }
          }
        },
        {
          "type": "object",
          "additionalProperties": false,
          "required": ["profile","values"],
          "properties": {
            "profile": {"type":"string","enum":["role_activation"]},
            "values": {
              "type": "object",
              "additionalProperties": false,
              "required": ["is_active"],
              "properties": {"is_active":{"type":"boolean"}},
              "minProperties": 1
            }
          }
        },
        {
          "type": "object",
          "additionalProperties": false,
          "required": ["profile","values"],
          "properties": {
            "profile": {"type":"string","enum":["promotion_change"]},
            "values": {
              "type": "object",
              "additionalProperties": false,
              "required": [],
              "properties": {
                "discount_type": {"type":"string","enum":["percentage","fixed_amount"]},
                "discount_value": {"type":"string","minLength":1,"maxLength":16,"pattern":"^(0|[1-9][0-9]{0,12})\\.[0-9]{2}$"},
                "currency": {"type":["string","null"],"pattern":"^[A-Z]{3}$","minLength":3,"maxLength":3},
                "minimum_order_amount": {"type":["string","null"],"minLength":1,"maxLength":16,"pattern":"^(0|[1-9][0-9]{0,12})\\.[0-9]{2}$"},
                "maximum_discount_amount": {"type":["string","null"],"minLength":1,"maxLength":16,"pattern":"^(0|[1-9][0-9]{0,12})\\.[0-9]{2}$"},
                "total_usage_limit": {"type":["string","null"],"minLength":1,"maxLength":19,"pattern":"^[1-9][0-9]{0,18}$"},
                "per_user_usage_limit": {"type":["string","null"],"minLength":1,"maxLength":19,"pattern":"^[1-9][0-9]{0,18}$"},
                "starts_at": {"type":"string","minLength":20,"maxLength":20,"pattern":"^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$"},
                "ends_at": {"type":"string","minLength":20,"maxLength":20,"pattern":"^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$"},
                "is_active": {"type":"boolean"}
              },
              "minProperties": 1
            }
          }
        },
        {
          "type": "object",
          "additionalProperties": false,
          "required": ["profile","values"],
          "properties": {
            "profile": {"type":"string","enum":["workshop_exception"]},
            "values": {
              "type": "object",
              "additionalProperties": false,
              "required": [],
              "properties": {
                "location_id": {"type":["string","null"],"minLength":1,"maxLength":19,"pattern":"^[1-9][0-9]{0,18}$"},
                "slot_id": {"type":["string","null"],"minLength":1,"maxLength":19,"pattern":"^[1-9][0-9]{0,18}$"},
                "exception_date": {"type":"string","minLength":10,"maxLength":10,"pattern":"^[0-9]{4}-[0-9]{2}-[0-9]{2}$"},
                "is_closed": {"type":"boolean"},
                "capacity_override": {"type":["integer","null"],"minimum":0,"maximum":2147483647},
                "start_time_override": {"type":["string","null"],"minLength":8,"maxLength":8,"pattern":"^[0-9]{2}:[0-9]{2}:[0-9]{2}$"},
                "end_time_override": {"type":["string","null"],"minLength":8,"maxLength":8,"pattern":"^[0-9]{2}:[0-9]{2}:[0-9]{2}$"},
                "is_active": {"type":"boolean"}
              },
              "minProperties": 1
            }
          }
        },
        {
          "type": "object",
          "additionalProperties": false,
          "required": ["profile","values"],
          "properties": {
            "profile": {"type":"string","enum":["reference_activation"]},
            "values": {
              "type": "object",
              "additionalProperties": false,
              "required": ["is_active","location_id","parent_id"],
              "properties": {
                "is_active": {"type":"boolean"},
                "location_id": {"type":["string","null"],"minLength":1,"maxLength":19,"pattern":"^[1-9][0-9]{0,18}$"},
                "parent_id": {"type":["string","null"],"minLength":1,"maxLength":19,"pattern":"^[1-9][0-9]{0,18}$"}
              },
              "minProperties": 1
            }
          }
        },
        {
          "type": "object",
          "additionalProperties": false,
          "required": ["profile","values"],
          "properties": {
            "profile": {"type":"string","enum":["package_material_activation"]},
            "values": {
              "type": "object",
              "additionalProperties": false,
              "required": ["workshop_package_id","material_id","is_active"],
              "minProperties": 1,
              "properties": {
                "workshop_package_id": {"type":"string","minLength":1,"maxLength":19,"pattern":"^[1-9][0-9]{0,18}$"},
                "material_id": {"type":"string","minLength":1,"maxLength":19,"pattern":"^[1-9][0-9]{0,18}$"},
                "is_active": {"type":"boolean"}
              }
            }
          }
        }
      ]
    }
  }
}
```

- For `reference_activation`, simple targets are `categories`, `workshop_locations`, `materials`, `gemstones`, `attachments`, `shape` and `loyalty_policies`. Link targets are `promotion_locations` (`parent_id = promotion_id`), `material_locations` (`parent_id = material_id`), `workshop_package_locations` (`parent_id = workshop_package_id`) with their `location_id`. `workshop_package_materials` uses its dedicated `package_material_activation` profile with exact package/material IDs. Never mislabel a material ID as a location ID. Composite targets have null `entity_id`; these typed IDs are logical audit scope, not new FK declarations.
- The owning domain must register exact entity/action/profile mappings before enabling a writer; matching a profile shape alone is insufficient. The existing free-standing `schedule_exceptions` example is replaced by this document's actual `workshop_exceptions` entity. Any additional action, account-status change, configuration key, publication flag or price/quantity snapshot requires an explicitly reviewed closed profile/version; no generic `values: {}`, unrestricted string field names or fallback profile is allowed.
- For a successful update, both images use the same name/version/profile and the same non-empty field set. Include old/new values of every admissible changed field; compare against locked pre/post state. A creation has SQL-null before-image and an after-image containing the applicable profile's complete admissible scope/state. `CREATE_HOLIDAY` requires all eight declared exception fields, including nullable scope/override fields; no JSON holiday reason is permitted. SQL-null both sides is allowed only when the registered action has no admissible change image.
- For `REJECTED`/`FAILED`, never present proposed changes as a committed after-image. Use SQL-null after-image and only an admissible actual pre-image if required by the registered action; reason/outcome explain the rejection/failure. P2.8's independent failure/rejection audit transaction preserves the evidence without inventing committed state.
- Do not carry contact/profile fields, custom/AI text, raw callback input, payment/card credentials, voucher secrets, tokens, headers, exception stacks, arbitrary URLs/media or whole entities. Public catalogue codes and operational numeric IDs are allowed only for the approved purpose; pseudonymous IDs still follow P2.7 access/retention. Free-text row `reason` also needs minimization; the JSON schema does not sanitize that column.
- A privacy-cleanup operation under P2.7 is an exceptional authorized redaction with its own minimal evidence, not permission to edit audit images during ordinary administration. JSON versioning does not extend retention or recreate erased fields from an archive.

### Validation layers, resource limits and named checks

MySQL supports Draft 4 validation in `JSON_SCHEMA_VALID`, including use in `CHECK`; constraints need inline schemas, `$ref` is unsupported, and invalid regex patterns may be ignored. Use the fully expanded schemas above and verify regex behavior in both validators. [MySQL JSON validation documentation](https://dev.mysql.com/doc/refman/8.4/en/json-validation-functions.html), [Draft 4 validation specification](https://json-schema.org/draft-04/draft-fge-json-schema-validation-00).

| Layer | Required enforcement |
| --- | --- |
| Strict parsing and construction | Reject duplicate keys, malformed JSON, excess input/depth and unknown properties before normalization. Build persisted snapshots from typed, allowlisted server data; do not silently drop prohibited fields and report success. MySQL normalizes duplicate keys on insertion, so post-insert validation cannot recover them. [MySQL JSON normalization](https://dev.mysql.com/doc/refman/8.4/en/json.html#json-normalization) |
| Application schema validation | Dispatch by the exact supported name/version and validate the complete envelope with the bundled immutable Draft 4 schema. Enforce numeric/date semantics, profile/action binding, cross-row equality and permitted source data before any write. Unknown contracts fail closed. |
| Database validation | Named enforced checks validate every non-null envelope against the accepted inline schemas and reject JSON literal null. Separate row-local guards enforce frozen presence, payload bounds and applicable audit row/profile/outcome shape. Cross-row catalogue/action authorization remains in owning services. |
| Read/export/restore | Select a reader for the exact stored contract; verify archived/restored payloads against it. Escape displayed strings; do not evaluate formulas, follow payload URLs or treat snapshot data as executable input. Apply P2.7 authorization/minimization to the envelope and exports. |

Proposed technical resource ceilings, distinct from jewellery/business cardinalities:

- Component JSON: at most **65,536 bytes (64 KiB)** per document; each selection array at most **128 items** (256 combined); maximum object/array nesting depth **8**, counting the root as depth 1.
- Audit JSON: at most **16,384 bytes (16 KiB)** per image and **32,768 bytes** combined per row, maximum depth **8**. Each profile has only its finite declared fields; no nested arrays or free-form text payloads.
- Application admission checks UTF-8 serialized byte length before persistence; database checks the UTF-8 textual JSON representation with `OCTET_LENGTH(CAST(column AS CHAR CHARACTER SET utf8mb4))`, not physical binary storage size. Enforce both ceilings; differences in normalization/escaping can still cause the database to reject a near-limit application value, and that rejection must roll back under P2.8. Do not use `JSON_STORAGE_SIZE` as the byte contract.
- Declared string maxima and array bounds are part of V1. Raising a bound changes the contract and requires a reviewed next version; exceeding a bound returns a typed validation error, never truncation or silent array dropping.

| Proposed enforced constraint name | Contract |
| --- | --- |
| `chk_ring_designs_component_snapshot_json` | SQL-null only for draft; otherwise `JSON_SCHEMA_VALID(inline_accepted_schema, component_snapshot) = 1`. Use explicit null handling/`COALESCE(..., 0)` so missing/unknown data cannot pass as SQL `UNKNOWN`. |
| `chk_ring_designs_component_snapshot_size` | Non-null textual payload at most 65,536 UTF-8 bytes; nullable draft handled explicitly. |
| `chk_audit_logs_before_data_json` | SQL-null is allowed; any non-null value must validate against the accepted inline audit schema. |
| `chk_audit_logs_after_data_json` | SQL-null is allowed; any non-null value must validate against the accepted inline audit schema. |
| `chk_audit_logs_snapshot_size` | Each image and their null-as-zero sum obey the audit byte bounds. |
| `chk_audit_logs_snapshot_binding` | Explicit null-safe row-local rules bind non-null profiles to entity/action, forbid committed after-images for rejected/failed rows, and require both images for successful registered update/activation actions, SQL-null before plus complete after for creation, and matching name/version/profile for an update pair. Closed action mapping is expanded inline in the migration, never a mutable table lookup. |

- Exact pre/post changed key-set equality, source-state matches, numeric BIGINT/decimal range, real dates, depth and complete business invariants require the application checks above. Do not claim `JSON_VALID`, a native JSON column, or schema validation alone enforces them.
- Generate database schemas/check branches from the approved immutable resources, preserve already supported version branches and verify the deployed MySQL engine's functions/enforced checks. A constraint cannot dynamically resolve schemas from a registry table. SQL DDL belongs to versioned migrations; application writes remain through JPA.
- Parsing/schema errors expose a safe field path and typed error; never echo the payload into logs/error responses. Successful snapshots/audit writes commit with their business action under P2.8. Snapshot/audit validation failure must not leave a partially committed business mutation; failure evidence uses the same minimal registered audit contract.

### Version evolution, legacy data and acceptance

1. Treat each `(schema_name, schema_version)` as immutable, including field meanings and profile mapping. Adding/removing/renaming fields or profiles, changing units/null interpretation/types or raising bounds creates the next positive integer version. A pricing-rule change alone is still tracked by `rules_version`.
2. Deploy readers/validators for old **and** new contracts, then a migration expanding accepted inline checks, then new writers. Only emit the new version once every relevant reader (history, export, archive restore and retry worker) supports it. Rolling back a writer does not permit an old reader to misinterpret newly stored data.
3. Preserve stored frozen/audit JSON in its original version. Readers may adapt it in memory into a current view, without overwriting evidence, recalculating amounts or inventing missing fields. Retain old schema resources/readers while any referenced live/archive/restorable record remains under P2.7 policy/hold; age alone cannot justify dropping a decoder.
4. Before constraint rollout, inventory existing non-null values by column and actual shape. Unversioned legacy values require an explicitly reviewed legacy reader/mapping; do not label them V1 simply because parsing succeeds. If provenance/units/required values cannot be demonstrated, isolate them for authorized remediation and block affected new operations; preserve admissible evidence and applicable retention. Do not persist a fake `schema_version = 0` or backfill guessed prices/IDs/defaults. Do not enable new checks while retained rows fail them: the rollout needs either a proven, narrowly validated legacy branch or an approved quarantine/mapping that preserves P2.3 references and P2.7 evidence. An unconditional legacy bypass is forbidden.
5. Unsupported/corrupt versions fail closed for business decisions and render a controlled unavailable-data result for authorized history reads. Export may preserve the original admissible stored JSON representation plus an unsupported-contract marker under scoped access; no generic latest-version parser or silent mutation is permitted. Archive/restore must retain envelope/schema support and must not reintroduce prohibited PII.
6. Implementation acceptance checks both schemas with valid documents, wrong/missing versions, extra nested keys, JSON literal null, numeric IDs/decimal exponents, invalid dates/ranges, duplicate keys, mismatched selection rows/results/profiles, byte/depth/array limits and malformed partial audit pairs. Verify application/database parity on actual MySQL, full business rollback on JSON/audit failure, old/new rolling deployments and restore/legacy readers.
7. Documentation acceptance: all three columns point to this contract; V1 schemas, permitted-data/resource boundaries and immutable compatibility rules are explicit. This edit performs no database migration or backend test. P2.10 remains a separate review; unresolved business vocabulary/formulas are not solved by adding JSON fields.

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
- Voucher/loyalty use appears in Report 3, while Report 1 defers loyalty/marketing. P2.5 proposes conditional promotion usage, limits, branch and discount snapshots; the four-table P2.6 model adds explicit loyalty holds, shared source allocations, conversion/discount snapshots, expiry and direct payment-generation binding; refund-driven point restoration is excluded. Final eligibility/stacking, actual rates/lifetimes and feature-scope approval remain separate prerequisites.

### products

| Column                       | Type          | Constraints / description                                                                                                                       |
| ---------------------------- | ------------- | ----------------------------------------------------------------------------------------------------------------------------------------------- |
| `product_id`                 | BIGINT        | Primary key.                                                                                                                                    |
| `category_id`                | BIGINT        | Required foreign key to `categories`; assumes one category per product.                                                                         |
| `product_name`               | VARCHAR(255)  | Required catalogue name displayed to customers.                                                                                                 |
| `description`                | TEXT          | Nullable product information displayed on the product-detail page.                                                                              |
| `unit_price`                 | DECIMAL(15,2) | Required current selling price; must be non-negative. Exact pricing rules remain `TBD`.                                                         |
| `currency`                   | CHAR(3)       | Required currency code, for example `VND`. Approved currency/default remains `TBD`.                                                             |
| `available_to_sell_quantity` | INT | Required; defaults to `0`; non-negative remaining-unsold sale quantity under P2.8's proposal. Manual edits and one-time accepted sale deductions use product gates; valid unpaid holds reduce availability separately. Not a warehouse ledger. |
| `is_published`               | BOOLEAN       | Required; defaults to `FALSE`. Controls visibility in the public catalogue.                                                                     |
| `created_at`                 | DATETIME      | Required creation timestamp.                                                                                                                    |
| `updated_at`                 | DATETIME      | Required last-update timestamp.                                                                                                                 |

Indexes:

- `(is_published, category_id)` for published catalogue browsing and category filtering.
- `(category_id)` for category membership queries and the foreign key.

Rules:

- Only published products in an active category are shown in the public catalogue; category activation is a proposed visibility rule.
- Product name/description support search; the search implementation is `TBD`. A normal B-tree index is not assumed to support substring search.
- Product availability and the entire cart are revalidated at checkout. Unpublished, invalid or insufficient-quantity products cannot be purchased.
- P2.8's proposed remaining-unsold model derives active unpaid holds from `order_items` of non-expired `pending_payment` orders. Reserve/release and first accepted sale are atomic under product gates; manual quantity edits use the same gates and cannot go below valid holds. Paid lines are not subtracted a second time from an already-decremented quantity.
- Product edits do not rewrite historical order-item snapshots. A product referenced by an order item cannot be physically deleted; unpublish it instead.
- This table does not manage warehouses, lots, serial numbers, procurement or stock-movement ledgers.

### categories

| Column          | Type         | Constraints / description                                                                                                     |
| --------------- | ------------ | ----------------------------------------------------------------------------------------------------------------------------- |
| `category_id`   | BIGINT       | Primary key.                                                                                                                  |
| `category_name` | VARCHAR(100) | Required category label for grouping/filtering catalogue products. Proposed unique value under the chosen database collation. |
| `description`   | TEXT         | Nullable explanation of the category.                                                                                         |
| `is_active`     | BOOLEAN      | Required; defaults to `TRUE`. Proposed flag for enabling the category in public catalogue navigation.                         |
| `created_at`    | DATETIME     | Required creation timestamp.                                                                                                  |
| `updated_at`    | DATETIME     | Required last-update timestamp.                                                                                               |

Indexes:

- Unique `(category_name)` to prevent duplicate labels under the chosen collation.
- `(is_active, category_name)` for active-category navigation.

Rules:

- A category may contain many products. Actual category labels and the need for multiple categories per product remain `TBD`.
- Deactivating a category hides its products under the proposed visibility rule; it does not alter existing order records.
- A category referenced by a product cannot be physically deleted until those products are reassigned.
- No category hierarchy or additional jewellery types are assumed.

### orders

| Column                 | Type          | Constraints / description                                                                                                                                                                                                               |
| ---------------------- | ------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `order_id`             | BIGINT        | Primary key.                                                                                                                                                                                                                            |
| `order_code`           | VARCHAR(64)   | Required unique customer-facing order reference.                                                                                                                                                                                        |
| `checkout_key` | VARCHAR(128) | `NOT NULL`, no default; trusted opaque command key retained across checkout retries, unique `uk_orders_checkout_key`. Reuse requires the same scoped Member/cart/branch/benefit request; see P2.8. |
| `member_user_id`       | BIGINT        | Required foreign key to `users`. Only an authenticated Member can place a retail order.                                                                                                                                                 |
| `location_id`          | BIGINT        | Required foreign key to `workshop_locations`; processing branch fixed at checkout before branch-dependent promotion eligibility. No default or branch inferred from delivery/profile data; see P2.5. |
| `status`               | VARCHAR(30)   | `NOT NULL`, no SQL default; enforced `chk_orders_status` from the P2.2 registry. The approved checkout flow must supply its initial state; checkout policy remains separate. |
| `fulfilment_method`    | VARCHAR(20)   | Nullable, defaults to `NULL` before the Member chooses fulfilment after verified full payment; enforced `chk_orders_fulfilment_method` from the P2.2 registry. |
| `subtotal_amount`      | DECIMAL(15,2) | Required non-negative snapshot of the sum of order-item line amounts.                                                                                                                                                                   |
| `promotion_discount_amount` | DECIMAL(15,2) | `NOT NULL DEFAULT 0`; non-negative frozen promotion money-discount snapshot, at most `subtotal_amount`. P2.5 defines conditional usage linkage; this is not a loyalty discount. |
| `loyalty_points_redeemed` | BIGINT | `NOT NULL DEFAULT 0`; frozen non-negative requested/to-be-spent points of the selected P2.6 generation, not proof of payment. |
| `loyalty_discount_amount` | DECIMAL(15,2) | `NOT NULL DEFAULT 0`; frozen non-negative point-to-money reduction matching the selected P2.6 generation. Zero when loyalty is unused. |
| `total_amount`         | DECIMAL(15,2) | Required non-negative frozen amount due: `subtotal_amount - promotion_discount_amount - loyalty_discount_amount` for the applicable approved discount policy. An unused discount is zero. Joint use requires approved stacking; carrier fees are excluded. No other adjustments are inferred. |
| `currency`             | CHAR(3)       | Required currency snapshot; must match the order items and its payment.                                                                                                                                                                 |
| `hold_expires_at`      | DATETIME      | Nullable. If the Report 2 approach is approved, required for a pending checkout and set to checkout time plus 15 minutes. It does not expire a paid order.                                                                              |
| `paid_at`              | DATETIME      | Nullable until verified full-payment confirmation is accepted; then required.                                                                                                                                                           |
| `picked_up_by_user_id` | BIGINT        | Nullable foreign key to `users`; required when an authorised operational user records customer pickup. This is the recording actor, not the customer.                                                                                   |
| `picked_up_at`         | DATETIME      | Nullable; required when `status` is `picked_up`.                                                                                                                                                                                        |
| `created_at`           | DATETIME      | Required order/checkout creation timestamp under the approved lifecycle.                                                                                                                                                                |
| `updated_at`           | DATETIME      | Required last-update timestamp.                                                                                                                                                                                                         |

Indexes:

- Unique `(order_code)` for order lookup.
- Unique `uk_orders_checkout_key (checkout_key)` for P2.8 complete-checkout idempotency; conflicts require full rollback before loading the committed original in a fresh transaction.
- `(member_user_id, created_at)` for a Member's purchase history.
- Explicit `(location_id, created_at)` for branch lookup and `fk_orders_location_id`, with `ON DELETE RESTRICT` under P2.3.
- Enforced `chk_orders_promotion_discount`: `promotion_discount_amount >= 0 AND promotion_discount_amount <= subtotal_amount`; cross-table usage sums and the applicable total formula are checked by the P2.5 service.
- Enforced `chk_orders_loyalty_snapshot`: `(loyalty_points_redeemed = 0 AND loyalty_discount_amount = 0) OR (loyalty_points_redeemed > 0 AND loyalty_discount_amount > 0)`; required fields reject null. Enforced `chk_orders_discount_total`: `subtotal_amount >= 0 AND promotion_discount_amount + loyalty_discount_amount <= subtotal_amount AND total_amount = subtotal_amount - promotion_discount_amount - loyalty_discount_amount`. Unsupported free-order flows remain rejected by service policy when a gateway payment must be positive.
- `(status, created_at)` for operational order queues.
- `(status, hold_expires_at)` for unpaid-hold expiry processing if that model is approved.

Rules:

- An order contains at least one `order_items` row and is created from the complete validated cart; checkout creates no partial order. P2.8 `T-CHECKOUT` commits all item/amount/resource/discount holds, required audit and durable work together.
- Prices, quantities, branch, currency and amounts are fixed for that checkout. Catalogue/campaign changes do not recalculate an existing order. Under P2.5, the promotion snapshot equals the frozen discounts of its selected reservation generations; released historical generations are not summed again. Failure release preserves this quote but blocks discounted payment until valid matching reservations exist.
- P2.6 point/money snapshots equal the selected loyalty generation's quantity/discount, not a sum of released historical attempts. Hold release leaves them frozen but blocks discounted payment until a valid matching generation exists. Accepted payment preserves these snapshots; this design has no refund-driven point restoration. Promotion plus loyalty is rejected unless a joint stacking/basis policy is approved.
- Only a verified gateway confirmation matching the payable target, full amount and currency can mark a retail order as paid. Browser redirects are not payment evidence.
- Under the proposed Report 2 lifecycle: `pending_payment` -> `paid` or `expired`. Expiry releases the unpaid hold; a paid order has no automatic reservation expiry. Handling a successful callback arriving after hold expiry remains `TBD`.
- After payment: `paid` -> `preparing`, then the pickup branch `ready_for_pickup` -> `picked_up`, or the carrier branch `prepared_for_carrier` -> `handed_to_carrier`. These exact status codes/transitions are proposed, not final requirements.
- Fulfilment choice is made after verified full payment. Carrier fulfilment requires a complete `delivery_infos` record; pickup does not require delivery contact/address.
- Customer pickup requires recording actor and timestamp. Carrier handoff requires the evidence recorded in `delivery_infos`.
- V1 has no in-system cancellation, refund, return or shipment-tracking lifecycle. Order history must be retained according to an approved retention policy (`TBD`).

#### Checkout hold and sale-quantity rules

- A checkout hold is created only for a complete, validated cart and records the held quantities through the pending order's `order_items`; there is no partial-cart hold.
- Under P2.8's proposed remaining-unsold model, availability is `products.available_to_sell_quantity` minus quantities held by non-expired `pending_payment` orders. Accepted paid quantities are already deducted from that product quantity and must not be subtracted again. The physical feature mapping must explicitly adopt/reconcile this interpretation before rollout.
- Creating or extending a hold must lock the affected product rows or use an equivalent atomic conditional update. The operation must fail when any product would become negative; a later read of availability cannot be used as the only oversell protection.
- `hold_expires_at` is required for a pending order under the hold model. A scheduled expiry worker and checkout/payment transactions must be safe to retry concurrently.
- Expiry releases a hold exactly once under P2.8 `T-EXPIRE`. It changes the unpaid state/discount holds atomically, does not increment product quantity (reserve never decremented it), and cannot expire an order already accepted as paid.
- P2.8 `T-PAYMENT` accepts verified success only after the locked, fresh deadline/quote/generation checks. It atomically changes the order to `paid`, decrements each remaining-unsold product quantity once and converts the hold into the committed sale. Receipt time alone is not sufficient, and the same quantity is neither reserved nor deducted again.
- A verified successful callback arriving after expiry cannot silently mark an expired order as paid. The system must place the payment in an exception/reconciliation path, preserve the callback in `payment_transactions` and require an authorized resolution under the approved late-payment policy.
- Catalogue price or availability changes do not rewrite existing order-item snapshots. Product unpublish prevents new checkout but does not cancel an existing paid order.
- If a future inventory ledger is introduced, it must become the authoritative source for reservations, releases, commits and adjustments; `available_to_sell_quantity` must not be treated as both a mutable balance and an independent ledger.

### order_items

| Column                  | Type          | Constraints / description                                                                                                                                     |
| ----------------------- | ------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `order_item_id`         | BIGINT        | Primary key.                                                                                                                                                  |
| `order_id`              | BIGINT        | Required foreign key to `orders`.                                                                                                                             |
| `product_id`            | BIGINT        | Required foreign key to `products` for the catalogue-product retail scope.                                                                                    |
| `product_name_snapshot` | VARCHAR(255)  | Required product-name snapshot at checkout; keeps order history readable after a catalogue rename.                                                            |
| `quantity`              | INT           | Required purchased quantity; must be greater than `0`.                                                                                                        |
| `unit_price`            | DECIMAL(15,2) | Required non-negative selling-price snapshot at checkout, not a live lookup of `products.unit_price`.                                                         |
| `line_amount`           | DECIMAL(15,2) | Required non-negative line total; must equal `quantity * unit_price`. Proposed stored snapshot; a generated column is an alternative physical implementation. |
| `created_at`            | DATETIME      | Required timestamp when the checkout line is created.                                                                                                         |

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

| Column           | Type         | Constraints / description                                                                                                       |
| ---------------- | ------------ | ------------------------------------------------------------------------------------------------------------------------------- |
| `product_img_id` | BIGINT       | Primary key.                                                                                                                    |
| `product_id`     | BIGINT       | Required foreign key to `products`.                                                                                             |
| `img_url`        | TEXT         | Required product-image URL or object-storage path. Stores a media reference, not binary image data.                             |
| `alt_text`       | VARCHAR(255) | Nullable image description for accessibility; required when the image conveys information not already expressed by nearby text. |
| `sort_order`     | INT          | Required non-negative display position; proposed unique position within a product.                                              |
| `created_at`     | DATETIME     | Required image-record creation timestamp.                                                                                       |
| `updated_at`     | DATETIME     | Required last-update timestamp.                                                                                                 |

Indexes:

- Unique `(product_id, sort_order)` for ordered product-image retrieval.

Rules:

- One product may have multiple catalogue images. The lowest `sort_order` supplies the proposed catalogue thumbnail/main image, avoiding a separate main-image flag.
- Authorised catalogue maintainers may add, replace, reorder or remove image references. File validation, publication minimum-image count and storage retention remain `TBD`.
- These are catalogue images; Member reference images remain in `custom_design_requests.img_url`.
- Changing catalogue images does not alter an order's price or quantity snapshots.

### payments

| Column                     | Type          | Constraints / description                                                                                                                                                                                  |
| -------------------------- | ------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `payment_id`               | BIGINT        | Primary key. Proposed model: one row per payment attempt, updated with its verified gateway outcome.                                                                                                       |
| `payment_code`             | VARCHAR(64)   | Required unique internal payment-attempt reference used to correlate provider confirmation with the payable target.                                                                                        |
| `order_id`                 | BIGINT        | Nullable foreign key to `orders`; required for a retail full-payment attempt.                                                                                                                              |
| `loyalty_redemption_id` | BIGINT | Nullable foreign key to `loyalty_redemptions`, default `NULL`; exact P2.6 generation bound at retail-attempt creation and immutable after commit. Required when the frozen retail quote uses loyalty, null otherwise and for workshop deposits. |
| `loyalty_linked_at` | DATETIME | Nullable, default `NULL`; required with the loyalty FK. Trusted pre-initiation binding time committed with attempt creation, not earlier than `created_at`; immutable after commit. |
| `workshop_registration_id` | BIGINT        | Nullable logical foreign key to `workshop_registration`; required for a workshop-deposit attempt. This table name follows this file's inventory; mapping to implemented `workshop_bookings` remains `TBD`. |
| `payment_purpose`          | VARCHAR(30)   | `NOT NULL`, no default; enforced `chk_payments_payment_purpose` from the P2.2 registry. Must match the selected target. |
| `amount`                   | DECIMAL(15,2) | Required amount requested for this attempt; must be greater than `0`. Retail amount must match the order's full amount due; workshop deposit formula remains subject to the approved booking policy.       |
| `currency`                 | CHAR(3)       | Required currency snapshot; must match the payable target.                                                                                                                                                 |
| `provider`                 | VARCHAR(50)   | Required configured Payment Gateway identifier; the authoritative SRS selects VNPay for V1. Physical adapter/event mapping still requires its approved feature contract. |
| `provider_transaction_id`  | VARCHAR(255)  | Nullable until a provider transaction reference is available. Reference format/uniqueness scope remain `TBD` in the gateway contract.                                                                      |
| `status`                   | VARCHAR(20)   | `NOT NULL DEFAULT 'pending'`; enforced `chk_payments_status` from the P2.2 registry, including attempt-only `cancelled`. Describes the payment attempt, not fulfilment. |
| `verified_at`              | DATETIME      | Nullable until an authentic provider outcome has passed server-side verification; required for `succeeded`.                                                                                                |
| `paid_at`                  | DATETIME      | Nullable; required for `succeeded`. Records the confirmed payment timestamp; its provider/local timestamp source remains `TBD`.                                                                            |
| `failure_reason`           | TEXT          | Nullable safe failure explanation/code. Must not contain gateway secrets, card data or raw sensitive callback payloads.                                                                                    |
| `evidence_retired_at` | DATETIME | Nullable, default `NULL`; immutable once set by the guarded P2.7 retention operation. Permanently blocks further application payment effects before event-key purge; no change to the original status. |
| `created_at`               | DATETIME      | Required payment-attempt creation timestamp.                                                                                                                                                               |
| `updated_at`               | DATETIME      | Required last-update timestamp.                                                                                                                                                                            |

Indexes:

- Unique `(payment_code)` for attempt correlation.
- Proposed unique `(provider, provider_transaction_id)` when the provider contract guarantees this uniqueness scope; nullable references allow pending attempts.
- `(order_id, created_at)` for retail payment history.
- `idx_payments_loyalty_redemption (loyalty_redemption_id, payment_id)` supports P2.6 generation-attempt lookup and the named restricted FK; it is non-unique so permitted retries can share a generation.
- `(workshop_registration_id, created_at)` for workshop payment history.
- `(status, created_at)` for pending-attempt processing.
- `(evidence_retired_at, created_at)` and enforced `chk_payments_evidence_retired_at` for P2.7 retirement selection/date shape. Retirement eligibility still requires current evidence/target/hold checks.

Rules:

- Exactly one target is set: retail attempts have `order_id` and no `workshop_registration_id`; workshop attempts have `workshop_registration_id` and no `order_id`. Guest workshop deposits therefore do not require a Member foreign key on this table.
- One target may have multiple payment attempts. Retrying is conditional on the approved gateway and target-state rules; an already paid target must not be charged again by a normal retry.
- Enforced `chk_payments_loyalty_link` requires `(loyalty_redemption_id IS NULL AND loyalty_linked_at IS NULL) OR (loyalty_redemption_id IS NOT NULL AND loyalty_linked_at IS NOT NULL AND payment_purpose = 'retail_full_payment' AND order_id IS NOT NULL AND workshop_registration_id IS NULL AND loyalty_linked_at >= created_at)`. The selected generation's Member/order/currency/quote agreement and required link for a non-zero order loyalty snapshot are service checks.
- Bind the loyalty FK/time with attempt creation before provider initiation; neither a present nor absent committed binding may change later. For a loyalty-bearing attempt, before accepting payment require its exact generation to be reserved/valid and consistent with the frozen order discount. On success, its `loyalty_redemptions.redeemed_payment_id` points back to this attempt. A duplicate accepted callback returns the committed result; an old/released generation cannot consume a replacement. Retail attempts without loyalty and all workshop attempts retain null loyalty FK/time.
- A retail sale requires one accepted verified full payment. Accepting it, recording the paid state and deducting held quantities must be idempotent; repeated callbacks must not repeat those effects.
- The proposed transaction-reference index alone does not define duplicate-event handling. Event identity, signature verification, allowed status transitions, timeout/late-success handling and retry require the approved provider contract. P2.7 defines admissible retained evidence and forbids raw callback storage; it does not replace those verification/transition rules.
- Exactly one payment target is required: `order_id` XOR `workshop_registration_id`. A retail payment must reference only an order and use `retail_full_payment`; a workshop payment must reference only a registration and use `workshop_deposit`. Both-null and both-set records are invalid and must be rejected transactionally.
- A browser redirect, client-supplied success flag or unverifiable callback must not mark payment as `succeeded`.
- Payment data is retained as business history; it is not deleted when catalogue data changes. P2.7 applies the SRS five-year evidence period, scoped access, privacy/expiry and permanent retired-attempt guard. Referenced minimal business facts/keys remain protected under P2.3; this is not permission for indefinite raw-provider/PII retention.
- This proposal does not introduce card-data storage, reconciliation, accounting or in-system refund processing. Custom-manufacturing payment linkage remains `TBD` outside the catalogue-retail/booking targets described here.

### payment_transactions

This table stores correlated, normalized provider callback/recovery evidence separately from the business payment record in `payments`. It supports callback idempotency, externally handled evidence review and payment-attempt history; it does not independently mark an order/booking as paid or add an in-system reconciliation workflow. Unmatched/unverifiable input cannot invent a `payment_id` to satisfy the FK; record only a safe rejection in its authorized owning security/audit path, with no raw callback payload. P2.7 defines the retention/access boundary; event identity and acceptance still require the approved adapter contract.

| Column                    | Type          | Constraints / description                                                                                  |
| ------------------------- | ------------- | ---------------------------------------------------------------------------------------------------------- |
| `payment_transaction_id`  | BIGINT        | Primary key.                                                                                               |
| `payment_id`              | BIGINT        | Required foreign key to `payments`.                                                                        |
| `provider`                | VARCHAR(50)   | Required payment-provider identifier.                                                                      |
| `provider_transaction_id` | VARCHAR(255) | Nullable when not supplied by the authenticated message; preserve the actual provider reference when supplied. Its presence/format must satisfy the owning adapter's accepted-event rules. |
| `provider_event_id` | VARCHAR(255) | Nullable when no native provider event ID exists; preserve actual supplied authenticated ID and native uniqueness. No fabricated event ID; P2.8 adds a trusted receipt-fingerprint gate. |
| `status`                  | VARCHAR(30)   | `NOT NULL`, no default; enforced `chk_payment_transactions_status` from the P2.2 registry. Closed internal callback-result codes, never arbitrary provider text. |
| `amount`                  | DECIMAL(15,2) | Required amount reported by the provider.                                                                  |
| `currency`                | CHAR(3)       | Required provider currency.                                                                                |
| `payload_hash`            | VARCHAR(128)  | Required hash/fingerprint of the verified callback payload; raw credentials and tokens must not be stored. |
| `received_at`             | DATETIME      | Required callback receipt timestamp.                                                                       |
| `processed_at` | DATETIME | Nullable final local processing-decision timestamp; committed by P2.8 `T-PAYMENT` with its result. Receipt alone leaves it null. |
| `business_applied_at` | DATETIME | Nullable, default `NULL`; immutable first local business-application decision time for this receipt, committed with the target/resource/discount effect. Null for pending or completed non-application outcomes. |
| `processing_reason` | VARCHAR(100) | Nullable, default `NULL`; safe non-blank diagnostic for completed non-application (duplicate/rejection/exception). Null when pending or this receipt applied the business effect. No raw payload/PII. |
| `created_at`              | DATETIME      | Required row-creation timestamp.                                                                           |

Indexes and rules:

- Unique `(provider, provider_event_id)` when `provider_event_id` is available; duplicate callbacks must be recorded or recognized without applying the business outcome twice.
- Unique `uk_payment_transactions_fingerprint (provider, payment_id, payload_hash)` for trusted normalized verified receipt deduplication, plus `(processed_at, received_at)` for unfinished receipt recovery. Neither index alone proves a payment was accepted.
- Enforced `chk_payment_transactions_processing` from P2.8 protects pending/completed timestamp/reason shape. Immutable receipt facts remain separate from the single allowed completion metadata transition.
- Index `(payment_id, received_at)` for payment timeline and reconciliation.
- Index `(provider, provider_transaction_id)` for provider lookup; final uniqueness scope remains provider-contract dependent.
- Index `(received_at, payment_transaction_id)` for P2.7 cutoff scans, without replacing payment/event lookup indexes.
- Only a verified callback matching the expected payment target, amount and currency may update `payments` and the related order/booking. Browser redirects are not evidence.
- `payment_transactions` is append-only. Corrections or reprocessing results are new records or processing metadata, not deletion of the original callback.
- Normalized callback evidence is retained for five years from original `received_at`; required event keys/references remain available through active processing. Exact P2.7 retention/hold/retirement guards govern later leaf expiry; no raw payload, signature, card or contact data is stored. Callback readers never infer success from a hash alone.
- P2.8 separates `T-RECEIPT` (durable evidence/work only) from `T-PAYMENT` (one complete local business effect/result). A rolled-back application leaves the committed receipt incomplete and retryable; completed non-application evidence does not turn the target paid. P2.7's retired-attempt guard precedes every attempted application.

### delivery_infos

| Column                  | Type         | Constraints / description                                                                                                         |
| ----------------------- | ------------ | --------------------------------------------------------------------------------------------------------------------------------- |
| `delivery_infor_id`     | BIGINT       | Primary key. Retains the table naming requested in this file.                                                                     |
| `order_id`              | BIGINT       | Required unique foreign key to `orders`; at most one delivery-information record per retail order.                                |
| `recipient_name`        | VARCHAR(255) | Required recipient/contact name for active carrier fulfilment; fixed `[REDACTED]` after the approved 30-day privacy cutoff. |
| `recipient_phone`       | VARCHAR(30)  | Required contact phone for active fulfilment; preserve prefixes/leading zeroes. Fixed `[REDACTED]` after the privacy cutoff. Ordinary phone validation remains `TBD`. |
| `delivery_address`      | TEXT         | Required complete order-specific address for active fulfilment; fixed `[REDACTED]` after the privacy cutoff. No address-book relationship is assumed. |
| `delivery_note`         | TEXT         | Nullable recipient delivery instructions.                                                                                         |
| `carrier_name`          | VARCHAR(100) | Nullable before handoff; required when an authorised operational user records actual carrier handoff.                             |
| `handoff_reference`     | VARCHAR(255) | Nullable before handoff; required handoff receipt/reference when handoff is recorded. It is evidence, not live shipment tracking. |
| `handed_off_by_user_id` | BIGINT       | Nullable foreign key to `users`; required recording operational actor at carrier handoff.                                         |
| `handed_off_at`         | DATETIME     | Nullable; required actual carrier-handoff timestamp.                                                                              |
| `created_at`            | DATETIME     | Required timestamp when delivery details are saved.                                                                               |
| `updated_at`            | DATETIME     | Required last-update timestamp.                                                                                                   |

Indexes:

- Unique `(order_id)` for one delivery-information record per order and direct order lookup.
- `(handed_off_by_user_id, handed_off_at)` for actor/time handoff-evidence queries.

Rules:

- A delivery-information record belongs only to an order with verified full payment and `fulfilment_method = carrier`; a pickup order requires no such record.
- Recipient/contact/address fields must be complete before the order is prepared for carrier handoff.
- Carrier name, handoff reference, recording actor and timestamp are recorded together with the transition to `handed_to_carrier`; incomplete evidence cannot complete that transition.
- Recipient details and safe handoff facts are fixed after handoff for ordinary business edits. The approved privacy exception irreversibly obscures recipient name/phone/address at `handed_off_at + 30 days` and clears `delivery_note`; it does not rewrite the actor/time/order or handoff outcome. Any other business correction procedure remains `TBD`.
- Delivery details are an order-specific snapshot, not a live reference to a Member profile or saved address.
- Carrier fees are paid separately to the carrier and are not added to `orders.total_amount` or recorded as a platform payment.
- V1 responsibility ends at carrier handoff. This table has no carrier API, live tracking, delivered/failed-delivery status, return or shipping-refund workflow.
- The SRS selects recipient/address deletion or irreversible obscuring 30 days after handoff; P2.7 forbids keeping copies in longer-lived audit/payment evidence. To preserve the existing required string columns/FK graph, this proposal uses the fixed literal `[REDACTED]` for the three recipient/contact/address values and null for the optional note, with safe cleanup evidence. This reserved literal is rejected as ordinary fulfilment input. Such marked history is exempt from operational phone/address completeness checks and cannot be reused for fulfilment. Linked order/user facts remain pseudonymous, not an anonymous order. Access before the cutoff stays limited to the authorized own-order/assigned-fulfilment flow; receipt references must not encode contact/address or expose longer-lived PII copies.

## Locations / branches

Design status: Draft proposal. A location represents one physical workshop/retail branch. It is the master record referenced by workshop slots, booking exceptions and promotion applicability.

### workshop_locations

| Column          | Type         | Constraints / description                                                                                                                                                                                                                  |
| --------------- | ------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `location_id`   | BIGINT       | Primary key; technical surrogate identifier used internally by foreign keys and joins. It has no business meaning, should not be re-used after deletion, and is not the code shown to customers or external systems.                       |
| `location_code` | VARCHAR(64)  | Required unique stable business code for the branch, used in administration, reports, URLs/integrations when a human-readable or externally exchanged identifier is needed. It must remain stable when the branch name or address changes. |
| `location_name` | VARCHAR(255) | Required public branch name.                                                                                                                                                                                                               |
| `address_line`  | VARCHAR(500) | Required physical address or address snapshot used for display.                                                                                                                                                                            |
| `city`          | VARCHAR(100) | Required city/province.                                                                                                                                                                                                                    |
| `country_code`  | CHAR(2)      | Required ISO 3166-1 alpha-2 country code; proposed default `VN`.                                                                                                                                                                           |
| `phone_number`  | VARCHAR(30)  | Nullable branch contact phone number.                                                                                                                                                                                                      |
| `timezone`      | VARCHAR(64)  | Required IANA time-zone identifier used to interpret local opening dates/times; proposed default `Asia/Ho_Chi_Minh`.                                                                                                                       |
| `is_active`     | BOOLEAN      | Required; proposed default `TRUE`. Inactive locations cannot receive new bookings or promotion applicability.                                                                                                                              |
| `created_at`    | DATETIME     | Required creation timestamp.                                                                                                                                                                                                               |
| `updated_at`    | DATETIME     | Required last-update timestamp.                                                                                                                                                                                                            |

Indexes:

- Unique `(location_code)` for stable branch lookup.
- `(is_active, city, location_name)` for public branch selection and administration.

Rules:

- A location is a physical branch, not a staff assignment or a dated workshop slot. A location may have many slots, bookings and exceptions.
- Deactivation prevents new operational use but does not delete historical slots, bookings, orders, promotion applicability records or audit history.
- `timezone` is authoritative for local `slot_date`, opening hours and exception evaluation.

### workshop_package_locations

| Column                | Type     | Constraints / description                                                                                     |
| --------------------- | -------- | ------------------------------------------------------------------------------------------------------------- |
| `workshop_package_id` | BIGINT   | Required foreign key to `workshop_packages`.                                                                  |
| `location_id`         | BIGINT   | Required foreign key to `workshop_locations`.                                                                 |
| `is_active`           | BOOLEAN  | Required; defaults to `TRUE`. Controls whether the package can be selected for new bookings at this location. |
| `created_at`          | DATETIME | Required relationship-creation timestamp.                                                                     |
| `updated_at`          | DATETIME | Required last-update timestamp.                                                                               |

Indexes and rules:

- Composite primary key `(workshop_package_id, location_id)` prevents duplicate package/location links.
- Index `(location_id, is_active, workshop_package_id)` supports package selection for a branch.
- A package is eligible at a location only when the package, location and relationship are active, and the selected slot belongs to that location.
- Deactivating the relationship affects new bookings only; existing bookings retain their package and location snapshots.

### material_locations

| Column        | Type     | Constraints / description                                                                                     |
| ------------- | -------- | ------------------------------------------------------------------------------------------------------------- |
| `material_id` | BIGINT   | Required foreign key to `materials`.                                                                          |
| `location_id` | BIGINT   | Required foreign key to `workshop_locations`.                                                                 |
| `is_active`   | BOOLEAN  | Required; defaults to `TRUE`. Controls whether the material can be selected for new designs at this location. |
| `created_at`  | DATETIME | Required relationship-creation timestamp.                                                                     |
| `updated_at`  | DATETIME | Required last-update timestamp.                                                                               |

Indexes and rules:

- Composite primary key `(material_id, location_id)` prevents duplicate material/location links.
- Index `(location_id, is_active, material_id)` supports material selection for a branch.
- A material is usable at a location only when the material, location and relationship are active. Package compatibility must also be satisfied.
- Deactivating the relationship affects new designs/bookings only; frozen designs and historical booking snapshots remain readable.

## Promotions

Design status: Future/conditional draft. [Report 3 - Use Cases](../documents/docs/report-3-software-requirement-specification/sections/03-i-overall-requirements/04-user-requirements/02-use-cases.md) lists campaigns, voucher codes and order application; [Report 1 - Limitations](../documents/docs/report-1-project-introduction/sections/07-v-project-scope-limitations/02-limitations-exclusions.md) defers loyalty/marketing. P2.5 supplies proposed usage/reservation, limit and discount-snapshot storage. Eligibility, stacking and scope approval remain prerequisites before checkout is enabled. Branch applicability remains explicit in `promotion_locations`.

### promotions

| Column                    | Type          | Constraints / description                                                                                                                                                               |
| ------------------------- | ------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `promotion_id`            | BIGINT        | Primary key.                                                                                                                                                                            |
| `promotion_name`          | VARCHAR(255)  | Required campaign/promotion name.                                                                                                                                                       |
| `description`             | TEXT          | Nullable customer-facing explanation and conditions.                                                                                                                                    |
| `voucher_code`            | VARCHAR(64)   | Nullable unique voucher code. Proposed simplification: at most one code per promotion; a campaign with multiple voucher codes requires a separate voucher table.                        |
| `discount_type`           | VARCHAR(20)   | `NOT NULL`, no default; enforced `chk_promotions_discount_type` from the P2.2 registry. |
| `discount_value`          | DECIMAL(15,2) | Required positive value; percentage must be at most `100`, fixed amount is expressed in `currency`.                                                                                     |
| `currency`                | CHAR(3)       | Required for a fixed amount or monetary threshold/cap; may be null for a percentage with no monetary conditions. Must match an eligible order whenever monetary conditions are present. |
| `minimum_order_amount`    | DECIMAL(15,2) | Nullable non-negative minimum eligible merchandise amount; the precise eligibility basis remains `TBD`.                                                                                 |
| `maximum_discount_amount` | DECIMAL(15,2) | Nullable positive monetary cap for a percentage promotion; not used for a fixed discount under this proposal.                                                                           |
| `total_usage_limit`       | BIGINT        | Nullable positive campaign-wide number of uses, across all branches. Null means explicitly approved unlimited use; activation must validate the intended policy. |
| `per_user_usage_limit`    | BIGINT        | Nullable positive number of uses per Member across this campaign's branches. Null means explicitly approved unlimited use; not an implicit exemption from eligibility. |
| `starts_at`               | DATETIME      | Required promotion-validity start timestamp.                                                                                                                                            |
| `ends_at`                 | DATETIME      | Required end timestamp; must be later than `starts_at`. Proposed validity interval includes the start and excludes the end.                                                             |
| `is_active`               | BOOLEAN       | Required; proposed default `FALSE`. Manual activation does not override the validity window.                                                                                            |
| `created_by_user_id`      | BIGINT        | Required foreign key to `users`; authorised campaign-creation actor. Exact managing role remains `TBD`.                                                                                 |
| `created_at`              | DATETIME      | Required creation timestamp.                                                                                                                                                            |
| `updated_at`              | DATETIME      | Required last-update timestamp.                                                                                                                                                         |

Indexes:

- Unique `(voucher_code)` for voucher lookup; multiple null codes are permitted.
- `(is_active, starts_at)` for active/scheduled promotion selection, with end-time filtering.
- `(created_by_user_id, created_at)` for campaign administration.

Rules:

- A voucher is valid only when its promotion is active, within its approved validity window and eligible for the order. Code normalization/case sensitivity remain `TBD` before enforcing uniqueness.
- Discount cannot exceed the eligible order amount; percentage/fixed-amount rounding and tax interaction remain `TBD`.
- A null voucher code does not automatically authorize applying the promotion. Automatic versus code-based application must be specified.
- This table defines the campaign, not proof of use. P2.5 supplies `promotion_redemptions`, payment-attempt links and order discount snapshots; derive usage from those records rather than an independent editable campaign counter.
- Enforced `chk_promotions_usage_limits`: `(total_usage_limit IS NULL OR total_usage_limit > 0) AND (per_user_usage_limit IS NULL OR per_user_usage_limit > 0)`. A zero cap is invalid; deactivate the campaign to stop new use. Limit changes use P2.5's quota lock and cannot fall below held/redeemed usage.
- Later campaign edits/deactivation must not recalculate a completed order's historical amounts.
- Stacking with other vouchers/loyalty, product/category restrictions and customer targeting remain unresolved. P2.5 defines total/per-Member limit storage and enforcement; actual finite caps or explicitly unlimited policy require campaign approval.

### promotion_locations

| Column               | Type     | Constraints / description                                                                                                                                                                                                                                                                                                                                                                                                    |
| -------------------- | -------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `promotion_id`       | BIGINT   | Required foreign key to `promotions`.                                                                                                                                                                                                                                                                                                                                                                                        |
| `location_id`        | BIGINT   | Required foreign key to `workshop_locations`.                                                                                                                                                                                                                                                                                                                                                                                |
| `is_active`          | BOOLEAN  | Required; defaults to `TRUE`. Set to `FALSE` to disable future applicability while retaining the assignment and creator history. |
| `created_at`         | DATETIME | Required timestamp when the promotion is enabled for the location.                                                                                                                                                                                                                                                                                                                                                           |
| `updated_at`         | DATETIME | Required last-update timestamp, including assignment activation/deactivation. |
| `created_by_user_id` | BIGINT   | Required foreign key to `users`; identifies the authenticated user who created this promotion-to-branch assignment. It provides accountability for who enabled the promotion at the branch, supports audit/history and investigation of accidental or unauthorised scope changes, and must refer to an authorised operational user. It does not determine promotion eligibility and must not be used as the promotion owner. |

Primary key: `(promotion_id, location_id)`.

Indexes:

- `(location_id, is_active, promotion_id)` for finding enabled assignments at one branch.
- `(promotion_id, location_id)` is covered by the composite primary key for listing branches assigned to a promotion.

Rules:

- An active row means that the promotion is enabled at that location; no row or `is_active = FALSE` means that it does not apply there. This supports branch-specific applicability without deleting the assignment history or duplicating the promotion.
- A promotion is eligible for an order only when its promotion row is active and within its validity window, the order's branch/location has a matching active `promotion_locations` row, and all other promotion rules pass.
- The promotion, location and assignment must be retained for history. Disable applicability with `is_active = FALSE` instead of deleting the link; this affects new eligibility only and must not recalculate completed orders. All three FKs use `ON DELETE RESTRICT` under the P2.3 registry.
- Activation/deactivation must record the actor and old/new flag in `audit_logs` and update `updated_at`. Re-enabling reuses the same composite-key row without replacing its original `created_by_user_id` or `created_at`.
- An inactive location cannot be newly linked to a promotion. Existing links remain for history and must be ignored during eligibility checks while the location is inactive.
- If a future requirement needs a global promotion, it must be modeled explicitly (for example, with a separate scope flag); absence of rows must not mean “all locations”.

## Promotion usage and redemption — P2.5

Design status: Proposed extension for review, not an implemented/enabled checkout feature. Scope is promotion usage for Member retail orders: who used a promotion, which order/branch/payment attempt, reservation/redemption timestamps, frozen discount amounts, usage limits and failure release. Loyalty redemption/expiry remain P2.6; refunds are excluded; stacking and final pricing/eligibility rules remain P2.10. The existing Report 1/Report 2 scope disagreement is preserved; this schema proposal does not ratify feature scope.

### Design choice and scope

| Approach | Benefit | Gap / trade-off | Proposal |
| --- | --- | --- | --- |
| Campaign counters alone | Simple total-use reporting. | Cannot identify the order/user, preserve actual discounts, distinguish pending attempts or recover duplicate/failure events safely. | Not selected. |
| Insert usage only after payment succeeds | Keeps a paid-only history. | Concurrent checkouts can both claim the last available voucher use; quotas need a reservation mechanism. | Not selected. |
| Reservation/redemption row with immutable price snapshots and payment-attempt links | Reserves quota before payment and preserves releases/retries without double-counting. | Requires transactional quota checks, state guards and callback/expiry coordination. | Select `promotion_redemptions` plus `payment_promotion_redemptions`. |

- Retain one voucher code per `promotions` row as already proposed. Limits apply per promotion across its branches, not per `promotion_locations` row. Multiple-code campaigns, Guest use, workshop/custom-order discounts and per-branch quotas require separate design.
- `orders.location_id` below records the processing branch before branch-dependent eligibility is evaluated; it is not the delivery address. Selection/assignment of that branch remains an owning checkout requirement, not a guessed UI flow. Missing/ambiguous branch mapping blocks promotion use.
- `orders.promotion_discount_amount` is the immutable checkout snapshot of promotion money reduction. For the promotion-only extension, `total_amount = subtotal_amount - promotion_discount_amount`; with no approved discount, the snapshots are zero and the existing no-discount behavior remains. P2.6 explicitly specifies a conditional loyalty snapshot; using both requires an approved joint stacking/basis policy, never an implicit extra reduction.
- Schema support for several distinct promotion rows per order does not authorize stacking. The service rejects any combination without an approved policy. Eligibility basis, code normalization, rounding, stacking order, caps and free-order treatment must be approved before monetary application; do not infer them from schema defaults. Current payment attempts require a positive payable amount.

### promotion_redemptions

One row represents one reservation generation for one order/promotion, followed by exactly one terminal outcome. It records a Member beneficiary even if a separately authorized actor initiates the action; audit records identify that actor. No raw email/phone or provider credential is copied here.

| Column | Type | Constraints / description |
| --- | --- | --- |
| `promotion_redemption_id` | BIGINT | Primary key. |
| `reservation_key` | VARCHAR(128) | `NOT NULL`; unique checkout reservation/idempotency key, supplied/generated by the owning service. Reusing it requires the same order/promotion/user/quote. |
| `promotion_id` | BIGINT | Required foreign key to `promotions`. |
| `order_id` | BIGINT | Required foreign key to `orders`. |
| `member_user_id` | BIGINT | Required foreign key to `users`; must equal the order's Member beneficiary. |
| `location_id` | BIGINT | Required foreign key to `workshop_locations`; must equal the order's processing branch and match an eligible active promotion-location assignment when reserved. |
| `status` | VARCHAR(20) | `NOT NULL DEFAULT 'reserved'`; closed values `reserved`, `redeemed`, `released`, enforced by the extended P2.2 registry. |
| `active_order_id` | BIGINT generated | Nullable `STORED` generated value: `CASE WHEN status IN ('reserved', 'redeemed') THEN order_id ELSE NULL END`; not an additional FK or an editable order identity. |
| `promotion_name_snapshot` | VARCHAR(255) | Required name for readable historical usage after campaign changes. |
| `voucher_code_snapshot` | VARCHAR(64) | Nullable exact accepted voucher code; null only for separately approved automatic promotion application, not implicit eligibility. |
| `discount_type_snapshot` | VARCHAR(20) | `NOT NULL`, no default; closed `percentage`, `fixed_amount` values under the extended P2.2 registry. |
| `discount_value_snapshot` | DECIMAL(15,2) | Required positive configured value; a percentage is at most 100. |
| `eligible_amount_snapshot` | DECIMAL(15,2) | Required positive amount to which the approved policy applies this promotion. It must be within the permitted order basis. |
| `minimum_order_amount_snapshot` | DECIMAL(15,2) | Nullable non-negative campaign threshold at reservation. |
| `maximum_discount_amount_snapshot` | DECIMAL(15,2) | Nullable positive monetary cap at reservation. |
| `discount_amount` | DECIMAL(15,2) | Required positive frozen money reduction, at most `eligible_amount_snapshot`. In `reserved` it is the promised checkout discount; only `redeemed` records an actually accepted paid-order discount. `released` records historical unused intent. |
| `currency` | CHAR(3) | Required currency snapshot matching order, quote and relevant payment attempt. |
| `total_usage_limit_snapshot` | BIGINT | Nullable positive campaign-wide cap observed at reservation; null records explicitly approved unlimited use. Live campaign limits still gate new reservations. |
| `per_user_usage_limit_snapshot` | BIGINT | Nullable positive per-Member cap observed at reservation; null records explicitly approved unlimited use. |
| `eligibility_policy_version` | VARCHAR(64) | Required identifier of the approved calculation/eligibility policy used; no unapproved policy is fabricated by this field. |
| `reserved_at` | DATETIME | Required reservation timestamp. |
| `expires_at` | DATETIME | Required approved checkout-reservation deadline, later than `reserved_at` and no later than the order's hold deadline. No independent hold duration is selected here. |
| `redeemed_at` | DATETIME | Nullable until accepted verified payment; required only in `redeemed`. |
| `redeemed_payment_id` | BIGINT | Nullable foreign key to `payments`; required only in `redeemed`, identifying the accepted attempt linked to this exact reservation generation. |
| `released_at` | DATETIME | Nullable until terminal release; required only in `released`. |
| `release_reason` | VARCHAR(100) | Nullable safe diagnostic explanation/code; non-blank only in `released`. Not a state or permission code; contains no sensitive callback payload. |
| `updated_at` | DATETIME | Required timestamp of the last guarded lifecycle metadata update. |

Indexes and physical invariants:

- Unique `uk_promotion_redemptions_reservation_key (reservation_key)` prevents duplicate checkout requests creating additional quota claims. Compare the immutable request/quote on a repeated key; conflicting reuse is rejected.
- Unique `uk_promotion_redemptions_active_order (promotion_id, active_order_id)` permits at most one reserved or redeemed generation per order/promotion, while retaining multiple released generations. Do not use `UNIQUE(order_id, promotion_id, status)`, which would incorrectly limit release history. Generated values and nullable unique-key semantics must be verified on the selected MySQL release: [generated columns](https://dev.mysql.com/doc/refman/8.4/en/create-table-generated-columns.html), [unique indexes](https://dev.mysql.com/doc/refman/8.4/en/create-table.html).
- Index `(promotion_id, status, promotion_redemption_id)` for current quota lookup; `(promotion_id, member_user_id, status, promotion_redemption_id)` for Member quota; `(order_id, reserved_at)` and `(member_user_id, reserved_at)` for history; `(status, expires_at)` for release work; `(location_id)` and `(redeemed_payment_id)` support the remaining FKs.
- All five FKs use named `ON DELETE RESTRICT` constraints in the extended P2.3 registry. Keep released/redemption rows, snapshots and audit; no routine delete, nullable payment-FK nulling or ORM cascade is allowed.
- Named enforced `chk_promotion_redemptions_amounts` requires `discount_value_snapshot > 0`, `eligible_amount_snapshot > 0`, `discount_amount > 0`, `discount_amount <= eligible_amount_snapshot`, and `(discount_type_snapshot <> 'percentage' OR discount_value_snapshot <= 100)`.
- Enforced `chk_promotion_redemptions_optional_values`: `(minimum_order_amount_snapshot IS NULL OR minimum_order_amount_snapshot >= 0) AND (maximum_discount_amount_snapshot IS NULL OR maximum_discount_amount_snapshot > 0) AND (total_usage_limit_snapshot IS NULL OR total_usage_limit_snapshot > 0) AND (per_user_usage_limit_snapshot IS NULL OR per_user_usage_limit_snapshot > 0)`.
- Named enforced `chk_promotion_redemptions_lifecycle` requires `expires_at > reserved_at` and exactly the following metadata shape: reserved has null redeemed/released timestamps, payment and reason; redeemed has non-null payment and `redeemed_at >= reserved_at`, with null release timestamp/reason; released has `released_at >= reserved_at`, a non-blank reason, and null redeemed timestamp/payment. Express required non-null tests explicitly so SQL `UNKNOWN` cannot admit an incomplete terminal row. A CHECK validates shape, not previous-state transitions or cross-table payment validity.
- After insertion, beneficiary, order, promotion/branch, policy/price/limit snapshots, reservation key and deadline are immutable. State, terminal evidence and `updated_at` change only through the lifecycle service; retained current rows are supplemented by audit transitions, not treated as a complete event log themselves.

### payment_promotion_redemptions

Append-only links freeze the reservation generation presented to each payment attempt. Without these links, a late callback from an old released generation could consume a newer reservation on the same order incorrectly.

| Column | Type | Constraints / description |
| --- | --- | --- |
| `payment_id` | BIGINT | Required foreign key to `payments`. |
| `promotion_redemption_id` | BIGINT | Required foreign key to `promotion_redemptions`. |
| `created_at` | DATETIME | Required timestamp when the payment attempt is prepared with this reservation. |

- Composite primary key `(payment_id, promotion_redemption_id)` prevents duplicate links. Index `(promotion_redemption_id, payment_id)` supports attempt history and the reverse FK.
- Both FKs use named `ON DELETE RESTRICT` constraints. A payment may link to several distinct approved promotions, and a reserved generation may link to multiple permitted retry attempts. Links are fixed before provider initiation and never replaced/deleted to point an old attempt at a new generation.
- The service verifies that each link belongs to the payment's retail `order_id`, matches currency and the frozen discount quote, and uses a reservation valid for that attempt. Workshop payments cannot create these links. One attempt cannot link to multiple generations of the same promotion; linkage coverage and discount totals are checked before initiation and again at payment acceptance.

### Limits, lifecycle and payment failure

```text
reserved -> redeemed
reserved -> released
```

- Limits count **uses/orders**, not discount money. Campaign-wide count is all `reserved` plus `redeemed` rows for the promotion; Member count uses the same statuses filtered by `member_user_id`. Released rows do not consume quota. One promotion's repeated attempts share one held generation; they are not additional uses.
- Do not exclude an overdue `reserved` row from quota solely by comparing its deadline with the current clock. Release it through the guarded expiry operation first; until released it still counts conservatively. Do not maintain independent editable `used_count` totals that can diverge from these records.
- New reservations must check both live limits under a `PESSIMISTIC_WRITE` lock on the promotion row, using fresh current reads for quota records. All reserve/redeem/release and limit-edit writers use the same gate. Reject a new reservation when adding one use would exceed either finite cap. Campaign limit changes must not reduce a finite cap below held plus redeemed usage, globally or for any affected Member; null is an explicit approved unlimited policy, not missing validation.
- Checkout validates active Member entitlement, self-benefit restrictions, branch/applicability, validity window, limits and approved discount policy; atomically creates order/item snapshots, quota reservations and the order's promotion discount snapshot. Any failure rolls back the whole checkout. Campaign edits/deactivation after a valid hold do not rewrite its quote or consume quota again; a permitted payment consumes that fixed reservation while still valid. New/replacement reservations revalidate current campaign rules.
- Provider initiation is outside the database transaction. A conclusively failed initiation or verified failed/cancelled attempt does not itself redeem a voucher. Under the order/reservation locks, release only if no accepted successful payment and no other nonterminal/in-flight linked attempt can still legitimately consume the reservation. A browser redirect, network timeout or ambiguous provider result is not conclusive failure; retain the hold until resolution/deadline rather than releasing quota that another valid attempt may use.
- Release sets `status = released`, `released_at`, reason and audit in one committed local operation. It frees exactly one quota use without deleting the row or mutating the order's frozen discount/amount. Failure of the transaction leaves the original hold, not a half-released counter. An already released row is an idempotent no-op; an already redeemed row cannot be released by a failure/expiry event.
- A released generation is terminal. A permitted retry either reuses the still-reserved generation or creates a **new** reservation key/row and a **new** payment attempt linked to it. Reacquisition on the same unpaid order requires renewed eligibility/quota and exactly the same frozen discount quote/total; a different quote requires a new checkout/order. Never reopen or relink an old generation/attempt. An order with a frozen nonzero promotion discount but no valid reservation cannot initiate or accept a normal discounted payment.
- Accept verified full payment only when the exact linked reservation generations remain reserved, valid and consistent with the order/payment quote. Atomically mark them redeemed with the accepted payment ID/time together with the successful business payment/order/hold effect; duplicate events do not repeat quota consumption. Repeat callbacks for the same accepted attempt/generation are no-ops; later failures never undo redemption.
- Successful evidence after release/expiry or on an old generation goes to the existing payment-exception path. Preserve provider evidence but do not consume a replacement reservation, silently revive the old discount, mark the order paid or trigger fulfilment. Manual late-payment policy remains P2.10; in-system refund/reversal of redeemed promotions is not introduced here.
- Coordinate payment acceptance, failure, retry preparation and expiry with P2.8's full lock order: any authorization prefix, order, resource gates, promotions in ascending order, involved Member/policy and dependent reservation/attempt rows. This preserves the order-before-promotion protocol. All writers participate and use current data after locks; do not decide from stale snapshots or `SKIP LOCKED`.

### Compatibility and acceptance criteria

- Existing orders do not acquire invented historical voucher usage. Backfill zero promotion discount only after validating the existing no-discount basis; never infer the branch from address/profile or recreate discounts from today's campaign configuration. Resolve processing-branch mapping before enforcing its new required FK. Applied migrations stay immutable.
- Finite limits, explicitly unlimited policy, promotion eligibility/rounding and branch assignment must be approved before campaign activation/use. Campaign numeric values and final stacking decisions are not invented by this proposal. Keep this future/conditional design distinct from an implemented feature.
- Verify beneficiary/order/branch consistency, real redeemed amount/time/payment, immutable snapshots after campaign edits, exact reservation-key replay, duplicate links and uniqueness across reserved/redeemed/released generations. New status/type columns use P2.2 code collation/validation; all new FKs use P2.3 retention rules.
- Two independent MySQL transactions must contend for the last campaign and last per-Member use: exactly one new reservation succeeds. Test payment-success versus release/expiry races, duplicate/out-of-order callbacks, one failed attempt while another is pending, and failure followed by a fresh reservation; an old successful callback must not consume that replacement. Test audit/checkout rollback and an attempted cap reduction below held/redeemed usage.
- Documentation acceptance: all requested usage facts, caps and failure release have defined storage/guards; existing total-amount and missing-usage statements point to this conditional extension. This edit does not run migrations, backend tests or claim database enforcement, and does not complete P2.6–P2.10.

## Workshop booking, slots, packages and exceptions

Design status: Draft proposal. This section uses the logical names `workshop_registration` and `workshop_exceptions`. The existing backend uses `workshop_bookings` and `booking_email_confirmations`, not this proposed physical schema.

Sources:

- [Report 3 - Workshop Booking Workflow](../documents/docs/report-3-software-requirement-specification/assets/diagrams/workflows/10-workshop-booking-swimlane.puml): configured capacity, Guest/Member booking, package invoice, deposit confirmation and QR notification.
- [Report 3 - Use Cases](../documents/docs/report-3-software-requirement-specification/sections/03-i-overall-requirements/04-user-requirements/02-use-cases.md): slot selection before design, branch/material filtering, group booking, check-in, packages and holiday/off-day exceptions. Group booking is additionally described in [Report 1 - Major Features](../documents/docs/report-1-project-introduction/sections/07-v-project-scope-limitations/01-major-features.md).
- [Report 1 - Proposed Solution](../documents/docs/report-1-project-introduction/sections/06-iv-proposed-solution/00-overview.md): three operating sessions, package deposits, additional participants and continuation work.
- [Member Authentication Specification](features/001-member-authentication/spec.md): Guest contact-email confirmation before payment, a hashed one-time token and 15-minute expiry.
- [Report 3 - Continuation Workflow](../documents/docs/report-3-software-requirement-specification/assets/diagrams/details/26-continuation-custody-detail.puml): Staff-created continuation booking and separate custody evidence.

Design assumptions and unresolved points:

- A `slots` row represents one dated session at one location, not a recurring timetable template. A group registration consumes its `participant_count` seats in one slot. These are proposed physical modeling choices.
- `workshop_locations` is the location/branch master defined above. Branch/material/package compatibility still requires separate relationship specifications.
- Booking workflows specify a 50% package deposit, while Report 3 also lists material-based deposit configuration. The proposed package deposit percentage defaults to 50 for that workflow; precedence of material/package rules and group-pricing basis remain `TBD`.
- Capacity values, temporary seat-hold timing, payment deadline after email confirmation, exception precedence and safe rescheduling policies remain `TBD`. Email-token expiry is not a retail-stock or payment-attempt expiry.
- Package invoices, adjustment consent, settlement, policy acceptance, email tokens and custody evidence remain separate records. These four tables alone do not represent the complete workshop workflow.

### workshop_registration

| Column                        | Type          | Constraints / description                                                                                                                                                                                    |
| ----------------------------- | ------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `workshop_registration_id`    | BIGINT        | Primary key. Logical booking identity referenced by `payments.workshop_registration_id`.                                                                                                                     |
| `booking_code`                | VARCHAR(64)   | Required unique booking/lookup reference. Existing persistence creates a code with the booking; its public exposure/QR issuance follows the approved confirmation flow.                                      |
| `member_user_id`              | BIGINT        | Nullable foreign key to `users`: null for a Guest booking, set for a Member booking or an explicitly imported eligible Guest booking.                                                                        |
| `slot_id`                     | BIGINT        | Required foreign key to `slots`; records the selected dated location/session.                                                                                                                                |
| `workshop_package_id`         | BIGINT        | Required foreign key to `workshop_packages`.                                                                                                                                                                 |
| `ring_design_id`              | BIGINT        | Nullable foreign key to `ring_designs` for a permitted catalogue/configured-design selection. In-person consultation may leave it null; it does not link a custom image request.                             |
| `design_path`                 | VARCHAR(30)   | Nullable proposed path: `catalogue_model`, `configured_design`, `in_person`. Exact required paths and timing remain `TBD`; image-request linkage is excluded by the existing `custom_design_requests` rules. |
| `contact_name`                | VARCHAR(255)  | Required booking contact name under this proposal.                                                                                                                                                           |
| `contact_email`               | VARCHAR(255)  | Required booking-notification and contact-email confirmation address; independent of a Member's mutable profile email.                                                                                       |
| `canonical_email`             | VARCHAR(255)  | Required normalized contact-email lookup value, following the approved email policy. Not an identity or automatic-link key.                                                                                  |
| `contact_phone`               | VARCHAR(30)   | Required booking contact phone under this proposal; exact validation remains `TBD`.                                                                                                                          |
| `participant_count`           | INT           | Required positive number of booked participants; used for slot-capacity consumption.                                                                                                                         |
| `package_name_snapshot`       | VARCHAR(255)  | Required package-name snapshot at booking/invoice creation.                                                                                                                                                  |
| `package_price_snapshot`      | DECIMAL(15,2) | Required non-negative listed package-price snapshot. Whether group pricing is per person or per group remains `TBD`.                                                                                         |
| `total_amount`                | DECIMAL(15,2) | Required non-negative initial booking-invoice amount derived by the approved package/group rules. Subsequent operational adjustments belong to billing records.                                              |
| `deposit_percentage_snapshot` | DECIMAL(5,2)  | Required applicable deposit percentage snapshot, greater than `0` and at most `100`; baseline workflow uses `50`.                                                                                            |
| `deposit_amount`              | DECIMAL(15,2) | Required non-negative deposit due snapshot; calculated from the approved invoice basis, percentage and rounding rules.                                                                                       |
| `currency`                    | CHAR(3)       | Required invoice/payment currency snapshot.                                                                                                                                                                  |
| `confirmation_state`          | VARCHAR(30)   | `NOT NULL`, no default; enforced `chk_workshop_registration_confirmation_state` from the P2.2 registry. Guest creation supplies `EMAIL_CONFIRMATION_PENDING`; Member confirmation policy and mapping to existing codes remain `TBD`. |
| `status`                      | VARCHAR(30)   | `NOT NULL`, no default; enforced `chk_workshop_registration_status` from the P2.2 registry. Separate from email confirmation; exact mapping to existing persistence remains `TBD`. |
| `hold_expires_at` | DATETIME | Nullable, default `NULL`; required for a pending temporary group/resource hold under P2.8. Persist its original deadline independently of the email-token deadline; no implicit extension on confirmation/retry. |
| `confirmed_at`                | DATETIME      | Nullable until the booking's required confirmation gates, including verified deposit where applicable, are satisfied.                                                                                        |
| `checked_in_by_user_id`       | BIGINT        | Nullable foreign key to `users`; authorised Staff actor for recorded group check-in.                                                                                                                         |
| `checked_in_at`               | DATETIME      | Nullable; required when check-in is recorded.                                                                                                                                                                |
| `actual_participant_count`    | INT           | Nullable until check-in; non-negative actual participating count, independent of the booked count.                                                                                                           |
| `parent_registration_id`      | BIGINT        | Nullable self-referencing foreign key for a Staff-created continuation booking; must not reference itself or create a cycle.                                                                                 |
| `created_by_user_id`          | BIGINT        | Nullable foreign key to `users`; records the authenticated creation actor where applicable and is required for a Staff-created continuation. Guest creation does not require a user row.                     |
| `created_at`                  | DATETIME      | Required booking-creation timestamp.                                                                                                                                                                         |
| `updated_at`                  | DATETIME      | Required last-update timestamp.                                                                                                                                                                              |

Indexes:

- Unique `(booking_code)` for lookup/check-in correlation.
- `(member_user_id, created_at)` for Member booking history.
- `(slot_id, status)` for capacity and operational attendance queries.
- `(status, hold_expires_at)` for P2.8 temporary group-hold candidate discovery; the scheduled scan does not itself authorize expiry.
- Enforced `chk_workshop_registration_hold_deadline`: `hold_expires_at IS NULL OR hold_expires_at > created_at`; `created_at` is required `NOT NULL`. A pending temporary resource hold additionally requires the deadline under the owning service/state mapping.
- `(canonical_email, member_user_id)` for permitted Guest-booking import selection.
- `(parent_registration_id)` for linked continuation history.
- `(confirmation_state, created_at)` for confirmation-state queries; actual token-expiry scanning uses the separate confirmation table's expiry field.

Rules:

- Guests and Members may book; Member linkage is optional and never created merely because a profile and booking email have matching text.
- Select a slot before the booking design path. Validate the selected package/design against location/material capabilities and approved constraints before proceeding.
- Capacity validation and reservation/release must be transactional for the entire group. Do not calculate availability by counting booking rows instead of participants.
- P2.8 `T-BOOK-HOLD`, `T-PAYMENT` and `T-EXPIRE` use the same slot gate and commit the whole group, required local confirmation/invoice state, audit/work and resource effect together. Confirmation converts the existing hold into committed seats without reserving a second group; expiry never releases paid/committed seats. Physical mapping must supply/reconcile the new hold deadline before enabling this protocol.
- A Guest's pending email confirmation refuses payment/settlement and committed capacity. Its one-time token hash/expiry stays in `booking_email_confirmations` (or its approved logical equivalent), never in this table as plaintext.
- Consuming a valid email token confirms only the email gate; it does not by itself mark the booking deposit paid. Verified gateway deposit confirmation is required for normal booking confirmation and QR-ticket issuance.
- Overdue temporary Guest bookings expire idempotently and release temporary capacity/invoice state under the authentication feature; full integration and state naming still require the booking plan.
- Package and contact snapshots are not rewritten by later catalogue/profile edits. Confirmed bookings are not silently moved or repriced when a slot/package/exception changes.
- Group-level check-in records actor, time and actual count. Per-person attendance, if required, needs separate participant records; it is not represented by one group timestamp.
- Unbooked additional participants and any fee waiver/change are processed through billing/consent/approval records, not by silently changing the original package/deposit snapshot.
- Only Staff create a capacity-checked continuation on customer request. Continuation fee/deposit rules remain `TBD`; parent linkage does not automatically reuse or charge the original deposit. Custody intake/release evidence is outside this table.

### slots

| Column        | Type     | Constraints / description                                                                                                   |
| ------------- | -------- | --------------------------------------------------------------------------------------------------------------------------- |
| `slot_id`     | BIGINT   | Primary key.                                                                                                                |
| `location_id` | BIGINT   | Required foreign key to `workshop_locations`.                                                                               |
| `slot_date`   | DATE     | Required local operating date at the location.                                                                              |
| `start_time`  | TIME     | Required local session start time.                                                                                          |
| `end_time`    | TIME     | Required local session end time; later than `start_time` for the documented same-day sessions.                              |
| `capacity`    | INT      | Nullable until configured; when set, must be non-negative. Null means configuration required; zero means no bookable seats. |
| `is_open`     | BOOLEAN  | Required; proposed default `FALSE` until the dated session is intentionally opened for booking.                             |
| `created_at`  | DATETIME | Required session-creation timestamp.                                                                                        |
| `updated_at`  | DATETIME | Required last-update timestamp.                                                                                             |

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

| Column                | Type          | Constraints / description                                                                                                                                          |
| --------------------- | ------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `workshop_package_id` | BIGINT        | Primary key.                                                                                                                                                       |
| `package_code`        | VARCHAR(64)   | Required unique stable package reference.                                                                                                                          |
| `package_name`        | VARCHAR(255)  | Required public package name.                                                                                                                                      |
| `description`         | TEXT          | Nullable package content and experience description.                                                                                                               |
| `conditions`          | TEXT          | Nullable participation/package conditions; required conditions must be approved before publication.                                                                |
| `duration_minutes`    | INT           | Nullable positive advertised duration; must fit the eligible session under the approved package/session policy.                                                    |
| `price`               | DECIMAL(15,2) | Required non-negative current listed package price; pricing unit/group rules remain `TBD`.                                                                         |
| `currency`            | CHAR(3)       | Required package currency code.                                                                                                                                    |
| `deposit_percentage`  | DECIMAL(5,2)  | Required proposed package deposit percentage; baseline default `50`, greater than `0` and at most `100`. Precedence against material-based settings remains `TBD`. |
| `img_url`             | TEXT          | Nullable package-display image URL or storage reference.                                                                                                           |
| `is_published`        | BOOLEAN       | Required; proposed default `FALSE`. Controls public discoverability and eligibility for new bookings.                                                              |
| `created_at`          | DATETIME      | Required package-creation timestamp.                                                                                                                               |
| `updated_at`          | DATETIME      | Required last-update timestamp.                                                                                                                                    |

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

### workshop_package_materials

| Column                | Type     | Constraints / description                            |
| --------------------- | -------- | ---------------------------------------------------- |
| `workshop_package_id` | BIGINT   | Required foreign key to `workshop_packages`.         |
| `material_id`         | BIGINT   | Required foreign key to `materials`.                 |
| `is_active`           | BOOLEAN  | Required; defaults to `TRUE`. Set to `FALSE` to retire compatibility for new selections while retaining the link for history. |
| `created_at`          | DATETIME | Required relationship-creation timestamp.            |
| `updated_at`          | DATETIME | Required last-update timestamp for the relationship. |

Indexes and rules:

- Composite primary key `(workshop_package_id, material_id)` prevents duplicate package/material links.
- Index `(material_id, is_active, workshop_package_id)` supports enabled reverse compatibility lookup.
- The relation expresses package compatibility, not branch availability, inventory or a price override.
- For new booking/design validation, the package must be published, the material active and this relationship active. Disable compatibility with `is_active = FALSE` rather than deleting its row; historical bookings/frozen designs retain their snapshots.
- Both FKs use `ON DELETE RESTRICT` under the P2.3 registry. Flag changes update `updated_at` and record the actor/old/new value in `audit_logs`; reactivation retains the original row and `created_at`.

### workshop_exceptions

| Column                  | Type     | Constraints / description                                                                                                      |
| ----------------------- | -------- | ------------------------------------------------------------------------------------------------------------------------------ |
| `workshop_exception_id` | BIGINT   | Primary key.                                                                                                                   |
| `location_id`           | BIGINT   | Nullable foreign key to `workshop_locations`; null with no slot means all locations on the exception date under this proposal. |
| `slot_id`               | BIGINT   | Nullable foreign key to `slots`; set for a single-session exception and null for a whole-day scope.                            |
| `exception_date`        | DATE     | Required affected local operating date. A multi-day holiday uses one dated record per day under this proposal.                 |
| `is_closed`             | BOOLEAN  | Required; proposed default `TRUE`. Blocks new booking in the matching scope.                                                   |
| `capacity_override`     | INT      | Nullable non-negative replacement capacity for an open affected session; not used when `is_closed` is true.                    |
| `start_time_override`   | TIME     | Nullable replacement session start; if used, an end-time override and a specific `slot_id` are also required.                  |
| `end_time_override`     | TIME     | Nullable replacement session end; must be later than the corresponding start override.                                         |
| `reason`                | TEXT     | Required holiday/off-day/schedule-change explanation.                                                                          |
| `is_active`             | BOOLEAN  | Required; proposed default `TRUE`. Inactive exceptions do not affect new availability decisions.                               |
| `created_by_user_id`    | BIGINT   | Required foreign key to `users`; authorised schedule-exception actor.                                                          |
| `created_at`            | DATETIME | Required exception-creation timestamp.                                                                                         |
| `updated_at`            | DATETIME | Required last-update timestamp.                                                                                                |

Indexes:

- `(exception_date, is_active, location_id)` for date/location exception selection.
- `(slot_id, is_active)` for session-specific exceptions.

Rules:

- Proposed scopes are global/date (both references null), location/date (`location_id` set, no slot), or one dated slot (both references set). For a slot scope, location/date must match the referenced slot.
- The three exception scopes are mutually exclusive and must be validated transactionally: global/date has both `location_id` and `slot_id` null; location/date has `location_id` set and `slot_id` null; slot scope has both set and the slot's location/date must match the exception.
- One active exception per identical scope/date is proposed. Nullable scope keys mean a naive unique `(location_id, exception_date, slot_id)` index does not enforce this; conditional keys or transactional validation must be designed.
- Proposed resolution chooses the most specific matching scope: slot, then location/date, then global/date. Approval is required for this precedence and whether any broader closure must remain absolute; the actual policy is `TBD`.
- A closed exception has no capacity/time override; an open exception must supply a meaningful capacity or paired time change under this proposal. Range/overlap and committed-capacity validation remain mandatory.
- Creating/changing an exception affects eligibility for new bookings; paid/confirmed registrations require explicit operational handling and are not silently cancelled, moved or refunded.
- This table describes schedule exceptions, not customer cancellations, payment failures, workshop custody or inventory adjustments.

## Custom design request

### custom_design_requests

| Column                     | Type        | Constraints / description                                                                   |
| -------------------------- | ----------- | ------------------------------------------------------------------------------------------- |
| `custom_design_request_id` | BIGINT      | Primary key.                                                                                |
| `member_user_id`           | BIGINT      | Required foreign key to `users`. Only a Member can submit a request.                        |
| `request_description`      | TEXT        | Required description entered by the Member.                                                 |
| `img_url`                  | TEXT        | Required reference-image URL or object-storage path. Each request stores exactly one image. |
| `status`                   | VARCHAR(20) | `NOT NULL DEFAULT 'need_review'`; enforced `chk_custom_design_requests_status` from the P2.2 registry. |
| `reviewed_by_user_id`      | BIGINT      | Nullable foreign key to `users`; set when the request is accepted or rejected.              |
| `reviewed_at`              | DATETIME    | Nullable; set when the request is accepted or rejected.                                     |
| `review_reason`            | TEXT        | Required when `status` is `rejected`; not required when `status` is `accepted`.             |
| `created_at`               | DATETIME    | Required submission timestamp.                                                              |
| `updated_at`               | DATETIME    | Required last-update timestamp.                                                             |

Indexes:

- `(status, created_at)` for the review queue.
- `(member_user_id, created_at)` for a Member's request history.

Rules:

- Lifecycle: `need_review` -> `accepted` or `rejected`.
- A Member cannot edit a request description or its reference images after submission. A rejected request cannot be reviewed again or resubmitted; the Member creates a new request instead.
- This table has no relationship to `ring_designs`, `orders`, pricing, payment, fulfilment, guest requests, or AI analysis.

### custom_design_reviews

This table is the append-only review timeline for a custom design request. The current request status remains in `custom_design_requests`; each review decision is recorded here for Member/Staff detail screens and auditability.

| Column                     | Type        | Constraints / description                                                   |
| -------------------------- | ----------- | --------------------------------------------------------------------------- |
| `custom_design_review_id`  | BIGINT      | Primary key.                                                                |
| `custom_design_request_id` | BIGINT      | Required foreign key to `custom_design_requests`.                           |
| `reviewer_user_id`         | BIGINT      | Required foreign key to `users`; must have an authorized review role.       |
| `decision`                 | VARCHAR(20) | `NOT NULL`, no default; enforced `chk_custom_design_reviews_decision` from the P2.2 registry. |
| `reason`                   | TEXT        | Required for `rejected`; optional for `accepted` unless policy requires it. |
| `created_at`               | DATETIME    | Required review-decision timestamp.                                         |

Indexes and rules:

- Index `(custom_design_request_id, created_at)` for chronological timeline loading.
- Index `(reviewer_user_id, created_at)` for reviewer history.
- A reviewer cannot review a request created by the same user or a request belonging to the reviewer's own Member identity; the attempt is rejected and recorded in `audit_logs`.
- Only an authorized Staff/Owner reviewer may create a review row. Review rows are append-only; a later decision is a new row subject to the request state rules.
- The service must atomically create the review row and update `custom_design_requests.status`, `reviewed_by_user_id`, `reviewed_at` and `review_reason` for the current decision.
- P2.8 `T-REVIEW` also rechecks independent reviewer authority/current request under locks and commits required owning audit/work with the decision/current fields. Competing or unauthorized terminal reviews cannot leave a timeline row without its matching accepted state; denial/failure audit uses P2.8's survival rules.

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

Required supporting relations:

- `ring_design_gemstones(ring_design_id, gemstone_id, quantity, ...)` is required for a design with one or more gemstone selections. `quantity` must be positive; the final uniqueness key for variant, size and placement remains to be specified before implementation.
- `ring_design_attachments(ring_design_id, attachment_id, quantity, ...)` is required for attachment selections. `quantity` must be positive; placement and variant uniqueness remain to be specified before implementation.
- `workshop_package_materials(workshop_package_id, material_id, ...)` is required to represent package/material compatibility. A single `material_id` on `workshop_packages` must not be introduced as a substitute for this many-to-many relation.
- These supporting relations are part of the logical design even though their complete physical columns are still `TBD`; a design/package must not be considered valid merely because its component IDs exist.

### ring_design_gemstones

| Column                    | Type         | Constraints / description                                                        |
| ------------------------- | ------------ | -------------------------------------------------------------------------------- |
| `ring_design_gemstone_id` | BIGINT       | Primary key.                                                                     |
| `ring_design_id`          | BIGINT       | Required foreign key to `ring_designs`.                                          |
| `gemstone_id`             | BIGINT       | Required foreign key to `gemstones`.                                             |
| `quantity`                | INT          | Required positive selected quantity.                                             |
| `variant_code`            | VARCHAR(64)  | Nullable option/variant identifier; final variant catalog remains `TBD`.         |
| `placement`               | VARCHAR(100) | Nullable design placement description; controlled placement values remain `TBD`. |
| `created_at`              | DATETIME     | Required selection-creation timestamp.                                           |
| `updated_at`              | DATETIME     | Required last-update timestamp while the design is editable.                     |

Indexes and rules:

- Index `(ring_design_id)` for design component loading.
- Index `(gemstone_id)` for component-reference queries.
- `quantity` must be greater than `0`.
- Duplicate selection rules involving variant, size and placement remain `TBD`; do not assume `(ring_design_id, gemstone_id)` is unique until those dimensions are finalized.
- A selected gemstone must be active when a design is validated or published. Historical frozen selections remain readable after later deactivation.

### ring_design_attachments

| Column                      | Type         | Constraints / description                                                        |
| --------------------------- | ------------ | -------------------------------------------------------------------------------- |
| `ring_design_attachment_id` | BIGINT       | Primary key.                                                                     |
| `ring_design_id`            | BIGINT       | Required foreign key to `ring_designs`.                                          |
| `attachment_id`             | BIGINT       | Required foreign key to `attachments`.                                           |
| `quantity`                  | INT          | Required positive selected quantity.                                             |
| `variant_code`              | VARCHAR(64)  | Nullable option/variant identifier; final variant catalog remains `TBD`.         |
| `placement`                 | VARCHAR(100) | Nullable design placement description; controlled placement values remain `TBD`. |
| `created_at`                | DATETIME     | Required selection-creation timestamp.                                           |
| `updated_at`                | DATETIME     | Required last-update timestamp while the design is editable.                     |

Indexes and rules:

- Index `(ring_design_id)` for design component loading.
- Index `(attachment_id)` for component-reference queries.
- `quantity` must be greater than `0`.
- Duplicate selection rules involving variant and placement remain `TBD`; do not assume `(ring_design_id, attachment_id)` is unique until those dimensions are finalized.
- A selected attachment must be active when a design is validated or published. Historical frozen selections remain readable after later deactivation.

### ring_designs

| Column               | Type          | Constraints / description                                                                                                                                                                                                            |
| -------------------- | ------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `ring_design_id`     | BIGINT        | Primary key. Proposed identity of a specific design/version.                                                                                                                                                                         |
| `design_name`        | VARCHAR(255)  | Required display name for a catalogue model; nullable for a customer's unnamed configured design.                                                                                                                                    |
| `description`        | TEXT          | Nullable catalogue/design explanation.                                                                                                                                                                                               |
| `design_type`        | VARCHAR(30)   | `NOT NULL`, no default; enforced `chk_ring_designs_design_type` from the P2.2 registry. Does not include image-based custom requests. |
| `created_by_user_id` | BIGINT        | Nullable foreign key to `users`; authorised catalogue author or Member configuration creator when authenticated. Null can represent a permitted Guest workshop configuration.                                                        |
| `source_design_id`   | BIGINT        | Nullable self-referencing foreign key to a catalogue model used as a configuration starting point or to a preceding design version. Must not reference itself or create a cycle.                                                     |
| `material_id`        | BIGINT        | Required foreign key to `materials` under the single-base-material proposal.                                                                                                                                                         |
| `shape_id`           | BIGINT        | Required foreign key to `shape` for the base ring form.                                                                                                                                                                              |
| `ring_size`          | VARCHAR(30)   | Nullable selected ring size. Size system, allowed values and the point at which it becomes required remain `TBD`; it is not a free-text substitute for validated size options.                                                       |
| `img_url`            | TEXT          | Nullable catalogue preview/model image. Not a Member custom-request reference image.                                                                                                                                                 |
| `component_snapshot` | JSON          | SQL-null only while editing a draft; otherwise required `ring_design_components/1` envelope and closed P2.9 schema, 64 KiB cap, selection/result consistency and immutable frozen-version rules. |
| `estimated_price`    | DECIMAL(15,2) | Nullable non-negative system-calculated estimate using approved component/price rules. Not an AI-generated price, fixed order price or amount charged.                                                                               |
| `currency`           | CHAR(3)       | Nullable until an estimate exists; required with `estimated_price` and must match its monetary inputs.                                                                                                                               |
| `difficulty_score`   | DECIMAL(10,4) | Nullable non-negative calculated difficulty score. Scale, formula and thresholds remain `TBD`.                                                                                                                                       |
| `rules_version`      | VARCHAR(64)   | Nullable until evaluation; required with stored estimate/difficulty results under this proposal. Identifies the approved rule set used; its backing rule records remain a separate dependency.                                       |
| `status`             | VARCHAR(20)   | `NOT NULL DEFAULT 'draft'`; enforced `chk_ring_designs_status` from the P2.2 registry. `published` is only for a catalogue model. |
| `created_at`         | DATETIME      | Required design-version creation timestamp.                                                                                                                                                                                          |
| `updated_at`         | DATETIME      | Required last-update timestamp.                                                                                                                                                                                                      |

Indexes:

- `(design_type, status, created_at)` for published-model browsing and configuration queues.
- `(created_by_user_id, created_at)` for permitted creator history.
- `(material_id, shape_id)` for component-based model filtering.
- `(shape_id)` for shape-reference queries and the foreign key.
- `(source_design_id)` for model/version lineage.

Rules:

- Catalogue/configured designs are validated against approved active components, location/package compatibility and hard constraints. Merely selecting existing IDs is not enough to prove feasibility.
- Gemstone and attachment selections use the additional junctions described above. A design with no stone or decoration has no corresponding selection rows, rather than a fake zero-ID component.
- The JSON snapshot preserves the selected configuration and evaluation inputs; it does not replace relational foreign keys, compatibility checks or an approved rule store. P2.9 defines its versioned closed schema, data bounds, validation and historical-reader contract.
- Proposed lifecycle: draft configurations become `validated`; validated catalogue models may become `published`; retirement uses `archived`. Validation/publication authority and exact transitions remain `TBD`.
- Only published catalogue models are publicly selectable. A customer's configuration is private to its permitted booking/Member journey and is not automatically published.
- Referenced validated designs are frozen versions under this proposal. Changing a referenced model or configuration creates a new version; later component-price edits do not silently rewrite historical selections/estimates.
- A price estimate is informational until the owning billing/order flow snapshots and accepts the payable amount. No automatic pricing, purchase or manufacturing follows design validation.
- Guest configuration storage, access credentials and abandoned-draft retention remain `TBD`; a null creator does not make a design public.
- A Guest workshop booking may retain a configured `ring_designs` row with `created_by_user_id` null. That row remains private to the permitted booking flow and must not be exposed as a public catalogue model. Guest design and contact data are retained only as long as required by the approved booking/business-record retention policy.
- This table has no `custom_design_request_id`, reference-image review status, AI output or request-review decision. The existing custom-request lifecycle remains separate.

### gemstones

| Column             | Type          | Constraints / description                                                                                        |
| ------------------ | ------------- | ---------------------------------------------------------------------------------------------------------------- |
| `gemstone_id`      | BIGINT        | Primary key.                                                                                                     |
| `gemstone_code`    | VARCHAR(64)   | Required unique catalogue-option code.                                                                           |
| `gemstone_name`    | VARCHAR(100)  | Required gemstone-option display name.                                                                           |
| `description`      | TEXT          | Nullable approved gemstone details.                                                                              |
| `gemstone_type`    | VARCHAR(100)  | Nullable stone/material classification; final taxonomy remains `TBD`.                                            |
| `color`            | VARCHAR(50)   | Nullable catalogue color label.                                                                                  |
| `cut_name`         | VARCHAR(100)  | Nullable gemstone cut/form label; distinct from the base ring `shape`.                                           |
| `dimensions`       | VARCHAR(100)  | Nullable descriptive size specification. Supported units/dimensions and structured option modeling remain `TBD`. |
| `unit_price`       | DECIMAL(15,2) | Required non-negative current catalogue price for the documented `price_unit`.                                   |
| `price_unit`       | VARCHAR(20)   | Required explicit pricing unit, for example `piece`. Approved units/conversions remain `TBD`.                    |
| `currency`         | CHAR(3)       | Required price currency code.                                                                                    |
| `difficulty_score` | DECIMAL(10,4) | Nullable non-negative configured difficulty contribution; no score or aggregation rule is invented.              |
| `img_url`          | TEXT          | Nullable catalogue-option image URL or storage reference.                                                        |
| `is_active`        | BOOLEAN       | Required; proposed default `FALSE` until the option is approved for new selections.                              |
| `created_at`       | DATETIME      | Required option-creation timestamp.                                                                              |
| `updated_at`       | DATETIME      | Required last-update timestamp.                                                                                  |

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

| Column             | Type          | Constraints / description                                                                                                           |
| ------------------ | ------------- | ----------------------------------------------------------------------------------------------------------------------------------- |
| `material_id`      | BIGINT        | Primary key.                                                                                                                        |
| `material_code`    | VARCHAR(64)   | Required unique material-option code.                                                                                               |
| `material_name`    | VARCHAR(100)  | Required display name of the base ring material.                                                                                    |
| `description`      | TEXT          | Nullable material characteristics and use conditions.                                                                               |
| `purity`           | VARCHAR(50)   | Nullable approved purity/grade label, where relevant; final format remains `TBD`.                                                   |
| `unit_price`       | DECIMAL(15,2) | Required non-negative catalogue price for one documented pricing unit.                                                              |
| `price_unit`       | VARCHAR(20)   | Required explicit unit, for example `gram` or `piece`; final unit system remains `TBD`. A price is not implicitly a per-ring total. |
| `currency`         | CHAR(3)       | Required material-price currency code.                                                                                              |
| `difficulty_score` | DECIMAL(10,4) | Nullable non-negative configured difficulty contribution; rule values remain `TBD`.                                                 |
| `img_url`          | TEXT          | Nullable material preview URL or storage reference.                                                                                 |
| `is_active`        | BOOLEAN       | Required; proposed default `FALSE` until approved for new selections.                                                               |
| `created_at`       | DATETIME      | Required material-creation timestamp.                                                                                               |
| `updated_at`       | DATETIME      | Required last-update timestamp.                                                                                                     |

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

| Column             | Type          | Constraints / description                                                                                 |
| ------------------ | ------------- | --------------------------------------------------------------------------------------------------------- |
| `attachment_id`    | BIGINT        | Primary key. Represents a physical ring decoration/assembly component under the stated interpretation.    |
| `attachment_code`  | VARCHAR(64)   | Required unique component-option code.                                                                    |
| `attachment_name`  | VARCHAR(100)  | Required component display name.                                                                          |
| `description`      | TEXT          | Nullable physical-component description and use conditions.                                               |
| `attachment_type`  | VARCHAR(100)  | Nullable approved component classification; final taxonomy remains `TBD`.                                 |
| `unit_price`       | DECIMAL(15,2) | Required non-negative current price per explicit `price_unit`.                                            |
| `price_unit`       | VARCHAR(20)   | Required pricing unit, for example `piece`; units remain subject to catalogue approval.                   |
| `currency`         | CHAR(3)       | Required price currency code.                                                                             |
| `difficulty_score` | DECIMAL(10,4) | Nullable non-negative configured effort/difficulty contribution; not an independent feasibility decision. |
| `img_url`          | TEXT          | Nullable physical-component preview URL or storage reference.                                             |
| `is_active`        | BOOLEAN       | Required; proposed default `FALSE` until approved for selection.                                          |
| `created_at`       | DATETIME      | Required component-creation timestamp.                                                                    |
| `updated_at`       | DATETIME      | Required last-update timestamp.                                                                           |

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

| Column             | Type          | Constraints / description                                                                                                                                                                |
| ------------------ | ------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `shape_id`         | BIGINT        | Primary key. Retains the singular table name requested in this document.                                                                                                                 |
| `shape_code`       | VARCHAR(64)   | Required unique base ring-form option code.                                                                                                                                              |
| `shape_name`       | VARCHAR(100)  | Required ring-form display name.                                                                                                                                                         |
| `description`      | TEXT          | Nullable approved form/structural description and constraints.                                                                                                                           |
| `price_adjustment` | DECIMAL(15,2) | Nullable non-negative configured form-related estimate contribution. Null means no approved standalone price input, not an automatic zero charge. The final pricing model remains `TBD`. |
| `currency`         | CHAR(3)       | Nullable when there is no monetary contribution; required with `price_adjustment`.                                                                                                       |
| `difficulty_score` | DECIMAL(10,4) | Nullable non-negative configured structural-difficulty contribution; formula/thresholds remain `TBD`.                                                                                    |
| `img_url`          | TEXT          | Nullable ring-form preview URL or storage reference.                                                                                                                                     |
| `is_active`        | BOOLEAN       | Required; proposed default `FALSE` until the form is approved for new selections.                                                                                                        |
| `created_at`       | DATETIME      | Required ring-form creation timestamp.                                                                                                                                                   |
| `updated_at`       | DATETIME      | Required last-update timestamp.                                                                                                                                                          |

Indexes:

- Unique `(shape_code)` for stable ring-form lookup.
- `(is_active, shape_name)` for available-form selection.

Rules:

- One base ring form may be referenced by many designs; one base form per design is a stated proposal, not a documented final cardinality.
- Shape is separate from gemstone cut, ring size and a complete catalogue model. Its exact structural attribute schema remains `TBD`.
- Material, component placement and size compatibility must be checked against approved hard constraints; a numeric score alone cannot override an incompatible design.
- A standalone shape price contribution is included only under an approved estimation rule and must not double-count component/labour prices.
- Form/score/price changes do not rewrite frozen design versions. Disable an option for new selection rather than deleting referenced history.
