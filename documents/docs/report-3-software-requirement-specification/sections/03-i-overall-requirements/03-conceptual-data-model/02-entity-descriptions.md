3.2 Entity Descriptions

**Entity: User (users)**

| Field | Value |
| --- | --- |
| Purpose | Represents a person with an authenticated system account, with exactly one assigned business role. Guest workshop customers do not require a User record. |
| Key business attributes | Firebase UID, display name, contact email, email verification, phone number, profile image, assigned role and account status. |
| Business identity | The Firebase UID (external_user_id) is unique across accounts. Email is optional and not unique; matching email text does not merge accounts. |
| Status / lifecycle | active or suspended. Suspension blocks protected access while retaining business history. Authentication credentials are managed by Firebase. |

**Entity: Role (roles)**

| Field | Value |
| --- | --- |
| Purpose | Defines a business role that can be assigned to system accounts. |
| Key business attributes | Role code, role name, description and activation flag. |
| Business identity | role_code is unique. Each User references one Role; Guest is not an assigned account role. Role labels alone do not define permissions. |
| Status / lifecycle | Active or inactive through is_active. Deactivation is rejected while any User, including a suspended User, still references the Role. |

**Entity: Audit Log (audit_logs)**

| Field | Value |
| --- | --- |
| Purpose | Records administrative actions and rejected operations for accountability, separate from payment and design-review timelines. |
| Key business attributes | Actor, affected entity, action, outcome, reason, approved before/after images, request reference and occurrence time. |
| Business identity | Each entry has its own audit_log_id. The request reference and affected entity identify context, not a declared unique business key. |
| Status / lifecycle | Outcomes are SUCCESS, REJECTED or FAILED, rather than workflow states. Evidence is append-only under normal operations, with five-year retention and guarded privacy/expiry. |

**Entity: Category (categories)**

| Field | Value |
| --- | --- |
| Purpose | Groups catalogue products for browsing and filtering. |
| Key business attributes | Category name, description and activation flag. |
| Business identity | The category name is proposed as unique under the selected database collation; category_id provides a stable reference. |
| Status / lifecycle | Active or inactive through is_active. Inactive categories are excluded from public selection; historical product references are preserved. |

**Entity: Product (products)**

| Field | Value |
| --- | --- |
| Purpose | Represents a catalogue item available for Member retail purchase. |
| Key business attributes | Product name, description, category, unit price, currency, remaining-unsold quantity and publication flag. |
| Business identity | product_id identifies the catalogue item. The design does not declare a unique product name or a separate SKU. |
| Status / lifecycle | Published or unpublished through is_published. Checkout revalidates availability and active holds. Unpublishing stops new purchases without rewriting historical order items. |

**Entity: Product Image (product_imgs)**

| Field | Value |
| --- | --- |
| Purpose | Provides an ordered catalogue image for a Product. |
| Key business attributes | Product reference, image URL, alternative text and display position. |
| Business identity | product_img_id identifies the image record; (product_id, sort_order) is unique within a Product's image list. |
| Status / lifecycle | No separate status. Authorized maintainers may add, replace, reorder or remove image references. |

**Entity: Order (orders)**

| Field | Value |
| --- | --- |
| Purpose | Records a Member's retail purchase, processing branch, frozen payable amount and fulfilment progress. |
| Key business attributes | Order reference, Member, branch, fulfilment method, subtotal, promotion and loyalty discounts, points requested, currency, hold deadline and payment/pickup times. |
| Business identity | order_code is the unique customer-facing reference. checkout_key uniquely identifies a checkout command across retries with the same request. |
| Status / lifecycle | Proposed: pending_payment becomes paid or expired; paid becomes preparing, then ready_for_pickup → picked_up or prepared_for_carrier → handed_to_carrier. No in-system cancellation, refund or return lifecycle. |

**Entity: Order Item (order_items)**

| Field | Value |
| --- | --- |
| Purpose | Records a Product and its purchased quantity within an Order, preserving the agreed name and price. |
| Key business attributes | Order, Product, product-name snapshot, quantity, unit price and line amount. |
| Business identity | order_item_id identifies the line. The proposed unique (order_id, product_id) aggregates each Product into one line per Order. |
| Status / lifecycle | No independent status; follows the parent Order. Historical line snapshots are retained and are not recalculated from later catalogue changes. |

