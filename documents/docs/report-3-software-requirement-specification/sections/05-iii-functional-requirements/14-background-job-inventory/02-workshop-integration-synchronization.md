### 14.2 Workshop Integration Synchronization

**Trigger:** After an enabled schedule/capacity change, confirmed booking or eligible retry.

**Purpose:** Synchronize approved Mland workshop availability and confirmed-booking information with Klook.

**Processing steps**

1. Load the committed authorized change and enabled integration contract.
2. Send permitted availability or booking data, validate the response and record its outcome.
3. Keep Mland capacity and accepted bookings authoritative on remote failure; retry policy is TBD. Do not consume Klook cancellation events.

**Failure behavior**

| Scenario | System behavior |
| --- | --- |
| Temporary dependency or processing failure | Record a safe failure and use the approved retry policy; no false success or duplicate business effect. |
| Stale, duplicate or conflicting evidence | Recheck current facts and return no change or a safe rejection; preserve accepted business history. |

**Source:** [Background Job Inventory](../../03-i-overall-requirements/05-system-fuctionalities/03-background-job-inventory.md).
