### 4.4 Permission Matrix

Full = unrestricted  ·  Restricted = conditions apply (see footnotes)  ·  No = denied

The matrix covers the five platform roles defined in the Actors section. External providers such as VNPay, Gemini, the Mail Gateway, SSO, Cloud Storage, and GHTK are integration actors rather than platform roles; their system-to-system boundaries are defined in the External API Inventory. The GHTK partner account (role `CARRIER`, "Đối tác vận chuyển (GHTK)") is a restricted courier-portal account outside the five roles; see footnote 6.

|Entity / Function|Action|Guest|Member|Staff|Manager|Admin|
|---|---|---|---|---|---|---|
|Public catalogue and workshop information|Browse products, ring models, workshop packages, schedules, and store information|Full|Full|Full|Full|Full|
|Account|Register, sign in, and recover account|Full|Full|Restricted|Restricted|Restricted|
|Personal profile|View and update own profile|No|Full|Restricted|Restricted|Restricted|
|User account|Create, view, update, lock, or unlock platform accounts|No|No|No|No|Full|
|Workshop booking|Create a workshop booking and hold temporary capacity|Full|Full|Restricted|No|No|
|Workshop payment|Pay a workshop deposit and view the payment result|Full|Full|No|No|No|
|Booking lookup|Look up booking details and QR check-in ticket|Full|Full|Restricted|Restricted|No|
|Workshop operations|Check in a participant by QR code or manual code|No|No|Restricted|Full|No|
|Workshop schedule|Create, update, or cancel schedules and branch allocations|No|No|No|Full|No|
|Workshop staffing|Assign Staff to workshop slots|No|No|No|Full|No|
|Workshop time and capacity|Configure time slots, session capacity, and participant limits|No|No|No|Full|No|
|Ticket folio|View a ticket bill, add-ons, and balance (UC 77)|Restricted|Restricted|Restricted|Full|No|
|Ticket folio|Add a consented charge and collect the balance (UC 78, 79)|No|No|Restricted|Full|No|
|Ticket folio|Verify transfers, manage handover exceptions and debt, and correct a folio (UC 80, 81, 82)|No|No|No|Full|No|
|Cash day|Close a branch cash day; only a Manager resolves a difference (UC 83)|No|No|Restricted|Full|No|
|Ticket cancellation|Cancel unused workshop tickets (UC 84)|Restricted|Restricted|Restricted|Full|No|
|Ticket change|Select replacement sessions for a ticket (UC 85)|Restricted|Restricted|Restricted|Full|No|
|Ticket change|Approve or reject a queued change request (UC 86)|No|No|Restricted|Full|No|
|Ticket change|Grant an extra counted change (UC 87)|No|No|No|Full|No|
|Mland cancellation|Choose a replacement session or refund (UC 88); Staff and Manager only with the buyer proof|Restricted|Restricted|Restricted|Restricted|No|
|Refund|Submit the receiving refund account (UC 89)|Restricted|Restricted|Restricted|Restricted|No|
|Refund|Process a refund request (UC 90); Staff may only resend the refund-account link|No|No|Restricted|Full|No|
|Product catalogue|Create and update products, images, stock and proposed prices|No|No|Restricted|Full|No|
|Product publication|Publish, hide or show again a product (UC 17)|No|No|No|Full|No|
|Product category|Create, update, activate, or deactivate product categories|No|No|Full|Full|No|
|AI support|Use basic text support|Full|Full|Full|Full|Restricted|
|Staff consultation chat|Start and follow own conversation; claim, reply to, close, or assign conversations|No|Restricted|Restricted|Full|No|
|Workshop ring design|Create, evaluate, save, and archive own ring design; Guest may only browse public options and sees a sign-in prompt|No|Full|No|No|Technical/audit only|
|Ring-design catalogue|Change the ring-design catalogue and rules|No|No|No|Full|Technical settings only|
|Independent Design Request|Create and view own request and its human-review result|No (sign-in required)|Full|No|No|Technical/audit only|
|Independent Design Review|Review an in-scope request under approved authority policy|No|No|Restricted|Restricted|No business decision|
|Published pricing|Decide the published price of a new workshop package or retail product|No|No|No|Full|No|
|Review Management|Create/edit own eligible review; Staff hide; Manager hide/remove/reply; Admin technical/audit only|Read public reviews only; create/edit requires sign-in|Own eligible sources only|Hide any review|Hide/remove/reply|Technical/audit only|
|Loyalty|View own points and history|No|Full|No|No|No|
|Loyalty|Redeem points during eligible checkout|No|Full|No|No|No|
|Loyalty|Configure the loyalty point policy (UC 51)|No|No|No|Full|No|
|Promotion and voucher|Create, update, activate, deactivate, and audit campaigns or vouchers|No|No|No|Full|No|
|Shopping cart|Add, update, or remove own retail cart items|No|Full|No|No|No|
|Retail checkout|Create an eligible retail order, pay by VNPay or at the counter for store pickup, and cancel an unpaid order|No|Full|No|No|No|
|Retail order|View own order history and order details|No|Full|Restricted|Restricted|Restricted|
|Retail fulfilment|Prepare and pack a paid retail order and mark it ready for pickup or ready for the GHTK courier|No|No|Restricted|Restricted|No|
|Retail fulfilment|Record customer pickup and counter cash collection|No|No|Restricted|No|No|
|Retail payment and cash|View daily retail cash and the VNPay checkouts that need manual reconciliation (read-only)|No|No|No|Restricted|No|
|Retail shipment|Record the courier pickup and the delivery of a GHTK shipment|No|No|No|No|No|
|Custom manufacturing|Submit and view own custom order|No|Full|No|No|No|
|Custom manufacturing|Prepare a quote (Staff or Manager) and approve it (Manager only)|No|No|Restricted|Full|No|
|Custom manufacturing|Fulfil by Member pickup or Staff-recorded GHTK handoff|No|No|Full|Restricted|Restricted|
|Notification|Manage notification templates, and resend or cancel delivery requests|No|No|No|Restricted|Full|
|Third-party integrations|Manage approved credentials, endpoints, and activation status|No|No|No|No|Full|
|Executive dashboard|View consolidated revenue, workshop and retail KPIs, day/week/month/year trends, and the top 5 products and workshop packages for all branches or one branch|No|No|No|Restricted|No|
|Public Mland reviews|Browse public workshop-session and retail-product reviews|Full|Full|Full|Full|Full|

