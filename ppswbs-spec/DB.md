## Scope and reading conventions

Design status: Restricted logical draft. Giữ nguyên form entity: Column / Type / Constraints, Indexes và Rules. Chỉ bổ sung trường/quan hệ trên bảng ban đầu, không thêm entity. Chưa phải migration được duyệt.

Sources:

- [ERD hiện tại — sơ đồ chính](../documents/ERD.md): nguồn danh sách bảng.
- [Report 2 — Scope](../documents/docs/report-2-project-management-plan/sections/02-i-project-overview/01-scope-purpose.md), [Report 3 — Use Cases](../documents/docs/report-3-software-requirement-specification/sections/03-i-overall-requirements/04-user-requirements/02-use-cases.md) và [Business Rules](../documents/docs/report-3-software-requirement-specification/sections/07-v-requirement-appendix/01-business-rules.md): baseline người dùng chọn.
- [General Spec](spec-general.md), [Auth Data Model](features/001-member-authentication/data-model.md), [V1 Migration](../ppswbs_backend/src/main/resources/db/migration/V1__init_member_auth.sql): identity boundary và schema thực tế.
- [Settlement](../documents/docs/report-3-software-requirement-specification/assets/diagrams/details/25-workshop-checkin-settlement-detail.puml), [Custody](../documents/docs/report-3-software-requirement-specification/assets/diagrams/details/26-continuation-custody-detail.puml), [Report 1 — Limitations](../documents/docs/report-1-project-introduction/sections/07-v-project-scope-limitations/02-limitations-exclusions.md): kiểm tra nguồn và xung đột.

Scope decisions:

- Người dùng gọi bộ ban đầu là 24 bảng; sơ đồ chính ERD.md thực tế có **23 bảng**. Giữ đúng 23 tên này; không tự thêm bảng thứ 24 hoặc các bảng nối ở sơ đồ bổ sung.
- Retail/custom dùng chung orders với order_type; payments/delivery tham chiếu order_id cho cả hai. Booking-image request vẫn riêng, không tự biến thành manufacturing.
- Một role/user và primary gemstone/attachment có FK; nhiều giá trị còn lại dùng JSON hoặc để TBD. ID trong JSON **không phải SQL FK**, không có PK/unique riêng từng phần tử.
- PK = khóa chính, FK = khóa ngoại, UK/unique = khóa duy nhất. DATETIME precision/UTC và JSON schema/version/retention/concurrency là quyết định feature plan; slot local time dùng branch timezone.
- audit_trail giữ redacted evidence của chính record/domain, append với locking/validation; không central audit store, không token/credential/ảnh/raw AI text hoặc bản sao recipient PII đã erase. JSON không tự enforce immutability.
- Đây là mô hình logical giới hạn. Không xóa/đổi các bảng policy/token/audit đã có trong applied migration V1; users/workshop_registration mapping sang members/accounts/workshop_bookings vẫn TBD.
- ERD.md chỉ được đọc để lấy tên bảng. Các cạnh/thuộc tính cũ chưa đồng bộ với lần sửa DB này; dùng phần Relationships for ERD bên dưới khi vẽ lại.

## Users and roles

Design status: Draft within the original table set; Report 2/3 baseline, unresolved source conflicts and physical mapping remain TBD where identified.

### users

| Column | Type | Constraints / description |
| --- | --- | --- |
| `user_id` | BIGINT | Primary key; logical identity referenced by this document's user foreign keys. |
| `external_user_id` | VARCHAR(128) | Required unique Firebase UID, obtained only from a server-verified identity token. Never derived from email. |
| `email` | VARCHAR(255) | Nullable contact/profile snapshot; not a unique identity or account-merge key. |
| `email_verified` | BOOLEAN | Required; defaults to `FALSE`. Snapshot of Firebase email verification, not proof that a booking's contact email was confirmed. |
| `display_name` | VARCHAR(255) | Nullable basic profile/display name. |
| `phone_number` | VARCHAR(30) | Nullable profile phone; stored as text. Required contact fields are enforced by the booking/order flow, not by identity provisioning. |
| `address` | TEXT | Nullable profile address proposed for UC-06. It is not an address book and never substitutes for an order-specific delivery snapshot. |
| `photo_url` | TEXT | Nullable profile-image URL or storage reference. |
| `status` | VARCHAR(30) | Required. Proposed account values: `active`, `suspended`; default `active` for a newly provisioned account. Member entitlement still requires active role and current policy acceptance. Mapping to existing Member states is `TBD`. |
| `role_id` | BIGINT | Required foreign key to `roles.role_id`. Proposed one current business role per user; reconciliation with multi-role authentication remains TBD. |
| `role_granted_by_user_id` | BIGINT | Nullable foreign key to `users.user_id`. Null for approved system Member provisioning; actor for privileged role assignment. |
| `role_granted_at` | DATETIME | Required current role-grant time; retain earlier changes in audit_trail. |
| `policy_acceptances` | JSON | Nullable append-only policy type/version/content digest/reference, accepted_at, locale and correlation snapshots; effective Terms/Privacy pair required for entitlement. Embedded policy references have no SQL FK. |
| `cart_snapshot` | JSON | Nullable current Member cart: supported product IDs/options, positive quantities and revision. Application-validated references, not SQL FKs; whole cart revalidated at checkout. |
| `audit_trail` | JSON | Nullable append-only redacted record/domain evidence: stable event key, actor context, time, reason/state change and correlation. Application locking/validation required; embedded actor IDs are not SQL FKs, and JSON does not enforce immutable events. |
| `created_at` | DATETIME | Required account-creation timestamp. |
| `updated_at` | DATETIME | Required last-update timestamp. |

Indexes:

- Unique (external_user_id); non-unique (email); (role_id, status); (role_granted_by_user_id).

Rules:

- Một current role/user qua role_id là proposal giản lược; khác multi-role account_roles của auth feature, reconciliation vẫn TBD. Không thay schema đang chạy.
- Firebase sở hữu credential/token; UID unique, email không unique và không dùng tự gộp identity. Guest không cần users giả.
- Policy acceptance giữ phiên bản/content/time và earlier history trong JSON; server kiểm tra effective pair/role/status/entitlement. ID bên trong JSON không có FK.
- Cart JSON giữ selection/revision, không cam kết giá/stock; checkout revalidate toàn giỏ và tạo order/items nguyên tử.

### roles

| Column | Type | Constraints / description |
| --- | --- | --- |
| `role_id` | BIGINT | Primary key. |
| `role_code` | VARCHAR(50) | Required unique stable business-role code. Working-agreement candidates: `MEMBER`, `STAFF`, `OWNER`, `ADMIN_TECHNICAL`; reconciliation with Report 3 remains `TBD`. |
| `role_name` | VARCHAR(100) | Required human-readable role label; changing the label does not change the role code or permissions. |
| `description` | TEXT | Nullable description of the role's business responsibility and boundary. |
| `is_active` | BOOLEAN | Required; proposed default `TRUE`. An inactive role cannot grant authorization under this proposal. |
| `permission_codes` | JSON | Required proposed array of approved operation codes, default empty. Code catalogue/role mapping remain TBD; account, ownership and branch checks still apply. |
| `updated_by_user_id` | BIGINT | Nullable foreign key to `users.user_id`. Authorised role-definition actor; null for bootstrap. |
| `audit_trail` | JSON | Nullable append-only redacted record/domain evidence: stable event key, actor context, time, reason/state change and correlation. Application locking/validation required; embedded actor IDs are not SQL FKs, and JSON does not enforce immutable events. |
| `created_at` | DATETIME | Required role-creation timestamp. |
| `updated_at` | DATETIME | Required last-update timestamp. |

Indexes:

- Unique (role_code); (is_active, role_code); (updated_by_user_id).

Rules:

- Role → users là 1–N trong proposal này; referenced role không xóa. permission_codes dùng allowlist được duyệt, không bỏ ownership/account checks.
- Mapping Member/Staff/Manager/Admin với MEMBER/STAFF/OWNER/ADMIN_TECHNICAL và branch authority còn TBD. Role changes cần actor/audit.

## Loyalty points

Design status: Draft within the original table set; Report 2/3 baseline, unresolved source conflicts and physical mapping remain TBD where identified.

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

- Unique (event_key); (member_user_id, created_at, loyalty_point_id); (order_id, entry_type); (recorded_by_user_id).

Rules:

- Ledger immutable: sum points_delta là balance; earn dương, redeem âm, adjustment có reason/actor; event_key unique.
- Không mutable points balance trên users. Order giữ money/rate snapshots; points reserve/debit/compensating entry phải atomic/idempotent, exact policy vẫn TBD.
- Report 2/3 có loyalty, Report 1 hoãn; conversion/caps/expiry và source reconciliation TBD.

## Product catalogue, orders, payment and delivery

Design status: Draft within the original table set; Report 2/3 baseline, unresolved source conflicts and physical mapping remain TBD where identified.

### products

