## 2. Assumptions and Constraints

The business and integration decisions in this section are traced in [07. Evidences](07-evidences.md). Project-management values elsewhere in this report are either illustrative proposals or marked TBD until SEP490_G22 approves a delivery baseline; they are not system-integration TBD items.

### Assumptions

::: {.docx-table width="100%" columns="15%,50%,35%" repeat-header="false"}

| No. | Description | Note |
|---|---|---|
| A-01 | Mland provides and maintains operational master data: catalogue/component data, package and product prices, capacity, blackout dates, feasible-design constraints, available-to-sell quantities, loyalty rates, voucher rules, and promotion data. | Missing or stale configuration can stop a sale or booking; it is not inferred by the system. [E-01](07-evidences.md#e-01--mland-business-decision-record) |
| A-02 | Admin obtains and protects the production and sandbox credentials, approved callback URLs, and provider contracts for VNPay, Klook, Google Maps/Places, Gemini, the Mland-domain SMTP service, and any cloud storage service. | Credentials, contracts, billing, quotas, and supplier approval are go-live prerequisites. [E-02](07-evidences.md#e-02--vnpay-payment-and-ipn), [E-07](07-evidences.md#e-07--klook-supplier-integration-and-availability) |
| A-03 | Mland publishes general Terms and Privacy Policy that cover image-processing purposes, the stated retention periods, Google attribution, and delivery-recipient data. | No separate feature-specific image-consent checkbox is added in V1. [E-01](07-evidences.md#e-01--mland-business-decision-record) |
| A-04 | Finance or Legal confirms the five-year retention period for VNPay references and business audit evidence before go-live. | Mland stores no card data. [E-01](07-evidences.md#e-01--mland-business-decision-record) |

:::

### Constraints

::: {.docx-table width="100%" columns="15%,50%,35%" repeat-header="false"}

| No. | Description | Note |
|---|---|---|
| C-01 | VNPay is the sole V1 payment provider. A payment is completed only by a valid signed IPN or the defined signed QueryDR recovery result; Return URL is display-only. | Every confirmation validates signature, transaction reference, amount, and success status; duplicate events are idempotent. [E-02](07-evidences.md#e-02--vnpay-payment-and-ipn), [E-03](07-evidences.md#e-03--vnpay-querydr) |
| C-02 | VNPay payment links expire after 10 minutes and the associated retail/cart, workshop, or custom-order hold expires after 15 minutes. | A pending payment after minute 15 does not auto-confirm or auto-fulfil; it is handled outside the system as a payment exception. [E-01](07-evidences.md#e-01--mland-business-decision-record), [E-02](07-evidences.md#e-02--vnpay-payment-and-ipn) |
| C-03 | GHTK is a manual-handoff delivery provider only. | No GHTK API, shipping quote, tracking, delivery-failure, return, or refund workflow is in V1. Recipient/address data are deleted or obscured 30 days after handoff. [E-01](07-evidences.md#e-01--mland-business-decision-record), [E-10](07-evidences.md#e-10--ghtk-provider-capability) |
| C-04 | Klook synchronization protects Mland-owned workshop capacity. | Klook cancellation events are not consumed by Mland V1; Staff or Manager acts on Klook's own notification outside Mland. [E-01](07-evidences.md#e-01--mland-business-decision-record), [E-07](07-evidences.md#e-07--klook-supplier-integration-and-availability) |
| C-05 | Google Maps Embed and Places are read-only. | Places content is not persisted; rating/review presentation includes required attribution and a Google Maps link. [E-04](07-evidences.md#e-04--google-maps-embed-and-places-policy) |
| C-06 | Gemini is a paid AI service used only for support and reference. | Text prompts/responses are retained at most 30 days; AI images are not retained; AI does not decide custom orders. [E-01](07-evidences.md#e-01--mland-business-decision-record), [E-05](07-evidences.md#e-05--gemini-data-terms-and-logging) |
| C-07 | In-system cancellation and refund are supported only for Mland workshop bookings made by Guests or Members: a confirmed booking with a paid deposit may be cancelled at least 24 hours before the scheduled session start, and Mland initiates a VNPay refund for 100% of the deposit. Retail/custom-order cancellation and refunds, accounting, and payment-reconciliation workflows remain outside V1. | VNPay refund processing time and pending/failed refund handling remain unspecified. QueryDR is a payment-recovery check, not a reconciliation workflow. [E-03](07-evidences.md#e-03--vnpay-querydr), [E-11](07-evidences.md#e-11--workshop-booking-window-cancellation-and-deposit-refund) |
| C-08 | A Guest or Member may book a workshop session no more than 30 calendar days before its scheduled start. | Applies to workshop bookings made through Mland. [E-11](07-evidences.md#e-11--workshop-booking-window-cancellation-and-deposit-refund) |

:::