**Entity: Payment (payments)**

| Field | Value |
| --- | --- |
| Purpose | Represents one payment attempt for a retail Order or a workshop deposit. |
| Key business attributes | Payment reference, exactly one payable target, purpose, amount, currency, provider references, verified outcome, failure reason, optional loyalty-generation binding and evidence-retirement time. |
| Business identity | payment_code is unique per attempt. Provider-reference uniqueness depends on the approved gateway contract. A retry uses the applicable attempt and generation rules. |
| Status / lifecycle | Starts at pending; documented outcomes are succeeded, failed, cancelled or expired. Only verified provider evidence can authorize success. Evidence retirement permanently blocks further application effects without changing the original outcome. |

**Entity: Payment Transaction (payment_transactions)**

| Field | Value |
| --- | --- |
| Purpose | Stores normalized callback or recovery evidence for a Payment, supporting duplicate recognition and recovery. |
| Key business attributes | Payment, provider event/transaction references, reported amount and currency, normalized outcome, payload fingerprint, receipt/processing/application times and processing reason. |
| Business identity | Each receipt has a payment_transaction_id. Available provider event IDs and the (provider, payment_id, payload_hash) fingerprint provide documented duplicate-event guards. |
| Status / lifecycle | Outcome codes are received, succeeded, failed, cancelled or unknown; receipt alone does not confirm payment. Original evidence is retained while guarded processing metadata records application decisions. Evidence follows the five-year retention policy. |

**Entity: Delivery Information (delivery_infos)**

| Field | Value |
| --- | --- |
| Purpose | Stores a retail Order's recipient details and evidenced handoff to a third-party carrier. |
| Key business attributes | Order, recipient name and phone, delivery address and note, carrier, handoff reference, recording actor and handoff time. |
| Business identity | Each record has a delivery_infor_id; order_id is required and unique, allowing at most one delivery record per Order. |
| Status / lifecycle | No separate status. Applies to paid carrier Orders; responsibility ends at recorded handoff. Recipient/contact/address data is redacted 30 days after handoff while required handoff history remains. |

**Entity: Workshop Location (workshop_locations)**

| Field | Value |
| --- | --- |
| Purpose | Represents an operating branch used for workshops, retail processing and branch-specific eligibility. |
| Key business attributes | Stable branch code, name, address, city, country, contact phone, timezone and activation flag. |
| Business identity | location_code is a unique stable business reference, independent of later name or address changes. |
| Status / lifecycle | Active or inactive through is_active. Deactivation blocks new operational use without deleting or silently relocating existing bookings and Orders. |

**Entity: Slot (slots)**

| Field | Value |
| --- | --- |
| Purpose | Represents a dated workshop session at a branch, with time and capacity constraints. |
| Key business attributes | Branch, session date, start/end times, configured capacity and open flag. |
| Business identity | slot_id identifies the session; (location_id, slot_date, start_time) uniquely identifies a dated start at a branch. |
| Status / lifecycle | Open or closed through is_open, subject to schedule exceptions. Capacity must be configured before booking; null requires configuration and zero means no bookable seats. Remaining seats are derived from participant quantities and valid holds; closure does not silently move paid bookings. |

**Entity: Workshop Package (workshop_packages)**

| Field | Value |
| --- | --- |
| Purpose | Defines a selectable workshop offering and its booking terms. |
| Key business attributes | Package code, name, description, conditions, duration, price, currency, deposit percentage, image and publication flag. |
| Business identity | package_code is the unique stable catalogue reference. |
| Status / lifecycle | Published or unpublished through is_published. Eligibility also depends on branch and material relationships. Retiring a Package preserves booking snapshots. |

**Entity: Workshop Registration (workshop_registration)**