| Column | Type | Constraints / description |
| --- | --- | --- |
| `product_id` | BIGINT | Primary key. |
| `category_id` | BIGINT | Required foreign key to `categories`; assumes one category per product. |
| `product_name` | VARCHAR(255) | Required catalogue name displayed to customers. |
| `description` | TEXT | Nullable product information displayed on the product-detail page. |
| `option_schema` | JSON | Proposed nullable versioned definition of supported retail size/engraving choices and validation limits; null means no options. Exact schema and option pricing remain `TBD`; this is not variant-level inventory. |
| `unit_price` | DECIMAL(15,2) | Required current selling price; must be non-negative. Exact pricing rules remain `TBD`. |
| `currency` | CHAR(3) | Required currency code, for example `VND`. Approved currency/default remains `TBD`. |
| `available_to_sell_quantity` | INT | Required; defaults to `0`; must be non-negative. Manually maintained sale quantity, not a warehouse inventory balance or a real-time guarantee. |
| `is_published` | BOOLEAN | Required; defaults to `FALSE`. Controls visibility in the public catalogue. |
| `audit_trail` | JSON | Nullable append-only redacted record/domain evidence: stable event key, actor context, time, reason/state change and correlation. Application locking/validation required; embedded actor IDs are not SQL FKs, and JSON does not enforce immutable events. |
| `created_at` | DATETIME | Required creation timestamp. |
| `updated_at` | DATETIME | Required last-update timestamp. |

Indexes:

- (is_published, category_id); (category_id).

Rules:

- Chỉ published product/category hợp lệ được chọn. Quantity là số lượng bán cập nhật thủ công, không warehouse inventory.
- Checkout kiểm tra cả giỏ/options/giá/quantity; reserve/deduct/release nguyên tử và không trừ hai lần. Order snapshots không đổi theo catalogue.
- Không xóa referenced product. Size-specific stock chưa mô hình hóa; option_schema không tạo variant stock records.

### categories

| Column | Type | Constraints / description |
| --- | --- | --- |
| `category_id` | BIGINT | Primary key. |
| `category_name` | VARCHAR(100) | Required category label for grouping/filtering catalogue products. Proposed unique value under the chosen database collation. |
| `description` | TEXT | Nullable explanation of the category. |
| `is_active` | BOOLEAN | Required; defaults to `TRUE`. Proposed flag for enabling the category in public catalogue navigation. |
| `audit_trail` | JSON | Nullable append-only redacted record/domain evidence: stable event key, actor context, time, reason/state change and correlation. Application locking/validation required; embedded actor IDs are not SQL FKs, and JSON does not enforce immutable events. |
| `created_at` | DATETIME | Required creation timestamp. |
| `updated_at` | DATETIME | Required last-update timestamp. |

Indexes:

- Unique (category_name) under selected collation; (is_active, category_name).

Rules:

- Một category/product theo proposal; không tự thêm hierarchy/multi-category. Inactive category ảnh hưởng catalogue mới, không sửa order history.

### orders

| Column | Type | Constraints / description |
| --- | --- | --- |
| `order_id` | BIGINT | Primary key. |
| `order_code` | VARCHAR(64) | Required unique customer-facing order reference. |
| `member_user_id` | BIGINT | Required foreign key to `users.user_id`. Both retail and custom manufacturing require an authenticated eligible Member. |
| `location_id` | BIGINT | Proposed required foreign key to `workshop_locations.location_id`: responsible retail/custom branch for preparation and fulfilment, and retail promotion scope. Allocation and queue scope remain `TBD`; never derive this branch from a recipient address. |
| `status` | VARCHAR(30) | Required type-specific state. Retail: pending_payment/expired/paid/preparing/ready_for_pickup/picked_up/prepared_for_carrier/handed_to_carrier. Custom also uses pending_deposit/rejected/in_progress/ready_for_balance. Exact transitions remain TBD. |
| `fulfilment_method` | VARCHAR(20) | Nullable until retail choice after full payment; required pickup/carrier at custom submission. |
| `subtotal_amount` | DECIMAL(15,2) | Required non-negative amount: retail sum of line snapshots; custom original wax-package amount. |
| `voucher_discount_amount` | DECIMAL(15,2) | Required non-negative checkout snapshot; proposed default `0`. Supported only if promotion scope is ratified. |
| `points_redeemed` | BIGINT | Required non-negative checkout snapshot; proposed default `0`. Ledger posting/reservation must be atomic under the approved loyalty policy. |
| `points_discount_amount` | DECIMAL(15,2) | Required non-negative money-value snapshot; proposed default `0`. Conversion rate/cap remain `TBD`. |
| `total_amount` | DECIMAL(15,2) | Required non-negative amount. Retail freezes subtotal minus voucher/points discounts at checkout. Custom finalizes wax-package amount plus actual surcharge before balance payment. Carrier fees excluded; zero-payable completion path remains TBD. |
| `currency` | CHAR(3) | Required currency snapshot; must match the order items and its payment. |
| `hold_expires_at` | DATETIME | Nullable 15-minute provisional retail-quantity/custom-queue hold deadline; not an expiry of a paid order or committed custom queue position. |
| `paid_at` | DATETIME | Nullable until all payable obligations are satisfied: retail full amount, or custom deposit and final balance. Deposit alone is insufficient. |
| `picked_up_by_user_id` | BIGINT | Nullable foreign key to `users`; required when an authorised operational user records customer pickup. This is the recording actor, not the customer. |
| `picked_up_at` | DATETIME | Nullable; required when `status` is `picked_up`. |
| `order_type` | VARCHAR(20) | Required proposed retail/custom discriminator, default retail. Both Member-only; type-specific fields/transitions must be validated. |
| `promotion_id` | BIGINT | Nullable foreign key to `promotions.promotion_id`. At most one promotion/voucher for a retail checkout; null for custom manufacturing. |
| `voucher_code_snapshot` | VARCHAR(64) | Nullable code snapshot, required when a code-based promotion is selected. |
| `promotion_policy_snapshot` | JSON | Nullable immutable rate/cap/eligibility context at checkout. |
| `promotion_usage_state` | VARCHAR(20) | Nullable without promotion; proposed reserved/consumed/released, updated atomically with hold/payment/expiry. |
| `promotion_usage_updated_at` | DATETIME | Nullable last benefit-usage transition time; earlier evidence retained in audit_trail. |
| `workshop_package_id` | BIGINT | Nullable foreign key to `workshop_packages.workshop_package_id`. Required approved wax-package reference for custom; null for retail. Wax identification remains TBD. |
| `design_input_type` | VARCHAR(20) | Nullable for retail; required reference_image/configuration for custom. Image input is not sent to AI to accept/reject/price manufacturing. |
| `reference_image_url` | TEXT | Nullable for retail/configuration or after approved erasure; required at custom image submission. Delete rejected/withdrawn images after processing and accepted images by pickup/handoff under Report 2. |
| `ring_design_id` | BIGINT | Nullable foreign key to `ring_designs.ring_design_id`. Required frozen design for custom configuration input; null for retail/image input. |
| `requested_deadline` | DATE | Nullable date supplied by the Member. |
| `assigned_deadline` | DATE | Nullable for retail or a rejected/unaccepted custom request; required for accepted custom manufacturing. System chooses the earliest valid date when none is requested; calendar/lead-time rules remain `TBD`. |
| `wax_package_price_snapshot` | DECIMAL(15,2) | Nullable for retail; required non-negative original wax-package price for custom, matching its subtotal. |
| `deposit_amount` | DECIMAL(15,2) | Nullable for retail; required custom 50% wax-package deposit snapshot; rounding remains TBD. |
| `actual_surcharge_amount` | DECIMAL(15,2) | Nullable until the final amount is set; non-negative total actual surcharge. Report 2 does not require a line-item surcharge breakdown. |
| `final_balance_amount` | DECIMAL(15,2) | Nullable for retail/unfinalized custom; remaining package amount after deposit plus actual surcharge. |
| `final_amount_set_by_user_id` | BIGINT | Nullable foreign key to `users.user_id`; required Staff/Manager actor when final amount is set. |
| `final_amount_set_at` | DATETIME | Nullable until final-amount recording. |
| `request_description` | TEXT | Nullable custom-manufacturing instructions; not a workshop image-review decision. |
| `deposit_paid_at` | DATETIME | Nullable until accepted verified custom_deposit payment; retail does not use this field. |
| `queue_committed_at` | DATETIME | Nullable until custom deposit commits one queue position. |
| `queue_released_at` | DATETIME | Nullable until custom pickup/carrier handoff releases the committed position. |
| `audit_trail` | JSON | Nullable append-only redacted record/domain evidence: stable event key, actor context, time, reason/state change and correlation. Application locking/validation required; embedded actor IDs are not SQL FKs, and JSON does not enforce immutable events. |
| `created_at` | DATETIME | Required order/checkout creation timestamp under the approved lifecycle. |
| `updated_at` | DATETIME | Required last-update timestamp. |

Indexes:

- Unique (order_code); (member_user_id, created_at); (location_id, order_type, status); (status, hold_expires_at).
- (promotion_id, promotion_usage_state); (ring_design_id); (workshop_package_id); (assigned_deadline, order_type, status); (picked_up_by_user_id); (final_amount_set_by_user_id).

Rules:

- order_type phân biệt retail/custom; cả hai Member-only. Retail cần ≥1 order_items; custom lưu single design/package/amount trên orders và không dùng dòng retail.
- Retail amount/line snapshot bất biến tại checkout; toàn giỏ held tối đa 15 phút và accepted retail_full_payment mới hoàn tất sale.
- Custom auto-accept chỉ khi deadline hợp lệ và queue còn chỗ; cọc 50% gói wax, balance = phần còn lại của gói + actual surcharge. AI không quyết định manufacturing.
- Custom reference image hoặc frozen configuration là hai input riêng; accepted request không tự tạo catalogue product. Wax identity, queue global/per-branch, deadline/rounding/final-amendment policies còn TBD.
- Khi submit custom order, chọn đúng một image/configuration và kiểm tra required fields tương ứng; sau erasure hợp lệ không buộc giữ URL ảnh. Retail không dùng custom deadline/deposit/design fields; custom không có retail items và voucher/points discount phải bằng 0 trong proposal hiện tại.
- Tối đa một promotion/voucher trên retail order; code/policy/discount/usage state giữ ở đây. Kiểm tra active/time/branch/limits và consume/release nguyên tử; custom không tự dùng voucher/points.
- Retail chọn fulfilment sau trả đủ; custom chọn lúc submission nhưng trả đủ deposit/balance trước pickup/handoff. Deposit không tự đặt paid_at. Handoff/pickup releases custom queue; carrier fees ngoài order.
- Không ghi đè expired checkout thành order mới; retain benefit/hold history. Shared retail/custom ownership và billing boundaries cần được chốt trước implementation.

