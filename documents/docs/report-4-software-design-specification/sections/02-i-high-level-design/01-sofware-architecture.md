## 1. Software Architecture

(Software architecture diagram — source: `images/software-architecture.drawio`, export: `images/software-architecture.png`)

### Architecture component descriptions

::: {.docx-table width="100%" columns="8%,27%,65%" repeat-header="false"}

|No|Component|Description|
|---|---|---|
|**Client Tier**|||
|01|Web Browser|Entry point for every actor (Guest, Member, Staff, Manager, Admin). Loads the React SPA over HTTPS.|
|02|app (router · layouts)|Application shell: React Router 7 route table, role-based layouts and session guards that decide which screens each role can open.|
|03|features (pages)|Screens grouped by business module (account, product management, retail order, workshops, loyalty and promotion, review, design review, OTA connectivity, operations home).|
|04|shared/ui|Reusable UI components shared by all screens: top bar, operations sidebar, paginator, common controls and messages.|
|05|shared/api|HTTP client that calls the backend REST API with JSON and attaches the signed-in user's Firebase ID token as a Bearer token.|
|06|shared/auth|Sign-in and session handling with the Firebase JS SDK; resolves the user's role and home screen.|
|07|shared/i18n|Vietnamese and English interface texts for domestic and international customers.|
|08|shared/payment|Keeps and recovers the payment state when the customer returns from the VNPay payment page.|
|**Application Tier**|||
|01|Security Filter Chain|Spring Security chain in front of every request: adds a correlation ID, verifies the Firebase ID token, blocks suspended or unprovisioned accounts (IdentityGateFilter) and checks the role allowed for each endpoint.|
|02|Controller Layer|REST controllers (`/api/**`) that receive requests, validate request DTOs and delegate to the service layer; errors are returned in one `ErrorResponse` format.|
|03|Service Layer|Business rules and transactions of each module (XxxService). Each module exposes a Facade (XxxFacade) as its only public API for other modules.|
|04|Repository Layer|Spring Data JPA repositories (dao) that read and write the module's own tables.|
|05|Entity Layer|JPA entities that map domain objects to MySQL tables.|
|06|Scheduled Jobs|Background jobs (`@Scheduled`) such as expiring unpaid holds and retail orders, recovering deposit status with VNPay QueryDR, starting workshop sessions and cleaning up retained data.|
|07|Domain Events|Spring Modulith application events used for loose coupling between modules (for example booking confirmed, attendance finalized, notification ready); the event publication registry stores them so they are not lost.|
|08|Business Modules|Spring Modulith modular monolith: Account Management, Product Management, Ready-ring Sales, Fulfilment, Workshop Booking, Workshop Operations, Workshop Settlement, Payment Integration, Loyalty & Promotion, Review Management, Notification Management, OTA Connectivity, Reporting and Design Review (AI).|
|09|Integration Adapters|Ports and adapters that isolate external services: Firebase Admin SDK, VNPay client, Cloudinary media storage adapter, SMTP mail adapter, Gemini REST client adapter, OTA adapters (Klook, GetYourGuide).|
|10|Common Layer|Cross-cutting code shared by all modules: configuration (security, Firebase, timezone), validation, exception handling, DTOs and shared ports (booking, payment, workshop).|
|**Data Tier**|||
|01|MySQL|Relational database for all modules, accessed through Spring Data JPA / Hibernate; schema versions are managed with Flyway migrations.|
|**External Services**|||
|01|Firebase Authentication|Identity provider: the browser signs in and gets an ID token; the backend verifies it with the Firebase Admin SDK.|
|02|VNPay Payment Gateway|Online payment for deposits, final payments and retail orders: payment URL, IPN callback, QueryDR status check and refund, signed with HMAC-SHA512.|
|03|Cloudinary|Stores and serves product and catalogue images.|
|04|SMTP Mail Server|Sends booking and notification emails (Gmail SMTP by default).|
|05|Google Gemini API|AI support for the design review module: analysing reference images, suggesting prices and answering AI chat requests.|
|06|OTA Platforms|Klook and GetYourGuide: workshop inventory is pushed to the platforms and booking or cancellation events are received from them.|

:::
