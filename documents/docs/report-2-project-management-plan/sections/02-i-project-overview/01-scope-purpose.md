## 1. Scope and Purpose

**Purpose.** The project builds the Personalized Product Sales and Workshop Booking System (PPSWBS) for Mland, a ring-making workshop. The system replaces chat-based coordination with one bilingual web application. Customers book workshops, buy ready-made rings, and request custom rings online. Staff and Managers run every branch from the same record.

**Scope.** V1 delivers the 13 features defined in Report 1, grouped by journey:

| Journey | Features | What is delivered |
|---|---|---|
| Workshop | FE-03, FE-12 | Package and session set-up, group booking with a per-ticket deposit, QR check-in, final payment, cancellation, session change, and refund handling; ring design with workshop recommendation |
| Ready-made retail | FE-02, FE-08 | Product catalogue with approval, cart, online or counter payment, store pickup or GHTK delivery |
| Custom ring | FE-04, FE-05, FE-13 | Design request with human review, quote, full online payment, production and delivery; consultation chat |
| Shared functions | FE-01, FE-06, FE-07, FE-09, FE-10, FE-11 | Accounts and roles, points and vouchers, email notifications, ring-difficulty policy, Manager dashboard, reviews |

**Exclusions.** V1 excludes HR management, inventory and production planning, accounting, gateway refunds, retail and custom-order cancellation, AI analysis and chat, CAD/3D tools, shipment tracking, and SMS or Zalo notifications. The reasons are listed in Report 1, Limitations & Exclusions.

**Relationship to other projects.** PPSWBS is a new, standalone system; it does not extend or replace another project. It connects to external services only: VNPay for payment, Firebase Authentication for sign-in, Cloudinary for images, GHTK for delivery fees, and Mland's SMTP server for email.
