### 3.3 Data Business Rules

The following rules define data constraints, lifecycle restrictions, historical preservation, and cross-entity consistency that are not fully visible in the conceptual ERD.

| Rule | Description | Enforced On |
|---|---|---|
| DBR-01: External identity uniqueness | Each User must have a unique Firebase UID (`external_user_id`). Email is optional and is not used as a unique account identity. | `users` |
| DBR-02: One role per user | Each User references exactly one Role. A Guest may use workshop services without a User record. | `users`, `roles` |
| DBR-03: Role deactivation protection | A Role cannot be deactivated while any User, including a suspended User, still references it. | `roles`, `users` |
| DBR-04: Append-only audit evidence | Audit Log records are append-only during normal operations. Each record keeps the actor, affected entity, action, outcome, reason, and occurrence time. | `audit_logs` |
| DBR-05: Audit retention | Audit evidence is retained for five years. Privacy or expiry controls may restrict access without silently rewriting the original event. | `audit_logs` |
| DBR-06: Catalogue publication boundary | Inactive Categories and unpublished Products cannot be selected for new public purchases. Unpublishing a Product must not modify historical Order Items. | `categories`, `products`, `order_items` |
| DBR-07: Product image ordering | A Product cannot contain two Product Images with the same display position. | `product_imgs` |
| DBR-08: Order line snapshot | An Order Item stores the agreed product name, unit price, quantity, and line amount at checkout. Later catalogue edits must not recalculate historical Order Items. | `orders`, `order_items`, `products` |
| DBR-09: Checkout idempotency | A `checkout_key` identifies one checkout command across retries. Repeating the same key must not create a second retail Order. | `orders` |
| DBR-10: Order reference uniqueness | Each customer-facing `order_code` is unique and remains stable for the lifetime of the Order. | `orders` |
| DBR-11: Controlled Order lifecycle | Retail Order status changes must follow the approved lifecycle: pending payment, paid, preparing, ready for pickup or prepared for carrier, and the corresponding completion state. V1 does not create cancellation, refund, or return states. | `orders` |
| DBR-12: One delivery record per order | An Order has at most one Delivery Information record. Delivery data is created for a paid carrier order and records the evidenced handoff. | `delivery_infos` |
| DBR-13: Payment target integrity | Each Payment belongs to exactly one payable target and has one payment purpose. A Payment cannot be linked simultaneously to unrelated Orders or workshop deposits. | `payments` |
| DBR-14: Verified payment authority | A Payment may be marked successful only from valid signed provider evidence while the related hold is active. Browser return data alone cannot update payment success. | `payments`, `payment_transactions` |
| DBR-15: Payment duplicate protection | A provider event or equivalent callback fingerprint may be applied only once. Duplicate callbacks must be recorded or recognized without applying the payment effect twice. | `payment_transactions`, `payments` |
| DBR-16: Stable branch identity | `location_code` is unique and remains the stable business reference for a Workshop Location even when its name, address, or contact data changes. | `workshop_locations` |
| DBR-17: Slot identity and capacity | A workshop Slot is unique by branch, session date, and start time. Capacity must be configured before booking; a null or zero capacity is not bookable. | `slots` |
| DBR-18: Booking identity and snapshots | Each Workshop Registration has a unique `booking_code` and stores package, price, deposit, participant, and contact snapshots. A Guest contact email does not establish account ownership. | `workshop_registration` |
| DBR-19: Active relationship requirement | A package, material, or promotion may be selected at a branch only when its relationship record and the referenced entities are active. Disabling a relationship affects new selections, not retained history. | `workshop_package_locations`, `material_locations`, `promotion_locations` |
| DBR-20: Relationship uniqueness | A package-branch, material-branch, package-material, and promotion-branch pair may have only one relationship record. Lifecycle changes update the relationship instead of creating duplicate active pairs. | Relationship entities |
| DBR-21: Stable component codes | Material, Shape, Gemstone, and Attachment codes are unique stable catalogue references. Inactive components cannot be newly selected. | `materials`, `shape`, `gemstones`, `attachments` |
| DBR-22: Ring design version integrity | A validated, published, or referenced Ring Design is immutable. A change to a frozen design creates a new version and preserves the original source lineage. | `ring_designs` |
| DBR-23: Design component validity | Ring Design component quantities must be positive. A newly validated design may reference only active and compatible Gemstones and Attachments. | `ring_design_gemstones`, `ring_design_attachments`, `ring_designs` |
| DBR-24: Design request immutability | After submission, a Custom Design Request's description and reference image cannot be edited. A rejected request requires a new request instead of reopening the old record. | `custom_design_requests` |
| DBR-25: Review integrity | Each Custom Design Review must identify an authorized reviewer and a decision reason when required. The request decision and review record are stored atomically, and self-review is not permitted. | `custom_design_reviews`, `custom_design_requests` |
| DBR-26: Promotion code uniqueness | A voucher code is unique when present, and a Promotion contains at most one voucher code. | `promotions` |
| DBR-27: Promotion history preservation | A Promotion or Promotion Location may be deactivated for future eligibility, but existing Orders retain the frozen promotion terms and discount amount applied at checkout. | `promotions`, `promotion_locations`, `orders` |
| DBR-28: Promotion redemption lifecycle | A Promotion Redemption follows `reserved` to `redeemed` or `released`. Terminal states cannot be reversed, and a verified payment may consume a reservation only once. | `promotion_redemptions` |
| DBR-29: Loyalty policy versioning | `policy_version` is unique. Only one Loyalty Policy may be active per currency, and published or referenced policy terms are immutable; changes create a new version. | `loyalty_policies` |
| DBR-30: Immutable loyalty ledger | Loyalty Point entries are immutable after posting. `event_key` is unique for each business event, and holds do not create a debit entry until the related payment is verified. | `loyalty_points` |
| DBR-31: Loyalty redemption lifecycle | A Loyalty Redemption follows `reserved` to `redeemed` or `released`. A failed or expired checkout releases the hold without creating a compensating credit or restoring a previously spent point entry. | `loyalty_redemptions`, `loyalty_points` |
| DBR-32: Loyalty allocation integrity | A loyalty credit lot cannot be allocated beyond its spendable quantity. Released allocations count as neither held nor consumed, while settled allocations are immutable. | `loyalty_allocations`, `loyalty_points` |
| DBR-33: AI and personal-data retention | Gemini text prompts and responses are retained for at most 30 days, and AI image input is not retained after processing. Other reference images, payment records, and audit evidence follow their approved retention rules. | AI request records, `attachments`, `payments`, `audit_logs` |
