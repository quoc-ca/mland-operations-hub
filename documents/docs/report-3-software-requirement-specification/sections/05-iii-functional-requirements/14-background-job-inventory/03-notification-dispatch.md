### 14.3 Notification Dispatch

**Trigger:** A committed workflow creates a pending email task or an eligible retry becomes due.

**Purpose:** Deliver approved security/workflow emails without changing the accepted business result.

**Processing steps**

1. Read the committed task and approved template/recipient contract; unspecified notice contracts remain TBD.
2. Send through configured Mland SMTP and record a safe provider outcome.
3. Retry temporary failures under the approved policy; exact retry values are TBD. Mail failure does not reverse a sale, booking or approval.

**Failure behavior**

| Scenario | System behavior |
| --- | --- |
| Temporary dependency or processing failure | Record a safe failure and use the approved retry policy; no false success or duplicate business effect. |
| Stale, duplicate or conflicting evidence | Recheck current facts and return no change or a safe rejection; preserve accepted business history. |

**Source:** [Background Job Inventory](../../03-i-overall-requirements/05-system-fuctionalities/03-background-job-inventory.md).