| Field | Value |
| --- | --- |
| Purpose | Represents one workshop booking for a Member or Guest, including a participant group and the confirmation/payment gates. |
| Key business attributes | Booking reference, optional Member, slot, Package, optional ring design, design path, contact snapshot, participant counts, price/deposit snapshots, email gate, hold deadline, check-in and continuation references. |
| Business identity | booking_code is unique. Guest contact email is booking-scoped and does not automatically establish account ownership. A continuation is a new booking linked to a previous registration. |
| Status / lifecycle | Proposed: pending → confirmed → checked_in → completed, or pending → expired. The email gate separately records EMAIL_CONFIRMATION_PENDING, CONFIRMED or EXPIRED; confirmation requires applicable email/deposit gates. Physical state mapping remains to be approved. |

**Entity: Workshop Exception (workshop_exceptions)**

| Field | Value |
| --- | --- |
| Purpose | Records a dated closure or capacity/time exception for the workshop schedule. |
| Key business attributes | Exception date, optional branch/slot scope, closure flag, capacity/time overrides, reason, activation flag and creator. |
| Business identity | workshop_exception_id identifies the record. Scope can be global, branch-wide or slot-specific; enforcing one active exception per identical scope/date remains a documented design requirement. |
| Status / lifecycle | Active or inactive through is_active. Changes affect new booking eligibility; existing confirmed bookings require explicit handling. |

**Entity: Workshop Package Location (workshop_package_locations)**

| Field | Value |
| --- | --- |
| Purpose | Defines whether a Workshop Package may be selected at a branch. |
| Key business attributes | Package, branch and relationship activation flag. |
| Business identity | The (workshop_package_id, location_id)pair uniquely identifies the relationship. |
| Status / lifecycle | Active or inactive through is_active. Disabling stops new branch-specific selection while retaining the relationship and existing booking snapshots. |

**Entity: Material Location (material_locations)**

| Field | Value |
| --- | --- |
| Purpose | Defines whether a Material is available for new design selection at a branch. |
| Key business attributes | Material, branch and relationship activation flag. |
| Business identity | The (material_id, location_id) pair uniquely identifies the relationship. |
| Status / lifecycle | Active or inactive through is_active. Selection also requires the Material and branch to be active. The relationship records availability, not inventory quantity. |

**Entity: Workshop Package Material (workshop_package_materials)**

| Field | Value |
| --- | --- |
| Purpose | Defines compatibility between a Workshop Package and a Material. |
| Key business attributes | Package, Material and relationship activation flag. |
| Business identity | The (workshop_package_id, material_id) pair uniquely identifies the relationship. |
| Status / lifecycle | Active or inactive through is_active. New use also requires a published Package and active Material. Disable rather than delete a retained relationship; changes are audited and do not rewrite frozen history. |

**Entity: Material (materials)**

| Field | Value |
| --- | --- |
| Purpose | Defines a selectable base material used in ring designs and compatible workshop offerings. |
| Key business attributes | Material code, name, description, purity, unit price, pricing unit, currency, difficulty score, image and activation flag. |
| Business identity | material_code is the unique stable catalogue reference. |
| Status / lifecycle | Active or inactive through is_active. Repricing affects new evaluations; historical snapshots remain unchanged. Branch availability is managed separately. |

**Entity: Shape (shape)**

| Field | Value |
| --- | --- |
| Purpose | Defines a selectable base ring form, separate from ring size, gemstone cut and a complete design. |
| Key business attributes | Shape code, name, description, proposed price contribution, currency, difficulty score, image and activation flag. |
| Business identity | shape_code is the unique stable catalogue reference. |
| Status / lifecycle | Active or inactive through is_active. Referenced forms are retained; changes do not rewrite frozen designs. |

**Entity: Gemstone (gemstones)**

| Field | Value |
| --- | --- |
| Purpose | Defines a selectable gemstone option for ring-design component selections. |
| Key business attributes | Gemstone code, name, type, colour, cut, dimensions, unit price, pricing unit, currency, difficulty score, image and activation flag. |
| Business identity | gemstone_code is the unique stable option reference. |
| Status / lifecycle | Active or inactive through is_active. Inactive options cannot be newly selected; referenced selections and historical price snapshots remain valid. |

**Entity: Attachment (attachments)**

| Field | Value |
| --- | --- |
| Purpose | Defines a selectable ring decoration or accessory component. |
| Key business attributes | Attachment code, name, type, description, unit price, pricing unit, currency, difficulty score, image and activation flag. |
| Business identity | attachment_code is the unique stable catalogue reference. |
| Status / lifecycle | Active or inactive through is_active. Repricing or retirement affects new selection/evaluation only; referenced frozen designs retain their snapshots. |

