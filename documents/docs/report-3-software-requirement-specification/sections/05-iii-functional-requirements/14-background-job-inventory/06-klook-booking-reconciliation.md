### 14.6 Klook Booking Reconciliation

**Trigger:** An enabled Klook result is ambiguous, duplicated or eligible for retry.

**Purpose:** Resolve workshop integration evidence without duplicate bookings or capacity effects.

**Processing steps**

1. Check the required Mland availability/hold flow and retained booking/integration identity.
2. Apply an eligible confirmed result once; inconsistent or incomplete evidence remains unresolved rather than inventing a booking.
3. Record the result and retry only under approved rules. Klook cancellations do not automatically release Mland capacity.

**Failure behavior**

| Scenario | System behavior |
| --- | --- |
| Temporary dependency or processing failure | Record a safe failure and use the approved retry policy; no false success or duplicate business effect. |
| Stale, duplicate or conflicting evidence | Recheck current facts and return no change or a safe rejection; preserve accepted business history. |

**Source:** [Background Job Inventory](../../03-i-overall-requirements/05-system-fuctionalities/03-background-job-inventory.md).
