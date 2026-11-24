## 1. Major Features {#major-features}

| ID | Feature Name | Description | Addressed Gaps |
| --- | --- | --- | --- |
| FE-01 | Account Management | Handles registration, authentication, profile management, and RBAC across Guest, Member, Staff, Manager, and Admin Technical roles. | GAP-06 |
| FE-02 | Product Management | Manages published ready-made rings, variants, prices, publication status, and manually maintained available-to-sell quantities. Ready-made sales use full payment; custom orders are handled separately. | GAP-01, GAP-05 |
| FE-03 | Workshop Management | Manages workshop packages, two configurable branches, three daily session templates, group booking, approved deposit, capacity, attendance, check-in, continuation, custody, and settlement. | GAP-02, GAP-04 |
| FE-04 | Design Request & Human Review | Allows an active Member to submit a description and/or up to five images. Guest users can see the entry point but must sign in before submission. Image quality is validated before submission. Staff or Manager accepts, rejects, or requests clarification with a mandatory explanation. No AI or automated decision is included. | GAP-03 |
| FE-05 | Personalized Design & Quote Workflow | Connects Feature-012 design snapshots with eligible Member custom-manufacturing requests, human review, Manager-approved quotes, one-time full payment, and production/fulfilment tracking. It does not use a retail cart or deposit. | GAP-01, GAP-03, GAP-04 |
| FE-06 | Loyalty & Promotion Management | Tracks transaction histories, reward points, membership progression, promotions, and Member vouchers according to the approved scope. | GAP-07 |
| FE-07 | Notification Management | Centralizes approved email notifications such as booking/payment confirmation, review result, quote availability, order status, pickup readiness, and internal approval requests. | GAP-04, GAP-05 |
| FE-08 | Order Management & Fulfilment | Manages ready-made and eligible paid custom-order fulfilment through pickup or permitted Staff-recorded carrier handoff. It excludes carrier integration and live shipment tracking. | GAP-04 |
| FE-09 | Administrative Parameter Configuration | Separates Manager-owned business and financial parameters from Admin Technical accounts, RBAC, credentials, monitoring, and technical audit. | GAP-06 |
| FE-10 | Manager Dashboard | Provides role-based monitoring of bookings, attendance, retail and custom orders, payments, fulfilment, review queues, quotes, and exceptions. | GAP-04 |
| FE-11 | Review Management | Allows Guests to read public reviews and active Members to create or edit reviews for eligible workshop sessions and retail products. Staff and Manager perform the approved moderation and reply actions. Google review data is not written back. | GAP-05 |
| FE-12 | Workshop Ring Design Configuration | Allows an active Member to configure and save a workshop-ring design from approved options, evaluate it against server rules, and receive an advisory workshop recommendation. Guest users can see the entry point but must sign in before configuration. It does not attach designs to bookings. | GAP-01, GAP-02 |

Feature-013 Staff Consultation Chat is documented separately as a supporting draft dependency. It is not counted as an approved Major Feature in this Report 1 baseline.