#### Permission Matrix Footnotes

1. `Restricted` means that the actor may perform the action only within a delegated threshold, own account/order, approved workflow state, or configured operational limit. For Manager, `Restricted` MUST NOT mean an assigned branch or Workshop Location: an active Manager has global operational scope for an action explicitly authorized to Manager, except for retail operations and the executive dashboard (footnote 9). Staff may remain assigned-branch/Workshop-Location-scoped where the owning feature states that rule.
2. Guests may book workshops and pay workshop deposits. Under the Feature-004 and Feature-012 member-only rebaseline (2026-11-24), a Guest may see the entry point, explanation, and public options but cannot create, upload, track, or read a design request or ring design; a protected action prompts sign-in, and no Guest design session, tracking token, or daily quota exists. Guests cannot complete retail checkout or create custom-manufacturing orders.
3. Members may access and modify only their own profile, cart, orders, loyalty records, independent Design Requests, and custom-manufacturing orders.
4. Staff and Managers review independent Design Requests only under the approved Feature-004 authority and scope policy. Managers, not AI, decide published package and product prices.
5. Admin manages technical configuration, accounts, fixed-role assignment, and approved integration credentials. Admin has no promotion or loyalty-point permission (GBR-18) and no access to FE-10 executive revenue, branch rankings, customer details, or individual order/payment details. Admin does not replace Staff or Manager business approvals unless an explicit authorized override is defined.
6. GHTK shipments are recorded in the platform; V1 calls GHTK only for the delivery-fee quote and has no shipment-creation, tracking, or webhook integration. Staff packs the order and marks it ready for the courier. The GHTK partner account (role `CARRIER`) records only "Đã lấy hàng" (courier pickup) and "Giao thành công" (delivery) for the GHTK shipments of every branch, and sees the pickup branch, recipient name, phone and address, item count, and shipping fee, never product prices, vouchers, points, or Member account data. Only that account records the Retail shipment row; the platform roles cannot.
7. Feature-011 (public-read / Member-write rebaseline) lets a Guest browse public Mland reviews and aggregates only; the former Guest email/phone lookup is removed. An owning Member may create a review only for an eligible checked-in workshop attendance or picked-up/delivered retail order line. Staff may HIDE any Feature-011 review; Manager may HIDE/REMOVE and reply; Admin has technical/audit access only. This global Staff hide permission is deliberate and is not inferred from branch scope.
8. For ticket settlement and changes (Feature-003.3), `Restricted` for a Guest or Member means only their own ticket after the required ownership, contact, one-time-code, or single-use-link proof; `Restricted` for Staff means the assigned location and, where the owning feature requires it, a session assignment.
9. Retail operations (Feature-008) and the executive dashboard (Feature-010) differ from footnote 1: a Manager reaches only the branches assigned to them, and Staff only their own branch. Recording customer pickup and collecting cash at the counter belong to Staff of the branch only (decision of 2026-11-25, Feature-008 T089); a Manager has no retail-order screen for them. Admin has no retail-order business authority.
10. Account (Feature-001): Staff and Manager sign in and recover their account but cannot self-register; they complete an Admin invitation. They may view their own profile and change their own email and password; only an Admin edits their full name and phone. Account administration (`/admin/accounts`) is Admin only.
11. Staff consultation chat (Feature-013): a Member uses only their own conversations, Staff works within its current Workshop Location, and a Manager has global authority including assignment; Admin has no business chat access. Ring-design catalogue (Feature-012): Staff is denied unless separately granted. Notification (Feature-007): Admin and Manager act at global scope, and only an Admin may authorize a resend from an unknown delivery outcome.
