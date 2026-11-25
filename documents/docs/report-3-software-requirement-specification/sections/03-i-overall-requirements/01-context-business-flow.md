## 1. Context Diagram

The Personalized Product Sales and Workshop Booking System is an operations platform for a ring atelier. It supports public discovery, workshop booking and settlement, workshop ring-design configuration, retail sales with store pickup or GHTK delivery, Member custom design requests, and shop-side fulfilment. It is not a warehouse-management, ERP or accounting system. The governing V1 scope and decision evidence are in [Report 2: Scope and Purpose](../../../report-2-project-management-plan/sections/02-i-project-overview/01-scope-purpose.md) and [Evidences](../../../report-2-project-management-plan/sections/02-i-project-overview/07-evidences.md).

The platform serves five roles and one partner account:

- **Guests** browse products, workshop packages, sessions, promotions and public reviews, use basic AI chat support, register, and book and pay a workshop deposit without member benefits.
- **Members** additionally maintain their profile and address book, place retail orders, use vouchers and loyalty points, configure and save ring designs, submit custom design requests and pay approved quotes, write reviews, and chat with Staff or with basic AI support.
- **Staff** work at one assigned location. They maintain products and branch stock, check in participants and collect balances at the counter, review design requests and prepare quote inputs, record the production and handover of paid custom orders, prepare and hand over retail orders, hide reviews, and answer consultation chats.
- **Managers** run workshops (sessions, slots, capacity, Staff assignment, cancellations, refunds and changes), approve products and prices, manage promotions, vouchers and the point policy, manage email templates, approve design quotes, moderate and reply to reviews, assign chats, and view the dashboard and payment reconciliation.
- **Admins** manage accounts, roles, Staff locations and operational parameters. They have no business authority.
- **The Carrier (GHTK Partner)** records courier pickup and delivery of GHTK shipments and views delivery statistics.

There are two deliberately separate customer journeys around design:

- **Workshop ring design.** A signed-in Member configures a ring from system-provided options, saves it and sees a recommended workshop. A design is not attached to a workshop booking.
- **Custom design request.** A signed-in Member submits a description, reference images after consent, and optionally a saved design. Staff or Manager reviews it by hand with an explanation; no AI is involved. An accepted request receives a quote that the Manager approves, and the Member pays the complete quote in one VNPay payment. There is no deposit, cart line or later balance, and an unpaid quote expires.

All online payment is through VNPay: workshop deposits, retail checkout and custom design quotes. Only a verified, signed VNPay result, or a signed QueryDR status check, changes a payment state; the browser return page is display-only. Pickup orders may also be paid in cash at the counter, and workshop balances may be collected at the counter in cash, through VNPay or by bank transfer. Refunds are manual bank transfers approved by a Manager.

For retail delivery, the platform obtains a GHTK fee quote at checkout (the only GHTK API call), keeps the shipment record with an internal tracking number and records the courier's steps through the GHTK partner account; cash on delivery is not used. Staff prepares the order and marks it ready for the carrier, and the Carrier records pickup and delivery.

The Mail Gateway delivers account invitations, workshop booking confirmations with QR tickets, and retail order confirmations. Firebase Authentication provides sign-in, verification and password recovery; the platform decides account status, role and authorization from its own database. Cloudinary stores product images and uploaded files. Provider connections and credentials are environment configuration.

Mland uses the paid Gemini API only for basic AI chat support: the platform sends the Guest's or Member's chat request and returns the response. Answers are limited to approved public information and FAQs; the AI cannot confirm transactions or make business decisions. AI is not used to analyze design images, suggest prices or review design requests; design-request images are reviewed by Staff or Manager and are never sent to an AI provider.

V1 has no AI pricing, and no Google Maps/Places or external review integration; Mland is the sole source of truth for workshop sessions and capacity.

{{r3-context-diagram width=100% align=center}}
