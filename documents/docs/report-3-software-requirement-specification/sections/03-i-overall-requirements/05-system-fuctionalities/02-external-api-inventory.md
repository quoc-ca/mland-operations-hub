### 5.2 External API Inventory

This inventory lists every interface between the system and an external service. API-01 and API-02 are called from outside; the others are called by the system.

#### 5.2.1 GET: /api/v1/integrations/payments/vnpay/events (API-01)

::: {.docx-table width="100%" columns="15%,85%" repeat-header="false"}

| | |
| --- | --- |
| ***URL*** | GET: /api/v1/integrations/payments/vnpay/events |
| ***Purpose*** | Receives the payment result (IPN) from VNPay and settles the workshop deposit, ticket balance, retail order or custom order it belongs to. |
| ***Caller*** | VNPay payment gateway |
| ***Auth*** | No sign-in. HMAC-SHA512 signature check, then reference, amount, currency and duplicate checks. |

:::

#### 5.2.2 GET: /payment/vnpay/return (API-02)

::: {.docx-table width="100%" columns="15%,85%" repeat-header="false"}

| | |
| --- | --- |
| ***URL*** | GET: /payment/vnpay/return |
| ***Purpose*** | Redirects the customer back to the matching booking or order page after VNPay; it never changes a payment state. |
| ***Caller*** | Customer browser, redirected by VNPay |
| ***Auth*** | None; the result is shown for information only. |

:::

#### 5.2.3 GET: https://sandbox.vnpayment.vn/paymentv2/vpcpay.html (API-03)

::: {.docx-table width="100%" columns="15%,85%" repeat-header="false"}

| | |
| --- | --- |
| ***URL*** | GET: {VNPay payment URL}/paymentv2/vpcpay.html (sandbox by default; configurable) |
| ***Purpose*** | Opens the VNPay payment page for a workshop deposit, ticket balance, retail order or custom-order quote. |
| ***Caller*** | Mland system builds the signed link; the customer browser opens it. |
| ***Auth*** | Merchant code (TMN code) and HMAC-SHA512 signature over the request parameters. |

:::

#### 5.2.4 POST: VNPay QueryDR merchant API (API-04)

::: {.docx-table width="100%" columns="15%,85%" repeat-header="false"}

| | |
| --- | --- |
| ***URL*** | POST: {VNPay QueryDR URL} (configured per environment, HTTPS) |
| ***Purpose*** | Asks VNPay for the real status of a payment whose result is still unknown. |
| ***Caller*** | Mland system (scheduled reconciliation jobs) |
| ***Auth*** | Merchant code and HMAC-SHA512 request signature; the response signature is also verified. Timeout 5 s connect, 10 s read. |

:::

#### 5.2.5 GET: https://services.giaohangtietkiem.vn/services/shipment/fee (API-05)

::: {.docx-table width="100%" columns="15%,85%" repeat-header="false"}

| | |
| --- | --- |
| ***URL*** | GET: https://services.giaohangtietkiem.vn/services/shipment/fee |
| ***Purpose*** | Gets the GHTK shipping fee for a branch and delivery address during retail checkout; it never creates a shipment. |
| ***Caller*** | Mland system |
| ***Auth*** | GHTK partner token in the `Token` header. Timeout 5 s. |

:::

#### 5.2.6 POST: https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent (API-06)

::: {.docx-table width="100%" columns="15%,85%" repeat-header="false"}

| | |
| --- | --- |
| ***URL*** | POST: https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent (default model gemini-2.5-flash) |
| ***Purpose*** | Generates the text answer for Basic AI Support from approved public information. |
| ***Caller*** | Mland system |
| ***Auth*** | Gemini API key. Timeout 15 s; without a key the system returns a fixed fallback answer. |

:::

#### 5.2.7 POST: https://api.cloudinary.com/v1_1/{cloud}/image/upload (API-07)

::: {.docx-table width="100%" columns="15%,85%" repeat-header="false"}

| | |
| --- | --- |
| ***URL*** | POST: https://api.cloudinary.com/v1_1/{cloud}/image/upload and /image/destroy |
| ***Purpose*** | Stores and deletes private product images and refund receipt images; images are read through signed links. |
| ***Caller*** | Mland system |
| ***Auth*** | Cloudinary API key with API-secret signature; files are stored as authenticated (private) objects. |

:::

#### 5.2.8 SMTP: {mail host}:587 (API-08)

::: {.docx-table width="100%" columns="15%,85%" repeat-header="false"}

| | |
| --- | --- |
| ***URL*** | SMTP: {mail host}:587 with STARTTLS |
| ***Purpose*** | Sends account invitations, booking confirmations with QR tickets, verification codes and other business emails. |
| ***Caller*** | Mland system (notification delivery job) |
| ***Auth*** | SMTP username and password over STARTTLS. |

:::

#### 5.2.9 Firebase Authentication API (API-09)

::: {.docx-table width="100%" columns="15%,85%" repeat-header="false"}

| | |
| --- | --- |
| ***URL*** | Firebase Authentication (Google Identity Toolkit), through the Firebase SDKs |
| ***Purpose*** | Browser: sign-up, email/password and Google sign-in, email verification, password reset and change. Server: verifies ID tokens, creates and deletes internal accounts, issues invitation tokens and revokes sessions of suspended accounts. |
| ***Caller*** | Customer or employee browser; Mland system |
| ***Auth*** | Browser: Firebase project API key. Server: Firebase service account. Every protected request carries a Firebase ID token in the `Authorization: Bearer` header. |

:::
