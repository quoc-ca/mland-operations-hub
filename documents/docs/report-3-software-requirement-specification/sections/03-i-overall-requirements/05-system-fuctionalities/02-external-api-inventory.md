### 5.2 External API Inventory

::: {.docx-table width="100%" columns="10%,25%,25%,40%" repeat-header="false"}

|#|API Name|Calling Path|Description|
|---|---|---|---|
|1|VNPay Payment API|System → VNPay / VNPay → System|Creates payment requests and receives approved signed payment confirmations for workshop deposits, retail payments, and one-time custom-order quote payments. The system validates the signature, transaction reference, amount, success status, and duplicate-event status. A browser Return URL never confirms payment. For a retail checkout, the payment link lasts the whole 10-minute hold and is reused when the Member pays again, so one checkout has one VNPay transaction.|
|2|Partner Booking API (Retired)|None|Not used; no partner booking channel is part of V1 (Stakeholder statement, Feature-003 owner, 2026-11-25).|
|3|Gemini API|System → Gemini|Provides Basic AI Support (basic customer text support) only. It does not receive Feature-004 reference images, analyze design images, suggest prices, or price, accept, reject, or review design requests and custom-manufacturing orders.|
|5|Mland SMTP / Mail Gateway|System → Mail Gateway|Delivers mandatory Staff/Manager invitations and approved business emails, including booking and retail confirmations. Firebase sends Member verification and password-recovery emails. SMTP acceptance does not prove inbox delivery or account activation.|
|6|SSO Provider API|System ↔ Firebase Authentication / Google|Email and password registration, sign-in and password reset, and Google sign-in. The provider establishes identity only; Mland resolves account status, business role, and authorization from its own database.|
|7|Cloud Storage API|System ↔ Cloudinary|Stores catalogue media and refund receipt images. Temporary design reference images use local server storage.|
|8|GHTK Delivery API|System → GHTK|Before checkout, the system obtains the GHTK delivery fee for each branch and recipient (fee query only). After verified payment, the platform records one prepaid/no-COD shipment per paid GHTK branch order with a platform-generated tracking number; no shipment is created through the GHTK API and no GHTK webhook is received in V1. The shipment moves Đang chuẩn bị hàng → Chờ ĐVVC lấy hàng (Staff) → Đã bàn giao cho ĐVVC → Đã giao (GHTK partner portal). If GHTK fee access is not configured, GHTK is not offered and store pickup remains available. Delivery-failure, return, cancellation, and refund resolution workflows are out of scope.|

:::
