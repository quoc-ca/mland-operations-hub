## 17. External API Inventory

The system uses 9 external interfaces. Section I.5.2 External API Inventory specifies each interface's URL, purpose, caller and authentication; this table is the summary.

::: {.docx-table width="100%" columns="12%,30%,28%,30%" repeat-header="false"}

| API ID | Interface | Caller | Auth |
| --- | --- | --- | --- |
| API-01 | VNPay payment notification (IPN) | VNPay | HMAC-SHA512 signature |
| API-02 | VNPay browser return | Customer browser | None; display only |
| API-03 | VNPay payment page | Mland system, opened by the browser | Merchant code and HMAC-SHA512 signature |
| API-04 | VNPay payment status query (QueryDR) | Mland system | Merchant code and HMAC-SHA512 signature |
| API-05 | GHTK shipping fee query | Mland system | GHTK partner token |
| API-06 | Gemini text generation | Mland system | Gemini API key |
| API-07 | Cloudinary image upload and delete | Mland system | Signed with Cloudinary API secret |
| API-08 | Mland SMTP Mail Gateway | Mland system | SMTP account over STARTTLS |
| API-09 | Firebase Authentication | Browser and Mland system | Firebase ID token; service account on the server |

:::
