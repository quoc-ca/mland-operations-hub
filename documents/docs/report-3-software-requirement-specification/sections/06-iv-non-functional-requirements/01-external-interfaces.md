## 1. External Interfaces

Scope follows Report 2 and Report 3's [External API Inventory](../03-i-overall-requirements/05-system-fuctionalities/02-external-api-inventory.md). Provider documentation in [Report 2 Evidences](../../../report-2-project-management-plan/sections/02-i-project-overview/07-evidences.md) supports capability checks; Mland decisions determine the capabilities V1 uses.

### 1.1 User and hardware interfaces

| ID | Requirement | Verification criterion |
| --- | --- | --- |
| NFR-IF-01 | Public pages and authenticated Member/shop functions shall use the web interface, with server-enforced access rules on direct requests. | Public browsing requires no sign-in; protected direct requests receive the same checks as UI navigation. See UC1–UC14 and UC43–UC53. |
| NFR-IF-02 | Workshop check-in shall support QR scanning and manual code entry; scanning alone shall not record attendance without Staff verification. | UC20 works with either input; invalid, unconfirmed or already checked-in bookings create no new attendance effect. |

V1 does not mandate a dedicated scanner, terminal, printer or native mobile application. QR scanning uses equipment supported by the selected browser environment; coverage remains NFR-OPEN-03. Card entry takes place at VNPay rather than through a Mland card-data interface.

### 1.2 Software and service interfaces

| ID | Interface and direction | Required boundary | Verification criterion |
| --- | --- | --- | --- |
| NFR-IF-03 | VNPay: Mland → initiation/QueryDR; VNPay → signed IPN; browser → Return URL. | Sole provider for workshop deposits, retail full payment and custom deposits/balances. Only validated signed IPN or eligible signed QueryDR authorizes success; Return URL is informational. | Tampered, mismatched, duplicate, unknown and late evidence follows NFR-SEC-06 and NFR-REL-02/03. Browser-only success changes no payment state. |
| NFR-IF-04 | Klook: approved availability/hold and confirmed-booking exchange. | Mland owns capacity. Klook confirmation requires Mland availability/hold. Klook cancellation is not consumed and cannot automatically release capacity. | UC27/UC28 reject confirmation without eligible hold, deduplicate bookings and preserve Mland capacity on synchronization failure. |
| NFR-IF-05 | Paid Gemini: Mland → permitted prompt/image/pricing context; Gemini → advice. | Basic text support, Member booking-design feature analysis and package/product price suggestions only. AI cannot publish prices or decide custom manufacturing. | UC29–UC31 reject unsupported requests/unusable results; failure creates no automatic approval. Apply NFR-DAT-01/02 cleanup. |
| NFR-IF-06 | Google Maps Embed/Places: read-only map and permitted public ratings/reviews. | Show required attribution and Google Maps link. Do not persist Places rating/review content, collect internal reviews or submit reviews on the customer's behalf. | Inspect presentation and storage/logs. Review action opens Google Maps; failure is unavailable content, not an invented rating/review. |
| NFR-IF-07 | Mland-domain SMTP / Mail Gateway: Mland → security/workflow messages. | Verification/recovery, QR tickets, payment requests/receipts, design-review results and order updates as required by workflows. No notification centre or email-driven approval. | Mandatory verification cannot be bypassed on delivery failure. Failed notices after committed payment/password change/fulfilment do not reverse results; see NFR-REL-05. |
| NFR-IF-08 | Configured identity/SSO service: provider ↔ browser/application identity flow. | Provider establishes identity; Mland resolves current status, role, ownership, branch and delegation. | Invalid tokens/suspended accounts grant no protected access. SSO claims cannot assign Staff/Manager/Admin privileges. See UC1/UC2 and NFR-SEC-01/02. |
| NFR-IF-09 | Cloud Storage: authorized media upload/read/delete. | Separate public catalogue assets and private custom references by disclosure/retention. AI input is not a retained catalogue/custom-order asset. | Unauthorized requests cannot read private media. NFR-DAT-02/03/07 govern deletion without deleting referenced catalogue/history media. |
| NFR-IF-10 | GHTK: physical handoff outside the platform; shop actor → Mland record. | Manual handoff only; no GHTK API, quote, live delivery state, delivery-failure, return, cancellation or refund workflow. | UC53 requires actual custody, carrier/reference, actor and time. Packing alone cannot set `handed_to_carrier`. Apply NFR-DAT-04. |

### 1.3 Communication and configuration controls

| ID | Requirement | Verification criterion |
| --- | --- | --- |
| NFR-IF-11 | Sensitive web/API exchanges shall use encrypted transport; SMTP shall use the approved protected connection. Validate untrusted input before state changes. | Inspect endpoint/transport configuration and submit invalid payloads. Payment signatures remain mandatory over encrypted transport. This is a derived protection criterion; protocol versions are not baselined here. |
| NFR-IF-12 | Only authorized Admin may configure approved integration types/endpoints/credentials/activation. Failed required validation shall prevent activation. | UC55 rejects unsupported/invalid configuration, masks secrets, audits changes safely and creates no business transaction during a connection test. |

Production/sandbox credentials, callback URLs, contracts, billing and quotas are go-live prerequisites under Report 2 A-02. Provider access/UAT success is not claimed. Exact endpoints, schemas, identity setup, timeouts and retry budgets belong to approved interface/design configuration; pending values are NFR-OPEN-04/05.
