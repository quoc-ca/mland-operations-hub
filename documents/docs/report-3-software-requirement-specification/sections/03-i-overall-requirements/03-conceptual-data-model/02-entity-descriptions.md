### 3.2 Entity Descriptions

Each block describes one entity in the ERD with its purpose and key business attributes only. The database tables named in brackets are described column by column in Report 4, section 3.

**Entity: Account (accounts, account_profiles)**

| Field | Value |
| --- | --- |
| Purpose | Every person who signs in to the system, regardless of role. Guest customers do not need an Account. |
| Key business attributes | Firebase UID, login email, email verification time, full name, phone, role (Member / Staff / Manager / Carrier / Admin) and account status. |
| Business identity | Firebase UID is unique across the system. Email is not used as the account identity. |
| Status / lifecycle | Active · Pending setup (internal account not yet activated) · Suspended (protected access blocked, history kept). |

**Entity: Branch (workshop_locations)**

| Field | Value |
| --- | --- |
| Purpose | A Mland branch that runs workshops, holds ready-ring stock and fulfils orders. |
| Key business attributes | Branch code, display name and address. |
| Business identity | Branch code is unique. |
| Status / lifecycle | Active or inactive. An inactive branch cannot receive new sessions or orders. |

**Entity: Product Category (catalogue_categories)**

| Field | Value |
| --- | --- |
| Purpose | Groups ready-ring products for browsing and filtering. |
| Key business attributes | Category name and description. |
| Business identity | Category name is unique (case-insensitive). |
| Status / lifecycle | Active or inactive. Inactive categories are hidden from the public catalogue. |

**Entity: Product (catalogue_products)**

| Field | Value |
| --- | --- |
| Purpose | A ready-ring product sold to Members. |
| Key business attributes | Product name, description, category, images and publish flag. |
| Business identity | Each product has its own identifier; product names are not required to be unique. |
| Status / lifecycle | Unpublished · Published (after Manager approval of content and price) · Deleted (soft delete, history kept). |

**Entity: Product Variant (catalogue_variants, catalogue_variant_branch_stock)**

| Field | Value |
| --- | --- |
| Purpose | One ring size of a product, with its own price and stock at each branch. |
| Key business attributes | Ring size, SKU, applied price, stock per branch and sale flag. |
| Business identity | SKU is unique. A product has at most one variant per ring size. |
| Status / lifecycle | Sale enabled or disabled. A price change takes effect only after a Manager approves the price proposal. |

**Entity: Cart (retail_carts, retail_cart_lines)**

| Field | Value |
| --- | --- |
| Purpose | The shopping cart of a Member before checkout. |
| Key business attributes | Member, and for each line: variant, branch and quantity. |
| Business identity | Each Member has at most one cart. |
| Status / lifecycle | No status. Lines are added, changed or removed until checkout. |

**Entity: Retail Checkout (retail_grouped_checkouts)**

| Field | Value |
| --- | --- |
| Purpose | One Member payment that covers the orders created from a cart, possibly at several branches. |
| Key business attributes | Member, merchandise subtotal, shipping charge, discount, payable amount, payment method (VNPay or cash at counter) and payment deadline. |
| Business identity | The idempotency key identifies one checkout command across retries. |
| Status / lifecycle | Accepted unpaid → Payment initiated → Paid → Completed, or Expired / Cancelled when payment does not arrive in time. |

**Entity: Retail Order (retail_orders)**

| Field | Value |
| --- | --- |
| Purpose | The part of a checkout fulfilled by one branch. |
| Key business attributes | Checkout, branch, fulfilment method (pickup or delivery), subtotal, shipping charge, payable amount and recipient details. |
| Business identity | Each order has its own identifier and belongs to exactly one checkout. |
| Status / lifecycle | Follows the checkout payment state, then the fulfilment state until pickup or delivery is completed. |

**Entity: Retail Order Item (retail_order_items)**

| Field | Value |
| --- | --- |
| Purpose | One variant bought in a retail order. |
| Key business attributes | Order, variant, quantity, unit price and line total. |
| Business identity | Each line has its own identifier within its order. |
| Status / lifecycle | No status. Price and quantity are frozen at checkout and are not recalculated later. |

