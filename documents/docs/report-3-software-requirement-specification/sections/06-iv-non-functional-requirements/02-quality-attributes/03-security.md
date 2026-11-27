### 2.3 Security

::: {.docx-table width="100%" columns="25%,75%" repeat-header="false"}

| Requirement | Description |
| --- | --- |
| Authentication required | Every page and action requires sign-in, except the public catalogue, workshop packages and sessions, public reviews, Basic AI Support, guest workshop booking with its look-up links, and the payment return page. |
| Password protection | The system never stores passwords. Firebase holds the credentials; the system receives only a verified identity token. |
| Repeated sign-in attempts | The system sets no lockout threshold of its own. Firebase temporarily blocks repeated failed sign-ins, and the sign-in page then shows a "too many attempts" message. A guest booking verification code is blocked after 5 wrong entries and can be resent at most 5 times per hour, with 60 seconds between sends. |
| Account status | A suspended account, or an account whose setup is incomplete, cannot use protected functions. |
| Role and branch scope | Every request is checked against the user's role. Staff act only in their assigned branch, Members see only their own records, and the Carrier sees no prices or Member data. |
| Payment integrity | A payment changes state only after its VNPay signature, reference, amount and status are verified. Card data is never entered into or stored by the system. |
| Duplicate requests | A repeated payment notification or command creates at most one result. Reusing a request key with different content is rejected. |
| Private files | Product and receipt images are private and opened only through signed, time-limited links. Uploads must be JPEG, PNG or WebP, at most 5 MB each. |
| Refund bank accounts | Refund bank account numbers are stored encrypted and are readable only by a Manager processing the refund. |
| Secrets | Provider keys and passwords are kept outside source code and are never shown on screens or in logs. |
| Usage limits | Basic AI Support has a daily limit per Guest and per Member. Design image analysis has a limit per Member every 5 minutes. |
| Audit log | Payment confirmation, price publication, design review, custom quote, refund, pickup and carrier handover record the actor, time, record and reason. |
| Personal data retention | A daily job deletes AI chat text and temporary design images after 30 days. Refund-link submissions and settlement notification data are deleted after 30 days. Delivery recipient name, phone and address stay with the order and are visible only to the Member, permitted Staff and Manager, and the Carrier. |

:::
