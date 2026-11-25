### 3.3 Data Business Rules

The following rules define data constraints, lifecycle restrictions, historical preservation, and cross-entity consistency for the entities in sections 3.1 and 3.2 that are not fully visible in the conceptual ERD.

| Rule | Description | Enforced On |
|---|---|---|
| DBR-01: Account identity | Each Account has a unique Firebase UID. The login email is not used as the account identity. A Guest customer does not need an Account. | `accounts` |
| DBR-02: One fixed role per account | Each Account holds exactly one fixed role: Member, Staff, Manager, Carrier or Admin. V1 has no configurable roles or permissions. | `accounts` |
| DBR-03: Account lifecycle | An Account is Active, Pending setup or Suspended. Staff and Manager remain pending until their current invitation is used, the intended email is verified and assigned location scope is valid. A suspended Account loses protected access, but its history is kept. | `accounts` |
| DBR-04: Branch identity and availability | Each Branch has a unique branch code. An inactive Branch cannot receive new workshop sessions or new orders. | `workshop_locations`, `workshop_sessions`, `retail_orders` |
| DBR-05: Category uniqueness and visibility | A Product Category name is unique, ignoring case. An inactive Category is hidden from the public catalogue. | `catalogue_categories` |
| DBR-06: Product publication | A Product becomes Published only after a Manager approves its content and price. Deleting a Product is a soft delete that keeps its history. | `catalogue_products` |
| DBR-07: Variant identity | Each Product Variant SKU is unique, and a Product has at most one Variant per ring size. | `catalogue_variants` |
| DBR-08: Approved price and branch stock | A Variant price change takes effect only after a Manager approves the price proposal. Stock is held per Variant and per Branch. | `catalogue_variants`, `catalogue_variant_branch_stock` |
| DBR-09: One cart per Member | Each Member has at most one Cart. A Cart line identifies one Variant, one Branch and a quantity. | `retail_carts`, `retail_cart_lines` |
| DBR-10: Checkout idempotency | The idempotency key identifies one checkout command across retries. Repeating the same key must not create a second Retail Checkout. | `retail_grouped_checkouts` |
| DBR-11: Checkout lifecycle | A Retail Checkout moves from Accepted unpaid to Payment initiated, Paid and Completed, or ends as Expired or Cancelled when payment does not arrive before the payment deadline. | `retail_grouped_checkouts` |
| DBR-12: Order per branch | Each Retail Order belongs to exactly one Retail Checkout and is fulfilled by exactly one Branch. A checkout covering several branches creates one Retail Order per Branch. | `retail_orders`, `retail_grouped_checkouts` |
| DBR-13: Order line snapshot | A Retail Order Item freezes the unit price, quantity and line total at checkout. Later catalogue or price changes must not recalculate it. | `retail_order_items` |
| DBR-14: One shipment per order | A Retail Order has at most one Shipment, and its tracking number is unique. The Shipment moves from Preparing to Ready for carrier, Handed to carrier, In transit and Delivered. | `retail_shipments` |
| DBR-15: Bookable packages | A Workshop Package moves from Draft to Published to Retired. Only a Published package can be booked. | `workshop_packages` |
| DBR-16: Session identity and lifecycle | Each Workshop Session has a unique session reference. It moves from Draft to Published, Sales paused, In progress and Attendance finalized, or from Cancellation requested to Cancelled. | `workshop_sessions` |
| DBR-17: Add-on identity | Each Workshop Add-on has a unique reference and moves from Draft to Active to Retired. | `workshop_addons` |
| DBR-18: Booking group identity | Each Booking Group has a unique group code. It moves from Pending payment to Confirmed, or ends as Expired or Cancelled. | `booking_groups` |
| DBR-19: Ticket identity and states | Each Ticket has a unique ticket code and a unique QR reference and belongs to one Booking Group. Its booking state and financial state (Unpaid, Deposit paid, Fully paid, Refund pending, Refunded) are tracked separately. | `ticket_bookings` |
| DBR-20: Payment identity and target | Each Payment has a unique payment reference and a unique VNPay merchant reference, and pays exactly one target: a Booking Group, a Ticket folio or a Retail Checkout. | `group_payment_attempts`, `settlement_folio_collections` |
| DBR-21: Verified payment authority | A VNPay attempt becomes Success only from a verified, signed provider result. The browser return page alone cannot mark a payment as successful. A folio collection becomes Confirmed only after verification. | `group_payment_attempts`, `settlement_folio_collections` |
| DBR-22: One folio per ticket | Each Ticket has at most one Folio, with a unique bill reference. The Folio moves from Open to Finalized to Closed. | `settlement_final_bills` |
| DBR-23: Single ticket cancellation | A Ticket can be cancelled only once. Each Ticket Cancellation has a unique reference and is recorded once when the cancellation is accepted. | `settlement_ticket_cancellations` |
| DBR-24: Ticket change quota | Each Ticket Change has a unique reference. Customer-requested changes are limited by the package quota, counted by the number of changes used on the Ticket. | `settlement_ticket_changes`, `ticket_bookings` |
| DBR-25: Refund request lifecycle | Each Refund Request has a unique reference and stores the recipient bank account masked. It moves from Pending account to Pending approval, Pending transfer, Pending reconciliation and Refunded, or ends as Closed unreachable. | `settlement_refund_requests` |
| DBR-27: Design request ownership and lifecycle | Each Design Request has a unique reference and one owning Member. It moves from Active to Completed or Withdrawn; its review state is Need review, Accepted, Rejected or Clarification required. | `design_requests` |
| DBR-28: Custom order origin | Each Custom Order has a unique reference and is created only after its Design Request is accepted. Payment follows the approved quote. | `custom_orders`, `design_requests` |
| DBR-29: Ring design versioning | A Ring Design reference and version together identify one immutable design version. A change creates a new version instead of editing a Validated, Attached or Locked version. | `ring_designs` |
| DBR-30: One wallet per Member | Each Member has exactly one Loyalty Wallet. Its available and held points change only through Point Transactions. | `loyalty_wallet` |
| DBR-31: Append-only point ledger | Point Transactions are never edited. A mistake is corrected by a reversing entry. | `loyalty_point_ledger` |
| DBR-32: Promotion uniqueness and revisions | A voucher code is unique, ignoring case. Every change to a Promotion creates a new immutable revision; total and per-Member usage limits apply across revisions. | `promotion_program` |
| DBR-33: One review per eligible purchase | A Member may write one Review per eligible purchase or workshop booking. Staff or a Manager may hide a Review and only a Manager may remove it; a hidden Review can be restored. | `reviews` |
| DBR-34: Notification deduplication | One original request is accepted for each business event, template, recipient role and recipient fingerprint. An eligible resend is a separate request linked to the original. Recipient display is masked. | `notification_request` |
| DBR-35: Consultation identity and lifecycle | Each Consultation Conversation has a unique reference, one Member and an assigned Branch, and moves from Open to Closed. | `consultation_conversations`, `consultation_messages` |
| DBR-36: Append-only audit evidence | Audit records of business actions are append-only during normal operations and are retained for five years. Privacy controls may restrict access without rewriting the original event. | Audit records for all entities above |
| DBR-37: AI chat retention | Basic AI (Gemini) chat prompts and responses are retained for at most 30 days. Design-request images are never sent to the AI provider. | AI chat records (outside the conceptual ERD) |
| DBR-38: Internal invitation version | Each Staff or Manager invitation belongs to one pending Account and bound Firebase UID. A resend creates a new version, invalidates the old single-use link and starts a new 48-hour period. Only a secret digest and key version are retained; the raw setup link is not stored in notification records or audit. | `accounts`, invitation records, `notification_request` |