### order_items

| Column | Type | Constraints / description |
| --- | --- | --- |
| `order_item_id` | BIGINT | Primary key. |
| `order_id` | BIGINT | Required foreign key to `orders.order_id`. Retail line only under this proposal; custom stores its single design/package/amount on orders and has no retail lines. |
| `product_id` | BIGINT | Required foreign key to `products` for the catalogue-product retail scope. |
| `product_name_snapshot` | VARCHAR(255) | Required product-name snapshot at checkout; keeps order history readable after a catalogue rename. |
| `line_key` | VARCHAR(128) | Required stable normalized identity of the product plus supported options. Unique with `order_id`; exact canonicalization is `TBD`. |
| `options_snapshot` | JSON | Required validated option snapshot, for example ring size and engraving from UC-43; an empty object means no options. Supported schema/version and price impact remain `TBD`. |
| `quantity` | INT | Required purchased quantity; must be greater than `0`. |
| `unit_price` | DECIMAL(15,2) | Required non-negative selling-price snapshot at checkout, not a live lookup of `products.unit_price`. |
| `line_amount` | DECIMAL(15,2) | Required non-negative line total; must equal `quantity * unit_price`. Proposed stored snapshot; a generated column is an alternative physical implementation. |
| `created_at` | DATETIME | Required timestamp when the checkout line is created. |

Indexes:

- Unique (order_id, line_key); (product_id).

Rules:

- FK order_id luôn bắt buộc; chỉ thuộc order retail. Retail cần ≥1 item bằng transaction check; custom có 0 item.
- Unique order_id/line_key thay order_id/product_id: cùng sản phẩm khác size/engraving có thể là hai dòng.
- quantity > 0, line_amount = quantity × unit_price, currency theo order; snapshot sau checkout bất biến.

### product_imgs

| Column | Type | Constraints / description |
| --- | --- | --- |
| `product_img_id` | BIGINT | Primary key. |
| `product_id` | BIGINT | Required foreign key to `products`. |
| `img_url` | TEXT | Required product-image URL or object-storage path. Stores a media reference, not binary image data. |
| `alt_text` | VARCHAR(255) | Nullable image description for accessibility; required when the image conveys information not already expressed by nearby text. |
| `sort_order` | INT | Required non-negative display position; proposed unique position within a product. |
| `audit_trail` | JSON | Nullable append-only redacted record/domain evidence: stable event key, actor context, time, reason/state change and correlation. Application locking/validation required; embedded actor IDs are not SQL FKs, and JSON does not enforce immutable events. |
| `created_at` | DATETIME | Required image-record creation timestamp. |
| `updated_at` | DATETIME | Required last-update timestamp. |

Indexes:

- Unique (product_id, sort_order).

Rules:

- Nhiều ảnh/product, unique thứ tự; không ảnh reference của customer. Validation/minimum publication/retention còn TBD.

### payments

| Column | Type | Constraints / description |
| --- | --- | --- |
| `payment_id` | BIGINT | Primary key. One online payment attempt or one recorded shop receipt, distinguished by payment_record_type. |
| `payment_code` | VARCHAR(64) | Required unique internal attempt/receipt reference; correlates this record with its payable target. |
| `order_id` | BIGINT | Nullable foreign key to `orders.order_id`. Retail or custom target, validated against order_type and payment_purpose; XOR with booking. |
| `workshop_registration_id` | BIGINT | Nullable foreign key to `workshop_registration.workshop_registration_id`. Workshop deposit or shop-balance target; XOR with order. |
| `payment_purpose` | VARCHAR(30) | Required retail_full_payment/workshop_deposit/workshop_balance/custom_deposit/custom_balance; must match target, record type and frozen amount. |
| `amount` | DECIMAL(15,2) | Required positive requested/received amount matching the approved payable obligation and currency. |
| `currency` | CHAR(3) | Required currency snapshot; must match the payable target. |
| `provider` | VARCHAR(50) | Required VNPay for gateway_attempt; nullable for shop_receipt. Provider-specific payload/dedup scope remain TBD. |
| `provider_transaction_id` | VARCHAR(255) | Nullable until a provider transaction reference is available. Reference format/uniqueness scope remain `TBD` in the gateway contract. |
| `status` | VARCHAR(20) | Required. Online candidates: `pending`, `succeeded`, `failed`, `expired`; shop_receipt is `succeeded` only after authorised actual-receipt recording. No fulfilment state is inferred from a pending record. |
| `verified_at` | DATETIME | Nullable until server-verified gateway evidence; required for accepted online success, null for manually recorded shop receipts. |
| `paid_at` | DATETIME | Nullable until verified online payment or actual shop receipt is recorded. |
| `failure_reason` | TEXT | Nullable safe failure explanation/code. Must not contain gateway secrets, card data or raw sensitive callback payloads. |
| `link_expires_at` | DATETIME | Nullable for shop_receipt; required online link expiry, initiation plus 10 minutes. Separate from hold and email-token expiry. |
| `payment_record_type` | VARCHAR(20) | Required gateway_attempt/shop_receipt: one online attempt or one actually recorded cash/bank-transfer receipt. |
| `payment_method` | VARCHAR(20) | Required proposed vnpay/cash/bank_transfer. Retail/custom obligations use VNPay; detailed workshop balance may use shop receipt. |
| `accepted_at` | DATETIME | Nullable until this payment/receipt is applied once to its target obligation, after required hold/amount/status checks. |
| `recorded_by_user_id` | BIGINT | Nullable foreign key to `users.user_id`. Required authorised Staff actor for shop_receipt; online system confirmation may be null. |
| `confirmation_source` | VARCHAR(20) | Nullable before evidence; proposed ipn/querydr/staff_recorded, matching record type. Return URL never confirms. |
| `gateway_event_key` | VARCHAR(255) | Nullable provider-scoped accepted-evidence key; unique with provider when supplier contract guarantees scope. Exact identity remains TBD. |
| `verification_history` | JSON | Nullable append-only allowlisted callback/QueryDR/late-exception evidence, safe keys/checks/timestamps only. Embedded events have no individual SQL unique constraint; no signatures, tokens, secrets, card data or raw PII. |
| `audit_trail` | JSON | Nullable append-only redacted record/domain evidence: stable event key, actor context, time, reason/state change and correlation. Application locking/validation required; embedded actor IDs are not SQL FKs, and JSON does not enforce immutable events. |
| `created_at` | DATETIME | Required payment-attempt creation timestamp. |
| `updated_at` | DATETIME | Required last-update timestamp. |

Indexes:

- Unique (payment_code); proposed unique (provider, provider_transaction_id) and (provider, gateway_event_key), provider scope TBD.
- (order_id, payment_purpose, created_at); (workshop_registration_id, payment_purpose, created_at); (status, created_at); (recorded_by_user_id).

Rules:

- Chính xác một order_id/workshop_registration_id khác null. order_id dùng cho retail/custom; purpose phải khớp order_type và amount/currency snapshot.
- gateway_attempt cần signed VNPay IPN hoặc signed QueryDR và target hold/state hợp lệ; Return URL không xác nhận. Link 10 phút, hold resource tối đa 15 phút; late success sau release chỉ manual exception.
- shop_receipt chỉ ghi actual workshop balance cash/bank transfer, có Staff actor/time; không fake VNPay/provider/verified_at cho offline receipt.
- Target có nhiều attempts nhưng mỗi nghĩa vụ tiền chỉ áp dụng một lần qua accepted_at + target transaction/state. JSON history không tự bảo đảm dedup; accepted provider reference/key scope và retries còn TBD.
- Unknown callback không có attempt cần technical evidence ngoài ERD; không tạo payment giả. Không card data/refund/accounting; history JSON phải lock khi append và redact/retain theo policy.

### delivery_infors

| Column | Type | Constraints / description |
| --- | --- | --- |
| `delivery_infor_id` | BIGINT | Primary key. Retains the table naming requested in this file. |
| `order_id` | BIGINT | Required unique foreign key to `orders.order_id`; at most one delivery-information record per retail/custom order. |
| `recipient_name` | VARCHAR(255) | Required recipient/contact name before carrier preparation; nullable after approved PII erasure. |
| `recipient_phone` | VARCHAR(30) | Required contact phone before carrier preparation; nullable after approved erasure. Text preserves prefixes and leading zeroes; exact validation remains `TBD`. |
| `delivery_address` | TEXT | Required complete delivery-address snapshot before carrier preparation; nullable after approved erasure. No address-book relationship is assumed. |
| `delivery_note` | TEXT | Nullable recipient delivery instructions. |
| `carrier_name` | VARCHAR(100) | Nullable before handoff; required when an authorised operational user records actual carrier handoff. |
| `handoff_reference` | VARCHAR(255) | Nullable before handoff; required handoff receipt/reference when handoff is recorded. It is evidence, not live shipment tracking. |
| `handed_off_by_user_id` | BIGINT | Nullable foreign key to `users`; required recording operational actor at carrier handoff. |
| `handed_off_at` | DATETIME | Nullable; required actual carrier-handoff timestamp. |
| `audit_trail` | JSON | Nullable append-only redacted record/domain evidence: stable event key, actor context, time, reason/state change and correlation. Application locking/validation required; embedded actor IDs are not SQL FKs, and JSON does not enforce immutable events. |
| `created_at` | DATETIME | Required timestamp when delivery details are saved. |
| `updated_at` | DATETIME | Required last-update timestamp. |