**Entity: Shipment (retail_shipments)**

| Field | Value |
| --- | --- |
| Purpose | The carrier delivery of a retail order. |
| Key business attributes | Order, branch, tracking number and recipient address. |
| Business identity | One shipment per order; the tracking number is unique. |
| Status / lifecycle | Preparing → Ready for carrier → Handed to carrier → In transit → Delivered. |

**Entity: Workshop Package (workshop_packages)**

| Field | Value |
| --- | --- |
| Purpose | A workshop offer that customers book, such as a ring-making course. |
| Key business attributes | Name, description, public price, minimum and maximum participants, required number of sessions and scheduling rules. |
| Business identity | Each package has its own identifier. |
| Status / lifecycle | Draft → Published → Retired. Only published packages can be booked. |

**Entity: Workshop Session (workshop_sessions)**

| Field | Value |
| --- | --- |
| Purpose | One scheduled workshop at a branch on a given date and time slot. |
| Key business attributes | Branch, date, start and end time, capacity and check-in opening time. |
| Business identity | Session reference is unique. |
| Status / lifecycle | Draft → Published → Sales paused → In progress → Attendance finalized; or Cancellation requested → Cancelled. |

**Entity: Workshop Add-on (workshop_addons)**

| Field | Value |
| --- | --- |
| Purpose | An extra item or service sold during a workshop, charged to the ticket bill. |
| Key business attributes | Package, branch, description and price. |
| Business identity | Add-on reference is unique. |
| Status / lifecycle | Draft → Active → Retired. |

**Entity: Booking Group (booking_groups)**

| Field | Value |
| --- | --- |
| Purpose | One booking made by a Member or Guest buyer for one or more participants. |
| Key business attributes | Group code, buyer, contact email and phone, payment mode, amount to collect, hold expiry and payment deadline. |
| Business identity | Group code is unique. |
| Status / lifecycle | Pending payment → Confirmed; or Expired / Cancelled. |

**Entity: Ticket (ticket_bookings)**

| Field | Value |
| --- | --- |
| Purpose | One participant's place in a booking group, used for check-in and settlement. |
| Key business attributes | Ticket code, booking group, package, participant order, QR reference and number of changes used. |
| Business identity | Ticket code and QR reference are unique. |
| Status / lifecycle | Pending payment → Confirmed → Completed or No-show; or Expired / Cancelled. Financial state: Unpaid → Deposit paid → Fully paid, or Refund pending → Refunded. |

**Entity: Payment (group_payment_attempts, settlement_folio_collections)**

| Field | Value |
| --- | --- |
| Purpose | Money received for a booking group, a ticket bill or a retail checkout. |
| Key business attributes | Paid target, method (VNPay, cash or bank transfer), amount, provider reference and confirmation time. |
| Business identity | Payment reference and VNPay merchant reference are unique. |
| Status / lifecycle | Pending → Success, Failed or Expired for VNPay attempts. Bill collections: Pending verification → Confirmed, Failed or Voided. |

**Entity: Ticket Bill (settlement_final_bills)**

| Field | Value |
| --- | --- |
| Purpose | The bill of a ticket after the workshop, combining the deposit, add-on charges and the remaining amount. |
| Key business attributes | Ticket, booking group, amount after promotion, accepted deposit, remaining amount, handover approval and recorded debt. |
| Business identity | Bill reference is unique; one bill per ticket. |
| Status / lifecycle | Open → Finalized → Closed. |

**Entity: Ticket Cancellation (settlement_ticket_cancellations)**

| Field | Value |
| --- | --- |
| Purpose | A request to cancel one ticket and its outcome. |
| Key business attributes | Ticket, requester, channel, reason, request time, points returned and voucher restored. |
| Business identity | Cancellation reference is unique; a ticket can be cancelled only once. |
| Status / lifecycle | Recorded once when the cancellation is accepted; a refund may follow. |

**Entity: Ticket Change (settlement_ticket_changes)**

| Field | Value |
| --- | --- |
| Purpose | A move of a ticket from its sessions to other sessions. |
| Key business attributes | Ticket, change type, original and new sessions, price increase, requester and reason. |
| Business identity | Change reference is unique. |
| Status / lifecycle | Applied once accepted; customer changes are limited by the package quota. |

