### 4.2 Use Cases (UC)

::: {.docx-table width="100%" columns="8%,17%,13%,17%,45%" repeat-header="false"}

| ID | Use Case | Actors | Feature | Description |
| --- | --- | --- | --- | --- |
| UC-01 | Sign In | All | Account Management | Authenticates a user by email/password or Google sign-in and resolves the platform account, role and permissions. |
| UC-02 | Reset Password | All | Account Management | Resets the password of a password account through the email-based recovery flow. |
| UC-03 | Browse and View Products | All | Product Management | Searches, filters and pages through published products, and opens a product's images, applied price and available quantity per size and branch. |
| UC-04 | Browse Public Reviews | All | Review Management | Reads published workshop-session and retail-product reviews and their rating aggregates. |
| UC-05 | View and Save Vouchers | All | Loyalty & Promotion Management | Views voucher cards and conditions in the Voucher hub and on product and package pages; only a signed-in Member saves vouchers to My vouchers. |
| UC-06 | Register Account | Guest | Account Management | Creates a Member account after successful registration by email/password or Google sign-in. |
| UC-07 | Chat with Basic AI Support | Guest, Member | Basic AI Support | Answers customer questions about products, workshops and store FAQs within approved public information. |
| UC-08 | Book Workshop | Guest, Member | Workshop Management | Browses workshop packages and their details, selects session sets and participant counts, and confirms a booking group with one ticket per person; the best automatic discount applies, and a Member may add one voucher and points. |
| UC-09 | Pay Workshop Deposit | Guest, Member | Workshop Management | Pays the booking-time amount of the booking group through VNPay; a group below 5,000 VND is confirmed without VNPay. |
| UC-10 | View My Booking | Guest, Member | Workshop Management | Opens a booking group by sign-in, by group code with one stored contact, or by the emailed Magic Link, and shows its tickets, status, amounts and QR codes. |
| UC-11 | Send Ticket to Recipient | Guest, Member | Workshop Management | The buyer sends a ticket to another participant by email, which issues a new QR code; a ticket can be sent online at most three times, and Staff or Manager may act for the buyer at the counter with the buyer proof. |
| UC-12 | Cancel Workshop Tickets | Guest, Member | Workshop Management | The buyer cancels one or more unused tickets, all or none; Staff or Manager may act for the buyer at the counter with the buyer proof. |
| UC-13 | Respond to Mland Cancellation Offer | Guest, Member | Workshop Management | When Mland cancels, the buyer chooses a replacement session or a refund; Staff or Manager may submit the choice at the counter with the buyer proof. |
| UC-14 | Submit Refund Account | Guest, Member | Workshop Management | The payer enters and confirms the receiving bank account through a single-use link; Staff or Manager may enter it at the counter. |
| UC-15 | View Ticket Folio | Guest, Member, Staff, Manager | Workshop Management | The ticket owner, assigned Staff or Manager views the bill, add-ons and remaining balance of a workshop ticket. |
| UC-16 | Change Ticket Sessions | Guest, Member, Staff, Manager | Workshop Management | The buyer, assigned Staff or Manager selects valid replacement sessions for a ticket; a late-arrival change is made only by Staff or Manager. |
| UC-17 | Configure Workshop Ring | Member | Workshop Ring Design Configuration | Selects a published ring model or a blank design, configures components and engraving, saves a version and receives an advisory workshop recommendation. |
| UC-18 | Request Custom Ring | Member | Design Request & Human Review; Personalized Design & Quote Workflow | Submits a description, up to five reference images and/or a saved ring design, then follows the review decision, the approved quote and the custom order. |
| UC-19 | Pay Custom Order Quote | Member | Personalized Design & Quote Workflow | Pays the complete Manager-approved quote through VNPay; the quote expires if payment is not verified before its deadline. |
| UC-20 | Chat with Staff Consultant | Member | Member Consultation Chat | Opens and continues a plain-text consultation with Staff or Manager, with or without a saved design or design request as context. |
| UC-21 | Update Shopping Cart | Member | Order Management & Fulfilment | Adds a product size, branch and quantity to the cart, changes quantities and removes lines. |
| UC-22 | Checkout Retail Order | Member | Order Management & Fulfilment | Revalidates the selected cart lines, applies the best discount, one voucher and points, chooses pickup or GHTK delivery, and creates one order per branch. |
| UC-23 | Pay for Retail Order | Member | Order Management & Fulfilment | Pays the full retail order through VNPay; only a verified signed VNPay result completes the sale. |
| UC-24 | Track My Orders | Member | Order Management & Fulfilment | Lists own retail orders, shows payment, fulfilment and recorded delivery steps, and cancels an unpaid order. |
| UC-25 | View Loyalty Points | Member | Loyalty & Promotion Management | Shows the own point balance, held and available points, point value and point history. |
| UC-26 | Create or Edit Own Review | Member | Review Management | Creates or edits one review for an eligible source: a checked-in workshop ticket or a picked-up or delivered retail order line. |
| UC-27 | Change Password | Member, Staff, Manager, Admin, Carrier | Account Management | Reauthenticates the owner of a password account and changes the password through Firebase. |
| UC-28 | Update Profile | Member, Staff, Manager, Admin | Account Management | Views and updates the own name, phone number, login email and shipping address book. |
| UC-29 | Record Customer Pickup | Staff | Order Management & Fulfilment | Records the handover of a ready pickup order and confirms the counter cash where it is due. |
| UC-30 | Submit Product or Price Proposal | Staff, Manager | Product Management | Submits a new product or a new price for an existing size for Manager approval. |
| UC-31 | Submit Custom Order | Staff, Manager | Personalized Design & Quote Workflow | Turns an accepted design request into a custom order and prepares its quote; a quote prepared by Staff goes to Manager approval, and a quote prepared by a Manager is approved on creation. |
| UC-32 | Manage Products | Staff, Manager | Product Management | Creates draft products with sizes and system-generated SKUs, manually or by Excel import, uploads images, updates content, applies Manager-approved prices, stops or resumes sale, and maintains stock per branch. |
| UC-33 | Manage Product Categories | Staff, Manager | Product Management | Creates, renames, activates and deactivates product categories with unique names. |
| UC-34 | Check In Participant | Staff, Manager | Workshop Management | Confirms attendance by scanning the ticket QR code or by manual code lookup, resends tickets to a customer who lacks them, records reason-coded overrides, and finalizes the session attendance so that unchecked tickets become no-show. |
| UC-35 | Add Folio Charge | Staff, Manager | Workshop Management | Adds a customer-consented add-on or variable charge to an open folio after the session starts. |
| UC-36 | Collect Ticket Balance | Staff, Manager | Workshop Management | Collects the remaining ticket balance by VNPay, cash or bank transfer; the folio is finalized when the balance reaches zero. |
| UC-37 | Close Branch Cash Day | Staff, Manager | Workshop Management | Submits the daily cash count of a branch; a Manager confirms any difference. |
| UC-38 | Decide Ticket Change Request | Staff, Manager | Workshop Management | Approves or rejects a queued ticket change request of the location. |
| UC-39 | Review Custom Design Request | Staff, Manager | Design Request & Human Review | Reviews a design request by hand and records the decision with an explanation. |
| UC-40 | Prepare Retail Order | Staff, Manager | Order Management & Fulfilment | Packs a paid retail order and marks it ready for customer pickup or for the GHTK courier. |
| UC-41 | Fulfil Custom Order | Staff, Manager | Order Management & Fulfilment | Records production of a fully paid custom order and prepares it for Member pickup or for the GHTK courier. |
| UC-42 | Hide Review | Staff, Manager | Review Management | Hides a published review from public view. |
| UC-43 | Handle Consultation Chat | Staff, Manager | Member Consultation Chat | Answers Member consultations; Staff works within the assigned location and Manager sees all locations. |
| UC-44 | Approve and Publish Product | Manager | Product Management | Accepts, adjusts or rejects with a reason a pending product or price proposal. |
| UC-45 | Change Product Price or Visibility | Manager | Product Management | Changes a size price directly, and hides or shows a whole product. |
| UC-46 | Manage Workshop Locations | Manager | Workshop Management | Creates, renames, readdresses, deactivates and reactivates workshop locations; a location in use by a future session cannot be deactivated. |
| UC-47 | Manage Workshop Packages | Manager | Workshop Management | Creates and updates workshop packages, add-ons and their availability per location. |
| UC-48 | Manage Workshop Schedule | Manager | Workshop Management | Creates sessions singly or in bulk with their capacity, and publishes, pauses or cancels them. |
| UC-49 | Assign Staff to Workshop Session | Manager | Workshop Management | Revokes or restores Staff assignments of a session; active Staff of the location are assigned automatically. |
| UC-50 | Configure Workshop Time Slot | Manager | Workshop Management | Defines the standard daily time slots of a location for workshop sessions. |
| UC-51 | Correct Attendance | Manager | Workshop Management | Reverses an erroneous check-in or corrects finalized attendance with a reason, within the allowed period. |
| UC-52 | Verify Transfer Payment | Manager | Workshop Management | Confirms a pending bank-transfer collection or marks it as failed. |
| UC-53 | Manage Handover Exception and Debt | Manager | Workshop Management | Approves handover before payment verification and records or closes the resulting debt. |
| UC-54 | Correct Ticket Folio | Manager | Workshop Management | Voids a folio line or collection, corrects a finalized overcharge, or records a cash reversal for a closed cash day. |
| UC-55 | Grant Extra Change Allowance | Manager | Workshop Management | Grants one extra counted session change to a ticket. |
| UC-56 | Process Refund Request | Manager | Workshop Management | Approves, records, confirms, refuses, closes as unreachable or reopens a refund request, and resends the refund-account link. |
| UC-57 | Replace Refund Account Key | Manager | Workshop Management | Replaces the key that protects stored refund bank accounts. |
| UC-58 | Review Late Payment | Manager | Workshop Management | Reviews a VNPay payment that arrived after the booking hold expired, records its full refund by bank transfer, and records or escalates the follow-up. |
| UC-59 | Reconcile Workshop Payments | Manager | Workshop Management | Reviews workshop bank-transfer and VNPay collections that need reconciliation, and records or voids an absorbed undercharge. |
| UC-60 | Revoke or Rotate Magic Link | Manager | Workshop Management | Revokes or rotates the Magic Link of a booking group after an access incident. |
| UC-61 | Review Workshop Audit Log | Manager | Workshop Management | Reviews the audit records of workshop operations and settlement, and looks up bookings across sessions for customer support. |
| UC-62 | Approve Custom Order Quote | Manager | Personalized Design & Quote Workflow | Approves or rejects the prepared quote of a custom order before the Member pays. |
| UC-63 | Reply to or Remove Review | Manager | Review Management | Replies to a published review or removes it with a reason. |
| UC-64 | Assign Consultation | Manager | Member Consultation Chat | Assigns an unassigned consultation to a location so that its Staff can answer it. |
| UC-65 | Manage Promotions | Manager | Loyalty & Promotion Management | Creates, edits, pauses and resumes automatic discounts and vouchers for products or workshop packages. |
| UC-66 | Manage Loyalty Point Policy | Manager | Loyalty & Promotion Management | Configures the earning rate, point value, minimum points per use and point-discount cap. |
| UC-67 | Reconcile Retail Payments | Manager | Order Management & Fulfilment | Views the daily confirmed retail cash per branch and the retail payments that need manual reconciliation. |
| UC-68 | View Dashboard | Manager | Manager Dashboard | Shows business analytics, workshop utilization, retail revenue, branch KPIs, today's operations and recent events. |
| UC-69 | Manage Email Templates and Delivery | Manager, Admin | Notification Management | Maintains email templates, and inspects, resends, redelivers or cancels notification requests. |
| UC-70 | Manage User Account | Admin | Account Management | Creates Staff and Manager accounts by invitation and Carrier accounts directly, searches, edits, assigns the fixed role and Workshop Location, suspends or restores accounts, and resolves Member email collisions. |
| UC-71 | Configure Ring Difficulty Policy | Admin | Administrative Parameter Configuration | Publishes a new version of the workshop-ring difficulty policy used to evaluate ring designs. |
| UC-72 | Resend Unknown-Outcome Email | Admin | Notification Management | Resends an email whose delivery outcome is unknown, without repeating the business action. |
| UC-73 | View Technical Audit Records | Admin, Manager | Administrative Parameter Configuration | Views masked technical and audit records of reviews, ring designs and design requests, without making business decisions. |
| UC-74 | Record Shipment Delivery | Carrier | Order Management & Fulfilment | Records courier pickup and delivery of GHTK shipments for retail and custom orders, and views delivery statistics. |

:::
