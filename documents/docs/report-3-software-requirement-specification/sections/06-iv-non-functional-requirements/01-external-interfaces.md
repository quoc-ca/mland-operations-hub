## 1. External Interfaces

The system is a web application. Users reach it through a browser, and the system exchanges data with six external services.

### 1.1 User Interfaces

1. All actors use the same responsive web interface; V1 has no native mobile application.
2. Customer pages support Vietnamese and English. Vietnamese is the default, and the chosen language is remembered on the device.
3. Staff, Manager, Admin and Carrier screens use the same status terms as customer screens.

### 1.2 Hardware Interfaces

1. No dedicated hardware is required. A computer, tablet or phone with a current browser is sufficient.
2. Workshop check-in accepts the ticket QR code value or a manually typed ticket code.
3. Card details are entered only on the VNPay page, never on a Mland screen or device.

### 1.3 Software Interfaces

::: {.docx-table width="100%" columns="22%,20%,58%" repeat-header="false"}

| External System | Direction | Purpose and Boundary |
| --- | --- | --- |
| Firebase Authentication | Browser ↔ Firebase; System → Firebase | Email/password and Google sign-in, email verification and password reset. Firebase proves identity only; the system decides account status, role and branch from its own data. |
| VNPay Payment Gateway | System → VNPay; VNPay → System | Workshop deposits, ticket balances, retail payments and custom-order quotes. Only a signed VNPay notification or a signed status query (QueryDR) changes a payment; the browser return page does not. |
| GHTK Delivery | System → GHTK | Shipping fee query only. The Carrier account records pickup and delivery in the system; no shipment is created through GHTK. |
| Gemini AI | System → Gemini | Text answers for Basic AI Support from approved public information. Gemini receives no design images and makes no business decision. |
| Cloudinary Storage | System ↔ Cloudinary | Product images and refund receipt images, stored privately and opened through signed links. |
| Mland SMTP Mail Gateway | System → SMTP | Account invitations, booking confirmations with QR tickets and other business emails. A failed email never reverses a completed business action. |

:::

### 1.4 Communication Interfaces

1. Browser and server exchange JSON over HTTPS. The server accepts browser requests only from configured web origins.
2. Email is sent over SMTP with STARTTLS.
3. When an external service fails or times out, the system keeps the Mland record unchanged and shows a retryable failure state.