Indexes:

- Unique (order_id); (handed_off_by_user_id, handed_off_at).

Rules:

- order_id required + unique: tối đa một delivery cho retail/custom; pickup không cần row.
- Recipient đầy đủ trước carrier preparation; custom có thể thu thập từ submission. Hoàn tất handoff chỉ khi toàn bộ nghĩa vụ tiền được trả.
- Handoff cần carrier/reference/Staff/time cùng transition order. GHTK manual only, no API/tracking; recipient PII erase sau 30 ngày theo Report 2, audit không giữ địa chỉ đã erase.

## Locations / branches

Design status: Draft within the original table set; Report 2/3 baseline, unresolved source conflicts and physical mapping remain TBD where identified.

### workshop_locations

| Column | Type | Constraints / description |
| --- | --- | --- |
| `location_id` | BIGINT | Primary key; technical surrogate identifier used internally by foreign keys and joins. It has no business meaning, should not be re-used after deletion, and is not the code shown to customers or external systems. |
| `location_code` | VARCHAR(64) | Required unique stable business code for the branch, used in administration, reports, URLs/integrations when a human-readable or externally exchanged identifier is needed. It must remain stable when the branch name or address changes. |
| `location_name` | VARCHAR(255) | Required public branch name. |
| `address_line` | VARCHAR(500) | Required physical address or address snapshot used for display. |
| `city` | VARCHAR(100) | Required city/province. |
| `country_code` | CHAR(2) | Required ISO 3166-1 alpha-2 country code; proposed default `VN`. |
| `phone_number` | VARCHAR(30) | Nullable branch contact phone number. |
| `timezone` | VARCHAR(64) | Required IANA time-zone identifier used to interpret local opening dates/times; proposed default `Asia/Ho_Chi_Minh`. |
| `is_active` | BOOLEAN | Required; proposed default `TRUE`. Inactive locations cannot receive new bookings or promotion applicability. |
| `supported_material_ids` | JSON | Nullable explicit material IDs/availability; null means incomplete configuration, not all materials. Application validates materials; no SQL FK for array elements. |
| `available_workshop_package_ids` | JSON | Nullable explicit offered package IDs, validated against publication/local compatibility; no SQL FK for array elements. |
| `standard_sessions` | JSON | Nullable standard local timeframes/default capacity; dated slots remain authoritative snapshots. |
| `custom_queue_limit` | INT | Nullable until configured; non-negative proposed per-branch custom queue limit. Global versus branch scope remains TBD. |
| `minimum_custom_lead_days` | INT | Nullable until configured; non-negative proposed lead time, calendar/working-day basis TBD. |
| `custom_deadline_exclusions` | JSON | Nullable explicit unavailable custom deadline dates/reasons; not workshop closures. Configuration scope/version policy remains TBD. |
| `audit_trail` | JSON | Nullable append-only redacted record/domain evidence: stable event key, actor context, time, reason/state change and correlation. Application locking/validation required; embedded actor IDs are not SQL FKs, and JSON does not enforce immutable events. |
| `created_at` | DATETIME | Required creation timestamp. |
| `updated_at` | DATETIME | Required last-update timestamp. |

Indexes:

- Unique (location_code); (is_active, city, location_name).

Rules:

- Chi nhánh sở hữu local timezone/session config; slots là dated snapshots. Deactivate thay xóa lịch sử.
- Material/package arrays là logical N–N, application kiểm tra tồn tại/active/compatibility; null không nghĩa hỗ trợ tất cả, không có SQL FK trên phần tử.
- Custom queue/lead days/excluded dates là proposal per-branch. Global policy/source version còn TBD; không tạo branch giả chứa settings.

## Promotions

Design status: Draft within the original table set; Report 2/3 baseline, unresolved source conflicts and physical mapping remain TBD where identified.

### promotions

| Column | Type | Constraints / description |
| --- | --- | --- |
| `promotion_id` | BIGINT | Primary key. |
| `promotion_name` | VARCHAR(255) | Required campaign/promotion name. |
| `description` | TEXT | Nullable customer-facing explanation and conditions. |
| `voucher_code` | VARCHAR(64) | Nullable unique authoritative code, at most one code per promotion under the restricted proposal. |
| `discount_type` | VARCHAR(20) | Required. Proposed values: `percentage`, `fixed_amount`. |
| `discount_value` | DECIMAL(15,2) | Required positive value; percentage must be at most `100`, fixed amount is expressed in `currency`. |
| `currency` | CHAR(3) | Required for a fixed amount or monetary threshold/cap; may be null for a percentage with no monetary conditions. Must match an eligible order whenever monetary conditions are present. |
| `minimum_order_amount` | DECIMAL(15,2) | Nullable non-negative minimum eligible merchandise amount; the precise eligibility basis remains `TBD`. |
| `maximum_discount_amount` | DECIMAL(15,2) | Nullable positive monetary cap for a percentage promotion; not used for a fixed discount under this proposal. |
| `starts_at` | DATETIME | Required promotion-validity start timestamp. |
| `ends_at` | DATETIME | Required end timestamp; must be later than `starts_at`. Proposed validity interval includes the start and excludes the end. |
| `is_active` | BOOLEAN | Required; proposed default `FALSE`. Manual activation does not override the validity window. |
| `created_by_user_id` | BIGINT | Required foreign key to `users`; authorised campaign-creation actor. Exact managing role remains `TBD`. |
| `total_usage_limit` | INT | Nullable positive configured limit; null semantics must be approved, not assumed unlimited. |
| `per_member_usage_limit` | INT | Nullable positive configured Member limit; policy remains `TBD`. |
| `audit_trail` | JSON | Nullable append-only redacted record/domain evidence: stable event key, actor context, time, reason/state change and correlation. Application locking/validation required; embedded actor IDs are not SQL FKs, and JSON does not enforce immutable events. |
| `created_at` | DATETIME | Required creation timestamp. |
| `updated_at` | DATETIME | Required last-update timestamp. |

Indexes:

- Unique (voucher_code), allowing null; (is_active, starts_at); (created_by_user_id, created_at).

Rules:

- Mỗi promotion tối đa một authoritative voucher code; nhiều mã/campaign chưa chuẩn hóa. Null code không tự cấp automatic entitlement.
- orders.promotion_id và immutable code/policy/amount + usage state thay redemption row. Active/time/branch/limits được kiểm tra; đổi promotion không reprice order cũ.
- GBR-10 cho một voucher/order kết hợp points; stacking/limits/rounding và scope conflict Report 1 còn TBD.

### promotion_locations

| Column | Type | Constraints / description |
| --- | --- | --- |
| `promotion_id` | BIGINT | Required foreign key to `promotions.promotion_id`; part of composite primary key. |
| `location_id` | BIGINT | Required foreign key to `workshop_locations.location_id`; part of composite primary key. |
| `is_active` | BOOLEAN | Required proposed applicability flag, default TRUE. Deactivate for new eligibility while retaining historical links. |
| `audit_trail` | JSON | Nullable append-only redacted record/domain evidence: stable event key, actor context, time, reason/state change and correlation. Application locking/validation required; embedded actor IDs are not SQL FKs, and JSON does not enforce immutable events. |
| `created_at` | DATETIME | Required timestamp when the promotion is enabled for the location. |
| `created_by_user_id` | BIGINT | Required foreign key to `users`; identifies the authenticated user who created this promotion-to-branch assignment. It provides accountability for who enabled the promotion at the branch, supports audit/history and investigation of accidental or unauthorised scope changes, and must refer to an authorised operational user. It does not determine promotion eligibility and must not be used as the promotion owner. |

Primary key: `(promotion_id, location_id)`.

Indexes:

- Composite PK (promotion_id, location_id); (location_id, promotion_id); (created_by_user_id).

Rules:

- PK ghép promotion_id/location_id chống gán trùng; N–N promotions/locations qua bảng nối có sẵn.
- No row không nghĩa global. Eligibility cần active link/master/time/rules; giữ historical row và deactivate thay delete.
- created_by_user_id là actor bật branch applicability, không phải customer hay campaign owner.

## Workshop booking, slots, packages and exceptions

Design status: Draft within the original table set; Report 2/3 baseline, unresolved source conflicts and physical mapping remain TBD where identified.

### workshop_registration

