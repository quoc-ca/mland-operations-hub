### 14.1 Payment Hold Expiry and Release

**Trigger:** On the approved expiry schedule; exact polling interval and processing-delay tolerance: TBD.

**Purpose:** Release unpaid workshop seats, retail variant/benefit holds and custom queue slots without undoing accepted verified payment.

**Processing steps**

1. Identify attempts and holds using their original 10-minute link and 15-minute hold deadlines.
2. Recheck verification and terminal facts; settle and release cannot both take effect for the same resources.
3. For an expired unpaid hold, release its resources once and record the actual outcome; retain exception evidence without revival.

**Failure behavior**

| Scenario | System behavior |
| --- | --- |
| Temporary dependency or processing failure | Record a safe failure and use the approved retry policy; no false success or duplicate business effect. |
| Stale, duplicate or conflicting evidence | Recheck current facts and return no change or a safe rejection; preserve accepted business history. |

**Source:** [Background Job Inventory](../../03-i-overall-requirements/05-system-fuctionalities/03-background-job-inventory.md).
