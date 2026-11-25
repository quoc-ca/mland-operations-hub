## 1. Software Architecture

{{r4-software-rchitecture width=100% align=center}}

### Architecture component descriptions

::: {.docx-table width="100%" columns="8%,27%,65%" repeat-header="false"}

|No|Component|Description|
|---|---|---|
|**Client Tier**|||
|01|Web Browser|Entry point for every actor (Guest, Member, Staff, Manager, Admin and the GHTK partner account, role Carrier). Loads the React SPA over HTTPS.|
|02|app (router · layouts)|Application shell: React Router 7 route table, role-based layouts and session guards that decide which screens each role can open.|
|03|features (pages)|Screens grouped by business module (account, product management, retail order, ring design configuration, design review, consultation chat, loyalty and promotion, review, notification, manager dashboard, operator home); the workshop and workshop settlement screens are kept in their own folders beside `features`.|
|04|shared/ui|Reusable UI components shared by all screens: top bar, operations sidebar, paginator, common controls and messages.|
|05|shared/api|HTTP client that calls the backend REST API with JSON and attaches the signed-in user's Firebase ID token as a Bearer token.|
|06|shared/auth|Sign-in and session handling with the Firebase JS SDK; resolves the user's role and home screen.|
|07|shared/i18n|Vietnamese and English interface texts for domestic and international customers.|
|08|shared/payment|Keeps and recovers the payment state when the customer returns from the VNPay payment page.|
|**Application Tier**|||
|01|Security Filter Chain|Spring Security chain in front of every request: adds a correlation ID, verifies the Firebase ID token, blocks suspended or unprovisioned accounts (IdentityGateFilter) and controls access to each endpoint. Only explicitly listed endpoints are public, for example guest booking actions, public reviews and the VNPay callbacks.|
|02|Controller Layer|REST controllers (`/api/**`) that receive requests, validate request DTOs and delegate to the service layer; errors are returned in one `ErrorResponse` format.|
|03|Service Layer|Business rules and transactions of each module (XxxService). Each module exposes a Facade (XxxFacade) as its only public API for other modules.|
|04|Repository Layer|Spring Data JPA repositories (dao) that read and write the module's own tables.|
|05|Entity Layer|JPA entities that map domain objects to MySQL tables.|
|06|Scheduled Jobs|Background jobs (`@Scheduled`) such as expiring unpaid holds and retail orders, recovering deposit status with VNPay QueryDR, starting workshop sessions and cleaning up retained data.|
|07|Domain Events|Spring Modulith application events used for loose coupling between modules (for example booking confirmed, attendance finalized, notification ready); the event publication registry stores them so they are not lost.|
|08|Business Modules|Spring Modulith modular monolith: Account Management, Product Management, Ready-ring Sales, Fulfilment, Custom Manufacturing, Workshop Ring Design Configuration, Workshop Booking, Workshop Operations, Workshop Settlement, Payment Integration, Loyalty & Promotion, Review Management, Staff Consultation Chat, Notification Management, Reporting and Human Design Review.|
|09|Integration Adapters|Ports and adapters that isolate external services: Firebase Admin SDK, VNPay client, GHTK shipping fee client, Cloudinary storage adapters, SMTP mail adapter, Gemini REST client adapter.|
|10|Common Layer|Cross-cutting code: configuration (security, Firebase, scheduling, time zone), the correlation ID filter, the common error response, and shared events and ports (booking, payment, notification, settlement, workshop).|
|**Data Tier**|||
|01|MySQL|Relational database for all modules, accessed through Spring Data JPA / Hibernate; schema versions are managed with Flyway migrations.|
|**External Services**|||
|01|Firebase Authentication|Identity provider: the browser signs in and gets an ID token; the backend verifies it with the Firebase Admin SDK.|
|02|VNPay Payment Gateway|Online payment for deposits, final payments and retail orders: payment URL, IPN callback, QueryDR status check and refund, signed with HMAC-SHA512.|
|03|Cloudinary|Stores and serves product and catalogue images, and stores refund receipt images.|
|04|SMTP Mail Server|Sends booking and notification emails (Gmail SMTP by default). Real sending is switched on by configuration; otherwise emails are only captured inside the application.|
|05|Google Gemini API|Basic AI Support text capability: it answers approved public-information questions. Reference image analysis is not used. The code still contains an AI price suggestion endpoint, which is outside the current specification.|
|06|Retired|No partner booking platform is integrated (Stakeholder statement, Feature-003 owner, 2026-11-25); the number is not reused.|
|07|GHTK|Giao Hang Tiet Kiem: the delivery fee of a retail branch order is quoted by API (50 g per ring). V1 does not create shipments or receive webhooks; the shipment record is kept in the platform and its courier steps are recorded by the Carrier account.|

:::