| Column | Type | Constraints / description |
| --- | --- | --- |
| `workshop_registration_id` | BIGINT | Primary key. Logical booking identity referenced by `payments.workshop_registration_id`. |
| `booking_code` | VARCHAR(64) | Required unique booking/lookup reference. Existing persistence creates a code with the booking; its public exposure/QR issuance follows the approved confirmation flow. |
| `member_user_id` | BIGINT | Nullable foreign key to `users`: null for a Guest booking, set for a Member booking or an explicitly imported eligible Guest booking. |
| `slot_id` | BIGINT | Required foreign key to `slots`; records the selected dated location/session. |
| `workshop_package_id` | BIGINT | Required foreign key to `workshop_packages`. |
| `ring_design_id` | BIGINT | Nullable foreign key to `ring_designs` for a permitted catalogue/configured-design selection. In-person consultation may leave it null; it does not link a custom image request. |
| `design_path` | VARCHAR(30) | Nullable proposed path: `catalogue_model`, `configured_design`, `booking_image`, `in_person`. `booking_image` is Member-only under Report 3 and links through `custom_design_requests.workshop_registration_id`; reconcile the former independent-review boundary before implementation. |
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
| `hold_expires_at` | DATETIME | Proposed nullable temporary seat-hold deadline. Report 2/3 specifies at most 15 minutes for a payment-linked hold; pre-email holds and start-time alignment remain `TBD` in the booking plan. Never use email-token expiry or payment-link expiry interchangeably. |
| `booking_channel` | VARCHAR(20) | Proposed required channel, default `mland`; candidate `klook` identifies the separately authenticated partner flow. Channel never bypasses capacity/confirmation validation. |
| `checked_in_by_user_id` | BIGINT | Nullable foreign key to `users`; authorised Staff actor for recorded group check-in. |
| `checked_in_at` | DATETIME | Nullable; required when check-in is recorded. |
| `actual_participant_count` | INT | Nullable until check-in; non-negative actual participating count, independent of the booked count. |
| `parent_registration_id` | BIGINT | Nullable foreign key to `workshop_registration.workshop_registration_id`. Staff continuation; no self-reference or cycle. |
| `created_by_user_id` | BIGINT | Nullable foreign key to `users`; records the authenticated creation actor where applicable and is required for a Staff-created continuation. Guest creation does not require a user row. |
| `invoice_code` | VARCHAR(64) | Nullable unique invoice reference; required when a one-per-booking package invoice is issued. Not a statutory tax invoice. |
| `invoice_state` | VARCHAR(20) | Required proposed draft/issued/settled/void, independent of booking and email state; transitions remain TBD. |
| `invoice_issued_at` | DATETIME | Nullable until package invoice issuance. |
| `invoice_adjustments` | JSON | Nullable append-only records: stable event key, signed amount/reason, Staff actor/time, customer consent decision/method/evidence, and required Owner/Manager approval. Embedded actor IDs are not SQL FKs. |
| `email_confirm_token_hash` | VARCHAR(128) | Nullable current Guest one-time token hash; never plaintext. Pending Guest flow requires one hash; resend/rotation rules remain TBD. |
| `email_confirmation_expires_at` | DATETIME | Nullable unless email confirmation pending; required token deadline, 15 minutes per auth feature. Independent of payment hold. |
| `email_confirmed_at` | DATETIME | Nullable until valid contact-email confirmation; not a deposit-payment timestamp. |
| `qr_issued_at` | DATETIME | Nullable until required email/deposit/booking gates pass. QR access/lookup-proof strategy remains TBD; no raw reusable access secret. |
| `external_booking_id` | VARCHAR(255) | Nullable unique Klook reference in the proposed single supplier account; multi-account uniqueness requires scope revision. |
| `external_linked_at` | DATETIME | Nullable partner-link timestamp. |
| `partner_sync_state` | VARCHAR(20) | Nullable current pending/synced/failed summary, not a complete retry/inbound-message history. |
| `custody_items` | JSON | Nullable bounded item/custody histories: stable item code, description/photo reference, location, intake/release actor/time, continuation reference and verification method. Application validates references; no independent SQL PK/FK per item/cycle. |
| `audit_trail` | JSON | Nullable append-only redacted record/domain evidence: stable event key, actor context, time, reason/state change and correlation. Application locking/validation required; embedded actor IDs are not SQL FKs, and JSON does not enforce immutable events. |
| `created_at` | DATETIME | Required booking-creation timestamp. |
| `updated_at` | DATETIME | Required last-update timestamp. |

Indexes:

- Unique (booking_code), (invoice_code); proposed unique (external_booking_id) for one supplier account.
- (member_user_id, created_at); (slot_id, status); (workshop_package_id); (ring_design_id); (canonical_email, member_user_id); (parent_registration_id); (confirmation_state, email_confirmation_expires_at); (created_by_user_id); (checked_in_by_user_id).

Rules:

- Guest member_user_id null; import cần verified matching email, explicit confirmation và entitlement, không tự nối theo email.
- Package chọn trước slot/design theo GBR-01; group capacity theo tổng participant_count, held/released nguyên tử. Location suy qua slot, không thêm location FK dư.
- Email hash/expiry/time nằm trên booking logical; email gate khác deposit gate. Pending Guest chưa payment; pre-email hold timing/resend/rotation còn TBD. Applied confirmation table không bị xóa.
- Invoice một/booking qua code/state và amount/deposit snapshots; adjustments JSON chứa event key, reason/actor/time, consent và required approval. Effective adjustment bất biến, correction append mới.
- Shop settlements qua payments.workshop_balance; chỉ accepted deposit/receipt giảm unpaid balance. Fee 100,000 VND/additional contributing person và waiver authority cần reconcile nguồn.
- Staff tạo continuation capacity-checked, không self/cycle/auto-hold-next/reuse-deposit. custody_items giữ item/intake/release proof, không PK/FK riêng.
- Klook external code/link time/sync summary không thay reliable queue/protocol; no cancellation consumption. Embed invoice/custody/consent cần owner/transaction/concurrency design, không claim đã chuẩn hóa đầy đủ.

### slots

| Column | Type | Constraints / description |
| --- | --- | --- |
| `slot_id` | BIGINT | Primary key. |
| `location_id` | BIGINT | Required foreign key to `workshop_locations`. |
| `slot_date` | DATE | Required local operating date at the location. |
| `start_time` | TIME | Required local session start time. |
| `end_time` | TIME | Required local session end time; later than `start_time` for the documented same-day sessions. |
| `capacity` | INT | Nullable until configured; when set, must be non-negative. Null means configuration required; zero means no bookable seats. |
| `is_open` | BOOLEAN | Required; proposed default `FALSE` until the dated session is intentionally opened for booking. |
| `assigned_staff_user_id` | BIGINT | Nullable foreign key to `users.user_id`. One primary authorised facilitator; overlap/scope rules remain TBD. |
| `additional_staff_assignments` | JSON | Nullable extra Staff IDs/assignment context, application validation only; no SQL FK on elements. |
| `audit_trail` | JSON | Nullable append-only redacted record/domain evidence: stable event key, actor context, time, reason/state change and correlation. Application locking/validation required; embedded actor IDs are not SQL FKs, and JSON does not enforce immutable events. |
| `created_at` | DATETIME | Required session-creation timestamp. |
| `updated_at` | DATETIME | Required last-update timestamp. |

Indexes:

- Unique (location_id, slot_date, start_time); (slot_date, is_open, location_id); (assigned_staff_user_id).

Rules:

- Slot là dated occurrence; unique location/date/start. standard_sessions trong branch chỉ mẫu, không tự sửa session đã có booking.
- Giờ tài liệu: 09:30–12:00, 13:00–15:30, 16:00–18:30. Open/configured capacity/exceptions và tổng participant_count/eligible holds quyết định availability.
- Primary Staff có FK; extra Staff JSON chỉ application references. Permission/overlap và Staff assignment versus HR scope còn TBD.

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
| `deposit_percentage` | DECIMAL(5,2) | Required proposed setting; normal workshop deposit follows the Report 3 50% baseline. Other percentages/material precedence are not activated without approved policy. |
| `img_url` | TEXT | Nullable package-display image URL or storage reference. |
| `is_published` | BOOLEAN | Required; proposed default `FALSE`. Controls public discoverability and eligibility for new bookings. |
| `material_id` | BIGINT | Nullable foreign key to `materials.material_id`. Optional primary included material; not necessarily the only selectable material. |
| `supported_material_ids` | JSON | Nullable additional compatible material IDs/conditions; application checks all references. No SQL FK on array elements. |
| `package_type` | VARCHAR(30) | Proposed required workshop/wax classification; approved wax-package identity and overlap of purposes remain TBD. |
| `audit_trail` | JSON | Nullable append-only redacted record/domain evidence: stable event key, actor context, time, reason/state change and correlation. Application locking/validation required; embedded actor IDs are not SQL FKs, and JSON does not enforce immutable events. |
| `created_at` | DATETIME | Required package-creation timestamp. |
| `updated_at` | DATETIME | Required last-update timestamp. |

Indexes:

- Unique (package_code); (is_published, package_name); (material_id).

Rules:

- Primary material FK optional; nhiều material options ở JSON cần application validation, không N–N SQL integrity.
- Price/name/deposit snapshot trên booking/order giữ lịch sử; normal booking cọc 50% theo GBR-02. Group-price basis/wax classification còn TBD.
- Publication không tự tạo capacity/branch offering. AI không publish giá; approved configuration source và price-decision history chưa chuẩn hóa đầy đủ.

### worshop_exceptions

| Column | Type | Constraints / description |
| --- | --- | --- |
| `workshop_exception_id` | BIGINT | Primary key. The requested table spelling `worshop_exceptions` is retained; naming correction is a separate decision. |
| `location_id` | BIGINT | Nullable foreign key to `workshop_locations`; null with no slot means all locations on the exception date under this proposal. |
| `slot_id` | BIGINT | Nullable foreign key to `slots`; set for a single-session exception and null for a whole-day scope. |
| `exception_date` | DATE | Required affected local operating date. A multi-day holiday uses one dated record per day under this proposal. |
| `is_closed` | BOOLEAN | Required; proposed default `TRUE`. Blocks new booking in the matching scope. |
| `capacity_override` | INT | Nullable non-negative replacement capacity for an open affected session; not used when `is_closed` is true. |
| `start_time_override` | TIME | Nullable replacement session start; if used, an end-time override and a specific `slot_id` are also required. |
| `end_time_override` | TIME | Nullable replacement session end; must be later than the corresponding start override. |
| `reason` | TEXT | Required holiday/off-day/schedule-change explanation. |
| `is_active` | BOOLEAN | Required; proposed default `TRUE`. Inactive exceptions do not affect new availability decisions. |
| `created_by_user_id` | BIGINT | Required foreign key to `users`; authorised schedule-exception actor. |
| `audit_trail` | JSON | Nullable append-only redacted record/domain evidence: stable event key, actor context, time, reason/state change and correlation. Application locking/validation required; embedded actor IDs are not SQL FKs, and JSON does not enforce immutable events. |
| `created_at` | DATETIME | Required exception-creation timestamp. |
| `updated_at` | DATETIME | Required last-update timestamp. |

