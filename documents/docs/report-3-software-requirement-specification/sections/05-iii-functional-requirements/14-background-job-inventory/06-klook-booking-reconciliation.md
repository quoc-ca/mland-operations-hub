### 14.6 OTA Booking Reconciliation

**Trigger:** A partner booking or cancellation event is unmatched, conflicting or duplicated.

**Purpose:** Resolve partner evidence without duplicate bookings or capacity effects; unmatched or conflicting events become work items for review.

**Processing steps**

1. Check the Mland availability and the retained partner booking identity.
2. Apply an eligible confirmed result once; inconsistent or incomplete evidence remains unresolved rather than inventing a booking.
3. Record the result and retry only under approved rules. Only a cancellation confirmed by the partner releases Mland capacity.

**Failure behavior**

| Scenario | System behavior |
| --- | --- |
| Temporary dependency or processing failure | Record a safe failure and use the approved retry policy; no false success or duplicate business effect. |
| Stale, duplicate or conflicting evidence | Recheck current facts and return no change or a safe rejection; preserve accepted business history. |

**Source:** [Background Job Inventory](../../03-i-overall-requirements/05-system-fuctionalities/03-background-job-inventory.md).