**Entity: Refund Request (settlement_refund_requests)**

| Field | Value |
| --- | --- |
| Purpose | Money that Mland must return to a customer after a cancellation. |
| Key business attributes | Booking group, amount, reason, recipient bank account (masked), approver, transfer reference and transfer proof. |
| Business identity | Request reference is unique. |
| Status / lifecycle | Pending account → Pending approval → Pending transfer → Pending reconciliation → Refunded; or Closed unreachable. |

**Entity: Design Request (design_requests)**

| Field | Value |
| --- | --- |
| Purpose | A request from a Member to have a custom ring designed from a description, reference images, and optionally a Member-owned Feature-012 design snapshot. |
| Key business attributes | Owner, description, reference images, estimated value and review decision. |
| Business identity | Request reference is unique. |
| Status / lifecycle | Active → Completed or Withdrawn. Review: Need review → Accepted, Rejected or Clarification required. |

**Entity: Custom Order (custom_orders)**

| Field | Value |
| --- | --- |
| Purpose | An order to make a custom ring after a design request is accepted. |
| Key business attributes | Member, design request, referenced ring design, quotes, production status, fulfilment status and shipping address. |
| Business identity | Custom order reference is unique. |
| Status / lifecycle | Tracked by order status, production status and fulfilment status; payment follows the approved quote. |

**Entity: Ring Design (ring_designs)**

| Field | Value |
| --- | --- |
| Purpose | A ring designed in the configurator from material, shape, gemstones and attachments. |
| Key business attributes | Owner, material, shape, ring size, selected components, estimated price and difficulty score. |
| Business identity | Design reference and version together identify one immutable design version. |
| Status / lifecycle | Draft → Saved → Archived. A saved evaluation only produces an advisory workshop recommendation; Feature-012 does not attach a design to a workshop booking. |

**Entity: Loyalty Wallet (loyalty_wallet)**

| Field | Value |
| --- | --- |
| Purpose | The point balance of a Member. |
| Key business attributes | Member, available points and held points. |
| Business identity | One wallet per Member. |
| Status / lifecycle | No status. The balance changes only through point transactions. |

**Entity: Point Transaction (loyalty_point_ledger)**

| Field | Value |
| --- | --- |
| Purpose | One movement of points in a wallet. |
| Key business attributes | Wallet, kind (earn, hold, consume, release, reverse), change in balance, change in held points and time. |
| Business identity | Each entry has its own identifier; entries are never edited. |
| Status / lifecycle | Append-only. A mistake is corrected by a reversing entry. |

**Entity: Promotion (promotion_program)**

| Field | Value |
| --- | --- |
| Purpose | A voucher or automatic discount for products or workshops. |
| Key business attributes | Kind (voucher or automatic discount), scope (product or workshop), voucher code, total limit, limit per Member and distribution (public or claim required). |
| Business identity | Voucher code is unique (case-insensitive). |
| Status / lifecycle | Enabled or Paused. Each change creates a new immutable revision. |

**Entity: Review (reviews)**

| Field | Value |
| --- | --- |
| Purpose | A customer review of a product or workshop. |
| Key business attributes | Reviewer, target, rating, text, images and Manager reply. |
| Business identity | Each review has its own identifier; one review per eligible purchase or booking. |
| Status / lifecycle | Public → Hidden or Removed by a Manager; a hidden review can be restored. |

**Entity: Notification (notification_request)**

| Field | Value |
| --- | --- |
| Purpose | An email sent to a customer or staff member about a business event. |
| Key business attributes | Business event, template, recipient (masked), attempt count and next attempt time. |
| Business identity | The business event reference and template identify one notification. |
| Status / lifecycle | Pending → Processing → Sent; or Retry scheduled → Failed after the last attempt. |

**Entity: Consultation Conversation (consultation_conversations, consultation_messages)**

| Field | Value |
| --- | --- |
| Purpose | A chat between a Member and Staff about a product, workshop or order. |
| Key business attributes | Member, context, assigned branch, Staff who claimed it and messages. |
| Business identity | Conversation reference is unique. |
| Status / lifecycle | Open → Closed. |