Indexes:

- (exception_date, is_active, location_id); (slot_id, is_active); (created_by_user_id).

Rules:

- Global/date: location/slot null; location/date: location có, slot null; slot scope: cả hai có và location/date phải khớp slot.
- Một active exception/scope/date cần conditional key/transaction; nullable tuple unique thông thường chưa đủ. Precedence/safe reschedule còn TBD; không silently cancel/move paid booking.
- Custom deadline exclusions khác workshop schedule closure. Giữ spelling hiện tại.

## Custom design request

Design status: Draft within the original table set; Report 2/3 baseline, unresolved source conflicts and physical mapping remain TBD where identified.

### custom_design_requests

| Column | Type | Constraints / description |
| --- | --- | --- |
| `custom_design_request_id` | BIGINT | Primary key. |
| `member_user_id` | BIGINT | Required foreign key to `users`. Only a Member can submit a request. |
| `workshop_registration_id` | BIGINT | Proposed required foreign key to `workshop_registration.workshop_registration_id`. The requesting Member must own the eligible booking; one booking may have several historical submissions. |
| `request_description` | TEXT | Required description entered by the Member. |
| `img_url` | TEXT | Required reference-image URL or object-storage path at submission; nullable after approved media erasure. One image per request is the retained draft assumption; Report 3 does not settle image count. Do not persist the AI provider's image input after processing. |
| `estimated_value` | DECIMAL(15,2) | Proposed nullable non-negative reviewed estimate in VND; required before selecting the human review tier. Valuation basis/version remain `TBD`; AI does not publish a selling price. |
| `review_tier` | VARCHAR(20) | Proposed nullable snapshot: `staff` for value at most 3,000,000 VND, `manager` above it under GBR-05. Role-code mapping remains `TBD`. |
| `submitted_at` | DATETIME | Proposed required submission time; preserved even if media is erased. |
| `status` | VARCHAR(20) | Required; defaults to `need_review`. Allowed values: `need_review`, `accepted`, `rejected`. |
| `reviewed_by_user_id` | BIGINT | Nullable foreign key to `users`; set when the request is accepted or rejected. |
| `reviewed_at` | DATETIME | Nullable; set when the request is accepted or rejected. |
| `review_reason` | TEXT | Required when `status` is `rejected`; not required when `status` is `accepted`. |
| `candidate_features` | JSON | Nullable minimal extracted component/material suggestions; structured schema and retention classification remain `TBD`. No image bytes/base64 or raw prompt/response. |
| `analysis_status` | VARCHAR(20) | Nullable before processing; proposed pending/succeeded/failed latest AI-attempt summary, not a business decision. |
| `analysis_model` | VARCHAR(100) | Nullable configured Gemini model/version snapshot. |
| `analyzed_at` | DATETIME | Nullable latest processing completion time; individual attempt history is not normalized. |
| `analysis_expires_at` | DATETIME | Nullable approved expiry for derived AI data; erase candidate payload independently from retained human decision context. |
| `decision_context` | JSON | Nullable before terminal review; required safe value/rule/tier context on accepted/rejected outcome. No raw AI image/text in long-lived evidence. |
| `applied_rules_version` | VARCHAR(64) | Nullable rule-version snapshot; exact valuation source remains TBD. |
| `audit_trail` | JSON | Nullable append-only redacted record/domain evidence: stable event key, actor context, time, reason/state change and correlation. Application locking/validation required; embedded actor IDs are not SQL FKs, and JSON does not enforce immutable events. |
| `created_at` | DATETIME | Required submission timestamp. |
| `updated_at` | DATETIME | Required last-update timestamp. |

Indexes:

- (status, created_at); (member_user_id, created_at); (workshop_registration_id, created_at); (reviewed_by_user_id, reviewed_at); (analysis_expires_at).

Rules:

- Giữ tên bảng; Report 3 Member booking-image request có booking FK. Khác independent-review scope của ERD cũ được ghi trong giới hạn.
- Latest AI result + một terminal human decision nằm trong cùng row; no raw image/prompt/text lưu lâu, no direct order/payment conversion.
- Staff review estimate ≤ 3,000,000 VND, Manager trên ngưỡng theo GBR-05; valuation/role mapping TBD. Outcome giữ reviewer/reason/time/decision context.
- Submission bất biến, need_review → accepted/rejected; rejected không re-review, tạo request mới. Multi-review/AI-attempt history chưa chuẩn hóa; derived payload expiry riêng với human evidence.

## Ring designs and component catalogue

Design status: Draft within the original table set; Report 2/3 baseline, unresolved source conflicts and physical mapping remain TBD where identified.

### ring_designs

| Column | Type | Constraints / description |
| --- | --- | --- |
| `ring_design_id` | BIGINT | Primary key. Proposed identity of a specific design/version. |
| `design_name` | VARCHAR(255) | Required display name for a catalogue model; nullable for a customer's unnamed configured design. |
| `description` | TEXT | Nullable catalogue/design explanation. |
| `design_type` | VARCHAR(30) | Required. Proposed values: `catalogue_model`, `configured_design`; does not include image-based custom requests. |
| `created_by_user_id` | BIGINT | Nullable foreign key to `users`; authorised catalogue author or Member configuration creator when authenticated. Null can represent a permitted Guest workshop configuration. |
| `source_design_id` | BIGINT | Nullable foreign key to `ring_designs.ring_design_id`. Source model/previous version; no self-reference or cycle. |
| `material_id` | BIGINT | Required foreign key to `materials.material_id`. One base material under the constrained proposal. |
| `shape_id` | BIGINT | Required foreign key to `shape.shape_id`. One base ring form, not gemstone cut or ring size. |
| `ring_size` | VARCHAR(30) | Nullable selected ring size. Size system, allowed values and the point at which it becomes required remain `TBD`; it is not a free-text substitute for validated size options. |
| `engraving_text` | VARCHAR(255) | Proposed nullable workshop engraving choice from UC-33; allowed characters, length, placement, compatibility and estimate contribution remain `TBD`. Snapshot it with the frozen design. |
| `img_url` | TEXT | Nullable catalogue preview/model image. Not a Member custom-request reference image. |
| `component_snapshot` | JSON | Nullable draft, required frozen component/quantity/attribute/price snapshot; primary fields must match it. Additional component types are application-validated embedded references, not SQL FKs. |
| `estimated_price` | DECIMAL(15,2) | Nullable non-negative system-calculated estimate using approved component/price rules. Not an AI-generated price, fixed order price or amount charged. |
| `currency` | CHAR(3) | Nullable until an estimate exists; required with `estimated_price` and must match its monetary inputs. |
| `difficulty_score` | DECIMAL(10,4) | Nullable non-negative calculated difficulty score. Scale, formula and thresholds remain `TBD`. |
| `rules_version` | VARCHAR(64) | Nullable before evaluation; required approved rule-version snapshot for saved results; no relational rule-set FK in this restricted model. |
| `status` | VARCHAR(20) | Required; proposed default `draft`. Proposed values: `draft`, `validated`, `published`, `archived`; `published` is only for a catalogue model. |
| `gemstone_id` | BIGINT | Nullable foreign key to `gemstones.gemstone_id`. One primary selected gemstone option with positive quantity when present. |
| `gemstone_quantity` | INT | Nullable if gemstone_id null; otherwise required positive count. |
| `attachment_id` | BIGINT | Nullable foreign key to `attachments.attachment_id`. One primary physical attachment option with positive quantity when present. |
| `attachment_quantity` | INT | Nullable if attachment_id null; otherwise required positive count. |
| `evaluation_rules_snapshot` | JSON | Nullable until evaluation; safe immutable rule/input context when freezing results. Approved configuration source/ownership remains TBD. |
| `audit_trail` | JSON | Nullable append-only redacted record/domain evidence: stable event key, actor context, time, reason/state change and correlation. Application locking/validation required; embedded actor IDs are not SQL FKs, and JSON does not enforce immutable events. |
| `created_at` | DATETIME | Required design-version creation timestamp. |
| `updated_at` | DATETIME | Required last-update timestamp. |

Indexes:

- (design_type, status, created_at); (created_by_user_id, created_at); (material_id, shape_id); (shape_id); (gemstone_id); (attachment_id); (source_design_id).

Rules:

- Một base material/shape bắt buộc; primary gemstone/attachment optional, quantity > 0 khi có ID.
- Direct FK chỉ bảo đảm một selected loại đá và một loại phụ kiện. Nhiều loại/placement trong snapshot chỉ được application validation; không vẽ junction FK giả.
- Primary IDs/quantity khớp component_snapshot; frozen version giữ giá/rule context. Thay catalogue không rewrite referenced design.
- Published catalogue models mới public; configured Guest/Member access/retention còn TBD. Source không self/cycle; hard compatibility không bị difficulty score ghi đè.

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
| `audit_trail` | JSON | Nullable append-only redacted record/domain evidence: stable event key, actor context, time, reason/state change and correlation. Application locking/validation required; embedded actor IDs are not SQL FKs, and JSON does not enforce immutable events. |
| `created_at` | DATETIME | Required option-creation timestamp. |
| `updated_at` | DATETIME | Required last-update timestamp. |

