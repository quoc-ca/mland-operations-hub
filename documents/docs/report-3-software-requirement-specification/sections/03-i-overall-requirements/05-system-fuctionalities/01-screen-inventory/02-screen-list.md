#### b. Screen List

| # | Screen Name | Feature | Description |
| --- | --- | --- | --- |
| 1 | Home Page | Public Information | Introduces the shop and provides access to sign-in, the product catalogue and reviews. |
| 2 | User Login | Authentication | Allows users to sign in and reach the dashboard corresponding to their current role. |
| 3 | User Register | Authentication | Allows visitors to create a Member account using supported email registration or SSO. |
| 4 | Email Verification | Authentication | Allows users to verify their email registration before completing account activation. |
| 5 | Forgot Password | Authentication | Allows users to request password recovery without revealing whether an account exists. |
| 6 | Reset Password | Authentication | Allows eligible users to set a new password through a valid recovery link. |
| 7 | SSO Provider | Authentication | Provides external identity-provider sign-in or registration and returns the authentication result to the system. |
| 8 | User Profile | Account Management | Displays the signed-in user's permitted profile information and available account actions. |
| 9 | Change Password | Account Management | Allows eligible users to change a locally managed password; provider-managed credentials remain with the identity provider. |
| 10 | Product Catalogue | Product Management | Allows visitors and Members to browse published products, search by name, filter by category, price and stock, sort results, see each card's price after the best automatic discount and voucher, and add a variant to the cart from a Quick add box. |
| 11 | Product Details | Product Management | Displays product images, sizes with available quantity, applied and discounted prices (recalculated for the chosen branch), applicable offers, the size conversion chart and reviews; allows choosing size, branch and quantity to add to the cart or buy now.
| 12 | Review List | Public Information | Displays available reviews for visitors to browse. |
| 13 | Member Dashboard | Dashboard | Provides access to product shopping, workshop booking, custom orders, personal histories and loyalty. |
| 14 | Staff Dashboard | Dashboard | Provides access to authorized product maintenance, workshop check-in, design review, order operations and final-amount proposals. |
| 15 | Manager Dashboard | Dashboard | Provides access to revenue, workshop management, design review, price approvals, promotions and final-amount approval. |
| 16 | Admin Dashboard | Dashboard | Provides access to accounts, permissions, parameters, integrations and technical support; excludes revenue and business approvals. |
| 17 | Shopping Cart | Order Management and Fulfillment | Allows Members to review their own cart, update variant quantities and remove items; prices remain indicative. |
| 18 | Retail Checkout | Order Management and Fulfillment | Allows Members to select a processing branch and review the entire cart, eligible benefits and payable amount before submission. |
| 19 | Workshop Package List | Workshop Booking | Displays Signature, Silver Clay and Wax workshop packages for browsing and selection. |
| 20 | Workshop Package Details | Workshop Booking | Displays the selected package's description and applicable terms, with access to booking and the relevant design path. |
| 21 | Location and Session Selection | Workshop Booking | Allows users to select a suitable branch and available workshop attendance date and session. |
| 22 | Booking Information Form | Workshop Booking | Collects required contact and participant information for the selected workshop booking. |
| 23 | Booking Summary | Workshop Booking | Allows users to review the package, branch, session, participants, selected design and deposit before confirming the booking. |
| 24 | My Retail Orders | Order Management and Fulfillment | Allows Members to browse their own retail purchase history and open order details. |
| 25 | Retail Order Details | Order Management and Fulfillment | Displays frozen purchase terms, payment, hold and fulfilment status, with eligible payment and fulfilment actions. |
| 26 | My Booking | Workshop Booking | Allows Members to browse their own workshop bookings and open booking details. |
| 27 | Booking Details / QR Code | Workshop Booking | Displays booking and verified payment information and shows the check-in QR code only after booking confirmation. |
| 28 | My Custom Orders | Custom Manufacturing | Allows Members to browse their own custom orders and access order details or a new request. |
| 29 | Custom Order Details | Custom Manufacturing | Displays the Member's custom-order progress, complete Manager-approved quote, one-time payment status, expiry outcome and recorded handoff information. |
| 30 | New Custom Order | Custom Manufacturing | Allows Members to submit design, material, contact and applicable completion-date information for a shop-manufacturing request. |
| 31 | My Loyalty | Loyalty and Promotion Management | Displays the Member's available, total and held points and the current point policy, with access to point history and saved vouchers.
| 32 | Point History | Loyalty and Promotion Management | Allows Members to review their own point transactions (earned, held, spent, released and refunded) with their source and balance changes.
| 33 | Payment Gateway Redirect | Payment | Routes an eligible retail, workshop-deposit or one-time custom-order quote payment to the configured payment gateway. |
| 34 | Payment Page | Payment | Provides the external payment gateway interface for completing the selected payment. |
| 35 | Payment Result | Payment | Displays system-verified payment status and links to the relevant booking or order; browser return alone does not confirm payment. |
| 36 | Design Path Selection | Design Customization | Allows users to choose an existing ring design or supported ring configuration. The image-based design path is visible only to an eligible Member and requires the booking context and consent flow. |
| 37 | Existing Ring Design Selection | Design Customization | Allows users to browse and select an available shop ring design for their workshop. |
| 38 | Image Upload Consent Form | Design Customization | Allows an eligible Member to review and actively record versioned consent before temporary reference-image storage or AI processing. |
| 39 | AI Feature Analysis | Design Customization | Displays candidate features and image-quality information returned by AI, the current analysis state, estimate context, retry guidance, and the required Staff/Manager human-review status. |
| 40 | Reference Image Upload | Design Customization | Allows an eligible Member to upload a reference image after recorded consent and receive file, ownership, context, quality, and rate-limit validation feedback. |
| 41 | Ring Configuration | Design Customization | Allows users to build a ring design using supported configuration choices. |
| 42 | Parameter Customization | Design Customization | Allows users to adjust supported design parameters and review the updated price estimate and difficulty. |
| 43 | Ring Components | Design Customization | Allows users to select supported components for their customized ring. |
| 44 | Ring Attachment | Design Customization | Allows users to configure supported attachments for the selected ring design. |
| 45 | Ring Engraving | Design Customization | Allows users to specify supported engraving options for the selected ring design. |
| 46 | Design Review Status and Quote | Design Customization | Displays analysis status, the current estimate context, Staff/Manager review status, Accepted/Rejected/Clarification Required outcome, reason, and the permitted next action. It never treats AI output alone as approval or final price. |
| 47 | Product Management List | Product Management | Allows Staff and Managers to browse managed products by name or SKU, category and branch in counted status tabs (All, Drafts, Products to review, Rejected, Ready to publish, Published, Hidden, Prices to review) with a stock column; Managers can publish, hide, show again or open submissions for review, while Staff can stop or resume sale at their own branch and send price drafts or new prices for rejected ones.
| 48 | New Product | Product Management | Allows Staff and Managers to enter product content, selling branches, sizes, prices, per-branch quantities and images; Staff prices stay proposals, Manager prices apply on creation.
| 49 | Product Management Details | Product Management | Allows Staff and Managers to maintain content, images, branch quantities and sale state, add selling branches and new sizes, manage price proposals and price history, submit for initial review or delete while protecting holds; only Managers publish, hide or show the product again.
| 50 | Price Proposal Form | Product Management | Allows Staff to propose new prices per size or for several sizes, save drafts and submit them for Manager review, and Managers to change the price of a size of a published product in one step (Change price); approved prices are applied as a separate step and submission never changes the applied price.
| 51 | Workshop Session List | Workshop Management | Allows authorized Staff to browse workshop sessions within their permitted scope. |
| 52 | Workshop Session Details | Workshop Management | Displays permitted session information and provides access to participant check-in. |
| 53 | Participant Check-in | Workshop Management | Allows authorized Staff to verify a confirmed booking by QR or manual lookup and record actual attendance. |
| 54 | Booking Design Review List | Design Review | Lists design requests within the Staff or Manager reviewer's authority and provides access to their details. |
| 55 | Booking Design Review Details | Design Review | Allows authorized Staff or Managers to inspect the submitted design and estimate and record a decision within their review authority. |
| 56 | Retail Order List | Order Management and Fulfillment | Allows Staff to browse retail orders within assigned branches. |
| 57 | Retail Order Operations | Order Management and Fulfillment | Allows authorized Staff to record valid preparation, readiness, owner pickup and manual carrier handoff without a manual paid override. |
| 58 | Custom Order List | Custom Manufacturing | Allows authorized Staff to browse custom orders within their permitted processing scope. |
| 59 | Custom Order Operations | Custom Manufacturing | Displays permitted custom-order progress, proposal status and eligible handoff actions, with access to final-amount proposal. |
| 60 | Final Amount Proposal | Custom Manufacturing | Allows Staff to prepare, revise and submit the final-amount proposal to Manager; Staff cannot finalize it or request payment before approval. |
| 61 | Executive Revenue Dashboard | Revenue Reporting | Allows Managers only to view revenue within permitted periods and branches, with missing or delayed data identified. |
| 62 | Workshop Schedule | Workshop Management | Allows Managers to browse the workshop schedule and open session creation or details. |
| 63 | New Session Form / Workshop Session Details | Workshop Management | Allows Managers to create, inspect or edit workshop sessions while protecting existing bookings and holds. |
| 64 | Staff Assignment | Workshop Management | Allows Managers to assign eligible Staff to workshop sessions after checking assignment conflicts. |
| 65 | Workshop Configuration | Workshop Management | Allows Managers to configure time slots, capacity, branch materials and options, and holiday or off-day exceptions. |
| 66 | Prices to Review | Price Approval | Lists, in the Prices to review tab of the Product Management List and in product details, the price proposals of published products waiting for a decision, unsent drafts and rejected prices, with submitter, branch and price history; Managers start approval or rejection per size or in bulk, Staff only follow their proposals.
| 67 | Price Proposal Review | Price Approval | Allows Managers to approve a proposal at the proposed or an adjusted price, optionally applying it immediately, or reject it with a required reason.
| 68 | Promotion List | Loyalty and Promotion Management | Allows Managers to browse vouchers and automatic discounts by scope and state and open their details or create a new programme.
| 69 | Promotion Form | Loyalty and Promotion Management | Allows Managers to create a voucher or automatic discount for products or workshops with its discount, validity, limits, points setting, targets and, for a voucher, its distribution (Public or Must be saved).
| 70 | Final Amount Approval List | Custom Manufacturing | Allows Managers to browse final-amount proposals submitted by Staff and select one for final review. |
| 71 | Final Amount Review / Approval | Custom Manufacturing | Allows Managers to approve the final amount and release the matching payment request, or return the proposal to Staff for revision. |
| 72 | User Account List | Account Administration | Allows Admins to browse user accounts and open account creation or details. |
| 73 | New User / User Account Details | Account Administration | Allows Admins to create accounts, inspect permitted account information and edit allowed fields without implicitly changing roles or status. |
| 74 | Assign Role / Lock Confirmation | Account Administration | Allows Admins to confirm a permitted role assignment or account lock action separately from account editing. |
| 75 | Role / Permission Configuration | Administrative Configuration | Allows Admins to configure supported role permissions and access policies. |
| 76 | Operational Parameters | Administrative Configuration | Allows Admins to validate and save supported system parameters. |
| 77 | Integration List | Integration Administration | Allows Admins to browse configured integrations and maintain supported settings with masked secrets and safe connection checks. |
| 78 | Technical Order Support | Technical Support | Allows Admins to view minimized order and payment diagnostics without customer details, impersonation, business mutation or paid override. |
| 79 | Eligible Review Sources | Review Management | Allows a Guest to look up eligible sources by email/phone or a Member to view own checked-in workshop attendance and picked-up/delivered retail order lines. |
| 80 | Create Review | Review Management | Collects a 1–5 rating and optional text up to 2,000 characters; valid submission is immediately PUBLIC. |
| 81 | Edit Published Review | Review Management | Allows the review owner to edit their own public review while recording a revision/audit entry. |
| 82 | Public Review List and Aggregate | Review Management | Lists PUBLIC reviews, supports target/rating filters, masks names and calculates aggregates from PUBLIC rows only. |
| 83 | Review Moderation Queue | Review Management | Allows Staff/Managers to open authorized post-publication review actions. |
| 84 | Review Moderation Details | Review Management | Allows Staff to HIDE and Managers to HIDE/REMOVE with reason and audit evidence. |
| 85 | Manager Reply Editor | Review Management | Allows one current Manager reply per review and audited reply edits; no threaded replies. |
| 86 | Product Categories | Product Management | Allows Staff and Managers to search categories by name, filter them by status, and create, edit, deactivate and reactivate product categories after reviewing affected products. |
| 87 | Import Products from Excel | Product Management | Allows Staff and Managers to download a branch-specific template, validate an .xlsx file, review row errors, import products as unpublished drafts and attach images. |
| 88 | Voucher Store | Loyalty and Promotion Management | Allows visitors and Members to browse the vouchers that must be saved before use, by scope, and lets Members save them; each save claims one use. |
| 89 | Voucher Details | Loyalty and Promotion Management | Displays a voucher's discount and full conditions; lets Members save a voucher that must be saved, while a public voucher needs no saving. |
| 90 | My Saved Vouchers | Loyalty and Promotion Management | Allows Members to review their saved vouchers by scope, separated into usable and expired or unusable, and go to products or workshops to use them. |
| 91 | Promotion Details | Loyalty and Promotion Management | Allows Managers to view, edit, pause or resume a programme and follow its usage and total discount. |
| 92 | Point Policy | Loyalty and Promotion Management | Allows Managers to view and change the loyalty-point earning and spending policy, applied only to transactions accepted after saving. |
