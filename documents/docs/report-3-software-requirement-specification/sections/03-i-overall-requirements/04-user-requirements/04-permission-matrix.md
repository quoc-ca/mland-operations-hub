### 4.4 Permission Matrix

Full = unrestricted  ·  Restricted = conditions apply (see footnotes)  ·  No = denied

The matrix covers the five platform roles defined in the Actors section. External providers such as VNPay, Klook, Gemini, Google Maps, the Mail Gateway, SSO, Cloud Storage, and GHTK are integration actors rather than platform roles; their system-to-system boundaries are defined in the External API Inventory. The GHTK partner account (role `CARRIER`, "Đối tác vận chuyển (GHTK)") is a restricted courier-portal account outside the five roles; see footnote 6.

|Entity / Function|Action|Guest|Member|Staff|Manager|Admin|
|---|---|---|---|---|---|---|
|Public catalogue and workshop information|Browse products, ring models, workshop packages, schedules, and store information|Full|Full|Full|Full|Full|
|Account|Register, sign in, and recover account|Full|Full|No|No|Restricted|
|Personal profile|View and update own profile|No|Full|No|No|Restricted|
|User account|Create, view, update, lock, or unlock platform accounts|No|No|No|Restricted|Full|
|Role-based access control|Configure roles and permissions|No|No|No|No|Full|
|Workshop booking|Create a workshop booking and hold temporary capacity|Full|Full|Restricted|No|Restricted|
|Workshop payment|Pay a workshop deposit and view the payment result|Full|Full|No|No|Restricted|
|Booking lookup|Look up booking details and QR check-in ticket|Full|Full|Restricted|Restricted|Restricted|
|Workshop operations|Check in a participant by QR code or manual code|No|No|Full|Restricted|Restricted|
|Workshop schedule|Create, update, or cancel schedules and branch allocations|No|No|Restricted|Full|Restricted|
|Workshop staffing|Assign Staff to workshop slots|No|No|Restricted|Full|Restricted|
|Workshop time and capacity|Configure time slots, capacity, participant limits, materials, holidays, and closures|No|No|No|Full|Restricted|
|Workshop integration|Synchronize schedule and confirmed booking with Klook|No|No|No|Full|Restricted|
|Product catalogue|Create and update products, images, prices, and publication status|No|No|Full|Restricted|Restricted|
|AI support|Use basic text support|Full|Full|Full|Full|Restricted|
|Independent Design Request|Create and view own request and safe analysis/review result|TBD by ownership-proof policy|Full under policy|No|No|Technical/audit only|
|Independent Design Review|Review an in-scope request under approved authority policy|No|No|Restricted|Restricted|No business decision|
|AI-assisted pricing|Review AI suggestions and decide the published price|No|No|No|Full|Restricted|
|Review Management|Create/edit own eligible review; Staff hide; Manager hide/remove/reply; Admin technical/audit only|Guest/Member under source eligibility|Own sources only|Hide any review|Hide/remove/reply|Technical/audit only|
|Loyalty|View own points and history|No|Full|No|No|Restricted|
|Loyalty|Redeem points during eligible checkout|No|Full|No|No|Restricted|
|Promotion and voucher|Create, update, activate, deactivate, and audit campaigns or vouchers|No|No|Restricted|Full|Restricted|
|Shopping cart|Add, update, or remove own retail cart items|No|Full|No|No|Restricted|
|Retail checkout|Create an eligible retail order, pay by VNPay or at the counter for store pickup, and cancel an unpaid order|No|Full|No|No|Restricted|
|Retail order|View own order history and order details|No|Full|Restricted|Restricted|Restricted|
|Retail fulfilment|Prepare and pack a paid retail order and mark it ready for pickup or ready for the GHTK courier|No|No|Full|Restricted|Restricted|
|Retail fulfilment|Record customer pickup and counter cash collection|No|No|Full|Restricted|Restricted|
|Retail shipment|Record the courier pickup and the delivery of a GHTK shipment|No|No|No|No|No|
|Custom manufacturing|Submit and view own custom order|No|Full|No|No|Restricted|
|Custom manufacturing|Configure queue rules and unavailable deadline dates|No|No|No|Full|Full|
|Custom manufacturing|Set final custom amount and request final payment|No|No|Full|Full|Restricted|
|Custom manufacturing|Fulfil by Member pickup or Staff-recorded GHTK handoff|No|No|Full|Restricted|Restricted|
|Operational parameters|Configure deadlines, loyalty rates, point value, and discount caps|No|No|No|Restricted|Full|
|Third-party integrations|Manage approved credentials, endpoints, and activation status|No|No|No|No|Full|
|Executive dashboard|View consolidated revenue, workshop and retail KPIs, day/week/month/year trends, and the top 5 products and workshop packages for all branches or one branch|No|No|No|Restricted|No|
|Public store review|Open the Google Maps review link|Full|Full|Full|Full|Full|

#### Permission Matrix Footnotes

1. `Restricted` means that the actor may perform the action only within a delegated threshold, own account/order, approved workflow state, or configured operational limit. For Manager, `Restricted` MUST NOT mean an assigned branch or Workshop Location: an active Manager has global operational scope for an action explicitly authorized to Manager. Staff may remain assigned-branch/Workshop-Location-scoped where the owning feature states that rule.
2. Guests may book workshops and pay workshop deposits. Feature-004 Guest Design Request access is enabled through an opaque guest session/tracking boundary; Guest requests are limited to five per calendar day. Guests cannot complete retail checkout or create custom-manufacturing orders.
3. Members may access and modify only their own profile, cart, orders, loyalty records, independent Design Requests, and custom-manufacturing orders.
4. Staff and Managers review independent Design Requests only under the approved Feature-004 authority and scope policy. Managers, not AI, decide published package and product prices.
5. Admin manages technical configuration, accounts, RBAC, and approved integration credentials. Admin may view purpose-limited parameter-impact information in FE-09 but has no access to FE-10 executive revenue, branch rankings, customer details, or individual order/payment details. Admin does not replace Staff or Manager business approvals unless an explicit authorized override is defined.
6. GHTK shipments are recorded in the platform; V1 calls GHTK only for the delivery-fee quote and has no shipment-creation, tracking, or webhook integration. Staff packs the order and marks it ready for the courier. The GHTK partner account (role `CARRIER`) records only "Đã lấy hàng" (courier pickup) and "Giao thành công" (delivery) for the GHTK shipments of every branch, and sees the pickup branch, recipient name, phone and address, item count, and shipping fee, never product prices, vouchers, points, or Member account data. Only that account records the Retail shipment row; the platform roles cannot.
7. Feature-011 allows a matched Guest or owning Member to create a review only for an eligible checked-in workshop attendance or picked-up/delivered retail order line. Staff may HIDE any Feature-011 review; Manager may HIDE/REMOVE and reply; Admin has technical/audit access only. This global Staff hide permission is deliberate and is not inferred from branch scope.