Indexes:

- Unique (gemstone_code); (is_active, gemstone_type, color).

Rules:

- Primary design FK với selection quantity trên ring_designs. Additional embedded types không có SQL FK; variant/unit/cut rules còn TBD.
- Master catalogue, không stock; disable thay xóa lịch sử.

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
| `audit_trail` | JSON | Nullable append-only redacted record/domain evidence: stable event key, actor context, time, reason/state change and correlation. Application locking/validation required; embedded actor IDs are not SQL FKs, and JSON does not enforce immutable events. |
| `created_at` | DATETIME | Required material-creation timestamp. |
| `updated_at` | DATETIME | Required last-update timestamp. |

Indexes:

- Unique (material_code); (is_active, material_name).

Rules:

- Master base material/optional primary package material; branch/package options kiểm tra riêng. Pricing cần explicit unit/quantity/conversion rule, không suy size thành charge.
- No inventory/procurement ledger; disable thay delete referenced records, không rewrite historical prices.

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
| `audit_trail` | JSON | Nullable append-only redacted record/domain evidence: stable event key, actor context, time, reason/state change and correlation. Application locking/validation required; embedded actor IDs are not SQL FKs, and JSON does not enforce immutable events. |
| `created_at` | DATETIME | Required component-creation timestamp. |
| `updated_at` | DATETIME | Required last-update timestamp. |

Indexes:

- Unique (attachment_code); (is_active, attachment_type).

Rules:

- Phụ kiện vật lý, không uploaded file; primary design FK. Active không chứng minh compatibility; no stock movement model.

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
| `audit_trail` | JSON | Nullable append-only redacted record/domain evidence: stable event key, actor context, time, reason/state change and correlation. Application locking/validation required; embedded actor IDs are not SQL FKs, and JSON does not enforce immutable events. |
| `created_at` | DATETIME | Required ring-form creation timestamp. |
| `updated_at` | DATETIME | Required last-update timestamp. |

Indexes:

- Unique (shape_code); (is_active, shape_name).

Rules:

- Base ring form, không gemstone cut/size; shape → designs 1–N. Price/difficulty chỉ theo approved rules, no double count.
- Disable referenced form thay delete; structural schema/formula vẫn TBD.

## Relationships for ERD

A → B: trái là số A mỗi B tham chiếu; phải là số B mỗi A có thể có. `1 : 0..N` = required FK, `0..1 : 0..N` = nullable FK, `1 : 0..1` = required + unique FK. Mỗi actor FK vào users là cạnh riêng.

### Users and roles relationships

| Bảng cha → bảng con | A : B | FK ở bảng con → PK bảng cha | Ý nghĩa / ràng buộc |
| --- | --- | --- | --- |
| `roles` → `users` | `1 : 0..N` | `users.role_id` → `roles.role_id` | Một role hiện hành; multi-role auth reconciliation TBD. |
| `users` → `users` | `0..1 : 0..N` | `users.role_granted_by_user_id` → `users.user_id` | Actor/customer theo role_granted_by_user_id; FK không tự kiểm tra quyền. |
| `users` → `roles` | `0..1 : 0..N` | `roles.updated_by_user_id` → `users.user_id` | Actor/customer theo updated_by_user_id; FK không tự kiểm tra quyền. |

### Loyalty points relationships

| Bảng cha → bảng con | A : B | FK ở bảng con → PK bảng cha | Ý nghĩa / ràng buộc |
| --- | --- | --- | --- |
| `users` → `loyalty_points` | `1 : 0..N` | `loyalty_points.member_user_id` → `users.user_id` | Actor/customer theo member_user_id; FK không tự kiểm tra quyền. |
| `orders` → `loyalty_points` | `0..1 : 0..N` | `loyalty_points.order_id` → `orders.order_id` | Tham chiếu tùy chọn. |
| `users` → `loyalty_points` | `0..1 : 0..N` | `loyalty_points.recorded_by_user_id` → `users.user_id` | Actor/customer theo recorded_by_user_id; FK không tự kiểm tra quyền. |

### Product catalogue, orders, payment and delivery relationships

| Bảng cha → bảng con | A : B | FK ở bảng con → PK bảng cha | Ý nghĩa / ràng buộc |
| --- | --- | --- | --- |
| `categories` → `products` | `1 : 0..N` | `products.category_id` → `categories.category_id` | Child thuộc đúng một parent; parent có thể có nhiều child. |
| `products` → `product_imgs` | `1 : 0..N` | `product_imgs.product_id` → `products.product_id` | Child thuộc đúng một parent; parent có thể có nhiều child. |
| `users` → `orders` | `1 : 0..N` | `orders.member_user_id` → `users.user_id` | Actor/customer theo member_user_id; FK không tự kiểm tra quyền. |
| `workshop_locations` → `orders` | `1 : 0..N` | `orders.location_id` → `workshop_locations.location_id` | Child thuộc đúng một parent; parent có thể có nhiều child. |
| `users` → `orders` | `0..1 : 0..N` | `orders.picked_up_by_user_id` → `users.user_id` | Actor/customer theo picked_up_by_user_id; FK không tự kiểm tra quyền. |
| `promotions` → `orders` | `0..1 : 0..N` | `orders.promotion_id` → `promotions.promotion_id` | Tối đa một ưu đãi retail; snapshot và usage state trên orders. |
| `workshop_packages` → `orders` | `0..1 : 0..N` | `orders.workshop_package_id` → `workshop_packages.workshop_package_id` | Gói wax custom, không phải workshop booking. |
| `ring_designs` → `orders` | `0..1 : 0..N` | `orders.ring_design_id` → `ring_designs.ring_design_id` | Configuration custom; null cho retail/image input. |
| `users` → `orders` | `0..1 : 0..N` | `orders.final_amount_set_by_user_id` → `users.user_id` | Actor/customer theo final_amount_set_by_user_id; FK không tự kiểm tra quyền. |
| `orders` → `order_items` | `1 : 0..N` | `order_items.order_id` → `orders.order_id` | Retail ≥1 item bằng business check; custom có 0 item, nên cardinality chung 0..N. |
| `products` → `order_items` | `1 : 0..N` | `order_items.product_id` → `products.product_id` | Child thuộc đúng một parent; parent có thể có nhiều child. |
| `orders` → `payments` | `0..1 : 0..N` | `payments.order_id` → `orders.order_id` | Target retail/custom; XOR với booking, purpose/type phải khớp. |
| `workshop_registration` → `payments` | `0..1 : 0..N` | `payments.workshop_registration_id` → `workshop_registration.workshop_registration_id` | Target workshop deposit/balance; XOR với order. |
| `users` → `payments` | `0..1 : 0..N` | `payments.recorded_by_user_id` → `users.user_id` | Actor/customer theo recorded_by_user_id; FK không tự kiểm tra quyền. |
| `orders` → `delivery_infors` | `1 : 0..1` | `delivery_infors.order_id` → `orders.order_id` | Unique required FK, tối đa một delivery cho retail/custom. |
| `users` → `delivery_infors` | `0..1 : 0..N` | `delivery_infors.handed_off_by_user_id` → `users.user_id` | Actor/customer theo handed_off_by_user_id; FK không tự kiểm tra quyền. |

### Locations / branches relationships

| Bảng cha → bảng con | A : B | FK ở bảng con → PK bảng cha | Ý nghĩa / ràng buộc |
| --- | --- | --- | --- |

### Promotions relationships

| Bảng cha → bảng con | A : B | FK ở bảng con → PK bảng cha | Ý nghĩa / ràng buộc |
| --- | --- | --- | --- |
| `users` → `promotions` | `1 : 0..N` | `promotions.created_by_user_id` → `users.user_id` | Actor/customer theo created_by_user_id; FK không tự kiểm tra quyền. |
| `promotions` → `promotion_locations` | `1 : 0..N` | `promotion_locations.promotion_id` → `promotions.promotion_id` | Child thuộc đúng một parent; parent có thể có nhiều child. |
| `workshop_locations` → `promotion_locations` | `1 : 0..N` | `promotion_locations.location_id` → `workshop_locations.location_id` | Child thuộc đúng một parent; parent có thể có nhiều child. |
| `users` → `promotion_locations` | `1 : 0..N` | `promotion_locations.created_by_user_id` → `users.user_id` | Actor/customer theo created_by_user_id; FK không tự kiểm tra quyền. |

### Workshop booking, slots, packages and exceptions relationships