**Entity: Ring Design (ring_designs)**

| Field | Value |
| --- | --- |
| Purpose | Represents a version of a catalogue ring model or a customer's configured design for the permitted workshop journey. |
| Key business attributes | Design name and description, design type, optional creator/source design, base Material and Shape, ring size, image, component snapshot, estimated price, currency, difficulty and rules version. |
| Business identity | ring_design_id identifies a specific design/version. source_design_id records lineage; a design name is not declared unique. Changes to referenced frozen designs create new versions. |
| Status / lifecycle | Proposed states are draft, validated, published and archived. Only validated catalogue models may be published; customer configurations remain private. |

**Entity: Ring Design Gemstone (ring_design_gemstones)**

| Field | Value |
| --- | --- |
| Purpose | Records a gemstone selection and its quantity within a Ring Design. |
| Key business attributes | Design, Gemstone, positive quantity, optional variant code and placement. |
| Business identity | ring_design_gemstone_id identifies the selection. A design/gemstone pair is not declared unique. |
| Status / lifecycle | No independent status. Selections can change only while the design is editable; validated/frozen/referenced designs retain them. Newly validated selections require an active, compatible Gemstone. |

**Entity: Ring Design Attachment (ring_design_attachments)**

| Field | Value |
| --- | --- |
| Purpose | Records an attachment selection and its quantity within a Ring Design. |
| Key business attributes | Design, Attachment, positive quantity, optional variant code and placement. |
| Business identity | ring_design_attachment_id identifies the selection. A design/attachment pair is not assumed unique until variant and placement rules are approved. |
| Status / lifecycle | No independent status. Selections can change only while the design is editable; frozen/referenced designs retain them. Newly validated selections require an active, compatible Attachment. |

**Entity: Custom Design Request (custom_design_requests)**

| Field | Value |
| --- | --- |
| Purpose | Records a Member's request for Staff review of a ring idea using a description and one reference image. |
| Key business attributes | Submitting Member, description, image URL, request status, current reviewer, review time and reason. |
| Business identity | custom_design_request_id identifies a submitted request. A new request is required instead of reopening or resubmitting a rejected one. |
| Status / lifecycle | need_review → accepted or rejected; rejection requires a reason, and both outcomes are terminal. Submitted content is not editable. Acceptance does not create a Ring Design, quote, booking, Order or payment. |

**Entity: Custom Design Review (custom_design_reviews)**

| Field | Value |
| --- | --- |
| Purpose | Preserves the append-only decision timeline for a Custom Design Request. |
| Key business attributes | Request, independent authorized reviewer, decision, reason and decision time. |
| Business identity | custom_design_review_id identifies the review event. A Request may have review history; its reference is not unique in this entity. |
| Status / lifecycle | Decision is accepted or rejected, rather than a separate lifecycle status. The review and current Request decision are recorded atomically. Terminal Request rules and the prohibition on self-review still apply. |

**Entity: Promotion (promotions)**

| Field | Value |
| --- | --- |
| Purpose | Defines a conditional discount campaign or voucher for eligible Member retail Orders. |
| Key business attributes | Campaign name, description, optional voucher code, discount type/value, currency, minimum amount, discount cap, total/per-Member usage limits, validity window, activation flag and creator. |
| Business identity | promotion_id identifies the campaign. An optional voucher_code is unique; the current proposal allows at most one code per Promotion. |
| Status / lifecycle | Eligibility depends on is_active, the validity window and approved branch/order rules; there is no separate status field. Retirement preserves discount history. |

**Entity: Promotion Location (promotion_locations)**

| Field | Value |
| --- | --- |
| Purpose | Defines a Promotion's applicability at a branch. |
| Key business attributes | Promotion, branch, activation flag, assignment creator and creation/update times. |
| Business identity | The (promotion_id, location_id) pair uniquely identifies the assignment. Reactivation retains the original creator and creation time. |
| Status / lifecycle | Active or inactive through is_active; absence of a link means the Promotion does not apply at that branch. Disabling affects new eligibility and preserves existing discount snapshots. |

