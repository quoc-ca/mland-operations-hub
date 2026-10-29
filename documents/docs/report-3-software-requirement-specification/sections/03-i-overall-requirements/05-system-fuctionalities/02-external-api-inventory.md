### 5.2 External API Inventory

::: {.docx-table width="100%" columns="10%,25%,25%,40%" repeat-header="false"}

|#|API Name|Calling Path|Description|
|---|---|---|---|
|1|VNPay Payment API|System → VNPay / VNPay → System|Creates payment requests and receives approved signed payment confirmations for workshop deposits, retail payments, custom-order deposits, and custom-order final balances. The system validates the signature, transaction reference, amount, success status, and duplicate-event status. A browser Return URL never confirms payment.|
|2|Klook Workshop Registration API|System ↔ Klook|Synchronizes enabled workshop availability and confirmed-booking information. Mland remains the source of truth for workshop schedule, capacity, and holds; Klook cancellation events are not consumed in V1.|
|3|Gemini API|System → Gemini|Provides basic customer text support, independent Design Request image analysis, and package/product price suggestions. Gemini suggestions support a human decision and do not accept, reject, price, or review a custom-manufacturing order.|
|4|Google Maps Embed and Places API|System → Google Maps/Places|Displays store locations and permitted public rating/review information on a read-only basis. Places review/rating content is not persisted, and the UI must provide the required attribution and link back to Google Maps.|
|5|Mland SMTP / Mail Gateway|System → Mail Gateway|Delivers account-security and workflow emails, including OTP or verification messages, workshop QR tickets, payment requests/receipts, booking-design review results, and order-status messages. The mail service does not make business decisions or provide a notification centre.|
|6|SSO Provider API|System ↔ Google/Facebook SSO|Supports external identity registration, login, password recovery, and identity-token exchange. The provider establishes identity only; Mland resolves account status, business role, and authorization from its own database.|
|7|Cloud Storage API|System ↔ Cloud Storage Service|Stores catalogue media and eligible custom-manufacturing reference images outside the application database. Access and deletion follow the approved retention rules; rejected or withdrawn custom requests and independently classified AI images are deleted after processing.|
|8|GHTK Delivery API|System ↔ GHTK; GHTK → System webhook|For a paid retail branch order, the system obtains a delivery-fee quote before checkout, creates one prepaid/no-COD shipment after verified payment, stores provider tracking identifiers, and receives or reconciles normalized delivery status. Staff records the physical carrier handoff after shipment creation. If approved production GHTK access or callback security is unavailable, GHTK is disabled and pickup remains available; the system never uses a mock or manual fallback as carrier evidence. Delivery-failure, return, cancellation, and refund resolution workflows are out of scope.|

:::