| Bảng cha → bảng con | A : B | FK ở bảng con → PK bảng cha | Ý nghĩa / ràng buộc |
| --- | --- | --- | --- |
| `workshop_locations` → `slots` | `1 : 0..N` | `slots.location_id` → `workshop_locations.location_id` | Child thuộc đúng một parent; parent có thể có nhiều child. |
| `users` → `slots` | `0..1 : 0..N` | `slots.assigned_staff_user_id` → `users.user_id` | Một primary facilitator; extra Staff JSON không có SQL FK. |
| `materials` → `workshop_packages` | `0..1 : 0..N` | `workshop_packages.material_id` → `materials.material_id` | Primary optional; vật liệu thêm JSON không có SQL FK. |
| `users` → `workshop_registration` | `0..1 : 0..N` | `workshop_registration.member_user_id` → `users.user_id` | Guest null; import phải verified email + explicit confirmation. |
| `slots` → `workshop_registration` | `1 : 0..N` | `workshop_registration.slot_id` → `slots.slot_id` | Child thuộc đúng một parent; parent có thể có nhiều child. |
| `workshop_packages` → `workshop_registration` | `1 : 0..N` | `workshop_registration.workshop_package_id` → `workshop_packages.workshop_package_id` | Child thuộc đúng một parent; parent có thể có nhiều child. |
| `ring_designs` → `workshop_registration` | `0..1 : 0..N` | `workshop_registration.ring_design_id` → `ring_designs.ring_design_id` | Tham chiếu tùy chọn. |
| `users` → `workshop_registration` | `0..1 : 0..N` | `workshop_registration.checked_in_by_user_id` → `users.user_id` | Actor/customer theo checked_in_by_user_id; FK không tự kiểm tra quyền. |
| `workshop_registration` → `workshop_registration` | `0..1 : 0..N` | `workshop_registration.parent_registration_id` → `workshop_registration.workshop_registration_id` | Continuation, cấm self/cycle. |
| `users` → `workshop_registration` | `0..1 : 0..N` | `workshop_registration.created_by_user_id` → `users.user_id` | Actor/customer theo created_by_user_id; FK không tự kiểm tra quyền. |
| `workshop_locations` → `worshop_exceptions` | `0..1 : 0..N` | `worshop_exceptions.location_id` → `workshop_locations.location_id` | Tham chiếu tùy chọn. |
| `slots` → `worshop_exceptions` | `0..1 : 0..N` | `worshop_exceptions.slot_id` → `slots.slot_id` | Tham chiếu tùy chọn. |
| `users` → `worshop_exceptions` | `1 : 0..N` | `worshop_exceptions.created_by_user_id` → `users.user_id` | Actor/customer theo created_by_user_id; FK không tự kiểm tra quyền. |

### Custom design request relationships

| Bảng cha → bảng con | A : B | FK ở bảng con → PK bảng cha | Ý nghĩa / ràng buộc |
| --- | --- | --- | --- |
| `users` → `custom_design_requests` | `1 : 0..N` | `custom_design_requests.member_user_id` → `users.user_id` | Actor/customer theo member_user_id; FK không tự kiểm tra quyền. |
| `workshop_registration` → `custom_design_requests` | `1 : 0..N` | `custom_design_requests.workshop_registration_id` → `workshop_registration.workshop_registration_id` | Ảnh trong booking theo Report 3; validate requester ownership. |
| `users` → `custom_design_requests` | `0..1 : 0..N` | `custom_design_requests.reviewed_by_user_id` → `users.user_id` | Actor/customer theo reviewed_by_user_id; FK không tự kiểm tra quyền. |

### Ring designs and component catalogue relationships

| Bảng cha → bảng con | A : B | FK ở bảng con → PK bảng cha | Ý nghĩa / ràng buộc |
| --- | --- | --- | --- |
| `users` → `ring_designs` | `0..1 : 0..N` | `ring_designs.created_by_user_id` → `users.user_id` | Actor/customer theo created_by_user_id; FK không tự kiểm tra quyền. |
| `ring_designs` → `ring_designs` | `0..1 : 0..N` | `ring_designs.source_design_id` → `ring_designs.ring_design_id` | Tham chiếu tùy chọn. |
| `materials` → `ring_designs` | `1 : 0..N` | `ring_designs.material_id` → `materials.material_id` | Child thuộc đúng một parent; parent có thể có nhiều child. |
| `shape` → `ring_designs` | `1 : 0..N` | `ring_designs.shape_id` → `shape.shape_id` | Child thuộc đúng một parent; parent có thể có nhiều child. |
| `gemstones` → `ring_designs` | `0..1 : 0..N` | `ring_designs.gemstone_id` → `gemstones.gemstone_id` | Primary gemstone optional với positive quantity. |
| `attachments` → `ring_designs` | `0..1 : 0..N` | `ring_designs.attachment_id` → `attachments.attachment_id` | Primary phụ kiện vật lý optional với positive quantity. |

### Quan hệ N–N có bảng nối giữ nguyên

| Hai entity | Bảng nối | Khóa / dữ liệu |
| --- | --- | --- |
| orders ↔ products | order_items | PK order_item_id, FK order_id/product_id, unique order_id/line_key; retail quantity/options/price snapshot. |
| promotions ↔ workshop_locations | promotion_locations | PK ghép promotion_id/location_id, FK cả hai master; active/actor/time. |

### JSON/application references — không phải SQL FK

| Trường hiện có / bổ sung | Liên kết | Kiểm tra / giới hạn |
| --- | --- | --- |
| users.cart_snapshot | products và options | Application kiểm tra từng ID/quantity/revision; checkout sang order_items. |
| users.policy_acceptances | Terms/Privacy versions | Version/digest/time snapshot; không policy FK trong restricted ERD. |
| roles.permission_codes | Approved operation codes | Allowlist validation; không permission entity mới. |
| workshop_locations.supported_material_ids | materials | Logical N–N, application validates IDs/active/availability. |
| workshop_locations.available_workshop_package_ids | workshop_packages | Logical N–N offering/publication/compatibility. |
| workshop_packages.supported_material_ids | materials | Additional compatibility options, application checks. |
| ring_designs.component_snapshot | Components/price/rule inputs | Primary IDs có FK; additional types/placements chỉ application references. |
| slots.additional_staff_assignments | users (Staff) | Không FK/unique mỗi element; validate role/overlap. |
| workshop_registration.invoice_adjustments | Adjustment/consent/approval actors | Stable event key, amount/reason/consent checks, lock append. |
| workshop_registration.custody_items | Item, booking chain, location, actor | Application integrity; không SQL PK/FK cho mỗi custody cycle. |
| payments.verification_history và audit_trail | Provider/change evidence | Target-level idempotency/locking; không SQL unique mỗi event. |

## Constraints and known limits

| Nhóm | Quy tắc / phần giữ TBD |
| --- | --- |
| Fixed table set | Giữ đúng 23 entity trong ERD chính, không thêm bảng. Tên bảng thứ 24 chưa xuất hiện trong nguồn được chỉ định. |
| PK/FK/retention | Mọi SQL FK trỏ PK được khai báo, nullable/unique rõ; không cascade delete lịch sử. JSON IDs không có referential integrity tương đương junction tables. |
| Order discriminator | Retail/custom chung orders: type-specific required/forbidden fields và lifecycle khác nhau. Custom 0 item, retail ≥1 item. Nullable fields là trade-off để giữ bảng. |
| Payment XOR/effects | Exactly one order/booking target; purpose/method/type/amount/currency matching. Nhiều attempts nhưng mỗi nghĩa vụ được áp dụng một lần; deposit và balance custom khác nhau. |
| Gateway versus shop receipt | IPN/QueryDR verified online; offline cần actual receipt + Staff proof. Unknown callbacks không có target chưa được lưu trong mô hình này, không tạo payment giả. |
| Promotion/points | Một code/promotion và một promotion/order. Usage counts theo reserved/consumed orders, atomic expiry/redeem; points ledger immutable và debit/compensation policy còn TBD. |
| Time/capacity | Link VNPay 10 phút, resource hold tối đa 15 phút, email token 15 phút là các đồng hồ khác nhau. Pre-email hold/release/invoice activation policy còn TBD. Capacity theo participant_count, queue theo eligible custom orders. |
| Existing auth schema | Embedded email/policy fields là logical proposal, không xóa applied policy/token/audit tables hoặc rename schema. Mapping/backfill/concurrency/security review còn TBD. |
| Role simplification | Một role/user có FK trực tiếp nhưng auth feature đề xuất đa vai trò. Role-code/branch-permission/model reconciliation còn TBD; không coi hai mô hình tương đương. |
| Embedded evidence | Consent/audit/custody JSON không có PK/unique/immutability per-element được DB enforce. Array growth, concurrent append, authorization, retention và immutable corrections cần feature plan. |
| Components/compatibility | Primary đá/phụ kiện FK cho một type mỗi design; nhiều loại và branch/package materials ở JSON phải application validation. Không vẽ SQL N–N giả. |
| Module ownership | Shared orders và invoice-on-booking cần chốt domain owner/facade/transaction boundary trước triển khai; việc sửa DB.md không tự đổi architecture conventions. |
| Manufacturing | Wax package, lead-time calendar/global-versus-branch queue/deadline exclusions, final-amount amendments và retention vẫn TBD; AI không decide custom orders. |
| Historical source conflicts | Report 2/3 bao gồm loyalty/Staff facilitation; Report 1 hoãn loyalty/loại HR. Workshop fee/waiver/settlement/custody details và role names còn cần reconcile. |
| Booking image boundary | Report 3 gắn Member image request với booking, khác ERD cũ review-only. Latest analysis + terminal decision được giữ; nhiều reviews/overrides/attempts chưa có normalized history. |
| Unmodeled technical storage | Persisted chat, reliable email/integration retry queues và global versioned parameter storage chưa được đặc tả trong 23 bảng; cần approved infrastructure/config source, không claim field summary đã hoàn thành mọi UC. |
| Klook | Single supplier account scoped unique external booking ID; hold/security/retry protocol và unknown inbound history TBD. No Klook cancellation auto-release. |
| Privacy | Report 2: no AI image input after processing, text ≤30 ngày, custom image tới pickup/handoff, delivery PII 30 ngày, payment/audit 5 năm. Reconcile older policy; audit không giữ PII đã erase. |
| ERD source | ERD.md chỉ được đọc lấy table set, chưa sửa cạnh/cột cũ; dùng Relationships for ERD của DB.md này khi vẽ lại. |

Validation scope: 23 entity definitions and 48 SQL FK relationships; exact original table set, PK/FK targets, nullable/unique/cardinality, entity form and JSON-reference distinction checked. No application/migration changes.
