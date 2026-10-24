### 4.4 Permission Matrix

Full = unrestricted  ·  Restricted = conditions apply (see footnotes)  ·  No = denied

The matrix covers the five platform roles defined in the Actors section. External providers such as VNPay, Klook, Gemini, Google Maps, the Mail Gateway, SSO, Cloud Storage, and GHTK are integration actors rather than platform roles; their system-to-system boundaries are defined in the External API Inventory.

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
|Booking-design image|Submit and view own booking-design image analysis|No|Full|No|No|Restricted|
|Booking-design review|Review a request estimated at or below 3,000,000 VND|No|No|Full|Restricted|No|
|Booking-design review|Review a request above 3,000,000 VND or override when authorized|No|No|No|Full|Restricted|
|AI-assisted pricing|Review AI suggestions and decide the published price|No|No|No|Full|Restricted|
|Loyalty|View own points and history|No|Full|No|No|Restricted|
|Loyalty|Redeem points during eligible checkout|No|Full|No|No|Restricted|
|Promotion and voucher|Create, update, activate, deactivate, and audit campaigns or vouchers|No|No|Restricted|Full|Restricted|
|Shopping cart|Add, update, or remove own retail cart items|No|Full|No|No|Restricted|
|Retail checkout|Create an eligible retail order and initiate payment|No|Full|No|No|Restricted|
|Retail order|View own order history and order details|No|Full|Restricted|Restricted|Restricted|
|Retail fulfilment|Review, prepare, pack, and update paid retail order status|No|No|Full|Restricted|Restricted|
|Retail fulfilment|Record customer pickup or manual GHTK handoff|No|No|Full|Restricted|Restricted|
|Custom manufacturing|Submit and view own custom order|No|Full|No|No|Restricted|
|Custom manufacturing|Configure queue rules and unavailable deadline dates|No|No|No|Full|Full|
|Custom manufacturing|Set final custom amount and request final payment|No|No|Full|Full|Restricted|
|Custom manufacturing|Fulfil by Member pickup or Staff-recorded GHTK handoff|No|No|Full|Restricted|Restricted|
|Operational parameters|Configure deadlines, loyalty rates, point value, and discount caps|No|No|No|Restricted|Full|
|Third-party integrations|Manage approved credentials, endpoints, and activation status|No|No|No|No|Full|
|Executive dashboard|View consolidated revenue, utilization, and branch KPIs|No|No|Restricted|Full|Full|
|Public store review|Open the Google Maps review link|Full|Full|Full|Full|Full|

#### Permission Matrix Footnotes

1. `Restricted` means that the actor may perform the action only within an assigned branch, delegated threshold, own account/order, approved workflow state, or configured operational limit.
2. Guests may book workshops and pay workshop deposits, but they cannot submit booking-design images, complete retail checkout, or create custom-manufacturing orders.
3. Members may access and modify only their own profile, cart, orders, loyalty records, booking-design requests, and custom-manufacturing orders.
4. Staff may review booking-design requests estimated at or below 3,000,000 VND, while Managers review requests above that threshold. Managers, not AI, decide published package and product prices.
5. Admin manages technical configuration, accounts, RBAC, and approved integration credentials. Admin does not replace Staff or Manager business approvals unless an explicit authorized override is defined.
6. GHTK actions are internal records of manual handoff. The matrix does not grant any role access to GHTK tracking or delivery-management APIs because V1 has no such integration.