**Entity: Promotion Redemption (promotion_redemptions)**

| Field | Value |
| --- | --- |
| Purpose | Records a conditional promotion quota reservation and its eventual use or release for one retail checkout generation. |
| Key business attributes | Reservation reference, Promotion, Order, Member, branch, frozen eligibility/discount/limit terms, discount amount, currency, deadline, accepted payment and terminal evidence. |
| Business identity | reservation_key is unique. At most one reserved/redeemed generation exists per Order/Promotion; multiple released generations are retained. |
| Status / lifecycle | reserved → redeemed or released, with terminal states irreversible. Verified payment consumes quota once; eligible failure/expiry releases the unpaid reservation without deleting history. Old callbacks cannot use a replacement generation. |

**Entity: Payment Promotion Redemption (payment_promotion_redemptions)**

| Field | Value |
| --- | --- |
| Purpose | Links a payment attempt to the exact conditional Promotion Redemption generations included in its quote. |
| Key business attributes | Payment, Promotion Redemption and pre-initiation link creation time. |
| Business identity | The (payment_id, promotion_redemption_id) pair is unique. Multiple attempts may use a still-valid generation; multiple campaigns per attempt require approved stacking. |
| Status / lifecycle | No independent status. Links are committed before provider initiation and retained as immutable evidence; an old attempt cannot be relinked to a replacement generation. |

**Entity: Loyalty Policy (loyalty_policies)**

| Field | Value |
| --- | --- |
| Purpose | Defines conditional, versioned terms for point-to-money conversion and credit expiry. |
| Key business attributes | Policy version, currency, conversion block and money unit, minimum points, discount caps/basis, credit lifetime, validity window, publication and activation details, creator. |
| Business identity | policy_version is unique. At most one Policy is active per currency; historical versions remain available for existing credits and quotes. |
| Status / lifecycle | Unpublished drafts start inactive; only approved published terms may be activated. Published/referenced terms are immutable and changes create a new version. Retirement does not reprice valid holds. |

**Entity: Loyalty Point (loyalty_points)**

| Field | Value |
| --- | --- |
| Purpose | Records an immutable conditional loyalty ledger event; positive entries also identify individual credit lots. |
| Key business attributes | Member, optional Order, Policy, entry type, signed point quantity, optional credit expiry, redemption/source references, event key and manual-correction actor/reason. |
| Business identity | event_key is unique per business event. Each entry has a loyalty_point_id; at most one redeem debit is recorded per Redemption. |
| Status / lifecycle | Entry types are earn, redeem, adjustment and expiry, not workflow states. Posted entries are immutable; holds do not post debits. Spendable points exclude holds and overdue credits. No refund-restoration entry type is included. |

**Entity: Loyalty Redemption (loyalty_redemptions)**

| Field | Value |
| --- | --- |
| Purpose | Records a conditional retail checkout's point hold and frozen point-to-money discount quote. |
| Key business attributes | Reservation reference, Order, Member, Policy, points requested, conversion/cap/basis snapshots, discount amount, currency, hold deadline, accepted payment and terminal evidence. |
| Business identity | reservation_key is unique. An Order has at most one reserved/redeemed generation, while released generations remain in history. |
| Status / lifecycle | reserved → redeemed or released; both terminal states are irreversible. Accepted payment posts one debit; eligible unpaid release changes availability without a compensating credit. The design excludes refund-driven restoration. |

**Entity: Loyalty Allocation (loyalty_allocations)**

| Field | Value |
| --- | --- |
| Purpose | Identifies the fixed source-credit quantity used by a conditional checkout hold or an actual point debit. |
| Key business attributes | Positive credit source, optional Redemption, optional debit entry, allocated quantity, creation time and consumption time. |
| Business identity | loyalty_allocation_id identifies the slice. Separate unique redemption/credit and debit/credit pairs prevent duplicate source allocation for each applicable operation. |
| Status / lifecycle | No independent status. Held/released checkout slices have no debit; redemption attaches a debit and consumption time once. Expiry/negative adjustments create consumed slices directly. Released slices count as neither held nor consumed; settled slices are immutable. |
