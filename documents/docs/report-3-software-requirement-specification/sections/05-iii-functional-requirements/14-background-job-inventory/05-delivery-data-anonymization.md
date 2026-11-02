### 14.5 Delivery Data Anonymization

**Trigger:** Daily for eligible GHTK handoffs completed at least 30 days earlier.

**Purpose:** Retire unnecessary recipient/contact/address data without deleting required order or minimized audit facts.

**Processing steps**

1. Use the original actual handoff time, not recipient entry, preparation or retry time.
2. Delete or irreversibly obscure recipient and delivery-address data and applicable copies after the approved 30-day boundary.
3. Preserve minimized payment/order/handoff evidence; failures remain visible and retries do not reset the retention clock.

**Failure behavior**

| Scenario | System behavior |
| --- | --- |
| Temporary dependency or processing failure | Record a safe failure and use the approved retry policy; no false success or duplicate business effect. |
| Stale, duplicate or conflicting evidence | Recheck current facts and return no change or a safe rejection; preserve accepted business history. |

**Source:** [Background Job Inventory](../../03-i-overall-requirements/05-system-fuctionalities/03-background-job-inventory.md).
