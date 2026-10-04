## 1. External Interfaces

The interfaces below are limited to approved V1 providers. Provider documentation verifies capability or constraint; Mland business decisions determine the implemented scope. See [Report 2 Evidences](../../../report-2-project-management-plan/sections/02-i-project-overview/07-evidences.md).

| Interface | V1 purpose | Required boundary |
| --- | --- | --- |
| VNPay | Initiates workshop deposits, retail payments, custom deposits, and custom final-balance payments. Receives IPN and uses QueryDR only for defined recovery. | Validate signature, reference, amount, and success status; process duplicates idempotently. Return URL is not confirmation. Payment links expire after 10 minutes. |
| Klook | Exchanges enabled workshop availability and confirmed-booking information. | Mland owns capacity. Klook obtains Mland availability/hold before confirmation. Klook cancellation is not received or processed by Mland V1. |
| Gemini API | Provides basic text support, booking-design image analysis, and package/product price suggestions. | Paid service only. Suggestions do not make business decisions; AI does not evaluate custom-manufacturing orders. Retain text logs at most 30 days and no AI image input after processing. |
| Google Maps Embed and Places | Shows store locations and permitted public rating/review information. | Read-only. Display required attribution and Google Maps link; do not persist Places review/rating content or write reviews to Google. |
| Mland-domain SMTP service | Sends account-security and workflow emails, including QR tickets, payment requests/receipts, and review/order updates. | Delivers a platform-requested message only; it does not make business decisions or create a notification centre. |
| Cloud Storage Service | Stores catalogue media and eligible custom-manufacturing reference images. | Apply the image retention/deletion rules in the NFR overview; do not retain independently classified AI images. |
| Giao Hang Tiet Kiem (GHTK) | Receives physical paid retail/custom orders through Staff-recorded manual handoff. | No API integration, quote, tracking, delivery-failure, return, cancellation, or refund interface in V1. |
| SSO Provider | Establishes external identity for registration, login, and recovery. | The application resolves account status, business role, and authorization from its own database. |
