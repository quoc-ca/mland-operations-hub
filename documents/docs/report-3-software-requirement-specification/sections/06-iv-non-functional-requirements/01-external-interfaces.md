## 1. External Interfaces

Scope follows Report 2 and Report 3's [External API Inventory](../03-i-overall-requirements/05-system-fuctionalities/02-external-api-inventory.md). Provider documentation in [Report 2 Evidences](../../../report-2-project-management-plan/sections/02-i-project-overview/07-evidences.md) supports capability checks; Mland decisions determine the capabilities V1 uses.

### 1.1 User and hardware interfaces

| ID | Requirement | Verification criterion |
| --- | --- | --- |
| NFR-IF-01 | Public pages and authenticated Member/shop functions shall use the web interface, with server-enforced access rules on direct requests. | Public browsing requires no sign-in; protected direct requests receive the same checks as UI navigation. See UC1–UC18 and UC52–UC61. |
| NFR-IF-02 | Workshop check-in shall support QR scanning and manual code entry; scanning alone shall not record attendance without Staff verification. | UC24 works with either input; invalid, unconfirmed or already checked-in bookings create no new attendance effect. |

V1 does not mandate a dedicated scanner, terminal, printer or native mobile application. QR scanning uses equipment supported by the selected browser environment; coverage remains NFR-OPEN-01. Card entry takes place at VNPay rather than through a Mland card-data interface.

### 1.2 Software and service interfaces

| ID | Interface and direction | Required boundary | Verification criterion |
| --- | --- | --- | --- |
| NFR-IF-03 | VNPay: Mland → initiation/QueryDR; VNPay → signed IPN; browser → Return URL. | Sole provider for workshop deposits, retail full payment and one-time custom-order quote payment. Only validated signed IPN or eligible signed QueryDR authorizes success; Return URL is informational. | Tampered, mismatched, duplicate, unknown and late evidence follows NFR-SEC-04 and NFR-REL-02/03. Browser-only success changes no payment state. |
| NFR-IF-04 | OTA partners (Klook and GetYourGuide candidates): availability publishing, booking and confirmed-cancellation exchange. | Mland owns capacity and tickets; each partner has its own adapter and cannot overwrite them. Only a confirmed partner cancellation releases capacity. No partner is enabled before its go-live gates are evidenced. | UC91–UC93 and partner callbacks reject unauthenticated or ineligible bookings, deduplicate events, open a reconciliation item for unclear evidence and preserve Mland capacity on synchronization failure. |
| NFR-IF-05 | Paid Gemini: Mland → permitted text prompt and public information; Gemini → text answer. | Basic text support only. No image analysis or price suggestion; Gemini does not receive Feature-004 images. AI cannot confirm transactions, publish prices or decide custom manufacturing. | UC33 rejects unsupported requests/unusable results; failure creates no business effect. Apply NFR-DAT-01 cleanup. |
| NFR-IF-06 | Retired: Google Maps Embed/Places is not used. | Not applicable; removed from V1 (confirmed 2026-11-25). No Google Maps/Places request, credential, review link, rating or review data is used. | Inspect configuration, routes and network calls for absence of the retired integration. |
| NFR-IF-07 | Mland-domain SMTP / Mail Gateway: Mland → internal invitations and business messages. | Staff/Manager setup invitations, QR tickets, payment requests/receipts, design-review results and order updates as required by workflows. Firebase sends Member verification and password-recovery messages. No notification centre or email-driven approval. | A failed or uncertain invitation leaves the account pending. Failed notices after committed business actions do not reverse those actions; see NFR-REL-05. |
| NFR-IF-08 | Configured identity/SSO service: provider ↔ browser/application identity flow. | Provider establishes identity; Mland resolves current status, role, ownership, branch and delegation. | Invalid tokens/suspended accounts grant no protected access. SSO claims cannot assign Staff/Manager/Admin privileges. See UC1/UC2 and NFR-SEC-01/02. |
| NFR-IF-09 | Cloud Storage: authorized media upload/read/delete. | Separate public catalogue assets and private custom references by disclosure/retention. | Unauthorized requests cannot read private media. NFR-DAT-02/03/07 govern deletion without deleting referenced catalogue/history media. |
| NFR-IF-10 | GHTK: Mland → fee query; shop actor and GHTK partner account → Mland shipment record. | Fee query only; no shipment-creation, tracking or webhook API. The platform creates the tracking number, Staff marks the order ready for the courier and the partner account records pickup and delivery. No delivery-failure, return, cancellation or refund workflow. | UC61 changes shipment steps only in order and by the permitted actor. If the fee query fails, GHTK is not offered. Apply NFR-DAT-04. |

### 1.3 Communication and configuration controls

| ID | Requirement | Verification criterion |
| --- | --- | --- |
| NFR-IF-11 | Sensitive web/API exchanges shall use encrypted transport; SMTP shall use the approved protected connection. Validate untrusted input before state changes. | Inspect endpoint/transport configuration and submit invalid payloads. Payment signatures remain mandatory over encrypted transport. This is a derived protection criterion; protocol versions are not baselined here. |
| NFR-IF-12 | Only authorized Admin may configure approved integration types/endpoints/credentials/activation. Failed required validation shall prevent activation. | UC63 rejects unsupported/invalid configuration, masks secrets, audits changes safely and creates no business transaction during a connection test. |

Production/sandbox credentials, callback URLs, contracts, billing and quotas are go-live prerequisites under Report 2 A-02 and NFR-OPEN-03. Provider access/UAT success is not claimed. Exact endpoints, schemas, identity setup, timeouts and retry budgets belong to approved interface/design configuration under NFR-OPEN-04.
